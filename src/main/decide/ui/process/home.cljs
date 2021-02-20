(ns decide.ui.process.home
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [material-ui.data-display :as dd]
    [material-ui.data-display.list :as list]
    [material-ui.inputs :as inputs]
    [material-ui.layout :as layout]
    [material-ui.layout.grid :as grid]
    [material-ui.surfaces :as surfaces]
    [goog.object :as gobj]
    [taoensso.timbre :as log]))

(defsc TopEntry [_this {::proposal/keys [id title pro-votes my-opinion]
                        :keys [root/current-session] :as props}]
  {:query [::proposal/id ::proposal/title ::proposal/pro-votes ::proposal/my-opinion
           [:root/current-session '_]]
   :ident ::proposal/id}
  (list/item
    {:button true
     :component :a
     :href (str "proposal/" id)}
    (list/item-text {:primary title
                     :secondary (i18n/trf "Approvals: {pros}" {:pros pro-votes})} title)
    (when (get current-session :session/valid?)
      (list/item-secondary-action {}
        (if (pos? my-opinion)
          (layout/box {:color "success.main"} (dd/typography {:color :inherit} (i18n/tr "Approved")))
          (inputs/button {:color :primary}

            (i18n/tr "Approve")))))))

(def ui-top-entry (comp/factory TopEntry {:keyfn ::proposal/id}))

(defn section-paper [props & children]
  (layout/box (merge {:clone true :p 2} props)
    (apply surfaces/paper {:variant :outlined} children)))

(defn top-proposals [proposals]
  (first (partition-by ::proposal/pro-votes (sort-by ::proposal/pro-votes > proposals))))

(defn sort-by-votes [proposals]
  (sort-by ::proposal/pro-votes > proposals))

(declare ui-experimental-ballot-entry)

(defsc BallotEntry [_ {::proposal/keys [title pro-votes parents children my-opinion]}]
  {:query [::proposal/id
           ::proposal/title
           ::proposal/pro-votes
           {::proposal/parents 1}
           {::proposal/children 1}
           ::proposal/my-opinion]
   :ident ::proposal/id}
  (list/item {}
    (list/item-icon {}
      (inputs/checkbox
        {:edge :start
         :checked (pos? my-opinion)}))

    (list/item-text {:secondary (i18n/trf "Approvals: {pros}" {:pros pro-votes})}
      (if-not (and (empty? parents) (empty? children))

        ;; expandable content
        (layout/box {:component :details}
          (dom/summary {} title)
          (layout/box {:borderLeft 1}
            (when-not (empty? children)
              (list/list
                {:subheader (list/subheader {} (i18n/tr "Children"))
                 :dense true}
                (map ui-experimental-ballot-entry (sort-by-votes children))))
            (when-not (empty? parents)
              (list/list
                {:subheader (list/subheader {} (i18n/tr "Parents"))
                 :dense true}
                (map ui-experimental-ballot-entry (sort-by-votes parents))))))

        title))))

(def ui-experimental-ballot-entry (comp/computed-factory BallotEntry {:keyfn ::proposal/id}))

(defsc Ballot [_ {::process/keys [proposals]}]
  {:query [::process/slug
           {::process/proposals (comp/get-query BallotEntry)}]
   :ident ::process/slug}
  (section-paper {:pb 0 :borderColor "warning.main"}        ; TODO remove warning color
    (dd/typography {:component :h2 :variant "h5"} (i18n/tr "Ballot")
      (let [sorted-proposals (sort-by-votes proposals)]
        (list/list {}
          (map ui-experimental-ballot-entry sorted-proposals))))))

(def ui-experimental-ballot (comp/computed-factory Ballot))

(defn render-nodes! [^js/object dom-node nodes]
  (-> dom-node
    (.append "g")
    (.selectAll ".node")
    (.data nodes)
    (.enter)
    (.append "rect")
    (.classed "node", true)
    (.attr "x" (fn [^js/object node] (. node -x0)))
    (.attr "y" (fn [^js/object node] (. node -y0)))
    (.attr "width" (fn [^js/object d] (- (.-x1 d) (.-x0 d))))
    (.attr "height" (fn [^js/object d] (- (.-y1 d) (.-y0 d))))
    (.attr "fill" "blue")))

(defn render-links! [^js/object dom-node links]
  (-> dom-node
    (.append "g")
    (.selectAll ".node")
    (.data links)
    (.enter)
    (.append "path")
    (.classed "link", true)
    (.attr "d" (js/d3.sankeyLinkHorizontal))
    (.attr "fill" "none")
    (.attr "stroke" "#606060")
    (.attr "stroke-width" (fn [^js/object d] (.-width d)))
    (.attr "stroke-opacity" 0.5)))

(def generate-sankey
  (doto (js/d3.sankey)
    (.size #js [400 200])
    (.nodeId (fn [node] (.-id node)))
    (.nodeWidth 20)
    (.nodePadding 10)
    (.nodeAlign js/d3.sankeyCenter)))

(defn render-sankey! [dom-node {:keys [nodes links] :as props}]
  (log/info 'render-sankey!)
  (let [svg (.select js/d3 "#my-diagram")
        ^js/object graph
        (generate-sankey
          (clj->js
            (-> props
              (update :nodes
                (fn [nodes]
                  (map
                    (fn [node]
                      {:id (str (::proposal/id node))
                       :fixedValue (::proposal/pro-votes node)})
                    nodes)))

              (update :links
                (fn [links]
                  (map (fn [{:keys [from to weight]}]
                         {:source (str (::proposal/id from))
                          :target (str (::proposal/id to))
                          :value weight})
                    links))))))]
    (log/spy :info graph)
    (.remove (.selectAll svg "*"))
    (render-nodes! svg (.-nodes graph))
    (render-links! svg (.-links graph))))

(defsc SankeyDiagram [this props]
  {:componentDidMount
   (fn [this]
     (when-let [dom-node (gobj/get this "svg")]
       (render-sankey! dom-node (comp/props this))))
   :shouldComponentUpdate
   (fn [this next-props next-state]
     (when-let [dom-node (gobj/get this "svg")]
       (render-sankey! dom-node next-props))
     false)}
  (dom/svg {:id "my-diagram"
            :style {:backgroundColor "rgb(240,240,240)"}
            :width "100%" :height 200
            :viewBox "0 0 400 200"
            :ref (fn [r] (gobj/set this "svg" r))}))

(def ui-sankey (comp/computed-factory SankeyDiagram))

(defsc Proposal [_ _]
  {:query [::proposal/id ::proposal/title ::proposal/pro-votes]
   :ident ::proposal/id})

(defsc ProcessHome [_this {::process/keys [description proposals]
                           :keys [>/experimental-ballots sankey]}]
  {:query [::process/slug ::process/description
           {::process/proposals (comp/get-query TopEntry)}
           {:sankey [{:nodes (comp/get-query Proposal)} :links]}
           {:>/experimental-ballots (comp/get-query Ballot)}]
   :ident ::process/slug}
  (layout/box {:clone true :pt 2}
    (layout/container {:maxWidth :lg :component :main}

      (section-paper {}
        (when sankey
          (ui-sankey sankey)))

      ;; description section
      (grid/container {:spacing 2}
        (grid/item {:xs 12}
          (section-paper {}
            (dd/typography {:component :h2 :variant "h4" :paragraph true}
              (i18n/trc "Description of a process" "Description"))
            (dd/typography {:variant "body1"}
              description)))


        (let [top-proposals (top-proposals proposals)]
          (when-not (zero? (count top-proposals))
            (grid/item {:xs 12}
              (section-paper {:pb 0}
                (dd/typography {:component :h2 :variant "h5"}
                  (i18n/trf "{numProposals, plural,
                    =0 {There are no proposals!}
                    =1 {The current best proposal:}
                    other {The current best proposals:}}"
                    {:numProposals (count top-proposals)}))
                (list/list {}
                  (map ui-top-entry top-proposals))))))

        (grid/item {:xs 12}
          (ui-experimental-ballot experimental-ballots))))))

(def ui-process-home (comp/computed-factory ProcessHome))

(defmutation init-overview-screen [{:keys [slug]}]
  (action [{:keys [app ref]}]
    (let [process-ident [::process/slug slug]]

      (df/load! app process-ident ProcessHome
        {:target (conj ref :process-home)
         :post-mutation `dr/target-ready
         :post-mutation-params {:target ref}}))))

(defsc ProcessOverviewScreen [_ {:keys [process-home] :as props}]
  {:query [{:process-home (comp/get-query ProcessHome)}]
   :ident (fn [] [:SCREEN ::ProcessOverviewScreen])
   :route-segment ["home"]
   :will-enter
   (fn will-enter-ProcessOverviewScreen [app {::process/keys [slug]}]
     (let [ident (comp/get-ident ProcessOverviewScreen {:process-home {::process/slug slug}})]
       (dr/route-deferred ident
         #(comp/transact! app [(init-overview-screen {:slug slug})] {:ref ident}))))}
  (ui-process-home process-home))
