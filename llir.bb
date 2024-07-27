(def m1-target "arm64-apple-macosx14.0.0")

(defn target [t] (format "target triple = \"%s\"" t))

(defn extern-i8* [f-name]
  (format "declare i32 @%s(i8* nocapture) nounwind"
          f-name))

(defn as-ptr
  "
  Crude wrapper for getelementptr, just for strings (for now).
  "
  [var-name body-len]
  (format "getelementptr [%d x i8],[%d x i8]* @%s, i64 0, i64 0"
          body-len body-len (name var-name)))

(defn els [& args]
  (str/join "\n" args))

(defn def-global-fn [ret-type fn-name args & body]
  (format "define %s @%s(%s) nounwind {
%s
}"
          (name ret-type)
          fn-name
          (str/join ", " args)
          (str/join "\n" (map (partial str "  ") body))))

(def m1-target "arm64-apple-macosx14.0.0")
(defn target [t] (format "target triple = \"%s\"" t))

(def aligns {:i32 4
             :ptr 8})

(defn name? [x]
  (if (or (symbol? x) (keyword? x))
    (name x)
    x))

(defn global-const-str [var-name s]
  (format "@%s = private unnamed_addr constant [%d x i8] c\"%s\\00\""
          (name? var-name)
          (inc (count s))
          s))

(defn farg [typ nam] (format "%s noundef %%%s" (name typ) (name? nam)))
(defn assign [nam val] (format "%%%s = %s" (name? nam) val))
(defn alloca [typ] (format "alloca %s, align %d" (name typ) (aligns typ)))

(defn reg-or-num [v]
  (if (keyword? v)
    (format "%%%s" (name v))
    v))

(defn store [typ val at]
  (format "store %s %s, ptr %s, align %d"
          (name typ)
          (reg-or-num val)
          (reg-or-num at)
          (aligns typ)))

(defn load [typ from]
  (format "load %s, ptr %s, align %d"
          (name typ)
          (reg-or-num from)
          (aligns typ)))

(defn ret [typ val]
  (format "ret %s %s" (name typ) (reg-or-num val)))

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
          (str/join ", " (map (comp #(str % " nocapture") name) arg-types))))

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
                           (str (name typ) " %" (name? nam))))))

(comment
  (spit
   "argcount.ll"
   (els
    (target m1-target)
    (def-global-fn :i32 "main" [(farg :i32 0)
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
    (def-global-fn :i32 "main" [(farg :i32 :arg0)
                                (farg :ptr :arg1_unused)]
      (assign :retptr (alloca :i32))
      (store :i32 :arg0 :retptr)
      (assign :retval (load :i32 :retptr))
      (ret :i32 :retval)))))
