#!/usr/bin/env bb

(require '[clojure.edn :as edn])
(require '[babashka.fs :as fs])

(load-file (str (fs/parent *file*) "/llir.bb"))

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

(def ops
  {'* #(mul :i32 %1 %2)
   '+ #(add :i32 %1 %2)
   '/ #(div :i32 %1 %2)
   '- #(sub :i32 %1 %2)
   'print
   #(call "i32 (i8*, ...)"
          :printf
          [:i8* :as_ptr]
          [:i32 (sigil %1)])})

(defn main [[path]]
  (when path
    (let [assignments (->> path
                           slurp
                           edn/read-string
                           convert-to-ssa)
          outfile (->> path
                       fs/file-name
                       fs/split-ext
                       first)
          ir (module
              (external-fn :i32 :printf :i8*, :...)
              (def-global-const-str :fmt_str "%d\n")
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
                (ret :i32 0)))]
      (compile-to outfile ir))))

(comment
  (main ["example.lisp"])
  (sh "./example"))

(main *command-line-args*)
