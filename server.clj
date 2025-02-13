(require '[clojure.string :as str]
         '[clojure.edn :as edn]
         '[clojure.math :as math]
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

(defn current-time
  "Returns current unix time"
  []
  (quot (System/currentTimeMillis) 1000))


(defn normalize-text
  [text]
  (-> text
      (or "")
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

(def db-file "vocabulary.edn")


(defonce learning-pairs-indexed (atom nil))


(defn read-db
  []
  (edn/read-string (slurp db-file)))


(defn save-db!
  [vocabulary]
  (pprint/pprint vocabulary (io/writer db-file)))


(defn learning-pairs
  []
  (vals @learning-pairs-indexed))


(defn add-learning-pair!
  [value translation]
  (let [current-time (current-time)
        learning-pair {:created-at current-time
                       :last-reviewed current-time
                       :forgetting-rate 0.01 ;; approx 95% forgetting after 5 min
                       :translation translation
                       :word value
                       :reviews []}]
    (swap! learning-pairs-indexed assoc  value learning-pair)
    learning-pair))


(defn remove-learning-pair!
  [value]
  (swap! learning-pairs-indexed dissoc value))


(defn word-value
  [learning-pair]
  (:word learning-pair))


(defn word-translation
  [learning-pair]
  (:translation learning-pair))


(defn search-word
  [search]
  (cond->> (learning-pairs)
    (some? search)
    (filter
     (fn [learning-pair]
       (or
        (str/includes? (normalize-text (word-value learning-pair)) (normalize-text search))
        (str/includes? (normalize-text (word-translation learning-pair)) (normalize-text search)))))))


(defn retention-level
  [learning-pair]
  (let [{:keys [last-reviewed forgetting-rate]} learning-pair
        time-since-review (- (current-time) last-reviewed)]
    (* 100 (math/exp (- (* forgetting-rate time-since-review))))))



(defn last-reviewed
  [learning-pair]
  (:last-reviewed learning-pair))


#_
(defn forgetting-rate-modifier
  [learning-pair retained?]
  (let [{:keys [last-reviewed forgetting-rate]} learning-pair
        time-since-review (- (current-time) last-reviewed)
        rate-reward (math/exp (- (/ (* 3 time-since-review) (* 60 60 24 365))))
        rate-penalty (- 1 (math/exp (- (/ (* 3 time-since-review) (* 60 60 24 365)))))]
    (* 100 (math/exp (- (* forgetting-rate time-since-review))))))


(defn update-forgetting-rate!
  [word retained?]
  (swap! learning-pairs-indexed update word #(-> %
                                                (assoc :last-reviewed (current-time))
                                                (update :reviews conj retained?)
                                                (update :forgetting-rate (fn [forgetting-rate]
                                                                           (if retained?
                                                                             (* 0.9 forgetting-rate)
                                                                             (* 1.2 forgetting-rate)))))))


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
  (let [challenges (->> (learning-pairs)
                        (sort-by retention-level)
                        (take 10))]
    (swap! session-state assoc :challenges challenges)))


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
  (when-let [learning-pairs (rand-nth (seq (challenges)))]
    (swap! session-state assoc :current-word (word-value learning-pairs))
    (word-translation learning-pairs)))


(defn has-next-challenge?
  []
  (seq (challenges)))


(defn learn-current-word!
  []
  (let [current-word (current-word)]
    (swap! session-state update :challenges (fn [challenges]
                                              (remove #(= (:word %) current-word) challenges)))
    (swap! session-state update :learned-words conj current-word)))


(defn review-user-input!
  [user-input]
  (let [current-word (current-word)
        retained? (= (normalize-text user-input) (normalize-text current-word))]
    (update-forgetting-rate! current-word retained?)
    (when retained?
      (learn-current-word!))))


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


(defn words-list-item
  [{:keys [word-value word-translation retention-level]}]
  [:li.list-item.word-item
   {:id word-value
    :hx-target "this"}
   [:form
    {:hx-put "/learning-pair"
     :hx-trigger "change from:find (input)"
     :hx-swap "outerHTML"}
    [:input.word-item__value
     {:name "value"
      :value word-value}]
    [:input.word-item__translation
     {:name "translation"
      :value word-translation}]]
   [:div.word-item__learning-progress
    {:style {:background-color (prozent->color retention-level)}}]])


(defn words-list
  [& {:keys [search]}]
  [:ul.words-list.list
   {:id "words-list"}
   (when-let [learning-pairs (search-word search)]
     (list
      (for [learning-pair (take 10 (sort-by retention-level learning-pairs))]
        (let [word-value (word-value learning-pair)
              retention-level (retention-level learning-pair)
              word-translation (word-translation learning-pair)]
          (words-list-item
           {:word-value word-value
            :word-translation word-translation
            :retention-level retention-level})))
      (when (seq (drop 10 learning-pairs))
        [:li.list-item.word-item.word-item--add-new-word
         [:b
          "Загрузить больше"]
         [:span.arrow-down-icon]])))])


(defn home
  []
  [:div.home
   [:div
    {:style {:padding "24px"}}
    [:button.big-button.green-button
     {:hx-get "/learning-session"}
     "НАЧАТЬ"]]
   [:section
    {:style {:flex 1
             :display "flex"
             :flex-direction "column"
             :overflow "hidden"
             :width "100%"}}
    [:form
     {:style {:position "relative"
              :margin "0 24px 12px 24px"}}
     [:span.input__search-icon
      {:style {:position "absolute"}}]
     [:input.new-word-form__input
      {:autocomplete "off"
       :style {:padding-left "40px"}
       :hx-post "/search"
       :placeholder "Поиск слова"
       :hx-target "#words-list"
       :hx-swap "outerHTML"
       :hx-trigger "input changed delay:500ms, keyup[key=='Enter']"
       :name "search"}]]
    [:hr
     {:style {:width "100%", :margin 0}}]
    (words-list)]
   [:hr
    {:style {:width "100%", :margin 0}}]
   [:form.new-word-form
    {:hx-on::after-request "if(event.detail.successful) {this.reset(); htmx.find('#new-word-value').focus()}"
     :hx-post "/learning-pair"
     :hx-swap "outerHTML"
     :hx-target "#words-list"}
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
       :name "translation"}]]
    [:button.big-button.blue-button
     {:type "submit"}
     "ДОБАВИТЬ"]]])


(defn learning-session-progress
  [progress]
  [:div.progress-bar-value
   {:id "progress-bar"
    :hx-get "/learning-session/progress"
    :hx-swap "outerHTML"
    :hx-trigger "next-challenge from:body"}
   {:style {"--__internal__progress-bar-value" (str progress "%")}}])


(defn challenge-text
  []
  [:div.text-plate
   {:id "challenge-text"
    :hx-get "/learning-session/challenge"
    :hx-swap "outerHTML"
    :hx-trigger "next-challenge from:body"}
   (next-challenge!)])


(defn finish-session-button
  []
  [:button.big-button.green-button
   {:autofocus "true"
    :hx-post "/exit-session"}
   "ЗАКОНЧИТЬ"])


(defn learning-session
  []
  [:div.learning-session
   [:div.learning-session__header
    [:button.cancel-button
     {:hx-trigger "click"
      :hx-post "/exit-session"}
     [:span.close-icon]]
    [:div.progress-bar
     (learning-session-progress 0)]]
   [:form.learning-session__body
    {:hx-post "/learning-session/challenge"
     :hx-on:next-challenge "this.reset()"
     :hx-target "#learning-session-footer"
     :hx-trigger "submit, keydown[key=='Enter'] from:#user-input"
     :hx-swap "none"
     :id "challenge-form"}
    [:div.learning-session__challenge
     (challenge-text)]
    [:textarea.learning-session__user-input
     {:autocapitalize "off"
      :autocomplete "off"
      :autocorrect "off"
      :autofocus "true"
      :hx-on:challenge-over "this.disabled = true"
      :id "user-input"
      :lang "de"
      :name "user-input"
      :placeholder "Напишите на немецком"
      :spellcheck "false"}]]
   [:div.learning-session__footer
    {:id "learning-session-footer"}
    [:button.big-button.green-button
     {:form "challenge-form"
      :hx-delete "/learning-session"
      :hx-on:challenge-over "this.disabled = true"
      :hx-trigger "challenge-over from:body"
      :type "submit"}
     "ПРОВЕРИТЬ"]]])

(defn home-routes [{:keys [:request-method :uri] :as req}]
  (println [request-method uri])
  (case [request-method uri]
    [:get "/icons.svg"] {:body (slurp "icons.svg")
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

    [:get "/learning-session/progress"] {:body (learning-session-progress
                                                (current-progress-prozent))
                                         :status 200}

    [:get "/learning-session/challenge"] {:body (challenge-text)
                                          :status 200}
    [:post "/learning-session/challenge"] (let [user-input (-> req :params :user-input)]
                                            (if (some? (review-user-input! user-input))
                                              {:headers (if (has-next-challenge?)
                                                          {"HX-Trigger" "next-challenge"}
                                                          {"HX-Trigger" "challenge-over"})
                                               :status 200}
                                              {:body (list
                                                      [:div.learning-session__footer.learning-session-footer--error
                                                       {:hx-on::load "htmx.find('#user-input').disabled = true"}
                                                       [:div.correct-answer
                                                        [:h2.correct-answer__header
                                                         "Правильный ответ:"]
                                                        [:div.correct-answer__body
                                                         (current-word)]]
                                                       [:button#submit-button.big-button.red-button
                                                        {:hx-post "/next-challenge"
                                                         :autofocus "true"}
                                                        "ДАЛЕЕ"]])
                                               :headers {"HX-Trigger" "challenge-over"}
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
                                       [:button#submit-button.big-button.green-button
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
                                   (add-learning-pair! value translation)
                                   {:body (words-list)
                                    :status 200})))
    [:put "/learning-pair"] {:status 200
                             :body (let [{:keys [value translation]} (:params req)
                                         original-value (get-in req [:headers "hx-target"])]
                                     (remove-learning-pair! original-value)
                                     (add-learning-pair! value translation)
                                     (words-list-item
                                      {:word-value value
                                       :word-translation translation
                                       :retention-level 100}))}
    [:post "/exit-session"] (do
                              (quit-learning-session!)
                              {:headers {"HX-Location" "/"
                                         "HX-Push-Url" "true"}})
    [:post "/search"] {:body (words-list {:search (-> req :params :search)})
                       :status 200}))


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
  (reset! learning-pairs-indexed (read-db))
  (add-watch learning-pairs-indexed :save-on-modify (fn [_ _ current-vocabulary new-vocabulary]
                                          (when (not= current-vocabulary new-vocabulary)
                                            (save-db! new-vocabulary))))
  (stop-server!)
  (start-server! #'app port)
  (println "serving" url))

(comment
  @(promise))

