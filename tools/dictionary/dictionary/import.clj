(ns dictionary.import
  (:require [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [db :as db]
            [dictionary.emit :as emit]
            [promesa.core :as p])
  (:import [java.net URI])
  (:gen-class))


(def ^:private default-db "dictionary-db")


(def ^:private default-batch-size 1000)


(def ^:private default-max-bytes 5242880)


(def ^:private meta-doc-id "dictionary-meta")


(def ^:private schema-version 1)


(def ^:private cli-options
  [["-i" "--input-dir PATH" "Directory containing dictionary files"]
   ["-d" "--db NAME" "CouchDB database name" :default default-db]
   ["-b" "--batch N" "Batch size" :default default-batch-size :parse-fn parse-long]
   ["-m" "--max-bytes BYTES" "Max bytes per batch" :default default-max-bytes :parse-fn parse-long]
   [nil "--reset" "Reset database before import"]
   [nil "--dry-run" "Validate files and print summary, then exit"]
   ["-h" "--help" "Show help"]])


(defn- exit!
  [code message]
  (binding [*out* *err*]
    (println message))
  (System/exit code))


(defn- usage
  [summary]
  (str
   "Usage: dictionary-import --input-dir PATH [--db NAME] [--batch N] [--max-bytes BYTES] [--reset]\n\n"
   "Options:\n"
   summary))


(defn- parse-args
  [args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)
        missing-input? (str/blank? (:input-dir options))]
    (cond
      (:help options) (exit! 0 (usage summary))
      (seq errors)    (exit! 1 (str (str/join "\n" errors) "\n\n" (usage summary)))
      (seq arguments) (exit! 1
                             (str "Unexpected arguments: " (str/join " " arguments)
                                  "\n\n" (usage summary)))
      missing-input?  (exit! 1 (str "Missing required argument: --input-dir\n\n" (usage summary)))
      :else           options)))


(defn- ensure-env
  [key]
  (let [value (System/getenv key)]
    (when (str/blank? value)
      (exit! 1 (str "Missing required environment variable: " key)))
    value))


(defn- conn-from-env
  []
  (let [url    (ensure-env "COUCHDB_URL")
        uri    (URI. url)
        scheme (or (.getScheme uri) "http")
        host   (.getHost uri)
        port   (let [p (.getPort uri)]
                 (if (neg? p)
                   (if (= scheme "https") 443 80)
                   p))
        user   (or (System/getenv "COUCHDB_USER") "admin")
        pass   (ensure-env "COUCHDB_PASS")]
    (when (str/blank? host)
      (exit! 1 "COUCHDB_URL must include host"))
    {:scheme   scheme
     :host     host
     :port     port
     :username user
     :password pass}))


(defn- ensure-db!
  [conn db-name reset?]
  (let [db-ref   {:name db-name :conn conn}
        existed? (db/exists? conn db-name)]
    (when (and reset? existed?)
      @(db/destroy db-ref))
    (let [db (db/use conn db-name)]
      ;; Allow public read access (empty members = no auth required for reads)
      (db/secure db {:admins  {:names [] :roles []}
                     :members {:names [] :roles []}})
      db)))


(defn- validate-input!
  [input-dir]
  (when (str/blank? input-dir)
    (exit! 1 "Missing required argument: --input-dir"))
  (let [dir (io/file input-dir)]
    (when-not (.isDirectory dir)
      (exit! 1 (str "Input directory not found: " input-dir)))))


(defn- read-manifest
  [input-dir]
  (let [manifest-path (str input-dir "/manifest.edn")
        f (io/file manifest-path)]
    (when-not (.exists f)
      (exit! 1 (str "Missing manifest: " manifest-path)))
    {:path manifest-path
     :data (edn/read-string (slurp f))}))


(defn- manifest-files
  [manifest]
  (get-in manifest [:files]))


(defn- file-entry
  [files filename]
  (get files filename))


(defn- manifest-sha
  [manifest-path]
  (emit/sha256 manifest-path))


(defn- compare-meta
  [meta manifest-sha]
  (let [schema (or (:schema-version meta) (:schema_version meta))
        sha    (or (:manifest-sha256 meta) (:manifest_sha256 meta))]
    (and (= schema-version schema)
         (= manifest-sha sha))))


(defn- bulk-upload!
  "Upload a batch to CouchDB. Returns {:ok n :errors [...]}."
  [db docs]
  (p/let [results  (db/bulk-docs db docs)
          failures (vec (filter :error results))]
    {:ok     (- (count results) (count failures))
     :errors failures}))


(defn- jsonl-batches
  [reader batch-size max-bytes]
  (let [lines (remove str/blank? (line-seq reader))]
    ((fn step [lines]
       (lazy-seq
        (loop [ls lines, batch [], bytes 0]
          (if-let [line (first ls)]
            (let [doc    (json/parse-string line true)
                  bytes' (+ bytes (inc (count (.getBytes ^String line "UTF-8"))))]
              (if (or (>= (inc (count batch)) batch-size)
                      (>= bytes' max-bytes))
                (cons (conj batch doc) (step (rest ls)))
                (recur (rest ls) (conj batch doc) bytes')))
            (when (seq batch)
              (list batch))))))
     lines)))


(defn- import-jsonl!
  [db file-path batch-size max-bytes]
  (println (str "Importing " file-path "..."))
  (with-open [reader (io/reader file-path)]
    (let [total      (atom 0)
          all-errors (atom [])
          batches    (jsonl-batches reader batch-size max-bytes)]
      @(p/doseq [batch batches]
         (p/let [{:keys [ok errors]} (bulk-upload! db batch)]
           (swap! total + ok)
           (when (seq errors)
             (swap! all-errors into errors))))
      (when (seq @all-errors)
        (let [err-count (count @all-errors)]
          (exit! 1
                 (format "Upload of %s had %d error(s). First 3:\n%s"
                         file-path
                         err-count
                         (pr-str (take 3 @all-errors))))))
      @total)))


(defn- write-metrics!
  [input-dir manifest-data db-info]
  (let [path    (str input-dir "/import-metrics.json")
        payload {:generated_at (:generated-at manifest-data)
                 :doc_count    (:doc-count db-info)
                 :data_size    (:data-size db-info)
                 :disk_size    (:disk-size db-info)
                 :files        (:files manifest-data)}]
    (spit path (json/generate-string payload {:pretty true}))
    (println (str "Wrote metrics: " path))))


(defn- validate-file-hash!
  [file-path expected-sha]
  (let [actual (emit/sha256 file-path)]
    (when (not= expected-sha actual)
      (exit! 1
             (format "SHA-256 mismatch for %s\n  expected: %s\n  actual:   %s"
                     file-path
                     expected-sha
                     actual)))))


(defn- validate-file-count!
  [file-path expected-count]
  (with-open [reader (io/reader file-path)]
    (let [actual (count (remove str/blank? (line-seq reader)))]
      (when (not= expected-count actual)
        (exit! 1
               (format "Line count mismatch for %s\n  expected: %d\n  actual:   %d"
                       file-path
                       expected-count
                       actual))))))


(defn- validate-sample-docs!
  [file-path doc-type]
  (with-open [reader (io/reader file-path)]
    (let [sample-lines (->> (line-seq reader)
                            (remove str/blank?)
                            (take 5))]
      (doseq [line sample-lines]
        (let [doc (try (json/parse-string line true)
                       (catch Exception e
                         (exit! 1 (format "Invalid JSON in %s: %s" file-path (.getMessage e)))))]
          (case doc-type
            :entries
            (do
              (when-not (str/starts-with? (str (:_id doc)) "lemma:")
                (exit! 1 (format "Entry _id does not start with 'lemma:' in %s: %s" file-path (:_id doc))))
              (when (str/blank? (:type doc))
                (exit! 1 (format "Entry missing 'type' in %s" file-path)))
              (when (str/blank? (:value doc))
                (exit! 1 (format "Entry missing 'value' in %s" file-path)))
              (when (empty? (:translation doc))
                (exit! 1 (format "Entry has empty 'translation' in %s" file-path))))

            :surface-forms
            (do
              (when-not (str/starts-with? (str (:_id doc)) "sf:")
                (exit! 1 (format "Surface form _id does not start with 'sf:' in %s: %s" file-path (:_id doc))))
              (when (str/blank? (:value doc))
                (exit! 1 (format "Surface form missing 'value' in %s" file-path)))
              (when (empty? (:entries doc))
                (exit! 1 (format "Surface form has empty 'entries' in %s" file-path))))))))))


(defn- validate-jsonl!
  "Pre-flight validation: check SHA, line count, and sample doc structure for both JSONL files."
  [input-dir files]
  (let [entries-file (get files "dictionary-entries.jsonl")
        sf-file      (get files "surface-forms.jsonl")
        entries-path (str input-dir "/dictionary-entries.jsonl")
        sf-path      (str input-dir "/surface-forms.jsonl")]
    (println "Validating JSONL files...")
    (validate-file-hash! entries-path (:sha256 entries-file))
    (validate-file-count! entries-path (:count entries-file))
    (validate-sample-docs! entries-path :entries)
    (validate-file-hash! sf-path (:sha256 sf-file))
    (validate-file-count! sf-path (:count sf-file))
    (validate-sample-docs! sf-path :surface-forms)
    (println "  Validation passed.")))


(defn -main
  [& args]
  (let [{:keys [input-dir db batch max-bytes reset dry-run]} (parse-args args)
        _ (validate-input! input-dir)

        conn (conn-from-env)
        {:keys [path data]} (read-manifest input-dir)
        manifest-sha-value (manifest-sha path)
        files (manifest-files data)
        entries-path (str input-dir "/dictionary-entries.jsonl")
        forms-path (str input-dir "/surface-forms.jsonl")]
    (when-not (and (map? files)
                   (file-entry files "dictionary-entries.jsonl")
                   (file-entry files "surface-forms.jsonl"))
      (exit! 1 "Manifest is missing required file entries."))

    (validate-jsonl! input-dir files)

    (when dry-run
      (let [entry-stats (get files "dictionary-entries.jsonl")
            sf-stats    (get files "surface-forms.jsonl")]
        (println "\n=== Dry Run Summary ===")
        (println (format "  Dictionary entries: %,d (%,d bytes)" (:count entry-stats) (:bytes entry-stats)))
        (println (format "  Surface forms:      %,d (%,d bytes)" (:count sf-stats) (:bytes sf-stats)))
        (println (format "  Target database:    %s" db))
        (println "  Validation passed. No data was uploaded.")
        (println "========================")
        (System/exit 0)))

    (let [db-ref      (ensure-db! conn db reset)
          info        @(db/info db-ref)
          doc-count   (:doc-count info)
          meta        (when (pos? doc-count) @(db/get db-ref meta-doc-id))
          up-to-date? (and meta (compare-meta meta manifest-sha-value))]
      (cond
        up-to-date?
        (do
          (println "Dictionary DB already up to date. Skipping import.")
          (System/exit 0))

        (pos? doc-count)
        (exit! 1 "Dictionary DB is not empty. Re-run with --reset to replace it.")

        :else
        (let [entry-count (import-jsonl! db-ref entries-path batch max-bytes)
              form-count  (import-jsonl! db-ref forms-path batch max-bytes)
              meta-doc    {:_id          meta-doc-id
                           :type         "dictionary-meta"
                           :schema-version schema-version
                           :generated-at (:generated-at data)
                           :manifest-sha256 manifest-sha-value
                           :files        files}]
          @(db/insert db-ref meta-doc)
          (let [info         @(db/info db-ref)
                actual-count (:doc-count info)
                expected     (+ entry-count form-count 1)]
            (when (not= expected actual-count)
              (exit! 1 (format "Post-upload count mismatch: expected %d, got %d" expected actual-count)))
            (println (format "Import complete. Entries: %,d, Surface forms: %,d" entry-count form-count))
            (write-metrics! input-dir data info)))))))
