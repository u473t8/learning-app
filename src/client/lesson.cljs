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


(defn lesson-doc
  "Create a new lesson document from words."
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


(defn get-lesson
  []
  (db/get db lesson-id))


(defn start-lesson!
  []
  (p/let [existing (get-lesson)
          words    (vocabulary/words-for-lesson words-per-lesson)]
    (log/debug :start-lesson/words words)
    (when (seq words)
      (let [doc (cond-> (lesson-doc (vec words))
                  existing (assoc :_rev (:_rev existing)))]
        (p/let [{:keys [rev]} (db/insert db doc)]
          (assoc doc :_rev rev))))))


(defn ensure-lesson!
  []
  (p/let [existing (get-lesson)]
    (if existing
      existing
      (start-lesson!))))


(defn- correct-answer
  "Get the expected answer for a trial."
  [lesson]
  (let [current-trial (:current-trial lesson)
        word (find-word (:words lesson) (:word-id current-trial))]
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
   Returns {:correct? :correct-answer :is-finished? :lesson}"
  [lesson answer]
  (let [current-trial (:current-trial lesson)
        word          (find-word (:words lesson) (:word-id current-trial))
        answer-text   (correct-answer lesson)
        correct?      (= (normalized-answer answer)
                         (normalized-answer answer-text))
        remaining     (if correct?
                        (remove-trial (:remaining-trials lesson) current-trial)
                        (:remaining-trials lesson))
        is-finished?  (and correct? (empty? remaining))
        updated-lesson (assoc lesson
                              :remaining-trials remaining
                              :last-result      {:correct? correct? :correct-answer answer-text})]
    ;; Fire-and-forget: rotate example in background (don't block UI)
    (when (and correct?
               (example-trial? current-trial)
               (:example word)
               (examples/online?))
      (-> (p/do
            (vocabulary/delete-example (-> word :example :id))
            (examples/fetch-example (:value word)))
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
      (p/let [{:keys [rev]} (db/insert db updated-lesson)]
        {:correct-answer answer-text
         :correct?     correct?
         :is-finished? is-finished?
         :lesson       (assoc updated-lesson :_rev rev)}))))


(defn advance-lesson!
  "Select the next random trial from remaining trials.
   Returns the updated lesson, or nil if no more trials."
  [lesson]
  (let [remaining (:remaining-trials lesson)]
    (log/debug :advance-lesson/remaining remaining)
    (when (seq remaining)
      (let [next-trial     (random-trial remaining)
            updated-lesson (assoc lesson
                                  :current-trial next-trial
                                  :last-result   nil)]
        (p/let [{:keys [rev]} (db/insert db updated-lesson)]
          (log/debug :advance-lesson/updated-lesson updated-lesson)
          (assoc updated-lesson :_rev rev))))))


(defn finish-lesson!
  []
  (p/let [existing (get-lesson)]
    (when existing
      (db/remove db existing))))
