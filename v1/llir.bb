(def m1-target "arm64-apple-macosx14.0.0")

(defn target [t] (format "target triple = \"%s\"" t))

(defn extern-i8* [f-name]
  (format "declare i32 @%s(i8* nocapture) nounwind"
          f-name))

(defn symbolic? [x]
  (or (symbol? x)
      (keyword? x)))

(defn name? [x]
  (if (symbolic? x)
    (name x)
    x))

(defn gep [target-type typ nam & intpairs]
  (format "getelementptr %s, %s %s, %s"
          target-type
          typ
          nam
          (str/join ", " (map (fn [[a b]]
                                (str (name? a) " " b))
                              intpairs))))

(defn star [x] (str (name x) "*")) ;; FIXME

;; FIXME: remove in favor of `gep`:
(defn as-ptr
  "
  Crude wrapper for getelementptr, just for strings (for now).
  "
  [var-name body-len]
  (format "getelementptr [%d x i8],[%d x i8]* @%s, i64 0, i64 0"
          body-len body-len (name var-name)))

(defn els [& args]
  (str/join "\n\n" args))

(def globals  (atom #{}))

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

(comment
  (sigil :foo)
  ;;=>
  "%foo"
  (add-to-globals! :foo)
  ;;=>
  #{"foo"}
  (sigil :foo)
  ;;=>
  "@foo")

(def m1-target "arm64-apple-macosx14.0.0")

(defn target [t] (format "target triple = \"%s\"" t))

(def aligns {:i32 4
             :ptr 8})

(defn fixedarray [n typ] (format "[%d x %s]"
                                 n
                                 (name? typ)))

(defn type= [nam typ] (format "%s = type %s"
                              (sigil nam)
                              typ))

(comment
  (type= :Stack (fixedarray 1000 :i32))
  ;;=>
  "%Stack = type [1000 x i32]")

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

(defn def-global-int [nam typ val]
  (add-to-globals! nam)
  (format "%s = global %s %s"
          (sigil nam)
          (name? typ)
          val))

(defn def-global-const-str [var-name s]
  (add-to-globals! var-name)
  (format "%s = private unnamed_addr constant [%d x i8] c\"%s\\00\""
          (sigil var-name)
          (inc (count s))
          s))

(defn def-global-zeroed-var-as-ptr [nam typ]
  (add-to-globals! nam)
  (format "%s = global %%%s zeroinitializer"
          (sigil nam)
          (name? typ)))

(defn farg [typ nam] (format "%s noundef %%%s" (name typ) (name? nam)))
(defn assign [nam val]
  (format "%s = %s" (sigil nam) val))
(defn alloca [typ] (format "alloca %s, align %d" (name typ) (aligns typ)))

(defn reg-or-num [v]
  (if (keyword? v)
    (format "%%%s" (name v))
    v))

(defn store-typed [from-type from-val to-type to-val]
  (format "store %s %s, %s %s, align %d"
          (name from-type)
          from-val
          to-type
          to-val
          (aligns from-type)))

(defn store [typ val at]
  (format "store %s %s, ptr %s, align %d"
          (name typ)
          (reg-or-num val)
          (reg-or-num at)
          (aligns typ)))

(defn load-typed [typ from]
  (format "load %s, %s* %s, align %d"
          (name? typ)
          (name? typ)
          (sigil from)
          (aligns typ)))

(defn load [typ from]
  (format "load %s, ptr %s, align %d"
          (name typ)
          (reg-or-num from)
          (aligns typ)))

(defn add [typ a b]
  (format "add %s %s, %s"
          (name? typ)
          (sigil a)
          (sigil b)))

(defn sub [typ a b]
  (format "sub %s %s, %s"
          (name? typ)
          (sigil a)
          (sigil b)))

(defn mul [typ a b]
  (format "mul %s %s, %s"
          (name? typ)
          (sigil a)
          (sigil b)))

(defn ret
  ([_] "ret void") ;; unary is always void
  ([typ val]
   (format "ret %s %s" (name typ) (reg-or-num val))))

(defn br-label [lbl]
  (format "br label %s" (sigil lbl)))

(defn maybe-nocapture [s]
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
          (str/join ", " (map (comp maybe-nocapture name) arg-types))))

(defn if-not-equal [typ lhs rhs then-clause else-clause]
  (let [cond-name (gensym "cond")]
    (els
     (format "%s = icmp eq %s %s, %s"
             (sigil cond-name)
             (name? typ)
             (sigil lhs)
             (sigil rhs))
     (format "br i1 %s, label %%end, label %%body"
             (sigil cond-name))
     "body:"
     then-clause
     "end:"
     else-clause)))
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

(comment
  (spit
   "argcount.ll"
   (els
    (target m1-target)
    (def-fn :i32 "main" [(farg :i32 0)
                         (farg :ptr 1)]
      (assign 3 (alloca :i32))
      (assign 4 (alloca :i32))
      (assign 5 (alloca :ptr))
      (store :i32 0 :3)
      (store :i32 :0 :4)
      (store :ptr :1 :5)
      (assign 6 (load :i32 :4))
      (ret :i32 :6)))))

(comment
  (spit
   "argcount-smaller.ll"
   (els
    (target m1-target)
    (def-fn :i32 "main" [(farg :i32 :arg0)
                         (farg :ptr :arg1_unused)]
      (assign :retptr (alloca :i32))
      (store :i32 :arg0 :retptr)
      (assign :retval (load :i32 :retptr))
      (ret :i32 :retval)))))
