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

