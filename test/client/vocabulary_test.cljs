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


(def user-db-name   (db-fixtures/db-name "client.vocabulary-test.user"))
(def device-db-name (db-fixtures/db-name "client.vocabulary-test.device"))


(use-fixtures :each (db-fixtures/db-fixture-multi [user-db-name device-db-name]))


(defn- with-test-dbs
  [f]
  (db-fixtures/with-test-dbs
   [user-db-name device-db-name]
   (fn [[user-db device-db]]
     (p/with-redefs [utils/now-iso time/now-iso
                     utils/now-ms  time/now-ms]
       (f {:user/db user-db :device/db device-db})))))


(deftest add-creates-vocab-and-initial-review
  (async-testing "`add!` creates vocab and initial review"
    (with-test-dbs
     (fn [dbs]
       (p/let [{:keys [word-id created?]} (sut/add! dbs "der Hund" "пёс")
               vocabs  (db-queries/fetch-by-type (:user/db dbs) "vocab")
               reviews (db-queries/fetch-by-type (:user/db dbs) "review")]
         (is (string? word-id))
         (is (true? created?))
         (is (= 1 (count vocabs)))
         (is (= 1 (count reviews)))
         (is (= "der Hund" (:value (first vocabs))))
         (is (= word-id (:word-id (first reviews))))
         (is (true? (:retained (first reviews)))))))))


(deftest list-returns-summaries-with-retention
  (async-testing "`list` returns summaries with retention"
    (with-test-dbs
     (fn [dbs]
       (p/do
         (sut/add! dbs "der Hund" "пёс")
         (sut/add! dbs "die Katze" "кот")
         (p/let [{:keys [words total]} (sut/list dbs)]
           (is (= 2 (count words)))
           (is (= 2 total))
           (is (every? :retention-level words))))))))


(deftest list-filters-and-paginates
  (async-testing "`list` supports search and limit"
    (with-test-dbs
     (fn [dbs]
       (p/do
         (sut/add! dbs "der Hund" "пёс")
         (sut/add! dbs "die Katze" "кот")
         (sut/add! dbs "der Vogel" "птица")
         (p/let [{:keys [words total]} (sut/list dbs {:search "Hund" :limit 1})]
           (is (= 3 total))
           (is (= 1 (count words)))
           (is (= "der Hund" (:value (first words))))))))))


(deftest count-uses-db-doc-count-as-limit
  (async-testing "`count` uses db/info doc-count for single find"
    (let [info-calls (atom 0)
          find-calls (atom [])
          doc-count  26]
      (p/with-redefs [db/info
                      (fn [_]
                        (swap! info-calls inc)
                        (p/resolved {:doc-count doc-count}))

                      db/find
                      (fn [_ query]
                        (swap! find-calls conj query)
                        (p/resolved {:docs (vec (repeat doc-count {:type "vocab"}))}))]
        (p/let [cnt (sut/count {:user/db :fake})
                q   (first @find-calls)]
          (is (= doc-count cnt))
          (is (= 1 @info-calls))
          (is (= doc-count (:limit q)))
          (is (nil? (:skip q))))))))


(deftest list-and-count-return-all-words-beyond-25
  (async-testing "`list` and `count` return full data when db has more than 25 words"
    (with-test-dbs
     (fn [dbs]
       (p/do
         (p/loop [i 0]
           (when (< i 30)
             (p/do
               (sut/add! dbs (str "word-" i) (str "перевод-" i))
               (p/recur (inc i)))))
         (p/let [cnt (sut/count dbs)
                 {:keys [words total]} (sut/list dbs)]
           (is (= 30 cnt))
           (is (= 30 total))
           (is (= 30 (count words)))))))))


(deftest get-returns-summary
  (async-testing "`get` returns word summary"
    (with-test-dbs
     (fn [dbs]
       (p/let [{:keys [word-id]} (sut/add! dbs "der Hund" "пёс")
               result  (sut/get dbs word-id)]
         (is (= word-id (:_id result)))
         (is (= "der Hund" (:value result)))
         (is (= "пёс" (-> result :translation first :value)))
         (is (number? (:retention-level result))))))))


(deftest update-updates-and-returns-summary
  (async-testing "`update!` modifies and returns summary"
    (with-test-dbs
     (fn [dbs]
       (p/let [{:keys [word-id]} (sut/add! dbs "der Hund" "пёс")
               result            (sut/update! dbs word-id "лиса")]
         (is (= word-id (:_id result)))
         (is (= "der Hund" (:value result)))   ; unchanged
         (is (= "лиса" (-> result :translation first :value))))))))


(deftest delete-removes-word-related-docs
  (async-testing "`delete!` removes word, reviews and examples"
    (with-test-dbs
     (fn [dbs]
       (p/do
         (p/let [{:keys [word-id]} (sut/add! dbs "der Hund" "пёс")]
           (p/do
             (sut/add-review dbs word-id true "пёс")
             (db/insert (:device/db dbs) {:type "example" :word-id word-id :value "Der Hund läuft"})
             (sut/delete! dbs word-id)
             (p/let [vocabs   (db-queries/fetch-by-type (:user/db dbs) "vocab")
                     reviews  (db-queries/fetch-by-type (:user/db dbs) "review")
                     examples (db-queries/fetch-by-type (:device/db dbs) "example")]
               (is (empty? vocabs))
               (is (empty? reviews))
               (is (empty? examples))))))))))


(deftest add-review-creates-review-document
  (async-testing "`add-review` creates review document"
    (with-test-dbs
     (fn [dbs]
       (p/do
         (p/let [{:keys [word-id]} (sut/add! dbs "der Hund" "пёс")]
           (p/do
             (sut/add-review dbs word-id false "собака")
             (p/let [reviews (db-queries/fetch-by-type (:user/db dbs) "review")]
               (is (= 2 (count reviews)))
               (is (= 1 (count (filter (fn [r] (false? (:retained r))) reviews))))))))))))
