(ns sw)


(defmacro resources
  [urls]
  (let [resources-list (atom [])]
    (doseq [url urls]
      (let [revision (hash (slurp (str "resources/public" url)))]
        (swap! resources-list conj {:url url :revision revision})))
    @resources-list))
