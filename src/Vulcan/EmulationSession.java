package Vulcan;

import Intel8080.cpu.Intel8080;
import Intel8080.cpu.Memory;
import java.util.function.Consumer;

/** Application-lifetime processor and memory, with one active program and one output destination. */
final class EmulationSession {
    static final EmulationSession SHARED = new EmulationSession();
    final Memory memory = new Memory();
    final Intel8080 cpu = new Intel8080(memory);
    private volatile Object owner;
    private volatile Consumer<Integer> output = value -> {};
    private volatile long revision;
    private final java.util.ArrayDeque<Integer> input = new java.util.ArrayDeque<>();
    private final java.util.concurrent.ScheduledExecutorService inputWorker =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "Vulcan-console-input");
                thread.setDaemon(true);
                return thread;
            });

    private EmulationSession() {
        cpu.startClockWorker();
        cpu.addPortOutputListener((port, value) -> {
            if (port == Intel8080.PORT_KEY_DATA) output.accept(value);
        });
        inputWorker.scheduleAtFixedRate(this::pumpInput, 0, 1, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /** Replaces only the loaded address range, preserving the rest of the previous memory. */
    synchronized long load(Object nextOwner, byte[] image, int start, int end, int entry, Consumer<Integer> sink) {
        if (start < 0 || end < start || end >= Memory.MEM_SIZE || image.length != Memory.MEM_SIZE)
            throw new IllegalArgumentException("Invalid program memory range.");
        cpu.stop();
        input.clear();
        output = value -> {};
        cpu.reset();
        for (int address = start; address <= end; address++) memory.set(address, image[address]);
        cpu.setEntryAddress(entry);
        owner = nextOwner;
        output = sink == null ? value -> {} : sink;
        return ++revision;
    }

    /** Reports whether a panel still owns the loaded program. */
    boolean owns(Object candidate) { return owner == candidate; }
    long revision() { return revision; }

    /** Starts only the program whose owner and load revision still match. */
    synchronized boolean run(Object candidate, long expectedRevision) {
        if (owner != candidate || revision != expectedRevision) return false;
        cpu.run();
        return true;
    }

    /** Stops the caller's program without interrupting a newer program in another panel. */
    synchronized void stop(Object candidate) {
        if (owner == candidate) { revision++; cpu.stop(); }
    }

    synchronized void resume(Object candidate, long expectedRevision) {
        if (owner != candidate || revision != expectedRevision) return;
        cpu.continueExecution(); cpu.run();
    }

    /** Resets the current program's registers and pending input while retaining its memory. */
    synchronized void reset(Object candidate, int entry) {
        if (owner != candidate) return;
        revision++; input.clear(); cpu.reset(); cpu.setEntryAddress(entry);
    }

    /** Queues a complete ASCII/byte line, terminated by LF, for a console's current program. */
    synchronized boolean submitInput(Object candidate, String line) {
        if (owner != candidate || cpu.isStopped() || cpu.isHalted() || line.length() + input.size() >= 8192) return false;
        for (int i = 0; i < line.length(); i++) if (line.charAt(i) > 255) return false;
        for (int i = 0; i < line.length(); i++) input.addLast((int) line.charAt(i));
        input.addLast(10);
        pumpInput();
        return true;
    }

    /** Supplies the next byte only after the program acknowledges the previous byte with IN 0. */
    private synchronized void pumpInput() {
        if (input.isEmpty() || cpu.isStopped() || cpu.isHalted()) return;
        synchronized (cpu) {
            if ((cpu.getIOPorts()[Intel8080.PORT_KEY_STATUS] & 1) == 0) cpu.offerKeyInput(input.removeFirst());
        }
    }
}
