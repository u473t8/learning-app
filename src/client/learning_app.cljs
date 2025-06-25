(ns learning-app
  (:require-macros [learning-app :refer [resources]])
  (:require
   [lambdaisland.glogi :as log]
   [lambdaisland.glogi.console :as glogi-console]
   [promesa.core :as p]))


(glogi-console/install!)

(log/set-levels
 {:glogi/root :all})


(def resources
  (resources
   ["/index.html"
    "/manifest.json"
    "/favicon.ico"]))


(js/self.addEventListener
 "install"
 (fn [event]
   (..
    event
    (waitUntil
     (p/let [cache (js/caches.open "resources")]
       (p/doseq [{:keys [url revision]} resources]
         (p/let [cached-response (.match cache url)]
           (if (some? cached-response)
             (p/let [body (.text cached-response)]
               (when (not= (hash body) revision)
                 (.add cache url)))
             (p/promise
              (.add cache url))))))))))


(defn normalize-url
  [url]
  (.-href (js/URL. url js/self.location.origin)))


(js/self.addEventListener
 "activate"
 (fn [event]
   (..
    event
    (waitUntil
     (p/do
       (log/debug :activate "Cleaning up unused cache")
       (p/let [cache           (js/caches.open "resources")
               cached-requests (.keys cache)
               resource-urls   (->> resources (map :url) (map normalize-url) set)]
         (p/doseq [cached-request cached-requests]
           (let [cached-url (.-url cached-request)]
             (when-not (contains? resource-urls cached-url)
               (.delete cache cached-url)))))

       (log/debug :activate "Claiming the client")
       (.claim js/self.clients))))))


(js/self.addEventListener
 "fetch"
 (fn [event]
   (log/debug :fetch ["Intercepting request event:" (js->clj event)])))
