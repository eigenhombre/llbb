
       \\ initial state          stack: []
3      \\ put 3 on stack.        stack: [3]
99     \\ put 99 on stack.       stack: [3, 99]
drop   \\ discard top item.      stack: [3]
drop   \\ discard top item.      stack: []
2 2    \\ put 2 on stack, twice: stack: [2, 2]
+      \\ 2 + 2 = 4.             stack: [4]
5 *    \\ multiply 4 * 5.        stack: [20]
2 /    \\ divide by 2.           stack: [10]
-1 +   \\ add -1                 stack: [9]
8 -    \\ subtract 8 -> 1        stack: [1]
.      \\ prints '1'             stack: [1]
drop   \\ removes 1.             stack: []

