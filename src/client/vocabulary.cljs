(ns vocabulary
  (:refer-clojure :exclude [list count get])
  (:require
   [clojure.core :as clojure]
   [db :as db]
   [domain.vocabulary :as domain]
   [promesa.core :as p]
   [retention :as retention]
   [utils :as utils]))


(defn add!
  "Adds a new vocabulary word with an initial review."
  [dbs value translation]
  (let [now-iso (utils/now-iso)
        word    (domain/new-word value translation now-iso)]
    (p/let [{:keys [id]} (db/insert (:user/db dbs) word)
            review       (domain/new-review id true translation now-iso)]
      (db/insert (:user/db dbs) review)
      id)))


(defn get
  "Returns a word row by id, or nil if not found."
  [dbs word-id]
  (p/let [word (db/get (:user/db dbs) word-id)]
    (when word
      (p/let [retention-level (retention/level dbs word-id)]
        (assoc word :retention-level retention-level)))))


(defn list
  "Returns vocabulary rows with retention levels."
  ([dbs] (list dbs {}))
  ([dbs {:keys [order limit offset search]
         :or   {order  :desc}}]
   (p/let [{words :docs}    (db/find-all (:user/db dbs) {:selector {:type "vocab"}})
           retention-levels (retention/levels dbs (mapv :_id words))]

     (let [total-count        (clojure/count words)
           word-id->retention (->> retention-levels
                                   (map (juxt :word-id :retention-level))
                                   (into {}))
           words (cond->> words
                   (utils/non-blank search)
                   (filter (fn [{:keys [value translation]}]
                             (or (utils/includes? value search)
                                 (some #(utils/includes? (:value %) search) translation)))))

           words (->> words
                      (map (fn [word]
                             (assoc word :retention-level (word-id->retention (:_id word) 0))))
                      (sort-by :retention-level (if (= order :asc) < >)))
           words (cond->> words
                   offset   (drop offset)
                   limit    (take limit))]
       {:total total-count
        :words (vec words)}))))


(defn count
  "Returns the total number of vocabulary words."
  [dbs]
  (p/let [{words :docs} (db/find-all (:user/db dbs) {:selector {:type "vocab"}})]
    (clojure/count words)))


(defn update!
  "Updates a word's value and translation. Returns updated row, or nil if not found."
  [dbs word-id value translation]
  (p/let [word (db/get (:user/db dbs) word-id)]
    (when word
      (p/let [word (domain/update-word word value translation (utils/now-iso))
              _ (db/insert (:user/db dbs) word)
              retention-level (retention/level dbs word-id)]
        (assoc word :_id word-id :retention-level retention-level)))))


(defn delete!
  "Deletes a word and all its associated reviews and examples.
   No-op if word doesn't exist."
  [dbs word-id]
  (p/let [word (db/get (:user/db dbs) word-id)]
    (when word
      (p/let [{reviews :docs}  (db/find-all (:user/db dbs) {:selector {:type "review" :word-id word-id}})
              {examples :docs} (db/find-all (:user/db dbs) {:selector {:type "example" :word-id word-id}})]
        (p/all (map #(db/remove (:user/db dbs) %) reviews))
        (p/all (map #(db/remove (:user/db dbs) %) examples))
        (db/remove (:user/db dbs) word)))))


(defn add-review
  "Creates a review document for a word and updates its retention model."
  [dbs word-id retained translation]
  (let [review (domain/new-review word-id retained translation (utils/now-iso))]
    (p/let [insert-result (db/insert (:user/db dbs) review)]
      insert-result)))
