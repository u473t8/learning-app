(ns tools.utils
  (:require
   [ring.util.codec :as codec]
   [clojure.string :as str]))

;; "///"


(defn build-url
  [base params]
  (let [params (->> params
                    (remove #(-> % val nil?))
                    (into {}))]
    (cond-> base
      (seq params) (str "?" (codec/form-encode params)))))


(comment
  (build-url "/base" {:a 1})     ;; => "/base?a=1"
  (build-url "/base" {:b [1 2]}) ;; => "/base?b=1&b=2"
  (build-url "/base" {:c nil})   ;; => "/base"
  (build-url "/base" {})         ;; => "/base"
  (build-url "/base" nil)        ;; => "/base"
  )


(defn sanitize-text
  [text]
  (-> text
      (or "")
      (str/replace #"\p{Punct}" " ")
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


(defn prozent->color
  [prozent]
  (let [prozent (-> prozent (or 0) (max 0) (min 100))]
    (format "color-mix(in hsl, rgb(88, 204, 2) %s%%, rgb(255, 75, 75))" prozent)))
