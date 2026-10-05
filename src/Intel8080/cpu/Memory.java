package Intel8080.cpu;

import java.util.Arrays;

/** 64 KiB 8080 address space with named regions and a bounded downward-growing stack. */
public class Memory {
    public static final int MEM_SIZE = 0x10000;

    public static final int RST_VECTORS_START = 0x0000;
    public static final int RST_VECTORS_END = 0x00FF;
    public static final int PROGRAM_START = 0x0100;
    public static final int PROGRAM_END = 0x7FFF;
    public static final int RAM_START = 0x8000;
    public static final int RAM_END = 0x8FFF;
    public static final int VIDEO_START = 0x9000;
    public static final int VIDEO_END = 0xEFFF;
    public static final int STACK_START = 0xF000;
    public static final int STACK_END = 0xFFFF;

    private final byte[] memory = new byte[MEM_SIZE];
    private int sp = STACK_END;

    /** Reads a byte from the 16-bit address bus, wrapping addresses to 0x0000–0xFFFF. */
    public synchronized byte get(int address) { return memory[address & 0xFFFF]; }
    /** Writes a byte to the 16-bit address bus, wrapping addresses to 0x0000–0xFFFF. */
    public synchronized void set(int address, byte value) { memory[address & 0xFFFF] = value; }

    /** Pushes one byte onto the reserved stack region and rejects overflow. */
    public synchronized void push(byte value) {
        if (sp <= STACK_START) {
            throw new IllegalStateException(String.format(
                    "Stack overflow: SP=0x%04X, valid stack=0x%04X-0x%04X",
                    sp, STACK_START, STACK_END));
        }
        memory[--sp] = value;
    }

    /** Pops one byte from the reserved stack region and rejects underflow. */
    public synchronized byte pop() {
        if (sp >= STACK_END) {
            throw new IllegalStateException(String.format(
                    "Stack underflow: SP=0x%04X, valid stack=0x%04X-0x%04X",
                    sp, STACK_START, STACK_END));
        }
        return memory[sp++];
    }

    /** Adjusts the stack pointer while enforcing the reserved stack region. */
    public synchronized void incSp(int value) { setSp(sp + value); }

    /** Sets the stack pointer to an address inside the reserved stack region. */
    public synchronized void setSp(int address) {
        if (address < STACK_START || address > STACK_END) {
            throw new IllegalArgumentException(String.format(
                    "Stack pointer out of bounds: 0x%04X, valid range=0x%04X-0x%04X",
                    address & 0xFFFF, STACK_START, STACK_END));
        }
        sp = address;
    }

    /** Returns the current stack pointer. */
    public synchronized int getSp() { return sp; }

    /** Loads a full 64 KiB image, or raw executable bytes at PROGRAM_START. */
    public synchronized void loadRom(byte[] program) {
        if (program == null) throw new IllegalArgumentException("Program cannot be null.");
        if (program.length > MEM_SIZE) throw new IllegalArgumentException("Program image is too large for memory.");
        if (program.length == MEM_SIZE) {
            System.arraycopy(program, 0, memory, 0, MEM_SIZE);
            return;
        }
        if (program.length > MEM_SIZE - PROGRAM_START) {
            throw new IllegalArgumentException("Program is too large for the program region.");
        }
        System.arraycopy(program, 0, memory, PROGRAM_START, program.length);
    }

    /** Replaces all 64 KiB with a complete memory image. */
    public synchronized void loadImage(byte[] image) {
        if (image == null || image.length != MEM_SIZE) {
            throw new IllegalArgumentException("Memory image must be exactly 65536 bytes.");
        }
        System.arraycopy(image, 0, memory, 0, MEM_SIZE);
    }

    /** Clears memory and restores the stack pointer to STACK_END. */
    public synchronized void clear() {
        Arrays.fill(memory, (byte) 0);
        sp = STACK_END;
    }
}
