; program to sum an array
org 0x100
arr: db 1, 2, 3, 4, 5 ; = 1+2+3+4+5=15
n:   db 5

org 0x0
LDA n
MOV B, A

LXI H, arr
MVI C, 0x0


loop:
    MOV A, M
    ADD C
    MOV C, A
    INX H
    DCR B
    JNZ loop

MOV A, C
OUT 0x0
HLT
