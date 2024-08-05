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
  [(%x20548 * 66 3)
   (%x20547 * 77 %x20548)
   (%x20546 print %x20547)]

  (convert-to-ssa
   '(print (* 1 (+ 3 (/ 3 4)))))
  ;;=>
  [[%x18556 / 3 4]
   [%x18557 + 3 %x18556]
   [%x18558 * 1 %x18557]
   [%x18559 print %x18558]])
