(ns presenter.vocabulary
  "Adapts vocabulary retrieval models for UI views.")


(defn word-item-props
  [{:keys [_id value translation retention-level]}]
  {:id          _id
   :value       value
   :retention-level retention-level
   :translation (->> translation
                     (filter #(= "ru" (:lang %)))
                     (map :value)
                     first)})


(defn word-list-props
  [words]
  (mapv word-item-props words))
