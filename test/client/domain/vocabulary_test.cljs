(ns client.domain.vocabulary-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [client.support.fixtures :as fixtures]
   [domain.vocabulary :as sut]
   [utils :as utils]))


;; =============================================================================
;; Test Helpers
;; =============================================================================


(def test-now-ms 1724155500000)


;; 2024-08-20T10:45:00Z in milliseconds


;; =============================================================================
;; Document Builders
;; =============================================================================


(deftest new-word-doc-creates-vocab-document
  (testing "user adds a new word"
    (testing "system builds a vocab document"
      (let [word (sut/new-word "der Hund" "пёс" "2024-01-01T00:00:00.000Z")]
        (is (= "vocab" (:type word)))
        (is (= "der Hund" (:value word)))
        (is (= "пёс" (-> word :translation first :value)))
        (is (= "ru" (-> word :translation first :lang)))
        (is (= "2024-01-01T00:00:00.000Z" (:created-at word)))
        (is (= "2024-01-01T00:00:00.000Z" (:modified-at word)))))))


(deftest update-word-doc-updates-values
  (testing "user updates an existing word"
    (testing "system updates the vocab document"
      (let [word    (sut/new-word "der Hund" "пёс" "2024-01-01T00:00:00.000Z")
            updated (sut/update-word word "der Fuchs" "лиса" "2024-01-02T00:00:00.000Z")]
        (is (= "der Fuchs" (:value updated)))
        (is (= "лиса" (-> updated :translation first :value)))
        (is (= "2024-01-01T00:00:00.000Z" (:created-at updated)))
        (is (= "2024-01-02T00:00:00.000Z" (:modified-at updated)))))))


(deftest new-review-doc-creates-review-document
  (testing "user reviews a word"
    (testing "system stamps a review document"
      (let [review (sut/new-review "word-1" true "пёс" "2024-01-01T00:00:00.000Z")]
        (is (= "review" (:type review)))
        (is (= "word-1" (:word-id review)))
        (is (= true (:retained review)))
        (is (= "2024-01-01T00:00:00.000Z" (:created-at review)))
        (is (= "пёс" (-> review :translation first :value)))
        (is (= "ru" (-> review :translation first :lang)))))))


;; =============================================================================
;; Word Summary
;; =============================================================================


(deftest word-summary-normalizes-word-data
  (testing "user views a word summary"
    (testing "system normalizes translation and calculates retention"
      (let [summary (sut/word-summary (first fixtures/sample-words)
                                      [(first fixtures/sample-reviews)]
                                      test-now-ms)]
        (is (= "word-1" (:id summary)))
        (is (= "der Hund" (:value summary)))
        (is (= "пёс" (:translation summary)))
        (is (number? (:retention-level summary)))))))


;; =============================================================================
;; Summarize Words
;; =============================================================================


(deftest summarize-words-filters-by-search
  (testing "user searches their vocabulary"
    (testing "system filters words by query"
      (let [result (sut/summarize-words fixtures/sample-words
                                        fixtures/sample-reviews
                                        test-now-ms
                                        {:search "Hund"})]
        (is (= 1 (count result)))
        (is (= "word-1" (:id (first result))))))))


(deftest summarize-words-sorts-by-retention
  (testing "user orders words by retention"
    (testing "system sorts by retention level"
      (with-redefs [sut/retention-level (fn [reviews _now]
                                          (if (= "word-1" (:word-id (first reviews)))
                                            30
                                            10))]
        (let [result (sut/summarize-words fixtures/sample-words
                                          fixtures/sample-reviews
                                          test-now-ms
                                          {:order :asc})]
          (is (= ["word-2" "word-1"] (map :id result))))))))


(deftest summarize-words-paginates
  (testing "user pages through vocabulary"
    (testing "system returns the requested slice"
      (let [result (sut/summarize-words fixtures/sample-words
                                        fixtures/sample-reviews
                                        test-now-ms
                                        {:offset 1 :limit 1})]
        (is (= 1 (count result)))))))


;; =============================================================================
;; Retention Level
;; =============================================================================


(deftest retention-level-calculates-from-reviews
  (testing "user checks retention"
    (testing "system calculates retention percentage"
      (let [reviews [{:created-at "2024-08-20T10:00:00.000Z" :retained true}
                     {:created-at "2024-08-20T10:05:00.000Z" :retained true}]
            now-ms  (utils/iso->ms "2024-08-20T10:10:00.000Z")
            level   (sut/retention-level reviews now-ms)]
        (is (number? level))
        (is (> level 0))
        (is (<= level 100))))))


(deftest retention-level-decreases-over-time
  (testing "retention decays with time"
    (let [reviews     [{:created-at "2024-08-20T10:00:00.000Z" :retained true}
                       {:created-at "2024-08-20T10:05:00.000Z" :retained true}]
          soon-ms     (utils/iso->ms "2024-08-20T10:10:00.000Z")
          later-ms    (utils/iso->ms "2024-08-21T10:00:00.000Z")
          level-soon  (sut/retention-level reviews soon-ms)
          level-later (sut/retention-level reviews later-ms)]
      (is (> level-soon level-later)))))
