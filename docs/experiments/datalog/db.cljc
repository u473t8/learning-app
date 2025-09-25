(ns db
  (:require
   ;; #?(:cljs [datascript.core])
   ;; #?(:clj [datahike.api :as d])
   ;; #?(:clj [datahike-jdbc.core])
   #?(:clj [examples :as examples])
   [datalevin.core :as d]
   [clojure.math :as math]
   [utils :as utils]))


(def schema
  [;; User
   {:db/ident       :user/name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident       :user/email
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}
   {:db/ident       :user/password
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident       :user/vocabulary
    :db/valueType   :db.type/ref
    :db/isComponent true
    :db/cardinality :db.cardinality/many}

   ;; Vocabulary
   {:db/ident       :vocabulary.word/value
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident       :vocabulary.word/language
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one}
   {:db/ident       :vocabulary.word/translations
    :db/valueType   :db.type/tuple
    :db/tupleTypes  [:db.type/string :db.type/ref]
    :db/cardinality :db.cardinality/many}
   {:db/ident       :vocabulary.word/examples
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/many}
   {:db/ident       :vocabulary.word/reviews
    :db/valueType   :db.type/tuple
    :db/noHistory   true
    :db/tupleTypes  [:db.type/boolean :db.type/instant]
    :db/cardinality :db.cardinality/many}

   ;; Word
   {:db/ident       :dictionary.word/value
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident       :dictionary.word/translations
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/many}
   {:db/ident       :dictionary.word/language
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one}
   {:db/ident       :dictionary.word/value+language
    :db/valueType   :db.type/tuple
    :db/tupleAttrs  [:dictionary.word/value :dictionary.word/language]
    :db/unique      :db.unique/identity
    :db/cardinality :db.cardinality/one}

   ;; Example sentence
   {:db/ident       :example/value
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}
   {:db/ident       :example/translation
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident       :example/words
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/many}])


(defn datascript-schema
  [schema]
  (reduce
   (fn [schema {:db/keys [ident] :as entity}]
     (assoc schema ident (dissoc entity :db/ident)))
   {} schema))


#_(def db-config
  (datascript-schema schema)
  #_
   (:clj
    (reduce
     (fn [schema {:db/keys [ident] :as entity}]
       (assoc schema ident (dissoc entity :db/ident)))
     {} schema)
    #_{:store
       {:backend :jdbc
        :dbtype  "sqlite"
        :dbname  "app.db"}
       :initial-tx schema}
    :cljs
    (reduce
     (fn [schema {:db/keys [ident] :as entity}]
       (assoc schema ident (dissoc entity :db/ident)))
     {} schema)))


(def conn
  (d/get-conn "asdf" (datascript-schema schema)))


(defn initial-setup
  [conn]
  (d/transact
   conn
   [{:db/ident :language/DE}
    {:db/ident :language/RU}]))


(defn add-word!
  [conn user word-value translations]
  (let [word-value (utils/sanitize-text word-value)]
    (-> (d/transact
         conn
         [{:db/id                      user
           :user/vocabulary [{:db/id "word", :vocabulary.word/value word-value}]
           :dictionary.word/value        word-value
           :dictionary.word/translations translations
           :dictionary.word/reviews      [true (utils/now)]}])
        (get-in [:tempid "word"]))))


(defn add-example!
  [conn word _language]
  #?(:clj
     (let [example (-> @conn (d/entity word) :vocabulary.word/value examples/generate)]
       (d/transact
        conn
        [{:db/id "example"
          :example/value       (example "value")
          :example/translation (example "translation")}
         {:db/id word
          :vocabulary.word/examples ["example"]}]))))


(defn replace-word!
  [conn word new-translations]
  (d/transact
   conn
   [{:db/id word
     :vocabulary.word/translations new-translations}]))


(defn remove-word!
  [conn word]
  (d/db-with
   conn
   [:db/retractEntity word]))


(defn review-word!
  [conn word submission instant]
  (let [word-value (-> @conn (d/entity word) :vocabulary.word/value)
        retained?  (= (utils/normalize-german word-value)
                      (utils/normalize-german submission))]
    (d/transact
     conn
     [{:db/id word, :vocabulary.word/reviews [retained? instant]}])))


(defn reviews
  "Returns a word's review history as a sequence of [retained? timestamp] pairs.

  Args:
  * `db`   - the database instance
  * `word` - the ID of the word entity

  Returns:
  A sequence of pairs:
  * `retained?` - a boolean indicating whether the word was remembered
  * `instant`   - review time as java.util.Date instant"

  [db word]
  (->> (d/q '[:find ?retained ?instant
              :in $ ?word
              :where
              [?word :vocabulary.word/reviews ?reviews]
              [(untuple ?reviews) [?retained ?instant]]]
            db word)
       (sort-by second)))


(defn retention-level
  "Returns the retention percentage of a given word at a specific point in time.

  Args:
  * `db`   - the database instance
  * `word` - the ID of the word entity
  * `now`  - the current time as java.util.Date or js/Date instant"

  [db word now]
  (let [reviews         (reviews db word)
        retentions      (map first reviews)
        timestamps      (map (comp utils/timestamp:instant->unix second) reviews)
        timedeltas      (map -  (rest timestamps) timestamps)
        forgetting-rate (reduce
                         (fn [forgetting-rate [retained? timedelta]]
                           (if retained?
                             (/ forgetting-rate (+ 1 (* forgetting-rate timedelta)))
                             (* 2 forgetting-rate)))
                         0.00000231 ; approx 50% forgetting after 5 min
                         (map vector (rest retentions) timedeltas))

        time-since-last-review (- (utils/timestamp:instant->unix now) (last timestamps))]
    (* 100 (math/exp (- (* forgetting-rate time-since-last-review))))))


(defn words
  "Returns a list of words filtered by a search pattern.
  The pattern is matched against either the word's value or its translation,
  allowing the user to search in either language.

  Args:
  * `db`             – the database instance
  * `user`           – the ID of the user entity
  * `search-pattern` – a string in either German or Russian"

  [db user & {:keys [search-pattern]}]
  (d/q '[:find ?word
         :in % $ ?user ?search-pattern
         :where
         [_ :user/vocabulary ?word]
         (match-word ?word ?search-pattern)]

       '[[(match-word ?word ?search-pattern)
          [?word :dictionary.word/value ?word-value]
          [(utils/includes? ?word-value ?search-pattern)]]

         [(match-word ?word ?search-pattern)
          [?word :dictionary.word/translation ?wt]
          [?wt :dictionary.word/value ?translation]
          [(utils/includes? ?translation ?search-pattern)]]]
       db user search-pattern))


(comment
  (defn setup-test-db
    []
    (d/db-with
     (d/db conn)
     [{:db/ident :language/DE}
      {:db/ident :language/RU}
      {:db/id                        "hund"
       :dictionary.word/value        "der Hund"
       :dictionary.word/translations ["пёс"]
       :dictionary.word/language     :language/DE}
      {:db/id                        "пёс"
       :dictionary.word/value        "пёс"
       :dictionary.word/translations ["hund"]
       :dictionary.word/language     :language/RU}
      {:db/id                        "kater"
       :dictionary.word/value        "der Kater"
       :dictionary.word/translations ["кот"]
       :dictionary.word/language     :language/DE}
      {:db/id                        "кот"
       :dictionary.word/value        "кот"
       :dictionary.word/translations ["kater"]
       :dictionary.word/language     :language/RU}
      {:user/name     "Egor Shundeev"
       :user/email    "shundeevegor@gmail.com"
       :user/password (str (hash "3434"))
       :user/vocabulary
       [{:vocabulary.word/value        "der Hund"
         :vocabulary.word/language     :language/DE
         :vocabulary.word/translations [["пёс" :language/RU]
                                        ["псинус" :language/RU]
                                        ["пёсинус" :language/RU]]
         :vocabulary.word/reviews      [[true #inst "2025-07-22T12:00"]]}
        {:vocabulary.word/value        "der Kater"
         :vocabulary.word/language     :language/DE
         :vocabulary.word/translations [["кот" :language/RU]
                                        ["котовичок" :language/RU]]
         :vocabulary.word/reviews      [[true #inst "2025-07-22T12:00"]]}]}]))

  (->> (d/q '[:find ?eid ?a ?v
              :where
              [?eid ?a ?v]]
            (d/db conn)))
  (vals (d/pull @conn '[*] 8))

  (defn test-user
    [db]
    (-> db (d/entity [:user/email "shundeevegor@gmail.com"]) :db/id))

  (defn do-test-review
    [db word retained? instant]
    (d/db-with
     db
     [{:db/id word
       :vocabulary.word/reviews [[retained? instant]]}]))

  (setup-test-db)

  ;; Testing db setup
  (let [db (setup-test-db)]
    (d/pull db '[*] (test-user db)))

  ;; Testing `words` function
  (let [db   (setup-test-db)
        user (test-user db)]
    (words db user {:search-pattern ""}))

  ;; Testing `reviews` function
  (let [db   (setup-test-db)
        user (test-user db)
        word (ffirst (words db user {:search-pattern "hund"}))
        db   (do-test-review db word true #inst "2025-07-25T20:00")
        db   (do-test-review db word true #inst "2025-07-25T20:01")]
    (reviews db word))

  ;; Testing `retention-level` function
  (let [db   (setup-test-db)
        user (test-user db)
        word (ffirst (words db user {:search-pattern "hund"}))
        db   (do-test-review db word true #inst "2025-07-26T12:00")]
    (retention-level db word #inst "2025-07-29T12:00"))

  ;; Testing retraction
  (let [db   (setup-test-db)
        user (test-user db)
        word (ffirst (words db user {:search-pattern "hund"}))
        db   (d/db-with
              db
              [[:db/retractEntity word]])]
    (d/pull db '[*] (test-user db))))

