(ns dbs
  (:require
   [db :as db]))


(def legacy-local-db-name "local-db")


(def user-db-name "user-db")


(def device-db-name "device-db")


(def dictionary-db-name "dictionary-db")


(defn local-db
  []
  (db/use legacy-local-db-name))


(defn user-db
  []
  (db/use user-db-name))


(defn device-db
  []
  (db/use device-db-name))


(defn dictionary-db
  []
  (db/use dictionary-db-name))


(defn dbs
  []
  {:user/db       (user-db)
   :device/db     (device-db)
   :dictionary/db (dictionary-db)})
