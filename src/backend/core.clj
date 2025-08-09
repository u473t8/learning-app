(ns core
  (:gen-class)
  (:require
   [cheshire.core :as cheshire]
   [clojure.string :as str]
   [hiccup :as hiccup]
   [honey.sql :as sql]
   [honey.sql.helpers  :as sql.helpers]
   [layout :as layout]
   [next.jdbc :as jdbc]
   [next.jdbc.datafy]
   [next.jdbc.prepare :as prepare]
   [next.jdbc.protocols :as p]
   [next.jdbc.result-set :as result-set]
   [org.httpkit.client :as client]
   [org.httpkit.server :as srv]
   [reitit.ring :as ring]
   [reitit.ring.middleware.dev :as middleware.dev]
   [ring.middleware.session :as session]
   [ring.middleware.session.store :as session.store]
   [ring.middleware.reload :as reload]
   [ring.util.response :as response]
   [taoensso.telemere :as telemere]
   [utils])
  (:import
   [java.sql PreparedStatement ResultSetMetaData]
   [java.util HexFormat]
   [javax.crypto Mac]
   [javax.crypto.spec SecretKeySpec]
   [org.sqlite Function]
   [sqlite.application-defined-functions NormalizeGerman RetentionLevel]))


(set! *warn-on-reflection* true)


(def port 8083)

;;
;; Examples Generator
;;

(defn open-ai-service-account-api-key
  []
  (System/getenv "OPEN_AI_KEY"))


(def dev-mode? (or (System/getenv "LEARNING_APP__ENVIRONMENT") true))

(def db-auth-secret
  (if dev-mode?
    "secret"
    (System/getenv "LEARNING_APP_DB_AUTH_SECRET")))


(def system-prompt
  "Process JSON requests containing German words array [\"word1\", \"word2\", ...] and return a JSON object where:
- Each key is a German word
   - Each value is an object with \"value\", \"translation\", \"structure\" keys
   - \"value\" contains a German sentence using the word with an appropriate grammatical form:
     * Nouns: Use cases (Nominativ/Genitiv/Dativ/Akkusativ) or number.
     * Verbs: Use tenses (Präsens/Präteritum/Imperativ/Konjunktiv/etc.), ensuring that if the input verb is provided without a separable prefix, only its inflected forms without any detachable prefix are used. Conversely, if the input verb includes a detachable prefix, all verb forms in the generated sentence must retain that prefix.
     * Adjectives: Use proper declensions.
   Generated sentences should align with language proficiency levels from A1 to C2, incorporating diverse grammatical structures (e.g., conditional clauses, subjunctive mood, etc.) as well as idioms, set phrases, and fixed expressions.
   The sentences should adhere to standard written and spoken German.
   - \"translation\" contains the Russian translation of the sentence.
   - \"structure\" is a list of pairs, where each pair consists of a word from the sentence in its used form, its dictionary form and its russian translation as it is used in the sentence. There should be a pair for each word in the sentence. Exclude pairs for funtction words, pronouns, particles, and prepositions. Exclude pronouns, articles, particles and prepositions from structure. Structure should include only nouns, verbs, adjectives and adverbs. For a noun the 'dictionaryForm' should include an article, e.g [{'usedForm': 'hund', 'dictionaryForm': 'der Hund', 'translation': 'пёс' }]. If a word in the sentence is a verb with separated prefix (and the prefix is actually separeted), the dictionary form of the whole verb should be provided both for separated prefix and root part of the verb, e.g [{'usedForm': 'komme', 'dictionaryForm': 'ankommen', 'translation': 'прибывать'}, {'usedForm': 'an', 'dictionaryForm': 'ankommen', 'translation': 'прибывать'}].Ensure that every noun, verb (including each separated prefix as its own entry with the same dictionary form as the full verb), adjective, and adverb is included in the 'structure' array. Do not skip any eligible word even if it appears only once.
Return only the JSON object without additional text.")


(defn gen-words-api-request
  [words]
  (client/request
   {:url "https://api.openai.com/v1/chat/completions"
    :method :post
    :headers {"Authorization" (str "Bearer " (open-ai-service-account-api-key))
              "Content-Type" "application/json"}
    :body (cheshire/generate-string
           {:model "gpt-4.1-2025-04-14" #_"gpt-4o-mini"
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
                                                                      :properties {"value" {:type "string"
                                                                                            :description "A German sentence using the word."}
                                                                                   "translation" {:type "string"
                                                                                                  :description "Russian translation of the sentence."}
                                                                                   "structure" {:type  "array",
                                                                                                :description  "A list of triplets containing the used word form, its dictionary form and its translation."
                                                                                                :items {:type "object",
                                                                                                        :properties  {:usedForm  {:type  "string",
                                                                                                                                  :description  "The word in its used form."},
                                                                                                                      :dictionaryForm  {:type  "string",
                                                                                                                                        :description  "The dictionary form of the word."}
                                                                                                                      :translation {:type  "string",
                                                                                                                                    :description  "The dictionary form of the word."}}
                                                                                                        :additionalProperties false
                                                                                                        :required  ["usedForm", "dictionaryForm", "translation"]}}}

                                                                      :additionalProperties false
                                                                      :required ["value" "translation" "structure"]}]))
                                                     :additionalProperties false
                                                     :required words}
                                            :strict true}}
            :top_p 1})}))



(defn generate-examples
  "Returns a map from words to maps:
  * `:value` — German text;
  * `:translation` — Russian translation;
  * `:structure` — a list of pairs, where each pair has:
    * `:dictionaryForm` — the dictionary form of the word;
    * `:usedForm` — the form of the word used in the sentence."
  [words]
  (let [response @(gen-words-api-request words)]
    ;; I am not converting keys of message content to keywords, as the upper level keys may contain spaces, e.g 'der Hund'.
    (-> response :body (cheshire/parse-string true) :choices first :message :content cheshire/parse-string)))


(defn generate-example
  "Returns a hash map with the following keys:
  * `:value` — German text;
  * `:translation` — Russian translation;
  * `:structure` — a list of pairs, where each pair has:
    * `:dictionaryForm` — the dictionary form of the word;
    * `:usedForm` — the form of the word used in the sentence;
    * `:translation`— russian translation of the word used in the sentence."
  [word]
  (-> [word] generate-examples vals first))


(comment
  (generate-example "das Entsetzen"))



;;
;; DB
;;

(def db-spec
  {:dbtype "sqlite", :dbname "app.db"})





(defmacro on-connection
  [[sym connectable] & body]
  `(jdbc/on-connection+options [~sym (-> (jdbc/get-connection ~connectable)
                                         (jdbc/with-logging
                                           (fn [_sym# sql-params#]
                                             {:time (System/currentTimeMillis)
                                              :query sql-params#})
                                           (fn [_sym# state# result#]
                                             (let [data# {:time   (str (- (System/currentTimeMillis) (:time state#)) " ms")
                                                          :query  (:query state#)
                                                          :result result#}
                                                   log-level# (if (instance? Throwable result#)
                                                                :error
                                                                :debug)]
                                               (telemere/log! {:level :error :id :sql/connection :data data#}))))
                                         (jdbc/with-options jdbc/unqualified-snake-kebab-opts))]

    (Function/create (p/unwrap ~sym) "normalize_german" (NormalizeGerman.))
    (Function/create (p/unwrap ~sym) "retention_level" (RetentionLevel.))

     ;; Enable foreign key constraints in SQLite, as they are disabled by default.
     ;; This constraint must be enabled separately for each connection.
     ;; See https://www.sqlite.org/foreignkeys.html#fk_enable
     (jdbc/execute! ~sym ["PRAGMA foreign_keys = on"])

     ~@body))


;; This makes possible to pass clojure map or vector as a query parameter.
;; The value will be saved in a column as JSON string.
(extend-protocol prepare/SettableParameter
  clojure.lang.IPersistentMap
  (set-parameter [m ^PreparedStatement ps idx]
    (.setObject ps idx (cheshire/generate-string m)))

  clojure.lang.IPersistentVector
  (set-parameter [m ^PreparedStatement ps idx]
    (.setObject ps idx (cheshire/generate-string m))))


;; This makes BLOB columns to be read as a JSON value.
(extend-protocol result-set/ReadableColumn
  java.lang.String
  (read-column-by-label [v _]
    v)
  (read-column-by-index [v ^ResultSetMetaData rsmeta idx]
    (case (.getColumnTypeName rsmeta idx)
      ;; It is not possible to declare "JSON" type in a table definition
      ;; https://sqlite.org/json1.html#interface_overview
      ;; I am going to use BLOB columns for JSON values only.
      "BLOB" (cheshire/parse-string v true)
      v)))


;;
;; Users
;;

 (defn user-id
   [db user-name password]
   (let [user (jdbc/execute-one! db ["select id from users where name = ? and password = ? " user-name (hash password)])]
     (:id user)))

;;
;; User Session
;;

 (deftype Sessions [db]
  session.store/SessionStore
  (read-session [_ token]
    (:value
     (jdbc/execute-one! db ["SELECT value FROM sessions WHERE token = ?" token])))
  (write-session [_ token value]
    (let [token (or token (str (random-uuid)))]
      (jdbc/execute! db ["INSERT INTO sessions (token, value) VALUES (?, ?)" token value])
      token))
  (delete-session [_ token]
    (jdbc/execute! db (sql/format {:delete-from :sessions, :where [:= :token token]}))
    nil))


;;
;; User Vocabulary
;;

(defn add-example!
  [db word-id]
  (let [word-value (:value (jdbc/execute-one! db ["SELECT value FROM words WHERE id = ?" word-id]))
        example (generate-example word-value)]
    (jdbc/execute!
     db (sql/format
         {:insert-into :examples
          :columns [:word-id, :value, :translation, :structure]
          :values [[word-id (example "value") (example "translation") [:lift (example "structure")]]]}))))



(defn refresh-examples!
  [db word-ids]
  (let [words        (jdbc/execute!
                      db (sql/format {:select [:id, :value]
                                      :from :words
                                      :where [:in :id word-ids]}))
        new-examples (generate-examples (map :value words))]

    (jdbc/execute!
     db (sql/format
         {:delete-from :examples
          :where [:in :word-id word-ids]}))

    (jdbc/execute!
     db (sql/format
         {:insert-into :examples
          :columns [:word-id, :value, :translation, :structure]
          :values (for [word words]
                    (let [example (get new-examples (:value word))]
                      [(:id word) (example "value") (example "translation") [:lift (example "structure")]]))}))))


(comment
  (on-connection [db db-spec]
                (refresh-examples! db (range 85 101))))


(defn add-word!
  "Adding a word, followed by creating an example."
  [db user-id value translation]
  ;; If the user already has this word added, the UI should show a warning with possible actions.
  (let [value   (utils/sanitize-text value) ;; why sanitize instead of normalize, and why there is sanitize at all?
        word-id (:id (jdbc/execute-one! db ["INSERT INTO words (user_id, value, translation) VALUES (?, ?, ?) RETURNING id" user-id value translation]))]
    (future
      (on-connection [db db-spec]
        (add-example! db word-id)))
    word-id))


(defn replace-word!
  [db word-id word-value translation]
  ;; If the user already has this word, the UI should show a warning with possible actions, idk.
  (let [word-value (utils/sanitize-text word-value)]
    (jdbc/execute! db ["REPLACE INTO WORDS (id, value, translation) VALUES (?, ?, ?)" word-id word-value translation])
    (future
      (on-connection [db db-spec]
        (add-example! db word-id)))))


(defn remove-word!
  [db id]
  (jdbc/execute! db ["delete from words where id = ?" id]))


(defn words
  "Returns a list of words, sorted by retention level, words with least retention comes first"
  [db user-id & {:keys [limit offset search-pattern]}]
  (let [search-pattern (when (some? search-pattern)
                         [:concat "%" [:normalize_german search-pattern] "%"])]
    (jdbc/execute!
     db (sql/format
         (cond-> {:select    [[:words.id :id]
                              [:words.value :value]
                              [:words.translation :translation]
                              [[:raw "retention_level(retained, reviewed_at, created_at ORDER BY reviewed_at)"]
                               :retention-level]]
                  :from      :words
                  :left-join [:reviews [:= :reviews.word-id :words.id]]
                  :where     [:= :user-id user-id]
                  :group-by  [:words.id, :words.value, :words.translation]
                  :order-by  [:retention-level]}

           (some? search-pattern)
           (sql.helpers/where
            [:or
             [:like
              [:normalize_german :value]
              search-pattern]
             [:like
              [:normalize_german :translation]
              search-pattern]])

           (some? limit)
           (sql.helpers/limit limit)

           (some? offset)
           (sql.helpers/offset offset))))))


(defn user-has-words?
  [db user-id]
  (some? (jdbc/execute-one! db ["select 1 from words where user_id = ?" user-id])))


(comment
  (on-connection [db db-spec]
    (words db 2)))


(defn examples
  [db word-ids]
  (jdbc/execute!
   db
   (sql/format
    {:select [:id, :value, :translation]
     :from {:select [:id, :value, :translation, [[:raw "row_number() OVER (PARTITION BY word_id ORDER BY random())"] :rn]]
            :from :examples
            :where [:in :word-id word-ids]
            :order-by [[[:random]]]}
     :where [:= :rn 1]})))


;;
;; Lesson
;;

(def words-per-lesson 3)


(defn challenge-source
  "Returns a map of challenge location:
   * `:source-table`
   * `:source-id`"
  [db user-id]
  (jdbc/execute-one!
   db (sql/format
       {:select [:source-table :source-id]
        :from   :lessons
        :join   [:challenges
                 [:= :challenges.id :lessons.current-challenge-id]]
        :where  [:= :user-id user-id]})))


(defn submit-user-answer!
  [db user-id user-answer]
  (let [{:keys [source-table source-id]} (challenge-source db user-id)
        challenge-answer (:value
                          (jdbc/execute-one!
                           db (sql/format
                               {:select [:value]
                                :from (keyword source-table)
                                :where [:= :id source-id]})))
        challenge-passed? (= (utils/normalize-german user-answer)
                             (utils/normalize-german challenge-answer))]
    (when (= source-table "words")
      (jdbc/execute! db ["INSERT INTO reviews (word_id, retained) VALUES (?, ?)" source-id challenge-passed?]))
    (when challenge-passed?
     (jdbc/execute! db ["UPDATE challenges SET passed = 1 WHERE source_table = ? and source_id = ?" source-table source-id]))))


(defn challenge-passed?
  [db user-id]
  (pos?
   (:passed
    (jdbc/execute-one!
     db (sql/format
         {:select [:passed]
          :from   :lessons
          :join   [:challenges
                   [:= :challenges.id :lessons.current-challenge-id]]
          :where  [:= :user-id user-id]})))))


(defn challenge-answer
  [db user-id]
  (let [{:keys [source-table source-id]} (challenge-source db user-id)]
    (:value
     (jdbc/execute-one!
      db (sql/format
          {:select [:value]
           :from (keyword source-table)
           :where [:= :id source-id]})))))


(defn passed-all-challenges?
  [db user-id]
  (empty?
   (jdbc/execute!
    db (sql/format
        {:select 1
         :from :lessons
         :join [:challenges [:= :challenges.lesson-id :lessons.id]]
         :where [:and [:= :user-id user-id] [:= :passed 0]]}))))


(defn lesson-progress
  [db user-id]
  (int
   (:progress
    (jdbc/execute-one!
     db (sql/format
         {:select [[[:raw "100 * avg(challenges.passed)"] :progress]]
          :from :lessons
          :join [:challenges [:= :challenges.lesson-id :lessons.id]]
          :where [:= :user-id user-id]})))))


(comment
  (on-connection [db db-spec]
    (lesson-progress db 2)))


(defn lesson-running?
  [db user-id]
  (some? (jdbc/execute-one! db ["select true as lesson_running from lessons where user_id = ?" user-id])))


(defn start-lesson!
  "Creates lesson in db and returns it's state"
  [db user-id]
  (when-not (lesson-running? db user-id)
    ;; If creating a lesson fails, then creating challenges does not make sence and vice versa.
    ;; The other thing is that challenges might belong to the lesson's state rather than being
    ;; stored in a separate table.
    (let [words     (words db user-id :limit words-per-lesson)
          examples  (examples db (map :id words))
          lesson-id (:id (jdbc/execute-one! db ["INSERT INTO lessons (user_id) VALUES (?) RETURNING id" user-id]))]
      (jdbc/execute!
       db (sql/format
           {:insert-into :challenges,
            :columns     [:source-id :source-table :lesson-id],
            :values      (concat
                          (for [word words]
                            [(:id word) "words" lesson-id])
                          (for [example examples]
                            [(:id example) "examples" lesson-id]))})))))


(defn start-challenge!
  [db user-id]
  (let [challenge-id (:challenge-id
                      (jdbc/execute-one!
                       db (sql/format
                           {:select [[:challenges.id :challenge-id]]
                            :from :lessons
                            :join [:challenges [:= :challenges.lesson-id :lessons.id]]
                            :where [:and  [:= :lessons.user-id user-id] [:= :passed 0]]
                            :order-by [[[:random]]]})))]
    (jdbc/execute! db ["UPDATE lessons SET current_challenge_id = ? WHERE user_id = ?" challenge-id user-id])))

(comment
  (on-connection [db db-spec]
                 (start-challenge! db 2)))


(defn current-challenge
  [db user-id]
  (let [{:keys [source-id source-table]} (challenge-source db user-id)]
    (jdbc/execute-one!
     db (sql/format
         {:select [:value, :translation]
          :from (keyword source-table)
          :where [:= :id source-id]}))))


(defn next-challenge!
  [db user-id]
  (let [next-challenge-id (:next-challenge-id
                           (jdbc/execute-one!
                            db (sql/format
                                {:select [[:challenges.id :next-challenge-id]]
                                 :from :lessons
                                 :join [:challenges [:= :challenges.lesson-id :lessons.id]]
                                 :where [:and [:= :user-id user-id] [:= :passed 0]]
                                 :order-by [[[:random]]]
                                 :limit 1})))]
    (jdbc/execute! db ["UPDATE lessons SET current_challenge_id = ? WHERE user_id = ?" next-challenge-id user-id])))


(comment
  (on-connection [db db-spec]
    (next-challenge! db 2)
    (current-challenge db 2)))


(defn finish-lesson!
  [db user-id]
  (let [learned-word-ids (->> {:select [[:source-id :id]]
                               :from   :lessons
                               :join   [:challenges
                                        [:= :challenges.id :lessons.current-challenge-id]]
                               :where  [:and
                                        [:= :user-id user-id]
                                        [:= :source-table "words"]]}
                              (sql/format)
                              (jdbc/execute! db)
                              (map :id))]
    (future
      (refresh-examples! db learned-word-ids))
    (jdbc/execute! db ["DELETE FROM lessons WHERE user_id = ?" user-id])))


(defn total-words-count
  [db user-id]
  (:total-words-count (jdbc/execute-one! db ["SELECT COUNT(*) AS total_words_count FROM words WHERE user_id = ?" user-id])))


;;
;; Pages
;;

(defn page
  [{:html/keys [head body]}]
  [:html
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1, interactive-widget=resizes-content"}]
    [:title "Sprecha"]
    [:link {:rel "icon" :href "/favicon.ico"}]
    head]
   [:body
    body]])


(defn words-list-item
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


(def words-list:chunk 5)


(defn words-list-items
  [{:keys [words show-load-more? next-page-url]}]
  (if (seq words)
    (list
     (for [word words]
       (words-list-item word))
     (when show-load-more?
       [:li.list-item.word-item.word-item--add-new-word
        {:hx-get next-page-url
         :hx-swap "outerHTML"
         :hx-trigger "click"}
        [:b
         "Загрузить больше"]
        [:span.arrow-down-icon]]))
    [:div "Ничего не найдено"]))


(defn home
  [{:keys [lesson-running?]}]
  [:div.home

   [:div#splash.home__splash]

   ;; Header with buttons
   [:section.home__header
    {:id "header"}
    (when lesson-running?
      [:button.big-button.red-button
       {:hx-delete "/lesson"}
       "ЗАКОНЧИТЬ"])
    [:button.big-button.green-button
     {:hx-get "/lesson"
      :hx-indicator "#loader"
      :hx-push-url "true"}
     (if lesson-running?
       "ПРОДОЛЖИТЬ"
       "НАЧАТЬ")]]

   ;; Words list
   [:section.home__words.words
    {:id "words"}
    [:input#words-loaded
     {:type "hidden"
      :name "words-loaded"
      :value words-list:chunk}]
    [:form.words__search
     [:div.input
      [:span.input__search-icon]
      [:input.input__input-area.input__input-area--icon
       {:autocomplete "off"
        :hx-get (utils/build-url "/words" {:limit words-list:chunk})
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


;;
;; Learning Session View
;;


(defn- learning-session:progress
  [progress]
  [:div.progress-bar-value
   {:id "progress"
    :style {"--__internal__progress-bar-value" (str progress "%")}}])


(defn- learning-session:footer-view
  ;; The function either receives no arguments or expects all three keys.
  ;; This structure feels awkward, but I’m unsure of a better alternative.
  [& {:keys [challenge-passed? challenge-answer answer-structure passed-all-challenges?]}]
  [:div.learning-session__footer
   {:id "footer"
    :class (cond
             (true? challenge-passed?) "learning-session__footer--success"
             (false? challenge-passed?) "learning-session__footer--error")}
   (when true #_challenge-answer
         [:div.challenge-answer
          [:h2.challenge-answer__header
           "Правильный ответ:"]
          [:div.challenge-answer__body
           #_(for [word (re-seq #"\w+|\p{Punct}|\s+" challenge-answer)]
             (if-let [word-info (challenge-structure word)])
             [:span word])]])
   (when challenge-passed?
     [:h2.challenge-success "Отлично!"])
   (cond
     passed-all-challenges?
     [:button.big-button.green-button
      {:autofocus "true"
       :hx-delete "/lesson"
       :hx-indicator "#loader"}
      [:div.big-button__loader.htmx-indicator
       {:id "loader"}]
      [:span.big-button__label
       "ЗАКОНЧИТЬ"]]

     (some? challenge-passed?)
     [:button.big-button
      {:autofocus "true"
       :class (if challenge-passed?
                "green-button"
                "red-button")
       :hx-indicator "#loader"
       :hx-post "/lesson/advance"
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


(defn- lesson:challenge-view
  [current-challenge]
  [:div.learning-session__challenge
   {:id "challenge"
    :hx-on:htmx:load "htmx.closest(this, 'form').reset(); htmx.find('textarea').focus()"}
   [:div.text-plate
    (:translation current-challenge)]])


(defn lesson:view
  [{:keys [progress current-challenge]}]
  [:div.learning-session
   {:id "learning-session"}

   [:div.learning-session__header
    [:button.cancel-button
     {:hx-trigger "click"
      :hx-delete "/lesson"}
     [:span.close-icon]]
    [:div.progress-bar
     (learning-session:progress progress)]]

   [:form.learning-session__body
    {:id "challenge-form"
     :hx-patch "/lesson"
     :hx-select-oob "#footer, #progress"
     :hx-disabled-elt "textarea, button[type='submit']"
     :hx-indicator "#loader"
     :hx-swap "none"}
    (lesson:challenge-view current-challenge)
    [:div.learning-session__user-input
     {:hx-get "/lesson/challenge/input"
      :hx-swap "innerHTML"
      :hx-trigger "new-challenge from:body"}
     [:textarea.text-input
      {:id "user-answer"
       :autocapitalize "off"
       :autocomplete "off"
       :autocorrect "off"
       :autofocus "true"
       :hx-on:keydown "if (event.key === 'Enter' && !event.shiftKey) {event.preventDefault();  this.form.requestSubmit()}"
       :lang "de"
       :name "user-answer"
       :placeholder "Напишите на немецком"
       :spellcheck "false"}]]]
   (learning-session:footer-view)])


(defn wrap-db-connection
  [handler]
  (fn [request]
    (on-connection [db db-spec]
      (handler (assoc request :db db)))))


(defn wrap-session
  [handler]
  (fn [request]
    (let [db (:db request)
          handler (session/wrap-session handler {:store (->Sessions db)})]
      (handler request))))


(defn service-worker-handler
  [request]
  (when (-> request :uri (= "/js/app/sw.js"))
    (-> (response/resource-response "public/js/app/sw.js")
        (response/content-type "text/javascript")
        (response/header "Service-Worker-Allowed" "/"))))
(defn hmac-sign
  [^String user-name ^String secret]
  (.formatHex
   (HexFormat/of)
   (.doFinal
    (doto (Mac/getInstance "HmacSHA256")
      (.init (SecretKeySpec. (.getBytes secret) "HmacSHA256")))
    (.getBytes user-name))))


(def protected-routes
  (ring/ring-handler
   ;; Main handler
   (ring/router
    ["/auth/check"
     {:get
      (fn [{:keys [session] :as _request}]
        (if (seq session)
          (let [user-name  (-> session :user-id str)
                user-roles [(str "u:" user-name)]
                token      (hmac-sign user-name db-auth-secret)]
            ;; https://docs.couchdb.org/en/stable/api/server/authn.html#proxy-authentication
            (-> {:status 200}
                (response/header "X-Auth-UserName" user-name)
                (response/header "X-Auth-Roles" (str/join "," user-roles))
                (response/header "X-Auth-Token" token)))
          {:status 401}))}])

   ;; Default handler
   (hiccup/wrap-render
    (fn [_]
      {:html/layout layout/page

       :html/body
       [:div#app
        {:hx-get "/home"
         :hx-trigger "controlled"
         :hx-push-url "true"}]}))

   {:middleware
    [wrap-db-connection
     wrap-session]}))


(def public-routes
  (ring/ring-handler
   ;; Main handler
   (ring/router
    [["/login"
      {:get
       (fn [_]
         {:html/layout page

          :html/head
          [:link {:rel "stylesheet" :href "/css/styles.css"}]

          :html/body
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
              #_icons/cancel]]
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
              "ВХОД"]]]]})

       :post
       (fn submit-login
         [{:keys [db params]}]
         (let [{:keys [user password]} params]
           (if-let [user-id (user-id db user password)]
             (-> (response/redirect "/" :see-other)
                 (assoc :session {:user-id user-id}))
             (response/bad-request "Невернoе имя пользователя или пароль"))))}]]

    {:reitit.middleware/transform
     middleware.dev/print-request-diffs

     :data
     {:middleware
      [hiccup/wrap-render]}})

   ;; Default handler
   (constantly (response/redirect "/login"))))


(def ring-handler
  #(ring/routes
    service-worker-handler
    (ring/create-resource-handler {:path "/"})
    protected-routes
    public-routes))


(def app
  (if dev-mode?
    (-> #'ring-handler
        (ring/reloading-ring-handler)
        (reload/wrap-reload))
    (ring-handler)))


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


(defn restart-server!
  [app port]
  (if @server
    (when-some [stopping-promise (srv/server-stop! @server)]
      @stopping-promise
      (start-server! app port))
    (start-server! app port)))


(defn -main
  []
  (let [url (str "http://localhost:" port "/")]
   (restart-server! #'app port)
   (println "Serving" url)))


(comment
)
