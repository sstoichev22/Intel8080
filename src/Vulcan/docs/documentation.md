# Vulcan 0.2.0

Vulcan is an Intel 8080 assembly editor, assembler, debugger, and memory display.

## Editor

Double-click a file in Files to open it. If it is already open, Vulcan selects its existing tab.
Drag a tab to a highlighted edge to split the editor, or outside the window to detach it.
Right-click a tab for close options. Files are saved when closing the application.
EXE files contain raw executable bytes; their character view is read-only.

Type an instruction prefix for autocomplete. Up/Down select a suggestion; Enter accepts it.
Labels and EQU names declared in the current file are included. Instruction suggestions follow the typed case.
Use View to hide or reveal regions in the current workspace.

## Running and debugging

Run executes the current ASM or EXE. Stop ends execution. Debug opens a supported file in Debugger.
The debugger highlights the next instruction. Step executes one whole instruction, including its operands.
STOP leaves PC on the terminating instruction; Reset restores the entry address so you can step again.
HLT is the processor's interrupt-wait state and retains its frame.

One processor, memory, and 2 MHz clock worker belong to the application. Starting another program replaces
the active execution. Loading replaces only the program's address range; bytes outside it remain in memory.
Each workspace keeps its own console output until a new run, a console visibility toggle, or application close.

## Terminal

From the directory containing your source:

```text
asm ascii-test.asm
./ascii-test
```

The assembler writes ascii-test.exe beside the source. Its OUT 0 bytes appear in Terminal.
Use `asm file.asm -n name` for a different executable name. `help` lists available commands.

## Text input and output

`OUT 0` sends the accumulator byte to the program's output destination.
`IN 1` bit 0 indicates unread input; `IN 0` reads that byte and acknowledges it.
The console input field sends a complete line when Enter is pressed, ending with LF (10).
Queued input is delivered one byte at a time so characters are not lost.

```asm
poll:   in 1
        ani 1
        jz poll
        in 0
        out 0
        cpi 10
        jnz poll
        stop
```

This program echoes one input line. Text programs should check readiness before reading input.
For a game, click Display's screen and type WASD. Screen input keeps the latest key rather than queuing stale turns.

## Display and settings

Display reads emulated memory using its selected address, dimensions, bit depth, and color mapping.
The default Snake preset reads one pixel per byte at 0x9000 on a 16 by 16 screen.

Settings opens settings.json. It stores theme colors, font sizes, open files, selected tabs, scroll positions,
window geometry, and editor divider positions. The `escape` color controls string escape highlighting.
Java2D Direct3D is disabled by default, matching the rendering setting tested on this machine.

Only ASM and EXE currently execute; C is reserved for future support.
