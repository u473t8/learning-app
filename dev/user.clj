(ns user
  (:require
   [debux.core]
   [sqlite.application-defined-functions]))


;; Improving Development Startup Time
;; https://clojure.org/guides/dev_startup_time
(binding [*compile-files* true       ;; compile during load
          *compile-path*  "classes"]
  (require 'user :reload-all))       ;; reload this and all transitively loaded namespaces


;; Using `require-resolve` instead of `:require` to avoid compile class pollution in the code above.

(def start-server
  (requiring-resolve 'core/-main))


(def stop-server
  (requiring-resolve 'core/stop-server!))


(def reload-deps
  (requiring-resolve 'clojure.repl.deps/sync-deps))


(comment
  (start-server)
  (stop-server)
  (reload-deps)
  )
