(require '[babashka.process :as ps])

(defn cmd [s]
  ((juxt :exit :out :err)
   (ps/shell {:continue true
              :out :string
              :err :string}
             s)))
