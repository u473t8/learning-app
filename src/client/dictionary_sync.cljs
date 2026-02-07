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
  (p/let [doc (db/get dict-db "dictionary-meta")]
    (some? doc)))


(defn- run-replication!
  [dict-db]
  (p/create
   (fn [resolve reject]
     (let [repl (db/replicate-from dict-db {:batch-size 100 :batches-limit 1})]
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
    (p/let [loaded-before (dictionary-loaded? dict-db)]
      (p/do
        ;; Always run a one-shot pull replication on startup. PouchDB checkpoints
        ;; keep this incremental and cheap when there are no changes.
        (log/info :dictionary-sync/starting {:loaded-before loaded-before})
        (run-replication! dict-db)
        (p/let [loaded-after (dictionary-loaded? dict-db)]
          (when-not loaded-after
            (throw (ex-info "dictionary-meta doc not found after replication" {}))))
        (log/info :dictionary-sync/complete {:loaded-before loaded-before})
        :complete))))


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
        nil))))
