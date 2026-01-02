(ns reitit.http.interceptors.keyword-parameters
  (:require [ring.middleware.keyword-params :as keyword-params]))

(defn keyword-parameters-interceptor
  "Interceptor to convert any string keys in the :params map to keywords.
  Only keys that can be turned into valid keywords are converted.

  This middleware does not alter the maps under :*-params keys. These are left
  as strings. "
  []
  {:name ::keyword-parameters
   :enter (fn [ctx]
            (update ctx :request keyword-params/keyword-params-request))})
