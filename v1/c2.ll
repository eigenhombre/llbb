; ModuleID = 'c2.c'
source_filename = "c2.c"
target datalayout = "e-m:o-i64:64-i128:128-n32:64-S128"
target triple = "arm64-apple-macosx14.0.0"

@__stdinp = external global ptr, align 8

; Function Attrs: noinline nounwind optnone ssp uwtable(sync)
define i32 @main() #0 {
  %1 = alloca i32, align 4
  %2 = alloca [100 x i8], align 1
  %3 = alloca i64, align 8
  %4 = alloca ptr, align 8
  store i32 0, ptr %1, align 4
  %5 = getelementptr inbounds [100 x i8], ptr %2, i64 0, i64 0
  %6 = load ptr, ptr @__stdinp, align 8
  %7 = call ptr @fgets(ptr noundef %5, i32 noundef 100, ptr noundef %6)
  %8 = icmp ne ptr %7, null
  br i1 %8, label %10, label %9

9:                                                ; preds = %0
  store i32 0, ptr %1, align 4
  br label %26

10:                                               ; preds = %0
  %11 = getelementptr inbounds [100 x i8], ptr %2, i64 0, i64 0
  %12 = call i64 @strtol(ptr noundef %11, ptr noundef %4, i32 noundef 10)
  store i64 %12, ptr %3, align 8
  %13 = getelementptr inbounds [100 x i8], ptr %2, i64 0, i64 0
  %14 = load ptr, ptr %4, align 8
  %15 = icmp eq ptr %13, %14
  br i1 %15, label %16, label %17

16:                                               ; preds = %10
  store i32 0, ptr %1, align 4
  br label %26

17:                                               ; preds = %10
  %18 = load ptr, ptr %4, align 8
  %19 = load i8, ptr %18, align 1
  %20 = sext i8 %19 to i32
  %21 = icmp ne i32 %20, 0
  br i1 %21, label %22, label %23

22:                                               ; preds = %17
  store i32 0, ptr %1, align 4
  br label %26

23:                                               ; preds = %17
  %24 = load i64, ptr %3, align 8
  %25 = trunc i64 %24 to i32
  store i32 %25, ptr %1, align 4
  br label %26

26:                                               ; preds = %23, %22, %16, %9
  %27 = load i32, ptr %1, align 4
  ret i32 %27
}

declare ptr @fgets(ptr noundef, i32 noundef, ptr noundef) #1

declare i64 @strtol(ptr noundef, ptr noundef, i32 noundef) #1

attributes #0 = { noinline nounwind optnone ssp uwtable(sync) "frame-pointer"="non-leaf" "min-legal-vector-width"="0" "no-trapping-math"="true" "probe-stack"="__chkstk_darwin" "stack-protector-buffer-size"="8" "target-cpu"="apple-m1" "target-features"="+aes,+crc,+crypto,+dotprod,+fp-armv8,+fp16fml,+fullfp16,+lse,+neon,+ras,+rcpc,+rdm,+sha2,+sha3,+sm4,+v8.1a,+v8.2a,+v8.3a,+v8.4a,+v8.5a,+v8a,+zcm,+zcz" }
attributes #1 = { "frame-pointer"="non-leaf" "no-trapping-math"="true" "probe-stack"="__chkstk_darwin" "stack-protector-buffer-size"="8" "target-cpu"="apple-m1" "target-features"="+aes,+crc,+crypto,+dotprod,+fp-armv8,+fp16fml,+fullfp16,+lse,+neon,+ras,+rcpc,+rdm,+sha2,+sha3,+sm4,+v8.1a,+v8.2a,+v8.3a,+v8.4a,+v8.5a,+v8a,+zcm,+zcz" }

!llvm.module.flags = !{!0, !1, !2, !3, !4}
!llvm.ident = !{!5}

!0 = !{i32 2, !"SDK Version", [2 x i32] [i32 14, i32 4]}
!1 = !{i32 1, !"wchar_size", i32 4}
!2 = !{i32 8, !"PIC Level", i32 2}
!3 = !{i32 7, !"uwtable", i32 1}
!4 = !{i32 7, !"frame-pointer", i32 1}
!5 = !{!"Apple clang version 15.0.0 (clang-1500.3.9.4)"}
