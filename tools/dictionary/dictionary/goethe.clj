(ns dictionary.goethe
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]))


(defn stem-level-index
  "Parse Goethe CSV, return [[stem level] ...] sorted by stem length descending."
  [csv-path]
  (with-open [reader (io/reader csv-path)]
    (let [rows      (csv/read-csv reader)
          header    (first rows)
          stem-idx  (.indexOf header "stem")
          level-idx (.indexOf header "level")]
      (->> (rest rows)
           (keep (fn [row]
                   (let [stem  (str/lower-case (str/trim (nth row stem-idx "")))
                         level (str/lower-case (str/trim (nth row level-idx "")))]
                     (when (and (seq stem) (seq level))
                       [stem level]))))
           (sort-by (fn [[stem _]] (- (count stem))))
           (vec)))))


(defn cefr-level
  "Find longest stem that is a prefix of (str/lower-case word).
   Index must be pre-sorted by stem length descending."
  [index word]
  (let [w (str/lower-case word)]
    (some (fn [[stem level]]
            (when (str/starts-with? w stem) level))
          index)))
