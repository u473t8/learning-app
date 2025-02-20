(require '[cheshire.core :as cheshire]
         '[clojure.string :as str]
         '[clojure.edn :as edn]
         '[clojure.math :as math]
         '[org.httpkit.server :as srv]
         '[org.httpkit.client :as client]
         '[hiccup2.core :as hiccup]
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
      (str/replace #"\p{Punct}" "")
      (str/replace #"ä" "ae")
      (str/replace #"ö" "oe")
      (str/replace #"ü" "ue")
      (str/replace #"ß" "ss")))


(defn normalize-index
  [indexed-coll]
  (reduce-kv
   (fn [result k v]
     (assoc result (normalize-text (name k)) v))
   {}
   indexed-coll))


(defn prozent->color
  [prozent]
  (let [prozent (-> prozent (or 0) (max 0) (min 100))]
    (format "color-mix(in hsl, rgb(88, 204, 2) %s%%, rgb(255, 75, 75))" prozent)))


;;
;; Examples Generator
;;

(def open-ai-service-account-api-key
  (System/getenv "OPEN_AI_KEY"))

(def system-prompt
  "Return a JSON object where:
   - Each key is a German word
   - Each value is an object with \"sentence\" and \"translation\" keys
   - \"sentence\" contains a German sentence using the word with appropriate grammatical form:
     * Nouns: Use cases (Nominativ/Genitiv/Dativ/Akkusativ) or number
     * Verbs: Use tenses (Präsens/Präteritum/Imperativ/Konjunktiv/etc.)
     * Adjectives: Use proper declensions
    Generated sentences should align with language proficiency levels from A1 to C2, incorporating diverse grammatical structures (e.g., conditional clauses, subjunctive mood, etc.) as well as idioms, set phrases, and fixed expressions.
    The sentences should adhere to standard written and spoken German.
  - \"translation\" contains the Russian translation of the sentence
    Return only the JSON object without additional text.")


(defn make-api-request
  [words]
  (client/request
   {:url "https://api.openai.com/v1/chat/completions"
    :method :post
    :headers {"Authorization" (str "Bearer " open-ai-service-account-api-key)
              "Content-Type" "application/json"}
    :body (cheshire/generate-string
           {:model "gpt-4o-mini"
            :messages [{:role "system"
                        :content system-prompt}
                       {:role "user"
                        :content (str "Generate for input words: " (cheshire/generate-string words))}]
            :response_format {:type "json_schema"
                              :json_schema {:name "sentence_examples"
                                            :schema {:type "object"
                                                     :properties (into
                                                                  {}
                                                                  (for [word words]
                                                                    [word
                                                                     {:type "object"
                                                                      :properties {"sentence" {:type "string"
                                                                                               :description "A German sentence using the word."}
                                                                                   "translation" {:type "string"
                                                                                                  :description "Russian translation of the sentence."}}
                                                                      :additionalProperties false
                                                                      :required ["sentence" "translation"]}]))
                                                     :additionalProperties false
                                                     :required words}
                                            :strict true}}
            :temperature 0})}))


(defn parse-json
  [json-string]
  (cheshire/parse-string json-string true))


(defonce examples-cache (atom {}))


(comment
  (reset! examples-cache {})
  (deref examples-cache))


(defn cached-words
  []
  (-> @examples-cache keys set))


;; With cache

(defn sentence-examples [words]
  (let [new-words (remove (cached-words) (map normalize-text words))
        _        (prn "new-words" new-words)
        response (when (seq new-words)
                   @(make-api-request new-words))
        examples (-> response :body parse-json :choices first :message :content parse-json normalize-index)]
    (swap! examples-cache merge examples)
    (vals (select-keys @examples-cache words))))


;; Without cache
#_
(defn sentence-examples [words]
  (let [response @(make-api-request words)]
    (-> response :body parse-json :choices first :message :content parse-json vals)))


(comment
  (remove (cached-words) ["einzig" "trauen"])
  (sentence-examples
   ["einzig" "trauen"]))


(defn example-value
  [example]
  (:sentence example))


(defn example-translation
  [example]
  (:translation example))


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
                       :forgetting-rate 0.00231 ;; approx 50% forgetting after 5 min
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

(def session-word-limit 10)

(def initial-session-state
  {:challenges {}
   :current-challenge nil})


(def session-state (atom initial-session-state))


(comment
  (deref session-state))


(defn start-learning-session!
  []
  (let [learning-pairs (->> (learning-pairs)
                            (sort-by retention-level)
                            (take session-word-limit))
        words (map word-value learning-pairs)
        challenges (into
                    (for [learning-pair learning-pairs]
                      {:text (word-translation learning-pair)
                       :answer (word-value learning-pair)
                       :word (word-value learning-pair)})
                    (for [example (sentence-examples words)]
                      {:text (example-translation example)
                       :answer (example-value example)
                       :sentence? true}))]
    (swap! session-state assoc :challenges challenges)
    (swap! session-state assoc :session-length (count challenges))))


(defn session-length
  []
  (:session-length @session-state))


(defn quit-learning-session!
  []
  (reset! session-state initial-session-state))


(defn current-challenge
  []
  (:current-challenge @session-state))


(defn challenges
  []
  (:challenges @session-state))


(defn next-challenge!
  []
  (when-let [challenge (rand-nth (seq (challenges)))]
    (swap! session-state assoc :current-challenge challenge)
    (:text challenge)))


(defn has-next-challenge?
  []
  (seq (challenges)))


(defn pass-challenge!
  []
  (swap! session-state update :challenges (fn [challenges]
                                            (remove #{(current-challenge)} challenges))))


(defn review-user-input!
  [user-input]
  (let [current-challenge (current-challenge)

        answer (:answer current-challenge)
        retained? (= (normalize-text user-input) (normalize-text answer))]
    (when-let [word (:word current-challenge)]
      (update-forgetting-rate! word retained?))
    (when retained?
      (pass-challenge!))))


(defn current-progress-prozent []
  (let [count-challenges (count (challenges))]
    (if (pos? count-challenges)
      (str (- 100.0 (/ (* 100 count-challenges) (session-length))) "%")
      0)))


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

(def loaders
  ["СЕЙЧАС ВСЁ БУДЕТ ГОТОВО"
   "УЖЕ ПОЧТИ ВСЁ"
   "УЖЕ ДОЛЖНО БЫЛО ЗАПУСТИТЬСЯ"
   "ПЕРЕНАПРАВЛЯЕМ ЗАПРОС В ПОДДЕРЖКУ"
   "ПОПРОБУЙТЕ ПЕРЕЗАГРУЗИТЬ РОУТЕР"])


(defn home
  []
  [:div.home
   [:div#loader.loader.htmx-indicator
    [:div.loader__list
     (for [loader loaders]
       [:div.loader__text
        loader])]]
   [:div
    {:style {:padding "24px"}}
    [:button.big-button.green-button
     {:hx-get "/learning-session"
      :hx-indicator "#loader"
      :hx-push-url "true"}
     "НАЧАТЬ"]]
   [:section.words
    {:style {:flex 1
             :display "flex"
             :flex-direction "column"
             :overflow "hidden"
             :width "100%"}}
    [:form.words__search
     [:div.input
      [:span.input__search-icon]
      [:input.input__input-area.input__input-area--icon
       {:autocomplete "off"
        :hx-get "/search"
        :placeholder "Поиск слова"
        :hx-target "#words-list"
        :hx-swap "outerHTML"
        :hx-trigger "input changed delay:500ms, keyup[key=='Enter']"
        :name "search"}]]]
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
   {:style {"--__internal__progress-bar-value" progress}}])


(defn challenge-text
  []
  [:div.text-plate
   {:hx-on::load "let user_input = htmx.find('#user-input'); user_input.disabled = false; user_input.focus()"}
   (next-challenge!)])


(defn text-input
  []
  [:textarea.text-input
   {:id "user-input"
    :autocapitalize "off"
    :autocomplete "off"
    :autocorrect "off"
    :autofocus "true"
    :hx-on:keydown "if (event.key === 'Enter' && !event.shiftKey) {event.preventDefault();  this.form.requestSubmit()}"
    :lang "de"
    :name "user-input"
    :placeholder "Напишите на немецком"
    :spellcheck "false"}])


(defn submit-challenge
  []
  [:button.big-button.green-button
   {:form "challenge-form"
    :type "submit"}
   "ПРОВЕРИТЬ"])



;; Events:
;; - new-challenge
;; - incorrect-submission
;; - challenge-over

(defn learning-session
  []
  [:div.learning-session

   [:div.learning-session__header
    [:button.cancel-button
     {:hx-trigger "click"
      :hx-delete "/learning-session"}
     [:span.close-icon]]
    [:div#progress-bar.progress-bar
     (learning-session-progress "0%")]]

   [:form.learning-session__body
    {:id "challenge-form"
     :hx-post "/learning-session/challenge"
     :hx-on::before-on-load "console.log(event)"
     :hx-on::before-request "htmx.find('textarea').disabled = true"
     :hx-swap "none"}
    [:div#challenge.learning-session__challenge
     {:hx-get "/learning-session/challenge/text"
      :hx-swap "innerHTML"
      :hx-trigger "new-challenge from:body"}
     (challenge-text)]
    [:div.learning-session__user-input
     {:hx-get "/learning-session/challenge/input"
      :hx-swap "innerHTML"
      :hx-trigger "new-challenge from:body"}
     (text-input)]]

   [:div.learning-session__footer
    [:div.challenge-answer
     {:hx-trigger "challenge-fail from:body"
      :hx-swap "beforeend"
      :hx-get "/learning-session/challenge/answer"}
     [:h2.challenge-answer__header
      "Правильный ответ:"]]
    [:div.learning-session__action
     {:hx-get "/learning-session/action"
      :hx-trigger "challenge-fail from:body, new-challenge, session-end from:body"
      :hx-vals "js:{event: event.type}"}
     (submit-challenge)]]])


(defn- home-routes [{:keys [:request-method :uri] :as req}]
  (println [request-method uri])
  (case [request-method uri]
    [:get "/icons.svg"]  {:body    (slurp "icons.svg")
                          :headers {"Content-Type" "image/svg+xml"}
                          :status  "200"}
    [:get "/styles.css"] {:body    (slurp "styles.css")
                          :headers {"Content-Type" "text/plain"}
                          :status  "200"}

    [:get "/"] (if (get-in req [:headers "hx-request"])
                 {:body    (home)
                  :headers {"HX-Retarget" "body"}
                  :status  200}
                 {:body   (page (home))
                  :status 200})

    [:get "/learning-session"] (do
                                 (start-learning-session!)
                                 (if (get-in req [:headers "hx-request"])
                                   {:body    (learning-session)
                                    :headers {"HX-Retarget" "body"}
                                    :status  200}
                                   {:body   (page (learning-session))
                                    :status 200}))

    [:delete "/learning-session"]     (do
                                        (quit-learning-session!)
                                        {:headers {"HX-Location" "/"
                                                   "HX-Push-Url" "true"}})
    [:get "/learning-session/action"] (let [event (-> req :params :event)]
                                        {:body (case event
                                                 "challenge-fail" [:button.big-button.red-button
                                                                   {:autofocus "true"
                                                                    :hx-on:click "htmx.trigger(this, 'new-challenge')"}
                                                                   "ДАЛЕЕ"]
                                                 "new-challenge"  (list
                                                                   (submit-challenge)
                                                                   [:div#challenge-answer
                                                                    {:hx-swap-oob "delete"}])
                                                 "session-end"    [:button.big-button.green-button
                                                                   {:autofocus "true"
                                                                    :hx-delete "/learning-session"}
                                                                   "ЗАКОНЧИТЬ"])
                                         :status 200})

    [:get "/learning-session/challenge/answer"] {:body [:div#challenge-answer.challenge-answer__body
                                                        (:answer (current-challenge))]
                                                 :status 200}

    [:get "/learning-session/challenge/input"] {:body (text-input)
                                                :status 200}

    [:get "/learning-session/challenge/text"]   {:body (challenge-text)
                                                 :status 200}

    [:get "/learning-session/challenge"]        {:status 200
                                                 :body [:div#challenge
                                                        {:hx-swap-oob "innerHTML"}
                                                        (challenge-text)]}

    [:post "/learning-session/challenge"]       (let [user-input (-> req :params :user-input)]
                                                  (if (review-user-input! user-input)
                                                    {:headers (if (has-next-challenge?)
                                                                {"HX-Trigger" "new-challenge"}
                                                                {"HX-Trigger" "session-end"})
                                                     :body [:div#progress-bar
                                                            {:hx-swap-oob "innerHTML"}
                                                            (learning-session-progress
                                                             (current-progress-prozent))]
                                                     :status 200}
                                                    {:headers {"HX-Trigger" "challenge-fail"}
                                                     :status 400}))

    [:get "/learning-session/progress"]         {:body (learning-session-progress
                                                        (current-progress-prozent))
                                                 :status 200}

    [:post "/learning-pair"]                    (let [{:keys [value translation]} (:params req)]
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
    [:get "/search"]        {:body (words-list {:search (-> req :params :search)})
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

