target triple = "arm64-apple-macosx14.0.0"

declare i32 @printf(i8* nocapture, ...) nounwind

@fmt_str = private unnamed_addr constant [4 x i8] c"%d
\00"

define i32 @main() nounwind {
  %as_ptr = getelementptr [4 x i8], [4 x i8]* @fmt_str, i64 0, i64 0
  %r468 = mul i32 66, 3

%r467 = mul i32 77, %r468

%r466 = call i32 (i8*, ...) @printf(i8* %as_ptr, i32 %r467)
  ret i32 0
}
