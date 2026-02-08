(ns db-migrations
  (:require
   [db :as db]
   [dbs :as dbs]
   [lambdaisland.glogi :as log]
   [promesa.core :as p]
   [utils :as utils]))


(def ^:private migration-id "migration:local-db-split")


(def ^:private doc-type-targets
  {"vocab"   :user
   "review"  :user
   "task"    :device
   "example" :device
   "lesson"  :device})


(defn- resolve-db
  [target]
  (case target
    :user   (dbs/user-db)
    :device (dbs/device-db)
    nil))


(defn- conflict?
  [err]
  (let [status (or (.-status err)
                   (:status err)
                   (get-in err [:body :status]))
        name   (or (.-name err) (:name err))]
    (or (= status 409)
        (= status "409")
        (= name "conflict"))))


(defn- strip-rev
  [doc]
  (dissoc doc :_rev))


(defn- copy-doc!
  [db doc]
  (p/catch
    (db/insert db (strip-rev doc))
    (fn [err]
      (if (conflict? err)
        nil
        (throw err)))))


(defn- copy-type!
  [local-db dest-db doc-type]
  (p/let [{:keys [docs]} (db/find local-db {:selector {:type doc-type}})]
    (when (seq docs)
      (log/info :db-migrations/copy-type {:type doc-type :count (count docs)}))
    (p/all (map #(copy-doc! dest-db %) docs))))


(defn- run-local-db-split!
  []
  (let [device-db (dbs/device-db)]
    (p/let [marker (db/get device-db migration-id)]
      (if marker
        (do
          (log/info :db-migrations/already-complete {:id migration-id})
          :already-complete)
        (let [local-db (dbs/local-db)]
          (log/info :db-migrations/start {:id migration-id})
          (p/do
            (p/doseq [[doc-type target] doc-type-targets]
              (copy-type! local-db (resolve-db target) doc-type))
            (db/insert
             device-db
             {:_id          migration-id
              :type         "migration"
              :migration-id "local-db-split"
              :source       "local-db"
              :targets      ["user-db" "device-db"]
              :created-at   (utils/now-iso)})
            (log/info :db-migrations/complete {:id migration-id})
            :complete))))))


(def ^:private task-data-migration-id "migration:task-data-payload")


(defn- run-task-data-payload!
  []
  (let [device-db (dbs/device-db)]
    (p/let [marker (db/get device-db task-data-migration-id)]
      (if marker
        (do
          (log/info :db-migrations/already-complete {:id task-data-migration-id})
          :already-complete)
        (p/do
          (p/let [{:keys [docs]} (db/find device-db
                                          {:selector {:type    "task"
                                                      :word-id {:$exists true}
                                                      :data    {:$exists false}}})]
            (p/doseq [task docs]
              (db/insert device-db
                         (-> task
                             (assoc :data {:word-id (:word-id task)})
                             (dissoc :word-id)))))
          (db/insert device-db
                     {:_id          task-data-migration-id
                      :type         "migration"
                      :migration-id "task-data-payload"
                      :created-at   (utils/now-iso)})
          (log/info :db-migrations/complete {:id task-data-migration-id})
          :complete)))))


;; ---------------------------------------------------------------------------
;; Public state & API
;; ---------------------------------------------------------------------------


(def ^:private migrations
  [{:id  "migration:local-db-split"
    :run #(run-local-db-split!)}
   {:id  "migration:task-data-payload"
    :run #(run-task-data-payload!)}])


(def ^:private migration-state
  (atom {:status :not-started :promise nil}))


(defn migration-status [] (:status @migration-state))


(defn- run-all-migrations!
  []
  (-> (p/do
        (p/doseq [{:keys [run]} migrations]
          (run))
        (swap! migration-state assoc :status :done :promise nil)
        true)
      (p/catch
        (fn [err]
          (log/error :db-migrations/error {:error (str err)})
          (swap! migration-state assoc :status :failed :promise nil)
          (throw err)))))


(defn ensure-migrated!
  []
  (let [{:keys [status promise]} @migration-state]
    (case status
      :done        (p/resolved true)
      :in-progress promise
      ;; :not-started or :failed — (re)try
      (let [p (run-all-migrations!)]
        (swap! migration-state assoc :status :in-progress :promise p)
        p))))
