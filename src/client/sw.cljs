;;
;; https://developer.mozilla.org/en-US/docs/Web/API/Service_Worker_API
;;
(ns sw
  (:require
   [application :as application]
   [clojure.string :as str]
   [lambdaisland.glogi :as log]
   [lambdaisland.glogi.console :as glogi-console]
   [promesa.core :as p]))


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
   (log/debug :install event)
   (p/let [cache (js/caches.open "resources")]
     (.addAll cache precache-url))))


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
     (js/self.clients.claim)))))


(defn wrap-js
  [handler]
  (fn [request]
    (let [url-object (js/URL. (.-url request))
          ;; https://github.com/ring-clojure/ring/wiki/Concepts#requests
          ring-request {:server-port    (.-port url-object)
                        :server-name    (.-hostname url-object)
                        :uri            (.-pathname url-object)
                        :url            (.-url request)
                        :query-string   (.-search url-object)
                        :sheme          (-> url-object .-protocol (str/replace #":" "") keyword)
                        :request-method (-> request .-method str/lower-case keyword)
                        :headers        (.-headers request)
                        :body           (.-body request)}
          ring-response (handler ring-request)]
      (js/console.log ::ring-response ring-response)
      (when-not (= (:status ring-response) 404)
        ;; https://developer.mozilla.org/en-US/docs/Web/API/Response/Response
        (js/Response.
         (:body ring-response)
         (clj->js
          (select-keys ring-response [:status :headers])))))))


(js/self.addEventListener
 "fetch"
 (fn [^FetchEvent event]
   (log/debug :fetch-url (.. event -request -url))
   (..
    event
    (respondWith
     (p/let [request (.. event -request)
             match   (js/caches.match request)]
       (or match ((wrap-js application/routes) request)))))))


(js/self.addEventListener
 "message"
 (fn [^ExtendableMessageEvent event]
   (log/debug :message/received ["Received a message from a client" event])
   (when (= (.. event -data) "claim-client")
     (log/debug :message/claim-clients "Claim clients")
     (p/do
       (js/self.clients.claim)
       (log/debug :message/claim-clients "Notify sender client")
       (.. event -source (postMessage #js {:type "controlled"}))))))


(defn ^:dev/after-load update-registration []
  (log/debug :dev/after-load "Update service worker")
  (js/self.registration.update))
