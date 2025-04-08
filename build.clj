(ns build
  (:require [clojure.tools.build.api :as b]))


(def class-dir "target/classes")

(def uber-file "target/learning-app.jar")

;; delay to defer side effects (artifact downloads)
(def basis (delay (b/create-basis {:project "deps.edn"})))


(defn clean [_]
  (b/delete {:path "target"}))


(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs   ["resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis      @basis
                  :src-dirs   ["src"]
                  :ns-compile '[learning-app]
                  :class-dir  class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis     @basis
           :main      'learning-app}))
