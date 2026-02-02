(ns dictionary-sync
  (:require
   [db :as db]
   [dbs :as dbs]
   [lambdaisland.glogi :as log]
   [promesa.core :as p]))


(def ^:private state
  (atom {:status :idle :promise nil}))


(defn loaded?
  []
  (= :ready (:status @state)))


(defn- dictionary-loaded?
  [dict-db]
  (p/let [doc (db/get dict-db "dictionary-state")]
    (some? doc)))


(defn- run-replication!
  [dict-db]
  (p/create
   (fn [resolve reject]
     (let [repl (db/replicate-from dict-db)]
       (.on repl "complete"
            (fn [info]
              (log/info :dictionary-sync/replication-complete
                        {:docs-read  (.. info -docs_read)
                         :docs-written (.. info -docs_written)})
              (resolve info)))
       (.on repl "error"
            (fn [err]
              (log/error :dictionary-sync/replication-error {:error (str err)})
              (reject err)))))))


(defn- start-sync!
  []
  (let [dict-db (dbs/dictionary-db)]
    (p/let [already-loaded (dictionary-loaded? dict-db)]
      (if already-loaded
        (do
          (log/info :dictionary-sync/already-loaded {})
          :already-loaded)
        (p/do
          (log/info :dictionary-sync/starting {})
          (run-replication! dict-db)
          (p/let [loaded (dictionary-loaded? dict-db)]
            (when-not loaded
              (throw (ex-info "dictionary-state doc not found after replication" {}))))
          (log/info :dictionary-sync/complete {})
          :complete)))))


(defn ensure-loaded!
  []
  (let [{:keys [status promise]} @state]
    (case status
      :ready   (p/resolved true)
      :syncing promise
      ;; :idle or :failed — (re)try
      (let [p (-> (p/do
                    (start-sync!)
                    (swap! state assoc :status :ready :promise nil)
                    true)
                  (p/catch
                    (fn [err]
                      (log/error :dictionary-sync/error {:error (str err)})
                      (swap! state assoc :status :failed :promise nil)
                      false)))]
        (swap! state assoc :status :syncing :promise p)
        p))))
