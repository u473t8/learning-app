(ns views.home
  "Home page view.")


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
          :hx-post         "/words"
          :hx-push-url     "false"
          :hx-swap         "none"
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
           {:id           "new-word-translation"
            :autocapitalize "off"
            :autocomplete "off"
            :autocorrect  "off"
            :required     true
            :hx-on:keydown "if(event.key==='Enter'){event.preventDefault(); this.form.requestSubmit();}"
            :lang         "ru"
            :name         "translation"
            :placeholder  "Перевод"}]]
         [:button.new-word-form__submit.big-button
          {:type "submit"}
          "ДОБАВИТЬ"]]]]
      [:div.home__words-probe
       {:hx-get      "/words-count"
        :hx-push-url "false"
        :hx-trigger  "load, words-changed from:body"
        :hx-target   "#word-count"
        :hx-swap     "outerHTML"}
       [:span#word-count {:class count-class} (str word-count)]]]]))
