/** Execution boundary: OUT 0x00 is Vulcan user output; other ports belong to emulator controls. */
package Vulcan;

import Intel8080.assembler.Assembler;
import Intel8080.cpu.Intel8080;
import Intel8080.cpu.Memory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class ProgramRunner {
    private static final int EXECUTABLE_LOAD_ADDRESS = Memory.PROGRAM_START;
    private final EmulationSession session = EmulationSession.SHARED;
    private final Intel8080 cpu = session.cpu;
    private final Object lifecycleLock = new Object();
    private volatile long runGeneration;
    private static final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "Vulcan-8080-Runner");
        thread.setDaemon(true);
        return thread;
    });

    /** Assembles and runs source code, forwarding output to the supplied listeners. */
    public void runSource(String source, Consumer<String> output) { runSource(source, output, b -> {}, () -> {}); }

    /** Assembles and runs source code, forwarding output to the supplied listeners. */
    public void runSource(String source, Consumer<String> output, Runnable finished) {
        runSource(source, output, b -> {}, finished);
    }

    /** Assembles and runs source code, forwarding output to the supplied listeners. */
    public void runSource(String source, Consumer<String> output, Consumer<Integer> byteOutput, Runnable finished) {
        Consumer<String> textOutput = output == null ? text -> { } : output;
        Consumer<Integer> byteListener = byteOutput == null ? value -> { } : byteOutput;
        Runnable completion = finished == null ? () -> { } : finished;
        long generation = replaceActiveRun();
        Consumer<String> currentTextOutput = text -> {
            if (generation == runGeneration) textOutput.accept(text);
        };
        Consumer<Integer> currentByteOutput = value -> {
            if (generation == runGeneration) byteListener.accept(value);
        };
        try {
            Assembler.AssemblyResult assembly = Assembler.assembleWithInfo(source == null ? "" : source);
            byte[] image = assembly.image();
            for (String warning : assembly.warnings()) currentTextOutput.accept("Warning: " + warning + "\n");
            startImage(image, assembly.info().programStart(), assembly.info().programEnd(), assembly.info().codeStart(), currentTextOutput, currentByteOutput, completion, generation);
        } catch (RuntimeException e) {
            currentTextOutput.accept("\u001B[31m" + formatError(e) + "\u001B[0m");
            completion.run();
        }
    }

    /** Loads and runs an executable file, forwarding output to the supplied listeners. */
    public void runBinary(Path path, Consumer<String> output) { runBinary(path, output, b -> {}, () -> {}); }

    /** Loads and runs an executable file, forwarding output to the supplied listeners. */
    public void runBinary(Path path, Consumer<String> output, Runnable finished) {
        runBinary(path, output, b -> {}, finished);
    }

    /** Loads and runs an executable file, forwarding output to the supplied listeners. */
    public void runBinary(Path path, Consumer<String> output, Consumer<Integer> byteOutput, Runnable finished) {
        Consumer<String> textOutput = output == null ? text -> { } : output;
        Consumer<Integer> byteListener = byteOutput == null ? value -> { } : byteOutput;
        Runnable completion = finished == null ? () -> { } : finished;
        long generation = replaceActiveRun();
        Consumer<String> currentTextOutput = text -> {
            if (generation == runGeneration) textOutput.accept(text);
        };
        Consumer<Integer> currentByteOutput = value -> {
            if (generation == runGeneration) byteListener.accept(value);
        };
        try {
            if (!Files.isRegularFile(path)) {
                currentTextOutput.accept("File not found.\n");
                completion.run();
                return;
            }
            byte[] bytes = Files.readAllBytes(path);
            byte[] image = new byte[Memory.MEM_SIZE];
            if (bytes.length > image.length - EXECUTABLE_LOAD_ADDRESS) {
                throw new IllegalArgumentException("Executable is too large for memory.");
            }
            System.arraycopy(bytes, 0, image, EXECUTABLE_LOAD_ADDRESS, bytes.length);
            startImage(image, EXECUTABLE_LOAD_ADDRESS, EXECUTABLE_LOAD_ADDRESS + bytes.length - 1, EXECUTABLE_LOAD_ADDRESS, currentTextOutput, currentByteOutput, completion, generation);
        } catch (Exception e) {
            currentTextOutput.accept("\u001B[31m" + VulcanDialog.errorSummary(e) + "\n\u001B[0m");
            completion.run();
        }
    }

    private void startImage(byte[] image, int start, int end, int entry, Consumer<String> output,
                            Consumer<Integer> byteOutput, Runnable finished, long generation) {
        final long loadedRevision;
        synchronized (lifecycleLock) {
            if (generation != runGeneration) { finished.run(); return; }
            loadedRevision = session.load(this, image, start, end, entry, byteOutput);
        }
        executor.submit(() -> {
            try {
                synchronized (lifecycleLock) {
                    if (generation != runGeneration || !session.run(this, loadedRevision)) return;
                }
                while (generation == runGeneration && session.owns(this) &&
                        session.revision() == loadedRevision && cpu.isRunning()) Thread.sleep(10);
                if (generation == runGeneration && session.owns(this) && cpu.getRuntimeError() != null)
                    output.accept("\u001B[31m" + cpu.getRuntimeError() + "\n\u001B[0m");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally { finished.run(); }
        });
    }

    /** Stops this runner's program while preserving application memory. */
    public void stop() {
        synchronized (lifecycleLock) { runGeneration++; session.stop(this); }
    }

    private long replaceActiveRun() {
        synchronized (lifecycleLock) { runGeneration++; session.stop(this); return runGeneration; }
    }

    /** Assembles a source file and writes its executable image beside the source. */
    public static Path assembleToBinary(Path sourceFile) throws Exception {
        return assembleToBinary(sourceFile, null);
    }

    /** Assembles a source file and writes its executable image beside the source. */
    public static Path assembleToBinary(Path sourceFile, String requestedName) throws Exception {
        if (sourceFile == null || !Files.isRegularFile(sourceFile)) throw new IllegalArgumentException("Assembly file not found.");
        Assembler.AssemblyResult result = Assembler.assembleWithInfo(Files.readString(sourceFile));
        byte[] image = result.image();
        Assembler.AssemblyInfo assembly = result.info();
        int start = assembly.programStart();
        int end = assembly.programEnd();
        if (start < 0) start = Memory.PROGRAM_START;
        if (start >= image.length || end < start || end >= image.length) {
            throw new IllegalArgumentException("The source did not produce a valid program image.");
        }
        byte[] executable = Arrays.copyOfRange(image, start, end + 1);

        String base;
        if (requestedName == null || requestedName.isBlank()) {
            String name = sourceFile.getFileName().toString();
            int dot = name.lastIndexOf('.');
            base = dot > 0 ? name.substring(0, dot) : name;
        } else base = requestedName.trim();
        if (base.toLowerCase().endsWith(".exe")) base = base.substring(0, base.length() - 4);
        if (base.isBlank()) base = "program";
        if (base.equals(".") || base.equals("..") || base.matches(".*[\\\\/:*?\"<>|].*")) {
            throw new IllegalArgumentException("The output name must be a file name without path separators or reserved characters.");
        }

        Path output = sourceFile.resolveSibling(base + ".exe");
        Path absolute = output.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        Path temporary = Files.createTempFile(parent, ".vulcan-assemble-", ".tmp");
        try {
            Files.write(temporary, executable);
            try {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return output;
    }

    /** Reports whether this runner currently has an executing CPU. */
    public boolean isRunning() {
        Intel8080 instance = cpu;
        return session.owns(this) && instance.isRunning();
    }

    /** Sends a line to this runner only while it still owns the shared CPU. */
    public boolean submitInput(String line) { return session.submitInput(this, line); }

    private static String formatError(RuntimeException e) {
        return VulcanDialog.errorSummary(e) + "\n";
    }
}
