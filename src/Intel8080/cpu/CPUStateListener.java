package Intel8080.cpu;

/** Receives notifications when the CPU starts, pauses, stops, or changes state. */
@FunctionalInterface
public interface CPUStateListener {

    void cpuStateChanged();
}
