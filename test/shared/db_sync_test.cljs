(ns db.sync-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [db :as sut]))

(deftest sync-invokes-pouchdb-sync-with-proxy-url
  (testing "sync delegates to PouchDB.sync"
    (let [calls (atom [])
          fake-db (doto (js-obj)
                    (aset "name" "userdb-1"))
          original-location (.-location js/globalThis)]
      (set! (.-location js/globalThis) (doto (js-obj)
                                         (aset "origin" "https://sprecha.test")))
      (try
        (with-redefs [sut/PouchDB (doto (js-obj)
                                     (aset "sync" (fn [local remote]
                                                     (swap! calls conj [local remote]))))]
          (sut/sync fake-db))
        (is (= [["userdb-1" "https://sprecha.test/db/userdb-1"]]
               @calls))
        (finally
          (set! (.-location js/globalThis) original-location))))))
