#!/usr/bin/env bb

(load-file "llir.bb")

(defn hello-main [body]
  (els (target m1-target)
       (extern-i8* "puts")
       (global-const-str "xxx" body)
       (main-calling-puts body)))

(let [hello-str (str/join " " *command-line-args*)]
  (println (hello-main hello-str)))
