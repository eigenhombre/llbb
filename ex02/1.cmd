cat argcount.c
cc argcount.c -o argcount
./argcount; echo $?
./argcount a b c; echo $?
