# Intel8080

A small Intel 8080 assembler and CPU emulator in Java, extended to a wide ISA. It is the core that the other Vulcan projects build on.

## Differences from a stock 8080

| | Intel 8080 | This project |
| --- | --- | --- |
| Registers | 8-bit A-L | 32-bit A-L |
| Register pairs | 16-bit BC/DE/HL | 64-bit BC/DE/HL |
| PC / SP | 16-bit | 64-bit |
| Memory | 64 KB | Sparse flat address space, display RAM at `0xF0000000` |
| Extra instructions | none | `STOP`, `LDBC`/`STBC`, `LDBC32`/`STBC32`, `ADDBC32`/`SUBBC32`, counted `RLC`/`RRC`/`RAL`/`RAR n` |
| Data directives | `DB`, `DW`, `DS`, `ORG` | adds `DD` and `DQ` |

Opcodes keep their original mnemonics; case-insensitive, with case-sensitive labels, strings and chars. Full reference: [wide-isa.md](../vulcanide/docs/wide-isa.md).

## Build and run

Needs Java 17+. From the `qa` repo (cloned next to this one):

```powershell
..\qa\build.ps1                  # writes builds\intel8080.jar
java -jar builds\intel8080.jar programs\asm\ascii-test.asm
java -jar builds\intel8080.jar programs\asm\snakeGame.asm 50000000   # optional instruction limit
```

The program is assembled and run until `STOP` or `HLT`. `OUT 0` prints to the console. The default limit is 500,000,000 instructions.

## Example

```asm
        ORG 0
        MVI A, 'H'
        OUT 0
        MVI A, 'i'
        OUT 0
        STOP
```

More in [programs/asm](programs/asm): `fib`, `mul`, `snakeGame`, `ansi-demo`, `displayTest`, `keyboardTest` and more.

## Layout

- `src/Intel8080/assembler` - two-pass assembler
- `src/Intel8080/cpu` - CPU, memory, opcode handlers
- `src/Intel8080/main` - command-line entry point
- `programs/asm` - sample programs
