; Intel 8080 exhaustive conformance test
; Memory map:
; 0000-00FF RST vectors
; 0100-7FFF program
; 8000-80FF reserved for video
; 8100-EFFF test RAM
; F000-FFFF stack
; SP is initialized to FF00.
; Canonical 8080 opcodes only; the 12 alternate * opcodes are excluded.

start:
    LXI SP, 0xff00
    LXI B, 0x8100
    LXI D, 0x8101
    LXI H, 0x8102
    MVI A, 0x5a
    MVI B, 0x11
    MVI C, 0x22
    MVI D, 0x33
    MVI E, 0x44
    MVI H, 0x81
    MVI L, 0x02
    STAX B
    STAX D
    LDAX B
    LDAX D
    INX B
    INX D
    DCX B
    DCX D
    LXI H, 0x8102
    SHLD 0x8104
    LHLD 0x8104
    LXI SP, 0xff00
    INX SP
    DCX SP
    INX H
    DCX H
    INR B
    DCR B
    INR C
    DCR C
    INR D
    DCR D
    INR E
    DCR E
    INR H
    DCR H
    INR L
    DCR L
    LXI H, 0x8102
    MVI M, 0x7f
    INR M
    DCR M
    INR A
    DCR A
    LXI B, 0x1234
    LXI D, 0x2345
    LXI H, 0x3456
    DAD B
    DAD D
    DAD H
    DAD SP
    LXI H, 0x8102
    MVI M, 0x3c
    LXI H, 0x8102
    MVI A, 0x99
    STA 0x8106
    LDA 0x8106
    RLC
    RRC
    RAL
    RAR
    CMA
    STC
    CMC
    MVI A, 0x09
    ADI 0x09
    DAA
    MVI B, 0x10
    MVI C, 0x20
    MVI D, 0x30
    MVI E, 0x40
    MVI H, 0x50
    MVI L, 0x60
    LXI H, 0x8102
    MVI M, 0x70
    MVI A, 0x80
    MOV B, B
    MOV B, C
    MOV B, D
    MOV B, E
    MOV B, H
    MOV B, L
    MOV B, M
    MOV B, A
    MOV C, B
    MOV C, C
    MOV C, D
    MOV C, E
    MOV C, H
    MOV C, L
    MOV C, M
    MOV C, A
    MOV D, B
    MOV D, C
    MOV D, D
    MOV D, E
    MOV D, H
    MOV D, L
    MOV D, M
    MOV D, A
    MOV E, B
    MOV E, C
    MOV E, D
    MOV E, E
    MOV E, H
    MOV E, L
    MOV E, M
    MOV E, A
    MOV H, B
    MOV H, C
    MOV H, D
    MOV H, E
    MOV H, H
    MOV H, L
    MOV H, M
    MOV H, A
    MOV L, B
    MOV L, C
    MOV L, D
    MOV L, E
    MOV L, H
    MOV L, L
    MOV L, M
    MOV L, A
    MOV M, B
    MOV M, C
    MOV M, D
    MOV M, E
    MOV M, H
    MOV M, L
    MOV M, A
    MOV A, B
    MOV A, C
    MOV A, D
    MOV A, E
    MOV A, H
    MOV A, L
    MOV A, M
    MOV A, A
    MVI A, 0x55
    MVI B, 0x11
    MVI C, 0x22
    MVI D, 0x33
    MVI E, 0x44
    MVI H, 0x81
    MVI L, 0x02
    LXI H, 0x8102
    MVI M, 0x66
    ADD B
    ADD C
    ADD D
    ADD E
    ADD H
    ADD L
    ADD M
    ADD A
    MVI A, 0x55
    MVI B, 0x11
    MVI C, 0x22
    MVI D, 0x33
    MVI E, 0x44
    MVI H, 0x81
    MVI L, 0x02
    LXI H, 0x8102
    MVI M, 0x66
    ADC B
    ADC C
    ADC D
    ADC E
    ADC H
    ADC L
    ADC M
    ADC A
    MVI A, 0x55
    MVI B, 0x11
    MVI C, 0x22
    MVI D, 0x33
    MVI E, 0x44
    MVI H, 0x81
    MVI L, 0x02
    LXI H, 0x8102
    MVI M, 0x66
    SUB B
    SUB C
    SUB D
    SUB E
    SUB H
    SUB L
    SUB M
    SUB A
    MVI A, 0x55
    MVI B, 0x11
    MVI C, 0x22
    MVI D, 0x33
    MVI E, 0x44
    MVI H, 0x81
    MVI L, 0x02
    LXI H, 0x8102
    MVI M, 0x66
    SBB B
    SBB C
    SBB D
    SBB E
    SBB H
    SBB L
    SBB M
    SBB A
    MVI A, 0x55
    MVI B, 0x11
    MVI C, 0x22
    MVI D, 0x33
    MVI E, 0x44
    MVI H, 0x81
    MVI L, 0x02
    LXI H, 0x8102
    MVI M, 0x66
    ANA B
    ANA C
    ANA D
    ANA E
    ANA H
    ANA L
    ANA M
    ANA A
    MVI A, 0x55
    MVI B, 0x11
    MVI C, 0x22
    MVI D, 0x33
    MVI E, 0x44
    MVI H, 0x81
    MVI L, 0x02
    LXI H, 0x8102
    MVI M, 0x66
    XRA B
    XRA C
    XRA D
    XRA E
    XRA H
    XRA L
    XRA M
    XRA A
    MVI A, 0x55
    MVI B, 0x11
    MVI C, 0x22
    MVI D, 0x33
    MVI E, 0x44
    MVI H, 0x81
    MVI L, 0x02
    LXI H, 0x8102
    MVI M, 0x66
    ORA B
    ORA C
    ORA D
    ORA E
    ORA H
    ORA L
    ORA M
    ORA A
    MVI A, 0x55
    MVI B, 0x11
    MVI C, 0x22
    MVI D, 0x33
    MVI E, 0x44
    MVI H, 0x81
    MVI L, 0x02
    LXI H, 0x8102
    MVI M, 0x66
    CMP B
    CMP C
    CMP D
    CMP E
    CMP H
    CMP L
    CMP M
    CMP A
    ADI 0x11
    ACI 0x22
    SUI 0x05
    SBI 0x03
    ANI 0xf0
    XRI 0x0f
    ORI 0x80
    CPI 0x7f
    LXI B, 0x1122
    LXI D, 0x3344
    LXI H, 0x5566
    PUSH B
    PUSH D
    PUSH H
    POP H
    POP D
    POP B
    MVI A, 0xa5
    STC
    PUSH PSW
    POP PSW
    LXI H, 0xff00
    MVI M, 0x00
    INX H
    MVI M, 0xff
    LXI H, 0x7788
    XTHL
    XCHG
    LXI H, 0xf100
    SPHL
    LXI SP, 0xff00
    DI
    EI
    MVI A, 0xa6
    OUT 0x01
    IN 0x01
    MVI A, 0x01
    ORA A
    JNZ cf_cond_nz
cf_cond_nz:
    NOP
    XRA A
    JZ cf_cond_z
cf_cond_z:
    NOP
    XRA A
    JNC cf_cond_nc
cf_cond_nc:
    NOP
    MVI A, 0x01
    ADD A
    MVI A, 0xff
    ADI 0x01
    JC cf_cond_c
cf_cond_c:
    NOP
    MVI A, 0x01
    ORA A
    JPO cf_cond_po
cf_cond_po:
    NOP
    XRA A
    JPE cf_cond_pe
cf_cond_pe:
    NOP
    MVI A, 0x01
    ORA A
    JP cf_cond_p
cf_cond_p:
    NOP
    MVI A, 0x80
    ORA A
    JM cf_cond_m
cf_cond_m:
    NOP
    MVI A, 0x01
    ORA A
    JMP after_jump
after_jump:
    MVI A, 0x01
    ORA A
    CNZ sub_CNZ
after_CNZ:
    NOP
    XRA A
    CZ sub_CZ
after_CZ:
    NOP
    XRA A
    CNC sub_CNC
after_CNC:
    NOP
    MVI A, 0x01
    ADD A
    MVI A, 0xff
    ADI 0x01
    CC sub_CC
after_CC:
    NOP
    MVI A, 0x01
    ORA A
    CPO sub_CPO
after_CPO:
    NOP
    XRA A
    CPE sub_CPE
after_CPE:
    NOP
    MVI A, 0x01
    ORA A
    CP sub_CP
after_CP:
    NOP
    MVI A, 0x80
    ORA A
    CM sub_CM
after_CM:
    NOP
    MVI A, 0x01
    ORA A
    CALL sub_CALL
after_CALL:
    NOP
    RST 0
    RST 1
    RST 2
    RST 3
    RST 4
    RST 5
    RST 6
    RST 7
    JMP pchl_entry
enter_RNZ:
    MVI A, 0x01
    ORA A
    RNZ
    MVI A, 0xee
    STA 0x8210
enter_RZ:
    XRA A
    RZ
    MVI A, 0xee
    STA 0x8210
enter_RNC:
    XRA A
    RNC
    MVI A, 0xee
    STA 0x8210
enter_RC:
    MVI A, 0x01
    ADD A
    MVI A, 0xff
    ADI 0x01
    RC
    MVI A, 0xee
    STA 0x8210
enter_RPO:
    MVI A, 0x01
    ORA A
    RPO
    MVI A, 0xee
    STA 0x8210
enter_RPE:
    XRA A
    RPE
    MVI A, 0xee
    STA 0x8210
enter_RP:
    MVI A, 0x01
    ORA A
    RP
    MVI A, 0xee
    STA 0x8210
enter_RM:
    MVI A, 0x80
    ORA A
    RM
    MVI A, 0xee
    STA 0x8210
pchl_entry:
    MVI A, 0x01
    ORA A
    LXI H, pchl_target
    PCHL
pchl_target:
    NOP
sub_RET:
    RET
sub_CNZ:
    RET
sub_CZ:
    RET
sub_CNC:
    RET
sub_CC:
    RET
sub_CPO:
    RET
sub_CPE:
    RET
sub_CP:
    RET
sub_CM:
    RET
sub_CALL:
    RET
run_cond_rets:
    MVI A, 0x01
    ORA A
    CALL enter_RNZ
after_ret_RNZ:
    NOP
    XRA A
    CALL enter_RZ
after_ret_RZ:
    NOP
    XRA A
    CALL enter_RNC
after_ret_RNC:
    NOP
    MVI A, 0x01
    ADD A
    MVI A, 0xff
    ADI 0x01
    CALL enter_RC
after_ret_RC:
    NOP
    MVI A, 0x01
    ORA A
    CALL enter_RPO
after_ret_RPO:
    NOP
    XRA A
    CALL enter_RPE
after_ret_RPE:
    NOP
    MVI A, 0x01
    ORA A
    CALL enter_RP
after_ret_RP:
    NOP
    MVI A, 0x80
    ORA A
    CALL enter_RM
after_ret_RM:
    NOP
    MVI A, 0xa5
    STA 0x8200
    MVI A, 0x80
    STA 0x8201
    MVI A, 0x80
    STA 0x8202
    MVI A, 0x55
    STA 0x8203
    STOP
; RST vectors (must be loaded at absolute addresses):
; 0000: C9, 0008: C9, 0010: C9, 0018: C9, 0020: C9, 0028: C9, 0030: C9, 0038: C9
