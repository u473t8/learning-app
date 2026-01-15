(ns lesson
  (:require
   [clojure.string :as str]
   [db :as db]
   [examples :as examples]
   [lambdaisland.glogi :as log]
   [promesa.core :as p]
   [utils :as utils]
   [vocabulary :as vocabulary]))


(def db
  (db/use "local-db"))


(def ^:private lesson-id "lesson")


(def ^:private words-per-lesson 3)


(def ^:private trial-type-word "word")


(def ^:private trial-type-example "example")


(defn- generate-trials
  "Generate trial objects from words. Each word gets a word trial,
   and if the word has an example, it also gets an example trial."
  [words]
  (vec
   (mapcat (fn [word]
             (cond-> [{:word-id (:id word) :type trial-type-word}]
               (:example word) (conj {:word-id (:id word) :type trial-type-example})))
    words)))


(defn- random-trial
  "Select a random trial from the given trials vector."
  [trials]
  (when (seq trials)
    (rand-nth trials)))


(defn example-trial?
  "Returns true if trial is an example trial."
  [trial]
  (= (:type trial) trial-type-example))


(defn word-trial?
  "Returns true if trial is a word trial."
  [trial]
  (= (:type trial) trial-type-word))


(defn- find-word
  "Find a word in the words array by its id."
  [words word-id]
  (first (filter #(= (:id %) word-id) words)))


(defn initial-state
  "Create a new lesson state from words."
  [words]
  (let [trials (generate-trials words)]
    {:_id         lesson-id
     :type        "lesson"
     :started-at  (utils/now-iso)
     :words       words
     :trials      trials
     :remaining-trials trials
     :current-trial (random-trial trials)
     :last-result nil}))


(defn get-state
  []
  (db/get db lesson-id))


(defn ensure!
  []
  (p/let [existing (get-state)]
    (if existing
      existing
      (p/let [words (vocabulary/words-for-lesson words-per-lesson)]
        (when (seq words)
          (log/debug :ensure-lesson/start words)
          (let [lesson-state (initial-state (vec words))]
            (p/let [{:keys [rev]} (db/insert db lesson-state)]
              (assoc lesson-state :_rev rev))))))))


(defn expected-answer
  "Get the expected answer for a trial."
  [state]
  (let [current-trial (:current-trial state)
        word (find-word (:words state) (:word-id current-trial))]
    (if (example-trial? current-trial)
      (-> word :example :value)
      (:value word))))


(defn- normalized-answer
  [answer]
  (-> (or answer "")
      (str/lower-case)
      (str/replace #"[äöüß]" {"ä" "a" "ö" "o" "ü" "u" "ß" "s"})
      (str/replace (js/RegExp. "[^\\p{L}\\p{N}]" "gu") " ")
      (str/replace #"\\s+" " ")
      (str/trim)))


(defn- remove-trial
  "Remove a specific trial from the remaining-trials vector."
  [remaining-trials trial]
  (vec (remove #(and (= (:word-id %) (:word-id trial))
                     (= (:type %) (:type trial)))
               remaining-trials)))


(defn check-answer!
  "Check the user's answer for the current trial.
   Returns {:correct? :correct-answer :is-finished? :state}"
  [state answer]
  (let [current-trial (:current-trial state)
        word          (find-word (:words state) (:word-id current-trial))
        answer-text   (expected-answer state)
        correct?      (= (normalized-answer answer)
                         (normalized-answer answer-text))
        remaining     (if correct?
                        (remove-trial (:remaining-trials state) current-trial)
                        (:remaining-trials state))
        is-finished?  (and correct? (empty? remaining))
        updated-state (assoc state :remaining-trials remaining :last-result {:correct? correct?})]
    ;; Fire-and-forget: rotate example in background (don't block UI)
    (when (and correct?
               (example-trial? current-trial)
               (:example word)
               (examples/online?))
      (-> (p/do
            (vocabulary/delete-example (-> word :example :id))
            (examples/fetch-one (:value word)))
          (p/then
           (fn [new-example]
             (when new-example
               (vocabulary/save-example (:id word) (:value word) new-example))))))
    ;; Main flow: save lesson and return result
    (p/do
      ;; Create review document (only for word trials)
      (when (word-trial? current-trial)
        (vocabulary/add-review (:id word) correct? (:translation word)))
      ;; Save updated lesson
      (p/let [{:keys [rev]} (db/insert db updated-state)]
        {:correct-answer answer-text
         :correct?     correct?
         :is-finished? is-finished?
         :state        (assoc updated-state :_rev rev)}))))


(defn advance!
  "Select the next random trial from remaining trials.
   Returns the updated state, or nil if no more trials."
  [state]
  (let [remaining (:remaining-trials state)]
    (log/debug :advance-lesson/remaining remaining)
    (when (seq remaining)
      (let [next-trial    (random-trial remaining)
            updated-state (assoc state
                                 :current-trial next-trial
                                 :last-result   nil)]
        (p/let [{:keys [rev]} (db/insert db updated-state)]
          (log/debug :advance-lesson/updated-lesson updated-state)
          (assoc updated-state :_rev rev))))))


(defn finish!
  []
  (p/let [existing (get-state)]
    (when existing
      (db/remove db existing))))
