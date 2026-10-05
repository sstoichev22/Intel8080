VIDEO_MEM_START equ 0x9000
WIDTH equ 16

.data

.code
lxi h, VIDEO_MEM_START
call hor

stop

hor:
    mvi b, WIDTH * WIDTH
_horLoop:
    mov a, l
    ani 3
    mov m, a
    inx h
    dcr b
    jnz _horLoop
    ret
