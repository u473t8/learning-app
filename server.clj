(require '[clojure.string :as str]
         '[clojure.edn :as edn]
         '[org.httpkit.server :as srv]
         '[hiccup2.core :as hiccup]
         '[debux.core]
         '[clojure.pprint :as pprint]
         '[clojure.java.io :as io]
         '[ring.middleware.keyword-params :as middleware.keyword-params]
         '[ring.middleware.params :as middleware.params])


(def port 8083)


;;
;; Helpers
;;

(defn normalize-text
  [text]
  (some-> text
          (str/trim)
          (str/lower-case)
          (str/replace #"\s+" " ")
          (str/replace #"ä" "ae")
          (str/replace #"ö" "oe")
          (str/replace #"ü" "ue")
          (str/replace #"ß" "ss")))


(defn prozent->color
  [prozent]
  (let [prozent (-> prozent (or 0) (max 0) (min 100))]
    (format "color-mix(in hsl, rgb(88, 204, 2) %s%%, rgb(255, 75, 75))" prozent)))


;;
;; DB
;;


(defn learning-pairs
  []
  (edn/read-string (slurp "learning-pairs.edn")))


(defn add-learning-pair!
  [learning-pair]
  (let [learning-pairs (conj (learning-pairs) learning-pair)]
    (pprint/pprint learning-pairs (io/writer "learning-pairs.edn"))))


;;
;; Learning Session
;;

(def initial-session-state
  {:challenges {}
   :current-word nil
   :learned-words #{}})


(def session-state (atom initial-session-state))


(defn start-learning-session!
  []
  (swap! session-state assoc :challenges (learning-pairs)))


(defn quit-learning-session! 
  []
  (reset! session-state initial-session-state))


(defn current-word
  []
  (:current-word @session-state))


(defn learned-words
  []
  (:learned-words @session-state))


(defn challenges
  []
  (:challenges @session-state))


(defn next-challenge!
  []
  (when-let [challenge (rand-nth
                        (seq
                         (reduce dissoc (challenges) (learned-words))))]
    (swap! session-state assoc :current-word (key challenge))
    (val challenge)))


(defn learn-current-word! []
  (swap! session-state update :learned-words conj (current-word)))


(defn submission-valid?
  [user-input]
  (= (normalize-text user-input) (normalize-text (current-word))))


(defn current-progress-prozent []
  (if (pos? (count (challenges)))
    (str (/ (* 100.0 (count (learned-words))) (count (challenges))) "%")
    0))


;;
;; Pages
;;

(defn page
  [& content]
  [:html
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:meta {:name "htmx-config" :content "{\"responseHandling\": [{\"code\":\"400\", \"swap\": true, \"error\": true}, {\"code\":\"200\", \"swap\": true, \"error\": false}] }"}]
    [:link {:rel "shortcut icon" :href "data:,"}]
    [:link {:rel "apple-touch-icon" :href "data:,"}]
    [:link {:href "https://fonts.googleapis.com/css2?family=Barlow:ital,wght@0,100;0,200;0,300;0,400;0,500;0,600;0,700;0,800;0,900;1,100;1,200;1,300;1,400;1,500;1,600;1,700;1,800;1,900&display=swap"
            :rel "stylesheet"}]
    [:link {:id "styles" :href "styles.css" :rel "stylesheet"}]
    [:script {:src "https://unpkg.com/htmx.org@2.0.4" :type "application/javascript"}]
    [:title "Learning"]]
   [:body
    content]])

(defn words-list
  []
  [:section
   {:hx-swap-oob "true"
    :id "words-list"
    :style {:padding "40px 0"
            :flex 1
            :gap "16px"
            :display "flex"
            :flex-direction "column"
            :overflow "hidden"
            :width "100%"}}
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
        [:b "Cначала неизученные"]]]]]]
   [:form
    {:style {:position "relative"}}
    [:span.input__search-icon
     {:style {:position "absolute"}}]
    [:input.new-word-form__input
     {:autocomplete "off"
      :style {:padding-left "40px"}
      :name "search"}]]
   [:ul.words-list.list
    (for [item (sort-by key (learning-pairs))]
      [:li.list-item.word-item
       [:div
        [:h3
         (key item)]
        [:p.word-item__translation
         (val item)]]
       [:div.word-item__learning-progress
        {:style {:background-color (prozent->color nil)}}]])
    [:li.list-item.word-item.word-item--add-new-word
     [:b
      "Загрузить больше"]
     [:img
      {:src "arrow-down.svg"}]]]])

(defn home
  []
  [:div.home
   [:section
    {:style {:padding "0 0 40px 0"
             :margin "0 auto"
             :max-width "420px"}}
    [:button#submit-button.submit-button
     {:hx-get "/learning-session"
      :type "submit"}
     "НАЧАТЬ"]]
   [:hr
    {:style {:margin 0
             :width "100%"}}]
   (words-list)
   [:hr
    {:style {:margin 0
             :width "100%"}}]
   [:form.new-word-form
    {:hx-on::after-request "if(event.detail.successful) {this.reset(); htmx.find('#new-word-value').focus()}"
     :hx-post "/learning-pair"
     :hx-swap "none"
     :hx-trigger "submit, change from:#new-word-translation"}
    [:label.new-word-form__label
     "Новое слово"
     [:input.new-word-form__input
      {:autocomplete "off"
       :hx-on:change "htmx.find('#new-word-translation').focus()"
       :id "new-word-value"
       :name "value"}]]
    [:label.new-word-form__label
     "Перевод"
     [:input.new-word-form__input
      {:autocomplete "off"
       :id "new-word-translation"
       :name "translation"}]]]])


(defn learning-session
  []
  (list
   [:form#session
    {:hx-post "/submit"
     :hx-swap "none"}
    [:div#session-header
     [:div
      {:style {:align-items "center"
               :display "grid"
               :grid-template-columns "min-content 1fr"
               :grid-gap "24px"}}
      [:button.cancel-button
       {:hx-trigger "click"
        :hx-post "/exit-session"}
       [:img
        {:style {:height "18px"
                 :width "18px"}
         :src "close-icon.svg"}]]
      [:div
       {:id "progress-bar"
        :style {"--web-ui_progress-bar-color" "rgb(var(--color-owl))"
                "--web-ui_progress-bar-shine-height" "3px"
                "--__internal__progress-bar-height" "16px"
                "--__internal__progress-bar-inner-value" "0%"}}
       [:div.progress-bar
        [:div.progress-bar-value
         {:id "progress-bar-value"
          :style {"--__internal__progress-bar-value" "0%"}}]]]]]
    [:div#session-body
     [:div.challenge
      [:div.challenge-text
       [:div.text-plate
        {:id "challenge-text"}
        (next-challenge!)]]
      [:div
       {:style {:display "flex"
                :align-items "end"}}
       [:textarea.user-input
        {:autocapitalize "off"
         :autocomplete "off"
         :autocorrect "off"
         :id "user-input"
         :lang "de"
         :name "user-input"
         :placeholder "Напишите на немецком"
         :spellcheck "false"}]]]]
    [:div#session-footer.session-footer
     [:button#submit-button.submit-button
      {:type "submit"}
      "ПРОВЕРИТЬ"]]]))


(defn home-routes [{:keys [:request-method :uri] :as req}]
  (println [request-method uri])
  (case [request-method uri]
    [:get "/close-icon.svg"] {:body (slurp "close-icon.svg")
                              :headers {"Content-Type" "image/svg+xml"}
                              :status "200"}
    [:get "/arrow-down.svg"] {:body (slurp "arrow-down.svg")
                              :headers {"Content-Type" "image/svg+xml"}
                              :status "200"}

    [:get "/styles.css"] {:body (slurp "styles.css")
                          :headers {"Content-Type" "text/plain"}
                          :status "200"}

    [:get "/"] (if (get-in req [:headers "hx-request"])
                 {:body (home)
                  :headers {"HX-Retarget" "body"}
                  :status 200}
                 {:body (page (home))
                  :status 200})

    [:get "/learning-session"] (do
                                 (start-learning-session!)
                                 (if (get-in req [:headers "hx-request"])
                                   {:body (learning-session)
                                    :headers {"HX-Retarget" "body"}
                                    :status 200}
                                   {:body (page (learning-session))
                                    :status 200}))

    [:post "/submit"] (let [user-input (-> req :params :user-input)]
                        (if (submission-valid? user-input)
                          (do
                            (learn-current-word!)
                            {:body (let [next-challenge #d/dbg (next-challenge!)]
                                     (list
                                      [:div#progress-bar-value.progress-bar-value
                                       {:hx-swap-oob "true"
                                        :id "progress-bar-value"
                                        :style {"--__internal__progress-bar-value" (current-progress-prozent)}}]
                                      (if (some? next-challenge)
                                        [:div#challenge-text.text-plate
                                         {:hx-swap-oob "true"
                                          :hx-on::load "htmx.find('#user-input').value = ''"}
                                         next-challenge]
                                        [:button#submit-button.submit-button
                                         {:hx-post "/exit-session"
                                          :hx-on::load "htmx.find('#user-input').disabled = true"
                                          :hx-swap-oob "true"}
                                         "ЗАКОНЧИТЬ"])))
                             :status 200})
                          {:body (list
                                  [:div#session-footer.session-footer.session-footer--error
                                   {:hx-swap-oob "outerHTML"
                                    :hx-on::load "htmx.find('#user-input').disabled = true"}
                                   [:div.correct-answer
                                    [:h2.correct-answer__header
                                     "Правильный ответ:"]
                                    [:div.correct-answer__body
                                     (current-word)]]
                                   [:button#submit-button.submit-button.submit-button-error
                                    {:hx-post "/next-challenge"}
                                    "ДАЛЕЕ"]])
                           :status 400}))
    [:post "/next-challenge"] {:body (list
                                      [:div
                                       {:hx-swap-oob "textContent"
                                        :id "challenge-text"}
                                       (next-challenge!)]
                                      [:div#session-footer.session-footer
                                       {:hx-swap-oob "outerHTML"
                                        :hx-on::load "htmx.find('#user-input').disabled = false;
                                                      htmx.find('#user-input').value = '';
                                                      htmx.find('#user-input').focus()"}
                                       [:button#submit-button.submit-button
                                        {:type "submit"}
                                        "ПРОВЕРИТЬ"]])
                               :status 200}
    [:post "/learning-pair"] (let [{:keys [value translation]} (:params req)]
                               (if (or
                                    (not (string? value)) (str/blank? value)
                                    (not (string? translation)) (str/blank? translation))
                                 {:body (cond-> (list)
                                          (or (not (string? value)) (str/blank? value))
                                          (conj
                                           [:input.new-word-form__input.new-word-form__input--error
                                            {:hx-on:change "htmx.find('#new-word-translation').focus()"
                                             :hx-swap-oob "true"
                                             :id "new-word-value"
                                             :name "value"}])

                                          (or (not (string? translation)) (str/blank? translation))
                                          (conj
                                           [:input.new-word-form__input.new-word-form__input--error
                                            {:id "new-word-translation"
                                             :hx-swap-oob "true"
                                             :name "translation"}]))
                                  :status 400}
                                 (do
                                   (add-learning-pair! [value translation])
                                   {:body (words-list)
                                   :status 200})))
    [:post "/exit-session"] (do
                              (quit-learning-session!)
                              {:headers {"HX-Location" "/"
                                         "HX-Push-Url" "true"}})))




(defn wrap-hiccup
  [handler]
  (fn [request]
    (let [response (handler request)]
      (cond-> response
        (-> response :body sequential?)
        (update :body #(-> % hiccup/html str))))))


(def app
  (-> home-routes
      wrap-hiccup
      middleware.keyword-params/wrap-keyword-params
      middleware.params/wrap-params))


(defonce server (atom nil))


(defn start-server!
  [app port]
  (reset! server (srv/run-server #'app {:port port, :legacy-return-value? false})))


(defn stop-server!
  []
  (when (some? @server)
    (when-let [stopping-promise (srv/server-stop! @server)]
      @stopping-promise
      (reset! server nil))))


(let [url (str "http://localhost:" port "/")]
  (stop-server!)
  (start-server! #'app port)
  (println "serving" url))

(comment
  @(promise))

