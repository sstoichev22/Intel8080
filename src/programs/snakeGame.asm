;this program will deal with the list for snake
;each coord is in form
;(x << 4) | y
;max length is 196, 14x14 playable grid.
org 0x7000
snake: ds 196

org 0x3000
screenWidth: db 16
len: db 3 ;whatever default val
dir: db 3 ;0=up, 1=right, 2=down, 3=left
randCounter: db 0
foodCoord: db 0b00100010

org 0x0
call initSnake
call initBoard
call gameLoop
hlt

;params:
;returns: loop until snake dies or fills screen
gameLoop:
    lxi h, randCounter
    inr m

    in 0x2
    ani 1
    jz gameLoop

    call handleInput
    call handleFood
    call moveInDirection
    call removeSnakeBack


    call showFood
    call showSnake

    mvi b, 0
    mvi c, 0
    mvi d, 0b11
    call setPixel

    jmp gameLoop
    hlt


handleFood:
    lxi h, snake
    mov b, m
    lxi h, foodCoord
    mov a, m
    cmp b
    cz moveFood
    ret

moveFood:
    lxi h, randCounter
    inr m
    mov b, m
    call checkFoodBounds
    cpi 1
    jz moveFood
    lxi h, foodCoord
    mov m, b
    ret

;params: B(x<<4|y)
;returns A <- 0 for good, 1 for bad
checkFoodBounds:
    call uncompact
    mov a, b
    cpi 0
    jz badFood
    cpi 0xf
    jz badFood
    mov a, c
    cpi 0
    jz badFood
    cpi 0xf
    jz badFood
    call compact

    lxi h, len
    mov d, m
    lxi h, snake
    call _checkFoodBoundsLoop

    ret

_checkFoodBoundsLoop:
    mov a, m
    cmp b
    jz badFood
    inx h
    dcr d
    jz goodFood
    jmp _checkFoodBoundsLoop

badFood:
    mvi a, 1
    ret
goodFood:
    mvi a, 0
    ret

showFood:
    lxi h, foodCoord
    mov b, m
    call uncompact
    mvi d, 0b10
    call setPixel
    ret


moveInDirection:
    lxi h, dir
    mov a, m
    cpi 0
    cz moveUp
    cpi 1
    cz moveRight
    cpi 2
    cz moveDown
    cpi 3
    cz moveLeft
    ret

;params: key press
;returns snake moved in direction of key
;details: W:up, A:left, S:down, D:right
handleInput:
    lxi h, dir
    mov d, m
    in 0x1
    ani 1
    rz

    in 0x0

    cpi 'w'
    cz setDirUp
    cpi 'W'
    cz setDirUp

    cpi 'a'
    cz setDirLeft
    cpi 'A'
    cz setDirLeft

    cpi 's'
    cz setDirDown
    cpi 'S'
    cz setDirDown

    cpi 'd'
    cz setDirRight
    cpi 'D'
    cz setDirRight
    ret

setDirUp:
    mov a, d
    cpi 0
    rz
    cpi 2
    rz

    mvi m, 0
    ret

moveUp:
    lxi h, snake
    mov b, m
    call uncompact
    mov a, c
    sui 0b0001
    ani 0x0f
    mov c, a
    call compact
    call addSnakeHead
    ret

setDirLeft:
    mov a, d
    cpi 1
    rz
    cpi 3
    rz

    mvi m, 3
    ret

moveLeft:
    lxi h, snake
    mov b, m
    call uncompact
    mov a, b
    sui 0b0001
    ani 0x0f
    mov b, a
    call compact
    call addSnakeHead
    ret

setDirDown:
    mov a, d
    cpi 0
    rz
    cpi 2
    rz

    mvi m, 2
    ret

moveDown:
    lxi h, snake
    mov b, m
    call uncompact
    mov a, c
    adi 0b0001
    ani 0x0f
    mov c, a
    call compact
    call addSnakeHead
    ret

setDirRight:
    mov a, d
    cpi 1
    rz
    cpi 3
    rz

    mvi m, 1
    ret

moveRight:
    lxi h, snake
    mov b, m
    call uncompact
    mov a, b
    adi 0b0001
    ani 0x0f
    mov b, a
    call compact
    call addSnakeHead
    ret

;params: [len]->B, A->0, snake->H
;returns the snake onto the screen
showSnake:
    lxi h, len
    mov b, m
    mvi a, 0
    lxi h, snake
    call _showSnakeLoop
    ret

    _showSnakeLoop:
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
        rz
        jmp _showSnakeLoop

;params:
;returns snake initialized starting at (8,8) and adds [len] amount to the right
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

;params: B(x<<4|y)
;returns snake pointing to B, and old snake trailing with the last value removed
addSnakeHead:
    call checkSnakeBounds
    call checkSnakeEat
    call checkSnakeEatItselfBounds
    lxi h, len
    inr m
    mov d, m
    lxi h, snake
    call _addSnakeHeadLoop
    ret

_addSnakeHeadLoop:
    mov c, m
    mov m, b
    mov b, c
    inx h
    dcr d
    rz
    jmp _addSnakeHeadLoop

checkSnakeEatItselfBounds:
    lxi h, len
    mov c, m
    lxi h, snake
    call _checkSnakeEatItselfBoundsLoop
    ret

_checkSnakeEatItselfBoundsLoop:
    mov a, m
    cmp b
    cz gameOver
    inx h
    dcr c
    rz
    jmp _checkSnakeEatItselfBoundsLoop

;params: B(x<<4|y)
;returns
checkSnakeEat:
    lxi h, foodCoord
    mov a, m
    cmp b
    cz snakeEat
    ret

snakeEat:
    lxi h, len
    inr m
    ret

;params B(x<<4|y)
checkSnakeBounds:
    call uncompact
    mov a, b
    cpi 0
    cz gameOver
    cpi 0xf
    cz gameOver
    mov a, c
    cpi 0
    cz gameOver
    cpi 0xf
    cz gameOver
    call compact
    ret


;params:
;returns [snake+len] -> 0
removeSnakeBack:
    lxi h, len
    dcr m
    mov a, m
    lxi h, snake
    add l
    mov l, a
    mov b, m
    mvi m, 0
    call uncompact
    mvi d, 0b00
    call setPixel
    ret



;params: B=x,C=y,D=color
;returns a pixel on the screen at (x,y) as color
setPixel:
    push b
    push d
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
    pop d
    pop b
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


gameOver:
    lxi h, len
    mov a, m
    out 0x3
    hlt

initBoard:
    mvi b, 0
    mvi c, 0
    mvi d, 0b11
    mvi e, 0x10
    call _initBoardLoopX
    mvi b, 0
    mvi c, 0xf
    mvi d, 0b11
    mvi e, 0x10
    call _initBoardLoopX
    mvi b, 0
    mvi c, 0
    mvi d, 0b11
    mvi e, 0x10
    call _initBoardLoopY
    mvi b, 0xf
    mvi c, 0
    mvi d, 0b11
    mvi e, 0xf
    call _initBoardLoopY

    ret

_initBoardLoopX:
    push b
    push d
    call setPixel
    pop d
    pop b
    inr b
    dcr e
    rz
    jmp _initBoardLoopX

_initBoardLoopY:
    push b
    push d
    call setPixel
    pop d
    pop b
    inr c
    dcr e
    rz
    jmp _initBoardLoopY