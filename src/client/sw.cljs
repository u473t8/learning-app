;;
;; https://developer.mozilla.org/en-US/docs/Web/API/Service_Worker_API
;;
(ns sw
  (:require-macros [sw :refer [resources]])
  (:require
   [clojure.string :as str]
   [lambdaisland.glogi :as log]
   [lambdaisland.glogi.console :as glogi-console]
   [learning-app]
   [promesa.core :as p]))


(glogi-console/install!)


(log/set-levels
 {:glogi/root :all})


(def resources
  (resources
   ["/index.html"
    "/manifest.json"
    "/js/htmx.min.js"
    "/favicon.ico"]))


(js/self.addEventListener
 "install"
 (fn [event]
   (log/debug :install event)
   (..
    event
    (waitUntil
     (p/let [cache (js/caches.open "resources")]
       (p/doseq [{:keys [url revision]} resources]
         (p/let [cached-response (.match cache url)]
           (if (some? cached-response)
             (p/let [body (.text cached-response)]
               (when (not= (hash body) revision)
                 (log/debug :add-cache {:url url :revision revision})
                 (.add cache url)))
             (p/promise
              (.add cache url))))))))))


(defn full-url
  [url]
  (.-href (js/URL. url js/self.location.origin)))


(js/self.addEventListener
 "activate"
 (fn [event]
   (log/debug :activate event)
   (..
    event
    (waitUntil
     (p/do
       (p/let [cache           (js/caches.open "resources")
               cached-requests (.keys cache)
               resource-urls   (->> resources (map :url) (map full-url) set)]
         (p/doseq [cached-request cached-requests]
           (let [cached-url (.-url cached-request)]
             (when-not (contains? resource-urls cached-url)
               (log/debug :delete-cache {:url cached-url})
               (.delete cache cached-url)))))
       (.claim js/self.clients))))))


(defn js-request->ring
  [^Request request]
  (let [url-object (js/URL. (.-url request))]
    ;; https://github.com/ring-clojure/ring/wiki/Concepts#requests
    {:server-port    (.-port url-object)
     :server-name    (.-hostname url-object)
     :uri            (.-pathname url-object)
     :url            (.-url request)
     :query-string   (.-search url-object)
     :sheme          (-> url-object .-protocol (str/replace #":" "") keyword)
     :request-method (-> request .-method str/lower-case keyword)
     :headers        (.-headers request)
     :body           (.-body request)}))


(defn ring->js-response
  [response  ^Request request]
  ;; https://developer.mozilla.org/en-US/docs/Web/API/Response/Response
  (if (instance? js/Response response)
    response
    (let [{:keys [body status headers]
           :or   {status 200 headers {}}} response]
      ;; Returning a cached response promise if route was not found
      (if (= status 404)
        (p/let [cache (js/caches.open "resources")]
          (.match cache request))
        (js/Response.
         body
         (clj->js
          {:status  status
           :headers headers}))))))


(js/self.addEventListener
 "fetch"
 (fn [^FetchEvent event] ; https://developer.mozilla.org/en-US/docs/Web/API/FetchEvent
   (log/debug :fetch-event event)
   (..
    event
    (respondWith
     (-> (.-request event)
         (js-request->ring)
         (learning-app/application)
         (ring->js-response (.-request event)))))))
