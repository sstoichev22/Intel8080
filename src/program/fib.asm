; fib(6)=0+1+1+2+3+5+8
MVI B, 6
MVI C, 0
MVI D, 1

CALL loop

loop:
    DCR B
    MOV A, B
    CPI 0
    RZ


    MOV A, C
    ADD D
    MOV E, A

    MOV C, D
    MOV D, E


    JMP loop


MOV A, D
OUT 0x0
HLT
