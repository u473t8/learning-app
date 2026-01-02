;;
;; https://developer.mozilla.org/en-US/docs/Web/API/Service_Worker_API
;;
(ns sw
  (:require
   [application :as application]
   [clojure.string :as str]
   [db :as db]
   [hiccup :as hiccup]
   [lambdaisland.glogi :as log]
   [lambdaisland.glogi.console :as glogi-console]
   [promesa.core :as p]
   [reitit.http :as http]
   [reitit.http.interceptors.keyword-parameters :as keyword-parameters]
   [reitit.http.interceptors.parameters :as parameters]
   [reitit.interceptor.sieppari :as sieppari]
   [utils :as utils]))


(glogi-console/install!)


(log/set-levels
 {:glogi/root :all})

(defonce sync-handle
  (atom nil))

(defn- sync-running?
  []
  (some? @sync-handle))


(defn- attach-sync-logs!
  [handle dbname]
  (let [payload (fn [data] (assoc data :db dbname))]
    (doto handle
      (.on "change" (fn [info] (log/debug :sync/change (payload {:info info}))))
      (.on "paused" (fn [error] (log/debug :sync/paused (payload {:error error}))))
      (.on "active" (fn [] (log/debug :sync/active (payload {}))))
      (.on "denied" (fn [error] (log/warn :sync/denied (payload {:error error}))))
      (.on "error" (fn [error] (log/error :sync/error (payload {:error error})))))))


(defn- start-sync!
  [db]
  (when (and db (.. js/self -navigator -onLine) (not (sync-running?)))
    (let [dbname (.-name db)
          handle (db/sync db {:live true :retry true})]
      (attach-sync-logs! handle dbname)
      (reset! sync-handle handle)
      handle)))


(defn- stop-sync!
  []
  (when-let [handle @sync-handle]
    (.cancel handle)
    (reset! sync-handle nil)))


(def precache-url
  ["/"
   "/manifest.json"
   "/css/styles.css"
   "/fonts/Nunito/nunito-v26-cyrillic_latin-regular.woff2"
   "/fonts/Nunito/nunito-v26-cyrillic_latin-500.woff2"
   "/fonts/Nunito/nunito-v26-cyrillic_latin-700.woff2"
   "/fonts/Nunito/nunito-v26-cyrillic_latin-800.woff2"
   "/js/htmx/htmx.min.js"
   "/js/htmx/idiomorph-ext.min.js"
   "/favicon.ico"
   "/icons.svg"
   "/js/app/shared.js"
   "/js/app/sw-loader.js"])


(js/self.addEventListener
 "install"
 (fn [^InstallEvent event]
   (log/debug :event/install event)
   (js/self.skipWaiting)
   (..
    event
    (waitUntil
     (p/let [cache (js/caches.open "resources")]
       (.addAll cache precache-url))))))


(js/self.addEventListener
 "activate"
 (fn [event]
   (log/debug :event/activate event)
   (..
    event
    (waitUntil
     (js/self.clients.claim)))))


(js/self.addEventListener
 "online"
 (fn [_event]
   (log/debug :event/online "Starting sync")
   (start-sync! application/db)))


(js/self.addEventListener
 "offline"
 (fn [_event]
   (log/debug :event/offline "Stopping sync")
   (stop-sync!)))


(def session-interceptor
  {:name  ::session-interceptor
   :enter (fn [ctx]
            (p/let [{:keys [user-id]} (db/get application/db "user-data")]
              (assoc-in ctx [:request :session] {:user-id user-id})))})


(def ring-handler
  (http/ring-handler
   (http/router
    application/ui-routes

    ;; {:layout-fn nil} -- explicitly disables default layout
    {:data {:interceptors [session-interceptor
                           (parameters/parameters-interceptor)
                           (keyword-parameters/keyword-parameters-interceptor)
                           (hiccup/interceptor {:layout-fn nil})]}})

   ;; the default handler
   (constantly {:status 404, :body ""})

   ;; interceptor queue executor
   {:executor sieppari/executor}))


(defn- request->ring
  [request]
  (let [request (.clone request)
        url-object (js/URL. (.-url request))
        ;; https://github.com/ring-clojure/ring/wiki/Concepts#requests
        headers (-> request .-headers .entries js/Object.fromEntries js->clj)]
    {:server-port    (.-port url-object)
     :server-name    (.-hostname url-object)
     :uri            (.-pathname url-object)
     :url            (.-url request)
     :query-string   (utils/non-blank (.-search url-object))
     :scheme         (-> url-object .-protocol (str/replace #":" "") keyword)
     :request-method (-> request .-method str/lower-case keyword)
     :headers        headers
     :body           (.-body request)

     ;; Custom non-ring fields
     :js/request     request}))

(defn local-routes
  [request]
  (let [ring-request (request->ring request)]
    (p/let [ring-response (ring-handler ring-request)]
      (when-not (= (:status ring-response) 404)
        ;; https://developer.mozilla.org/en-US/docs/Web/API/Response/Response
        (js/Response.
         (:body ring-response)
         (clj->js
          (select-keys ring-response [:status :headers])))))))


(js/self.addEventListener
 "fetch"
 (fn [^FetchEvent event]
   (log/debug :event/fetch event)
   (..
    event
    (respondWith
     (p/let [request (.. event -request)]
       (p/-> (js/caches.match request)
             (or (local-routes request))
             (or (js/fetch request))))))))


(defn- handle-claim-client!
  [event]
  (p/do
    (log/debug :event/message ["Claim clients"])
    (js/self.clients.claim)
    (log/debug :event/message ["Notify sender client"])
    (.. event -source (postMessage #js {:type "page-controlled"}))))

(defn- handle-register-user!
  [event]
  (p/let [user-data (db/get application/db "user-data")]
    (log/debug :message/event ["User Data" user-data])
    (when (nil? user-data)
      (let [user-id (.. event -data -value)]
        (log/debug :event/message ["Register user" user-id])
        (db/insert application/db {:user-id user-id} "user-data")))
    (start-sync! application/db)
    (log/debug :event/message ["Notify sender client" (.. event -source -id)])
    (.. event -source (postMessage #js {:type "user-registered"}))))

(js/self.addEventListener
 "message"
 (fn [^ExtendableMessageEvent event]
   (log/debug :event/message ["Received a message from a client" event])
   (case (.. event -data -type)
     "claim-client" (handle-claim-client! event)
     "register-user" (handle-register-user! event))))


#_{:clojure-lsp/ignore [:clojure-lsp/unused-public-var]}
(defn ^:dev/after-load update-registration []
  (log/debug :dev/after-load "Update service worker")
  (js/self.registration.update))
