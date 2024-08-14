target triple = "arm64-apple-macosx14.0.0"

declare i32 @printf(i8*, ...) nounwind

@format_str = private unnamed_addr constant [4 x i8] c"%d\0A\00"

; Define a type for the stack
%Stack = type [1000 x i32]

@globalstack = global %Stack zeroinitializer

@numstack = global i32 0

define i32 @get_stack_cnt() {
    %sp = load i32, i32* @numstack, align 4
    ret i32 %sp
}

define void @add_to_stack_cnt(i32 %value) {
    %sp0 = call i32 @get_stack_cnt()
    %sp1 = add i32 %sp0, %value
    store i32 %sp1, i32* @numstack, align 4
    ret void
}

define void @push(i32 %value) {
    ;; push value on global stack:
    %sp = call i32 @get_stack_cnt()
    %global_array_ptr = getelementptr %Stack, %Stack* @globalstack, i32 0, i32 %sp
    store i32 %value, i32* %global_array_ptr, align 4
    ;; increment global stack pointer:
    call void @add_to_stack_cnt(i32 1)
    ret void
}

define i32 @item_at(i32 %sp) {
    %idx1 = sub i32 %sp, 1
    %global_array_ptr = getelementptr %Stack, %Stack* @globalstack, i32 0, i32 %idx1
    %value = load i32, i32* %global_array_ptr, align 4
    ret i32 %value
}

define i32 @pop() {
    %sp = call i32 @get_stack_cnt()
    %cond = icmp eq i32 %sp, 0
    br i1 %cond, label %end, label %body

    body:
        %value = call i32 @item_at(i32 %sp)
        call void @add_to_stack_cnt(i32 -1)
        ret i32 %value
    end:
        ret i32 0  ;; return 0 if stack is empty
}

;; print the value at the top of the stack; if the stack is empty, print nothing:
define void @dot() {
    %sp = call i32 @get_stack_cnt()
    %cond = icmp eq i32 %sp, 0
    br i1 %cond, label %end, label %body

    body:
        %value = call i32 @item_at(i32 %sp)
        ;; Print the value:
        %as_ptr = getelementptr [4 x i8], [4 x i8]* @format_str, i64 0, i64 0
        call i32 (i8*, ...) @printf(i8* %as_ptr, i32 %value)
        br label %end
    end:
    ret void
}

define void @mul() {
    %sp = call i32 @get_stack_cnt()
    ;; make sure there are at least two items on the stack; no-op if not:
    %cond = icmp slt i32 %sp, 2
    br i1 %cond, label %end, label %body

    body:
        %value1 = call i32 @pop()
        %value2 = call i32 @pop()
        ;; multiply and push result on stack using @push:
        %result = mul i32 %value1, %value2
        call void @push(i32 %result)
        br label %end
    end:
    ret void
}

define i32 @main() {
    call void @dot()  ;; prints nothing
    call void @push(i32 66)
    call void @dot()  ;; prints 66
    call void @push(i32 77)
    call void @dot()  ;; 77
    ;; multiply top two items, putting result on top of stack:
    call void @mul()
    call void @dot()  ;; 66*77

    %value = call i32 @pop()
    call void @dot()  ;; does not print
    call i32 @pop()   ;; for now, no error handling...
    ret i32 0
}
