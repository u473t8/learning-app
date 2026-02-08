(ns views.lesson
  "Lesson page views."
  (:require
   [presenter.lesson :as presenter.lesson]))


(defn progress
  [value attrs]
  [:div#lesson-progress.progress-bar-value
   (merge
    {:style {:--__internal__progress-bar-value (str value "%")}}
    attrs)])


(defn- header
  [{:keys [progress-value]}]
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
    (progress progress-value {})]])


(defn challenge
  "Renders a challenge - prompt text and instruction."
  [{:keys [prompt is-example?]} & {:as attrs}]
  (let [instruction (if is-example?
                      "Переведите предложение на немецкий"
                      "Переведите слово на немецкий")]
    [:div#lesson-challenge.learning-session__challenge attrs
     [:p.text-plate {:lang "ru"} prompt]
     [:p {:style {:font-size "14px" :color "gray"}} instruction]]))


(defn input
  []
  [:div#lesson-footer
   {:hx-on:htmx:afterSettle "var input = htmx.find('#lesson-answer'); if(input){input.focus();}"}
   [:form.learning-session__footer.learning-session__footer--input
    {:hx-post "/lesson/answer" :hx-target "#lesson-footer" :hx-swap "outerHTML"}
    [:textarea.text-input
     {:id "lesson-answer"
      :name "answer"
      :placeholder "Введите перевод..."
      :maxlength 1000
      :autofocus true
      :lang "de"
      :hx-on:keydown
      "if(event.key==='Enter' && (event.ctrlKey || event.metaKey)){event.preventDefault(); this.form.requestSubmit();}"}]
    [:div.learning-session__action
     [:button.big-button {:type "submit"} "ПРОВЕРИТЬ"]]]])


(defn- success
  [{:keys [correct-answer finished?]}]
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
     (if finished?
       {:hx-delete "/lesson" :hx-target "#app" :hx-swap "innerHTML"}
       {:hx-post "/lesson/next" :hx-target "#lesson-footer" :hx-swap "outerHTML"})
     [:div.learning-session__action
      [:button.big-button
       {:id (if finished? "lesson-finish" "lesson-next") :type "submit" :autofocus true}
       (if finished? "ЗАКОНЧИТЬ" "ДАЛЕЕ")]]]]])


(defn- error
  [{:keys [correct-answer user-answer]}]
  [:div#lesson-footer
   {:tabindex "-1"
    :hx-on:htmx:afterSettle "var btn = this.querySelector('#lesson-next'); if(btn){btn.focus();}"
    :hx-on:keydown
    "if(event.key==='Enter' || event.key===' ' || event.code==='Space'){event.preventDefault(); this.querySelector('form').requestSubmit();}"}
   [:div.learning-session__footer.learning-session__footer--error
    [:div.challenge-answer
     [:p.challenge-answer__header "Ваш ответ:"]
     [:p.challenge-answer__body {:lang "de"} (or user-answer "")]
     [:p.challenge-answer__header "Правильный ответ:"]
     [:p.challenge-answer__body {:lang "de"} correct-answer]]
    [:form {:hx-post "/lesson/next" :hx-target "#lesson-footer" :hx-swap "outerHTML"}
     [:div.learning-session__action
      [:button.big-button
       {:id "lesson-next" :type "submit" :autofocus true}
       "ДАЛЕЕ"]]]]])


(defn footer
  "Renders footer based on props. Returns nil if no props (show input instead)."
  [props]
  (when props
    (case (:variant props)
      :success (success props)
      :error   (error props)
      nil)))


(defn page
  "Render the lesson page. Takes lesson state, uses presenter for props."
  [state]
  (let [{challenge-props :challenge
         progress-value  :progress
         footer-props    :footer}
        (presenter.lesson/page-props state)]
    [:div.learning-session
     (header {:progress-value progress-value})
     [:main.learning-session__body
      (challenge challenge-props)]
     (or (footer footer-props)
         (input))]))


(defn empty-state
  []
  [:div.learning-session
   [:div.learning-session__empty
    [:div.empty-state
     [:p.empty-state__text "Нет слов для урока"]
     [:p.empty-state__hint "Добавьте слова, чтобы начать обучение"]
     [:button.empty-state__cta
      {:hx-get "/words" :hx-push-url "true" :hx-target "#app" :hx-swap "innerHTML"}
      "Добавить слова"]]]])
