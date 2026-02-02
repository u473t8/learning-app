(ns client.examples-test
  (:require
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [client.support.db-fixtures :as db-fixtures]
   [client.support.db-queries :as db-queries]
   [client.support.fetch-mocks :as fetch-mocks]
   [db :as db]
   [examples :as sut]
   [promesa.core :as p]
   [tasks :as tasks])
  (:require-macros
   [client.support.test :refer [async-testing]]))


;; =============================================================================
;; Test Helpers
;; =============================================================================


(def test-device-db-name (db-fixtures/db-name "client.examples-test"))
(def test-user-db-name (db-fixtures/db-name "client.examples-test-user"))


(use-fixtures :each (db-fixtures/db-fixture-multi [test-device-db-name test-user-db-name]))


(defn- with-test-db
  "Uses local test DB, calls (f db-instance)."
  [f]
  (db-fixtures/with-test-db test-device-db-name f))


(defn- with-test-dbs
  "Sets up test DBs, calls (f dbs) where dbs is {:device-db ... :user-db ...}."
  [f]
  (db-fixtures/with-test-dbs
    [test-device-db-name test-user-db-name]
    (fn [[device-db user-db]]
      (f {:device-db device-db :user-db user-db}))))


;; =============================================================================
;; Unit Tests: fetch-one
;; =============================================================================


(deftest fetch-one-returns-parsed-json-on-success
  (async-testing "`fetch-one` returns parsed JSON on success"
    (let [example        {:value "Ich habe einen Hund" :translation "I have a dog"}
          original-fetch js/fetch]
      (set! js/fetch (fetch-mocks/mock-fetch-success example))
      (p/finally
        (p/let [result (sut/fetch-one "Hund")]
          (is (= "Ich habe einen Hund" (:value result)))
          (is (= "I have a dog" (:translation result))))
        (fn []
          (set! js/fetch original-fetch))))))


(deftest fetch-one-rejects-on-server-error
  (async-testing "`fetch-one` rejects on server error"
    (let [original-fetch js/fetch]
      (set! js/fetch (fetch-mocks/mock-fetch-error 500))
      (p/finally
        (p/catch
          (p/do
            (sut/fetch-one "Hund")
            (is false "Should have rejected"))
          (fn [error]
            (is (= 500 (:status (ex-data error))))))
        (fn []
          (set! js/fetch original-fetch))))))


(deftest fetch-one-rejects-on-network-error
  (async-testing "`fetch-one` rejects on network error"
    (let [original-fetch js/fetch]
      (set! js/fetch (fetch-mocks/mock-fetch-network-error))
      (p/finally
        (p/catch
          (p/do
            (sut/fetch-one "Hund")
            (is false "Should have rejected"))
          (fn [error]
            (is (= "Network error" (.-message error)))))
        (fn []
          (set! js/fetch original-fetch))))))


;; =============================================================================
;; Unit Tests: save-example!
;; =============================================================================


(deftest save-example-inserts-correct-document
  (async-testing "`save-example!` inserts correct document"
    (with-test-db
      (fn [db]
        (let [example {:value "Der Hund" :translation "The dog" :structure []}]
          (p/do
            (sut/save-example! db "word-123" "Hund" example)
            (p/let [docs (db-queries/fetch-examples db)]
              (let [saved (first docs)]
                (is (= 1 (count docs)))
                (is (= "example" (:type saved)))
                (is (= "word-123" (:word-id saved)))
                (is (= "Hund" (:word saved)))
                (is (= "Der Hund" (:value saved)))))))))))


(deftest save-example-throws-on-missing-value
  (let [example {:translation "The dog"}]
    (is (thrown-with-msg? js/Error
                          #"missing required fields"
                          (sut/save-example! nil "word-123" "Hund" example)))))


(deftest save-example-throws-on-missing-translation
  (let [example {:value "Der Hund"}]
    (is (thrown-with-msg? js/Error
                          #"missing required fields"
                          (sut/save-example! nil "word-123" "Hund" example)))))


;; =============================================================================
;; Unit Tests: find
;; =============================================================================


(deftest find-returns-example-when-exists
  (async-testing "`find` returns example when it exists"
    (with-test-db
      (fn [db]
        (p/do
          (db/insert db {:type "example" :word-id "word-123" :value "test"})
          (p/let [result (sut/find db "word-123")]
            (is (some? result))
            (is (= "word-123" (:word-id result)))))))))


(deftest find-returns-nil-when-not-exists
  (async-testing "`find` returns nil when not found"
    (with-test-db
      (fn [db]
        (p/let [result (sut/find db "nonexistent")]
          (is (nil? result)))))))


;; =============================================================================
;; Unit Tests: remove!
;; =============================================================================


(deftest remove-deletes-existing-document
  (async-testing "`remove!` deletes existing document"
    (with-test-db
      (fn [db]
        (p/do
          (p/let [{:keys [id]} (db/insert db {:type "example" :word-id "w1"})]
            (sut/remove! db id)
            (p/let [examples (db-queries/fetch-examples db)]
              (is (empty? examples)))))))))


(deftest remove-is-noop-when-not-exists
  (async-testing "`remove!` no-op when not found"
    (with-test-db
      (fn [db]
        (p/do
          (sut/remove! db "nonexistent")
          (p/let [examples (db-queries/fetch-examples db)]
            (is (empty? examples))))))))


;; =============================================================================
;; Integration Tests: Task Handler
;; =============================================================================


(deftest task-handler-returns-true-when-word-deleted
  (async-testing "task handler returns true when word is deleted"
    (with-test-dbs
      (fn [dbs]
        (p/let [result (tasks/execute-task
                        {:task-type "example-fetch"
                         :data      {:word-id "deleted-word"}}
                        dbs)]
          (is (true? result)))))))


(deftest task-handler-fetches-and-saves-on-success
  (async-testing "task handler fetches and saves example"
    (let [example        {:value "Der Hund läuft" :translation "The dog runs"}
          original-fetch js/fetch]
      (set! js/fetch (fetch-mocks/mock-fetch-success example))
      (p/finally
        (with-test-dbs
          (fn [{:keys [user-db device-db] :as dbs}]
            (p/do
              (db/insert user-db {:_id "word-123" :type "vocab" :value "Hund"})
              (p/let [result (tasks/execute-task
                              {:task-type "example-fetch"
                               :data      {:word-id "word-123"}}
                              dbs)]
                (is (true? result))
                (p/let [examples (db-queries/fetch-examples device-db)]
                  (is (= 1 (count examples)))
                  (is (= "Der Hund läuft" (:value (first examples)))))))))
        (fn []
          (set! js/fetch original-fetch))))))


(deftest task-handler-returns-false-on-fetch-failure
  (async-testing "task handler returns false on fetch failure"
    (let [original-fetch js/fetch]
      (set! js/fetch (fetch-mocks/mock-fetch-error 500))
      (p/finally
        (with-test-dbs
          (fn [{:keys [user-db] :as dbs}]
            (p/do
              (db/insert user-db {:_id "word-123" :type "vocab" :value "Hund"})
              (p/let [result (tasks/execute-task
                              {:task-type "example-fetch"
                               :data      {:word-id "word-123"}}
                              dbs)]
                (is (false? result))))))
        (fn []
          (set! js/fetch original-fetch))))))
