(ns user
  (:require
   [debux.core]
   [sqlite.application-defined-functions]))


;; Improving Development Startup Time
;; https://clojure.org/guides/dev_startup_time
(binding [*compile-files* true       ;; compile during load
          *compile-path*  "classes"]
  (require 'user :reload-all))       ;; reload this and all transitively loaded namespaces
