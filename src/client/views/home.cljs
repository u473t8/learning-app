(ns views.home
  "Home page view.")


(defn page
  []
  [:div.home
   [:header.home__intro
    [:h1.home__title
     "Главная"]
    [:p.home__subtitle
     "Быстро добавляйте слова и учите немецкий даже без сети."]]

   [:main.home__content
    [:section.home__add
     [:header.home__add-header
      [:h2.home__panel-title
       "Быстрое добавление"]
      [:button.home__words-button
       {:type        "button"
        :hx-get      "/words"
        :hx-push-url "true"
        :hx-swap     "innerHTML"
        :hx-target   "#app"}
       "Список слов"]]

     [:form.home__add-form
      {:hx-on:htmx:after-request
       "if(event.detail.successful && event.detail.elt===this) {this.reset();}"
       :hx-post         "/words"
       :hx-push-url     "false"
       :hx-swap         "none"
       :hx-disabled-elt "find button[type='submit']"
       :hx-disinherit   "hx-disabled-elt"}
      [:fieldset.home__add-fieldset
       [:legend.home__add-legend
        "Добавить слово"]
       [:word-autocomplete
        [:div.home__add-form-row
         [:div.autocomplete
          [:label.home__add-form-label
           {:for "new-word-value"}
           "Слово (немецкий)"]
          [:input.home__add-form-input
           {:id             "new-word-value"
            :name           "value"
            :autocapitalize "off"
            :autocomplete   "off"
            :autocorrect    "off"
            :autofocus      true
            :data-ac-role   "word"
            :hx-get         "/dictionary-entries"
            :hx-include     "this"
            :hx-sync        "this:replace"
            :hx-target      "next [data-ac-role='list']"
            :hx-trigger     "input changed delay:300ms"
            :hx-swap        "innerHTML"
            :required       true
            :lang           "de"
            :placeholder    "Новое слово"}]
          [:ul.suggestions
           {:data-ac-role "list"}]]
         [:span.home__add-form-arrow
          "→"]
         [:div.home__add-translation
          [:label.home__add-form-label
           {:for "new-word-translation"}
           "Перевод (русский)"]
          [:input.home__add-form-input
           {:id           "new-word-translation"
            :name         "translation"
            :autocapitalize "off"
            :autocomplete "off"
            :autocorrect  "off"
            :data-ac-role "translation"
            :lang         "ru"
            :placeholder  "Перевод"
            :required     true}]]]]

       [:button.home__add-form-submit.big-button.big-button--request-stable
        {:type "submit"}
        "ДОБАВИТЬ"]]]]]

   [:footer.home__footer
    [:h2.home__lesson-title
     "Урок"]
    [:button.home__lesson-button.big-button.green-button
     {:hx-get       "/lesson"
      :hx-indicator "#loader"
      :hx-push-url  "true"
      :hx-swap      "innerHTML"
      :hx-target    "#app"}
     "НАЧАТЬ УРОК"]]])
