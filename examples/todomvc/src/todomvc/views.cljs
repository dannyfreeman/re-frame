(ns todomvc.views
  (:require [reagent.core  :as reagent]
            [re-frame.core :refer [subscribe dispatch]]
            [re-frame.frame :as f]
            [clojure.string :as str]))


(defn todo-input [{:keys [title on-save on-stop]}]
  (let [val  (reagent/atom title)
        stop #(do (reset! val "")
                  (when on-stop (on-stop)))
        save #(let [v (-> @val str str/trim)]
                (on-save v)
                (stop))]
    (fn [props]
      [:input (merge (dissoc props :on-save :on-stop :title)
                     {:type        "text"
                      :value       @val
                      :auto-focus  true
                      :on-blur     save
                      :on-change   #(reset! val (-> % .-target .-value))
                      :on-key-down #(case (.-which %)
                                      13 (save)
                                      27 (stop)
                                      nil)})])))


(defn todo-item
  []
  (let [editing (reagent/atom false)]
    (fn [{:keys [id done title]}]
      (let [frame (f/use-frame)]
        [:li {:class (str (when done "completed ")
                          (when @editing "editing"))}
          [:div.view
            [:input.toggle
              {:type "checkbox"
               :checked done
               :on-change #(dispatch frame [:toggle-done id])}]
            [:label
              {:on-double-click #(reset! editing true)}
              title]
            [:button.destroy
              {:on-click #(dispatch frame [:delete-todo id])}]]
          (when @editing
            [todo-input
              {:class "edit"
               :title title
               :on-save #(if (seq %)
                            (dispatch frame [:save id %])
                            (dispatch frame [:delete-todo id]))
               :on-stop #(reset! editing false)}])]))))


(defn task-list
  []
  (let [frame (f/use-frame)
        visible-todos @(subscribe frame [:visible-todos])
        all-complete? @(subscribe frame [:all-complete?])]
    [:section.main
      [:input.toggle-all
        {:type "checkbox"
         :checked all-complete?
         :on-change #(dispatch frame [:complete-all-toggle])}]
      [:label
        {:for "toggle-all"}
        "Mark all as complete"]
      [:ul.todo-list
        (for [todo  visible-todos]
          ^{:key (:id todo)} [todo-item todo])]]))


(defn footer-controls
  []
  (let [frame (f/use-frame) ;; no need to pass frame, grab with hook
        [active done] @(f/use-subscription [:footer-counts])
        showing       @(subscribe [:showing]) ;; this subscribes to app-db
        a-fn          (fn [filter-kw txt]
                        [:a {:class (when (= filter-kw showing) "selected")
                             :title "This will change for all todo lists."
                             :href (str "#/" (name filter-kw))} txt])]
    [:footer.footer
     [:span.todo-count
      [:strong active] " " (case active 1 "item" "items") " left"]
     [:ul.filters
      [:li (a-fn :all    "All")]
      [:li (a-fn :active "Active")]
      [:li (a-fn :done   "Completed")]]
     (when (pos? done)
       [:button.clear-completed {:on-click #(dispatch frame [:clear-completed])}
        "Clear completed"])]))


(defn task-entry
  [frame] ;; Frame can be passed around if functional components aren't an option.
  [:header.header
   [todo-input
     {:class "new-todo"
      :placeholder "What needs to be done?"
      :on-save #(when (seq %)
                   (dispatch frame [:add-todo %]))}]])


(defn todo-app
  [frame msg]
  [:<>
   [:section.todoapp
    [task-entry frame] ;; pass the frame, just to demonstrate
    (when (seq @(subscribe frame [:todos]))
      [task-list])
    [footer-controls]]
   (if msg [:p msg] [:br])
   [:button.clicker {:on-click #(dispatch frame [:click])}
     (str "Click -> " @(subscribe frame [:click]))]
   [:footer.info
    [:p "Double-click to edit a todo"]]])

;; Every component under this can work with an optional frame.
(defn todo-app-with-frame
  ([]
   [f/frame-provider todo-app])
  ([app-id frame]
   (dispatch frame [:initialise-db app-id])
   (fn [app-id frame]
     [f/frame-provider
      {:context-id app-id :frame frame}
      todo-app
      "I'm not using app-db"])))

(defn app-db-still-works
  []
  [:div "Global todo items: "
   @(subscribe [:showing]) ;; this value really only exists in app-db
   (for [{:keys [id] :as t} @(subscribe [:visible-todos])] ;; this one exists in all dbs
     ^{:key id} [:p (str t)])])

;; This will stick around between reloads
;; If you just had one todo-app, they might as well just use this reference.
(defonce permanent-frame
  (reagent/atom {:show-title? true}))

(defn todo-apps
  []
  (let [todo-2-frame (reagent/atom {})]
    (fn []
      [:<>
       [:h1.todo-header "todos"]
       [todo-app-with-frame :todo-1 permanent-frame]
       ;; This will get reset on reloads, because todo-apps is re-rendered.
       [todo-app-with-frame :todo-2 todo-2-frame]
       [todo-app-with-frame] ;; these will operate on app-db
       [app-db-still-works]])))
