(ns vocabulary
  (:refer-clojure :exclude [list count get])
  (:require
   [clojure.core :as clojure]
   [db :as db]
   [domain.vocabulary :as domain]
   [promesa.core :as p]
   [utils :as utils]))


(defn add!
  "Adds a new vocabulary word with an initial review.
   The initial review marks the first exposure to the word,
   providing a baseline timestamp for retention decay calculation."
  [db value translation]
  (let [now-iso (utils/now-iso)
        word    (domain/new-word value translation now-iso)]
    (p/let [{:keys [id]} (db/insert db word)
            review       (domain/new-review id true translation now-iso)]
      (db/insert db review)
      id)))


(defn get
  "Returns a word summary by id, or nil if not found."
  [db word-id]
  (p/let [word (db/get db word-id)]
    (when word
      (p/let [{reviews :docs} (db/find db {:selector {:type "review" :word-id word-id}})]
        (domain/word-summary word reviews (utils/now-ms))))))


(defn list
  "Returns vocabulary words with retention levels.
   Options:
   - :order - :desc (default, highest first) or :asc (lowest first)
   - :limit - max number of words to return (nil for all)
   - :offset - number of words to skip
   - :search - filter words by value/translation"
  ([db] (list db {}))
  ([db {:keys [order limit offset search] :or {order :desc}}]
   (p/let [{words :docs}   (db/find db {:selector {:type "vocab"}})
           {reviews :docs} (db/find db {:selector {:type "review"}})]
     (domain/summarize-words
      words
      reviews
      (utils/now-ms)
      {:order  order
       :limit  limit
       :offset offset
       :search search}))))


(defn count
  "Returns the total number of vocabulary words."
  [db]
  (p/let [{words :docs} (db/find db {:selector {:type "vocab"}})]
    (clojure/count words)))


(defn update!
  "Updates a word's value and translation. Returns updated summary, or nil if not found."
  [db word-id value translation]
  (p/let [word (db/get db word-id)]
    (when word
      (p/let [word (domain/update-word word value translation (utils/now-iso))
              _ (db/insert db word)
              {reviews :docs} (db/find db {:selector {:type "review" :word-id word-id}})]
        (domain/word-summary (assoc word :_id word-id) reviews (utils/now-ms))))))


(defn delete!
  "Deletes a word and all its associated reviews and examples.
   No-op if word doesn't exist."
  [db word-id]
  (p/let [word (db/get db word-id)]
    (when word
      (p/let [{reviews :docs}  (db/find db {:selector {:type "review" :word-id word-id}})
              {examples :docs} (db/find db {:selector {:type "example" :word-id word-id}})]
        (p/do
          (p/all (map #(db/remove db %) reviews))
          (p/all (map #(db/remove db %) examples))
          (db/remove db word))))))


(defn add-review
  "Creates a review document for a word."
  [db word-id retained translation]
  (let [review (domain/new-review word-id retained translation (utils/now-iso))]
    (db/insert db review)))
