screenWidth equ 16
VIDEO_MEM_START equ 0xF0000000
MAX_LENGTH equ 196

.data
len: db 3
dir: db 3
randCounter: db 0
foodCoord: db 0x22
snake: ds 196
growing: db 0

.text
    call initSnake
    call renderFrame

gameLoop:
    lxi h, randCounter
    inr m
    lxi d, 500
    call delayms
    call handleInput
    call moveInDirection
    call renderFrame
    lda len
    cpi MAX_LENGTH
    jz gameOver
    jmp gameLoop

delayms:
    mov a, d
    ora e
    rz
delayms_millisecond:
    lxi b, 4167
delayms_loop:
    dcx b
    mov a, b
    ora c
    jnz delayms_loop
    dcx d
    mov a, d
    ora e
    jnz delayms_millisecond
    ret

handleInput:
    lxi h, dir
    mov d, m
    in 0x01
    ani 1
    rz
    in 0x00
    cpi 'w'
    jz setDirUp
    cpi 'W'
    jz setDirUp
    cpi 'a'
    jz setDirLeft
    cpi 'A'
    jz setDirLeft
    cpi 's'
    jz setDirDown
    cpi 'S'
    jz setDirDown
    cpi 'd'
    jz setDirRight
    cpi 'D'
    jz setDirRight
    ret

setDirUp:
    mov a, d
    cpi 2
    rz
    mvi m, 0
    ret

setDirLeft:
    mov a, d
    cpi 1
    rz
    mvi m, 3
    ret

setDirDown:
    mov a, d
    cpi 0
    rz
    mvi m, 2
    ret

setDirRight:
    mov a, d
    cpi 3
    rz
    mvi m, 1
    ret

moveInDirection:
    lda dir
    cpi 0
    jz moveUp
    cpi 1
    jz moveRight
    cpi 2
    jz moveDown
    jmp moveLeft

moveUp:
    lda snake
    sui 1
    mov b, a
    jmp addSnakeHead

moveRight:
    lda snake
    adi 0x10
    mov b, a
    jmp addSnakeHead

moveDown:
    lda snake
    adi 1
    mov b, a
    jmp addSnakeHead

moveLeft:
    lda snake
    sui 0x10
    mov b, a
    jmp addSnakeHead

addSnakeHead:
    call checkSnakeBounds
    mvi a, 0
    sta growing
    lda foodCoord
    cmp b
    jnz checkBody
    mvi a, 1
    sta growing
checkBody:
    lda len
    mov c, a
    lda growing
    ora a
    jnz checkBodyStart
    dcr c
checkBodyStart:
    lxi h, snake
checkBodyLoop:
    mov a, m
    cmp b
    jz gameOver
    inx h
    dcr c
    jnz checkBodyLoop

    lda len
    mov d, a
    lda growing
    ora a
    jz shiftSnake
    inr d
shiftSnake:
    lxi h, snake

shiftSnakeLoop:
    mov c, m
    mov m, b
    mov b, c
    inx h
    dcr d
    jnz shiftSnakeLoop
    lda growing
    ora a
    rz
    lxi h, len
    inr m
    mov a, m
    cpi MAX_LENGTH
    rz
    jmp moveFood

checkSnakeBounds:
    mov a, b
    ani 0x0f
    jz gameOver
    cpi 0x0f
    jz gameOver
    mov a, b
    ani 0xf0
    jz gameOver
    cpi 0xf0
    jz gameOver
    ret

moveFood:
    lxi h, randCounter
    mov a, m
    adi 73
    ani 0xff
    mov m, a
    mov b, a
    ani 0x0f
    jz moveFood
    cpi 0x0f
    jz moveFood
    mov a, b
    ani 0xf0
    jz moveFood
    cpi 0xf0
    jz moveFood
    lda len
    mov c, a
    lxi h, snake
foodBodyLoop:
    mov a, m
    cmp b
    jz moveFood
    inx h
    dcr c
    jnz foodBodyLoop
    mov a, b
    sta foodCoord
    ret

initSnake:
    lda len
    mov c, a
    lxi h, snake
    mvi b, 0x88
initSnakeLoop:
    mov m, b
    inx h
    mov a, b
    adi 0x10
    mov b, a
    dcr c
    jnz initSnakeLoop
    ret

renderFrame:
    lxi h, VIDEO_MEM_START
    mvi b, 256
clearFrameLoop:
    mvi m, 0
    inx h
    dcr b
    jnz clearFrameLoop
    call initBoard
    call showFood
    call showSnake
    ret

showFood:
    lda len
    cpi MAX_LENGTH
    rz
    lda foodCoord
    mov b, a
    call uncompact
    mvi d, 2
    jmp setPixel

showSnake:
    lda len
    mov e, a
    lxi h, snake
showSnakeLoop:
    push h
    mov b, m
    call uncompact
    mvi d, 1
    call setPixel
    pop h
    inx h
    dcr e
    jnz showSnakeLoop
    ret

setPixel:
    push d
    mov a, c
    rlc 4
    ora b
    mov e, a
    mvi d, 0
    lxi h, VIDEO_MEM_START
    dad d
    pop d
    mov m, d
    ret

uncompact:
    mov a, b
    ani 0x0f
    mov c, a
    mov a, b
    rrc 4
    ani 0x0f
    mov b, a
    ret

gameOver:
    lda len
    out 0x03
    stop

initBoard:
    mvi b, 0
    mvi c, 0
    mvi d, 3
    mvi e, 16
    call boardHorizontal
    mvi b, 0
    mvi c, 15
    mvi e, 16
    call boardHorizontal
    mvi b, 0
    mvi c, 0
    mvi e, 16
    call boardVertical
    mvi b, 15
    mvi c, 0
    mvi e, 16
    jmp boardVertical

boardHorizontal:
    call setPixel
    inr b
    dcr e
    jnz boardHorizontal
    ret

boardVertical:
    call setPixel
    inr c
    dcr e
    jnz boardVertical
    ret
