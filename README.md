Can you write a compiler using [Babashka](https://babashka.org/)?  I
mean a "real" compiler that will ultimately yield compact object code?
Is it even possible, or is it totally bonkers?

# Example 1


The following Babashka "Hello, world" script generates
[LLVM](https://llvm.org/)
[IR](https://en.wikipedia.org/wiki/Intermediate_representation), which
can be translated to object code using the LLVM toolchain as follows:



    $ cat ll1.bb
    #!/usr/bin/env bb
    
    (load-file "../llir.bb")
    
    (defn hello-main [body]
      (str/join "\n"
                [(target m1-target)
                 (extern-i8* "puts")
                 (global-const-str "xxx" body)
                 (main-calling-puts body)]))
    
    (print
     (hello-main (str/join " " *command-line-args*)))
    $ ./ll1.bb Hello, World > hello.ll
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
    $ time ./hello
    Hello, World
    
    real	0m0.178s
    user	0m0.001s
    sys	0m0.002s

Note the execution time is reasonably short.  Here `llir.bb` is a small
utility module in this repository used to generate LLVM IR commands.



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

