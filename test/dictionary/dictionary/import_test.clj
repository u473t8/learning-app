(ns dictionary.import-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [clojure.java.io :as io]
   [dictionary.import]))


;; Access private fns via var
(def ^:private parse-args #'dictionary.import/parse-args)


(def ^:private compare-meta #'dictionary.import/compare-meta)


(def ^:private jsonl-batches #'dictionary.import/jsonl-batches)


;; ---------------------------------------------------------------------------
;; parse-args (private)
;; ---------------------------------------------------------------------------


(deftest parse-args-all-flags
  (testing "parses all flags correctly"
    (let [opts (parse-args ["--input-dir" "/tmp/dict"
                            "--db" "my-db"
                            "--batch" "500"
                            "--max-bytes" "1000000"
                            "--reset"])]
      (is (= "/tmp/dict" (:input-dir opts)))
      (is (= "my-db" (:db opts)))
      (is (= 500 (:batch opts)))
      (is (= 1000000 (:max-bytes opts)))
      (is (true? (:reset opts))))))


(deftest parse-args-dry-run
  (testing "parses --dry-run flag"
    (let [opts (parse-args ["--input-dir" "/tmp/dict" "--dry-run"])]
      (is (true? (:dry-run opts)))
      (is (not (:reset opts))))))


(deftest parse-args-defaults
  (testing "uses defaults when flags not provided"
    (let [opts (parse-args ["--input-dir" "/tmp/dict"])]
      (is (= "dictionary-db" (:db opts)))
      (is (= 1000 (:batch opts)))
      (is (= 5242880 (:max-bytes opts)))
      (is (not (:reset opts))))))


(deftest parse-args-reset-positioning
  (testing "--reset can appear at different positions"
    (let [opts1 (parse-args ["--reset" "--input-dir" "/tmp/dict"])
          opts2 (parse-args ["--input-dir" "/tmp/dict" "--reset"])]
      (is (true? (:reset opts1)))
      (is (true? (:reset opts2))))))


;; ---------------------------------------------------------------------------
;; compare-meta (private)
;; ---------------------------------------------------------------------------


(deftest compare-meta-match
  (testing "returns true when schema version and SHA match"
    (is (true? (compare-meta {:schema-version 1 :manifest-sha256 "abc123"} "abc123")))))


(deftest compare-meta-version-mismatch
  (testing "returns false when schema version doesn't match"
    (is (not (compare-meta {:schema-version 2 :manifest-sha256 "abc123"} "abc123")))))


(deftest compare-meta-sha-mismatch
  (testing "returns false when SHA doesn't match"
    (is (not (compare-meta {:schema-version 1 :manifest-sha256 "abc123"} "xyz789")))))


(deftest compare-meta-snake-case-keys
  (testing "handles snake_case keys (from CouchDB couch->clj)"
    (is (true? (compare-meta {:schema_version 1 :manifest_sha256 "abc123"} "abc123")))))


(deftest compare-meta-nil-meta
  (testing "nil meta returns false"
    (is (not (compare-meta nil "abc123")))))


;; ---------------------------------------------------------------------------
;; jsonl-batches (private) — exposes the count shadowing bug
;; ---------------------------------------------------------------------------


(defn- make-reader
  "Create a BufferedReader from a string for testing jsonl-batches."
  [text]
  (io/reader (java.io.StringReader. text)))


(deftest jsonl-batches-basic-batching
  (testing "batches 3 lines with batch-size 2 into 2 batches"
    (let [lines   (str "{\"_id\":\"a\",\"v\":1}\n"
                       "{\"_id\":\"b\",\"v\":2}\n"
                       "{\"_id\":\"c\",\"v\":3}\n")
          reader  (make-reader lines)
          batches (vec (jsonl-batches reader 2 999999))]
      (is (= 2 (count batches)))
      (is (= 2 (count (first batches))))
      (is (= 1 (count (second batches)))))))


(deftest jsonl-batches-blank-line-skipping
  (testing "blank lines are skipped"
    (let [lines   (str "{\"_id\":\"a\"}\n"
                       "\n"
                       "   \n"
                       "{\"_id\":\"b\"}\n")
          reader  (make-reader lines)
          batches (vec (jsonl-batches reader 10 999999))]
      (is (= 1 (count batches)))
      (is (= 2 (count (first batches)))))))


(deftest jsonl-batches-byte-size-threshold
  (testing "batch splits when byte size threshold is reached"
    ;; Each line is ~15-20 bytes. Set max-bytes very low to force splitting.
    (let [lines   (str "{\"_id\":\"a\",\"v\":1}\n"
                       "{\"_id\":\"b\",\"v\":2}\n"
                       "{\"_id\":\"c\",\"v\":3}\n")
          reader  (make-reader lines)
          batches (vec (jsonl-batches reader 100 20))]
      ;; With max-bytes=20, each line should trigger a new batch
      (is (>= (count batches) 2)))))
