package Intel8080.cpu;

import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;

/**
 * Sparse, flat, 64-bit-addressed memory. Addresses identify individual bytes;
 * the first 64 KiB use a fixed array and higher pages are allocated on demand.
 */
public class Memory {
    public static final int MEM_SIZE = 0x10000;
    public static final int RST_VECTORS_START = 0x0000;
    public static final int PROGRAM_START = 0x0100;
    public static final int RAM_START = 0x8000;
    public static final long VRAM_START = 0xF0000000L;
    public static final int DEFAULT_VRAM_BYTES = 5 * 1024 * 1024;
    public static final long STACK_START = 0x00000000FFFFF000L;
    public static final long STACK_END = 0x0000000100000000L;
    public static final long HEAP_START = 0x0000010000000000L;
    public static final long HEAP_END = HEAP_START + 32767;

    private final byte[] memory = new byte[MEM_SIZE];
    private final Map<Long, byte[]> pages = new HashMap<>();
    private long stackPointer = STACK_END;
    private int vramBytes = DEFAULT_VRAM_BYTES;

    /** Returns the configured framebuffer capacity in bytes. */
    public synchronized int getVramBytes() {
        return vramBytes;
    }

    /**
     * Sets the framebuffer capacity, between 1 KiB and 256 MiB inclusive.
     * Invalid sizes leave the current capacity unchanged.
     */
    public synchronized void setVramBytes(int bytes) {
        if (bytes < 1024 || bytes > 256 * 1024 * 1024) {
            throw new IllegalArgumentException("VRAM size must be 1 KiB to 256 MiB.");
        }
        vramBytes = bytes;
    }

    /** Reads a byte at the unsigned 32-bit address represented by {@code address}. */
    public synchronized byte get(int address) {
        return get(Integer.toUnsignedLong(address));
    }

    /** Reads a byte without allocating an untouched sparse page. */
    public synchronized byte get(long address) {
        long pageNumber = address >>> 16;
        int pageOffset = (int) address & (MEM_SIZE - 1);
        if (pageNumber == 0) {
            return memory[pageOffset];
        }

        byte[] page = pages.get(pageNumber);
        return page == null ? 0 : page[pageOffset];
    }

    /** Writes a byte at the unsigned 32-bit address represented by {@code address}. */
    public synchronized void set(int address, byte value) {
        set(Integer.toUnsignedLong(address), value);
    }

    /** Writes a byte, allocating its sparse page only when needed. */
    public synchronized void set(long address, byte value) {
        long pageNumber = address >>> 16;
        int pageOffset = (int) address & (MEM_SIZE - 1);
        if (pageNumber == 0) {
            memory[pageOffset] = value;
            return;
        }

        pages.computeIfAbsent(pageNumber, ignored -> new byte[MEM_SIZE])[pageOffset] = value;
    }

    /**
     * Returns an independent snapshot of {@code length} bytes beginning at
     * {@code start}. Unallocated sparse pages read as zero. The maximum snapshot
     * is 256 MiB, and ranges may not wrap the unsigned 64-bit address space.
     */
    public synchronized byte[] readRange(long start, int length) {
        if (length < 0 || length > 256 * 1024 * 1024) {
            throw new IllegalArgumentException("Snapshot length must be between 0 and 256 MiB.");
        }
        if (length > 0 && Long.compareUnsigned(start + length - 1, start) < 0) {
            throw new IllegalArgumentException("Snapshot wraps the address space.");
        }

        byte[] result = new byte[length];
        int copied = 0;
        while (copied < length) {
            long address = start + copied;
            long pageNumber = address >>> 16;
            int pageOffset = (int) address & (MEM_SIZE - 1);
            int count = Math.min(length - copied, MEM_SIZE - pageOffset);
            byte[] page = pageNumber == 0 ? memory : pages.get(pageNumber);
            if (page != null) {
                System.arraycopy(page, pageOffset, result, copied, count);
            }
            copied += count;
        }
        return result;
    }

    /** Returns the number of allocated sparse pages, excluding the base 64 KiB. */
    public synchronized int getAllocatedPageCount() {
        return pages.size();
    }

    /**
     * Copies an image to an arbitrary 64-bit address. The input is not retained,
     * null input is rejected, and an image may not wrap the address space.
     */
    public synchronized void load(long startAddress, byte[] image) {
        if (image == null) {
            throw new IllegalArgumentException("Program image cannot be null.");
        }
        if (image.length > 0 && Long.compareUnsigned(startAddress + image.length - 1, startAddress) < 0) {
            throw new IllegalArgumentException("Program image wraps the 64-bit address space.");
        }

        int copied = 0;
        while (copied < image.length) {
            long address = startAddress + copied;
            long pageNumber = address >>> 16;
            int pageOffset = (int) address & (MEM_SIZE - 1);
            int count = Math.min(image.length - copied, MEM_SIZE - pageOffset);
            byte[] page = pageNumber == 0 ? memory : pages.computeIfAbsent(pageNumber, ignored -> new byte[MEM_SIZE]);
            System.arraycopy(image, copied, page, pageOffset, count);
            copied += count;
        }
    }

    /** Pushes one byte by decrementing the stack pointer before the write. */
    public synchronized void push(byte value) {
        set(--stackPointer, value);
    }

    /** Pops one byte, then increments the stack pointer. */
    public synchronized byte pop() {
        return get(stackPointer++);
    }

    /** Pushes a 64-bit value so the lowest-addressed byte is its least significant byte. */
    public synchronized void push64(long value) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            push((byte) (value >>> shift));
        }
    }

    /** Pops and returns a little-endian 64-bit value. */
    public synchronized long pop64() {
        long value = 0;
        for (int shift = 0; shift < 64; shift += 8) {
            value |= (pop() & 0xFFL) << shift;
        }
        return value;
    }

    /** Adds a signed offset to the stack pointer. */
    public synchronized void incSp(long value) {
        stackPointer += value;
    }

    /** Sets the stack pointer to an address without changing memory contents. */
    public synchronized void setSp(long address) {
        stackPointer = address;
    }

    /** Returns the current 64-bit stack pointer. */
    public synchronized long getSp() {
        return stackPointer;
    }

    /** Reads eight bytes as a little-endian 64-bit value. */
    public synchronized long read64(long address) {
        validate64BitAccess(address);
        long value = 0;
        for (int byteIndex = 0; byteIndex < Long.BYTES; byteIndex++) {
            value |= (get(address + byteIndex) & 0xFFL) << (byteIndex * Byte.SIZE);
        }
        return value;
    }

    /** Writes eight bytes from a little-endian 64-bit value. */
    public synchronized void write64(long address, long value) {
        validate64BitAccess(address);
        for (int byteIndex = 0; byteIndex < Long.BYTES; byteIndex++) {
            set(address + byteIndex, (byte) (value >>> (byteIndex * Byte.SIZE)));
        }
    }

    private static void validate64BitAccess(long address) {
        if (Long.compareUnsigned(address + Long.BYTES - 1, address) < 0) {
            throw new IllegalArgumentException("64-bit access wraps the address space.");
        }
    }

    /**
     * Loads a 64 KiB ROM at address zero, or shorter executable bytes at
     * {@link #PROGRAM_START}. A short load leaves all other bytes unchanged.
     */
    public synchronized void loadRom(byte[] program) {
        if (program == null) {
            throw new IllegalArgumentException("Program cannot be null.");
        }
        if (program.length > MEM_SIZE) {
            throw new IllegalArgumentException("Program image is too large for memory.");
        }
        if (program.length == MEM_SIZE) {
            System.arraycopy(program, 0, memory, 0, MEM_SIZE);
            return;
        }
        if (program.length > MEM_SIZE - PROGRAM_START) {
            throw new IllegalArgumentException("Program is too large for the program region.");
        }
        System.arraycopy(program, 0, memory, PROGRAM_START, program.length);
    }

    /** Replaces the base 64 KiB with an image of exactly {@link #MEM_SIZE} bytes. */
    public synchronized void loadImage(byte[] image) {
        if (image == null || image.length != MEM_SIZE) {
            throw new IllegalArgumentException("Memory image must be exactly 65536 bytes.");
        }
        System.arraycopy(image, 0, memory, 0, MEM_SIZE);
    }

    /** Clears flat and sparse memory and restores the initial stack pointer. */
    public synchronized void clear() {
        Arrays.fill(memory, (byte) 0);
        pages.clear();
        stackPointer = STACK_END;
    }
}
