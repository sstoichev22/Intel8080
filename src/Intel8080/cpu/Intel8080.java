package Intel8080.cpu;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/** Vulcan CPU: an 8080-style core with 32-bit registers, 64-bit pairs and addresses, interrupts, and port peripherals. */
public class Intel8080 {

    public static final int CLOCK_HZ = 100_000_000;
    private static final long STATE_UPDATE_PERIOD_NANOS = 1_000_000_000L / 60;
    public static final byte PORT_KEY_DATA = 0x00;
    public static final byte PORT_KEY_STATUS = 0x01;
    public static final long KEYBOARD_INTERRUPT_VECTOR = 0x10;
    public static final long MOUSE_INTERRUPT_VECTOR = 0x20;
    public static final int PORT_MOUSE_COMMAND = MousePeripheral.COMMAND_PORT;
    public static final int PORT_MOUSE_DATA = MousePeripheral.DATA_PORT;
    public static final int PORT_MOUSE_BUTTON = MousePeripheral.BUTTON_PORT;
    private final java.util.ArrayDeque<Integer> keyboardInput = new java.util.ArrayDeque<>();
    public static final int STOP_OPCODE = 0xFD;

    private static final AtomicInteger NEXT_ID = new AtomicInteger();
    private final List<CPUStateListener> stateListeners = new CopyOnWriteArrayList<>();
    private final List<PortOutputListener> portOutputListeners = new CopyOnWriteArrayList<>();

    /** Receives every byte written with OUT, with its port number. */
    @FunctionalInterface
    public interface PortOutputListener {

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
    private final java.util.Set<Long> breakpoints=new java.util.HashSet<>();
    private Long skipBreakpoint;

    /** Adds or removes a breakpoint at a 64-bit address. */
    public synchronized void setBreakpoint(long address,boolean enabled){

        if(enabled)breakpoints.add(address);else breakpoints.remove(address);
        notifyStateListeners();
    }
    public synchronized boolean hasBreakpoint(long address){return breakpoints.contains(address);}
    public synchronized java.util.Set<Long> getBreakpoints(){return java.util.Set.copyOf(breakpoints);}
    public synchronized void clearBreakpoints(){breakpoints.clear();skipBreakpoint=null;}

    int A, B, C, D, E, H, L;
    final Memory memory;
    long pc;
    int instructionCycles;

    boolean S, Z, AC, P, CY;
    volatile boolean IE;
    private int interruptEnableDelay;
    volatile boolean HLT;
    volatile boolean interruptPending;
    volatile long interruptVector;
    private boolean mouseInterruptPending;
    final byte[] ioports = new byte[256];
    private final ClockPeripheral clockPeripheral = new ClockPeripheral();
    private final MousePeripheral mousePeripheral = new MousePeripheral();
    final int cpuId;

    /** Creates a CPU attached to memory, in reset state. */
    public Intel8080(Memory memory) {
        if (memory == null) throw new IllegalArgumentException("Memory cannot be null.");
        this.memory = memory;
        cpuId = NEXT_ID.getAndIncrement();
        pc = Memory.PROGRAM_START;
        IE = false;
    }

    /** Stops asynchronous execution; the CPU can be run again later. */
    public synchronized void stop() {
        requestStop();
        notifyStateListeners();
    }

    /** Stops execution and releases the worker thread; the CPU cannot run afterwards. */
    public synchronized void close() {
        requestStop();
        clockWorker.shutdownNow();
        stateListeners.clear();
        portOutputListeners.clear();
    }

    synchronized void stopFromInstruction() { requestStop(); }

    private synchronized void requestStop() {
        executionGeneration++;
        running = false;
        paused = false;
        stopped = true;
        if (vmThread != null) vmThread.interrupt();
    }

    /** Starts asynchronous emulation, or rejects the call when {@link #close()} has shut down its worker. */
    public synchronized void run() {
        if (clockWorker.isShutdown()) throw new IllegalStateException("CPU is closed.");
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

    public void startClockWorker() { clockWorker.prestartCoreThread(); }

    /** Sets the program counter used for the next run. */
    public synchronized void setEntryAddress(long address) {
        if (running) throw new IllegalStateException("Stop the CPU before changing its entry address.");
        pc = address;
        skipBreakpoint = null;
        notifyStateListeners();
    }

    /** Pauses asynchronous execution at the next instruction boundary. */
    public synchronized void pause() {
        if (!running || stopped) return;
        paused = true;
        notifyStateListeners();
    }

    /** Resumes a paused run. */
    public synchronized void continueExecution() {
        if (!running || stopped) return;
        skipBreakpoint=physicalAddress();
        paused = false;
        notifyStateListeners();
    }

    /** Executes exactly one instruction, servicing a pending interrupt first. */
    public void step() {
        synchronized (this) {
            if (running && !paused) return;
            if (HLT && (!hasPendingInterrupt() || !IE)) return;
            boolean wasStopped = stopped;
            if (wasStopped) { stopped = false; runtimeError = null; }
            try { if (!acceptPendingInterrupt()) executeOneInstruction(); }
            finally { if (wasStopped && !running) { stopped = true; paused = false; } }
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
                    if (!IE) break;
                    if (servicePendingInterrupt(generation)) {
                        cycleBudget = Math.max(0.0, cycleBudget - 11.0);
                    } else {
                        LockSupport.parkNanos(1_000_000L);
                    }
                } else if (hasPendingInterrupt() && IE) {
                    if (servicePendingInterrupt(generation)) cycleBudget = Math.max(0.0, cycleBudget - 11.0);
                } else {
                    boolean executed = false;
                    int requiredCycles;
                    synchronized (this) {
                        long location=physicalAddress();
                        if(breakpoints.contains(location)&&(skipBreakpoint==null||skipBreakpoint.longValue()!=location)){paused=true;notifyStateListeners();continue;}
                        requiredCycles = Opcodes.minimumCycles(memory.get(physicalAddress()) & 0xFF);
                        if (running && !stopped && !paused && generation == executionGeneration && cycleBudget >= requiredCycles) {
                            skipBreakpoint=null;
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
        long address = pc;
        byte opcode = memory.get(physicalAddress());
        advancePC();
        instructionCycles = Opcodes.minimumCycles(opcode & 0xFF);
        execute(opcode);
        if (interruptEnableDelay > 0 && --interruptEnableDelay == 0) IE = true;

        if ((opcode & 0xFF) == STOP_OPCODE) { pc = address; }
        return instructionCycles;
    }

    /** Clears registers, flags, interrupt state, and pending input, and restores the default PC and SP. */
    public synchronized void reset() {
        executionGeneration++;
        running = false;
        paused = false;
        stopped = false;
        runtimeError = null;
        skipBreakpoint = null;
        if (vmThread != null) vmThread.interrupt();
        HLT = false;
        pc = Memory.PROGRAM_START;
        memory.setSp(Memory.STACK_END);
        A = B = C = D = E = H = L = 0;
        S = Z = AC = P = CY = false;
        IE = false;
        interruptEnableDelay = 0;
        interruptPending = false;
        interruptVector = 0;
        mouseInterruptPending = false;
        keyboardInput.clear();
        Arrays.fill(ioports, (byte) 0);
        clockPeripheral.reset();
        mousePeripheral.reset();
        notifyStateListeners();
    }

    public byte[] getIOPorts() { return ioports; }

    /** Queues one keyboard byte (0..255) and requests the keyboard interrupt. */
    public synchronized void offerKeyInput(int value) {
        if (value < 0 || value > 0xFF) throw new IllegalArgumentException("Input must fit in one byte.");
        if (keyboardInput.size() >= 8192) return;
        keyboardInput.addLast(value);
        ioports[PORT_KEY_DATA] = keyboardInput.getFirst().byteValue();
        ioports[PORT_KEY_STATUS] |= 1;
        if (vmThread != null) LockSupport.unpark(vmThread);
    }

    public void offerMousePosition(float x,float y){mousePeripheral.setPosition(x,y);}
    public synchronized void pressMouseButton(int button){mousePeripheral.pressButton(button);mouseInterruptPending=true;if(vmThread!=null)LockSupport.unpark(vmThread);}
    public void releaseMouseButton(int button){mousePeripheral.releaseButton(button);}
    public void releaseAllMouseButtons(){mousePeripheral.releaseAllButtons();}

    synchronized byte inputPort(int port) {
        int index = port & 0xFF;
        if(index==ClockPeripheral.DATA_PORT)return clockPeripheral.read();
        if(index==PORT_MOUSE_DATA)return mousePeripheral.readData();
        if(index==PORT_MOUSE_BUTTON)return mousePeripheral.readButton();
        byte value = ioports[index];
        if (index == PORT_KEY_DATA) {
            if (!keyboardInput.isEmpty()) value = keyboardInput.removeFirst().byteValue();
            if (keyboardInput.isEmpty()) ioports[PORT_KEY_STATUS] &= (byte) ~1;
            else ioports[PORT_KEY_DATA] = keyboardInput.getFirst().byteValue();
        }
        return value;
    }

    synchronized void outputPort(int port, byte value) {
        int index = port & 0xFF;
        if(index==ClockPeripheral.COMMAND_PORT)clockPeripheral.command(value&255);
        if(index==PORT_MOUSE_COMMAND)mousePeripheral.command(value&255);

        if (index != PORT_KEY_DATA && index != PORT_KEY_STATUS && index != PORT_MOUSE_COMMAND) ioports[index] = value;
        for (PortOutputListener listener : portOutputListeners) {
            try { listener.portOutput(index, value & 0xFF); } catch (RuntimeException ignored) { }
        }
    }

    /** Registers a listener for OUT instructions. */
    public void addPortOutputListener(PortOutputListener listener) {
        if (listener != null) portOutputListeners.add(listener);
    }

    /** Loads a legacy ROM image at the program start address. */
    public void loadProgram(byte[] program) {
        stop();
        reset();
        memory.loadRom(program);
    }

    /** Loads bytes at an address and sets the entry point. */
    public synchronized void loadFlatProgram(long loadAddress, byte[] program, long entryAddress) {
        stop(); reset(); memory.clear(); memory.load(loadAddress, program); setEntryAddress(entryAddress);
    }

    public Memory getMemory() { return memory; }

    public int getCpuId() { return cpuId; }

    /** Requests an interrupt that jumps to the given vector when interrupts are enabled. */
    public synchronized void triggerInterrupt(long vector) {
        interruptVector = vector;
        interruptPending = true;
        if (vmThread != null) LockSupport.unpark(vmThread);
    }

    /** Executes one opcode at the current PC; throws IllegalStateException for an undefined opcode. */
    public synchronized void execute(byte instruction) {
        OpcodeHandler handler = Opcodes.HANDLERS[instruction & 0xFF];
        if (handler == null) {
            throw new IllegalStateException(String.format(
                    "Unimplemented opcode 0x%02X at PC=0x%016X",
                    instruction & 0xFF, pc - 1));
        }
        handler.execute(this);
    }

    private synchronized void handleInterrupt() {
        memory.push64(pc);
        if (interruptPending) {
            pc = interruptVector;
            interruptPending = false;
        } else if (mouseInterruptPending) {
            pc = MOUSE_INTERRUPT_VECTOR;
            mouseInterruptPending = false;
        } else pc = KEYBOARD_INTERRUPT_VECTOR;
        IE = false;
        interruptEnableDelay = 0;
    }

    void enableInterrupts() { IE = false; interruptEnableDelay = 2; }
    void disableInterrupts() { IE = false; interruptEnableDelay = 0; }

    private synchronized boolean hasPendingInterrupt() {
        return interruptPending || mouseInterruptPending || (ioports[PORT_KEY_STATUS] & 1) != 0;
    }

    private boolean acceptPendingInterrupt() {
        if (!hasPendingInterrupt() || !IE) return false;
        HLT = false;
        handleInterrupt();
        return true;
    }

    private synchronized boolean servicePendingInterrupt(long generation) {
        if (generation != executionGeneration || !running || stopped || paused || !hasPendingInterrupt() || !IE) return false;
        return acceptPendingInterrupt();
    }

    /** Combines two 32-bit registers into a pair, preserving every bit of the low register. */
    long pair(int high, int low) {
        return ((long) high << 32) | Integer.toUnsignedLong(low);
    }

    /** Returns the current flat program-counter address. */
    long physicalAddress() {
        return pc;
    }

    /** Advances the 64-bit program counter by one byte. */
    void advancePC() {
        pc++;
    }

    /** Updates zero, sign and even-parity flags for a 32-bit result without changing carry flags. */
    void setZSP(int value) {
        Z = value == 0;
        S = value < 0;
        P = (Integer.bitCount(value) & 1) == 0;
    }

    /** Adds two unsigned 32-bit register values and updates the arithmetic flags. */
    int addWithFlags(int x, int y, boolean carryIn) {
        int carry = carryIn ? 1 : 0;
        long sum = Integer.toUnsignedLong(x) + Integer.toUnsignedLong(y) + carry;
        int result = (int) sum;
        setZSP(result);
        AC = ((x & 15) + (y & 15) + carry) > 15;
        CY = sum > 0xffffffffL;
        return result;
    }

    /** Subtracts two unsigned 32-bit register values and updates the arithmetic flags. */
    int subWithFlags(int x, int y, boolean borrowIn) {
        int borrow = borrowIn ? 1 : 0;
        long diff = Integer.toUnsignedLong(x) - Integer.toUnsignedLong(y) - borrow;
        int result = (int) diff;
        setZSP(result);
        AC = (x & 15) >= ((y & 15) + borrow);
        CY = diff < 0;
        return result;
    }

    /** Updates result flags for increment/decrement and preserves the carry flag. */
    int incDecWithFlags(int old, int delta) {
        int result = old + delta;
        setZSP(result);
        AC = delta > 0 ? (old & 15) == 15 : (old & 15) != 0;
        return result;
    }

    /** Updates result flags for logic operations and clears both carry flags. */
    int logicWithFlags(int result) {
        setZSP(result);
        CY = false;
        AC = false;
        return result;
    }

    /** Applies the 8080 AND auxiliary-carry rule and clears the carry flag. */
    int andWithFlags(int a, int b) {
        int result = a & b;
        setZSP(result);
        CY = false;
        AC = ((a | b) & 8) != 0;
        return result;
    }

    public synchronized int getA() { return A; }

    public synchronized int getB() { return B; }

    public synchronized int getC() { return C; }

    public synchronized int getD() { return D; }

    public synchronized int getE() { return E; }

    public synchronized int getH() { return H; }

    public synchronized int getL() { return L; }
    public synchronized long getPC() { return pc; }
    /** Returns the guest cycle cost of the most recently executed instruction. */
    public synchronized int lastInstructionCycles() { return instructionCycles; }
    public synchronized long getBC() { return pair(B, C); }
    public synchronized long getDE() { return pair(D, E); }
    public synchronized long getHL() { return pair(H, L); }
    public synchronized long getExecutionAddress() { return pc; }

    public boolean isPaused() { return paused; }

    public boolean isRunning() { return running; }

    public boolean isHalted() { return HLT; }

    public boolean isStopped() { return stopped; }

    public String getRuntimeError() { return runtimeError; }

    /** Registers a listener for run-state changes. */
    public void addStateListener(CPUStateListener listener) {
        if (listener != null) stateListeners.add(listener);
    }

    private void notifyStateListeners() {
        for (CPUStateListener listener : stateListeners) {
            try { listener.cpuStateChanged(); } catch (RuntimeException ignored) { }
        }
    }
}
