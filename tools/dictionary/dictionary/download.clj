(ns dictionary.download
  (:require [clojure.java.io :as io]
            [org.httpkit.client :as http]))


(def ^:private kaikki-url
  "https://kaikki.org/dictionary/downloads/de/de-extract.jsonl.gz")


(def ^:private goethe-url
  "https://raw.githubusercontent.com/technologiestiftung/sprach-o-mat/main/dictionary_a1a2b1_onlystems.csv")


(defn- with-retry
  "Retry body-fn up to max-attempts times with exponential backoff.
   body-fn is a no-arg function. Rethrows the last exception on failure."
  [max-attempts body-fn]
  (loop [attempt 1]
    (let [result (try
                   {:ok (body-fn)}
                   (catch Exception e
                     (if (< attempt max-attempts)
                       (do
                         (println (format "  Attempt %d/%d failed: %s. Retrying..."
                                          attempt
                                          max-attempts
                                          (.getMessage e)))
                         (Thread/sleep (* 1000 (long (Math/pow 2 (dec attempt)))))
                         ::retry)
                       (throw e))))]
      (if (= result ::retry)
        (recur (inc attempt))
        (:ok result)))))


(defn- download-once!
  "Single download attempt. Streams URL to file."
  [url f]
  (let [{:keys [status body error]} @(http/get url {:as :stream})]
    (when error
      (when (instance? java.io.Closeable body)
        (.close ^java.io.Closeable body))
      (throw (ex-info (str "Download failed: " url) {:error error})))
    (when (not= 200 status)
      (when (instance? java.io.Closeable body)
        (.close ^java.io.Closeable body))
      (throw (ex-info (str "Download failed with status " status ": " url) {:status status})))
    (with-open [in  body
                out (io/output-stream f)]
      (io/copy in out))))


(defn download-file!
  "Streams URL to dest-path with retry. Skips if file already exists."
  [url dest-path]
  (let [f (io/file dest-path)]
    (if (.exists f)
      (println "  Already exists:" dest-path)
      (do
        (println "  Downloading:" url)
        (io/make-parents f)
        (with-retry 3 #(download-once! url f))
        (println "  Saved:" dest-path (format "(%.1f MB)" (/ (.length f) 1048576.0)))))))


(defn download-sources!
  "Downloads Kaikki gz and Goethe CSV to data-dir. Returns path map."
  [data-dir]
  (let [kaikki-path (str data-dir "/de-extract.jsonl.gz")
        goethe-path (str data-dir "/dictionary_a1a2b1_onlystems.csv")]
    (println "Downloading sources...")
    (download-file! kaikki-url kaikki-path)
    (download-file! goethe-url goethe-path)
    {:kaikki kaikki-path
     :goethe goethe-path}))
