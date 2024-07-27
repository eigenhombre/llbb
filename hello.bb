#!/usr/bin/env bb

(load-file "llir.bb")

(let [hello-str (str/join " " *command-line-args*)]
  (println
   (els (target m1-target)
        (external-fn :i32 :puts :i8*)
        (global-const-str :message hello-str)
        (def-global-fn :i32 "main" []
          (assign :as_ptr (as-ptr :message (inc (count hello-str))))
          (call :i32 :puts [:i8* :as_ptr])
          (ret :i32 0)))))
