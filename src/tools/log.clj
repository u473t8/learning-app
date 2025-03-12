(ns tools.log
  (:require
   [taoensso.telemere :as t]))


(defmacro error
  [message]
  `(t/log! :error ~message))


(defmacro info
  [message]
  `(t/log! :info ~message))



