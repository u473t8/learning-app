(ns domain.vocabulary
  "Pure vocabulary logic extracted from IO-heavy client code."
  (:require
   [clojure.math :as math]
   [clojure.string :as str]
   [utils :as utils]))


(defn validate-new-word
  "Validates a new word. Returns {:ok data} or {:error details}."
  [value translation]
  (let [value-blank?       (str/blank? value)
        translation-blank? (str/blank? translation)]
    (if (or value-blank? translation-blank?)
      {:error {:value-blank? value-blank? :translation-blank? translation-blank?}}
      {:ok {:value value :translation translation}})))


(defn validate-word-update
  "Validates a word update. Returns {:ok data} or {:error details}."
  [{:keys [word-id value translation]}]
  (let [value-blank?       (str/blank? value)
        translation-blank? (str/blank? translation)]
    (cond
      (str/blank? word-id)
      {:error {:word-id-missing? true}}

      (or value-blank? translation-blank?)
      {:error {:value-blank? value-blank? :translation-blank? translation-blank?}}

      :else
      {:ok {:word-id word-id :value value :translation translation}})))


;; Forgetting rate constant calibrated for seconds
(def ^:private initial-forgetting-rate 0.00231)


(defn- ms->secs [ms] (quot ms 1000))


(defn new-word
  "Create a new vocab document from inputs."
  [value translation created-at]
  {:type        "vocab"
   :value       value
   :translation [{:lang "ru" :value translation}]
   :created-at  created-at
   :modified-at created-at})


(defn new-review
  "Create a new review document for a word."
  [word-id retained translation created-at]
  {:type        "review"
   :word-id     word-id
   :retained    retained
   :created-at  created-at
   :translation [{:lang "ru" :value translation}]})


(defn update-word
  "Update a vocab document with new values."
  [doc value translation modified-at]
  (cond-> (assoc doc
                 :value       value
                 :translation [{:lang "ru" :value translation}]
                 :modified-at modified-at)
    (nil? (:created-at doc)) (assoc :created-at modified-at)))


(defn retention-level
  "Calculate retention percentage for a list of reviews.
   Takes now-ms (current time in milliseconds)."
  [reviews now-ms]
  (let [now-secs        (ms->secs now-ms)
        reviews         (map #(update % :created-at (comp ms->secs utils/iso->ms)) reviews)
        reviews         (sort-by :created-at reviews)
        last-review-timestamp (-> reviews last :created-at)
        reviews         (->> reviews
                             (reverse)
                             (partition 2)
                             (map (fn [[review-1 review-0]]
                                    (assoc review-1
                                           :time-since-previous-review
                                           (- (:created-at review-1) (:created-at review-0)))))
                             (reverse))
        forgetting-rate (reduce
                         (fn [rate {:keys [retained time-since-previous-review]}]
                           (if retained
                             (/ rate (+ 1 (* rate time-since-previous-review)))
                             (* 2 rate)))
                         initial-forgetting-rate
                         reviews)
        time-since-last-review (- now-secs last-review-timestamp)]
    (* 100 (math/exp (- (* forgetting-rate time-since-last-review))))))


(defn word-summary
  "Builds a normalized word summary from vocab + reviews.
   Takes now-ms (current time in milliseconds)."
  [word reviews now-ms]
  {:id          (:_id word)
   :value       (:value word)
   :translation (->> (:translation word)
                     (filter #(-> % :lang (= "ru")))
                     (map :value)
                     first)
   :retention-level (retention-level reviews now-ms)})


(defn summarize-words
  "Build word summaries from vocab/reviews.
   Takes now-ms (current time in milliseconds).
   Options:
   - :order :asc/:desc (default :desc)
   - :limit number
   - :offset number
   - :search query"
  [words reviews now-ms
   {:keys [order limit offset search]
    :or   {order :desc}}]
  (let [reviews-by-word (group-by :word-id reviews)
        summaries       (->> words
                             (map (fn [word]
                                    (word-summary word
                                                  (get reviews-by-word (:_id word))
                                                  now-ms)))
                             (filter (fn [summary]
                                       (if (utils/non-blank search)
                                         (or (utils/includes? (:value summary) search)
                                             (utils/includes? (:translation summary) search))
                                         true)))
                             (sort-by :retention-level (if (= order :asc) < >)))]
    (cond->> summaries
      offset (drop offset)
      limit  (take limit))))
