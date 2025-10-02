(ns shared.lesson)

(def words-per-lesson 3)


(defn challenge-source
  [user-id])


(defn submit-user-answer!
  [user-id user-answer])


(defn challenge-passed?
  [user-id])


(defn challenge-answer
  [user-id])


(defn all-challenges-passed?
  [user-id])


(defn lesson-progress
  [ user-id])


(defn start-lesson!
  [ user-id])


(defn start-challenge!
  [user-id])


(defn current-challenge
  [user-id])


(defn next-challenge!
  [user-id])


(defn finish-lesson!
  "remove lesson from db, refresh examples (if possible)"
  [user-id])
