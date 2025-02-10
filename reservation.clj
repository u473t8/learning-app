(defn words-summary
  []
  [:div.words-summary
    [:div.words-summary__words-count
     (let [words-count (count (learning-pairs))]
       (str
        words-count
        " "
        (case (mod words-count 10)
          1 "cлово"
          (2 3 4) "слова"
          "cлов")))]
    [:form.dropdown-menu.words-summary__words-selector
     {:hx-on:change "htmx.find('.dropdown-menu__selector-label').textContent = event.srcElement.dataset.label"}
     [:div.dropdown-menu__selector
      {:hx-on:focusin "htmx.find('fieldset').style.setProperty('display', 'block')"
       :hx-on:focusout "htmx.find('fieldset').style.setProperty('display', 'none')"
       :tabindex -1}
      [:div.dropdown-menu__selector-label
       "Сначала неизученные"]
      [:svg.dropdown-menu__selector-arrow
       {:viewBox "0 0 15 9"}
       [:path
        {:d "M0.43934 0.43934C1.02513 -0.146447 1.97487 -0.146447 2.56066 0.43934L7.5 5.37868L12.4393 0.43934C13.0251 -0.146447 13.9749 -0.146447 14.5607 0.43934C15.1464 1.02513 15.1464 1.97487 14.5607 2.56066L8.56066 8.56066C7.97487 9.14645 7.02513 9.14645 6.43934 8.56066L0.43934 2.56066C-0.146447 1.97487 -0.146447 1.02513 0.43934 0.43934Z"
         :fill "currentColor"}]
       "Сначала неизученные"]
      [:fieldset.list.dropdown-menu__options-list
       [:label.list-item.dropdown-menu__option
        [:input
         {:checked true
          :data-label "Сначала изученные"
          :id "well-learned-first"
          :name "words-selector"
          :type "radio"
          :value "well-learned-first"}]
        [:b "Cначала изученные"]]
       [:label.list-item.dropdown-menu__option
        [:input
         {:data-label "Сначала неизученные"
          :id "poor-learned-first"
          :name "words-selector"
          :type "radio"
          :value "poor-learned-first"}]
        [:b "Cначала неизученные"]]]]]])

