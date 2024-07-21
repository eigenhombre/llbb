Can you write a compiler using Babashka?  I mean a "real" compiler that
will ultimately yield compact object code?  Is it even possible, or is
it totally bonkers?

This Babashka script generates [LLVM](https://llvm.org/)
[IR](https://en.wikipedia.org/wiki/Intermediate_representation) which
can be translated to object code using the LLVM toolchain as follows:

    $ ./ll.bb Hello, World > hello.ll
    $ clang -O3 hello.ll -o hello
    $ time ./hello
    Hello, World

    real	0m0.220s
    user	0m0.001s
    sys	0m0.003s
    $ ls -lh hello
    -rwxr-xr-x  1 jacobsen  staff    33K Jul 20 20:39 hello
    $ cat hello.ll
    target triple = "arm64-apple-macosx14.0.0"
    declare i32 @puts(i8* nocapture) nounwind
    @xxx = private unnamed_addr constant [12 x i8] c"Hello, World"
    define i32 @main() {
        %as_ptr = getelementptr [12 x i8],[12 x i8]* @xxx, i64 0, i64 0
        call i32 @puts(i8* %as_ptr)
        ret i32 0
    }
    $

While it may be "cheating" to use the LLVM toolchain, its IR is easily
generated using pretty much any programming language, and Babashka
provides the power of Clojure, fast start up speed, the REPL,
etc. making it a fun way to experiment with LLVM and toy languages.

The next step for this repository is to flesh out the Hello, World
example and move towards the direction of a simple toy language,
perhaps a purely arithmetic Forth or similar.
