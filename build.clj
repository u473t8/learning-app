(ns build
  (:require
   [clojure.string :as str]
   [clojure.tools.build.api :as b]))


(def target-dir "target")

(def class-dir "target/classes")

(def uber-file "target/learning-app.jar")

;; delay to defer side effects (artifact downloads)
(def basis (delay (b/create-basis {:project "deps.edn"})))


(defn clean [_]
  (println (format "Cleaning %s..." target-dir))
  (b/delete {:path "target"}))


(defn uber [_]
  (clean nil)

  (println "Copying resources...")
  (b/copy-dir {:src-dirs   ["resources"]
               :target-dir class-dir})

  (println "Compiling files...")
  (b/compile-clj {:basis      @basis
                  :src-dirs   ["src"]
                  :ns-compile '[sqlite.application-defined-functions learning-app]
                  :class-dir  class-dir})

  (println "Creating uber file...")
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis     @basis
           :main      'learning-app})
  (println (format "Uber file created: %s" uber-file)))


(defn run [_]
  (let [resources-dir "resources"
        class-path    (str/join ":" [uber-file])] ; Using ':' as classpath separator on Unix/Linux
    (println (format "Running '%s' with resources at '%s'" uber-file resources-dir))
    (let [process (-> (java.lang.ProcessBuilder.
                       ["java" "--class-path" class-path "learning_app"])
                      (.inheritIO)
                      (.start))]
      (.waitFor process))))
