.data
arr: db "Hello, world!\n", '\0'

.text
lxi h, arr
call helloWorld
stop

helloWorld:
    mov a, m
    cpi 0
    rz
    out 0x0
    inx h
    jmp helloWorld
