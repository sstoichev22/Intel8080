; program to sum an array
org 0x100
arr: db 1, 2, 3, 4, 5 ; = 1+2+3+4+5=15
n:   db 5

org 0x0
LDA n        ; A = [n]
MOV B, A     ; B = number of elements

LXI H, arr   ; HL points to start of array
MVI C, 0x0   ; accumulator for sum


loop:
    MOV A, M ; A = [HL]
    ADD C    ; A = A + C
    MOV C, A
    INX H    ; next element
    DCR B        ; return if B == 0
    JNZ loop

MOV A, C     ; output sum
OUT 0x0
HLT
