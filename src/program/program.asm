; Simple addition program for Intel 8080
; Adds 5 + 7 and stores the result at address 0xC000

        MVI A, 05H      ; Load 5 into accumulator (A)
        MVI B, 07H      ; Load 7 into register B
        ADD B           ; A = A + B (A = 12)
        STA 0C00H       ; Store A into memory address 0xC000
        HLT             ; Stop execution
