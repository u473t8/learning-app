(ns views.word
  "Pure view functions for word-related UI. Data → Hiccup."
  (:require [utils :as utils]))


(defn retention-text
  "Returns human-readable retention level description."
  [level]
  (cond
    (>= level 80) "Отлично запомнено"
    (>= level 50) "Хорошо изучено"
    (>= level 20) "Нужно повторить"
    :else         "Новое слово"))


(defn word-list-item
  "Renders a single word item in the list."
  [{:keys [id editing? retention-level translation value]}]
  (let [item-id (str "word-" id)]
    [:li.word-item
     {:class (when editing? "word-item--editing") :id item-id}
     (if editing?
       ;; Edit mode - show inputs
       [:form.word-item__form
        {:hx-put     (str "/words?word-id=" id)
         :hx-trigger "submit"
         :hx-swap    "outerHTML"
         :hx-target  (str "#" item-id)}
        [:div.word-item__inputs
         [:input.word-item__input
          {:name        "value"
           :autocapitalize "off"
           :autocomplete "off"
           :autocorrect "off"
           :autofocus   true
           :lang        "de"
           :placeholder "Слово"
           :value       value}]
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
          {:type      "button"
           :hx-get    (str "/words/" id)
           :hx-target (str "#" item-id)
           :hx-swap   "outerHTML"}
          "Отмена"]
         [:button.word-item__delete
          {:type       "button"
           :hx-delete  (str "/words?id=" id)
           :hx-confirm (str "Удалить «" value "»?")
           :hx-trigger "click"
           :hx-swap    "delete"
           :hx-target  (str "#" item-id)}
          "Удалить"]]]
       ;; Display mode - show text, tap to edit
       [:div.word-item__display
        {:hx-get (str "/words/" id "?edit=true") :hx-target (str "#" item-id) :hx-swap "outerHTML"}
        [:div.word-item__retention
         {:style {:background-color (utils/prozent->color retention-level)}
          :title (str (retention-text retention-level) " (" (int retention-level) "%)")}]
        [:span.word-item__value {:lang "de"} value]
        [:span.word-item__arrow "→"]
        [:span.word-item__translation {:lang "ru"} translation]])]))


(defn word-list
  "Renders the word list with pagination and empty state."
  [{:keys [pages search show-more? words] :or {pages 0 show-more? true}}]
  [:ul.word-list
   {:id "word-list"}
   (if (seq words)
     (list
      (for [word words]
        (word-list-item word))
      (when show-more?
        [:li.word-list__load-more
         {:hx-get     (utils/build-url "/words?limit=5" {:pages (inc pages) :search search})
          :hx-swap    "outerHTML"
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
          {:hx-get "/home" :hx-push-url "true" :hx-swap "innerHTML" :hx-target "#app"}
          "Добавить слово"]])])])


(defn validation-error-inputs
  "Renders OOB error inputs for validation failures. Preserves user's values."
  [{:keys [value-blank? translation-blank?]} {:keys [value translation]}]
  (cond-> (list)
    value-blank?
    (conj
     [:input.new-word-form__input.new-word-form__input--error
      {:hx-swap-oob "true" :id "new-word-value" :name "value" :value (or value "")}])
    translation-blank?
    (conj
     [:input.new-word-form__input.new-word-form__input--error
      {:hx-swap-oob "true"
       :id    "new-word-translation"
       :name  "translation"
       :value (or translation "")}])))
