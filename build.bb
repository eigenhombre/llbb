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

(defn read-matching-file [dir pat]
  (->> dir
       fs/list-dir
       (filter (comp (partial re-find pat) str))
       first
       str
       slurp))

(defn get-md [dir]
  (read-matching-file dir #"\d\.md$"))

(defn get-cmds [dir]
  (str/split (read-matching-file dir #"\d\.cmd$")
             #"\n"))

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

(println
 (str/join
  "\n\n"
  (for [[n dir] (map-indexed vector (find-examples))]
    (format "# Example %s

%s
%s"
            (inc n)
            (get-md dir)
            (session-str dir (get-cmds dir))))))
