(ns client.support.time
  (:require [utils :as utils]))


(def test-now-iso "2024-08-20T10:00:00.000Z")


(def now-iso
  (constantly test-now-iso))


(def now-ms
  (constantly (utils/iso->ms (now-iso))))
