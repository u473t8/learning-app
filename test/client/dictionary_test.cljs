(ns client.dictionary-test
  (:require
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [client.support.db-fixtures :as db-fixtures]
   [db :as db]
   [dictionary :as sut]
   [promesa.core :as p])
  (:require-macros
   [client.support.test :refer [async-testing]]))


;; =============================================================================
;; Test Helpers
;; =============================================================================


(def test-db-name (db-fixtures/db-name "client.dictionary-test"))


(use-fixtures :each (db-fixtures/db-fixture test-db-name))


(defn- with-test-db
  [f]
  (db-fixtures/with-test-db test-db-name f))


(defn- sf-doc
  "Build a surface-form document."
  [id value entries]
  {:_id id :type "surface-form" :value value :entries entries})


;; =============================================================================
;; suggest — short-circuit
;; =============================================================================


(deftest suggest-empty-string
  (async-testing "short-circuits on empty string"
    (with-test-db
      (fn [db]
        (p/let [result (sut/suggest db "")]
          (is (= {:suggestions [] :prefill nil} result)))))))


(deftest suggest-nil-input
  (async-testing "short-circuits on nil input"
    (with-test-db
      (fn [db]
        (p/let [result (sut/suggest db nil)]
          (is (= {:suggestions [] :prefill nil} result)))))))


;; =============================================================================
;; suggest — no matches
;; =============================================================================


(deftest suggest-no-matches
  (async-testing "returns empty when nothing matches"
    (with-test-db
      (fn [db]
        (p/do
          (db/insert db
                     (sf-doc "sf:hund"
                             "hund"
                             [{:lemma-id "l1" :lemma "Hund" :rank 80}]))
          (p/let [result (sut/suggest db "xyz")]
            (is (= [] (:suggestions result)))
            (is (nil? (:prefill result)))))))))


;; =============================================================================
;; suggest — single match
;; =============================================================================


(deftest suggest-single-match
  (async-testing "returns a single matching entry"
    (with-test-db
      (fn [db]
        (p/do
          (db/insert db
                     (sf-doc "sf:hund"
                             "hund"
                             [{:lemma-id "l1" :lemma "Hund" :rank 80}]))
          (p/let [result (sut/suggest db "Hund")]
            (is (= 1 (count (:suggestions result))))
            (is (= "Hund" (:lemma (first (:suggestions result)))))))))))


;; =============================================================================
;; suggest — deduplication
;; =============================================================================


(deftest suggest-dedup-keeps-highest-rank
  (async-testing "deduplication keeps the entry with the highest rank"
    (with-test-db
      (fn [db]
        (p/do
          (db/insert db
                     (sf-doc "sf:hund"
                             "hund"
                             [{:lemma-id "l1" :lemma "Hund" :rank 80}]))
          (db/insert db
                     (sf-doc "sf:hunde"
                             "hunde"
                             [{:lemma-id "l1" :lemma "Hund" :rank 90}]))
          (p/let [result (sut/suggest db "hund")]
            (is (= 1 (count (:suggestions result))))
            (is (= 90 (:rank (first (:suggestions result)))))))))))


;; =============================================================================
;; suggest — sorting & cap
;; =============================================================================


(deftest suggest-sorted-by-rank-capped-at-10
  (async-testing "results sorted by rank desc and capped at 10"
    (with-test-db
      (fn [db]
        (p/do
          (db/insert db
                     (sf-doc "sf:a"
                             "a"
                             (mapv (fn [i]
                                     {:lemma-id (str "l" i)
                                      :lemma    (str "Word" i)
                                      :rank     (* i 10)})
                                   (range 1 13))))
          (p/let [result (sut/suggest db "a")]
            (is (= 10 (count (:suggestions result))))
            (is (= 120 (:rank (first (:suggestions result)))))))))))


;; =============================================================================
;; suggest — prefill
;; =============================================================================


(deftest suggest-prefill-on-exact-match
  (async-testing "prefill returned when exact surface-form matches"
    (with-test-db
      (fn [db]
        (p/do
          (db/insert db
                     (sf-doc "sf:hund"
                             "hund"
                             [{:lemma-id "l1" :lemma "Hund" :rank 80}]))
          (p/let [result (sut/suggest db "Hund")]
            (is (= "Hund" (:prefill result)))))))))


(deftest suggest-no-prefill-on-prefix-only
  (async-testing "no prefill when only prefix matches exist"
    (with-test-db
      (fn [db]
        (p/do
          (db/insert db
                     (sf-doc "sf:hunde"
                             "hunde"
                             [{:lemma-id "l1" :lemma "Hunde" :rank 80}]))
          (p/let [result (sut/suggest db "Hund")]
            (is (nil? (:prefill result)))))))))
