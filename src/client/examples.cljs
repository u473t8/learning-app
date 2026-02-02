(ns examples
  "Client module for fetching example sentences from the backend."
  (:refer-clojure :exclude [list find])
  (:require
   [db :as db]
   [lambdaisland.glogi :as log]
   [promesa.core :as p]
   [tasks :as tasks]
   [utils :as utils]))


(defn fetch-one
  "Fetches an example sentence for the given German word from the backend.
   Returns a promise that resolves to the example map.
   Rejects on network or server errors."
  [word]
  (let [url (str "/api/examples?word=" (js/encodeURIComponent word))]
    (p/let [response (js/fetch url)]
      (if (.-ok response)
        (p/let [json (.json response)]
          (js->clj json :keywordize-keys true))
        (p/rejected (ex-info "Server error fetching example"
                             {:word word :status (.-status response)}))))))


(defn save-example!
  "Saves an example document for a vocabulary word.
   `db` - the PouchDB database instance
   `word-id` - the _id of the vocab document
   `word` - the German word (denormalized for convenience)
   `example` - map with :value, :translation, :structure from the backend

   Returns a promise. Throws if example is invalid."
  [db word-id word example]
  (when-not (and (:value example) (:translation example))
    (throw (ex-info "Invalid example: missing required fields"
                    {:word-id word-id :example example})))
  (let [example-doc {:type        "example"
                     :word-id     word-id
                     :word        word
                     :value       (:value example)
                     :translation (:translation example)
                     :structure   (:structure example)
                     :created-at  (utils/now-iso)}]
    (db/insert db example-doc)))


(defn find
  "Retrieves the example document for a given word-id, or nil if none exists."
  [db word-id]
  (p/let [{examples :docs} (db/find db {:selector {:type "example" :word-id word-id}})]
    (first examples)))


(defn list
  "Retrieves example documents for the given word-ids."
  [db word-ids]
  (p/let [{examples :docs} (db/find db {:selector {:type "example" :word-id {:$in word-ids}}})]
    examples))


(defn remove!
  "Deletes an example document by its _id. No-op if document doesn't exist."
  [db example-id]
  (p/let [example (db/get db example-id)]
    (when example
      (db/remove db example))))


(defn create-fetch-task!
  "Creates a task to fetch an example for the given word-id."
  [word-id]
  (tasks/create-task! "example-fetch" {:word-id word-id}))


(defmethod tasks/execute-task "example-fetch"
  [{:keys [data]} {:keys [user-db device-db]}]
  (let [{:keys [word-id]} data]
    (p/let [word-doc (db/get user-db word-id)]
      (if-not word-doc
        (do
          (log/warn :example-fetch/word-not-found {:word-id word-id})
          ;; Word deleted, consider task done
          true)

        (p/catch
         (p/let [example (fetch-one (:value word-doc))]
           (save-example! device-db word-id (:value word-doc) example)
           true)

         (fn [err]
           (log/warn :example-fetch/failed {:word-id word-id :error (ex-message err)})
           ;; Return false to trigger retry
           false))))))
