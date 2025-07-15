(ns sw-loader
  (:require
   [lambdaisland.glogi :as log]
   [lambdaisland.glogi.console :as glogi-console]
   [promesa.core :as p]))


(glogi-console/install!)

(log/set-levels
 {:glogi/root :debug})


(def notify-page
  (let [app-element (js/document.getElementById "app")
        controlled-event (js/CustomEvent. "controlled")]
    (fn []
      (log/debug :notify-page ["Notify app element that service worker is ready" app-element])
      (.. app-element (dispatchEvent controlled-event)))))


(defn most-active-service-worker
  [registration]
  (or
   (.. registration -installing)
   (.. registration -waiting)
   (.. registration -active)))


(defn on-registration-success
  [registration]
  (log/debug :on-registration-success ["Service Worker is registered" registration])
  (let [service-worker (most-active-service-worker registration)]
    (if-not js/navigator.serviceWorker.controller
      (do
        (log/debug :on-registration-success/no-controller "But it does not own the page. Requesting to claim the client")
        (.. service-worker (postMessage "claim-client")))
      (p/do
        js/navigator.serviceWorker.ready
        (notify-page)))))


(defn on-registration-fail
  [error]
  (log/error :registration/fail ["Service Worker registration failed" error]))


(defn register-service-worker
  []
  (-> (js/navigator.serviceWorker.register "/js/app/sw.js" #js {:scope "/"})
      (p/then on-registration-success)
      (p/catch on-registration-fail)))


(defn run-application
  []
  (log/debug :run-application/listen ["Listening for 'controlled' event after hard reset"])
  (js/navigator.serviceWorker.addEventListener
   "message"
   (fn [^ExtendableMessageEvent event]
     (log/debug :run-application/message ["Received a message" event])
     (when (= (.. event -data -type) "controlled")
       (notify-page))))

  (log/debug :run-application/register-service-worker "Registering the Service Worker")
  (register-service-worker)
  (log/debug :run-application/wait-service-worker "Waiting service worker to become ready"))


(if (js-in "serviceWorker" js/navigator)
  (run-application)
  (log/error :registration/reject "Service Worker is not supported"))


(defn ^:dev/after-load reload-page []
  (log/debug :dev/after-load "AFTER LOAD !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!1"))
