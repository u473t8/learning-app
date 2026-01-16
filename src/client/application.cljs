(ns application
  (:require
   [clojure.string :as str]
   [domain.word :as domain.word]
   [examples :as examples]
   [hiccup :as hiccup]
   [lambdaisland.glogi :as log]
   [lesson :as lesson]
   [promesa.core :as p]
   [reitit.http :as http]
   [reitit.http.interceptors.keyword-parameters :as keyword-parameters]
   [reitit.http.interceptors.parameters :as parameters]
   [reitit.interceptor.sieppari :as sieppari]
   [utils :as utils]
   [views.word :as views.word]
   [vocabulary :as vocabulary]))


;;
;; Home View
;;


(defn page-layout
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
     [:script {:src "/js/htmx/idiomorph-ext.min.js" :defer true}]
     head]
    [:body {:hx-ext "morph"}
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


(defn home
  [{:keys [word-count] :or {word-count 0}}]
  (let [count-class (if (zero? word-count) "word-count--empty" "word-count--ready")]
    [:div.home
     {:hx-on:htmx:afterSettle "var input = htmx.find('#new-word-value'); if(input){input.focus();}"}
     [:header.home__hero
      [:div.home__hero-text
       [:h1.home__title.home__title--sr "Sprecha"]
       [:p.home__subtitle
        "Быстро добавляйте слова и учите немецкий даже без сети."]]]

     [:section.home__grid
      [:div.home__add-stack
       [:div.home__footer-actions
        [:button.home__lesson-button.big-button.green-button
         {:hx-get       "/lesson"
          :hx-indicator "#loader"
          :hx-push-url  "true"
          :hx-swap      "innerHTML"
          :hx-target    "#app"}
         "НАЧАТЬ УРОК"]]
       [:section.home__panel.home__panel--add
        [:div.home__add-header
         [:h2.home__panel-title
          "Быстрое добавление"]
         [:button.home__words-button
          {:hx-get "/words" :hx-push-url "true" :hx-swap "innerHTML" :hx-target "#app"}
          "Список слов"]]
        [:form.new-word-form.new-word-form--quick
         {:hx-on:htmx:after-request
          "if(event.detail.successful) {this.reset(); htmx.find('#new-word-value').focus(); htmx.trigger(document.body, 'words-changed')}"
          :hx-post     "/words"
          :hx-push-url "false"
          :hx-swap     "none"
          :hx-disabled-elt "find .new-word-form__submit"}
         [:div.new-word-form__row
          [:input.new-word-form__input.new-word-form__input--quick
           {:id "new-word-value"
            :autocapitalize "off"
            :autocomplete "off"
            :autocorrect "off"
            :autofocus "true"
            :required true
            :hx-on:change "htmx.find('#new-word-translation').focus()"
            :hx-on:keydown
            "if(event.key==='Enter'){event.preventDefault(); htmx.find('#new-word-translation').focus();}"
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
            :required true
            :hx-on:keydown "if(event.key==='Enter'){event.preventDefault(); this.form.requestSubmit();}"
            :lang "ru"
            :name "translation"
            :placeholder "Перевод"}]]
         [:button.new-word-form__submit.big-button
          {:type "submit"}
          "ДОБАВИТЬ"]]]]
      [:div.home__words-probe
       {:hx-get      "/word-count"
        :hx-push-url "false"
        :hx-trigger  "load, words-changed from:body"
        :hx-target   "#word-count"
        :hx-swap     "outerHTML"}
       [:span#word-count {:class count-class} (str word-count)]]]]))


(defn words-page
  "Words page. When empty? is true, shows empty state without header/search/footer."
  [& {:keys [empty?]}]
  (if empty?
    [:div.words-page
     [:div.words-page__list
      [:ul.word-list
       {:id "word-list"}
       [:li.word-list__empty
        {:id "word-list-empty"}
        [:div.empty-state
         [:p.empty-state__text "Слов пока нет"]
         [:p.empty-state__hint "Добавьте первое слово на главной странице"]
         [:button.empty-state__cta
          {:hx-get "/home" :hx-push-url "true" :hx-swap "innerHTML" :hx-target "#app"}
          "Добавить слово"]]]]]]
    [:div.words-page
     [:header.words-page__header
      [:button.words-page__back
       {:hx-get "/home" :hx-push-url "true" :hx-swap "innerHTML" :hx-target "#app"}
       "← Назад"]
      [:h1.words-page__title "Мои слова"]]
     [:form.words-page__search
      [:div.input
       [:span.input__search-icon]
       [:input.input__input-area.input__input-area--icon
        {:autocomplete "off"
         :hx-get       "/words"
         :placeholder  "Поиск"
         :hx-target    "#word-list"
         :hx-swap      "innerHTML"
         :hx-trigger   "input changed delay:500ms, keyup[key=='Enter']"
         :name         "search"}]]]
     [:div.words-page__list
      {:hx-get     "/words"
       :hx-trigger "load, words-changed"
       :hx-target  "#word-list"
       :hx-swap    "outerHTML"}
      [:ul.word-list
       {:id "word-list"}]]
     [:footer.words-page__footer
      [:button.words-page__start.big-button.green-button
       {:hx-get       "/lesson"
        :hx-indicator "#loader"
        :hx-push-url  "true"
        :hx-swap      "innerHTML"
        :hx-target    "#app"}
       "НАЧАТЬ УРОК"]]]))


;;
;; Lesson View
;;


(defn- lesson-progress
  [state]
  (let [total-trials     (count (:trials state))
        remaining-trials (count (:remaining-trials state))
        completed-trials (- total-trials remaining-trials)]
    (if (pos? total-trials)
      (* 100 (/ completed-trials total-trials))
      0)))


(defn- lesson-current-trial-data
  [state]
  (let [current-trial (:current-trial state)
        word-id (:word-id current-trial)
        word    (first (filter #(= (:id %) word-id) (:words state)))]
    {:trial current-trial :word word}))


(defn- lesson-progress-value
  [progress attrs]
  [:div#lesson-progress.progress-bar-value
   (merge
    {:style {:--__internal__progress-bar-value (str progress "%")}}
    attrs)])


(defn- lesson-header
  [{:keys [progress on-cancel]}]
  [:header.learning-session__header
   [:button.cancel-button
    {:id          "lesson-cancel"
     :hx-delete   "/lesson"
     :hx-push-url "true"
     :hx-target   "#app"
     :hx-swap     "innerHTML"}
    [:svg {:viewBox "0 0 24 24" :width "18" :height "18"}
     [:path
      {:fill "currentColor"
       :d    "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"}]]]
   [:div.progress-bar
    (lesson-progress-value progress {})]])


(defn- lesson-challenge
  "Renders a challenge - either a word or an example to translate."
  [{:keys [word trial attrs]}]
  (let [example?    (lesson/example-trial? trial)
        prompt-text (if example?
                      (:translation (:example word))
                      (:translation word))
        instruction (if example?
                      "Переведите предложение на немецкий"
                      "Переведите слово на немецкий")]
    [:div#lesson-challenge.learning-session__challenge (or attrs {})
     [:p.text-plate {:lang "ru"} prompt-text]
     [:p {:style {:font-size "14px" :color "gray"}} instruction]]))


(defn- lesson-input
  []
  [:div#lesson-footer
   {:hx-on:htmx:afterSettle "var input = htmx.find('#lesson-answer'); if(input){input.focus();}"}
   [:form.learning-session__footer.learning-session__footer--input
    {:hx-patch "/lesson" :hx-target "#lesson-footer" :hx-swap "outerHTML"}
    [:input {:type "hidden" :name "action" :value "check"}]
    [:textarea.text-input
     {:id        "lesson-answer"
      :name      "answer"
      :placeholder "Введите перевод..."
      :autofocus true
      :lang      "de"
      :hx-on:keydown
      "if(event.key==='Enter' && (event.ctrlKey || event.metaKey)){event.preventDefault(); this.form.requestSubmit();}"}]
    [:div.learning-session__action
     [:button.big-button {:type "submit"} "ПРОВЕРИТЬ"]]]])


(defn- lesson-success
  [{:keys [correct-answer is-finished?]}]
  [:div#lesson-footer
   {:tabindex "-1"
    :hx-on:htmx:afterSettle
    "var btn = this.querySelector('#lesson-next') || this.querySelector('#lesson-finish'); if(btn){btn.focus();}"
    :hx-on:keydown
    "if(event.key==='Enter' || event.key===' ' || event.code==='Space'){event.preventDefault(); this.querySelector('form').requestSubmit();}"}
   [:div.learning-session__footer.learning-session__footer--success
    [:div.challenge-answer
     [:p.challenge-answer__header "Правильно!"]
     [:p.challenge-answer__body {:lang "de"} correct-answer]]
    [:form
     (cond-> {:hx-target "#lesson-footer" :hx-swap "outerHTML"}
       is-finished?       (assoc :hx-delete "/lesson"
                                 :hx-target "#app"
                                 :hx-swap   "innerHTML")
       (not is-finished?) (assoc :hx-patch "/lesson"))
     (when-not is-finished?
       [:input {:type "hidden" :name "action" :value "next"}])
     [:div.learning-session__action
      [:button.big-button
       {:id (if is-finished? "lesson-finish" "lesson-next") :type "submit" :autofocus true}
       (if is-finished? "ЗАКОНЧИТЬ" "ДАЛЕЕ")]]]]])


(defn- lesson-error
  [{:keys [correct-answer]}]
  [:div#lesson-footer
   {:tabindex "-1"
    :hx-on:htmx:afterSettle "var btn = this.querySelector('#lesson-next'); if(btn){btn.focus();}"
    :hx-on:keydown
    "if(event.key==='Enter' || event.key===' ' || event.code==='Space'){event.preventDefault(); this.querySelector('form').requestSubmit();}"}
   [:div.learning-session__footer.learning-session__footer--error
    [:div.challenge-answer
     [:p.challenge-answer__header "Правильный ответ:"]
     [:p.challenge-answer__body {:lang "de"} correct-answer]]
    [:form {:hx-patch "/lesson" :hx-target "#lesson-footer" :hx-swap "outerHTML"}
     [:input {:type "hidden" :name "action" :value "next"}]
     [:div.learning-session__action
      [:button.big-button
       {:id "lesson-next" :type "submit" :autofocus true}
       "ДАЛЕЕ"]]]]])


(defn- lesson-footer
  [state]
  (let [last-result    (:last-result state)
        correct?       (:correct? last-result)
        correct-answer (lesson/expected-answer state)
        is-finished?   (and correct? (empty? (:remaining-trials state)))]
    (when last-result
      (if correct?
        (lesson-success {:correct-answer correct-answer :is-finished? is-finished?})
        (lesson-error {:correct-answer correct-answer})))))


(defn lesson-page
  "Render the lesson page. Takes lesson state."
  [state]
  (let [{:keys [trial word]} (lesson-current-trial-data state)
        progress (lesson-progress state)]
    [:div.learning-session
     (lesson-header {:progress progress :on-cancel "/home"})
     [:main.learning-session__body
      (lesson-challenge {:word word :trial trial})]
     (or (lesson-footer state)
         (lesson-input))]))


(defn- lesson-empty-state
  []
  [:div.learning-session
   [:div.learning-session__empty
    [:div.empty-state
     [:p.empty-state__text "Нет слов для урока"]
     [:p.empty-state__hint "Добавьте слова, чтобы начать обучение"]
     [:button.empty-state__cta
      {:hx-get "/words" :hx-push-url "true" :hx-target "#app" :hx-swap "innerHTML"}
      "Добавить слова"]]]])


;;
;; Routes
;;


(def words-interceptor
  {:name  ::words-interceptor
   :enter (fn [ctx]
            (p/let [words (vocabulary/words)]
              (assoc-in ctx [:request :words] words)))})


(def change-word-interceptor
  {:name  ::change-word-interceptor
   :enter (fn [ctx]
            (let [{:keys [word-id value translation]} (-> ctx :request :params)]
              (p/let [word (vocabulary/change-word word-id value translation)]
                (assoc-in ctx [:request :vocabulary/word] word))))})


(def delete-word-interceptor
  {:name  ::delete-word-interceptor
   :enter (fn [ctx]
            (let [word-id (-> ctx :request :params :id)]
              (p/do
                (vocabulary/delete-word word-id)
                (p/let [word-count (vocabulary/words-count)]
                  (assoc-in ctx [:request :words/count] word-count)))))})


(def lesson-get-interceptor
  {:name  ::lesson-get-interceptor
   :enter (fn [ctx]
            (p/let [lesson-state (lesson/ensure!)
                    word-count   (vocabulary/words-count)]
              (assoc-in ctx
               [:request :lesson/data]
               {:lesson-state lesson-state :word-count word-count})))})


(def lesson-update-interceptor
  {:name  ::lesson-update-interceptor
   :enter (fn [ctx]
            (let [{:keys [action answer]} (-> ctx :request :params)]
              (p/let [lesson-state (lesson/get-state)
                      word-count   (vocabulary/words-count)]
                (if (nil? lesson-state)
                  (assoc-in ctx
                   [:request :lesson/data]
                   {:lesson-state nil :word-count word-count})
                  (case action
                    "check"
                    (p/let [{:keys [correct-answer correct? is-finished? state]}
                            (lesson/check-answer! lesson-state answer)]
                      (assoc-in ctx
                       [:request :lesson/data]
                       {:footer       (if correct?
                                        (lesson-success {:correct-answer correct-answer
                                                         :is-finished?   is-finished?})
                                        (lesson-error {:correct-answer correct-answer}))
                        :lesson-state state}))
                    "next"
                    (p/let [next-state (lesson/advance! lesson-state)]
                      (assoc-in ctx
                       [:request :lesson/data]
                       {:lesson-state next-state}))
                    ctx)))))})


(def lesson-finish-interceptor
  {:name  ::lesson-finish-interceptor
   :enter (fn [ctx]
            (p/do
              (lesson/finish!)
              (p/let [word-count (vocabulary/words-count)]
                (assoc-in ctx [:request :lesson/word-count] word-count))))})


(defn- htmx-request?
  [request]
  (-> request :headers (get "hx-request")))


(def page-layout-interceptor
  {:name  ::page-layout-interceptor
   :leave (fn [ctx]
            (let [request  (:request ctx)
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
               {:html/body [:span#word-count {:class class} (str count)] :status 200}))}]

    ["/words"
     {:get    {:interceptors [words-interceptor]
               :handler      (fn [{:keys [pages search words] :as request}]
                               (let [show-more?  (>= (count words) 10)
                                     htmx-target (-> request :headers (get "hx-target"))
                                     empty?      (empty? words)
                                     page-body   (if empty?
                                                   (words-page {:empty? true})
                                                   (words-page))]
                                 (cond
                                   (and (htmx-request? request) (= htmx-target "word-list"))
                                   {:html/body (views.word/word-list
                                                {:pages      pages
                                                 :search     search
                                                 :show-more? show-more?
                                                 :words      words})
                                    :status    200}

                                   (htmx-request? request)
                                   {:html/body page-body :status 200}

                                   :else
                                   {:html/body page-body :status 200})))}

      :post   {:handler (fn [request]
                          (let [{:keys [value translation] :as params} (:params request)
                                result (domain.word/validate-new-word params)]
                            (if-let [error (:error result)]
                              ;; Validation failed
                              {:html/body (views.word/validation-error-inputs error params)
                               :status    400}

                              ;; Valid - create word and schedule example fetch
                              (p/let [word-id (vocabulary/add-word value translation)]
                                ;; Fire-and-forget example fetch
                                (when (examples/online?)
                                  (utils/fire-and-forget!
                                   #(p/catch
                                     (p/let [example (examples/fetch-one value)]
                                      (when example
                                       (vocabulary/save-example word-id value example)))
                                     (fn [err]
                                      (log/warn :example-fetch/failed
                                                {:word value :error (str err)})))))
                                {:status 200}))))}

      :put    {:interceptors [change-word-interceptor]
               :handler      (fn [request]
                               (let [{:keys [id value translation retention-level]}
                                     (:vocabulary/word request)]
                                 {:status    200
                                  :html/body (views.word/word-list-item
                                              {:id id
                                               :value value
                                               :translation translation
                                               :retention-level retention-level})}))}

      :delete {:interceptors [delete-word-interceptor]
               :handler      (fn [request]
                               (if (zero? (:words/count request))
                                 {:headers   {"HX-Retarget" "#app" "HX-Reswap" "innerHTML"}
                                  :html/body (words-page {:empty? true})
                                  :status    200}
                                 {:status 200}))}}]

    ["/words/:id"
     {:get (fn [request]
             (let [word-id      (-> request :path-params :id)
                   query-string (:query-string request)
                   edit?        (and query-string (str/includes? query-string "edit=true"))]
               (p/let [word (vocabulary/get-word word-id)]
                 {:html/body (views.word/word-list-item (assoc word :editing? edit?))
                  :status    200})))}]

    ["/lesson"
     {:get    {:interceptors [lesson-get-interceptor]
               :handler      (fn [{:lesson/keys [data]}]
                               (let [{:keys [lesson-state word-count]} data]
                                 (cond
                                   (and (nil? lesson-state) (some-> word-count zero?))
                                   {:html/body (lesson-empty-state) :status 200}

                                   (nil? lesson-state)
                                   {:html/body (lesson-empty-state) :status 200}

                                   :else
                                   {:html/body (lesson-page lesson-state) :status 200})))}

      :patch  {:interceptors [lesson-update-interceptor]
               :handler      (fn [{:lesson/keys [data]}]
                               (let [{:keys [lesson-state footer]} data]
                                 (if lesson-state
                                   (let [{:keys [trial word]} (lesson-current-trial-data
                                                               lesson-state)
                                         progress (lesson-progress lesson-state)]
                                     {:html/body (list
                                                  (or footer (lesson-input))
                                                  (lesson-challenge
                                                   {:attrs {:hx-swap-oob "true"}
                                                    :trial trial
                                                    :word  word})
                                                  (lesson-progress-value progress
                                                                         {:hx-swap-oob "true"}))
                                      :status    200})

                                   {:status 404})))}

      :delete {:interceptors [lesson-finish-interceptor]
               :handler      (fn [{:lesson/keys [word-count]}]
                               {:headers   {"HX-Push-Url" "/home"}
                                :html/body (home {:word-count word-count})
                                :status    200})}}]]])


(def ring-handler
  (http/ring-handler
   (http/router
    ui-routes

    {:data {:interceptors [(parameters/parameters-interceptor)
                           (keyword-parameters/keyword-parameters-interceptor)
                           (hiccup/interceptor {:layout-fn nil})
                           page-layout-interceptor]}})

   (constantly {:status 404 :body ""})

   {:executor sieppari/executor}))
