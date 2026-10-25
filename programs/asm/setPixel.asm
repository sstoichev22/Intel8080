PIXELS equ 0x8000
WIDTH equ 32

.text
    MVI B, 8
    MVI C, 8
    MVI D, 3
    CALL set_pixel
    STOP

set_pixel:
    MOV A, D
    MVI H, 0
    MOV L, C
    DAD H
    DAD H
    DAD H
    DAD H
    DAD H
    MVI D, 0
    MOV E, B
    DAD D
    LXI D, PIXELS
    DAD D
    MOV M, A
    RET
