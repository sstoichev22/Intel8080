.data
ask: db "Enter a number: \n", '\0'
answer: db "Your number was: ", '\0'
buffer: ds 2

.code
lxi h, ask
call print

lxi h, buffer
call getinput

mvi a, '\0'
mov m, a

lxi h, answer
call print

lxi h, buffer
call print
stop

print:
    mov a, m
    out 0x0
    inx h
    cpi 0
    jnz print
    ret


getinput:
    _getinputloop:
        in 0x1
        ani 1
        jz _getinputloop
    in 0x0
    mov m, a
    inx h
    cpi 10 ;\n
    jnz getinput
    ret
