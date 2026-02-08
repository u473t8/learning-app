(ns backend.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [core :as sut]))

(deftest auth-proxy-response-returns-401-without-session
  (testing "blank session"
    (is (= {:status 401} (sut/auth-proxy-response nil)))
    (is (= {:status 401} (sut/auth-proxy-response {})))))

(deftest auth-proxy-response-populates-proxy-headers
  (testing "session present"
    (let [response (sut/auth-proxy-response {:user-id 42})
          headers  (:headers response)
          expected-token (sut/hmac-sign "42" sut/db-auth-secret)]
      (is (= 200 (:status response)))
      (is (= "42" (get headers "X-Auth-UserName")))
      (is (= "u:42" (get headers "X-Auth-Roles")))
      (is (= expected-token (get headers "X-Auth-Token"))))))
