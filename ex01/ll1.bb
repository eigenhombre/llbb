#!/usr/bin/env bb

(load-file "../llir.bb")

(defn hello-main [body]
  (str/join "\n"
            [(target m1-target)
             (extern-i8* "puts")
             (global-const-str "xxx" body)
             (main-calling-puts body)]))

(print
 (hello-main (str/join " " *command-line-args*)))


