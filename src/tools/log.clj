(ns tools.log
  (:require
   [taoensso.telemere :as t]))


(defmacro info
  [message]
  `(t/log! :info ~message))



