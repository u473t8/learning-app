(ns client.presenter.lesson-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [domain.lesson :as domain]
   [presenter.lesson :as sut]))


(deftest footer-props-returns-nil-without-result
  (testing "footer stays in input mode before answering"
    (let [state (domain/initial-state
                 [{:id "word-1" :value "der Hund" :translation "пёс"}]
                 []
                 :first
                 "2024-08-20T10:00:00.000Z")]
      (is (nil? (sut/footer-props state))))))


(deftest footer-props-includes-user-answer-for-example-trial
  (testing "wrong example answer includes both user and correct sentences"
    (let [state (domain/initial-state
                 []
                 [{:_id         "example-1"
                   :word-id     "word-1"
                   :word        "der Hund"
                   :value       "Der Hund schlaeft."
                   :translation "Пёс спит"}]
                 :first
                 "2024-08-20T10:00:00.000Z")
          state (domain/check-answer state "Mein Hund schlaeft")
          props (sut/footer-props state)]
      (is (= :error (:variant props)))
      (is (= "Mein Hund schlaeft" (:user-answer props)))
      (is (= "Der Hund schlaeft." (:correct-answer props))))))


(deftest footer-props-includes-user-answer-for-word-trial
  (testing "wrong word answer includes both user and correct values"
    (let [state (domain/initial-state
                 [{:id "word-1" :value "der Hund" :translation "пёс"}]
                 []
                 :first
                 "2024-08-20T10:00:00.000Z")
          state (domain/check-answer state "die Katze")
          props (sut/footer-props state)]
      (is (= :error (:variant props)))
      (is (= "die Katze" (:user-answer props)))
      (is (= "der Hund" (:correct-answer props))))))
