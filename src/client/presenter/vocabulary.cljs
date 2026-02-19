(ns presenter.vocabulary
  "Adapts vocabulary retrieval models for UI views."
  (:require
   [clojure.string :as str]))


(defn word-item-props
  [{:keys [_id value translation retention-level]}]
  {:id          _id
   :value       value
   :retention-level retention-level
   :translation (->> translation
                     (filter #(= "ru" (:lang %)))
                     (map :value)
                     (str/join ", "))})


(defn word-list-props
  [words]
  (mapv word-item-props words))
