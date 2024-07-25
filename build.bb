#!/usr/bin/env bb

(require '[babashka.process :as ps])

(defn basename [file]
  (str/join "/" (-> file
                    (str/split #"/")
                    butlast)))

(defn this-basename [] (basename *file*))

(defn cmd-result-str [dir cmd]
  (let [{:keys [out err]}
        (ps/shell {:continue true
                   :out :string
                   :err :string
                   :dir dir}
                  (format "bash -c '%s'" cmd))
        both (if (seq err)
               (format "%s%s" out err)
               out)]
    (if (seq both)
      (str/join
       (for [l (str/split both #"\n")]
         (format "    %s\n" l)))
      "")))

(defn expand-command [[_ cmd rest]]
  (format "    $ %s\n%s"
          cmd
          (cmd-result-str
           (this-basename)
           cmd)))

(defn expand-shell-commands [s]
  (clojure.string/replace
   s
   #"(?sm)^ {4}\$ (.+?)$$((?:(?!^ {4}\$|^$).*?\n)*)"
   #_"    \\$ $1\n    NEWERER RESULT\n"
   expand-command))

(comment
  (println
   (expand-shell-commands "

This is some stuff

    $ echo Hello World
    Hello
    $ no replacement
    $ echo OK
    OK
    $ more stuff

more stuff


")))

(defn replace-examples [md-path]
  (->> md-path
       slurp
       expand-shell-commands
       (spit md-path)))

(replace-examples "README.md")
(println "OK")
