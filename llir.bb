(require '[babashka.process :as sh])
(require '[babashka.fs :as fs])

(def m1-target "arm64-apple-macosx14.0.0")

(defn target [t] (format "target triple = \"%s\"" t))

(defn els [& args]
  (str/join "\n\n" args))

(def globals (atom #{}))
(def our-types (atom #{}))

(defn symbolic? [x]
  (or (symbol? x)
      (keyword? x)))

(defn name? [x]
  (if (symbolic? x)
    (name x)
    x))

(defn global? [x] (@globals (name? x)))
(defn our-type? [x] (@our-types (name? x)))

(defn add-to-globals! [x]
  (swap! globals conj (name? x)))

(defn add-to-our-types! [x]
  (swap! our-types conj (name? x)))

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

(defn def-type [nam typ]
  (add-to-our-types! typ)
  (format "%s = type %s"
          (sigil nam)
          typ))

(defn maybe-our-type [typ]
  (if (our-type? typ)
    (sigil typ)
    (name? typ)))

(defn assign-global [nam typ val]
  (add-to-globals! nam)
  (format "%s = global %s %s"
          (sigil nam)
          ;; can be primitive or our type:
          (maybe-our-type typ)
          ;; can be zeroinitializer:
          (name? val)))

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

(defn def-global-const-str [var-name s]
  (add-to-globals! var-name)
  (format "%s = private unnamed_addr constant [%d x i8] c\"%s\\00\""
          (sigil var-name)
          (inc (count s))
          s))

(defn maybe-variadic [s]
  (if (= s "...")
    s
    (format "%s nocapture" s)))

(defn external-fn
  "
  Define an externally-available (C) function, in the standard library
  (for now).  The code should not \"throw an exception\" in the LLVM
  sense.
  "
  [typ fn-name & arg-types]
  (format "declare %s @%s(%s) nounwind"
          (name typ)
          (name fn-name)
          (str/join ", " (map (comp maybe-variadic name) arg-types))))

(defn call
  "
  Invoke `fn-name` returning type `typ` with 0 or more type/arg pairs.
  E.g.,

  (call :i32 :negate [:i32 :x])
  ;;=>
  \"call i32 @negate(i32 %x)\"
  "
  [typ fn-name & arg-type-arg-pairs]
  (format "call %s @%s(%s)"
          (name typ)
          (name fn-name)
          (str/join ", " (for [[typ nam] arg-type-arg-pairs]
                           (format "%s %s"
                                   (name? typ)
                                   (reg-or-num nam))))))

(defn star [x] (str (name x) "*"))

(defn fixedarray [n typ] (format "[%d x %s]"
                                 n
                                 (name? typ)))

(defn gep [target-type typ nam & intpairs]
  (format "getelementptr %s, %s %s, %s"
          target-type
          typ
          nam
          (str/join ", " (map (fn [[a b]]
                                (str (name? a) " " b))
                              intpairs))))


(defn br-label [lbl]
  (format "br label %s" (sigil lbl)))

(defn icmp [comparison typ lhs rhs then-clause else-clause]
  (let [cond-name (gensym "cond")]
    (els
     (format "%s = icmp %s %s %s, %s"
             (sigil cond-name)
             (name? comparison)
             (name? typ)
             (sigil lhs)
             (sigil rhs))
     (format "br i1 %s, label %%body, label %%end"
             (sigil cond-name))
     "body:"
     then-clause
     (br-label :end)
     "end:"
     else-clause)))

(defn if-lt [typ lhs rhs then-clause else-clause]
  (icmp :slt typ lhs rhs then-clause else-clause))

(defn if-eq [typ lhs rhs then-clause else-clause]
  (icmp :eq typ lhs rhs then-clause else-clause))

(defn arithm [op typ a b]
  (format "%s %s %s, %s"
          (name? op)
          (name? typ)
          (sigil a)
          (sigil b)))

(defn add [typ a b] (arithm :add typ a b))
(defn sub [typ a b] (arithm :sub typ a b))
(defn mul [typ a b] (arithm :mul typ a b))
(defn div [typ a b] (arithm :sdiv typ a b))

(comment
  (div :i32 :a :b)
  ;;=>
  "sdiv i32 %a, %b"

  (add :i8 :x 1)
  ;;=>
  "add i8 %x, 1")

(defn module [& args]
  (apply els (list* (target m1-target) args)))

(defn sh
  "
  Use `bash` to run command(s) `s`, capturing both stdout/stderr
  as a concatenated string.  Throw an exception if the exit code
  is nonzero.
  "
  [s]
  (let [{:keys [out err]}
        (sh/shell {:out :string, :err :string}
                  (format "bash -c '%s'" s))]
    (str/join "\n" (remove empty? [out err]))))

(defn compile-to
  "
  Save IR `body` to a temporary file and compile it, writing the
  resulting binary to `progname` in the current working directory.
  "
  [progname body]
  (let [ll-file
        (str (fs/create-temp-file {:prefix "llbb-", :suffix ".ll"}))]
    (spit ll-file body)
    (sh (format "clang -O3 %s -o %s"
                ll-file
                progname))))

(comment
  ;; Non-program, smallest compilable unit:
  (spit "smallest-obj.ll" (module))
  (sh "clang -O3 -c smallest-obj.ll -o smallest.o")
  (sh "ls -al smallest.o")
  ;;=>
  "-rw-r--r--  1 jacobsen  staff  336 Aug 14 21:11 smallest.o\n"

  ;; Smallest program: just return 0:
  (compile-to "smallest-prog"
              (module
               (def-fn :i32 :main [] (ret :i32 0))))
  (sh "./smallest-prog; echo -n $?") ;;=> "0"
  (sh "ls -al smallest-prog")
  ;;=>
  "-rwxr-xr-x  1 jacobsen  staff  16848 Aug 13 21:45 smallest-prog\n"

  ;; Return a different exit code:
  (compile-to "one"
              (module
               (def-fn :i32 :main []
                 (ret :i32 3)))
              ;;=>
              "target triple = \"arm64-apple-macosx14.0.0\"\n\ndefine i32 @main() nounwind {\n  ret i32 3\n}")
  (sh "./one; echo -n $?") ;;=> "3"

  ;; Example program from blog post:
  (compile-to "new3"
              (println(module
                       (assign-global :i :i32 3)
                       (def-fn :i32 :main []
                         (assign :retval (load :i32 :i))
                         (ret :i32 :retval)))))
  (sh "./new3; echo $?")

  ;; Argument counting: return number of arguments as an exit code:
  (compile-to "argcount"
              (module
               (def-fn :i32 :main [[:i32 :arg0]
                                   [:ptr :arg1_unused]]
                 (assign :retptr (alloca :i32))
                 (store :i32 :arg0 :ptr :retptr)
                 (assign :retval (load :i32 :retptr))
                 (ret :i32 :retval))))
  (sh "./argcount; echo -n $?") ;;=> "1"
  (sh "./argcount 1 2 3; echo -n $?") ;;=> "4"

  ;; Hello, World example:
  (let [msg "Hello, World."
        n (inc (count msg))] ;; Includes string terminator
    (compile-to "hello"
                (module
                 (external-fn :i32 :puts :i8*)
                 (def-global-const-str :message msg)
                 (def-fn :i32 :main []
                   (assign :as_ptr
                           (gep (fixedarray n :i8)
                                (star (fixedarray n :i8))
                                (sigil :message)
                                [:i64 0]
                                [:i64 0]))
                   (call :i32 :puts [:i8* :as_ptr])
                   (ret :i32 0)))))

  (sh "./hello") ;;=> "Hello, World.\n"
  (sh "wc -c hello") ;;=> "   33432 hello\n"
  (sh "ls -l hello")
  ;;=>
  "-rwxr-xr-x  1 jacobsen  staff  33432 Aug 14 21:09 hello\n"
  )
