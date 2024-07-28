**NOTE** What follows are notes in progress, shown while they are
being written ... expect radical changes for the time being.

Can you write a compiler using [Babashka](https://babashka.org/)?  I
mean a "real" compiler that will ultimately yield compact object code?
Is it even possible, or is it totally bonkers?  I recently got
interested in answering the question using [LLVM](https://llvm.org/),
a powerful compiler toolchain that uses a sort of abstract assembly
language as it's ["intermediate
representation"](https://en.wikipedia.org/wiki/Intermediate_representation).

LLVM IR is easily generated using pretty much any programming
language. Babashka provides the power of Clojure, fast start up speed,
the REPL, etc., making it a fun way to experiment with LLVM and toy
languages.

Let's start with the question, what's the minimal semantic unit of compilation
that the C compiler will accept?

Here are some attempts.  First, a tiny bit of numerical state, on its own:

    $ cat min.c
    int x = 3;
    $ cc -c min.c
    $ ls -l min.o
    -rw-r--r--  1 jacobsen  staff  464 Jul 26 21:16 min.o

How about this one?  A void function of no arguments, that does nothing:

    $ cat minfun.c
    void x(void) {}
    $ cc -c minfun.c
    $ ls -l minfun.o
    -rw-r--r--  1 jacobsen  staff  504 Jul 26 21:16 minfun.o

One can view the LLVM output for a C file:

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

There's a lot of stuff there, but most of it looks like metadata
we can ignore for the time being.  (Note that most of the time I will
write `clang` to indicate we are doing something LLVM-specific,
and `cc` to indicate more general behavior, though on my system the two
are the same).

What would a minimal function look like?

    $ clang -S -emit-llvm minfun.c -o minfun.ll
    $ cat minfun.ll
    ; ModuleID = 'minfun.c'
    source_filename = "minfun.c"
    target datalayout = "e-m:o-i64:64-i128:128-n32:64-S128"
    target triple = "arm64-apple-macosx14.0.0"
    
    ; Function Attrs: noinline nounwind optnone ssp uwtable(sync)
    define void @x() #0 {
      ret void
    }
    
    attributes #0 = { noinline nounwind optnone ssp uwtable(sync) "frame-pointer"="non-leaf" "min-legal-vector-width"="0" "no-trapping-math"="true" "probe-stack"="__chkstk_darwin" "stack-protector-buffer-size"="8" "target-cpu"="apple-m1" "target-features"="+aes,+crc,+crypto,+dotprod,+fp-armv8,+fp16fml,+fullfp16,+lse,+neon,+ras,+rcpc,+rdm,+sha2,+sha3,+sm4,+v8.1a,+v8.2a,+v8.3a,+v8.4a,+v8.5a,+v8a,+zcm,+zcz" }
    
    !llvm.module.flags = !{!0, !1, !2, !3, !4}
    !llvm.ident = !{!5}
    
    !0 = !{i32 2, !"SDK Version", [2 x i32] [i32 14, i32 4]}
    !1 = !{i32 1, !"wchar_size", i32 4}
    !2 = !{i32 8, !"PIC Level", i32 2}
    !3 = !{i32 7, !"uwtable", i32 1}
    !4 = !{i32 7, !"frame-pointer", i32 1}
    !5 = !{!"Apple clang version 15.0.0 (clang-1500.3.9.4)"}

If I rip out a lot of extra junk, I get what looks like the significant
bits:

    # min.c:
    target triple = "arm64-apple-macosx14.0.0"
    @x = global i32 3, align 4

    # minfun.c:
    target triple = "arm64-apple-macosx14.0.0"
    define void @x() {
      ret void
    }

Can you get even more minimal?

    $ cat empty.c  # This file is literally empty
    $ cc -c empty.c
    $ ls -l empty.o
    -rw-r--r--  1 jacobsen  staff  336 Jul 26 21:16 empty.o
    $ clang -S -emit-llvm empty.c -o empty.ll
    $ cat empty.ll
    ; ModuleID = 'empty.c'
    source_filename = "empty.c"
    target datalayout = "e-m:o-i64:64-i128:128-n32:64-S128"
    target triple = "arm64-apple-macosx14.0.0"
    
    !llvm.module.flags = !{!0, !1, !2, !3, !4}
    !llvm.ident = !{!5}
    
    !0 = !{i32 2, !"SDK Version", [2 x i32] [i32 14, i32 4]}
    !1 = !{i32 1, !"wchar_size", i32 4}
    !2 = !{i32 8, !"PIC Level", i32 2}
    !3 = !{i32 7, !"uwtable", i32 1}
    !4 = !{i32 7, !"frame-pointer", i32 1}
    !5 = !{!"Apple clang version 15.0.0 (clang-1500.3.9.4)"}

You can see the same kinds of metadata seen in the other examples, but
without the juicy, significant bits.

By progressively adding complexity, one can begin to understand the IR.
For example, let's write our first main function (that does nothing):

    $ cat zero.c
    int main() {}
    $ cc zero.c -o zero
    $ ./zero; echo $?
    0

Interestingly, the exit code is 0, even though we didn't specify it.
(This behavior is spelled out in the C standard.)  Running our usual
trick of viewing the IR gives (in addition to the usual junk):

    define i32 @main() #0 {
      ret i32 0
    }

Can this IR, alone, run?

    $ cat zero.ll
    define i32 @main() #0 {
      ret i32 0
    }
    $ clang -O3 zero.ll -o zero-min
    warning: overriding the module target triple with arm64-apple-macosx14.0.0 [-Woverride-module]
    1 warning generated.
    $ ./zero-min; echo $?
    0

If we don't want a warning, we need to add, e.g.,

    target triple = "arm64-apple-macosx14.0.0"

to the top of the file.

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

Here I have added tiny helper functions to make both a simple `main`
function template and a target triple template.  Trying it,

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
    
    real	0m0.002s
    user	0m0.000s
    sys	0m0.001s
    $ ls -l five
    -rwxr-xr-x  1 jacobsen  staff  16840 Jul 26 21:16 five

One of my favorite things about Go, Rust and C is that they produce
relatively small, stand-alone binaries, compared with the massive
uberjar files involved in shipping Java/Clojure apps, Python
Eggs/virtualenvs/etc., etc.).

We've just started chipping out a path to building tiny executables
using Babashka, a tool typically thought of as primarily useful for
"scripting," leveraging LLVM's assembly-language-like IR and tooling
to get us close to the metal.

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
            (global-const-str :message hello-str)
            (def-global-fn :i32 "main" []
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

Note that the output of the program is "compiled into" the binary program:

    $ ./hello.bb The spice must flow. > spice.ll
    $ clang -O3 spice.ll -o spice
    $ ./spice
    The spice must flow.

In a sense, we have built a tiny compiler for a language that consists solely
of single strings to be printed.

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

```
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
```

These functions are collected in a file `forth.bb`.  Our next step
will be to use them to generate the appropriate LLVM IR for a given
input.
