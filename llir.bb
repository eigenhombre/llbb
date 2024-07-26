(def m1-target "arm64-apple-macosx14.0.0")

(defn target [t] (format "target triple = \"%s\"" t))

(defn global-const-str [var-name s]
  (format "@%s = private unnamed_addr constant [%d x i8] c\"%s\\00\""
          var-name
          (inc (count s))
          s))

(defn extern-i8* [f-name]
  (format "declare i32 @%s(i8* nocapture) nounwind"
          f-name))

(defn as-ptr [body-len var-name]
  (format "getelementptr [%d x i8],[%d x i8]* @%s, i64 0, i64 0"
          body-len body-len var-name))

(defn main-calling-puts [body]
  (format
   "define i32 @main() {
    %%as_ptr = %s

    call i32 @puts(i8* %%as_ptr)
    ret i32 0
}
" (as-ptr (inc (count body)) "xxx")))

(defn simple-main [retval]
  (format
   "define i32 @main() {
    ret i32 %d
}
" retval))

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
(name? 3)
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

