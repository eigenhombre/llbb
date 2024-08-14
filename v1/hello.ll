target triple = "arm64-apple-macosx14.0.0"

declare i32 @puts(i8* nocapture) nounwind

@message = private unnamed_addr constant [13 x i8] c"Hello, World\00"

define i32 @main() nounwind {
  %as_ptr = getelementptr [13 x i8],[13 x i8]* @message, i64 0, i64 0
  call i32 @puts(i8* %as_ptr)
  ret i32 0
}
