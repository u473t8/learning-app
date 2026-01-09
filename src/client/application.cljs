(ns application
  (:require
   [clojure.string :as str]
   [examples :as examples]
   [hiccup :as hiccup]
   [promesa.core :as p]
   [reitit.http :as http]
   [reitit.http.interceptors.keyword-parameters :as keyword-parameters]
   [reitit.http.interceptors.parameters :as parameters]
   [reitit.interceptor.sieppari :as sieppari]
   [utils :as utils]
   [vocabulary :as vocabulary]))

;;
;; Home View
;;

(defn word-list-item
  [{:keys [id editing? retention-level translation value]}]
  (let [item-id (str "word-" id)
        retention-text (cond
                         (>= retention-level 80) "Отлично запомнено"
                         (>= retention-level 50) "Хорошо изучено"
                         (>= retention-level 20) "Нужно повторить"
                         :else "Новое слово")]
    [:li.word-item
     {:class (when editing? "word-item--editing")
      :id item-id}
     (if editing?
       ;; Edit mode - show inputs
       [:form.word-item__form
        {:hx-put (str "/words?word-id=" id)
         :hx-trigger "submit"
         :hx-swap "outerHTML"
         :hx-target (str "#" item-id)}
        [:div.word-item__inputs
         [:input.word-item__input
          {:name "value"
           :autocapitalize "off"
           :autocomplete "off"
           :autocorrect "off"
           :autofocus true
           :lang "de"
           :placeholder "Слово"
           :value value}]
         [:span.word-item__arrow "→"]
         [:input.word-item__input
          {:name "translation"
           :autocapitalize "off"
           :autocomplete "off"
           :autocorrect "off"
           :lang "ru"
           :placeholder "Перевод"
           :value translation}]]
        [:div.word-item__actions
         [:button.word-item__save {:type "submit"} "Сохранить"]
         [:button.word-item__cancel
          {:type "button"
           :hx-get (str "/words/" id)
           :hx-target (str "#" item-id)
           :hx-swap "outerHTML"}
          "Отмена"]
         [:button.word-item__delete
          {:type "button"
           :hx-delete (str "/words?id=" id)
           :hx-confirm (str "Удалить «" value "»?")
           :hx-trigger "click"
           :hx-swap "delete"
           :hx-target (str "#" item-id)}
          "Удалить"]]]
       ;; Display mode - show text, tap to edit
       [:div.word-item__display
        {:hx-get (str "/words/" id "?edit=true")
         :hx-target (str "#" item-id)
         :hx-swap "outerHTML"}
        [:div.word-item__retention
         {:style {:background-color (utils/prozent->color retention-level)}
          :title (str retention-text " (" (int retention-level) "%)")}]
        [:span.word-item__value {:lang "de"} value]
        [:span.word-item__arrow "→"]
        [:span.word-item__translation {:lang "ru"} translation]
        [:span.word-item__chevron "›"]])]))

(defn- word-list
  [{:keys [pages search show-more? words]
    :or {pages 0, show-more? true}}]
  [:ul.word-list
   {:id "word-list"}
   (if (seq words)
     (list
      (for [word words]
        (word-list-item word))
      (when show-more?
        [:li.word-list__load-more
         {:hx-get (utils/build-url "/words?limit=5" {:pages (inc pages), :search search})
          :hx-swap "outerHTML"
          :hx-trigger "click"}
         "Загрузить ещё ↓"]))
     ;; Empty state
     [:li.word-list__empty
      {:id "word-list-empty"}
      (if (utils/non-blank search)
        [:div.empty-state
         [:p.empty-state__text "Ничего не найдено"]
         [:p.empty-state__hint "Попробуйте другой запрос"]]
        [:div.empty-state
         [:p.empty-state__text "Слов пока нет"]
         [:p.empty-state__hint "Добавьте первое слово на главной странице"]
         [:button.empty-state__cta
          {:hx-get "/home"
           :hx-push-url "true"
           :hx-swap "innerHTML"
           :hx-target "#app"}
          "Добавить слово"]])])])

(defn page-layout
  [{:html/keys [head body]}]
  [:html
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1, interactive-widget=resizes-content"}]
    [:title "Sprecha"]
    [:link {:rel "icon" :href "/favicon.ico"}]
    [:link {:rel "manifest" :href "/manifest.json"}]
    [:link {:rel "stylesheet" :href "/css/styles.css"}]
    [:script {:src "/js/htmx/htmx.min.js" :defer true}]
    [:script {:src "/js/htmx/idiomorph-ext.min.js" :defer true}]
    head]
   [:body {:hx-ext "morph"}
    [:div#app body]]])

(defn home
  [{:keys [word-count] :or {word-count 0}}]
  (let [count-class (if (zero? word-count) "word-count--empty" "word-count--ready")]
    [:div.home
     [:header.home__hero
      [:div.home__hero-text
       [:h1.home__title
        "Sprecha"]
       [:p.home__subtitle
        "Быстро добавляйте слова и учите немецкий даже без сети."]]]

     [:section.home__grid
      [:div.home__add-stack
       [:div.home__footer-actions
        [:button.home__lesson-button.big-button.green-button
         {:hx-get "/lesson"
          :hx-indicator "#loader"
          :hx-push-url "true"}
         "НАЧАТЬ УРОК"]]
       [:section.home__panel.home__panel--add
        [:div.home__add-header
         [:h2.home__panel-title
          "Быстрое добавление"]
         [:button.home__words-button
          {:hx-get "/words"
           :hx-push-url "true"
           :hx-swap "innerHTML"
           :hx-target "#app"}
          "Список слов"]]
        [:form.new-word-form.new-word-form--quick
         {:hx-on:submit "if(this.dataset.submitting) {event.preventDefault(); return;} this.dataset.submitting = 'true'"
          :hx-on:htmx:after-request "if(event.detail.successful) {this.reset(); htmx.find('#new-word-value').focus(); htmx.trigger(document.body, 'words-changed')} delete this.dataset.submitting"
          :hx-post "/words"
          :hx-push-url "false"
          :hx-swap "none"}
         [:div.new-word-form__row
          [:input.new-word-form__input.new-word-form__input--quick
           {:id "new-word-value"
            :autocapitalize "off"
            :autocomplete "off"
            :autocorrect "off"
            :autofocus "true"
            :hx-on:change "htmx.find('#new-word-translation').focus()"
            :lang "de"
            :name "value"
            :placeholder "Новое слово"}]
          [:span.new-word-form__arrow
           "→"]
          [:input.new-word-form__input.new-word-form__input--quick
           {:id "new-word-translation"
            :autocapitalize "off"
            :autocomplete "off"
            :autocorrect "off"
            :lang "ru"
            :name "translation"
            :placeholder "Перевод"}]]
         [:button.new-word-form__submit.big-button
          {:type "submit"}
          "ДОБАВИТЬ"]]]]
      [:div.home__words-probe
       {:hx-get "/word-count"
        :hx-push-url "false"
        :hx-trigger "load, words-changed from:body"
        :hx-target "#word-count"
        :hx-swap "outerHTML"}
       [:span#word-count {:class count-class} (str word-count)]]]]))

(def words-page
  [:div.words-page
   [:header.words-page__header
    [:button.words-page__back
     {:hx-get "/home"
      :hx-push-url "true"
      :hx-swap "innerHTML"
      :hx-target "#app"}
     "← Назад"]
    [:h1.words-page__title
     "Мои слова"]]
   [:form.words-page__search
    [:div.input
     [:span.input__search-icon]
     [:input.input__input-area.input__input-area--icon
      {:autocomplete "off"
       :hx-get "/words"
       :placeholder "Поиск"
       :hx-target "#word-list"
       :hx-swap "innerHTML"
       :hx-trigger "input changed delay:500ms, keyup[key=='Enter']"
       :name "search"}]]]
   [:div.words-page__list
    {:hx-get "/words"
     :hx-trigger "load"
     :hx-target "#word-list"
     :hx-swap "outerHTML"}
    [:ul.word-list
     {:id "word-list"}]]
   [:footer.words-page__footer
    [:button.words-page__start.big-button.green-button
     {:hx-get "/lesson"
      :hx-indicator "#loader"
      :hx-push-url "true"}
     "НАЧАТЬ УРОК"]]])

;;
;; Routes
;;

(def block-non-htmx-interceptor
  {:name ::block-non-htmx-interceptor
   :enter (fn [ctx]
            (let [htmx-request? (-> ctx :request :headers (get "hx-request"))]
              (cond-> ctx
                (not htmx-request?) (assoc :queue [], :response {:status 403}))))})

(def words-interceptor
  {:name ::words-interceptor
   :enter (fn [ctx]
            (p/let [words (vocabulary/words)]
              (assoc-in ctx [:request :words] words)))})

(def add-word-interceptor
  {:name ::add-word-interceptor
   :enter (fn [ctx]
            (p/let [{:keys [value translation]} (-> ctx :request :params)
                    word-id (vocabulary/add-word value translation)]
              ;; Fetch example asynchronously (fire-and-forget, don't block response)
              (when (examples/online?)
                (-> (examples/fetch-example value)
                    (p/then (fn [example]
                              (when example
                                (vocabulary/save-example word-id value example))))))
              (assoc-in ctx [:request :params :word-id] word-id)))})

(def change-word-interceptor
  {:name ::change-word-interceptor
   :enter (fn [ctx]
            (let [{:keys [word-id value translation]} (-> ctx :request :params)]
              (p/let [word (vocabulary/change-word word-id value translation)]
                (assoc-in ctx [:request :vocabulary/word] word))))})

(def delete-word-interceptor
  {:name ::delete-word-interceptor
   :enter (fn [ctx]
            (let [word-id (-> ctx :request :params :id)]
              (p/do
                (vocabulary/delete-word word-id)
                ctx)))})

(defn- htmx-request?
  [request]
  (-> request :headers (get "hx-request")))

(def page-layout-interceptor
  {:name ::page-layout-interceptor
   :leave (fn [ctx]
            (let [request (:request ctx)
                  response (:response ctx)]
              (if (or (htmx-request? request)
                      (nil? (:html/body response)))
                ctx
                (assoc-in ctx [:response :html/layout] page-layout))))})

(def ui-routes
  [[""
    ["/home"
     {:get (fn [_]
             (p/let [word-count (vocabulary/words-count)]
               {:html/body (home {:word-count word-count})}))}]

    ["/word-count"
     {:get (fn [_]
             (p/let [count (vocabulary/words-count)
                     class (if (zero? count) "word-count--empty" "word-count--ready")]
               {:html/body [:span#word-count {:class class} (str count)]
                :status 200}))}]

    ["/words"
     {:get {:interceptors [words-interceptor]
            :handler (fn [{:keys [pages search words] :as request}]
                       (let [show-more? (>= (count words) 10)
                             htmx-target (-> request :headers (get "hx-target"))]
                         (cond
                           (and (htmx-request? request) (= htmx-target "word-list"))
                           {:html/body (word-list {:pages pages
                                                   :search search
                                                   :show-more? show-more?
                                                   :words words})
                            :status 200}

                           (htmx-request? request)
                           {:html/body words-page
                            :status 200}

                           :else
                           {:html/body words-page
                            :status 200})))}

      :post {:interceptors [add-word-interceptor]
             :handler (fn [request]
                        (let [{:keys [value translation word-id]} (:params request)]
                          (if (or
                               (not (string? value)) (str/blank? value)
                               (not (string? translation)) (str/blank? translation))
                            {:html/body (cond-> (list)
                                          (or (not (string? value)) (str/blank? value))
                                          (conj
                                           [:input.new-word-form__input.new-word-form__input--error
                                            {:hx-on:change "htmx.find('#new-word-translation').focus()"
                                             :hx-swap-oob "true"
                                             :id "new-word-value"
                                             :name "value"}])

                                          (or (not (string? translation)) (str/blank? translation))
                                          (conj
                                           [:input.new-word-form__input.new-word-form__input--error
                                            {:id "new-word-translation"
                                             :hx-swap-oob "true"
                                             :name "translation"}]))
                             :status 400}
                            {:html/body (word-list-item
                                         {:id word-id
                                          :value value
                                          :translation translation
                                          :retention-level 100})
                             :status 200})))}

      :put {:interceptors [change-word-interceptor]
            :handler (fn [request]
                       (let [{:keys [id value translation retention-level]} (:vocabulary/word request)]
                         {:status 200
                          :html/body (word-list-item
                                      {:id id
                                       :value value
                                       :translation translation
                                       :retention-level retention-level})}))}
      :delete {:interceptors [delete-word-interceptor]
               :handler (fn [_] {:status 200})}}]

    ["/words/:id"
     {:get (fn [request]
             (let [word-id (-> request :path-params :id)
                   query-string (:query-string request)
                   edit? (and query-string (str/includes? query-string "edit=true"))]
               (p/let [word (vocabulary/get-word word-id)]
                 {:html/body (word-list-item (assoc word :editing? edit?))
                  :status 200})))}]

    ["/lesson"
     {:get (fn [_]
             {:html/body [:h1 "New Lesson"]
              :status 200})}]]])

(def ring-handler
  (http/ring-handler
   (http/router
    ui-routes

    {:data {:interceptors [(parameters/parameters-interceptor)
                           (keyword-parameters/keyword-parameters-interceptor)
                           (hiccup/interceptor)
                           page-layout-interceptor]}})

   (constantly {:status 404, :body ""})

   {:executor sieppari/executor}))
