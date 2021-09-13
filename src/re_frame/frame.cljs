;; cljs for now. clj could use dynamic scope instead of context.
(ns re-frame.frame
  (:require
   [react]
   [reagent.core :as r]
   [re-frame.core :as rf]
   [re-frame.db :as db]
   [re-frame.interop :as interop]
   [re-frame.loggers :refer [console]]))

(def app-db-context-id
  "The frame id for app-db."
  (str ::app-db))

(defonce ^:private frame-context-registry
  (atom {app-db-context-id db/app-db}))

(defonce ^:private frame-context (react/createContext))

(def ^:private FrameContextProvider (.-Provider frame-context))

(defn frame-provider
  "A reagent component that accepts a frame and makes it available
  to the provided child `component` as the first argument.
  Any extra arguments provided will be passed the to the given component after the frame.

  If only one argument, the component, is provided, then app-db will be used as the frame.

  Any component in it's subtree can subscribe to and dispatch to that frame
  with the `use-frame` and `use-subscription` react hooks.
  For this to work, functional react components must be used.

  The provided child component will be rendered as a functional component,
  but it is up the developer to either use reagent's functional component compiler
  or call each component with reagent's :f> directive."
  ([component]
   [:> FrameContextProvider {:value app-db-context-id}
     [:f> component db/app-db]])
  ([{:keys [context-id frame] :as props} _component & component-props]
   (when-not (or (and context-id frame)
                 (not (interop/ratom? frame)))
     (throw (ex-info "Expected both a context-id and a frame"
                     {:context-id context-id :frame frame})))
   (let [context-id (str context-id)
         ;; Wrap in a reaction so that disposing plays nicely with hot-reload.
         ;; Using form-3 component causes the disposal to happen after the next
         ;; version of the component is mounted, at which point the frame is removed
         ;; from the registry and use-frame falls back to app-db.
         ;; Note this is only done because of how the frames are stored in the frame-context-registry.
         ;; Normal ratoms will work just fine outside of this context.
         r (interop/make-reaction #(deref frame)
                                  :on-dispose #(do
                                                 (prn :im-disposin context-id)
                                                 (swap! frame-context-registry dissoc context-id))
                                  :on-set (fn [_ next] (reset! frame next)))]
     (swap! frame-context-registry assoc context-id r)
     (fn [next-props component & component-props]
       (when (and interop/debug-enabled?
                  (not= next-props props))
         (console :warn
                  "re-frame: All changes to context-id and frame props for the frame-provider"
                  "component are ignored after the first render has occurred."))
       [:> FrameContextProvider {:value context-id}
        (into [:f> component r] component-props)]))))


;; -- React Hooks -----------------------------------
;; These need to be the first statements in a let block,
;; which must be the first expression in a component.
;; https://reactjs.org/docs/hooks-rules.html

(defn use-frame
  "A react hook that provides the current frame.
  This relies on some parent component using a `frame-provider` component."
  []
  (let [frame-id (react/useContext frame-context)]
    (when-not (some? frame-id)
      (console :warn
               "re-frame: There is no frame context-provider above this call to use-frame in the react hierarchy.\n"
               "You should probably only use this when a parent component was created using the re-frame.frame/with-frame component.\n"
               "app-db will be provided as a fallback."))
    (get @frame-context-registry frame-id db/app-db)))

(defn use-subscription
  "A react hook that provides a subscription using the current frame.
  See `use-frame`."
  [query]
  (let [frame (use-frame)]
    ;; It's okay to deref here. Not sure if we should though.
    (rf/subscribe frame query)))

(defn use-dispatch-fn
  "A react hook that provides a dispatch function accepting one arg, the event.
  Dispatches using the current frame. See `use-frame`."
  []
  (let [frame (use-frame)]
    (fn dispatch [event]
      (rf/dispatch frame event))))

;; -- Use the current frame without react hooks -----

(defn frame-consumer
  [component]
  (r/create-class
    {:context-type frame-context
     :reagent-render
     (fn [component]
       (let [context-id (.-context (reagent.core/current-component))
             frame (get @frame-context-registry context-id db/app-db)]
         [component frame]))}))
