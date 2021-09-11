(ns decide.routes)

(def routes
  [["/decisions" {}]
   ["/decision/:slug/home"                  {:parameters {:path {:slug string?}}}]
   ["/decision/:slug/proposals"             {:parameters {:path {:slug string?}}}]
   ["/decision/:slug/proposal/:proposal-id" {:parameters {:path {:slug string?
                                                                 :proposal-id uuid?}}}]
   ["/decision/:slug/dashboard"             {:parameters {:path {:slug string?}}}]
   ["/decision/:slug/moderate"              {:parameters {:path {:slug string?}}}]

   ["/settings"]
   ["/help"]
   ["/privacy"]])