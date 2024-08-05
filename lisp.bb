(require '[clojure.edn :as edn])

(defn to-ssa [expr bindings]
  (if (not (coll? expr))
    expr
    (let [[op & args] expr
          result (gensym "r")
          args (doall
                (for [arg args]
                  (if-not (coll? arg)
                    arg
                    (to-ssa arg bindings))))]
      (swap! bindings conj (concat [result op] args))
      result)))

(defn convert-to-ssa [expr]
  (let [bindings (atom [])]
    (to-ssa expr bindings)
    @bindings))

(comment
  (->> "example.lisp"
       slurp
       edn/read-string
       convert-to-ssa)
  ;;=>
  [(r20892 * 66 3)
   (r20891 * 77 r20892)
   (r20890 print r20891)])
