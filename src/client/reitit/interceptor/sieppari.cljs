(ns reitit.interceptor.sieppari
  (:require
   [promesa.core :as p]
   [promesa.impl.promise]
   [reitit.interceptor]
   [sieppari.async]
   [sieppari.core :as sieppari]
   [sieppari.interceptor]
   [sieppari.queue :as queue]))


(extend-protocol sieppari.interceptor/IntoInterceptor
  reitit.interceptor/Interceptor
  (into-interceptor [t] t))


(extend-protocol sieppari.async/AsyncContext
  promesa.impl.promise/PromiseImpl
  (async? [_] true)
  (continue [t f] (p/then t f))
  (catch [t f] (p/catch t f)))


(defn execute
  {:arglists
   '([interceptors request]
     [interceptors request on-complete on-error])}
  ([interceptors request on-complete on-error]
   (if-let [queue (queue/into-queue interceptors)]
     (try
       (-> (new sieppari/RequestResponseContext request nil nil queue nil)
           (#'sieppari/enter)
           (#'sieppari/leave)
           (#'sieppari/deliver-result :response on-complete on-error))
       (catch js/Error e (on-error e)))
     (on-complete nil))))


(def executor
  #_{:clj-kondo/ignore [:missing-protocol-method]}
  (reify reitit.interceptor/Executor
    (queue [_ interceptors]
      (queue/into-queue
       (map
        (fn [{:reitit.interceptor/keys [handler] :as interceptor}]
          (or handler interceptor))
        interceptors)))
    (execute [_ interceptors request]
      (execute interceptors request identity identity))
    (execute [_ interceptors request respond raise]
      (execute interceptors request respond raise))))
