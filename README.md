Can you write a compiler using [Babashka](https://babashka.org/)?  I
mean a "real" compiler that will ultimately yield compact object code?
Is it even possible, or is it totally bonkers?

# Example 1


This Babashka script generates [LLVM](https://llvm.org/)
[IR](https://en.wikipedia.org/wiki/Intermediate_representation) which
can be translated to object code using the LLVM toolchain as follows:


    $ cat ll1.bb
    #!/usr/bin/env bb
    
    (load-file "../llir.bb")
    (load-file "../cmd.bb")
    
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
    @xxx = private unnamed_addr constant [12 x i8] c"Hello, World"
    define i32 @main() {
        %as_ptr = getelementptr [12 x i8],[12 x i8]* @xxx, i64 0, i64 0
    
        call i32 @puts(i8* %as_ptr)
        ret i32 0
    }
    $ clang -O3 hello.ll -o hello
    $ time ./hello
    Hello, World
    
    real	0m0.176s
    user	0m0.001s
    sys	0m0.002s

# Example 2

While it may be "cheating" to use the LLVM toolchain, its IR is easily
generated using pretty much any programming language, and Babashka
provides the power of Clojure, fast start up speed, the REPL,
etc. making it a fun way to experiment with LLVM and toy languages.

The next step for this repository is to flesh out the Hello, World
example and move towards the direction of a simple toy language,
perhaps a purely arithmetic Forth or similar.


