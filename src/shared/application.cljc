(ns application
  (:require
   [clojure.string :as str]
   [db :as db]
   [hiccup :as hiccup]
   [layout :as layout]
   [promesa.core :as p]
   [reitit.http :as http]
   [reitit.http.interceptors.parameters :as parameters]
   [reitit.interceptor.sieppari :as sieppari]
   [reitit.ring :as ring]
   [vocabulary :as vocabulary]
   [utils :as utils]))


(def db (db/use "app-db"))


(defn word-list-item
  [{:keys [id value translation retention-level]}]
  (let [item-id (str "word-" id)]
    [:li.list-item.word-item
     {:id item-id
      :hx-on:click "htmx.toggleClass(this, 'word-item--selected')"}
     [:form
      {:hx-put (str "/words?word-id=" id)
       :hx-trigger "change"
       :hx-swap "outerHTML"
       :hx-target (str "#" item-id)}
      [:input.word-item__value
       {:name "value"
        :autocapitalize "off"
        :autocomplete "off"
        :autocorrect "off"
        :hx-on:click "event.stopPropagation()"
        :lang "de"
        :value value}]
      [:input.word-item__translation
       {:name "translation"
        :autocapitalize "off"
        :autocomplete "off"
        :autocorrect "off"
        :hx-on:click "event.stopPropagation()"
        :lang "ru"
        :value translation}]]
     [:button.word-item__remove-button
      {:hx-delete (str "/words?id=" id)
       :hx-confirm (str "Вы уверены, что хотите удалить слово " value "?")
       :hx-trigger "click"
       :hx-swap "delete"
       :hx-target (str "#" item-id)}
      "УДАЛИТЬ"]
     [:div.word-item__learning-progress
      {:title (str (int retention-level) "%")
       :style {:background-color (utils/prozent->color retention-level)}}]]))


(defn- word-list
  [{:keys [pages search show-more? words]
    :or   {pages 0, show-more? true }}]
  [:ul.words__list.list
   {:id "word-list"}
   (if (seq words)
     (list
      (for [word words]
        (word-list-item word))
      (when show-more?
        [:li.list-item.word-item.word-item--add-new-word
         {:hx-get     (utils/build-url "/words?limit=5" {:pages (inc pages), :search search})
          :hx-swap    "outerHTML"
          :hx-trigger "click"}
         [:b
          "Загрузить больше"]
         [:span.arrow-down-icon]]))
     [:div "Ничего не найдено"])])


(def home
  [:div.home

   [:div#splash.home__splash]

   ;; Header with buttons
   [:section.home__header
    {:id "header"}
    [:button.big-button.green-button
     {:hx-get       "/lesson"
      :hx-indicator "#loader"
      :hx-push-url  "true"}
     "НАЧАТЬ"]]

   ;; Words list

   [:section.home__words.words
    {:hx-push-url "false"}
    [:form.words__search
     [:div.input
      [:span.input__search-icon]
      [:input.input__input-area.input__input-area--icon
       {:autocomplete "off"
        :hx-get       "/words"
        :placeholder  "Поиск слова"
        :hx-target    "#word-list"
        :hx-swap      "innerHTML"
        :hx-trigger   "input changed delay:500ms, keyup[key=='Enter']"
        :name         "search"}]]]
    [:hr
     {:style {:width "100%", :margin 0}}]

    [:div
     {:hx-get     "/words"
      :hx-trigger "load"}]]

   [:hr {:style {:width "100%", :margin 0}}]

   ;; Footer for adding words
   [:form.new-word-form
    {:hx-on:htmx:after-request "if(event.detail.successful) {this.reset(); htmx.find('#new-word-value').focus()}; console.log(event)"
     :hx-post                  "/words"
     :hx-push-url              "false"
     :hx-swap                  "afterbegin"
     :hx-target                "#word-list"}
    [:label.new-word-form__label
     "Новое слово"
     [:input.new-word-form__input
      {:id             "new-word-value"
       :autocapitalize "off"
       :autocomplete   "off"
       :autocorrect    "off"
       :autofocus      "true"
       :hx-on:change   "htmx.find('#new-word-translation').focus()"
       :lang           "de"
       :name           "value"}]]
    [:label.new-word-form__label
     "Перевод"
     [:input.new-word-form__input
      {:id             "new-word-translation"
       :autocapitalize "off"
       :autocomplete   "off"
       :autocorrect    "off"
       :lang           "ru"
       :name           "translation"}]]
    [:button.big-button.blue-button
     {:type "submit"}
     "ДОБАВИТЬ"]]])


(def session-interceptor
  {:name  ::session-interceptor
   :enter (fn [ctx]
            (p/let [{:keys [user-id]} (db/get db "user-data")]
              (assoc-in ctx [:request :session] {:user-id user-id})))})


(def block-non-htmx-interceptor
  {:name  ::block-non-htmx-interceptor
   :enter (fn [ctx] ctx)})


(def words-interceptor
  {:name  ::words-interceptor
   :enter (fn [ctx]
            (p/let [user-id (-> ctx :request :session :user-id)
                    words   (vocabulary/words user-id)]
              (assoc-in ctx [:request :words] words)))})


(def add-word-interceptor
  {:name ::add-word-interceptor
   :enter (fn [ctx]
            (p/let [{:keys [value translation]} (-> ctx :request :params)
                    user-id                     (-> ctx :request :session :user-id)

                    word-id (vocabulary/add-word user-id value translation)]
              (assoc-in ctx [:request :params :word-id] word-id)))})


(def change-word-interceptor
  {:name ::change-word-interceptor
   :enter (fn [ctx]
            (let [{:keys [word-id value translation]} (-> ctx :request :params)
                  user-id                             (-> ctx :request :session :user-id)]
              (p/let [word (vocabulary/change-word user-id word-id value translation)]
                (assoc-in ctx [:request :vocabulary/word] word))))})


(def delete-word-interceptor
  {:name ::delete-word-interceptor
   :enter (fn [ctx]
            (let [user-id (-> ctx :request :session :user-id)
                  word-id (-> ctx :request :params :id)]
              (p/do
                (vocabulary/delete-word user-id word-id)
                ctx)))})

(def xxx
  [["/home"
      {:get (fn [{:keys [session]}]
              {:html/layout layout/page
               :html/head   [:meta {:name "user-id" :content (:user-id session)}]
               :html/body   home
               :status      200})}]

     ["/words"
      {:interceptors [block-non-htmx-interceptor]
       :get {:interceptors [words-interceptor]
             :handler      (fn [{:keys [pages search words headers] :as request}]
                             (if (headers "hx-request")
                               {:html/body (word-list {:pages pages, :search search, :words words})
                                :status    200}
                               {:status 403}))}

       :post {:interceptors [add-word-interceptor]
              :handler      (fn [request]
                              (let [{:keys [value translation word-id]} (:params request)]
                                (if (or
                                     (not (string? value)) (str/blank? value)
                                     (not (string? translation)) (str/blank? translation))
                                  {:html/body (cond-> (list)
                                                (or (not (string? value)) (str/blank? value))
                                                (conj
                                                 [:input.new-word-form__input.new-word-form__input--error
                                                  {:hx-on:change "htmx.find('#new-word-translation').focus()"
                                                   :hx-swap-oob  "true"
                                                   :id           "new-word-value"
                                                   :name         "value"}])

                                                (or (not (string? translation)) (str/blank? translation))
                                                (conj
                                                 [:input.new-word-form__input.new-word-form__input--error
                                                  {:id          "new-word-translation"
                                                   :hx-swap-oob "true"
                                                   :name        "translation"}]))
                                   :status 400}
                                  {:html/body (word-list-item
                                               {:id              word-id
                                                :value           value
                                                :translation     translation
                                                :retention-level 100})
                                   :status 200})))}

       :put  {:interceptors [change-word-interceptor]
              :handler      (fn [request]
                              (let [{:keys [id value translation retention-level]} (:vocabulary/word request)]
                                {:status    200
                                 :html/body (word-list-item
                                             {:id              id
                                              :value           value
                                              :translation     translation
                                              :retention-level retention-level})}))}
       :delete {:interceptors [delete-word-interceptor]
                :handler      (fn [_] {:status 200})}}]

     ["/lesson"
      {:get (fn [_]
              {:html/body [:h1 "New Lesson"]
               :status    200})}]])


(def routes
  (http/ring-handler
   (http/router
    xxx

    ;; {:layout-fn nil} -- explicitly disables default layout
    {:data {:interceptors [(parameters/parameters-interceptor)
                           session-interceptor
                           (hiccup/interceptor {:layout-fn nil})]}})

   ;; the default handler
   (constantly {:status 404, :body ""})

   ;; interceptor queue executor
   {:executor sieppari/executor}))



(comment
  (require
   #?(:clj  '[clojure.pprint :as pprint]
      :cljs '[cljs.pprint :as pprint]))

  (def test-interceptor
    {:enter
     (fn [ctx]
       (p/let [db   (db/use "userdb-1")
               info (db/info db)]
         (assoc-in ctx [:request :info] info)))})

  (def test-app
    (http/ring-handler
     (http/router
      ["/"
       {:get {:interceptors [test-interceptor]
              :handler (fn [request]
                         {:status 200
                          :body (:info request)})}}])

     ;; the default handler
     (ring/create-default-handler)

     ;; executor
     {:executor sieppari/executor}))

  (p/let [res (test-app {:request-method :get, :uri "/"})]
    (pprint/pprint res)))

