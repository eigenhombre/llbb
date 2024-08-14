target triple = "arm64-unknown-linux-eabi"
declare i32 @puts(i8* nocapture) nounwind
@message = private unnamed_addr constant [20 x i8] c"Hello, Raspberry Pi\00"
define i32 @main() nounwind {
  %as_ptr = getelementptr [20 x i8],[20 x i8]* @message, i64 0, i64 0
  call i32 @puts(i8* %as_ptr)
  ret i32 0
}
