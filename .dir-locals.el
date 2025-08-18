((clojure-mode
  (cider-preferred-build-tool . clojure-cli)
  (cider-clojure-cli-aliases . "dev")
  (cider-clojure-cli-parameters . "--port 3333"))

 (clojurescript-mode
  (cider-preferred-build-tool . shadow-cljs)
  (cider-default-cljs-repl . shadow)
  (cider-shadow-watched-builds . ("app"))))
