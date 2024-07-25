(defn simple-main [retval]
  (format
   "define i32 @main() {
    ret i32 %d
}
" retval))

(spit "five.ll"
      (format "%s\n%s"
              "target triple = \"arm64-apple-macosx14.0.0\""
              (simple-main 5)))
