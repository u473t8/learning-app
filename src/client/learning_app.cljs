(ns learning-app
  (:require
   [reitit.ring :as ring]
   ["@thi.ng/hiccup" :as hiccup]))


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
    learning-app/routes
    {:data {:middleware [wrap-hiccup-render]}})
   (constantly {:status 404, :body ""})))
