; HL = VRAM pointer
lxi h, 0x8000
mvi c, 64

call byte_loop

hlt

byte_loop:
    mvi m, 00011011B
    inx h
    dcr c
    rz
    jmp byte_loop
