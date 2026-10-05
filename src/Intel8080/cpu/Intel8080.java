package Intel8080.cpu;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

public class Intel8080 {
    /** Nominal 8080 oscillator frequency used by the real-time instruction scheduler. */
    public static final int CLOCK_HZ = 2_000_000;
    private static final long STATE_UPDATE_PERIOD_NANOS = 1_000_000_000L / 60;
    public static final byte PORT_KEY_DATA = 0x00;
    public static final byte PORT_KEY_STATUS = 0x01;
    public static final byte PORT_GAME_UPDATE = 0x02;
    public static final int STOP_OPCODE = 0xFD;

    private static final AtomicInteger NEXT_ID = new AtomicInteger();
    private final List<CPUStateListener> stateListeners = new CopyOnWriteArrayList<>();
    private final List<PortOutputListener> portOutputListeners = new CopyOnWriteArrayList<>();

    /** Receives byte writes sent by an emulated OUT instruction. */
    @FunctionalInterface
    public interface PortOutputListener {
        /** Handles a byte written to an I/O port. */
        void portOutput(int port, int value);
    }

    private volatile boolean paused;
    private volatile boolean running;
    private volatile boolean stopped;
    private volatile long executionGeneration;
    private Thread vmThread;
    private final java.util.concurrent.ThreadPoolExecutor clockWorker =
            (java.util.concurrent.ThreadPoolExecutor) java.util.concurrent.Executors.newFixedThreadPool(1, task -> {
                Thread thread = new Thread(task, "Intel8080-clock");
                thread.setDaemon(true);
                return thread;
            });
    private volatile String runtimeError;

    byte A, B, C, D, E, H, L;
    final Memory memory;
    int pc;
    int instructionCycles;

    boolean S, Z, AC, P, CY;
    volatile boolean IE;
    final boolean[] IM = new boolean[5];
    boolean serialInput;
    volatile boolean HLT;
    volatile boolean interruptPending;
    volatile int interruptVector;
    final byte[] ioports = new byte[256];
    final int cpuId;

    /** Creates a CPU that executes instructions against the supplied 64 KiB memory. */
    public Intel8080(Memory memory) {
        if (memory == null) throw new IllegalArgumentException("Memory cannot be null.");
        this.memory = memory;
        cpuId = NEXT_ID.getAndIncrement();
        pc = Memory.PROGRAM_START;
        IE = false;
        Arrays.fill(IM, false);
    }

    /** Stops execution and notifies registered state listeners. */
    public synchronized void stop() {
        requestStop();
        notifyStateListeners();
    }

    synchronized void stopFromInstruction() { requestStop(); }

    private synchronized void requestStop() {
        executionGeneration++;
        running = false;
        paused = false;
        stopped = true;
        if (vmThread != null) vmThread.interrupt();
    }

    /** Starts the CPU on its worker thread. */
    public synchronized void run() {
        if (running) return;
        stopped = false;
        runtimeError = null;
        paused = false;
        running = true;
        long generation = ++executionGeneration;
        clockWorker.execute(() -> {
            synchronized (this) {
                if (generation != executionGeneration) return;
                vmThread = Thread.currentThread();
            }
            _run(generation);
        });
    }

    /** Keeps the single clock worker alive between program runs without executing idle memory. */
    public void startClockWorker() { clockWorker.prestartCoreThread(); }

    /** Selects the entry address while execution is stopped. */
    public synchronized void setEntryAddress(int address) {
        if (running) throw new IllegalStateException("Stop the CPU before changing its entry address.");
        if (address < 0 || address >= Memory.MEM_SIZE) throw new IllegalArgumentException("Entry address is out of range.");
        pc = address;
        notifyStateListeners();
    }

    /** Pauses execution without discarding CPU state. */
    public synchronized void pause() {
        if (!running || stopped) return;
        paused = true;
        notifyStateListeners();
    }

    /** Resumes an execution that was paused by the debugger. */
    public synchronized void continueExecution() {
        if (!running || stopped) return;
        paused = false;
        notifyStateListeners();
    }

    /** Executes exactly one instruction while stopped or debugger-paused. */
    public void step() {
        synchronized (this) {
            if (running && !paused) return;
            if (HLT || stopped) return;
            executeOneInstruction();
        }
        notifyStateListeners();
    }

    private void _run(long generation) {
        long previousTime = System.nanoTime();
        long lastStateNotification = previousTime;
        double cycleBudget = 0.0;
        try {
            while (running && !stopped && generation == executionGeneration) {
                if (paused) {
                    LockSupport.parkNanos(1_000_000L);
                    previousTime = System.nanoTime();
                    continue;
                }

                long now = System.nanoTime();
                long elapsed = now - previousTime;
                previousTime = now;
                cycleBudget = Math.min(CLOCK_HZ * 0.05,
                        cycleBudget + elapsed * (CLOCK_HZ / 1_000_000_000.0));

                if (HLT) {
                    if (servicePendingInterrupt(generation)) {
                        cycleBudget = Math.max(0.0, cycleBudget - 11.0);
                    } else {
                        LockSupport.parkNanos(1_000_000L);
                    }
                } else if (interruptPending && IE) {
                    if (servicePendingInterrupt(generation)) cycleBudget = Math.max(0.0, cycleBudget - 11.0);
                } else {
                    boolean executed = false;
                    int requiredCycles;
                    synchronized (this) {
                        requiredCycles = Opcodes.minimumCycles(memory.get(pc & 0xFFFF) & 0xFF);
                        if (running && !stopped && !paused && generation == executionGeneration && cycleBudget >= requiredCycles) {
                            cycleBudget -= executeOneInstruction();
                            executed = true;
                        }
                    }
                    long currentTime = System.nanoTime();
                    if (executed && currentTime - lastStateNotification >= STATE_UPDATE_PERIOD_NANOS) {
                        lastStateNotification = currentTime;
                        notifyStateListeners();
                    }
                    if (!executed) {
                        // Short clock waits need a spin wait; OS parking granularity would slow a 2 MHz CPU dramatically.
                        Thread.onSpinWait();
                    }
                }
            }
        } catch (RuntimeException error) {
            synchronized (this) {
                if (generation == executionGeneration) {
                    runtimeError = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                    requestStop();
                }
            }
        } finally {
            synchronized (this) {
                if (generation == executionGeneration) {
                    running = false;
                    paused = false;
                }
                if (vmThread == Thread.currentThread()) vmThread = null;
            }
            notifyStateListeners();
        }
    }

    private synchronized int executeOneInstruction() {
        int address = pc & 0xFFFF;
        byte opcode = memory.get(address);
        pc = (pc + 1) & 0xFFFF;
        instructionCycles = Opcodes.minimumCycles(opcode & 0xFF);
        execute(opcode);
        // STOP is a Vulcan termination opcode: keep the debugger on the terminating instruction.
        if ((opcode & 0xFF) == STOP_OPCODE) pc = address;
        return instructionCycles;
    }

    /** Resets registers, flags, ports, and control state to the processor startup values. */
    public synchronized void reset() {
        executionGeneration++;
        running = false;
        paused = false;
        stopped = false;
        runtimeError = null;
        if (vmThread != null) vmThread.interrupt();
        HLT = false;
        pc = Memory.PROGRAM_START;
        memory.setSp(Memory.STACK_END);
        A = B = C = D = E = H = L = 0;
        S = Z = AC = P = CY = false;
        IE = false;
        Arrays.fill(IM, false);
        serialInput = false;
        interruptPending = false;
        interruptVector = 0;
        Arrays.fill(ioports, (byte) 0);
        notifyStateListeners();
    }

    /** Returns the legacy port buffer; keyboard producers should use offerKeyInput for atomic delivery. */
    public byte[] getIOPorts() { return ioports; }

    /**
     * Latches the latest input byte. IN 1 bit zero reports readiness; IN 0 consumes that
     * byte and clears readiness. New input replaces unread input so a game never builds
     * an unbounded queue of stale turns. OUT instructions cannot overwrite this latch.
     *
     * @param value an unsigned byte, normally the ASCII value of a typed character
     */
    public synchronized void offerKeyInput(int value) {
        if (value < 0 || value > 0xFF) throw new IllegalArgumentException("Input must fit in one byte.");
        ioports[PORT_KEY_DATA] = (byte) value;
        ioports[PORT_KEY_STATUS] |= 1;
    }

    /** Reads an input port and atomically acknowledges keyboard data or the game update signal. */
    synchronized byte inputPort(int port) {
        int index = port & 0xFF;
        byte value = ioports[index];
        if (index == PORT_KEY_DATA) ioports[PORT_KEY_STATUS] &= (byte) ~1;
        if (index == PORT_GAME_UPDATE) ioports[PORT_GAME_UPDATE] = 0;
        return value;
    }

    /** Stores a port write and notifies listeners without blocking the CPU on listener failures. */
    synchronized void outputPort(int port, byte value) {
        int index = port & 0xFF;
        // IN and OUT have separate hardware directions, even when their port numbers match.
        if (index != PORT_KEY_DATA && index != PORT_KEY_STATUS) ioports[index] = value;
        for (PortOutputListener listener : portOutputListeners) {
            try { listener.portOutput(index, value & 0xFF); } catch (RuntimeException ignored) { }
        }
    }

    /** Registers a listener for writes to emulated output ports. */
    public void addPortOutputListener(PortOutputListener listener) {
        if (listener != null) portOutputListeners.add(listener);
    }

    /** Loads raw executable bytes at PROGRAM_START and preserves the rest of memory. */
    public void loadProgram(byte[] program) {
        stop();
        reset();
        memory.loadRom(program);
    }

    /** Returns the memory instance attached to this CPU. */
    public Memory getMemory() { return memory; }
    /** Returns the unique identifier used in the CPU worker thread name. */
    public int getCpuId() { return cpuId; }

    /** Queues a hardware interrupt at the supplied 16-bit address. */
    public synchronized void triggerInterrupt(int vector) {
        if (vector < 0 || vector > 0xFFFF) throw new IllegalArgumentException("Interrupt vector out of range.");
        interruptVector = vector;
        interruptPending = true;
        if (vmThread != null) LockSupport.unpark(vmThread);
    }

    /** Executes one decoded opcode without fetching it from memory. */
    public synchronized void execute(byte instruction) {
        OpcodeHandler handler = Opcodes.HANDLERS[instruction & 0xFF];
        if (handler == null) {
            throw new IllegalStateException(String.format(
                    "Unimplemented opcode 0x%02X at PC=0x%04X",
                    instruction & 0xFF, (pc - 1) & 0xFFFF));
        }
        handler.execute(this);
    }

    private synchronized void handleInterrupt() {
        memory.push((byte) ((pc >> 8) & 0xFF));
        memory.push((byte) (pc & 0xFF));
        pc = interruptVector & 0xFFFF;
        interruptPending = false;
        IE = false;
    }

    private synchronized boolean servicePendingInterrupt(long generation) {
        if (generation != executionGeneration || !running || stopped || !interruptPending || !IE) return false;
        HLT = false;
        handleInterrupt();
        return true;
    }

    int pair(byte high, byte low) { return ((high & 0xFF) << 8) | (low & 0xFF); }

    void setZSP(byte result) {
        int value = result & 0xFF;
        Z = value == 0;
        S = (value & 0x80) != 0;
        P = Integer.bitCount(value) % 2 == 0;
    }

    byte addWithFlags(byte a, byte b, boolean carryIn) {
        int x = a & 0xFF;
        int y = b & 0xFF;
        int carry = carryIn ? 1 : 0;
        int sum = x + y + carry;
        byte result = (byte) sum;
        setZSP(result);
        AC = ((x & 0x0F) + (y & 0x0F) + carry) > 0x0F;
        CY = sum > 0xFF;
        return result;
    }

    byte subWithFlags(byte a, byte b, boolean borrowIn) {
        int x = a & 0xFF;
        int y = b & 0xFF;
        int borrow = borrowIn ? 1 : 0;
        int diff = x - y - borrow;
        byte result = (byte) diff;
        setZSP(result);
        // The 8080 retains the ALU's bit-3 carry; only the full carry becomes a borrow flag.
        AC = (x & 0x0F) >= ((y & 0x0F) + borrow);
        CY = diff < 0;
        return result;
    }

    byte incDecWithFlags(byte oldVal, int delta) {
        int old = oldVal & 0xFF;
        byte result = (byte) ((old + delta) & 0xFF);
        setZSP(result);
        AC = delta > 0 ? ((old & 0x0F) == 0x0F) : ((old & 0x0F) != 0x00);
        return result;
    }

    byte logicWithFlags(byte result) {
        setZSP(result);
        CY = false;
        AC = false;
        return result;
    }

    byte andWithFlags(byte a, byte b) {
        byte result = (byte) ((a & 0xFF) & (b & 0xFF));
        setZSP(result);
        CY = false;
        AC = ((a | b) & 0x08) != 0;
        return result;
    }

    void log(String msg) { logf(msg); }
    void logf(String fmt, Object... args) { }

    /** Returns the current accumulator value. */
    public synchronized byte getA() { return A; }
    /** Returns the current B register value. */
    public synchronized byte getB() { return B; }
    /** Returns the current C register value. */
    public synchronized byte getC() { return C; }
    /** Returns the current D register value. */
    public synchronized byte getD() { return D; }
    /** Returns the current E register value. */
    public synchronized byte getE() { return E; }
    /** Returns the current H register value. */
    public synchronized byte getH() { return H; }
    /** Returns the current L register value. */
    public synchronized byte getL() { return L; }
    /** Returns the 16-bit program counter. */
    public synchronized int getPC() { return pc & 0xFFFF; }
    /** Reports whether execution is paused. */
    public boolean isPaused() { return paused; }
    /** Reports whether the worker is executing instructions. */
    public boolean isRunning() { return running; }
    /** Reports whether the processor has executed HLT. */
    public boolean isHalted() { return HLT; }
    /** Reports whether execution was explicitly stopped or terminated by STOP. */
    public boolean isStopped() { return stopped; }
    /** Returns the concise message from the most recent unhandled runtime failure, if any. */
    public String getRuntimeError() { return runtimeError; }

    /** Registers a listener notified after CPU state changes. */
    public void addStateListener(CPUStateListener listener) {
        if (listener != null) stateListeners.add(listener);
    }

    private void notifyStateListeners() {
        for (CPUStateListener listener : stateListeners) {
            try { listener.cpuStateChanged(); } catch (RuntimeException ignored) { }
        }
    }
}
