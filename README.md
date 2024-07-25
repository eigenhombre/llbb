**NOTE** What follows are notes in progress, shown while they are
being written ... expect radical changes for the time being.

Can you write a compiler using [Babashka](https://babashka.org/)?  I
mean a "real" compiler that will ultimately yield compact object code?
Is it even possible, or is it totally bonkers?  I recently got
interested in answering the question using [LLVM](https://llvm.org/),
a powerful compiler toolchain that uses a sort of abstract assembly
language as it's ["intermediate
representation"](https://en.wikipedia.org/wiki/Intermediate_representation).

Let's start with the question, what's the minimal compilation "unit" that a C compiler will accept?

Here are some attempts.  First, a tiny bit of numerical state, on its own:

    $ cat min.c
    int x = 3;
    $ cc -c min.c
    $ ls -l min.o
    -rw-r--r--  1 jacobsen  staff  464 Jul 25 08:27 min.o

How about this one?  A void function of no arguments, that does nothing:

    $ cat minfun.c
    void x(void) {}
    $ cc -c minfun.c
    $ ls -l minfun.o
    -rw-r--r--  1 jacobsen  staff  504 Jul 25 08:27 minfun.o

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
we can ignore for the time being.

How about a minimal function?

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

If I rip out all the extra junk I get what looks like the significant
bits:

    # min.c:
    target triple = "arm64-apple-macosx14.0.0"
    @x = global i32 3, align 4

    # minfun.c:
    target triple = "arm64-apple-macosx14.0.0"
    define void @x() #0 {
      ret void
    }

Can you get even more minimal?

    $ cat empty.c  # This file is literally empty
    $ cc -c empty.c
    $ ls -l empty.o
    -rw-r--r--  1 jacobsen  staff  336 Jul 25 08:27 empty.o
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
    
    real	0m0.001s
    user	0m0.000s
    sys	0m0.001s
    $ ls -l five
    -rwxr-xr-x  1 jacobsen  staff  16840 Jul 25 08:27 five

One of my favorite things about Go, Rust and C is that they produce
stand-alone binaries.  We've just started chipping out a path to
doing the same with Babashka, a tool typically thought of as primarily
useful for "scripting."

(Note that I could just as easily have used Clojure.  But small
Babashka scripts run much faster.)

# Hello Word

A little more work gets us to Hello, World:

    $ cat hello.bb
    #!/usr/bin/env bb
    
    (load-file "llir.bb")
    
    (defn hello-main [body]
      (els (target m1-target)
           (extern-i8* "puts")
           (global-const-str "xxx" body)
           (main-calling-puts body)))
    
    (let [hello-str (str/join " " *command-line-args*)]
      (println (hello-main hello-str)))
    $ ./hello.bb Hello, World > hello.ll
    $ cat hello.ll
    target triple = "arm64-apple-macosx14.0.0"
    declare i32 @puts(i8* nocapture) nounwind
    @xxx = private unnamed_addr constant [13 x i8] c"Hello, World\00"
    define i32 @main() {
        %as_ptr = getelementptr [13 x i8],[13 x i8]* @xxx, i64 0, i64 0
    
        call i32 @puts(i8* %as_ptr)
        ret i32 0
    }
    $ clang -O3 hello.ll -o hello
    $ ./hello
    Hello, World

Note the execution time is reasonably short.  Here `llir.bb` is a small
utility module in this repository used to generate LLVM IR commands similar
to the two we made, above.

The main addition here is the call to `puts`, which requires both the
external function definition and the call itself.  The `getelementptr`
(warning: dragons) is used to get the address of the string constant.

# Example 2

LLVM IR is easily
generated using pretty much any programming language. Babashka
provides the power of Clojure, fast start up speed, the REPL, etc.
making it a fun way to experiment with LLVM and toy languages.

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

`clang` can be used to generate LLVM IR from C code, as follows:

    $ clang -S -emit-llvm argcount.c -o argcount.ll
    $ cat argcount.ll
    ; ModuleID = 'argcount.c'
    source_filename = "argcount.c"
    target datalayout = "e-m:o-i64:64-i128:128-n32:64-S128"
    target triple = "arm64-apple-macosx14.0.0"
    
    ; Function Attrs: noinline nounwind optnone ssp uwtable(sync)
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
    
    attributes #0 = { noinline nounwind optnone ssp uwtable(sync) "frame-pointer"="non-leaf" "min-legal-vector-width"="0" "no-trapping-math"="true" "probe-stack"="__chkstk_darwin" "stack-protector-buffer-size"="8" "target-cpu"="apple-m1" "target-features"="+aes,+crc,+crypto,+dotprod,+fp-armv8,+fp16fml,+fullfp16,+lse,+neon,+ras,+rcpc,+rdm,+sha2,+sha3,+sm4,+v8.1a,+v8.2a,+v8.3a,+v8.4a,+v8.5a,+v8a,+zcm,+zcz" }
    
    !llvm.module.flags = !{!0, !1, !2, !3, !4}
    !llvm.ident = !{!5}
    
    !0 = !{i32 2, !"SDK Version", [2 x i32] [i32 14, i32 4]}
    !1 = !{i32 1, !"wchar_size", i32 4}
    !2 = !{i32 8, !"PIC Level", i32 2}
    !3 = !{i32 7, !"uwtable", i32 1}
    !4 = !{i32 7, !"frame-pointer", i32 1}
    !5 = !{!"Apple clang version 15.0.0 (clang-1500.3.9.4)"}

This is actually fairly simple, and a lot of the boilerplate can  be
eliminated.  Our next move is going to be to extend our Babashka
implementation to handle the `alloc`, `load` and `store` operations we
need.
