;;
;; https://developer.mozilla.org/en-US/docs/Web/API/Service_Worker_API
;;
(ns learning-app
  (:require-macros [learning-app :refer [resources]])
  (:require
   ["@thi.ng/hiccup" :as hiccup]
   [clojure.string :as str]
   [lambdaisland.glogi :as log]
   [lambdaisland.glogi.console :as glogi-console]
   [promesa.core :as p]
   [reitit.ring :as ring]))


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
     :query-string   (.-search url-object)
     :sheme          (-> url-object .-protocol (str/replace #":" "") keyword)
     :request-method (-> request .-method str/lower-case keyword)
     :headers        (.-headers request)
     :body           (.-body request)}))


(defn ring->js-response
  [{:keys [body status headers]
    :or   {status 200 headers {}}}]
  ;; https://developer.mozilla.org/en-US/docs/Web/API/Response/Response
  (js/Response.
   body
   (clj->js
    {:status  status
     :headers headers})))


(defn wrap-hiccup-render
  [handler]
  ;; Adapted from https://github.com/lambdaisland/hiccup/blob/main/src/lambdaisland/hiccup/middleware.clj
  (fn [request]
    (let [response (handler request)
          body     (:html/body response)]
      ;; Render HTML if there's a `:html/body` key OR there
      ;; is no `:body` key, because if there isn't then
      ;; there is nothing else to fall back to.
      (if (and body (not (:body response)))
        (-> response
            (assoc :status (or (:status response) 200)
                   :body   (hiccup/serialize (clj->js body)))
            (assoc-in [:headers "content-type"] "text/html; charset=utf-8"))
        response))))


(def routes
  ["/lesson"
   {:get
    (fn [_]
      {:html/body [:h1 "New Lesson"]
       :status    200})}])


(def application
  (ring/ring-handler
   (ring/router
    routes
    {:data {:middleware [wrap-hiccup-render]}})
   (constantly {:status 404, :body ""})))


(js/self.addEventListener
 "fetch"
 (fn [^FetchEvent event] ; https://developer.mozilla.org/en-US/docs/Web/API/FetchEvent
   (log/debug :fetch-event event)
   (..
    event
    (respondWith
     (-> (.-request event)
         (js-request->ring)
         (application)
         (ring->js-response))))))
