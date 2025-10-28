;program to sum an array
org 0x100
arr: db 1, 2, 3, 4, 5
n: db 5
;1+2+3+4+5
;=15
org 0x0

lxi h, n
mov b, l

lxi h, arr

mvi c, 0x0
call loop
mov a, c
out 0x0
hlt

loop:
    mov a, m
    add c
    mov c, a
    inx h
    dcr b
    rz
    jmp loop

