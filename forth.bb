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
  (str/split s #"\s+"))

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
       (remove empty?)
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
