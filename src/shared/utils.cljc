(ns utils
  (:require
   #?(:clj
      [ring.util.codec :as codec])
   [clojure.string :as str]))


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


(defn non-blank
  [string]
  (when-not (str/blank? string)
    string))


(defn kebab->snake
  "Convert a keyword name from kebab-case to snake_case string."
  [k]
  (str/replace (name k) #"-" "_"))


(defn transform-keys
  "Recursively transform map keys from kebab-case to snake_case."
  [x]
  (cond
    (map? x)        (-> x
                        (update-keys kebab->snake)
                        (update-vals transform-keys))
    (sequential? x) (mapv transform-keys x)
    (keyword? x)    (name x)
    :else           x))


;; =============================================================================
;; Time - Current
;; =============================================================================


(defn now-ms
  "Returns current time as milliseconds since epoch."
  []
  #?(:clj (System/currentTimeMillis)
     :cljs (js/Date.now)))


(defn now-iso
  "Returns current time as ISO-8601 string."
  []
  #?(:clj (.toString (java.time.Instant/now))
     :cljs (.toISOString (js/Date.))))


;; =============================================================================
;; Time - Conversions (all use milliseconds as base unit)
;; =============================================================================


(defn ms->iso
  "Converts milliseconds to ISO-8601 string."
  [ms]
  #?(:clj (.toString (java.time.Instant/ofEpochMilli ms))
     :cljs (.toISOString (js/Date. ms))))


(defn iso->ms
  "Converts ISO-8601 string to milliseconds."
  [iso]
  #?(:clj (.toEpochMilli (java.time.Instant/parse iso))
     :cljs (js/Date.parse iso)))


(defn date->ms
  "Converts Date object to milliseconds."
  [date]
  #?(:clj (.getTime ^java.util.Date date)
     :cljs (.getTime date)))
