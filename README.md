# Building Compact Programs In Your Own Programming Language

Some questions I have been pondering lately:
- How does one create small, self-contained programs in a new programming language, that start and run quickly?
- Can one write a compiler with the help of [Babashka](https://babashka.org/)?

I recently got interested in answering these questions using
[LLVM](https://llvm.org/), a powerful compiler toolchain that uses a
sort of abstract assembly language as its ["intermediate
representation"](https://en.wikipedia.org/wiki/Intermediate_representation) (IR).

LLVM IR is easily generated using pretty much any programming
language. Babashka provides the power of Clojure, fast start up speed,
the REPL, etc., making it a fun way to experiment with LLVM and toy
languages.

# First Steps

Let's start by looking at the smallest possible C programs.  For example,

    $ cat min.c
    int x = 3;
    $ cc -c min.c
    $ wc -c min.o
         464 min.o

This just provides a single value which could be used by some other function,
brought together at some future time using the linker.

Many people know how to get the C compiler to generate assembler
instead of object code.  But using `clang`, one can also create the
LLVM IR for a C file:

    $ clang -S -emit-llvm min.c -o min.ll
    $ cat min.ll
    ; ModuleID = 'min.c'
    source_filename = "min.c"
    target datalayout = "e-m:o-i64:64-i128:128-n32:64-S128"
    target triple = "arm64-apple-macosx14.0.0"
    
    @x = global i32 3, align 4
    
    !llvm.module.flags = !{!0, !1, !2, !3, !4}
    !llvm.ident = !{!5}
    
    !0 = !{i32 2, !"SDK Version", [2 x i32] [i32 14, i32 4]}
    !1 = !{i32 1, !"wchar_size", i32 4}
    !2 = !{i32 8, !"PIC Level", i32 2}
    !3 = !{i32 7, !"uwtable", i32 1}
    !4 = !{i32 7, !"frame-pointer", i32 1}
    !5 = !{!"Apple clang version 15.0.0 (clang-1500.3.9.4)"}

There's a lot of stuff there, but most of it is metadata
we can ignore for the time being.  (Note that most of the time I will
write `clang` to indicate we are doing something LLVM-specific,
and `cc` to indicate more general behavior, though on my system the two
are the same).

Stripping out the metadata, one has, simply:

    target triple = "arm64-apple-macosx14.0.0"
    
    @x = global i32 3, align 4

How about the "smallest" possible function? A `void` function of no
arguments, that does nothing:

    $ cat minfun.c
    void x(void) {}
    $ cc -c minfun.c
    $ wc -c minfun.o
         504 minfun.o

Its LLVM IR is also quite simple (here and below I elide the metadata):

```
target triple = "arm64-apple-macosx14.0.0"

define void @x() #0 {
  ret void
}
```

By progressively adding complexity, one can begin to understand the IR.
For example, let's write our first main function (that does nothing):

    $ cat zero.c
    int main() {}
    $ cc zero.c -o zero
    $ ./zero; echo $?
    0

Interestingly, the exit code is 0, even though we didn't specify it.
(This behavior is spelled out in the C standard.)  Running our usual
trick of viewing the IR gives:

    target triple = "arm64-apple-macosx14.0.0"

    define i32 @main() #0 {
      ret i32 0
    }

Can this IR, alone, run?

    $ cat zero.ll
    target triple = "arm64-apple-macosx14.0.0"
    
    define i32 @main() #0 {
      ret i32 0
    }
    $ clang -O3 zero.ll -o zero-min
    $ ./zero-min; echo $?
    0

If instead I want to return, say, 3, we can:

    $ cat three.c
    int main() { return 3; }
    $ cc three.c -o three
    $ ./three; echo $?
    3

I would expect something like

    define i32 @main() #0 {
      ret i32 3
    }

but instead get:

    define i32 @main() #0 {
      %1 = alloca i32, align 4
      store i32 0, ptr %1, align 4
      ret i32 3
    }

We'll come back to the `alloca` and `store` stuff in a moment.

Let's try generating some IR with Babashka.

    $ cat five.bb
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

Here I have added a few tiny helper functions to make both a simple
`main` function template and a target triple template.  Trying it,

    $ bb five.bb
    $ cat five.ll
    target triple = "arm64-apple-macosx14.0.0"
    define i32 @main() {
        ret i32 5
    }
    $ clang -O3 five.ll -o five
    $ ./five; echo $?
    5

It worked!  For this minimal example, it looks like we don't strictly
need that `alloca` and `store` stuff produced by `clang` from our C
code.

I do want to point out something remarkable.  We have taken a
high-level "scripting language," plus the LLVM toolchain, and
generated a small, fast binary executable:

    $ time ./five
    
    real	0m0.001s
    user	0m0.000s
    sys	0m0.001s
    $ wc -c five
       16840 five

Why is this interesting?  One of my favorite things about Go, Rust and
C is that they produce relatively small, stand-alone binaries,
compared with the massive uberjar files involved in shipping
Java/Clojure apps, Python Eggs / virtualenvs / etc.

Though we haven't done much useful yet, we have started chipping out a
path to building tiny executables using Babashka, a tool typically
thought of as primarily useful for "scripting," leveraging LLVM's
assembly-language-like IR and tooling to get us close to the metal.

(Note that I could just as easily have used Clojure instead of
Babashka to produce the IR.  But small Babashka scripts run much
faster.)

# Argument Counting

Let's make another simple program which accepts a variable number
of arguments and returns, as its exit code, the number of arguments
given (including the program name itself).

The equivalent C program is:

    $ cat argcount.c
    int main(int argc, char** argv) {
      return argc;
    }
    $ cc argcount.c -o argcount
    $ ./argcount; echo $?
    1
    $ ./argcount a b c; echo $?
    4

According to `clang -emit-llvm`, the IR for this looks like:

    target triple = "arm64-apple-macosx14.0.0"

    define i32 @main(i32 noundef %0, ptr noundef %1) #0 {
      %3 = alloca i32, align 4
      %4 = alloca i32, align 4
      %5 = alloca ptr, align 8
      store i32 0, ptr %3, align 4
      store i32 %0, ptr %4, align 4
      store ptr %1, ptr %5, align 8
      %6 = load i32, ptr %4, align 4
      ret i32 %6
    }

# Fleshing Out the Babashka Implementation

The following Babashka provides a generator we can use to
experiment with variations in the `argcount` program:

```
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
    (ret :i32 :6))))
```

Running this gives:

    $ cat argcount.ll
    target triple = "arm64-apple-macosx14.0.0"
    define i32 @main(i32 noundef %0, ptr noundef %1) nounwind {
      %3 = alloca i32, align 4
      %4 = alloca i32, align 4
      %5 = alloca ptr, align 8
      store i32 0, ptr %3, align 4
      store i32 %0, ptr %4, align 4
      store ptr %1, ptr %5, align 8
      %6 = load i32, ptr %4, align 4
      ret i32 %6
    }
    $ clang -O3 argcount.ll -o argcount
    $ ./argcount; echo $?
    1
    $ ./argcount a b c d; echo $?
    5

Some of the IR commands look superfluous to me.  Let's try a reduced
version, substituting more meaningful names for the registers and
arguments:

```
(spit
 "argcount-smaller.ll"
 (els
  (target m1-target)
  (def-global-fn :i32 "main" [(farg :i32 :arg0)
                              (farg :ptr :arg1_unused)]
    (assign :retptr (alloca :i32))
    (store :i32 :arg0 :retptr)
    (assign :retval (load :i32 :retptr))
    (ret :i32 :retval))))
```

This gives:

    $ clang -O3 argcount-smaller.ll -o argcount-smaller
    $ ./argcount-smaller; echo $?
    1
    $ ./argcount-smaller a b c d; echo $?
    5

This gives the same result as before.  The Babashka wrapper is still
thin enough at this point to appear superfluous, but my hope is to
build abstractions on top of it as this work proceeds.

# Hello World

A little more work gets us to Hello, World.  First, we'll define a few
more helper functions:

```
(defn external-fn
  "
  Define an externally-available (C) function, in the standard library
  (for now).  The code should not \"throw an exception\" in the LLVM
  sense.
  "
  [typ fn-name & arg-types]
  (format "declare %s @%s(%s) nounwind"
          (name typ)
          (name fn-name)
          (str/join ", " (map (comp #(str % " nocapture") name) arg-types))))

(defn call
  "
  Invoke `fn-name` returning type `typ` with 0 or more type/arg pairs.
  E.g.,

  (call :i32 :negate [:i32 :x])
  ;;=>
  \"call i32 @negate(i32 %x)\"
  "
  [typ fn-name & arg-type-arg-pairs]
  (format "call %s @%s(%s)"
          (name typ)
          (name fn-name)
          (str/join ", " (for [[typ nam] arg-type-arg-pairs]
                           (str (name typ) " %" (name? nam))))))

(defn as-ptr
  "
  Crude wrapper for getelementptr, just for strings (for now).
  "
  [var-name body-len]
  (format "getelementptr [%d x i8],[%d x i8]* @%s, i64 0, i64 0"
          body-len body-len (name var-name)))
```

Note that these wrapper functions are still kind of crude.  There is a lot
of functionality in the LLVM IR which are are not handling or delving into,
though the existing code is simple enough to be easily adapted to new use cases.

If we add these, along with the other helper functions defined above,
to a new file `llir.bb`, our Hello, World example can then be

    $ cat hello.bb
    #!/usr/bin/env bb
    
    (load-file "llir.bb")
    
    (let [hello-str (str/join " " *command-line-args*)]
      (println
       (els (target m1-target)
            (external-fn :i32 :puts :i8*)
            (def-global-const-str :message hello-str)
            (def-fn :i32 :main []
              (assign :as_ptr (as-ptr :message (inc (count hello-str))))
              (call :i32 :puts [:i8* :as_ptr])
              (ret :i32 0)))))
    $ ./hello.bb Hello, World > hello.ll
    $ cat hello.ll
    target triple = "arm64-apple-macosx14.0.0"
    
    declare i32 @puts(i8* nocapture) nounwind
    
    @message = private unnamed_addr constant [13 x i8] c"Hello, World\00"
    
    define i32 @main() nounwind {
      %as_ptr = getelementptr [13 x i8],[13 x i8]* @message, i64 0, i64 0
      call i32 @puts(i8* %as_ptr)
      ret i32 0
    }
    $ clang -O3 hello.ll -o hello
    $ ./hello
    Hello, World

The main addition here is the call to `puts`, which requires both the
external function definition and the call itself.  The `getelementptr`
(warning: [dragons](https://llvm.org/docs/GetElementPtr.html)) is used
to get the address of the string constant.

The program is, as expected, quite small:

    $ du -hs hello
     36K	hello

In comparison, a "hello world" Clojure überjar on my machine is 3.3 MB, nearly
100 times larger.

Note that the output of the program is "compiled into" the binary program:

    $ ./hello.bb The spice must flow. > spice.ll
    $ clang -O3 spice.ll -o spice
    $ ./spice
    The spice must flow.

In a sense, we have built a tiny compiler frontend for a language that
consists solely of single strings to be printed.

# Building A Compiling Calculator

Armed with some of these tools, we can start to build a simple
language that can do integer math.  We will take some of the basics of
[Forth](https://en.wikipedia.org/wiki/Forth_(programming_language)), a
language from the 1970s still in use today (especially in small
embedded systems), and build a simple calculator.

LLVM will handle the parts commonly known as "compiler backend" tasks,
and Babashka will provide our "frontend," namely breaking the text into
tokens and parsing them.  This task is made easy for us, because Forth
is syntactically quite simple, and Babashka relatively powerful.

## Front End

The front end parser is very simple.  

    $ cat parse.bb
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
      (def example "
    
    2 2 +  \\ 4
    5 *    \\ 20
    2 /    \\ 10
    -1 +   \\ add -1
    .      \\ prints 9
    
    
    ")
    
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

These functions are collected in `parse.bb`.  Our next step
will be to use them to generate the appropriate LLVM IR for a given
input.

# Building the Calculator -- IR "sketch"

Though we'll eventually write Babashka code for it, here is a working
example of the IR we want to generate.  We will support the following operations:
push, pop, multiply, and "dot" (`.`), which prints the top of the stack.

    $ cat stack.ll
    target triple = "arm64-apple-macosx14.0.0"
    
    declare i32 @printf(i8*, ...) nounwind
    
    @format_str = private unnamed_addr constant [4 x i8] c"%d\0A\00"
    
    ; Define a type for the stack
    %Stack = type [1000 x i32]
    
    @globalstack = global %Stack zeroinitializer
    
    @numstack = global i32 0
    
    define i32 @get_stack_cnt() {
        %sp = load i32, i32* @numstack, align 4
        ret i32 %sp
    }
    
    define void @add_to_stack_cnt(i32 %value) {
        %sp0 = call i32 @get_stack_cnt()
        %sp1 = add i32 %sp0, %value
        store i32 %sp1, i32* @numstack, align 4
        ret void
    }
    
    define void @push(i32 %value) {
        ;; push value on global stack:
        %sp = call i32 @get_stack_cnt()
        %global_array_ptr = getelementptr %Stack, %Stack* @globalstack, i32 0, i32 %sp
        store i32 %value, i32* %global_array_ptr, align 4
        ;; increment global stack pointer:
        call void @add_to_stack_cnt(i32 1)
        ret void
    }
    
    define i32 @item_at(i32 %sp) {
        %idx1 = sub i32 %sp, 1
        %global_array_ptr = getelementptr %Stack, %Stack* @globalstack, i32 0, i32 %idx1
        %value = load i32, i32* %global_array_ptr, align 4
        ret i32 %value
    }
    
    define i32 @pop() {
        %sp = call i32 @get_stack_cnt()
        %cond = icmp eq i32 %sp, 0
        br i1 %cond, label %end, label %body
    
        body:
            %value = call i32 @item_at(i32 %sp)
            call void @add_to_stack_cnt(i32 -1)
            ret i32 %value
        end:
            ret i32 0  ;; return 0 if stack is empty
    }
    
    ;; print the value at the top of the stack; if the stack is empty, print nothing:
    define void @dot() {
        %sp = call i32 @get_stack_cnt()
        %cond = icmp eq i32 %sp, 0
        br i1 %cond, label %end, label %body
    
        body:
            %value = call i32 @item_at(i32 %sp)
            ;; Print the value:
            %as_ptr = getelementptr [4 x i8], [4 x i8]* @format_str, i64 0, i64 0
            call i32 (i8*, ...) @printf(i8* %as_ptr, i32 %value)
            br label %end
        end:
        ret void
    }
    
    define void @mul() {
        %sp = call i32 @get_stack_cnt()
        ;; make sure there are at least two items on the stack; no-op if not:
        %cond = icmp slt i32 %sp, 2
        br i1 %cond, label %end, label %body
    
        body:
            %value1 = call i32 @pop()
            %value2 = call i32 @pop()
            ;; multiply and push result on stack using @push:
            %result = mul i32 %value1, %value2
            call void @push(i32 %result)
            br label %end
        end:
        ret void
    }
    
    define i32 @main() {
        call void @dot()  ;; prints nothing
        call void @push(i32 66)
        call void @dot()  ;; prints 66
        call void @push(i32 77)
        call void @dot()  ;; 77
        ;; multiply top two items, putting result on top of stack:
        call void @mul()
        call void @dot()  ;; 66*77
    
        %value = call i32 @pop()
        call void @dot()  ;; does not print
        call i32 @pop()   ;; for now, no error handling...
        ret i32 0
    }

Running this gives:

    $ clang -O3 stack.ll -o stack
    $ ./stack
    66
    77
    5082

A few salient features jump out.  First, the `mul`, `push`, `pop`, and
`dot` functions are all pretty simple, and `main` is a simple sequence
of these.  Second, errors are handled silently, and that's probably not
what we want in our "production" calculator.

Let's start fleshing out the Babashka generator.  But first, how do we
view the assembler output corresponding to our LLVM?

    $ clang -O3 -S stack.ll -o stack.s
    $ head stack.s
    	.section	__TEXT,__text,regular,pure_instructions
    	.build_version macos, 14, 0
    	.globl	_get_stack_cnt                  ; -- Begin function get_stack_cnt
    	.p2align	2
    _get_stack_cnt:                         ; @get_stack_cnt
    ; %bb.0:
    Lloh0:
    	adrp	x8, _numstack@PAGE
    Lloh1:
    	ldr	w0, [x8, _numstack@PAGEOFF]

The resulting assembler is not even three times longer than the LLVM IR.

    $ wc -c stack.s stack.ll
        7167 stack.s
        2795 stack.ll
        9962 total

# Putting The Pieces Together

The program `forth.bb` uses the IR generator and the front end to make a tiny
Forth-like calculator:

    $ cat example.fs
    \\ "Forth" calculator example
    
    3   \\ push 3 on stack
    66  \\ push 66 on stack
    .   \\ print item on top of stack; prints "66"
    *   \\ removes 66 and 3 and replaces top of stack with 198
    77  \\ push 77 on stack
    .   \\ prints "77"
    *   \\ removes 77 and 198 and replaces top of stack with 77 * 198 = 15246
    .   \\ prints "15246"
    $ ./forth.bb example.fs  # Creates example.ll
    $ clang -O3 example.ll -o example
    $ ./example
    66
    77
    15246
    $ du -hs example
     36K	example
    $ time ./example > /dev/null
    
    real	0m0.003s
    user	0m0.001s
    sys	0m0.002s

`forth.bb` is a fairly straightforward conversion of the LLVM IR shown
above. While is still far from a "real" Forth implementation (I've
only implemented one arithmetic operation; there is no way to define
new values or functions, and there is no real error handling), it
illustrates the principle I am interested in exploring: the generation
of LLVM IR to create small, fast executables.

# Lisp

A variant worth exploring is to switch from Forth syntax to Lisp:

    $ cat example.lisp
    
    ;; alternative calculator syntax:
    (print (* 77 (* 66 3)))

Here Babashka/Clojure helps us because this is valid [EDN](https://github.com/edn-format/edn) data, readable with `clojure.edn/read-string`.  But we need to convert the resulting
nested list into "SSA" (single static assignment) expressions LLVM understands.
This is relatively straightforward with a recursive function which expands leaves of
the tree and stores the results as intermediate values:

```
(defn to-ssa [expr bindings]
  (if (not (coll? expr))
    expr
    (let [[op & args] expr
          result (gensym "r")
          args (doall
                (for [arg args]
                  (if-not (coll? arg)
                    arg
                    (to-ssa arg bindings))))]
      (swap! bindings conj (concat [result op] args))
      result)))

(defn convert-to-ssa [expr]
  (let [bindings (atom [])]
    (to-ssa expr bindings)
    @bindings))
```

We use `gensym` here to get a unique variable name for each
assignment, and `doall` to force the evaluation of the lazy `for`
expansion of the argument terms.  The result:

    (->> "example.lisp"
         slurp
         edn/read-string
         convert-to-ssa)
    ;;=>
    [(r20892 * 66 3)
     (r20891 * 77 r20892)
     (r20890 print r20891)]

The next step will be to actually write out the corresponding LLVM IR.
This is satisfyingly small:
```
(def ops
  {'* #(mul :i32 %1 %2)
   'print
   #(call "i32 (i8*, ...)"
          :printf
          [:i8* :as_ptr]
          [:i32 (sigil %1)])})

(let [[filename] *command-line-args*
      outfile (str (fs/strip-ext filename) ".ll")
      format-str "%d\n"
      assignments (->> filename
                       slurp
                       edn/read-string
                       convert-to-ssa)]
  (spit outfile
        (els
         (target m1-target)
         (external-fn :i32 :printf :i8*, :...)
         (def-global-const-str :fmt_str format-str)
         (def-fn :i32 :main []
           (assign :as_ptr
                   (gep (fixedarray 4 :i8)
                        (star (fixedarray 4 :i8))
                        (sigil :fmt_str)
                        [:i64 0]
                        [:i64 0]))
           (apply els
                  (for [[reg op & args] assignments
                        :let [op-fn (ops op)]]
                    (if-not op-fn
                      (throw (ex-info "bad operator" {:op op}))
                      (assign reg (apply op-fn args)))))
           (ret :i32 0)))))
```

Putting the parts together (`lisp.bb`), we have:

    $ ./lisp.bb example.lisp
    $ clang -O3 example.ll -o example
    $ ./example
    15246

To say this is a "working Lisp compiler" at this point would be quite
grandiose... but we have the ingredients to build upon.  A good exercise
at this point would be to add the other arithmetic operators.  Future
posts on this topic may investigate function creation (both named functions
and lambdas), something LLVM does support and definitely required to make
our toy "languages" into real ones.

To summarize, the strategy we have taken is as follows:

1. Use a high level language (in our case, Babashka/Clojure) to
   parse input and translate into LLVM IR;
2. Compile LLVM IR to small, fast binaries using `clang`.

Whenever possible, I want to make small, fast programs, and I like playing
with and creating small programming languages.  LLVM provides a fascinating
set of tools and techniques for doing so.
