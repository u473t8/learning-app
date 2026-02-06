;;
;; https://developer.mozilla.org/en-US/docs/Web/API/Service_Worker_API
;;
(ns sw
  (:require
   [application :as application]
   [clojure.string :as str]
   [db :as db]
   [db-migrations :as db-migrations]
   [dbs :as dbs]
   [dictionary-sync :as dictionary-sync]
   [lambdaisland.glogi :as log]
   [logging]
   [promesa.core :as p]
   [tasks :as tasks]
   [utils :as utils]))


(def ^:const update-channel-name "sw-update-channel")


(def base-precache-urls
  ["/"
   "/manifest.json"
   "/css/styles.css"
   "/js/word-autocomplete.js"
   "/js/sw-loader.js"
   "/fonts/Nunito/nunito-v26-cyrillic_latin-regular.woff2"
   "/fonts/Nunito/nunito-v26-cyrillic_latin-500.woff2"
   "/fonts/Nunito/nunito-v26-cyrillic_latin-600.woff2"
   "/fonts/Nunito/nunito-v26-cyrillic_latin-600italic.woff2"
   "/fonts/Nunito/nunito-v26-cyrillic_latin-700.woff2"
   "/fonts/Nunito/nunito-v26-cyrillic_latin-800.woff2"
   "/js/htmx/htmx.min.js"
   "/favicon.ico"
   "/icons.svg"
   "/icons/ue-192.png"
   "/icons/ue-512.png"])


;;
;; Helpers
;;


(def ^:private update-channel
  (js/BroadcastChannel. update-channel-name))


;; Responder: replies to probes from other SWs.
;; Uses the shared channel, so probes from this SW are excluded by BroadcastChannel API.
(.addEventListener
 update-channel
 "message"
 (fn [event]
   (when (= "probe-manual-update" (.-data event))
     (log/debug :sw/responding-to-update-probe {})
     (.postMessage update-channel "supports-manual-update"))))


(defn- supports-manual-update?
  "Probe active SW via BroadcastChannel. Returns promise<boolean>."
  []
  (p/create
   (fn [resolve _]
     (let [timeout-id (atom nil)
           listener   (fn [event]
                        (when (= "supports-manual-update" (.-data event))
                          (js/clearTimeout @timeout-id)
                          (resolve true)))]
       (doto update-channel
         (.addEventListener "message" listener)
         (.postMessage "probe-manual-update"))
       (reset! timeout-id
         (js/setTimeout
          (fn []
            (.removeEventListener update-channel "message" listener)
            (resolve false))
          500))))))


(defn- set-update-pending!
  [pending?]
  (p/let [device-db (dbs/device-db)
          existing  (db/get device-db "sw-update-pending")
          doc       (merge {:_id "sw-update-pending" :pending pending?}
                           (when existing {:_rev (:_rev existing)}))]
    (db/insert device-db doc)))


;;
;; Listeners
;;


(js/self.addEventListener
 "install"
 (fn [^InstallEvent event]
   (log/debug :event/install event)
   (..
    event
    (waitUntil
     (p/do
       (set-update-pending! true)
       (p/-> (js/caches.open "resources")
             (.addAll base-precache-urls))
       ;; Check if active SW supports manual update
       (p/let [supports-manual? (supports-manual-update?)]
         (when-not supports-manual?
           (log/info :sw/auto-skip-waiting {:reason "active-sw-unsupported"})
           (js/self.skipWaiting))))))))


(js/self.addEventListener
 "activate"
 (fn [event]
   (log/debug :event/activate event)
   ;; Fire-and-forget: start dictionary sync in background, don't block activation
   (dictionary-sync/ensure-loaded!)
   (..
    event
    (waitUntil
     (p/do
       (set-update-pending! false)
       (js/self.clients.claim)
       (db-migrations/ensure-migrated!)
       (tasks/start! {:device-db (dbs/device-db)
                      :user-db   (dbs/user-db)}))))))


(js/self.addEventListener
 "message"
 (fn [event]
   (case (.. event -data -type)
     "ping" (some-> (.-source event)
                    (.postMessage #js {:type "pong"}))
     "SKIP_WAITING" (js/self.skipWaiting)
     nil)))


(js/self.addEventListener
 "online"
 (fn [_event]
   (tasks/resume!)))


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
        headers    (-> request .-headers .entries js/Object.fromEntries js->clj)
        headers    (update-keys headers str/lower-case)]
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
     :js/request     request}))


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
