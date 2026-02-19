(ns client.domain.vocabulary-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [domain.vocabulary :as sut]))


(deftest new-word-doc-creates-vocab-document
  (testing "user adds a new word"
    (let [word (sut/new-word "der Hund" [{:lang "ru" :value "пёс"}] "2024-01-01T00:00:00.000Z")]
      (is (= "vocab" (:type word)))
      (is (= "der Hund" (:value word)))
      (is (= "пёс" (-> word :translation first :value)))
      (is (= "ru" (-> word :translation first :lang)))
      (is (= "2024-01-01T00:00:00.000Z" (:created-at word)))
      (is (= "2024-01-01T00:00:00.000Z" (:modified-at word))))))


(deftest update-word-doc-updates-values
  (testing "user updates an existing word"
    (let [word    (sut/new-word "der Hund" [{:lang "ru" :value "пёс"}] "2024-01-01T00:00:00.000Z")
          updated (sut/update-word word "der Fuchs" "лиса" "2024-01-02T00:00:00.000Z")]
      (is (= "der Fuchs" (:value updated)))
      (is (= "лиса" (-> updated :translation first :value)))
      (is (= "2024-01-01T00:00:00.000Z" (:created-at updated)))
      (is (= "2024-01-02T00:00:00.000Z" (:modified-at updated))))))


(deftest new-review-doc-creates-review-document
  (testing "user reviews a word"
    (let [review (sut/new-review "word-1" true "пёс" "2024-01-01T00:00:00.000Z")]
      (is (= "review" (:type review)))
      (is (= "word-1" (:word-id review)))
      (is (true? (:retained review)))
      (is (= "2024-01-01T00:00:00.000Z" (:created-at review)))
      (is (= "пёс" (-> review :translation first :value)))
      (is (= "ru" (-> review :translation first :lang))))))
