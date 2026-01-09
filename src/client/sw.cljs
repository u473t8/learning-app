;;
;; https://developer.mozilla.org/en-US/docs/Web/API/Service_Worker_API
;;
(ns sw
  (:require
   [application :as application]
   [clojure.string :as str]
   [examples :as examples]
   [lambdaisland.glogi :as log]
   [lambdaisland.glogi.console :as glogi-console]
   [promesa.core :as p]
   [utils :as utils]))


(glogi-console/install!)


(log/set-levels
 {:glogi/root :all})


(def base-precache-urls
  ["/"
   "/manifest.json"
   "/css/styles.css"
   "/js/sw-loader.js"
   "/fonts/Nunito/nunito-v26-cyrillic_latin-regular.woff2"
   "/fonts/Nunito/nunito-v26-cyrillic_latin-500.woff2"
   "/fonts/Nunito/nunito-v26-cyrillic_latin-600.woff2"
   "/fonts/Nunito/nunito-v26-cyrillic_latin-600italic.woff2"
   "/fonts/Nunito/nunito-v26-cyrillic_latin-700.woff2"
   "/fonts/Nunito/nunito-v26-cyrillic_latin-800.woff2"
   "/js/htmx/htmx.min.js"
   "/js/htmx/idiomorph-ext.min.js"
   "/favicon.ico"
   "/icons.svg"
   "/icons/ue-192.png"
   "/icons/ue-512.png"])


;;
;; Listeners
;;


(js/self.addEventListener
 "install"
 (fn [^InstallEvent event]
   (log/debug :event/install event)
   ;; TODO: explain why do we use skipWaiting here
   (js/self.skipWaiting)
   (..
    event
    (waitUntil
     (p/-> (js/caches.open "resources")
           (.addAll base-precache-urls))))))


(js/self.addEventListener
 "activate"
 (fn [event]
   (log/debug :event/activate event)
   (..
    event
    (waitUntil
     (p/do
       (js/self.clients.claim)
       ;; Sync examples when service worker activates (app startup)
       (when (examples/online?)
         (examples/sync-examples!)))))))


(js/self.addEventListener
 "online"
 (fn [_event]
   (log/info :event/online "Browser came online, syncing examples")
   (examples/sync-examples!)))


(js/self.addEventListener
 "message"
 (fn [event]
   (when (= "ping" (.. event -data -type))
     (some-> (.-source event)
             (.postMessage #js {:type "pong"})))))


(defn- static-request?
  [request]
  (let [path (.-pathname (js/URL. (.-url request)))]
    (contains? (set base-precache-urls) path)))



(defn- api-request?
  "Returns true if this is a request to the backend API (e.g., /api/examples).
   API requests should be network-only, not cached."
  [request]
  (let [path (.-pathname (js/URL. (.-url request)))]
    (str/starts-with? path "/api/")))


(defn network-first-fetch
  [request]
  (p/catch
   (p/let [response (js/fetch request)
           cache    (js/caches.open "resources")]
     (.put cache request (.clone response))
     response)
   (fn [_]
     (js/caches.match request))))


(defn- request->ring
  "Transforms a JavaScript [Request](https://github.com/ring-clojure/ring/wiki/Concepts#requests) object into a Ring-style request hash map."
  [request]
  (let [request    (.clone request)
        url-object (js/URL. (.-url request))
        headers    (-> request .-headers .entries js/Object.fromEntries js->clj)]
    {:server-port    (.-port url-object)
     :server-name    (.-hostname url-object)
     :uri            (.-pathname url-object)
     :url            (.-url request)
     :query-string   (utils/non-blank (.-search url-object))
     :scheme         (-> url-object .-protocol (str/replace #":" "") keyword)
     :request-method (-> request .-method str/lower-case keyword)
     :headers        headers
     :body           (.-body request)

     ;; Custom non-ring fields
     :js/request request}))


(defn- ring->response
  "Transforms a Ring-style response hash map into JavaScript [Response](https://developer.mozilla.org/en-US/docs/Web/API/Response/Response) object into a Ring-style."
  [ring-response]
  (when-not (= (:status ring-response) 404)
    (js/Response.
     (:body ring-response)
     (clj->js
      (select-keys ring-response [:status :headers])))))


(defn local-handler
  [request]
  (let [ring-request (request->ring request)]
    (p/-> (application/ring-handler ring-request) ring->response)))


(js/self.addEventListener
 "fetch"
 (fn [^FetchEvent event]
   (log/debug :event/fetch event)
   (..
    event
    (respondWith
     (p/let [request (.. event -request)]
       (cond
         (api-request? request)    (js/fetch request)
         (static-request? request) (network-first-fetch request)
         :else                     (local-handler request)))))))


#_{:clojure-lsp/ignore [:clojure-lsp/unused-public-var]}
(defn ^:dev/after-load update-registration []
  (log/debug :dev/after-load "Update service worker")
  (js/self.registration.update))
