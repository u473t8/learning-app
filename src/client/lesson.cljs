(ns lesson
  (:require
   [db :as db]
   [domain.lesson :as domain]
   [examples :as examples]
   [lambdaisland.glogi :as log]
   [promesa.core :as p]
   [utils :as utils]
   [vocabulary :as vocabulary]))


(defn- state
  [device-db]
  (db/get device-db domain/lesson-id))


(defn start!
  "Start a new lesson. Fetches words and examples, creates and persists lesson state.
   Returns {:lesson-state ...} or {:error ...}"
  ([user-db device-db]
   (start! user-db device-db {}))
  ([user-db device-db
    {:keys [words-per-lesson trial-selector]
     :or   {words-per-lesson domain/default-words-per-lesson}}]
   (p/catch
     (p/let [selected-words (vocabulary/list user-db {:order :asc :limit words-per-lesson})]
       (if-not (seq selected-words)
         {:error :no-words-available}
         (p/let [word-ids      (mapv :id selected-words)
                 examples      (examples/list device-db word-ids)
                 lesson-state  (domain/initial-state selected-words examples trial-selector (utils/now-iso))
                 {:keys [rev]} (db/insert device-db lesson-state)]
           {:lesson-state (assoc lesson-state :_rev rev)})))
     (fn [err]
       (log/error :lesson/start-failed {:error (ex-message err)})
       {:error :lesson-start-failed}))))


(defn ensure!
  "Returns existing lesson or starts a new one.
   Returns {:lesson-state ...} or {:error ...}"
  ([user-db device-db]
   (ensure! user-db device-db {}))
  ([user-db device-db opts]
   (p/let [existing (state device-db)]
     (if existing
       {:lesson-state existing}
       (start! user-db device-db opts)))))


(defn check-answer!
  "Check the user's answer for the current trial.
   Returns {:result {:correct? :correct-answer :is-finished?} :lesson-state ...}
   On error returns {:error :keyword :lesson-state ...}"
  [user-db device-db answer]
  (p/let [current-state (state device-db)]
    (if-not current-state
      (do
        (log/warn :lesson/check-answer-missing {:answer answer})
        {:error        :lesson-not-found
         :lesson-state nil})
      (let [current-trial (domain/current-trial current-state)
            lesson-state  (domain/check-answer current-state answer)]
        (p/catch
          (p/do
            (when (domain/word-trial? current-trial)
              (vocabulary/add-review
               user-db
               (:word-id current-trial)
               (-> lesson-state domain/last-result :correct?)
               (:prompt current-trial)))
            (p/let [{:keys [rev]} (db/insert device-db lesson-state)]
              {:lesson-state (assoc lesson-state :_rev rev)}))
          (fn [err]
            (log/error :lesson/check-answer-save-failed {:error (ex-message err)})
            {:error        :lesson-save-failed
             :lesson-state lesson-state}))))))


(defn advance!
  "Select the next random trial from remaining trials.
   Returns {:lesson-state ...} or {:error ...} or nil if no more trials."
  [device-db]
  (p/let [lesson-state (state device-db)]
    (if-not lesson-state
      (do
        (log/warn :advance-lesson/missing-state {})
        {:error :lesson-not-found})
      (when-let [next-state (domain/advance lesson-state)]
        (p/catch
          (p/let [{:keys [rev]} (db/insert device-db next-state)]
            {:lesson-state (assoc next-state :_rev rev)})
          (fn [err]
            (log/error :advance-lesson/save-failed {:error (ex-message err)})
            {:error :lesson-save-failed}))))))


(defn finish!
  [device-db]
  (p/let [existing (state device-db)]
    (when existing
      (db/remove device-db existing))))
