(ns domain.word
  "Pure domain logic for words. No side effects, easy to test."
  (:require [clojure.string :as str]))


(defn validate-new-word
  "Validates a new word. Returns {:ok data} or {:error details}."
  [{:keys [value translation]}]
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
