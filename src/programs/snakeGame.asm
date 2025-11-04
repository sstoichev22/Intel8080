;this program will deal with the list for snake
;each coord is in form
;(x << 4) | y
;max length is 196, 14x14 playable grid.
;therefore to check for errors, we will make it at ROM_END-196
org 0x7F3B
snake: ds 196

org 0x3000
screenWidth: db 16
len: db 5 ;whatever default val

org 0x0
call initSnake

; Load initial values
lxi h, len
mov b, m        ; B = length counter
lxi h, snake    ; HL = current snake address

loop:
    mov a, b
    cpi 0
    jz done

    push b
    push h
    mov b, m
    call uncompact
    mvi d, 0b01
    call setPixel

    pop h
    pop b
    inx h
    dcr b
    jmp loop

done:
    hlt

initSnake:
    lxi h, len
    mov c, m
    lxi h, snake
    mvi b, 0b10001000
    call _initSnakeLoop
    ret
_initSnakeLoop:
    mov m, b
    inx h
    mov a, b
    adi 0b00010000
    mov b, a
    dcr c
    jnz _initSnakeLoop
    ret


;params: B=x,C=y,D=color
;returns a pixel on the screen at (x,y) as color
setPixel:
    mov e, d
    mov d, b
    lxi h, screenWidth
    mov b, m ; screenWidth
    mvi a, 0
    call mul
    add d ;y*screeWidth+x
    mov b, a
    rrc
    rrc
    ani 0b00111111
    lxi h, 0x8000
    mov l, a
    mov a, b
    ani 0b11
    mov b, a
    mvi a, 3
    sub b
    rlc
    ani 0b11111110
    mov b, a
    mov d, a
    mvi a, 0b11111100
    call lshift
    mov c, m
    ana c
    mov c, a
    mov a, e
    mov b, d
    call lshift
    ora c
    mov m, a
    ret

;params: A, B
;returns A << B
lshift:
    rlc
    dcr b
    jnz lshift
    ret


;params: B, C
;returns B * C -> A
mul:
    add c
    dcr b
    jnz mul
    ret

;params: B, C
;returns (B << 4) | C -> B
compact:
    mov a, b
    rlc
    rlc
    rlc
    rlc
    add c
    mov b, a
    ret
;params: B
;returns (B >> 4) & 0x0f -> B, B & 0x0f -> C
uncompact:
    mov c, b
    mov a, b
    rrc
    rrc
    rrc
    rrc
    ani 0x0f
    mov b, a
    mov a, c
    ani 0x0f
    mov c, a
    ret


