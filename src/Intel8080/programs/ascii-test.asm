.data 
arr: db "Hello, world!\n", '\0'

.code
lxi h, arr
call helloWorld
stop

helloWorld:
    mov a, m
    out 0x0
    inx h
    cpi 0
    jnz helloWorld
    ret