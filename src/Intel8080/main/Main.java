package Intel8080.main;

import Intel8080.assembler.Assembler;
import Intel8080.cpu.Intel8080;
import Intel8080.cpu.Memory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Command-line CPU runner: assembles one source file, executes it until STOP or HLT, prints
 * console port output, and reports instructions, guest cycles, and host throughput.
 * Usage: {@code java Intel8080.main.Main program.asm [maxInstructions]}.
 */
public final class Main {
    private static final long DEFAULT_INSTRUCTION_LIMIT = 500_000_000L;

    private Main() {}

    /** Runs the program named by the first argument and exits with status 0, or 1 on a CPU error or limit. */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: Main <program.asm> [maxInstructions]");
            System.exit(2);
        }
        long limit = args.length > 1 ? Long.parseLong(args[1]) : DEFAULT_INSTRUCTION_LIMIT;
        Result result = run(Files.readString(Path.of(args[0])), limit);
        System.out.printf(Locale.ROOT, "%n%,d instructions, %,d guest cycles in %.3f s (%.1f MIPS, %.1f MHz equivalent)%n",
                result.instructions(), result.cycles(), result.seconds(),
                result.instructions() / Math.max(1e-9, result.seconds()) / 1e6,
                result.cycles() / Math.max(1e-9, result.seconds()) / 1e6);
        if (result.error() != null) System.err.println(result.error());
        System.exit(result.error() == null && result.finished() ? 0 : 1);
    }

    /** Assembles and single-steps a program, echoing console output, and returns its execution statistics. */
    public static Result run(String source, long instructionLimit) {
        Assembler.FlatResult program = Assembler.assembleFlat(source);
        Memory memory = new Memory();
        program.segments().forEach(segment -> memory.load(segment.address(), segment.bytes()));
        Intel8080 cpu = new Intel8080(memory);
        try {
            cpu.addPortOutputListener((port, value) -> {
                if (port == Intel8080.PORT_KEY_DATA) System.out.print((char) (value & 0xff));
            });
            cpu.setEntryAddress(program.initialCodeAddress());
            long instructions = 0;
            long cycles = 0;
            String error = null;
            long started = System.nanoTime();
            try {
                while (!cpu.isStopped() && !cpu.isHalted() && instructions < instructionLimit) {
                    cpu.step();
                    cycles += cpu.lastInstructionCycles();
                    instructions++;
                }
            } catch (RuntimeException failure) {
                error = failure.getMessage();
            }
            double seconds = (System.nanoTime() - started) / 1e9;
            return new Result(instructions, cycles, seconds, cpu.isStopped() || cpu.isHalted(), error);
        } finally {
            cpu.close();
        }
    }

    /** Execution statistics of one run; finished is true when the program reached STOP or HLT. */
    public record Result(long instructions, long cycles, double seconds, boolean finished, String error) {}
}
