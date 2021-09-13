(ns re-frame.utils
  (:require
    [re-frame.db :refer [app-db]]
    [re-frame.loggers :refer [console]]))

(defn frame-from
  "Extract the current frame from a query or event vector's metadata."
  [event-or-query-v]
  (if-let [frame (-> event-or-query-v meta :frame)]
    frame
    (do
      (console :warn "re-frame: expected to find a frame on: " event-or-query-v)
      app-db)))

(defn with-frame
  "Add the frame to the event or query vector's metadata."
  [event-or-query-v frame]
  (vary-meta event-or-query-v assoc :frame frame))

(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  will not be present in the new structure.
  The key thing is that 'm' remains identical? to itself if the path was never present"
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))

(defn first-in-vector
  [v]
  (if (vector? v)
    (first v)
    (console :error "re-frame: expected a vector, but got:" v)))

(defn apply-kw
  "Like apply, but f takes keyword arguments and the last argument is
  not a seq but a map with the arguments for f"
  [f & args]
  {:pre [(map? (last args))]}
  (apply f (apply concat
                  (butlast args) (last args))))
