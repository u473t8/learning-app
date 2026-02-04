(ns application
  (:require
   [db-migrations :as db-migrations]
   [dbs :as dbs]
   [dictionary :as dictionary]
   [domain.vocabulary :as domain.vocabulary]
   [examples :as examples]
   [hiccup :as hiccup]
   [lesson :as lesson]
   [presenter.lesson :as presenter.lesson]
   [promesa.core :as p]
   [reitit.http :as http]
   [reitit.http.interceptors.keyword-parameters :as keyword-parameters]
   [reitit.http.interceptors.parameters :as parameters]
   [reitit.interceptor.sieppari :as sieppari]
   [views.dictionary :as views.dictionary]
   [views.home :as views.home]
   [views.lesson :as views.lesson]
   [views.vocabulary :as views.word]
   [vocabulary :as vocabulary]))


;;
;; Layout
;;


(defn- page-layout
  [{:html/keys [head body]}]
  (list
   [:!DOCTYPE "html"]
   [:html
    [:head
     [:meta {:charset "UTF-8"}]
     [:meta
      {:name    "viewport"
       :content "width=device-width, initial-scale=1, interactive-widget=resizes-content"}]
     [:title "Sprecha"]
     [:link {:rel "icon" :href "/favicon.ico"}]
     [:link {:rel "manifest" :href "/manifest.json"}]
     [:link {:rel "stylesheet" :href "/css/styles.css"}]
     [:script {:src "/js/htmx/htmx.min.js" :defer true}]
     [:script {:src "/js/word-autocomplete.js" :defer true}]
     head]
    [:body
     [:a.app-logo
      {:href        "/home"
       :hx-get      "/home"
       :hx-push-url "true"
       :hx-swap     "innerHTML"
       :hx-target   "#app"
       :aria-label  "Sprecha"}
      "Sprecha"]
     [:div#loader.loader
      [:div.loader__list {:style {:--items-count 1}}
       [:div.loader__text "Загружаем..."]]]
     [:div#app body]]]))


(defn- request-path
  [request]
  (let [uri (:uri request)
        qs  (:query-string request)]
    (if (seq qs)
      (str uri "?" qs)
      uri)))


(defn- migration-shell
  [path {:keys [status]}]
  [:div.migration-shell
   {:hx-get     path
    :hx-trigger "every 2s"
    :hx-target  "#app"
    :hx-swap    "innerHTML"}
   [:p "Updating data..."]
   (when (= status :failed)
     [:p "Retrying migration soon."])])


;;
;; Routes
;;


(def page-layout-interceptor
  {:name  ::page-layout-interceptor
   :leave (fn [ctx]
            (let [request  (:request ctx)
                  response (:response ctx)]
              (if (or (-> request :headers (get "hx-request"))
                      (nil? (:html/body response)))
                ctx
                (assoc-in ctx [:response :html/layout] page-layout))))})


(def db-interceptor
  "Injects database instance into request."
  {:name  ::db-interceptor
   :enter (fn [ctx]
            (if (not= :done (:migration/status ctx))
              ctx
              (update ctx
                      :request       assoc
                      :user-db       (dbs/user-db)
                      :device-db     (dbs/device-db)
                      :dictionary-db (dbs/dictionary-db))))})


(def migration-start-interceptor
  {:name  ::migration-start-interceptor
   :enter (fn [ctx]
            (db-migrations/ensure-migrated!)
            (assoc ctx :migration/status (db-migrations/migration-status)))})


(def migration-shell-interceptor
  {:name  ::migration-shell-interceptor
   :enter (fn [ctx]
            (let [status (or (:migration/status ctx)
                             (db-migrations/migration-status))]
              (if (= :done status)
                ctx
                (let [path (request-path (:request ctx))]
                  (-> ctx
                      ;; Short-circuit remaining interceptors
                      (update :queue empty)
                      (assoc :response
                             {:status    200
                              :html/body (migration-shell path {:status status})}))))))})


(def ui-routes
  [[""
    ["/home"
     {:get (fn [{:keys [user-db]}]
             (p/let [word-count (vocabulary/count user-db)]
               {:html/body (views.home/home {:word-count word-count})}))}]

    ["/dictionary-entries"
     {:get (fn [{:keys [dictionary-db params]}]
             (-> (p/let [{:keys [suggestions prefill]}
                         (dictionary/suggest dictionary-db (:value params))]
                   {:html/body (views.dictionary/suggestions suggestions prefill)
                    :status    200})
                 (p/catch
                   (fn [_err]
                     {:html/body (views.dictionary/suggestions [] nil)
                      :status    200}))))}]

    ["/words"
     {:head (fn [{:keys [user-db]}]
              (p/let [total (vocabulary/count user-db)]
                {:status  200
                 :headers {"X-Total-Count" (str total)}}))

      :get  (fn [{:keys [user-db params headers]}]
              (let [{:keys [pages search] :or {pages 1}} params
                    page-size   10
                    limit       (-> pages js/parseInt (* page-size) inc)
                    htmx-target (get headers "hx-target")]
                (p/let [words (vocabulary/list user-db {:limit limit :search search})
                        total (vocabulary/count user-db)]
                  {:headers   {"X-Total-Count" (str total)}
                   :html/body (if (= htmx-target "word-list")
                                (views.word/word-list
                                 {:pages      pages
                                  :search     search
                                  :show-more? (> total limit)
                                  :words      words})
                                (views.word/words-page {:empty? (zero? total)}))
                   :status    200})))

      :post (fn [{:keys [user-db params]}]
              (let [{:keys [value translation]} params
                    result (domain.vocabulary/validate-new-word value translation)]
                (if-let [error (:error result)]
                  {:html/body (views.word/validation-error-inputs error)
                   :status    400}
                  (p/let [word-id (vocabulary/add! user-db value translation)]
                    (examples/create-fetch-task! word-id)
                    {:status 201}))))}]

    ["/words-count"
     {:get (fn [{:keys [user-db]}]
             (p/let [total (vocabulary/count user-db)
                     class (if (zero? total) "word-count--empty" "word-count--ready")]
               {:html/body [:span#word-count {:class class} (str total)]
                :status    200}))}]

    ["/words/:id"
     {:get    (fn [{:keys [user-db path-params params]}]
                (let [word-id (:id path-params)
                      edit?   (= "true" (:edit params))]
                  (p/let [word (vocabulary/get user-db word-id)]
                    (if word
                      {:html/body (views.word/word-list-item word {:editing? edit?})
                       :status    200}
                      {:status 404}))))

      :put    (fn [{:keys [user-db path-params params]}]
                (let [word-id (:id path-params)
                      {:keys [value translation]} params
                      result  (domain.vocabulary/validate-word-update
                               {:word-id word-id :value value :translation translation})]
                  (if-let [error (:error result)]
                    {:status 400 :body error}
                    (p/let [word (vocabulary/update! user-db word-id value translation)]
                      (if word
                        {:html/body (views.word/word-list-item word)
                         :status    200}
                        {:status 404})))))

      :delete (fn [{:keys [user-db path-params]}]
                (let [word-id (:id path-params)]
                  (p/do
                    (vocabulary/delete! user-db word-id)
                    (p/let [total (vocabulary/count user-db)]
                      (if (zero? total)
                        {:headers   {"HX-Retarget" "#app" "HX-Reswap" "innerHTML"}
                         :html/body (views.word/words-page {:empty? true})
                         :status    200}
                        {:status 200})))))}]

    ["/lesson"
     {:get    (fn [{:keys [user-db device-db]}]
                (p/let [{:keys [lesson-state error]} (lesson/ensure! user-db device-db {:trial-selector :random})]
                  (cond
                    lesson-state
                    {:html/body (views.lesson/page lesson-state) :status 200}

                    (= error :no-words-available)
                    {:html/body (views.lesson/empty-state) :status 200}

                    :else
                    (throw (ex-info "Failed to create lesson" {:error error})))))

      :delete (fn [{:keys [user-db device-db]}]
                (p/let [_ (lesson/finish! device-db)
                        word-count (vocabulary/count user-db)]
                  {:headers   {"HX-Push-Url" "/home"}
                   :html/body (views.home/home {:word-count word-count})
                   :status    200}))}]

    ["/lesson/answer"
     {:post (fn [{:keys [user-db device-db params]}]
              (let [answer (:answer params)]
                (p/let [{:keys [lesson-state]} (lesson/check-answer! user-db device-db answer)]
                  (if lesson-state
                    (let [{:keys [challenge progress footer]} (presenter.lesson/page-props lesson-state)]
                      {:html/body (list
                                   (views.lesson/footer footer)
                                   (views.lesson/challenge challenge {:hx-swap-oob "true"})
                                   (views.lesson/progress progress {:hx-swap-oob "true"}))
                       :status    200})
                    {:status 404}))))}]

    ["/lesson/next"
     {:post (fn [{:keys [device-db]}]
              (p/let [{:keys [lesson-state]} (lesson/advance! device-db)]
                (if lesson-state
                  (let [{:keys [challenge progress]}
                        (presenter.lesson/page-props lesson-state)]
                    {:html/body (list
                                 (views.lesson/input)
                                 (views.lesson/challenge challenge {:hx-swap-oob "true"})
                                 (views.lesson/progress progress {:hx-swap-oob "true"}))
                     :status    200})
                  {:status 404})))}]]])


(def ring-handler
  (http/ring-handler
   (http/router
    ui-routes

    {:data {:interceptors [migration-start-interceptor
                           db-interceptor
                           (parameters/parameters-interceptor)
                           (keyword-parameters/keyword-parameters-interceptor)
                           (hiccup/interceptor {:layout-fn nil})
                           page-layout-interceptor
                           migration-shell-interceptor]}})

   (constantly {:status 404 :body ""})

   {:executor sieppari/executor}))
