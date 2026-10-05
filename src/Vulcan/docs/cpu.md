# Emulated processor

Vulcan's processor has Intel 8080 byte registers A, B, C, D, E, H, and L;
register pairs BC, DE, and HL; a 16-bit program counter; and a stack pointer.
It tracks sign, zero, auxiliary carry, parity, and carry flags.

The emulated clock is 2,000,000 cycles per second. Instructions consume their cycle budgets;
conditional branches use the applicable taken/not-taken timing.
The clock worker stays available between runs and waits while no program executes.

## Address space

| Region | Addresses |
| --- | --- |
| Restart vectors | 0x0000-0x00FF |
| Default program load region | 0x0100-0x7FFF |
| RAM | 0x8000-0x8FFF |
| Video memory | 0x9000-0xEFFF |
| Reserved stack | 0xF000-0xFFFF |

Memory is 64 KiB. Programs can use assembler origins; execution starts at their code entry address.
Replacing a program does not erase bytes outside the new loaded range.
The stack grows downward and rejects overflow/underflow.

`OUT 0` is program output. `IN 0` consumes keyboard/text input; `IN 1` bit 0 reports readiness.
STOP (0xFD) is Vulcan's custom program termination opcode. HLT waits for an enabled interrupt.
The explicit instruction handlers and opcode metadata remain in the Intel8080 CPU and assembler packages.
