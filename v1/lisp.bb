#!/usr/bin/env bb

(require '[clojure.edn :as edn])

(load-file "llir.bb")

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

(def ops
  {'* #(mul :i32 %1 %2)
   'print
   #(call "i32 (i8*, ...)"
          :printf
          [:i8* :as_ptr]
          [:i32 (sigil %1)])})

(let [[filename] *command-line-args*
      outfile (str (fs/strip-ext filename) ".ll")
      format-str "%d\n"
      assignments (->> filename
                       slurp
                       edn/read-string
                       convert-to-ssa)]
  (spit outfile
        (els
         (target m1-target)
         (external-fn :i32 :printf :i8*, :...)
         (def-global-const-str :fmt_str format-str)
         (def-fn :i32 :main []
           (assign :as_ptr
                   (gep (fixedarray 4 :i8)
                        (star (fixedarray 4 :i8))
                        (sigil :fmt_str)
                        [:i64 0]
                        [:i64 0]))
           (apply els
                  (for [[reg op & args] assignments
                        :let [op-fn (ops op)]]
                    (if-not op-fn
                      (throw (ex-info "bad operator" {:op op}))
                      (assign reg (apply op-fn args)))))
           (ret :i32 0)))))
