(def target "arm64-apple-macosx14.0.0")

(defn target-triple [triple]
  (format "target triple = \"%s\"" triple))

(defn simple-main [retval]
  (format
   "define i32 @main() {
    ret i32 %d
}
" retval))

(defn els [& args]
  (str/join "\n" args))

(spit "five.ll" (els (target-triple target)
                     (simple-main 5)))
