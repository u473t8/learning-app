(ns learning-app
  (:require
   [reitit.ring :as ring]
   ["@thi.ng/hiccup" :as hiccup]))


(defn build-url
  [path query-params]
  (if (seq query-params)
    (let [query-params (js/URLSearchParams. (clj->js query-params))]
      (str path "?" query-params))
    path))


(def word-list-page 5)


(defn home
  []
  [:div.home

   [:div#splash.home__splash]

   ;; Header with buttons
   [:section.home__header
    {:id "header"}
    #_(when lesson-running?
        [:button.big-button.red-button
         {:hx-delete "/lesson"}
         "ЗАКОНЧИТЬ"])
    [:button.big-button.green-button
     {:hx-get "/lesson"
      :hx-indicator "#loader"
      :hx-push-url "true"}
     #_(if lesson-running?
         "ПРОДОЛЖИТЬ"
         "НАЧАТЬ")]]

   ;; Words list
   [:section.home__words.words
    {:id "words"}
    [:input#words-loaded
     {:type "hidden"
      :name "words-loaded"
      :value word-list-page}]
    [:form.words__search
     [:div.input
      [:span.input__search-icon]
      [:input.input__input-area.input__input-area--icon
       {:autocomplete "off"
        :hx-get (build-url "/words" {:limit word-list-page})
        :placeholder "Поиск слова"
        :hx-target "#words-list"
        :hx-swap "innerHTML"
        :hx-trigger "input changed delay:500ms, keyup[key=='Enter']"
        :name "search"}]]]
    [:hr
     {:style {:width "100%", :margin 0}}]
    [:ul.words__list.list
     {:id "words-list"
      :hx-get "/words?limit=5"
      :hx-select-oob "#splash"
      ;; lazy loading words-list-items here
      :hx-trigger "load"}]]
   [:hr
    {:style {:width "100%", :margin 0}}]

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
  [["/"
    {:get
     (fn [_]
       {:html/body (home)
        :status    200})}]
   ["/lesson"
    {:get
     (fn [_]
       {:html/body [:h1 "New Lesson"]
        :status    200})}]])


(defn wrap-hiccup-render
  [handler]
  ;; Adapted from https://github.com/lambdaisland/hiccup/blob/main/src/lambdaisland/hiccup/middleware.clj
  (fn [request]
    (let [response (handler request)
          body     (:html/body response)]
      ;; Render HTML if there's a `:html/body` key OR there
      ;; is no `:body` key, because if there isn't then
      ;; there is nothing else to fall back to.
      (if (and body (not (:body response)))
        (-> response
            (assoc :status (or (:status response) 200)
                   :body   (hiccup/serialize (clj->js body)))
            (assoc-in [:headers "content-type"] "text/html; charset=utf-8"))
        response))))


(def application
  (ring/ring-handler
   (ring/router
    learning-app/routes
    {:data {:middleware [wrap-hiccup-render]}})
   (constantly {:status 404, :body ""})))
