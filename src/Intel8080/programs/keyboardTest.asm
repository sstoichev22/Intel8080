org 0x0

call waitKey
hlt

waitKey:
    in 0x1
    ani 1
    jz waitKey
    in 0x0
    out 0x2
    ret