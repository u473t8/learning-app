(ns vocabulary
  (:require
   [clojure.math :as math]
   [db :as db]
   [promesa.core :as p]
   [utils :as utils]))

(def db
  (db/use "local-db"))

(defn add-word
  [value translation]
  (let [vocab-entry {:type "vocab",
                     :value value,
                     :translation [{:lang "ru" :value translation}]}]
    (p/let [{:keys [id]} (db/insert db vocab-entry)
            review-entry {:type "review",
                          :word-id id
                          :retained true,
                          :timestamp (utils/now-iso)
                          :translation [{:lang "ru" :value translation}]}]
      (db/insert db review-entry)
      id)))

;; approx 50% forgetting after 5 min
(def ^:private initial-forgetting-rate 0.00231)

(defn retention-level
  [reviews]
  (let [;; Convert ISO review timestamps to unix seconds for the following timestamp arithmetics
        reviews (map #(update % :timestamp utils/timestamp:iso->unix) reviews)
        ;; Order reviews chronologically
        reviews (sort-by :timestamp reviews)

        last-review-timestamp (-> reviews last :timestamp)

        ;; Enrich each review with information about time passed since previous review
        reviews (->> reviews
                     (reverse)
                     (partition 2)
                     (map (fn [[review-1 review-0]]
                            (assoc review-1 :time-since-previous-review (- (:timestamp review-1) (:timestamp review-0)))))
                     (reverse))
        forgetting-rate (reduce
                         (fn [forgetting-rate {:keys [retained time-since-previous-review]}]
                           (if retained
                             (/ forgetting-rate (+ 1 (* forgetting-rate time-since-previous-review)))
                             (* 2 forgetting-rate)))
                         initial-forgetting-rate
                         reviews)
        time-since-last-review (- (utils/now-unix) last-review-timestamp)]
    (* 100 (math/exp (- (* forgetting-rate time-since-last-review))))))

(defn words
  []
  (p/let [{words :docs} (db/find db {:selector {:type "vocab"}})
          {reviews :docs} (db/find db {:selector {:type "review"}})]
    (sort-by
     :retention-level
     > ; descending order
     (for [word words]
       {:id (:_id word)
        :value (:value word)
        :translation (->> (:translation word)
                          (filter #(-> % :lang (= "ru")))
                          (map :value)
                          first)
        :retention-level (retention-level (filter #(-> % :word-id (= (:_id word))) reviews))}))))

(defn words-count
  []
  (p/let [{words :docs} (db/find db {:selector {:type "vocab"}})]
    (count words)))

(defn get-word
  [word-id]
  (p/let [doc (db/get db word-id)
          {reviews :docs} (db/find db {:selector {:type "review", :word-id word-id}})]
    {:id word-id
     :value (:value doc)
     :translation (->> (:translation doc)
                       (filter #(-> % :lang (= "ru")))
                       (map :value)
                       first)
     :retention-level (retention-level reviews)}))

(defn change-word
  [word-id value translation]
  (p/let [doc (db/get db word-id)
          new-doc (assoc doc :value value, :translation [{:lang "ru", :value translation}])]
    (db/insert db new-doc)
    (p/let [{reviews :docs} (db/find db {:selector {:type "review", :word-id word-id}})]
      {:id word-id,
       :value value,
       :translation (->> (:translation new-doc)
                         (filter #(-> % :lang (= "ru")))
                         (map :value)
                         first)
       :retention-level (retention-level reviews)})))

(defn delete-word
  [word-id]
  (p/let [doc (db/get db word-id)]
    (db/remove db doc)))
