(ns sw-version
  (:require [clojure.java.io :as io])
  (:import [java.security MessageDigest]))


(defmacro precache-manifest
  "Returns {:list urls, :hash \"abcd1234\"} where hash is computed from
   the content of the corresponding files in resources/public/.
   URLs that don't map to static files (e.g. \"/\") are skipped."
  [urls]
  (let [digest (MessageDigest/getInstance "SHA-256")]
    (doseq [url urls]
      (let [path (str "resources/public" url)
            f    (io/file path)]
        (when (.isFile f)
          (with-open [is (io/input-stream f)]
            (let [buf (byte-array 8192)]
              (loop []
                (let [n (.read is buf)]
                  (when (pos? n)
                    (.update digest buf 0 n)
                    (recur)))))))))
    (let [hex (->> (.digest digest)
                   (map #(format "%02x" (bit-and % 0xff)))
                   (apply str))
          ver (subs hex 0 8)]
      {:list urls :hash ver})))
