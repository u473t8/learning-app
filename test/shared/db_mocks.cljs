(ns shared.db-mocks
  (:require
   [promesa.core :as p]))


(defn- match-condition
  [value condition]
  (cond
    (and (map? condition) (contains? condition :$lte))
    (<= value (:$lte condition))

    (and (map? condition) (contains? condition :$ne))
    (not= value (:$ne condition))

    (and (map? condition) (contains? condition :$exists))
    (if (:$exists condition) (some? value) (nil? value))

    :else
    (= value condition)))


(defn- match-selector?
  [doc selector]
  (if (seq selector)
    (every?
     (fn [[key value]]
       (if (= key :$or)
         (some #(match-selector? doc %) value)
         (match-condition (get doc key) value)))
     selector)
    true))


(defn- sort-pairs
  [sort-spec]
  (mapv (fn [entry]
          (first entry))
        sort-spec))


(defn- comparator
  [pairs]
  (fn [a b]
    (loop [[[key direction] & rest] pairs]
      (if key
        (let [av  (get a key)
              bv  (get b key)
              cmp (compare av bv)]
          (if (zero? cmp)
            (recur rest)
            (if (= direction :desc) (- cmp) cmp)))
        0))))


(defn make
  []
  (let [db      (atom {:docs {} :id-seq 0})
        next-id #(str "doc-" (:id-seq (swap! db update :id-seq inc)))]
    {:db     db
     :insert (fn [_ doc]
               (let [doc-id (or (:_id doc) (next-id))
                     doc    (assoc doc :_id doc-id)]
                 (swap! db update :docs assoc doc-id doc)
                 (p/resolved {:id doc-id :rev "1"})))
     :find   (fn [_ {:keys [selector sort limit skip]}]
               (let [docs       (vals (:docs @db))
                     docs       (filter #(match-selector? % selector) docs)
                     sort-pairs (when (seq sort) (sort-pairs sort))
                     docs       (if sort-pairs
                                  (sort (comparator sort-pairs) docs)
                                  docs)
                     docs       (cond->> docs
                                  skip  (drop skip)
                                  limit (take limit))]
                 (p/resolved {:docs (vec docs)})))
     :get    (fn [_ doc-id]
               (p/resolved (get-in @db [:docs doc-id])))
     :remove (fn [_ doc]
               (swap! db update :docs dissoc (:_id doc))
               (p/resolved true))
     :use    (constantly @db)}))
