(defproject decide "1.0.0-SNAPSHOT"

  :plugins [[lein-tools-deps "0.4.5"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files    [:install :user :project]
                           :resolve-aliases [:dev]}

  :main decide.server-main
  :clean-targets ^{:protect false} [:target-path
                                    "resources/public/js/"
                                    "resources/public/workspaces/js/"
                                    "resources/public/css/main.css.map"]

  :aliases {"cljs-release" ["run" "-m" "shadow.cljs.devtools.cli" "release" "main"]}
  :profiles {:uberjar {:main           decide.server-main
                       :aot            [decide.server-main]
                       :uberjar-name   "decide.jar"

                       :jar-exclusions [#"public/js/test" #"public/js/workspaces" #"public/workspaces.html"]
                       :prep-tasks     ["clean" ["clean"]
                                        "compile" ["cljs-release"]]}})