(ns dictionary
  (:require
   [db :as db]
   [promesa.core :as p]
   [utils :as utils]))


(defn- build-query
  [normalized]
  {:startkey     (str "sf:" normalized)
   :endkey       (str "sf:" normalized "\uffff")
   :include-docs true
   :limit        50})


(defn- extract-entries
  [rows]
  (into []
        (comp (map :doc)
              (mapcat :entries))
        rows))


(defn- dedupe-by-lemma-id
  [entries]
  (->> entries
       (reduce (fn [acc entry]
                 (let [id (:lemma-id entry)]
                   (if (or (not (contains? acc id))
                           (> (:rank entry) (:rank (clojure.core/get acc id))))
                     (assoc acc id entry)
                     acc)))
               {})
       vals))


(defn- exact-entries
  [rows normalized]
  (some (fn [row]
          (when (= (:id row) (str "sf:" normalized))
            (get-in row [:doc :entries])))
        rows))


(defn- find-prefill
  [exact-entries _entries]
  (when (seq exact-entries)
    (:lemma (first exact-entries))))


(defn- attach-translations
  "Batch-fetches dictionary-entry docs and attaches first RU translation to each suggestion."
  [db suggestions]
  (if (empty? suggestions)
    (p/resolved suggestions)
    (p/let [ids    (mapv :lemma-id suggestions)
            result (db/all-docs db {:keys ids :include-docs true})
            trans  (into {}
                         (keep (fn [row]
                                 (when-let [doc (:doc row)]
                                   [(:id row)
                                    (->> (:translation doc)
                                         (some #(when (= "ru" (:lang %)) (:value %))))])))
                         (:rows result))]
      (mapv #(assoc % :translation (get trans (:lemma-id %))) suggestions))))


(defn suggest
  "Given a dbs map and user input, returns a promise of
   {:suggestions [...] :prefill <string-or-nil>}."
  [dbs input]
  (let [db         (:dictionary/db dbs)
        normalized (utils/normalize-german (or input ""))]
    (if (empty? normalized)
      (p/resolved {:suggestions [] :prefill nil})
      (p/let [result        (db/all-docs db (build-query normalized))
              rows          (:rows result)
              raw           (extract-entries rows)
              deduped       (dedupe-by-lemma-id raw)
              exact-entries (or (exact-entries rows normalized) [])
              exact-ids     (->> exact-entries (map :lemma-id) set)
              sorted        (->> deduped
                                 (sort-by (fn [entry]
                                            [(if (contains? exact-ids (:lemma-id entry)) 0 1)
                                             (- (:rank entry))]))
                                 (take 10)
                                 vec)
              prefill       (find-prefill exact-entries sorted)
              enriched      (attach-translations db sorted)]
        {:suggestions enriched
         :prefill     prefill}))))
