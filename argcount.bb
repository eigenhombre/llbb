#!/usr/bin/env bb

(load-file "llir.bb")

(defn argcount-llir []
  (target m1-target))

(println (argcount-llir))
