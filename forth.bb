(def example "

2 2 +  \\ 4
5 *    \\ 20
2 /    \\ 10
-1 +   \\ add -1
.      \\ prints 9


")

(defn strip-comments
  "
  Remove parts of lines beginning with backslash
  "
  [s]
  (str/replace s #"(?sm)^(.+?)\\.*?$" "$1"))

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
(def opmap {"+" :plus
            "-" :minus
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

      :else (node. :invalid :invalid))))

(comment
  (->> example
       strip-comments
       tokenize
       ast
       (map str))
  ;;=>
  '("[:num 2]"
    "[:num 2]"
    "[:op :plus]"
    "[:num 5]"
    "[:op :mul]"
    "[:num 2]"
    "[:op :div]"
    "[:num -1]"
    "[:op :plus]"
    "[:op :dot]"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; IR Generation Example

(def smaller-example "

66
.   \\ prints \"66\"
77
.   \\ prints \"77\"
*   \\ 66 * 77 = 5082
.   \\ prints \"5082\"

")

(load-file "llir.bb")

(println
 (let [nodes
       (->> smaller-example
            strip-comments
            tokenize
            ast)
       format-str "%d\n"]
   (els (target m1-target)
        (external-fn :i32 :printf :i8*, :...)
        (def-global-const-str :fmt_str format-str)
        (type= :Stack (fixedarray 1000 :i32))
        (def-global-int :numstack :i32 0)
        (def-global-zeroed-var-as-ptr :globalstack :Stack)
        (def-fn :i32 :get_stack_cnt []
          (assign :sp (load-typed :i32 :numstack))
          (ret :i32 :sp))
        (def-fn :void :add_to_stack_cnt [[:i32 :value]]
          (assign :sp0 (call :i32 :get_stack_cnt))
          (assign :sp1 (add :i32 :sp0 :value))
          (store-typed :i32
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
          (store-typed :i32
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
          (assign :value (load-typed :i32 :p))
          (ret :i32 :value))
        (def-fn :i32 :pop []
          (assign :sp (call :i32 :get_stack_cnt))
          (if-not-equal
              :i32 :sp 0
              (els
               (assign :value
                       (call :i32 :item_at [:i32 :sp]))
               (call :void :add_to_stack_cnt [:i32 -1])
               (ret :i32 :value))
              (ret :i32 0)))
        (def-fn :void :mul []
          (assign :sp (call :i32 :get_stack_cnt))
          (if-not-equal
              :i32 :sp 0
              (els
               (assign :value1
                       (call :i32 :pop))
               (assign :value2
                       (call :i32 :pop))
               (assign :result (mul :i32 :value1 :value2))
               (call :void :push [:i32 :result])
               (br-label :end))
              (els))
          (ret :void))
        (def-fn :void :dot []
          (assign :sp (call :i32 :get_stack_cnt))
          (if-not-equal
              :i32 :sp 0
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
                     [:i32 :value])
               (br-label :end))
              (els))
          (ret :void))
        (def-fn :i32 :main []
          (apply
           els
           (for [{:keys [typ val]} nodes]
             (if (= typ :num)
               (call :void :push [:i32 val])
               (call :void val))))
          (ret :i32 0)))))
