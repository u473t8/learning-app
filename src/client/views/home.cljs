(ns views.home
  "Home page view.")


(defn page
  [& _]
  [:div.home
   [:header.home__hero
    [:div.home__hero-text
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
        "if(event.detail.successful && event.detail.elt===this) {this.reset();}"
        :hx-post         "/words"
        :hx-push-url     "false"
        :hx-swap         "none"
        :hx-disabled-elt "find .new-word-form__submit"
        :hx-disinherit   "hx-disabled-elt"}
       [:word-autocomplete
        [:div.new-word-form__row
         [:div.autocomplete
          [:input.new-word-form__input.new-word-form__input--quick
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
         [:span.new-word-form__arrow
          "→"]
         [:input.new-word-form__input.new-word-form__input--quick
          {:id           "new-word-translation"
           :name         "translation"
           :autocapitalize "off"
           :autocomplete "off"
           :autocorrect  "off"
           :data-ac-role "translation"
           :lang         "ru"
           :placeholder  "Перевод"
           :required     true}]]]
       [:button.new-word-form__submit.big-button
        {:type "submit"}
        "ДОБАВИТЬ"]]]]]])
