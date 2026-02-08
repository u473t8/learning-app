(ns tasks
  "Simple task runner: scans DB for tasks, runs them in parallel, pauses when offline."
  (:require
   [db :as db]
   [dbs :as dbs]
   [lambdaisland.glogi :as log]
   [promesa.core :as p]
   [utils :as utils]))


;; =============================================================================
;; Configuration
;; =============================================================================


(def ^:private config
  {:max-backoff-ms 60000
   :max-concurrent 3})


;; =============================================================================
;; Task Documents
;; =============================================================================


(defn create-task
  "Create a task document."
  [task-type data now-iso]
  {:type       "task"
   :task-type  task-type
   :data       data
   :attempts   0
   :run-at     now-iso
   :created-at now-iso})


;; =============================================================================
;; Task Execution
;; =============================================================================


(defmulti execute-task
  "Execute a task. Dispatches on :task-type.

   Arguments:
     task - the task document (map with :task-type, :data, etc.)
     dbs  - map with :device-db and :user-db PouchDB instances

   Returns a promise that resolves to:
     - truthy  -> task succeeded, will be removed
     - falsy   -> task failed, will be retried with exponential backoff
     - ::unknown-task -> unknown task type, will be dead-lettered"
  (fn [task _dbs] (:task-type task)))


(defmethod execute-task :default
  [task _dbs]
  (log/warn :tasks/unknown-type {:task-type (:task-type task)})
  ::unknown-task)


;; =============================================================================
;; Core Runner
;; =============================================================================


(defn- online?
  []
  js/self.navigator.onLine)


(defn- backoff-ms
  [attempts]
  (min (:max-backoff-ms config)
       (* 1000 (Math/pow 2 (max 0 attempts)))))


(def ^:private page-size 50)


(defn- ensure-task-index!
  [db]
  (p/catch
    (db/create-index
     db
     [:type :run-at :created-at]
     {:name "by-type-run-at-created-at"
      :ddoc "by-type-run-at-created-at"})
    (fn [err]
      (log/error :tasks/index-error {:error (str err)}))))


(defn- fetch-due-tasks
  [db now-iso]
  (p/let [{:keys [docs]}
          (db/find db
                   {:selector  {:type       "task"
                                :run-at     {:$lte now-iso}
                                :created-at {:$exists true}
                                :$or        [{:status {:$exists false}}
                                             {:status {:$ne "failed"}}]}

                    ;; Every index field in selector must appear in sort
                    :sort      [{:type :asc}
                                {:run-at :asc}
                                {:created-at :asc}]
                    :limit     page-size
                    :use-index "by-type-run-at-created-at"})]
    (vec docs)))


(defn- mark-failed!
  [db task]
  (let [attempts    (inc (or (:attempts task) 0))
        next-run-ms (+ (utils/now-ms) (backoff-ms attempts))
        next-run    (utils/ms->iso next-run-ms)]
    (db/insert db (assoc task :attempts attempts :run-at next-run))))


(defn- dead-letter!
  [db task reason]
  (let [now-iso (utils/now-iso)]
    (db/insert db
               (assoc task
                      :status         "failed"
                      :failure-reason reason
                      :failed-at      now-iso
                      :run-at         nil))))


(defn- remove-with-latest-rev!
  [db task]
  (p/catch
    (db/remove db task)
    (fn [err]
      (let [status (or (:status err) (get-in err [:body :status]))]
        (cond
          (= status 404) true
          (= status 409) (p/let [fresh (db/get db (:_id task))]
                           (if fresh
                             (db/remove db fresh)
                             true))
          :else          true)))))


(defn- run-task!
  [dbs task]
  (let [device-db (:device-db dbs)]
    (p/catch
      (p/let [result (execute-task task dbs)]
        (cond
          (= result ::unknown-task)
          (do
            (log/warn :tasks/dead-letter {:id (:_id task) :reason :unknown-task})
            (dead-letter! device-db task "unknown-task-type"))

          result
          (do
            (log/debug :tasks/completed {:id (:_id task)})
            (remove-with-latest-rev! device-db task))

          :else
          (do
            (log/debug :tasks/failed {:id (:_id task)})
            (mark-failed! device-db task))))

      (fn [err]
        (log/error :tasks/error {:id (:_id task) :error (str err)})
        (mark-failed! device-db task)))))


(defn- take-next-task!
  [queue]
  (let [result (atom nil)]
    (swap! queue
      (fn [tasks]
        (if (seq tasks)
          (do
            (reset! result (first tasks))
            (subvec tasks 1))
          tasks)))
    @result))


(defn- run-worker!
  [dbs queue]
  (p/loop []
    (let [task (take-next-task! queue)]
      (when task
        (p/do
          (run-task! dbs task)
          (p/recur))))))


(defn- run-workers!
  [dbs tasks]
  (let [queue (atom (vec tasks))]
    (p/all
     (repeatedly (:max-concurrent config) #(run-worker! dbs queue)))))


(def ^:private state (atom {}))


(declare flush!)


(defn- schedule-retry!
  "Schedule a delayed flush after a failed task."
  [delay-ms]
  (js/setTimeout flush! delay-ms))


(defn- nearest-retry-delay
  "Returns ms until the earliest run-at among remaining tasks, or nil."
  [db]
  (p/let [{:keys [docs]}
          (db/find db
                   {:selector  {:type       "task"
                                :run-at     {:$exists true}
                                :created-at {:$exists true}
                                :$or        [{:status {:$exists false}}
                                             {:status {:$ne "failed"}}]}
                    :sort      [{:type :asc}
                                {:run-at :asc}
                                {:created-at :asc}]
                    :limit     1
                    :use-index "by-type-run-at-created-at"})]
    (when-let [task (first docs)]
      (max 0 (- (utils/iso->ms (:run-at task)) (utils/now-ms))))))


(defn- run-cycle!
  [dbs]
  (let [device-db (:device-db dbs)]
    (p/do
      (p/loop []
        (when (and (online?) (:enabled? @state))
          (p/let [tasks (fetch-due-tasks device-db (utils/now-iso))]
            (log/debug :run-cycle/tasks tasks)
            (when (seq tasks)
              (p/do
                (run-workers! dbs tasks)
                (p/recur))))))
      ;; After processing, check if any tasks remain for retry
      (p/let [delay (nearest-retry-delay device-db)]
        (when delay
          (log/debug :tasks/scheduling-retry {:delay-ms delay})
          (schedule-retry! delay))))))


;; =============================================================================
;; Public API
;; =============================================================================


(defn flush!
  "Trigger a run cycle if enabled and online. Fire-and-forget: the promise
   chain is a self-contained side effect (with its own error handling),
   so callers never block on task execution."
  []
  (when-let [{:keys [dbs enabled? running?]} @state]
    (when (and (some? dbs) enabled? (online?) (not running?))
      (swap! state assoc :running? true)
      (-> (run-cycle! dbs)
          (p/catch #(log/error :tasks/flush-error {:error (str %)}))
          (p/finally #(swap! state assoc :running? false)))))
  nil)


(defn start!
  "Start the task runner."
  []
  (let [dbs {:device-db (dbs/device-db)
             :user-db   (dbs/user-db)}]
    (reset! state {:enabled? true :dbs dbs})

    (log/info :tasks/starting config)
    (p/do
      (ensure-task-index! (:device-db dbs))
      (flush!))))


(defn stop!
  []
  (reset! state {})
  (log/info :tasks/stopped {}))


(defn resume!
  []
  (log/debug :tasks/resuming {})
  (flush!))


(defn create-task!
  [task-type data]
  (let [db      (dbs/device-db)
        now-iso (utils/now-iso)]
    (p/do
      (db/insert db (create-task task-type data now-iso))
      (flush!))))
