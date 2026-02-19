(ns domain.vocabulary
  "Pure vocabulary logic extracted from IO-heavy client code."
  (:require
   [clojure.string :as str]
   [utils :as utils]))


(defn normalize-value
  "Normalize a word value for duplicate matching using shared German normalization."
  [s]
  (utils/normalize-german (str s)))


(defn parse-translations
  "Split a comma-separated translation string into a vector of
   {:lang \"ru\" :value \"...\"} maps. Trims each, removes blanks."
  [s]
  (->> (str/split (str s) #"[,;.]")
       (map str/trim)
       (remove str/blank?)
       (mapv (fn [v] {:lang "ru" :value v}))))


(defn merge-translations
  "Merge two translation vectors, deduplicating by :value."
  [existing new-translations]
  (let [seen (set (map :value existing))]
    (into (vec existing)
          (remove #(seen (:value %)) new-translations))))


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


(defn new-word
  "Create a new vocab document from inputs.
   `translations` is a vector of {:lang :value} maps."
  [value translations created-at]
  {:type        "vocab"
   :value       value
   :translation translations
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
  "Update a vocab document with new values.
   `translation` is a scalar string that gets parsed into translation vectors."
  [doc value translation modified-at]
  (cond-> (assoc doc
                 :value       value
                 :translation (parse-translations translation)
                 :modified-at modified-at)
    (nil? (:created-at doc)) (assoc :created-at modified-at)))
