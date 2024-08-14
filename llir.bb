(require '[babashka.process :as sh])

(def m1-target "arm64-apple-macosx14.0.0")

(defn target [t] (format "target triple = \"%s\"" t))

(defn els [& args]
  (str/join "\n\n" args))

(def globals (atom #{}))

(defn symbolic? [x]
  (or (symbol? x)
      (keyword? x)))

(defn name? [x]
  (if (symbolic? x)
    (name x)
    x))

(defn global? [x] (@globals (name? x)))

(defn add-to-globals! [x]
  (swap! globals conj (name? x)))

(defn sigil
  "
  Prefix `x` with @ or % depending on whether it's global or local.
  "
  [x]
  (if (symbolic? x)
    (format "%s%s"
            (if (global? x) "@" "%")
            (name? x))
    x))

(defn def-fn [ret-type fn-name argpairs & body]
  (add-to-globals! fn-name)
  (let [args (for [[t n] argpairs]
               (format "%s %s" (name? t) (sigil n)))
        argstr (str/join ", " args)
        body (str/join "\n" (map (partial str "  ") body))]
    (format "define %s %s(%s) nounwind {\n%s\n}"
            (name? ret-type)
            (sigil fn-name)
            argstr
            body)))

(defn reg-or-num [v]
  (if (keyword? v)
    (format "%%%s" (name v))
    v))

(defn ret
  ([_] "ret void") ;; unary is always void
  ([typ val]
   (format "ret %s %s" (name typ) (reg-or-num val))))

(defn assign [nam val]
  (format "%s = %s" (sigil nam) val))

(def aligns {:i32 4
             :ptr 8})

(defn alloca [typ]
  (format "alloca %s, align %d"
          (name typ)
          (aligns typ)))

(defn store [from-type from-val to-type to-val]
  (format "store %s %s, %s %s, align %d"
          (name from-type)
          (sigil from-val)
          (name? to-type)
          (sigil to-val)
          (aligns from-type)))

(defn load [typ from]
  (format "load %s, %s* %s, align %d"
          (name? typ)
          (name? typ)
          (sigil from)
          (aligns typ)))

(defn module [& args]
  (apply els (list* (target m1-target) args)))

(comment
  (defn sh [s]
    (let [{:keys [out err]}
          (sh/shell {:continue true, :out :string, :err :string}
                    s)]
      (str/join "\n" (remove empty? [out err]))))
  (spit "smallest-obj.ll" (module))
  (sh "clang -O3 -c smallest-obj.ll -o smallest.o")
  (sh "ls -al smallest.o")

  (spit "smallest-prog.ll"
        (module
         (def-fn :i32 :main [] (ret :i32 0))))
  (sh "clang -O3 smallest-prog.ll -o smallest-prog")
  (sh "pwd")
  (sh "bash -c './smallest-prog; echo -n $?'") ;;=> "0"
  (sh "ls -al smallest-prog")
  ;;=>
  "-rwxr-xr-x  1 jacobsen  staff  16848 Aug 13 21:45 smallest-prog\n"

  (spit "one.ll"
        (module
         (def-fn :i32 :main [] (ret :i32 1))))
  (sh "clang -O3 one.ll -o one")
  (sh "bash -c './one; echo -n $?'") ;;=> "1"

  ;; Argument counting
  (spit "argcount.ll"
        (module
         (def-fn :i32 :main [[:i32 :arg0]
                             [:ptr :arg1_unused]]
           (assign :retptr (alloca :i32))
           (store :i32 :arg0 :ptr :retptr)
           (assign :retval (load :i32 :retptr))
           (ret :i32 :retval))))
  (sh "clang -O3 argcount.ll -o argcount")
  (sh "bash -c './argcount; echo -n $?'") ;;=> "1"
  (sh "bash -c './argcount 1 2 3; echo -n $?'") ;;=> "4"

  )
