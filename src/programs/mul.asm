org 0x0
mvi b, 3
mvi c, 5

call mul
out 0x0
hlt

mul:
    add b
    dcr c
    rz
    jmp mul