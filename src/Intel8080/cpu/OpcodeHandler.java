package Intel8080.cpu;

/** Executes one opcode against a CPU. */
@FunctionalInterface
public interface OpcodeHandler {

    void execute(Intel8080 cpu);
}
