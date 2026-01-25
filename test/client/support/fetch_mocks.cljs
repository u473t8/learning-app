(ns client.support.fetch-mocks
  (:require
   [promesa.core :as p]))


(defn mock-fetch-success
  "Returns a mock fetch that resolves with given data."
  [data]
  (fn [_url]
    (p/resolved
     #js {:ok   true
          :json (fn [] (p/resolved (clj->js data)))})))


(defn mock-fetch-error
  "Returns a mock fetch that resolves with error status."
  [status]
  (fn [_url]
    (p/resolved #js {:ok false :status status})))


(defn mock-fetch-network-error
  "Returns a mock fetch that rejects with network error."
  []
  (fn [_url]
    (p/rejected (js/Error. "Network error"))))
