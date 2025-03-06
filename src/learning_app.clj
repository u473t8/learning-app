(ns learning-app
  (:require
   [cheshire.core :as cheshire]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.math :as math]
   [clojure.pprint :as pprint]
   [clojure.string :as str]
   [hiccup2.core :as hiccup]
   [icons :as icons]
   [org.httpkit.client :as client]
   [org.httpkit.server :as srv]
   [ring.middleware.content-type :as middleware.content-type]
   [ring.middleware.keyword-params :as middleware.keyword-params]
   [ring.middleware.params :as middleware.params]
   [ring.middleware.resource :as middleware.resource]
   [ring.middleware.session :as middleware.session]
   [ring.middleware.session.memory :as memory]
   [tools.log :as log]))


(def port 8083)


;;
;; Helpers
;;

(defn current-time
  "Returns current unix time"
  []
  (quot (System/currentTimeMillis) 1000))


(comment
  (current-time))


(defn normalize-german-text
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
     (assoc result (normalize-german-text (name k)) v))
   {}
   indexed-coll))


(defn prozent->color
  [prozent]
  (let [prozent (-> prozent (or 0) (max 0) (min 100))]
    (format "color-mix(in hsl, rgb(88, 204, 2) %s%%, rgb(255, 75, 75))" prozent)))


(defn some-str?
  [string]
  (not (str/blank? string)))


(defn escape-id
  [string]
  (str/replace string #"\s" "_"))


(defn unescape-id
  [string]
  (str/replace string #"_" " "))


;;
;; Users
;;

(def users-store
  {"u473t8" {:password (hash "isKLUh9-polh1!@#4")
             :login "u473t8"}})


(defn password-valid?
  [user password]
  (let [password (str/trim password)]
    (= (get-in users-store [user :password]) (hash password))))


;;
;; User Session
;;


(defonce user-sessions (atom {}))

(comment
  user-sessions)


;;
;; Examples Generator
;;

(def open-ai-service-account-api-key
  (System/getenv "OPEN_AI_KEY"))

(def system-prompt
  "Process JSON requests containing German words array [\"word1\", \"word2\", ...] and return a JSON object where:
- Each key is a German word
   - Each value is an object with \"sentence\", \"translation\", and \"structure\" keys
   - \"sentence\" contains a German sentence using the word with an appropriate grammatical form:
     * Nouns: Use cases (Nominativ/Genitiv/Dativ/Akkusativ) or number.
     * Verbs: Use tenses (Präsens/Präteritum/Imperativ/Konjunktiv/etc.), ensuring that if the input verb is provided without a separable prefix, only its inflected forms without any detachable prefix are used. Conversely, if the input verb includes a detachable prefix, all verb forms in the generated sentence must retain that prefix.
     * Adjectives: Use proper declensions.
   Generated sentences should align with language proficiency levels from A1 to C2, incorporating diverse grammatical structures (e.g., conditional clauses, subjunctive mood, etc.) as well as idioms, set phrases, and fixed expressions.
   The sentences should adhere to standard written and spoken German.
   - \"translation\" contains the Russian translation of the sentence.
   - \"structure\" is a list of pairs, where each pair consists of a word from the sentence in its used form and its dictionary form. There should be a pair for each word in the sentence. Exclude pairs for funtction words, pronouns, particles, and prepositions. If a word in the sentence is a verb with separated prefix (and the prefix is actually separeted), the dictionary form of the whole verb should be provided both for separated prefix and root part of the verb, e.g [{'usedForm': 'komme', 'dictionaryForm': 'ankommen'}, {'usedForm': 'an', 'dictionaryForm': 'ankommen'}]
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
                        :content (str "Generate for input words " (cheshire/generate-string words))}]
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
                                                                                                  :description "Russian translation of the sentence."}
                                                                                   "structure" {:type  "array",
                                                                                                :description  "A list of pairs containing the used word form and its dictionary form."
                                                                                                :items {:type "object",
                                                                                                        :properties  {:usedForm  {:type  "string",
                                                                                                                                  :description  "The word in its used form."},
                                                                                                                      :dictionaryForm  {:type  "string",
                                                                                                                                        :description  "The dictionary form of the word."}}
                                                                                                        :additionalProperties false
                                                                                                        :required  ["usedForm", "dictionaryForm"]}}}

                                                                      :additionalProperties false
                                                                      :required ["sentence" "translation" "structure"]}]))
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
(defn generate-examples
  [words]
  (let [new-words (remove (cached-words) (map normalize-german-text words))
        response (when (seq new-words)
                   @(make-api-request new-words))
        examples (-> response :body parse-json :choices first :message :content parse-json normalize-index)]
    (clojure.pprint/pprint response)
    (swap! examples-cache merge examples)
    (select-keys @examples-cache words)))

(future (Thread/sleep 10000) (println "done") 100)
;; Without cache
#_(defn generate-examples
    [words]
    (let [response @(make-api-request words)]
      (-> response :body parse-json :choices first :message :content parse-json)))


(comment
  (remove (cached-words) ["einzig" "trauen"])
  (generate-examples
   ["einzig" "trauen"]))


(defn generate-examples!
  [words]
  ;; Partitioning, otherwise too many tokens in generate request.
  ;; For example 84 unpartitioned words becomes 504 tokens.
  ;; Quota is 100 tokens per request.
  (let [examples (reduce
                  (fn [examples words]
                    (merge examples (generate-examples words)))
                  (edn/read-string (slurp "examples.edn"))
                  (partition 4 words))]
    (pprint/pprint examples (io/writer "examples.edn"))
    (log/info (str "Generated examples for " (count words) "word"))))


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
        (str/includes? (normalize-german-text (word-value learning-pair)) (normalize-german-text search))
        (str/includes? (normalize-german-text (word-translation learning-pair)) (normalize-german-text search)))))))


(defn retention-level
  [learning-pair]
  (let [{:keys [last-reviewed forgetting-rate]} learning-pair
        time-since-review (- (current-time) last-reviewed)]
    (* 100 (math/exp (- (* forgetting-rate time-since-review))))))


(comment
  (retention-level
   {:created-at 1740140730,
    :last-reviewed 1740152268,
    :forgetting-rate 8.35359048891287E-5,
    :translation "отпускать, освобождать",
    :word "entlassen",
    :reviews [true]}))


(defn learn-pair
  [learning-pair retained?]
  (let [current-time (current-time)
        _ (prn :current-time current-time :last-reviewed (:last-reviewed learning-pair))
        time-since-review (- current-time (:last-reviewed learning-pair))]
    (-> learning-pair
        (update :reviews conj retained?)
        (assoc :last-reviewed current-time)
        (update :forgetting-rate (fn [forgetting-rate]
                                   (if retained?
                                     (/ forgetting-rate (+ 1 (* forgetting-rate time-since-review)))
                                     (* 2 forgetting-rate)))))))


(defn learn-word!
  [word retained?]
  (swap! learning-pairs-indexed update word learn-pair retained?))


;;
;; Learning Session
;;


(def session-word-limit 3)

(def initial-state
  {:challenges {}
   :current-challenge nil
   :user-answer nil
   :learned-words []
   :submitted? false})


(defonce learning-session (atom initial-state))


(comment
  (deref learning-session))


(defn sentence-examples
  [words]
  (-> (slurp "examples.edn")
      (edn/read-string)
      (select-keys words)
      (vals)))


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
                       :structure (:structure example)
                       :sentence? true}))
        challenge (rand-nth (seq challenges))]

    (swap! learning-session assoc :current-challenge challenge)
    (swap! learning-session assoc :challenges challenges)
    (swap! learning-session assoc :session-length (count challenges))))


(defn session-running?
  []
  (-> @learning-session :challenges seq boolean))


(defn finish-learning-session!
  []
  (future
    (generate-examples! (:learned-words @learning-session)))
  (reset! learning-session initial-state))


(defn last-challenge?
  [learning-session]
  (-> learning-session :challenges next nil?))


(defn word-retained?
  [learning-session]
  (= (-> learning-session :user-answer normalize-german-text)
     (-> learning-session :current-challenge :answer normalize-german-text)))


(defn challenge-passed?
  [learning-session]
  (and (:submitted? learning-session)
       (word-retained? learning-session)))


(defn challenge-failed?
  [learning-session]
  (and (:submitted? learning-session)
       (not (word-retained? learning-session))))


(defn submit-user-answer
  [learning-session user-answer]
  (-> learning-session
      (assoc :user-answer user-answer)
      (assoc :submitted? true)
      (update :learned-words (fn [learned-words]
                              (cond-> learned-words
                                (word-retained? learning-session)
                                (conj user-answer))))))


(defn persist-learning-progress!
  [{:keys [:current-challenge] :as learning-session}]
  (when-not (:sentence? current-challenge)
    (learn-word! (:word current-challenge) (word-retained? learning-session))))


(defn advance
  [learning-session]
  (let [current-challenge (:current-challenge learning-session)
        challenges (cond->> (:challenges learning-session)
                     (word-retained? learning-session)
                     (remove #{current-challenge}))]
    (-> learning-session
        (dissoc :user-answer :submitted?)
        (assoc :challenges challenges)
        (assoc :current-challenge (rand-nth (seq challenges))))))


;;
;; Pages
;;

(defn page
  [& content]
  [:html
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1, interactive-widget=resizes-content"}]
    [:meta {:name "htmx-config" :content "{\"responseHandling\": [{\"code\":\"400\", \"swap\": true, \"error\": true}, {\"code\":\"200\", \"swap\": true, \"error\": false}] }"}]
    [:link {:rel "shortcut icon" :href "data:,"}]
    [:link {:rel "apple-touch-icon" :href "data:,"}]
    [:link {:href "fonts/Nunito/nunito-v26-cyrillic_latin-regular.woff2" :as "font" :rel "preload" :type "font/woff2" :crossorigin "true"}]
    [:link {:href "fonts/Nunito/nunito-v26-cyrillic_latin-700.woff2" :as "font" :rel "preload" :type "font/woff2" :crossorigin "true"}]
    [:link {:href "fonts/Nunito/nunito-v26-cyrillic_latin-500.woff2" :as "font" :rel "preload" :type "font/woff2" :crossorigin "true"}]
    [:link {:id "styles" :href "styles.css" :rel "stylesheet"}]
    ;; Unminified
    [:script {:src "https://unpkg.com/htmx.org@2.0.4/dist/htmx.js" :crossorigin "anonymous" :intgerity "sha384-oeUn82QNXPuVkGCkcrInrS1twIxKhkZiFfr2TdiuObZ3n3yIeMiqcRzkIcguaof1"}]
    ;; Minified
    #_[:script {:src "https://unpkg.com/htmx.org@2.0.4" :crossorigin "anonymous" :intgerity "sha384-HGfztofotfshcF7+8n44JQL2oJmowVChPTg48S+jvZoztPfvwD79OC/LTtG6dMp+"}]
    [:title "Learning"]]
   [:body
    content]])


(defn words-list-item
  [{:keys [word-value word-translation retention-level]}]
  (let [item-id (escape-id word-value)]
   [:li.list-item.word-item
   {:id item-id}
   [:form
    {:hx-put "/learning-pairs"
     :hx-on:htmx:before-request "console.log(event)"
     :hx-trigger "change"
     :hx-target (str "#" item-id)
     :hx-swap "outerHTML"}
    [:input.word-item__value
     {:name "value"
      :autocapitalize "off"
      :autocomplete "off"
      :autocorrect "off"
      :lang "de"
      :value word-value}]
    [:input.word-item__translation
     {:name "translation"
      :autocapitalize "off"
      :autocomplete "off"
      :autocorrect "off"
      :lang "ru"
      :value word-translation}]]
   [:div.word-item__learning-progress
    {:style {:background-color (prozent->color retention-level)}}]]))


#_
(defn word-list-items
  [search]
  (when-let [learning-pairs (search-word search)]
    (list
     (for [learning-pair (take 10 (sort-by retention-level > learning-pairs))]
       (let [word-value (word-value learning-pair)
             retention-level (retention-level learning-pair)
             word-translation (word-translation learning-pair)]
         (words-list-item
          {:word-value word-value
           :word-translation word-translation
           :retention-level retention-level})))
     (when (seq (drop 10 learning-pairs))
       [:li.list-item.word-item.word-item--add-new-word
        {:hx-trigger "click"
         :hx-vals (format "{'search': '%s'}" search)
         :hx-swap "beforebegin"}
        [:b
         "Загрузить больше"]
        [:span.arrow-down-icon]]))))


(def words-list:page 4)


(defn words-list-items
  [& {:keys [search words-count]}]
  (let [words-count (or words-count words-list:page)]
    (when-let [learning-pairs (search-word search)]
      (list
       (for [learning-pair (->> learning-pairs
                                (sort-by retention-level >)
                                (take words-count))]
         (let [word-value (word-value learning-pair)
               retention-level (retention-level learning-pair)
               word-translation (word-translation learning-pair)]
           (words-list-item
            {:word-value word-value
             :word-translation word-translation
             :retention-level retention-level})))
       (when (seq (drop words-count learning-pairs))
         [:li.list-item.word-item.word-item--add-new-word
          {:hx-trigger "click"
           :hx-target "#words-list"
           :hx-get (cond-> "/learning-pairs"
                     (or (some-str? search) (pos? words-count)) (str "?")
                     (some-str? search) (str "search=" search)
                     (and (some-str? search) (pos? words-count)) (str "&")
                     (pos? words-count) (str "words-count=" (+ words-count words-list:page)))}
          [:b
           "Загрузить больше"]
          [:span.arrow-down-icon]])))))


(def loaders
  ["НАЧИНАЕМ УРОК"
   "СЕЙЧАС ВСЁ БУДЕТ ГОТОВО"
   "УЖЕ ПОЧТИ ВСЁ"
   "УЖЕ ДОЛЖНО БЫЛО ЗАПУСТИТЬСЯ"
   "ПЕРЕНАПРАВЛЯЕМ ЗАПРОС В ПОДДЕРЖКУ"
   "ПОПРОБУЙТЕ ПЕРЕЗАГРУЗИТЬ РОУТЕР"])


(defn home
  []
  [:div.home
   #_[:div#loader.loader.htmx-indicator
    [:div.loader__list
     {:style {"--items-count" (count loaders)}}
     (for [loader loaders]
       [:div.loader__text
        loader])]]
   [:div
    {:style {:padding "24px"
             :display "flex"
             :align-items "center"
             :justify-content "space-around"
             :width "100%"}}
    (when (session-running?)
      [:button.big-button.red-button
       {:hx-delete "/learning-session"}
       "ЗАКОНЧИТЬ"])
    [:button.big-button.green-button
     {:hx-get "/learning-session"
      :hx-indicator "#loader"
      :hx-push-url "true"}
     (if (session-running?)
       "ПРОДОЛЖИТЬ"
       "НАЧАТЬ")]]
   [:section.words
    {:style {:flex 1
             :display "flex"
             :flex-direction "column"
             :overflow "hidden"
             :width "100%"}}
    [:input#words-loaded
     {:type "hidden"
      :name "words-loaded"
      :value words-list:page}]
    [:form.words__search
     [:div.input
      [:span.input__search-icon]
      [:input.input__input-area.input__input-area--icon
       {:autocomplete "off"
        :hx-get "/learning-pairs"
        :placeholder "Поиск слова"
        :hx-target "#words-list"
        :hx-swap "innerHTML"
        :hx-trigger "input changed delay:500ms, keyup[key=='Enter']"
        :name "search"}]]]
    [:hr
     {:style {:width "100%", :margin 0}}]
    [:ul.words-list.list
     {:id "words-list"}
     (words-list-items
      {:words-count words-list:page})]]
   [:hr
    {:style {:width "100%", :margin 0}}]
   [:form.new-word-form
    {:hx-on:htmx:after-request "if(event.detail.successful) {this.reset(); htmx.find('#new-word-value').focus()}; console.log(event)"
     :hx-post "/learning-pairs"
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


;;
;; Learning Session View
;;

(defn current-progress-prozent
  [learning-session]

  (let [count-challenges (cond->> (count (:challenges learning-session))
                           (and (:submitted? learning-session) (word-retained? learning-session))
                           dec)
        session-length (:session-length learning-session)]
    (str (- 100.0 (/ (* 100 count-challenges) session-length)) "%")))


(defn challenge-text
  [learning-session]
  (-> learning-session :current-challenge :text))


(defn user-input-disabled?
  [learning-session]
  (:submitted? learning-session))


(defn footer-class
  [learning-session]
  (cond
    (challenge-passed? learning-session) "learning-session__footer--success"
    (challenge-failed? learning-session) "learning-session__footer--error"))


(defn show-correct-answer?
  [learning-session]
  (challenge-failed? learning-session))


(defn show-congratulations?
  [learning-session]
  (challenge-passed? learning-session))


(defn correct-answer
  [learning-session]
  (:answer (:current-challenge learning-session)))


(defn show-finish-button?
  [learning-session]
  (and
   (challenge-passed? learning-session)
   (last-challenge? learning-session)))

(defn- learning-session:progress [learning-session]
  [:div.progress-bar-value
   {:id "progress"
    :style {"--__internal__progress-bar-value" (current-progress-prozent learning-session)}}])


(defn- learning-session:footer-view
  [learning-session]
  [:div.learning-session__footer
   {:id "footer"
    :class (footer-class learning-session)}
   (when (show-correct-answer? learning-session)
     [:div.challenge-answer
      [:h2.challenge-answer__header
       "Правильный ответ:"]
      [:div.challenge-answer__body
       (correct-answer learning-session)]])
   (when (show-congratulations? learning-session)
     [:h2.challenge-success "Отлично!"])
   (cond
     (show-finish-button? learning-session)
     [:button.big-button.green-button
      {:autofocus "true"
       :hx-delete "/learning-session"
       :hx-indicator "#loader"}
      [:div.big-button__loader.htmx-indicator
       {:id "loader"}]
      [:span.big-button__label
       "ЗАКОНЧИТЬ"]]

     (:submitted? learning-session)
     [:button.big-button
      {:autofocus "true"
       :class (if (challenge-passed? learning-session)
                "green-button"
                "red-button")
       :hx-indicator "#loader"
       :hx-post "/learning-session/advance"
       :hx-select "#footer"
       :hx-select-oob "#challenge"
       :hx-swap "outerHTML"
       :hx-target "#footer"
       :hx-trigger "click"}
      [:div.big-button__loader.htmx-indicator
       {:id "loader"}]
      [:span.big-button__label
       "ДАЛЕЕ"]]

     :else
     [:button.big-button.green-button
      {:form "challenge-form"
       :type "submit"}
      [:div.big-button__loader.htmx-indicator
       {:id "loader"}]
      [:span.big-button__label
       "ПРОВЕРИТЬ"]])])

(show-finish-button? @learning-session)
(last-challenge? @learning-session)

(defn- learning-session:challenge-view
  [learning-session]
  [:div.learning-session__challenge
   {:id "challenge"
    :hx-on:htmx:load "htmx.closest(this, 'form').reset(); htmx.find('textarea').focus()"}
   [:div.text-plate
    (challenge-text learning-session)]])


(defn learning-session:view
  [learning-session]
  [:div.learning-session
   {:id "learning-session"}

   [:div.learning-session__header
    [:button.cancel-button
     {:hx-trigger "click"
      :hx-delete "/learning-session"}
     [:span.close-icon]]
    [:div.progress-bar
     (learning-session:progress
      learning-session)]]

   [:form.learning-session__body
    {:id "challenge-form"
     :hx-patch "/learning-session"
     :hx-select-oob "#footer, #progress"
     :hx-disabled-elt "textarea, button[type='submit']"
     :hx-indicator "#loader"
     :hx-swap "none"}
    (learning-session:challenge-view
     learning-session)
    [:div.learning-session__user-input
     {:hx-get "/learning-session/challenge/input"
      :hx-swap "innerHTML"
      :hx-trigger "new-challenge from:body"}
     [:textarea.text-input
      {:id "user-answer"
       :autocapitalize "off"
       :autocomplete "off"
       :autocorrect "off"
       :autofocus "true"
       :disabled (user-input-disabled? learning-session)
       :hx-on:keydown "if (event.key === 'Enter' && !event.shiftKey) {event.preventDefault();  this.form.requestSubmit()}"
       :lang "de"
       :name "user-answer"
       :placeholder "Напишите на немецком"
       :spellcheck "false"}
      (:user-input learning-session)]]]

   (learning-session:footer-view
    learning-session)])


(defn login
  []
  [:div.login-page
   [:form.login-page__form
    {:hx-post "/login"
     :hx-indicator "#loader"
     :hx-swap "none"}
    [:h1.login-page__header
     "Вход"]
    [:div.login-page__input.input
     [:input.login-page__input.input__input-area
      {:id "email"
       :type "text"
       :autocomplete "email"
       :name "user"
       :placeholder "Email или имя пользователя"}]
     [:button.input__clear-button
      {:hx-on:click "htmx.find('#email').value = ''; htmx.find('#email').focus()"}
      icons/cancel]]
    [:div.login-page__input.input
     [:input.input__input-area
      {:id "password"
       :type "password"
       :autocomplete "current-password"
       :name "password"
       :placeholder "Пароль"}]]
    [:div.login-page__error-message
     {:id "error-message"}]
    [:button.big-button.blue-button
     {:type "submit"}
     [:div.big-button__loader.htmx-indicator
      {:id "loader"}]
     [:span.big-button__label
      "ВХОД"]]]])


(defn routes [{:keys [:request-method :uri] :as request}]
  (log/info [(:remote-addr request) [request-method uri]])
  (case [request-method uri]

    [:get "/"]
    (if (get-in request [:headers "hx-request"])
      {:body    (home)
       :headers {"HX-Retarget" "body"}
       :status  200}
      {:body   (page (home))
       :status 200})

    [:get "/login"]
    (if (get-in request [:headers "hx-request"])
      {:body    (login)
       :headers {"HX-Retarget" "body"}
       :status  200}
      {:body   (page (login))
       :status 200})

    [:post "/login"]
    (let [{:keys [:user :password]} (:params request)]
      (if (password-valid? user password)
        {:headers {"HX-Location" "/"
                   "HX-Push-Url" "true"}
         :session {:user user}}
        {:status 400
         :headers {"HX-Retarget" "#error-message"
                   "HX-Reswap" "textContent"}
         :body "Неверный пароль. Повторите попытку."}))

    [:get "/learning-session"]
    (do
      (when-not (session-running?)
        (start-learning-session!))
      (if (get-in request [:headers "hx-request"])
        {:body    (learning-session:view @learning-session)
         :headers {"HX-Retarget" "body"}
         :status  200}
        {:body   (page (learning-session:view @learning-session))
         :status 200}))

    [:patch "/learning-session"]
    (let [{:keys [:user-answer]} (:params request)]
      (cond
        (some? user-answer) (let [learning-session (swap! learning-session submit-user-answer user-answer)]
                              (persist-learning-progress! learning-session)
                              {:body (list
                                      (when (challenge-passed? learning-session)
                                        (learning-session:progress learning-session))
                                      (learning-session:footer-view learning-session))
                               :status 200})))

    [:delete "/learning-session"]
    (do
      (finish-learning-session!)
      {:headers {"HX-Location" "/"
                 "HX-Push-Url" "true"}})

    [:post "/learning-session/advance"]
    (let [learning-session (swap! learning-session advance)]
      {:body (list
              (learning-session:challenge-view learning-session)
              (learning-session:footer-view learning-session))
       :status 200})

    [:post "/learning-pairs"]
    (let [{:keys [value translation]} (:params request)]
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
          {:body (words-list-item
                  {:word-value value
                   :word-translation translation
                   :retention-level 100})
           :status 200})))

    [:put "/learning-pairs"]
    (let [{:keys [value translation]} (:params request)
          original-word (unescape-id (get-in request [:headers "hx-target"]))]
      (remove-learning-pair! original-word)
      (add-learning-pair! value translation)
      {:status 200
       :body (words-list-item
              {:word-value value
               :word-translation translation
               :retention-level 100})})

    [:get "/learning-pairs"] {:status 200
                              :body (words-list-items
                                     {:words-count (-> request :params :words-count (or "") parse-long)
                                      :search (-> request :params :search)})}))


(defn wrap-hiccup
  [handler]
  (fn [request]
    (let [response (handler request)]
      (if (-> response :body sequential?)
        (-> response
            (update :body #(-> % hiccup/html str))
            (update :headers assoc "Content-Type" "text/html"))
        response))))


(defn wrap-login
  [handler]
  (fn [{:keys [session uri] :as request}]
    (cond
      (and (empty? session) (not= uri "/login"))
      {:status 302
       :headers {"Location" "/login"}}

      (and (seq session) (= uri "/login"))
      {:status 302
       :headers {"Location" "/"}}

      :else (handler request))))


(def app
  (-> routes
      (wrap-hiccup)
      (wrap-login)
      (middleware.resource/wrap-resource "public")
      (middleware.content-type/wrap-content-type)
      (middleware.keyword-params/wrap-keyword-params)
      (middleware.params/wrap-params)
      (middleware.session/wrap-session {:store (memory/memory-store user-sessions)})))


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


(defn -main
  []
  (let [url (str "http://localhost:" port "/")]
   (reset! learning-pairs-indexed (read-db))
   (add-watch learning-pairs-indexed :save-on-modify (fn [_ _ current-vocabulary new-vocabulary]
                                                       (when (not= current-vocabulary new-vocabulary)
                                                         (save-db! new-vocabulary))))
   (stop-server!)
   (start-server! #'app port)
   (println "serving" url)))


(comment
  (-main)

  (generate-examples! (keys @learning-pairs-indexed))

  (require '[clojure.repl.deps :as deps])

  (deps/sync-deps))


