(ns client.vocabulary-test
  (:require
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [client.support.db-fixtures :as db-fixtures]
   [client.support.db-queries :as db-queries]
   [client.support.time :as time]
   [db :as db]
   [promesa.core :as p]
   [utils :as utils]
   [vocabulary :as sut])
  (:require-macros
   [client.support.test :refer [async-testing]]))


;; =============================================================================
;; Test Helpers
;; =============================================================================


(def test-db-name (db-fixtures/db-name "client.vocabulary-test"))


(use-fixtures :each (db-fixtures/db-fixture test-db-name))


(defn- with-test-db
  "Sets up test DB and time utilities, calls (f db-instance)."
  [f]
  (db-fixtures/with-test-db
   test-db-name
   (fn [db]
     (p/with-redefs [utils/now-iso time/now-iso
                     utils/now-ms  time/now-ms]
       (f db)))))


;; =============================================================================
;; add!
;; =============================================================================


(deftest add-creates-vocab-and-initial-review
  (async-testing "`add!` creates vocab and initial review"
    (with-test-db
     (fn [db]
       (p/let [word-id (sut/add! db "der Hund" "пёс")
               vocabs  (db-queries/fetch-by-type db "vocab")
               reviews (db-queries/fetch-by-type db "review")]
         (is (string? word-id))
         (is (= 1 (count vocabs)))
         (is (= 1 (count reviews)))
         (is (= "der Hund" (:value (first vocabs))))
         (is (= word-id (:word-id (first reviews))))
         (is (= true (:retained (first reviews)))))))))


;; =============================================================================
;; list
;; =============================================================================


(deftest list-returns-summaries-with-retention
  (async-testing "`list` returns summaries with retention"
    (with-test-db
     (fn [db]
       (p/do
         (sut/add! db "der Hund" "пёс")
         (sut/add! db "die Katze" "кот")
         (p/let [result (sut/list db)]
           (is (= 2 (count result)))
           (is (every? :retention-level result))
           (is (every? :id result))
           (is (every? :value result))
           (is (every? :translation result))))))))


(deftest list-filters-by-search
  (async-testing "`list` filters by search query"
    (with-test-db
     (fn [db]
       (p/do
         (sut/add! db "der Hund" "пёс")
         (sut/add! db "die Katze" "кот")
         (p/let [result (sut/list db {:search "Hund"})]
           (is (= 1 (count result)))
           (is (= "der Hund" (:value (first result))))))))))


(deftest list-paginates
  (async-testing "`list` respects limit for pagination"
    (with-test-db
     (fn [db]
       (p/do
         (sut/add! db "der Hund" "пёс")
         (sut/add! db "die Katze" "кот")
         (sut/add! db "der Vogel" "птица")
         (p/let [result (sut/list db {:limit 2})]
           (is (= 2 (count result)))))))))


;; =============================================================================
;; count
;; =============================================================================


(deftest count-returns-total
  (async-testing "`count` returns total word count"
    (with-test-db
     (fn [db]
       (p/do
         (sut/add! db "der Hund" "пёс")
         (sut/add! db "die Katze" "кот")
         (p/let [cnt (sut/count db)]
           (is (= 2 cnt))))))))


;; =============================================================================
;; get
;; =============================================================================


(deftest get-returns-summary
  (async-testing "`get` returns word summary"
    (with-test-db
     (fn [db]
       (p/let [word-id (sut/add! db "der Hund" "пёс")
               result  (sut/get db word-id)]
         (is (= word-id (:id result)))
         (is (= "der Hund" (:value result)))
         (is (= "пёс" (:translation result)))
         (is (number? (:retention-level result))))))))


(deftest get-returns-nil-when-not-found
  (async-testing "`get` returns nil when not found"
    (with-test-db
     (fn [db]
       (p/let [result (sut/get db "nonexistent")]
         (is (nil? result)))))))


;; =============================================================================
;; update!
;; =============================================================================


(deftest update-updates-and-returns-summary
  (async-testing "`update!` modifies and returns summary"
    (with-test-db
     (fn [db]
       (p/let [word-id (sut/add! db "der Hund" "пёс")
               result  (sut/update! db word-id "der Fuchs" "лиса")]
         (is (= word-id (:id result)))
         (is (= "der Fuchs" (:value result)))
         (is (= "лиса" (:translation result))))))))


(deftest update-returns-nil-when-not-found
  (async-testing "`update!` returns nil when not found"
    (with-test-db
     (fn [db]
       (p/let [result (sut/update! db "nonexistent" "foo" "bar")]
         (is (nil? result)))))))


;; =============================================================================
;; delete!
;; =============================================================================


(deftest delete-removes-word-and-reviews
  (async-testing "`delete!` removes word and reviews"
    (with-test-db
     (fn [db]
       (p/do
         (p/let [word-id (sut/add! db "der Hund" "пёс")]
           (p/do
             (sut/add-review db word-id true "пёс")
             (sut/delete! db word-id)
             (p/let [vocabs  (db-queries/fetch-by-type db "vocab")
                     reviews (db-queries/fetch-by-type db "review")]
               (is (empty? vocabs))
               (is (empty? reviews))))))))))


(deftest delete-removes-examples
  (async-testing "`delete!` removes associated examples"
    (with-test-db
     (fn [db]
       (p/do
         (p/let [word-id (sut/add! db "der Hund" "пёс")]
           (p/do
             (db/insert db {:type "example" :word-id word-id :value "Der Hund läuft"})
             (sut/delete! db word-id)
             (p/let [examples (db-queries/fetch-by-type db "example")]
               (is (empty? examples))))))))))


(deftest delete-is-noop-when-not-found
  (async-testing "`delete!` no-op when not found"
    (with-test-db
     (fn [db]
       (p/do
         (sut/add! db "der Hund" "пёс")
         (sut/delete! db "nonexistent")
         (p/let [vocabs (db-queries/fetch-by-type db "vocab")]
           (is (= 1 (count vocabs)))))))))


;; =============================================================================
;; add-review
;; =============================================================================


(deftest add-review-creates-review-document
  (async-testing "`add-review` creates review document"
    (p/finally
      (with-test-db
       (fn [db]
         (p/do
           (p/let [word-id (sut/add! db "der Hund" "пёс")]
             (p/do
               (sut/add-review db word-id false "собака")
               (p/let [reviews (db-queries/fetch-by-type db "review")]
                 (is (= 2 (count reviews)))
                 (let [new-review (first (filter (fn [review]
                                                   (false? (:retained review)))
                                                 reviews))]
                   (is (some? new-review))
                   (is (= "собака" (-> new-review :translation first :value))))))))))
      (fn [] nil))))
