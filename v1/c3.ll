target triple = "arm64-apple-macosx14.0.0"

@__stdinp = external global ptr, align 8
declare ptr @fgets(ptr noundef, i32 noundef, ptr noundef) #1

define i32 @main() #0 {
  %1 = alloca i32, align 4
  %2 = alloca [100 x i8], align 1
  store i32 0, ptr %1, align 4
  %3 = getelementptr inbounds [100 x i8], ptr %2, i64 0, i64 0
  %4 = load ptr, ptr @__stdinp, align 8
  %5 = call ptr @fgets(ptr noundef %3, i32 noundef 100, ptr noundef %4)
  %6 = icmp ne ptr %5, null
  br i1 %6, label %8, label %7

7:                                                ; preds = %0
  store i32 0, ptr %1, align 4
  br label %9

8:                                                ; preds = %0
  store i32 1, ptr %1, align 4
  br label %9

9:                                                ; preds = %8, %7
  %10 = load i32, ptr %1, align 4
  ret i32 %10
}
