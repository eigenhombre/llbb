#!/usr/bin/env bb

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
       (edn/read-string)
       convert-to-ssa)
  ;;=>
  [(r623 + 2 2)
   (r622 * 5 r623)
   (r621 / r622 2)
   (r620 + -1 r621)
   (r619 - r620 8)
   (r618 print r619)])

