(ns views.home
  "Home page view.")


(defn home
  [{:keys [word-count update-pending?] :or {word-count 0}}]
  (let [count-class (if (zero? word-count) "word-count--empty" "word-count--ready")]
    [:div.home

     [:div#sw-update-slot
      {:hx-trigger "sw-update-available from:body"
       :hx-get     "/home"
       :hx-select  "#sw-update-slot"
       :hx-swap    "outerHTML"}
      (when update-pending?
        [:div.sw-update-toast
         [:span "Доступно обновление"]
         [:button.sw-update-toast__button
          {:onclick "navigator.serviceWorker.getRegistration().then(function(r){r.waiting&&r.waiting.postMessage({type:'SKIP_WAITING'})})"}
          "Обновить"]])]

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
          "if(event.detail.successful && event.detail.elt===this) {this.reset(); htmx.find('#new-word-value').focus(); htmx.trigger(document.body, 'words-changed')}"
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
              :hx-trigger     "input changed delay:200ms"
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
          "ДОБАВИТЬ"]]]]
      [:div.home__words-probe
       {:hx-get      "/words-count"
        :hx-push-url "false"
        :hx-trigger  "load, words-changed from:body"
        :hx-target   "#word-count"
        :hx-swap     "outerHTML"}
       [:span#word-count {:class count-class} (str word-count)]]]]))
