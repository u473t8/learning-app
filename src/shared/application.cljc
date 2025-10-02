(ns application
  (:require
   [clojure.string :as str]
   [db :as db]
   [layout :as layout]
   [promesa.core :as p]
   [utils :as utils]
   [vocabulary :as vocabulary]))


(def db (db/use "app-db"))


;;
;; Home View
;;

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


;;
;; Lesson View
;;

(defn lesson-progress
  [progress]
  [:div.progress-bar-value
   {:id "progress"
    :style {"--__internal__progress-bar-value" (str progress "%")}}])


(defn lesson-footer
  ;; The function either receives no arguments or expects all three keys.
  ;; This structure feels awkward, but I’m unsure of a better alternative.
  [& {:keys [challenge-passed? challenge-answer answer-structure passed-all-challenges?]}]
  [:div.learning-session__footer
   {:id "footer"
    :class (cond
             (true? challenge-passed?) "learning-session__footer--success"
             (false? challenge-passed?) "learning-session__footer--error")}
   (when true #_challenge-answer
         [:div.challenge-answer
          [:h2.challenge-answer__header
           "Правильный ответ:"]
          [:div.challenge-answer__body
           #_(for [word (re-seq #"\w+|\p{Punct}|\s+" challenge-answer)]
             (if-let [word-info (challenge-structure word)])
             [:span word])]])
   (when challenge-passed?
     [:h2.challenge-success "Отлично!"])
   (cond
     passed-all-challenges?
     [:button.big-button.green-button
      {:autofocus "true"
       :hx-delete "/lesson"
       :hx-indicator "#loader"}
      [:div.big-button__loader.htmx-indicator
       {:id "loader"}]
      [:span.big-button__label
       "ЗАКОНЧИТЬ"]]

     (some? challenge-passed?)
     [:button.big-button
      {:autofocus "true"
       :class (if challenge-passed?
                "green-button"
                "red-button")
       :hx-indicator "#loader"
       :hx-post "/lesson/advance"
       :hx-select "#footer"
       :hx-select-oob "#challenge"
       :hx-swap "outerHTML"
       :hx-target "#footer"
       :hx-trigger "click"}
      [:div.big-button__loader.htmx-indicator
       {:id "loader"}]
      [:span.big-button__label
       "ДАЛЕЕ"]]

     :else
     [:button.big-button.green-button
      {:form "challenge-form"
       :type "submit"}
      [:div.big-button__loader.htmx-indicator
       {:id "loader"}]
      [:span.big-button__label
       "ПРОВЕРИТЬ"]])])


(defn challenge-view
  [current-challenge]
  [:div.learning-session__challenge
   {:id "challenge"
    :hx-on:htmx:load "htmx.closest(this, 'form').reset(); htmx.find('textarea').focus()"}
   [:div.text-plate
    (:translation current-challenge)]])


(defn lesson:view
  [{:keys [progress current-challenge]}]
  [:div.learning-session
   {:id "learning-session"}

   [:div.learning-session__header
    [:button.cancel-button
     {:hx-trigger "click"
      :hx-delete "/lesson"}
     [:span.close-icon]]
    [:div.progress-bar
     (lesson-progress progress)]]

   [:form.learning-session__body
    {:id "challenge-form"
     :hx-patch "/lesson"
     :hx-select-oob "#footer, #progress"
     :hx-disabled-elt "textarea, button[type='submit']"
     :hx-indicator "#loader"
     :hx-swap "none"}
    (challenge-view current-challenge)
    [:div.learning-session__user-input
     {:hx-get "/lesson/challenge/input"
      :hx-swap "innerHTML"
      :hx-trigger "new-challenge from:body"}
     [:textarea.text-input
      {:id "user-answer"
       :autocapitalize "off"
       :autocomplete "off"
       :autocorrect "off"
       :autofocus "true"
       :hx-on:keydown "if (event.key === 'Enter' && !event.shiftKey) {event.preventDefault();  this.form.requestSubmit()}"
       :lang "de"
       :name "user-answer"
       :placeholder "Напишите на немецком"
       :spellcheck "false"}]]]
   (lesson-footer)])


;;
;; Routes
;;

(def block-non-htmx-interceptor
  {:name  ::block-non-htmx-interceptor
   :enter (fn [ctx]
            (let [htmx-request? (-> ctx :request :headers (get "hx-request"))]
              (cond-> ctx
                (not htmx-request?) (assoc :queue [], :response {:status 403}))))})


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

(def ui-routes
  [["/home"
    {:get (fn [{:keys [session]}]
            {:html/layout layout/page
             :html/head   [:meta {:name "user-id" :content (:user-id session)}]
             :html/body   home
             :status      200})}]

   ["/words"
    {:interceptors [block-non-htmx-interceptor]
     :get {:interceptors [words-interceptor]
           :handler      (fn [{:keys [pages search words]}]
                           {:html/body (word-list {:pages pages, :search search, :words words})
                            :status    200})}

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


(comment
  (require
   #?(:clj  '[clojure.pprint :as pprint]
      :cljs '[cljs.pprint :as pprint])
            '[reitit.ring :as ring]
            '[reitit.interceptor.sieppari :as sieppari]
            '[reitit.http :as http])

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
