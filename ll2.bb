#!/usr/bin/env bb

(defn basename [file]
  (str/join "/" (-> file
                    (str/split #"/")
                    butlast)))

(defn this-basename [] (basename *file*))

(defn load [f] (load-file (str (this-basename) "/" f)))

(load "llir.bb")

(defn argint-main [num-str]
  (str/join "\n"
            [(target m1-target)
             (extern-i8* "atoi")
             #_(global-const-str "xxx" body)
             #_(main-calling-puts body)]))

(argint-main "3")
