(ns sw-loader
  (:require
   [lambdaisland.glogi :as log]
   [lambdaisland.glogi.console :as glogi-console]
   [promesa.core :as p]))


(glogi-console/install!)

(log/set-levels
 {:glogi/root :debug})


(defn notify-page
  []
  (when-let [app-element (js/htmx.find "#app")]
    (log/debug :notify-page ["Notify app element that service worker is ready" app-element])
    (.dispatchEvent app-element (js/CustomEvent. "controlled"))))


(defn register-user
  []
  (log/debug :page/meta (js/document.querySelector "meta[name='user-id']"))
  (p/let [service-worker-registration js/navigator.serviceWorker.ready
          user-id (some-> (js/document.querySelector "meta[name='user-id']")
                          (.-content))]

    (log/debug :register-user/postMessage ["Registering the user for the current client" user-id])
    (.. service-worker-registration -active (postMessage #js {:type "register-user" :value user-id}))))


(defn claim-page-control
  [registration]
  (log/debug :claim-page-control/postMessage ["Requesting to claim the client"])
  (let [service-worker (or (.. registration -installing)
                           (.. registration -waiting)
                           (.. registration -active))]
    (.. service-worker (postMessage #js {:type "claim-client"}))))


(defn register-service-worker
  []
  (-> (js/navigator.serviceWorker.register "/js/app/sw.js" #js {:scope "/"})
      (p/then
       (fn on-registration-success [registration]
         (log/debug :register-service-worker/success ["Service Worker is registered" registration js/navigator.serviceWorker.controller])
         (if-not js/navigator.serviceWorker.controller
           (claim-page-control registration)
           (register-user))))
      (p/catch
       (fn on-registration-fail [error]
         (log/error :register-service-worker/error ["Service Worker registration failed" error])))))


(defn run-worker
  []
  (log/debug :run-worker/listen ["Listening for messages from service worker"])
  (js/navigator.serviceWorker.addEventListener
   "message"
   (fn [^ExtendableMessageEvent event]
     (log/debug :run-worker/message ["Received a message from service worker" event])
     (case (.. event -data -type)
       "page-controlled" (register-user)
       "user-registered" (notify-page))))

  (log/debug :run-worker/register-service-worker ["Registering service worker"])
  (register-service-worker)
  (log/debug :run-worker/wait-service-worker ["Waiting service worker to become ready"]))


;; Application entry point
(if (js-in "serviceWorker" js/navigator)
    (run-worker)
    (do
      (log/warn :registration/reject "Service Worker is not supported")
      ;; Workaround for a race condition: ensure htmx finishes binding
      ;; hx-trigger="controlled" on #app before dispatching the event
      (js/setTimeout notify-page 0)))


(defn ^:dev/after-load reload-page []
  (log/debug :dev/after-load "AFTER LOAD")
  (js/location.reload))
