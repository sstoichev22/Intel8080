; Snake for Vulcan's "16x16 Video 2-bit" Display preset.
; Video: one byte per pixel at 0x9000 + y*16 + x, using colors 0..3.
; Colors: 0=background, 1=snake, 2=food, 3=wall.
; Click Run in Display, then use WASD (upper/lower case). Run restarts.
; IN 1 bit 0 reports a pending key; IN 0 reads/consumes its ASCII byte.
; Movement is about twice per second at the CPU's 2 MHz clock.
; Coordinates in the snake list use (x << 4) | y; the playable grid is 14x14.
screenWidth equ 16
VIDEO_MEM_START equ 0x9000
MAX_LENGTH equ 196

.data
len: db 3
dir: db 3 ;0=up, 1=right, 2=down, 3=left
randCounter: db 0
foodCoord: db 0x22
snake: ds 196
growing: db 0

.code
    call initSnake
    call renderFrame

; Wait, consume the latest turn, move once, then draw the completed state.
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
    jz gameOver ;a full board is a win; do not search for nonexistent free food
    jmp gameLoop

; DE = milliseconds. Clobbers A, flags and BC; returns with DE = 0.
; 82 inner iterations and the surrounding instructions take about 1 ms at 2 MHz.
delayms:
    mov a, d
    ora e
    rz
delayms_millisecond:
    lxi b, 82
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

; Consume one ASCII key. Each accepted turn returns immediately, and a reversal
; into the snake's neck is ignored. H points to dir and D holds the old direction.
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

; Set upward movement unless the current movement is downward.
setDirUp:
    mov a, d
    cpi 2
    rz
    mvi m, 0
    ret

; Set leftward movement unless the current movement is rightward.
setDirLeft:
    mov a, d
    cpi 1
    rz
    mvi m, 3
    ret

; Set downward movement unless the current movement is upward.
setDirDown:
    mov a, d
    cpi 0
    rz
    mvi m, 2
    ret

; Set rightward movement unless the current movement is leftward.
setDirRight:
    mov a, d
    cpi 3
    rz
    mvi m, 1
    ret

; Select exactly one movement routine. Conditional calls would let a routine's
; changed accumulator accidentally participate in the next direction comparison.
moveInDirection:
    lda dir
    cpi 0
    jz moveUp
    cpi 1
    jz moveRight
    cpi 2
    jz moveDown
    jmp moveLeft

; Compute the head one row above, then validate and insert it.
moveUp:
    lda snake
    sui 1
    mov b, a
    jmp addSnakeHead

; Compute the head one column to the right, then validate and insert it.
moveRight:
    lda snake
    adi 0x10
    mov b, a
    jmp addSnakeHead

; Compute the head one row below, then validate and insert it.
moveDown:
    lda snake
    adi 1
    mov b, a
    jmp addSnakeHead

; Compute the head one column to the left, then validate and insert it.
moveLeft:
    lda snake
    sui 0x10
    mov b, a
    jmp addSnakeHead

; B = proposed head. Check walls, decide growth, and reject occupied cells.
; A non-growing move may enter the old tail because that cell vacates this tick.
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
; Carry the previous segment in B. Write exactly len cells, or len+1 on growth;
; never temporarily extend the array or compute a pointer with an 8-bit addition.
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

; B = packed coordinate. Leaves B unchanged; halts before any invalid move.
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

; Search all 256 candidate coordinates in a repeatable permuted order. Adding an
; odd value visits every byte, so a free cell is always found before repeating.
moveFood:
    lxi h, randCounter
    mov a, m
    adi 73
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

; Initialize the three segments at (8,8), (9,8), (10,8), facing left.
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

; Redraw a complete small frame, avoiding stale tail pixels after growth or turns.
; B starts at zero deliberately: decrementing it produces exactly 256 iterations.
renderFrame:
    lxi h, VIDEO_MEM_START
    mvi b, 0
clearFrameLoop:
    mvi m, 0
    inx h
    dcr b
    jnz clearFrameLoop
    call initBoard
    call showFood
    call showSnake
    ret

; Draw food, except after the snake fills the board.
showFood:
    lda len
    cpi MAX_LENGTH
    rz
    lda foodCoord
    mov b, a
    call uncompact
    mvi d, 2
    jmp setPixel

; Draw every segment; preserve the list pointer and remaining count per pixel.
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

; B=x, C=y, D=color (0..3). Writes one byte per pixel, as Display expects.
; Preserves BC and DE; clobbers A, flags and HL. Coordinates are within 0..15.
setPixel:
    mov a, c
    rlc
    rlc
    rlc
    rlc
    ora b
    lxi h, VIDEO_MEM_START
    mov l, a
    mov m, d
    ret

; B=(x<<4)|y -> B=x, C=y. Preserves DE and HL; clobbers A and flags.
uncompact:
    mov a, b
    ani 0x0f
    mov c, a
    mov a, b
    rrc
    rrc
    rrc
    rrc
    ani 0x0f
    mov b, a
    ret

; Report final length on port 3 and keep the last valid frame visible.
gameOver:
    lda len
    out 0x03
    hlt
    jmp gameOver

; Draw all four walls around the 14x14 playing area.
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

; Draw E horizontal wall pixels starting at (B,C); setPixel preserves the loop.
boardHorizontal:
    call setPixel
    inr b
    dcr e
    jnz boardHorizontal
    ret

; Draw E vertical wall pixels starting at (B,C).
boardVertical:
    call setPixel
    inr c
    dcr e
    jnz boardVertical
    ret
