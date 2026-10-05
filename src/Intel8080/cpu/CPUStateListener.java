package Intel8080.cpu;

/** Receives notifications after the emulated CPU changes state. */
@FunctionalInterface
public interface CPUStateListener {
    /** Called after execution, stepping, pausing, stopping, or resetting. */
    void cpuStateChanged();
}
