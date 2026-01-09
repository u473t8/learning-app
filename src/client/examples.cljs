(ns examples
  "Client module for fetching and syncing example sentences from the backend.

   The sync strategy is:
   1. Detect when the client is online
   2. Find vocabulary words that don't have examples yet
   3. Fetch examples in parallel (with concurrency limit) for speed
   4. On failure, skip the word - it will be retried on next online event
   5. Store each example in PouchDB as it arrives"
  (:require
   [lambdaisland.glogi :as log]
   [promesa.core :as p]
   [vocabulary :as vocabulary]))


;; Configuration
(def ^:private max-concurrent-fetches
  "Maximum number of parallel fetch requests."
  5)


(defn online?
  "Returns true if the browser reports being online."
  []
  js/navigator.onLine)


(defn fetch-example
  "Fetches an example sentence for the given German word from the backend.
   Returns a promise that resolves to the example map or nil on failure.
   Does not retry - relies on online events for retries at a higher level."
  [word]
  (let [url (str "/api/examples?word=" (js/encodeURIComponent word))]
    (-> (js/fetch url)
        (p/then (fn [response]
                  (if (.-ok response)
                    (p/let [json (.json response)]
                      (js->clj json :keywordize-keys true))
                    ;; Server error - log and return nil
                    (log/warn :fetch-example/server-error {:word word :status (.-status response)}))))
        (p/catch (fn [error]
                   ;; Network error - log and return nil
                   (log/warn :fetch-example/network-error {:word word :error (ex-message error)}))))))


(defn- fetch-and-save-example!
  "Fetches an example for a word and saves it if successful.
   Returns a promise that resolves to true if saved, false otherwise."
  [{:keys [id value]}]
  (p/let [example (fetch-example value)]
    (when example
      (vocabulary/save-example id value example)
      (log/debug :fetch-and-save-example/saved {:word value}))
    (boolean example)))


(defn- process-batch!
  "Processes a batch of words in parallel.
   Returns a promise that resolves to the count of successfully fetched examples."
  [words]
  (log/debug :process-batch/started {:size (count words)})
  (p/let [results (p/all (map fetch-and-save-example! words))]
    (count (filter true? results))))


(defn sync-examples!
  "Fetches examples for all vocabulary words that don't have one yet.

   Processing uses parallel batches (max-concurrent-fetches at a time) to:
   - Speed up the sync process
   - Avoid overwhelming the backend

   Failed words are skipped and will be retried on the next online event.
   Returns a promise that resolves when sync is complete (or when offline)."
  []
  (p/let [words (vocabulary/words-without-examples)]
    (log/debug :sync-examples/started {:words words})
    (if (empty? words)
      (log/info :sync-examples/complete {:reason "no words without examples"})
      (let [batches (partition-all max-concurrent-fetches words)]
        (p/loop [remaining-batches batches
                 total-fetched     0]
          (if (or (empty? remaining-batches) (not (online?)))
            (log/info :sync-examples/complete
                      {:fetched   total-fetched
                       :remaining (* (count remaining-batches) max-concurrent-fetches)
                       :reason    (if (empty? remaining-batches)
                                    "all done"
                                    "went offline")})
            (p/let [batch   (first remaining-batches)
                    fetched (process-batch! batch)]
              (p/recur (rest remaining-batches)
                       (+ total-fetched fetched)))))))))
