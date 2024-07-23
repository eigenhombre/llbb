#!/usr/bin/env bb

(require '[babashka.fs :as fs])
(require '[babashka.process :as ps])

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
    (str/join
     "\n"
     (str/split both #"\n"))))

(defn basename [file]
  (str/join "/" (-> file
                    (str/split #"/")
                    butlast)))

(defn this-basename [] (basename *file*))

(defn find-examples []
  (->> (this-basename)
       fs/list-dir
       (sort-by str)
       (filter (comp (partial re-find #"ex\d\d") str))))

(defn find-matching-files [dir pat]
  (->> dir
       fs/list-dir
       (filter (comp (partial re-find pat) str))))

(defn get-markdown-and-command-files [dir]
  (find-matching-files dir #"\d\.(?:c?)md$"))

(defn session-str [dir cmds]
  (str/join "\n"
            (for [cmd cmds
                  :let [result (cmd-result-str dir cmd)]]
              (format "    $ %s%s"
                      cmd
                      (if (seq result)
                        (str "\n"
                             (str/join
                              "\n"
                              (map (partial str "    ")
                                   (str/split result #"\n"))))
                        "")))))


(defn is-md? [f]
  (= (fs/extension f) "md"))

(defn is-cmd? [f]
  (= (fs/extension f) "cmd"))

(defn generate-example [dir ex-files]
  (str/join "\n\n"
            (for [f ex-files
                  :let [contents (slurp (str f))]]
              (cond
                (is-md? f) contents
                (is-cmd? f) (session-str dir (str/split
                                              contents
                                              #"\n"))
                :t (str "UNKNOWN FILE TYPE " f)))))

(defn generate-readme []
  (println
   (str/join
    "\n\n"
    (for [[n dir] (map-indexed vector (find-examples))]
      (format "# Example %s

%s"
              (inc n)
              (generate-example dir
                                (get-markdown-and-command-files dir)))))))

(generate-readme)
