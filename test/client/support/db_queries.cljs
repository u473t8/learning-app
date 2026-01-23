(ns client.support.db-queries
  (:require
   [db :as db]
   [promesa.core :as p]))


(defn fetch-by-type
  [db doc-type]
  (p/let [{:keys [docs]} (db/find db {:selector {:type doc-type}})]
    docs))


(defn fetch-examples
  [db]
  (fetch-by-type db "example"))
