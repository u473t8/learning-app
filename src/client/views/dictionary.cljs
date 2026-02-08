(ns views.dictionary
  "Dictionary autocomplete suggestion rendering.")


(defn suggestions
  "Renders a list of autocomplete suggestions as an HTML fragment.
   `items` is a seq of maps with :lemma. `prefill` is a string or nil
   indicating an exact match."
  [items prefill]
  (when (seq items)
    (for [{:keys [lemma translation]} items]
      [:li.suggestions__item
       (cond-> {:data-ac-role     "item"
                :data-lemma       (or lemma "")
                :data-translation (or translation "")}
         (= lemma prefill) (assoc :data-exact true))
       lemma])))
