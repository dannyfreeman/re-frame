(ns re-frame.cofx
  (:require
    [re-frame.db           :refer [app-db]]
    [re-frame.interceptor  :refer [->interceptor]]
    [re-frame.registrar    :refer [get-handler register-handler]]
    [re-frame.utils        :refer [frame-from]]
    [re-frame.loggers      :refer [console]]))


;; -- Registration ------------------------------------------------------------

(def kind :cofx)
(assert (re-frame.registrar/kinds kind))

(defn reg-cofx
  [id handler]
  (register-handler kind id handler))


;; -- Interceptor -------------------------------------------------------------

(defn inject-cofx
  ([id]
   (->interceptor
     :id      :coeffects
     :before  (fn coeffects-before
                [context]
                (if-let [handler (get-handler kind id)]
                  (update context :coeffects handler)
                  (console :error "No cofx handler registered for" id)))))
  ([id value]
   (->interceptor
     :id     :coeffects
     :before  (fn coeffects-before
                [context]
                (if-let [handler (get-handler kind id)]
                  (update context :coeffects handler value)
                  (console :error "No cofx handler registered for" id))))))


;; -- Builtin CoEffects Handlers  ---------------------------------------------

;; :frame
;;
;; Adds to coeffects the current frame attached to the event vector.
(reg-cofx
  :frame
  (fn [{:keys [event] :as coeffects}]
    (assoc coeffects :frame (frame-from event))))

;; :db
;;
;; Adds to coeffects the value in the current `frame`, under the key `:db`
(reg-cofx
  :db
  (fn db-coeffects-handler
    [{:keys [frame] :as coeffects}]
    (assoc coeffects :db (deref frame))))


;; Because these interceptors are used so much, we reify them
(def inject-frame (inject-cofx :frame))
(def inject-db (inject-cofx :db))


