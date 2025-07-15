(ns application
  (:require
   [hiccup :as hiccup]
   [layout :as layout]
   [reitit.ring :as ring]
   [utils :as utils]))


(defn dictionary-entry
  [{:keys [id value translation retention-level]}]
  (let [item-id (str "word-" id)]
    [:li.list-item.word-item
     {:id item-id
      :hx-on:click "htmx.toggleClass(this, 'word-item--selected')"}
     [:form
      {:hx-put (str "/words?id=" id)
       :hx-trigger "change"
       :hx-swap "outerHTML"
       :hx-target (str "#" item-id)}
      [:input.word-item__value
       {:name "word"
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


(defn- dictionary
  ([]
   (dictionary {}))
  ([{:keys [page search show-more? words]
     :or   {page 0, show-more? true, words [{:id 1 :value "word" :translation "перевод", :retention-level 77}
                                            {:id 1 :value "word" :translation "перевод", :retention-level 77}
                                            {:id 1 :value "word" :translation "перевод", :retention-level 77}
                                            {:id 1 :value "word" :translation "перевод", :retention-level 77}]}}]
   (let [page-url "/words?limit=5"]
     [:section.home__words.words
      {:id      "words"
       :hx-swap "morph"}
      #_[:input#words-loaded
         {:type  "hidden"
          :name  "words-loaded"
          :value dictionary-page-size}]
      [:form.words__search
       [:div.input
        [:span.input__search-icon]
        [:input.input__input-area.input__input-area--icon
         {:autocomplete "off"
          :hx-get       page-url
          :placeholder  "Поиск слова"
          :hx-target    "#words-list"
          :hx-swap      "innerHTML"
          :hx-trigger   "input changed delay:500ms, keyup[key=='Enter']"
          :name         "search"}]]]
      [:hr
       {:style {:width "100%", :margin 0}}]
      [:ul.words__list.list
       (if (seq words)
         (list
          (for [word words]
            (dictionary-entry word))
          (when show-more?
            [:li.list-item.word-item.word-item--add-new-word
             {:hx-get     (utils/build-url page-url {:page (inc page), :search search})
              :hx-swap    "outerHTML"
              :hx-trigger "click"}
             [:b
              "Загрузить больше"]
             [:span.arrow-down-icon]]))
         [:div "Ничего не найдено"])]])))


(def home
  [:div.home

   [:div#splash.home__splash]

   ;; Header with buttons
   [:section.home__header
    {:id "header"}
    [:button.big-button.green-button
     {:hx-get "/lesson"
      :hx-indicator "#loader"
      :hx-push-url "true"}
     "НАЧАТЬ"]]

   ;; Words list
   (dictionary)

   [:hr {:style {:width "100%", :margin 0}}]

   ;; Footer for adding words
   [:form.new-word-form
    {:hx-on:htmx:after-request "if(event.detail.successful) {this.reset(); htmx.find('#new-word-value').focus()}; console.log(event)"
     :hx-post "/words"
     :hx-swap "afterbegin"
     :hx-target "#words-list"}
    [:label.new-word-form__label
     "Новое слово"
     [:input.new-word-form__input
      {:id "new-word-value"
       :autocapitalize "off"
       :autocomplete "off"
       :autocorrect "off"
       :autofocus "true"
       :hx-on:change "htmx.find('#new-word-translation').focus()"
       :lang "de"
       :name "value"}]]
    [:label.new-word-form__label
     "Перевод"
     [:input.new-word-form__input
      {:id "new-word-translation"
       :autocapitalize "off"
       :autocomplete "off"
       :autocorrect "off"
       :lang "ru"
       :name "translation"}]]
    [:button.big-button.blue-button
     {:type "submit"}
     "ДОБАВИТЬ"]]])


(def routes
  (ring/ring-handler
   (ring/router
    [["/home"
      {:get
       (fn [_]
         {:html/body home
          :status    200})}]
     ["/lesson"
      {:get
       (fn [_]
         {:html/body [:h1 "New Lesson"]
          :status    200})}]]
    {:data {:middleware  [hiccup/wrap-render]
            :html/layout layout/page}})
   (constantly {:status 404, :body ""})))
