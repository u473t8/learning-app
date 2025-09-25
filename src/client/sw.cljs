;;
;; https://developer.mozilla.org/en-US/docs/Web/API/Service_Worker_API
;;
(ns sw
  (:require
   [application :as application]
   [clojure.string :as str]
   [db :as db]
   [lambdaisland.glogi :as log]
   [lambdaisland.glogi.console :as glogi-console]
   [promesa.core :as p]
   [vocabulary :as vocabulary]
   [utils :as utils]))


(glogi-console/install!)


(log/set-levels
 {:glogi/root :all})


(def precache-url
  ["/"
   "/manifest.json"
   "/css/styles.css"
   "/fonts/Nunito/nunito-v26-cyrillic_latin-regular.woff2"
   "/fonts/Nunito/nunito-v26-cyrillic_latin-500.woff2"
   "/fonts/Nunito/nunito-v26-cyrillic_latin-700.woff2"
   "/fonts/Nunito/nunito-v26-cyrillic_latin-800.woff2"
   "/js/htmx/htmx.min.js"
   "/js/htmx/idiomorph-ext.min.js"
   "/favicon.ico"
   "/icons.svg"
   "/js/app/shared.js"
   "/js/app/sw-loader.js"])


(js/self.addEventListener
 "install"
 (fn [^InstallEvent event]
   (log/debug :event/install event)
   (js/self.skipWaiting)
   (..
    event
    (waitUntil
     (p/let [cache (js/caches.open "resources")]
       (.addAll cache precache-url))))))


(js/self.addEventListener
 "activate"
 (fn [event]
   (log/debug :event/activate event)
   (..
    event
    (waitUntil
     (js/self.clients.claim)))))


(defn local-routes
  [request]
  (let [request    (.clone request)
        url-object (js/URL. (.-url request))
        ;; https://github.com/ring-clojure/ring/wiki/Concepts#requests
        headers      (-> request .-headers .entries js/Object.fromEntries js->clj)
        ring-request {:server-port    (.-port url-object)
                      :server-name    (.-hostname url-object)
                      :uri            (.-pathname url-object)
                      :url            (.-url request)
                      :query-string   (utils/non-blank (.-search url-object))
                      :sheme          (-> url-object .-protocol (str/replace #":" "") keyword)
                      :request-method (-> request .-method str/lower-case keyword)
                      :headers        headers
                      :body           (.-body request)

                      ;; Custom non-ring fields
                      :js/request     request}]
    (p/let [ring-response (application/routes ring-request)]
      (when-not (= (:status ring-response) 404)
        ;; https://developer.mozilla.org/en-US/docs/Web/API/Response/Response
        (js/Response.
         (:body ring-response)
         (clj->js
          (select-keys ring-response [:status :headers])))))))


(js/self.addEventListener
 "fetch"
 (fn [^FetchEvent event]
   (log/debug :event/fetch event)
   (..
    event
    (respondWith
     (p/let [request (.. event -request)]
       (p/-> (js/caches.match request)
             (or (local-routes request))
             (or (js/fetch request))))))))


(js/self.addEventListener
 "message"
 (fn [^ExtendableMessageEvent event]
   (log/debug :event/message ["Received a message from a client" event])
   (case (.. event -data -type)
     "claim-client"  (p/do
                       (log/debug :event/message ["Claim clients"])
                       (js/self.clients.claim)
                       (log/debug :event/message ["Notify sender client"])
                       (.. event -source (postMessage #js {:type "page-controlled"})))
     "register-user" (p/let [user-data (db/get application/db "user-data")
                             user-id   (.. event -data -value)
                             client-id (.. event -source -id)]
                       (log/debug :message/event ["User Data" user-data])
                       (when (nil? user-data)
                         (log/debug :event/message ["Register user" user-id])
                         (db/insert application/db {:user-id user-id} "user-data"))
                       (log/debug :event/message ["Notify sender client" client-id])
                       (.. event -source (postMessage #js {:type "user-registered"}))))))


#_{:clojure-lsp/ignore [:clojure-lsp/unused-public-var]}
(defn ^:dev/after-load update-registration []
  (log/debug :dev/after-load "Update service worker")
  (js/self.registration.update))
