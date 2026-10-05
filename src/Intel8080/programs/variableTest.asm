n equ 3

.data
arr: db 1, 2, 3

.code

mvi b, n
lxi h, arr

loop:
	mov a, m
	out 0x0
	inx h
	dcr b
	jnz loop
stop
	


