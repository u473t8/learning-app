(ns examples
  "Client module for fetching example sentences from the backend."
  (:refer-clojure :exclude [list find])
  (:require
   [dbs :as dbs]
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
   `dbs` - the databases map
   `word-id` - the _id of the vocab document
   `word` - the German word (denormalized for convenience)
   `example` - map with :value, :translation, :structure from the backend

   Returns a promise. Throws if example is invalid."
  [dbs word-id word example]
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
    (dbs/insert dbs example-doc)))


(defn find
  "Retrieves the example document for a given word-id, or nil if none exists."
  [dbs word-id]
  (p/let [{examples :docs} (dbs/find dbs {:selector {:type "example" :word-id word-id}})]
    (first examples)))


(defn list
  "Retrieves example documents for the given word-ids."
  [dbs word-ids]
  (p/let [{examples :docs} (dbs/find dbs {:selector {:type "example" :word-id {:$in word-ids}}})]
    examples))


(defn remove!
  "Deletes an example document by its _id. No-op if document doesn't exist."
  [dbs example-id]
  (p/let [example (dbs/get dbs "example" example-id)]
    (when example
      (dbs/remove dbs example))))


(defn create-fetch-task!
  "Creates a task to fetch an example for the given word-id."
  [word-id]
  (tasks/create-task! "example-fetch" {:word-id word-id}))


(defmethod tasks/execute-task "example-fetch"
  [{:keys [data]} dbs]
  (let [{:keys [word-id]} data]
    (p/let [word-doc (dbs/get dbs "vocab" word-id)]
      (if-not word-doc
        (do
          (log/warn :example-fetch/word-not-found {:word-id word-id})
          ;; Word deleted, consider task done
          true)

        (p/catch
          (p/let [example (fetch-one (:value word-doc))]
            (save-example! dbs word-id (:value word-doc) example)
            true)

          (fn [err]
            (log/warn :example-fetch/failed {:word-id word-id :error (ex-message err)})
            ;; Return false to trigger retry
            false))))))
