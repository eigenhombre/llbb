target triple = "arm64-apple-macosx14.0.0"

define i32 @main(i32 %arg0, ptr %arg1_unused) nounwind {
  %retptr = alloca i32, align 4
  store i32 %arg0, ptr %retptr, align 4
  %retval = load i32, i32* %retptr, align 4
  ret i32 %retval
}