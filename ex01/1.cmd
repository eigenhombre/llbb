cat ll1.bb
./ll1.bb Hello, World > hello.ll
cat hello.ll
clang -O3 hello.ll -o hello
time ./hello
