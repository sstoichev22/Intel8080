package Intel8080.cpu;

import java.util.Objects;
import java.util.function.LongSupplier;

/** Exposes a latched millisecond timestamp through byte-wide CPU ports. */
public final class ClockPeripheral {
    public static final int COMMAND_PORT = 0x20;
    public static final int DATA_PORT = 0x21;

    private final LongSupplier clock;
    private long snapshot;
    private int selectedByte;

    /** Creates a peripheral backed by the system wall clock in milliseconds. */
    public ClockPeripheral() {
        this(System::currentTimeMillis);
    }

    /** Creates a peripheral with a clock source, primarily for deterministic use. */
    public ClockPeripheral(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        reset();
    }

    /** Clears the latched timestamp and selects byte zero. */
    public void reset() {
        snapshot = 0;
        selectedByte = 0;
    }

    /**
     * Selects timestamp byte 0..7. Command zero captures a fresh timestamp;
     * other valid commands select bytes from that same snapshot. Invalid values
     * leave both the snapshot and current selection unchanged.
     */
    public void command(int value) {
        if (value < 0 || value > 7) return;
        if (value == 0) snapshot = clock.getAsLong();
        selectedByte = value;
    }

    /** Returns the selected timestamp byte; the caller interprets it unsigned. */
    public byte read() {
        return (byte) (snapshot >>> (selectedByte * Byte.SIZE));
    }
}
