(ns core
  (:gen-class)
  (:require
   [application]
   [buddy.hashers :as hashers]
   [cheshire.core :as cheshire]
   [clojure.string :as str]
   [db :as db]
   [hiccup :as hiccup]
   [honey.sql :as sql]
   [layout :as layout]
   [next.jdbc :as jdbc]
   [next.jdbc.prepare :as prepare]
   [next.jdbc.result-set :as result-set]
   [org.httpkit.server :as server]
   [reitit.http :as http]
   [reitit.http.interceptors.keyword-parameters :as keyword-parameters]
   [reitit.http.interceptors.parameters :as parameters]
   [reitit.interceptor.sieppari :as sieppari]
   [reitit.ring :as ring]
   [ring.middleware.session :as session]
   [ring.middleware.session.store :as session.store]
   [ring.util.response :as response]
   [utils :as utils])
  (:import
   [java.sql PreparedStatement ResultSetMetaData]
   [java.util HexFormat]
   [javax.crypto Mac]
   [javax.crypto.spec SecretKeySpec]))


(set! *warn-on-reflection* true)


(def port 8083)

(def dev-mode? (or (System/getenv "LEARNING_APP__ENVIRONMENT") true))

(def db-auth-secret
  (if dev-mode?
    "secret"
    (System/getenv "LEARNING_APP_DB_AUTH_SECRET")))


;;
;; DB
;;

(def db-spec
  {:dbtype "sqlite", :dbname "app.db"})


;; This makes possible to pass clojure map or vector as a query parameter.
;; The value will be saved in a column as JSON string.
(extend-protocol prepare/SettableParameter
  clojure.lang.IPersistentMap
  (set-parameter [m ^PreparedStatement ps idx]
    (.setObject ps idx (cheshire/generate-string m)))

  clojure.lang.IPersistentVector
  (set-parameter [m ^PreparedStatement ps idx]
    (.setObject ps idx (cheshire/generate-string m))))


;; This makes BLOB columns to be read as a JSON value.
(extend-protocol result-set/ReadableColumn
  java.lang.String
  (read-column-by-label [v _]
    v)
  (read-column-by-index [v ^ResultSetMetaData rsmeta idx]
    (case (.getColumnTypeName rsmeta idx)
      ;; It is not possible to declare "JSON" type in a table definition
      ;; https://sqlite.org/json1.html#interface_overview
      ;; I am going to use BLOB columns for JSON values only.
      "BLOB" (cheshire/parse-string v true)
      v)))


;;
;; Users
;;

 (defn user-id
  [db user-name password]
  (when (and (utils/non-blank user-name)
             (utils/non-blank password))
    (let [user (jdbc/execute-one! db ["SELECT id, password FROM users WHERE name = ?" user-name])]
      (when (:valid (hashers/verify password (:password user)))
        (:id user)))))


(defn add-user
  [db user-name password]
  ;; create user in SQLite DB
  (let [password-hash (hashers/derive password {:alg :argon2id})
        {:keys [id]}  (jdbc/execute-one! db ["INSERT INTO users (name, password) VALUES (?, ?) RETURNING id" user-name password-hash])

        ;; create user DB in CouchDB
        couch-db (db/use (str "userdb-" id))]

    ;; create user role in CouchDB
    (db/secure couch-db {:members {:names [], :roles [(str "u:" id)]}})))


;;
;; User Session
;;

 (deftype Sessions [db]
  session.store/SessionStore
  (read-session [_ token]
    (:value
     (jdbc/execute-one! db ["SELECT value FROM sessions WHERE token = ?" token])))
  (write-session [_ token value]
    (let [token (or token (str (random-uuid)))]
      (jdbc/execute! db ["INSERT INTO sessions (token, value) VALUES (?, ?)" token value])
      token))
  (delete-session [_ token]
    (jdbc/execute! db (sql/format {:delete-from :sessions, :where [:= :token token]}))
    nil))



;;
;; Pages
;;

(defn login-page
  [{:html/keys [head body]}]
  [:html
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1, interactive-widget=resizes-content"}]
    [:title "Sprecha"]
    [:link {:rel "icon" :href "/favicon.ico"}]
    [:link {:rel "stylesheet" :href "/css/styles.css"}]
    [:script {:src "/js/htmx/htmx.min.js"}]
    head]
   [:body
    body]])


(defn hmac-sign
  [^String user-name ^String secret]
  (.formatHex
   (HexFormat/of)
   (.doFinal
    (doto (Mac/getInstance "HmacSHA256")
      (.init (SecretKeySpec. (.getBytes secret) "HmacSHA256")))
    (.getBytes user-name))))


(defn auth-proxy-response
  "Produce the `/auth/check` response map expected by the CouchDB proxy.

  When the session is missing, or empty, a `401` response is returned.
  Otherwise the response includes the proxy auth headers populated from
  the current `:user-id`."
  [session]
  (if (seq session)
    (let [user-name  (-> session :user-id str)
          user-roles (str/join "," [(str "u:" user-name)])
          token      (hmac-sign user-name db-auth-secret)]
      (-> {:status 200}
          (response/header "X-Auth-UserName" user-name)
          (response/header "X-Auth-Roles" user-roles)
          (response/header "X-Auth-Token" token)))
    {:status 401}))


(def session-interceptor
  {:name  ::session-interceptor
   :enter (fn [ctx]
            (jdbc/on-connection [db db-spec]
              (let [opts {:store        (->Sessions db)
                          :set-cookies? true
                          :cookie-name  "ring-session"
                          :cookie-attrs {:path "/", :http-only true}}]
                (update ctx :request session/session-request opts))))
   :leave (fn [ctx]
            (jdbc/on-connection [db db-spec]
              (let [opts {:store        (->Sessions db)
                          :set-cookies? true
                          :cookie-name  "ring-session"
                          :cookie-attrs {:path "/", :http-only true}}]
                (update ctx :response session/session-response (:request ctx) opts))))})


(def login-interceptor
  {:name  ::login-interceptor
   :enter (fn [ctx]
            (jdbc/on-connection [db db-spec]
              (let [{:keys [user password]} (-> ctx :request :params)
                    user-id (user-id db user password)]
                (assoc-in ctx [:request :user-id] user-id))))})


(def login-redirect-interceptor
  {:name  ::login-redirect-interceptor
   :enter (fn [ctx]
            (let [session (-> ctx :request :session)]
              (cond-> ctx
                (empty? session) (assoc :queue [], :response (response/redirect "/login")))))})


(def root-redirect-interceptor
  {:name  ::root-redirect-interceptor
   :enter (fn [ctx]
            (let [session (-> ctx :request :session)]
              (cond-> ctx
                (seq session) (assoc :queue [], :response (response/redirect "/")))))})


(def app-routes
  (http/ring-handler
   ;; Main handler
   (http/router
    [(into
      [;;  Protected routes
       [""
        {:interceptors [login-redirect-interceptor]}
        ["/"
         {:get {:handler (fn [request]
                           (let [user-id (-> request :session :user-id)]
                             {:html/layout layout/page
                              :html/head   [:meta {:name "user-id" :content user-id}]
                              :html/body   [:div#app
                                            {:hx-get      "/home"
                                             :hx-trigger  "controlled"
                                             :hx-on:controlled "console.log(event, 'controlled')"
                                             :hx-push-url "true"}]
                              :status      200}))}}]
        ["/js/app/sw.js"
         {:get {:handler (fn [_]
                           (-> (response/resource-response "public/js/app/sw.js")
                               (response/content-type "text/javascript")
                               (response/header "Service-Worker-Allowed" "/")))}}]
        ["/auth/check"
         {:get
          (fn [{:keys [session] :as _request}]
            (auth-proxy-response session))}]]]
      application/ui-routes)

     ;; Public Routes
     ["/login"
      {:interceptors [root-redirect-interceptor]

       :get {:handler (fn [_]
                        {:html/layout login-page
                         :html/body [:div.login-page
                                     [:form.login-page__form
                                      {:action "/login"
                                       :method "post"}
                                      [:h1.login-page__header
                                       "Вход"]
                                      [:div.login-page__input.input
                                       [:input.login-page__input.input__input-area
                                        {:id "email"
                                         :type "text"
                                         :autocomplete "email"
                                         :name "user"
                                         :placeholder "Email или имя пользователя"}]
                                       [:button.input__clear-button
                                        {:hx-on:click "htmx.find('#email').value = ''; htmx.find('#email').focus()"}
                                        #_icons/cancel]]
                                      [:div.login-page__input.input
                                       [:input.input__input-area
                                        {:id "password"
                                         :type "password"
                                         :autocomplete "current-password"
                                         :name "password"
                                         :placeholder "Пароль"}]]
                                      [:div.login-page__error-message
                                       {:id "error-message"}]
                                      [:button.big-button.blue-button
                                       {:type "submit"}
                                       [:div.big-button__loader.htmx-indicator
                                        {:id "loader"}]
                                       [:span.big-button__label
                                        "ВХОД"]]]]})}

       :post {:interceptors [login-interceptor]
              :handler (fn [{:keys [user-id]}]
                         (if user-id
                           (-> (response/redirect "/" :see-other)
                               (assoc :session {:user-id user-id}))
                           (response/bad-request "Невернoе имя пользователя или пароль")))}}]]

    {:data {:interceptors [session-interceptor
                           (parameters/parameters-interceptor)
                           (keyword-parameters/keyword-parameters-interceptor)
                           (hiccup/interceptor {:layout-fn nil})]}})

   ;; Default handler
   (constantly {:status 404, :body ""})

   ;; interceptor queue executor
   {:executor sieppari/executor}))


(def resource-routes
  (ring/create-resource-handler
   {:path        "/"
    ;; Explicitly instruct handler not to serve any index files.
    ;; Without this, the handler may serve any index.html it finds on the classpath,
    ;; as happened once with the Datahike library.
    :index-files []}))


(def app
  (let [ring-handler #(ring/routes resource-routes, app-routes)]
    (if dev-mode?
      (ring/reloading-ring-handler ring-handler)
      (ring-handler))))


(defonce server (atom nil))


(defn start-server!
  [app port]
  (reset! server (server/run-server #'app {:port port, :legacy-return-value? false})))


(defn stop-server!
  []
  (when (some? @server)
    (when-let [stopping-promise (server/server-stop! @server)]
      @stopping-promise
      (reset! server nil))))


(defn restart-server!
  [app port]
  (if @server
    (when-some [stopping-promise (server/server-stop! @server)]
      @stopping-promise
      (start-server! app port))
    (start-server! app port)))


(defn -main
  []
  (let [url (str "http://localhost:" port "/")]
   (restart-server! #'app port)
   (println "Serving" url)))


(comment
  ;; Clear sessions
  (jdbc/on-connection [db db-spec]
    (jdbc/execute! db (sql/format {:delete-from :sessions}))))
