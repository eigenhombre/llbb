#!/usr/bin/env bb

(require '[babashka.process :as ps])

(defn cmd [s]
  ((juxt :exit :out :err)
   (ps/shell {:continue true
              :out :string
              :err :string}
             s)))

(def m1-target "arm64-apple-macosx14.0.0")

(defn target [t] (format "target triple = \"%s\"" t))

(defn global-const-str [var-name s]
  (format "@%s = private unnamed_addr constant [%d x i8] c\"%s\""
          var-name(count s) s))

(defn extern-i8* [f-name]
  (format "declare i32 @%s(i8* nocapture) nounwind"
          f-name))

(defn as-ptr [body-len var-name]
  (format "getelementptr [%d x i8],[%d x i8]* @%s, i64 0, i64 0"
          body-len body-len var-name))

(defn main-calling-puts [body]
  (format
   "define i32 @main() {
    ; Convert [n x i8]* to i8*:
    %%as_ptr = %s

    call i32 @puts(i8* %%as_ptr)
    ret i32 0
}
" (as-ptr (count body) "xxx")))

(defn hello-main [body]
  (println
   (str/join "\n"
             [(target m1-target)
              (extern-i8* "puts")
              (global-const-str "xxx" body)
              (main-calling-puts body)])))

(comment
  (spit "hello.ll" (hello-main "Hello, world!"))
  (let [[exit out err :as clang-out]
        (cmd "clang -O3 hello.ll -o hello")]
    (if-not (zero? exit)
      clang-out
      (cmd "./hello"))))

(hello-main (str/join " " *command-line-args*))
