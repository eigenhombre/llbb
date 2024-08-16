#!/usr/bin/env bb

(require '[babashka.fs :as fs])

(load-file (str (fs/parent *file*) "/llir.bb"))

(defn strip-comments
  "
  Remove parts of lines beginning with backslash
  "
  [s]
  (str/replace s #"(?sm)^(.*?)\\.*?$" "$1"))

(defn tokenize
  "
  Split `s` on any kind of whitespace
  "
  [s]
  (remove empty? (str/split s #"\s+")))

(defrecord node
    [typ val] ;; A node has a type and a value
  Object
  (toString [this]
    (format "[%s %s]" (:typ this) (:val this))))

;; Allowed operations
(def opmap {"+" :add
            "-" :sub
            "/" :div
            "*" :mul
            "." :dot
            "drop" :drop})

(defn ast
  "
  Convert a list of tokens into an \"abstract syntax tree\",
  which in our Forth is just a list of type/value pairs.
  "
  [tokens]
  (for [t tokens
        :let [op (get opmap t)]]
    (cond
      ;; Integers (possibly negative)
      (re-matches #"^\-?\d+$" t)
      (node. :num (Integer. t))

      ;; Operations
      op (node. :op op)

      :else (node. :invalid t))))

(comment
  (->> "example.fs"
       slurp
       strip-comments
       tokenize
       ast
       (map str))
  ;;=>
  ("[:num 2]" "[:num 2]" "[:op :add]" ;; ...
   ))

(defn def-arithmetic-op [nam op-fn]
  (def-fn :void nam []
    (assign :sp (call :i32 :get_stack_cnt))
    (if-lt :i32 :sp 2
           (els)  ;; NOP - not enough on stack
           (els
            (assign :value2
                    (call :i32 :pop))
            (assign :value1
                    (call :i32 :pop))
            (assign :result (op-fn :i32 :value1 :value2))
            (call :void :push [:i32 :result])))
    (ret :void)))

(defn main [[path]]
  (when path
    (let [nodes (->> path
                     slurp
                     strip-comments
                     tokenize
                     ast)
          outfile (->> path
                       fs/file-name
                       fs/split-ext
                       first)
          ir
          (module
           (external-fn :i32 :printf :i8*, :...)
           (def-global-const-str :fmt_str "%d\n")
           (def-type :Stack (fixedarray 1000 :i32))
           (assign-global :numstack :i32 0)
           (assign-global :globalstack "%Stack" :zeroinitializer)

           ;; helper fns:
           (def-fn :i32 :get_stack_cnt []
             (assign :sp (load :i32 :numstack))
             (ret :i32 :sp))

           (def-fn :void :add_to_stack_cnt [[:i32 :value]]
             (assign :sp0 (call :i32 :get_stack_cnt))
             (assign :sp1 (add :i32 :sp0 :value))
             (store :i32
                    (sigil :sp1)
                    (star :i32)
                    (sigil :numstack))
             (ret :void))

           (def-fn :void :push [[:i32 :value]]
             (assign :sp (call :i32 :get_stack_cnt))
             (assign :p (gep (sigil :Stack)
                             (star (sigil :Stack))
                             (sigil :globalstack)
                             [:i32 0]
                             [:i32 (sigil :sp)]))
             (store :i32
                    (sigil :value)
                    (star (name? :i32))
                    (sigil :p))
             (call :void :add_to_stack_cnt [:i32 1])
             (ret :void))

           (def-fn :i32 :item_at [[:i32 :sp]]
             (assign :idx1 (sub :i32 :sp 1))
             (assign :p (gep (sigil :Stack)
                             (star (sigil :Stack))
                             (sigil :globalstack)
                             [:i32 0]
                             [:i32 (sigil :idx1)]))
             (assign :value (load :i32 :p))
             (ret :i32 :value))

           (def-fn :i32 :pop []
             (assign :sp (call :i32 :get_stack_cnt))
             (if-lt :i32 :sp 1
                    (ret :i32 0)
                    (els
                     (assign :value
                             (call :i32 :item_at [:i32 :sp]))
                     (call :void :add_to_stack_cnt [:i32 -1])
                     (ret :i32 :value))))

           (def-arithmetic-op :mul mul)
           (def-arithmetic-op :add add)
           (def-arithmetic-op :sub sub)
           (def-arithmetic-op :div div)

           (def-fn :void :drop []
             (call :i32 :pop)
             (ret :void))

           (def-fn :void :dot []
             (assign :sp (call :i32 :get_stack_cnt))
             (if-lt :i32 :sp 2
                    (els)  ;; NOP
                    (els
                     (assign :value
                             (call :i32 :item_at [:i32 :sp]))
                     ;; GEP
                     (assign :as_ptr
                             (gep (fixedarray 4 :i8)
                                  (star (fixedarray 4 :i8))
                                  (sigil :fmt_str)
                                  [:i64 0]
                                  [:i64 0]))
                     ;; FIXME: cheating:
                     (call "i32 (i8*, ...)"
                           :printf
                           [:i8* :as_ptr]
                           [:i32 :value])))
             (ret :void))

           (def-fn :i32 :main []
             (apply
              els
              (for [{:keys [typ val] :as n} nodes]
                (cond
                  (= typ :num)
                  (call :void :push [:i32 val])

                  (= typ :op)
                  (call :void val)

                  :t (throw (ex-info "Bad token"
                                     {:node n})))))
             (ret :i32 0)))]
      (compile-to outfile ir))))

(comment
  (main ["example.fs"])
  (sh "./example"))

(main *command-line-args*)
