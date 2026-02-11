(ns application
  (:require
   [dbs :as dbs]
   [dictionary :as dictionary]
   [dictionary-sync :as dictionary-sync]
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


(defn- app-shell
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
     [:a.app-shell__logo
      {:href        "/home"
       :hx-get      "/home"
       :hx-push-url "true"
       :hx-swap     "innerHTML"
       :hx-target   "#app"}
      "Sprecha"]
     [:div#loader.app-shell__loader
      [:div.app-shell__loader-list {:style {:--items-count 1}}
       [:div.app-shell__loader-text "Загружаем..."]]]
     [:div#app body]]]))


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
                (assoc-in ctx [:response :html/layout] app-shell))))})


(def db-interceptor
  "Injects database instance into request."
  {:name  ::db-interceptor
   :enter (fn [ctx]
            (update ctx
                    :request       assoc
                    :user-db       (dbs/user-db)
                    :device-db     (dbs/device-db)
                    :dictionary-db (dbs/dictionary-db)))})


(def dictionary-sync-interceptor
  "Retries dictionary sync if it failed (e.g. server was unavailable)."
  {:name  ::dictionary-sync-interceptor
   :enter (fn [ctx]
            (when-not (dictionary-sync/loaded?)
              (dictionary-sync/ensure-loaded!))
            ctx)})


(def ui-routes
  [[""
    ["/home"
     {:get (fn [{:keys [user-db]}]
             (p/let [word-count (vocabulary/count user-db)]
               {:html/body (views.home/page {:word-count word-count})}))}]

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
                                (views.word/page {:empty? (zero? total)}))
                   :status    200})))

      :post (fn [{:keys [user-db params]}]
              (let [{:keys [value translation]} params
                    result (domain.vocabulary/validate-new-word value translation)]
                (if-let [error (:error result)]
                  {:html/body (views.word/validation-error-inputs error)
                   :status    400}
                  (p/let [word-id (vocabulary/add! user-db value translation)
                          total   (vocabulary/count user-db)]
                    (examples/create-fetch-task! word-id)
                    {:html/body (views.home/state-marker total {:hx-swap-oob "outerHTML"})
                     :status    201}))))}]

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
                         :html/body (views.word/page {:empty? true})
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
                   :html/body (views.home/page {:word-count word-count})
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

    {:data {:interceptors [db-interceptor
                           dictionary-sync-interceptor
                           (parameters/parameters-interceptor)
                           (keyword-parameters/keyword-parameters-interceptor)
                           (hiccup/interceptor {:layout-fn nil})
                           page-layout-interceptor]}})

   (constantly {:status 404 :body ""})

   {:executor sieppari/executor}))
