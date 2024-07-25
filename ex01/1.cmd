cat hello.bb
./hello.bb Hello, World > hello.ll
cat hello.ll
clang -O3 hello.ll -o hello
time ./hello
