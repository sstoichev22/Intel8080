package Intel8080.cpu;

/** Executes one supported 8080 opcode against its owning CPU. */
@FunctionalInterface
public interface OpcodeHandler {
    /** Applies the instruction to the supplied CPU state. */
    void execute(Intel8080 cpu);
}
