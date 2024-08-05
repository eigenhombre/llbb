(require '[clojure.edn :as edn])

(defn to-ssa [expr bindings]
  (cond
    (not (coll? expr)) expr

    :else
    (let [[op & args] expr
          result (gensym "%x")
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
  [(%x20892 * 66 3)
   (%x20891 * 77 %x20892)
   (%x20890 print %x20891)]
  )
