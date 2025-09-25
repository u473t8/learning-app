(ns utils
  (:require
   #?(:clj
      [ring.util.codec :as codec])
   [clojure.string :as str])
  #?(:clj
     (:import
      [java.time Instant])))


(defn build-url
  [path query-params]
  #?(:clj
     (let [params (->> query-params
                       (remove #(-> % val nil?))
                       (into {}))]
       (cond-> path
         (seq params) (str "?" (codec/form-encode params))))

     :cljs
     (when (seq query-params)
       (let [query-params (js/URLSearchParams. (clj->js query-params))]
         (str path "?" query-params)))))


(comment
  (build-url "/base" {:a 1})     ;; => "/base?a=1"
  (build-url "/base&a=1" {:a 2}) ;; => "/base?a=2"
  (build-url "/base&a=1" {:b 2}) ;; => "/base?a=1&b=2"
  (build-url "/base" {:b [1 2]}) ;; => "/base?b=1&b=2"
  (build-url "/base" {:c nil})   ;; => "/base"
  (build-url "/base" {})         ;; => "/base"
  (build-url "/base" nil)        ;; => "/base"
  )


(defn prozent->color
  [prozent]
  (let [prozent (-> prozent (or 0) (max 0) (min 100))]
    (str "color-mix(in hsl, rgb(88, 204, 2) " prozent "%, rgb(255, 75, 75))")))


(defn sanitize-text
  "Remove extra spaces"
  [text]
  (-> text
      (or "")
      ;; (str/replace #"\p{Punct}" " ")
      (str/trim)
      (str/replace #"\s+" " ")))


(defn normalize-german
  [text]
  (-> text
      (sanitize-text)
      (str/lower-case)
      (str/replace #"ä" "ae")
      (str/replace #"ö" "oe")
      (str/replace #"ü" "ue")
      (str/replace #"ß" "ss")))


(defn includes?
  [word pattern]
  (let [word    (normalize-german word)
        pattern (normalize-german pattern)]
    (str/includes? word pattern)))


(defn timestamp:instant->unix
  [instant]
  #?(:clj   (.getTime ^java.util.Date instant)
     :cljs  (.getTime instant)))


(defn timestamp:iso->unix
  [iso]
  #?(:clj
     (.getEpochSecond (Instant/parse iso))
     :cljs
     (quot (js/Date.parse iso) 1000)))


(defn now
  []
  #?(:clj  (java.util.Date.)
     :cljs (js/Date.)))


(defn now-iso
  []
  #?(:cljs (.toISOString (now))))


(defn now-unix
  []
  #?(:cljs (quot (js/Date.now) 1000)))


(defn non-blank
  [string]
  (when-not (str/blank? string)
    string))


