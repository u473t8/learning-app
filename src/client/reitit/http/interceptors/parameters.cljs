(ns reitit.http.interceptors.parameters
  (:require
   [clojure.string :as str]
   [lambdaisland.glogi :as log]
   [promesa.core :as p]))


(defn parameters-interceptor
  "Interceptor to parse urlencoded parameters from the query string and form
  body (if the request is a url-encoded form). Adds the following keys to
  the request map:

  :query-params - a map of parameters from the query string
  :form-params  - a map of parameters from the body
  :params       - a merged map of all types of parameter"
  []
  {:name  ::parameters
   :enter (fn [ctx]
            (let [query-params (some-> ctx
                                       :request
                                       :query-string
                                       js/URLSearchParams.
                                       .entries
                                       js/Object.fromEntries
                                       (js->clj :keywordize-keys true))
                  query-params (or query-params {})
                  content-type (-> ctx :request :headers (get "content-type"))
                  ctx          (-> ctx
                                   (assoc-in [:request :query-params] query-params)
                                   (assoc-in [:request :params] query-params))]
              (log/debug :parameters-interceptor/enter {"Content-Type" content-type})
              (if (some-> content-type (str/includes? "application/x-www-form-urlencoded"))
                (p/let [form-data (-> ctx :request :js/request .formData) ; a promise
                        form-data (-> form-data .entries js/Object.fromEntries (js->clj :keywordize-keys true))]
                  (log/debug :parameters-interceptor/enter {"Form-Data" form-data})
                  ;; original ring.middleware.params middleware merges form-data and query-params in the same order.
                  (-> ctx
                      (update :request assoc :form-params form-data)
                      (update-in [:request :params] merge form-data)))
                ctx)))})
