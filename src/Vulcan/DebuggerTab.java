/** Owns the debugger view, CPU controls, and source mapping. */
package Vulcan;

import Intel8080.assembler.Assembler;
import Intel8080.cpu.CPUStateListener;
import Intel8080.cpu.Intel8080;
import Intel8080.cpu.Memory;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class DebuggerTab extends JPanel implements CPUStateListener {
    private final SettingsManager settings;
    private final WorkspaceViews views = new WorkspaceViews();
    private final EmulationSession session = EmulationSession.SHARED;
    private long loadedRevision;
    private boolean executionStarted;
    private final Memory memory;
    private final Intel8080 cpu;
    private final DisassemblerPanel disassembler;
    private final MemoryPanel memoryPanel;
    private final DebugConsolePanel debugConsole;
    private final JLabel fileLabel;
    private final RegistersPanel registersPanel;
    private final JButton runButton;
    private final JButton stopButton;
    private final JButton pauseButton;
    private final JButton stepButton;
    private final JButton resetButton;
    private final Timer controlsTimer;
    private final FileSystemPanel fileSystem;
    private java.util.function.Consumer<Path> deletionListener = path -> {};
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Vulcan-Debugger-CPU");
        thread.setDaemon(true);
        return thread;
    });

    private volatile ProgramInfo programInfo = ProgramInfo.empty();
    private volatile Path programFile;
    private final AtomicBoolean runtimeErrorShown = new AtomicBoolean();
    private int lastPc;
    private volatile boolean shuttingDown;

    /** Creates the debugger workspace with its CPU, memory, and views. */
    public DebuggerTab(SettingsManager settings) {
        this.settings = settings;
        memory = session.memory;
        cpu = session.cpu;
        lastPc = cpu.getPC() & 0xFFFF;
        setLayout(new BorderLayout());
        setBackground(settings.getColor("panelBackground"));

        registersPanel = new RegistersPanel(cpu, settings);
        disassembler = new DisassemblerPanel(cpu, settings);
        memoryPanel = new MemoryPanel(cpu, settings);
        debugConsole = new DebugConsolePanel(settings);
        debugConsole.setInputHandler(line -> session.submitInput(this, line));
        fileLabel = new JLabel("");
        fileLabel.setFont(new Font(settings.getApplicationFont(), Font.BOLD, settings.getFontSize("debuggerFilename")));
        fileLabel.setForeground(settings.getColor("text"));
        fileSystem = new FileSystemPanel(settings, this::debug, "debuggerFilePanel");
        fileSystem.setDeletionListener(path -> deletionListener.accept(path));

        JPanel header = new JPanel(new BorderLayout());
        fileLabel.setHorizontalAlignment(SwingConstants.CENTER);
        header.add(fileLabel, BorderLayout.CENTER);
        runButton = controlButton(Icons.vector("run", 16, new Color(47, 184, 92)), "Run", () -> {
            if (cpu.isPaused()) continueExecution(); else run();
        });
        stopButton = controlButton(Icons.vector("stop", 16, settings.getColor("stopButton")), "Stop", this::stop);
        pauseButton = controlButton(Icons.vector("pause", 16, settings.getColor("text")), "Pause", this::pause);
        stepButton = controlButton(Icons.fit(Icons.STEP_ICON, 16, 16), "Step", this::step);
        resetButton = controlButton(Icons.fit(Icons.RESET_ICON, 16, 16), "Reset", this::reset);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        controls.setOpaque(false);
        for (JButton button : new JButton[]{runButton, stopButton, pauseButton, stepButton, resetButton}) controls.add(button);
        header.add(controls, BorderLayout.EAST);
        header.setBorder(BorderFactory.createEmptyBorder(4, 7, 4, 7));
        controlsTimer = new Timer(120, event -> refreshControlState());
        addHierarchyListener(event -> { if (isShowing()) controlsTimer.start(); else controlsTimer.stop(); });
        refreshControlState();

        JSplitPane codeSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, disassembler, memoryPanel);
        codeSplit.setDividerLocation(650);
        codeSplit.setContinuousLayout(true);
        codeSplit.setBorder(null);
        Theme.initializeDivider(codeSplit, 0.45);

        JSplitPane right = new JSplitPane(JSplitPane.VERTICAL_SPLIT, codeSplit, debugConsole);
        right.setDividerLocation(600);
        right.setContinuousLayout(true);
        right.setBorder(null);
        right.setResizeWeight(0.78);
        Theme.initializeDivider(right, 0.78);

        JSplitPane main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, registersPanel, right);
        main.setDividerLocation(220);
        main.setContinuousLayout(true);
        main.setBorder(null);

        add(header, BorderLayout.NORTH);
        JSplitPane workspace = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, fileSystem, main);
        workspace.setDividerLocation(220);
        workspace.setResizeWeight(0.0);
        workspace.setBorder(null);
        views.addSplit("Files", workspace, true);
        views.addSplit("Registers", main, true);
        views.addSplit("Instructions", codeSplit, true);
        views.addSplit("Memory", codeSplit, false);
        views.addConsole("Console", right, false, debugConsole);
        add(workspace, BorderLayout.CENTER);
        cpu.addStateListener(this);
    }

    /** Creates an icon-only debugger action with a hover name. */
    private JButton controlButton(Icon icon, String name, Runnable action) {
        JButton button = new JButton(icon);
        button.setToolTipText(name);
        button.setPreferredSize(new Dimension(31, 27));
        button.setBorder(BorderFactory.createEmptyBorder(4, 7, 4, 7));
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.addActionListener(event -> { action.run(); refreshControlState(); });
        return button;
    }

    /** Enables debugger controls according to the loaded program and execution state. */
    private void refreshControlState() {
        boolean loaded = programInfo.loaded();
        boolean running = cpu.isRunning();
        boolean paused = cpu.isPaused();
        runButton.setEnabled(loaded && (!running || paused));
        runButton.setToolTipText(paused ? "Continue" : "Run");
        stopButton.setEnabled(loaded && (running || paused));
        pauseButton.setEnabled(loaded && running && !paused);
        stepButton.setEnabled(loaded && (!running || paused) && !cpu.isStopped() && !cpu.isHalted());
        resetButton.setEnabled(loaded);
    }

    /** Loads a supported program file and its source mappings into the debugger. */
    public void debug(Path path) {
        if (path == null || !Files.isRegularFile(path)) return;
        Path absolute = path.toAbsolutePath().normalize();
        String name = absolute.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        boolean assembly = name.endsWith(".asm");
        boolean executable = name.endsWith(".exe");
        boolean cSource = name.endsWith(".c");
        if (!assembly && !executable && !cSource) return;

        stop();
        if (cSource) {
            clearProgram();
            settings.setCurrentFile(absolute);
            fileLabel.setText(absolute.getFileName().toString());
            return;
        }

        try {
            settings.setCurrentFile(absolute);
            byte[] image;
            String sourceText = null;
            ProgramInfo info;

            if (name.endsWith(".asm")) {
                sourceText = Files.readString(absolute, StandardCharsets.UTF_8);
                Assembler.AssemblyResult result = Assembler.assembleWithInfo(sourceText);
                image = result.image();
                for (String warning : result.warnings()) debugConsole.append("Warning: " + warning + "\n");
                Assembler.AssemblyInfo asmInfo = result.info();
                info = AsmSourceMap.build(
                        sourceText,
                        asmInfo.programStart(),
                        asmInfo.programEnd(),
                        asmInfo.codeStart(),
                        asmInfo.codeEnd(),
                        asmInfo.dataStart(),
                        asmInfo.dataEnd());
            } else if (name.endsWith(".exe")) {
                byte[] bytes = Files.readAllBytes(absolute);
                if (bytes.length > Memory.MEM_SIZE - Memory.PROGRAM_START) throw new IllegalArgumentException("Executable is too large for memory.");
                image = new byte[Memory.MEM_SIZE];
                System.arraycopy(bytes, 0, image, Memory.PROGRAM_START, bytes.length);
                Map<Integer, Integer> addressToLine = Map.of();
                Map<Integer, Integer> lineToAddress = Map.of();
                info = new ProgramInfo(Memory.PROGRAM_START,
                        Memory.PROGRAM_START + bytes.length - 1,
                        Memory.PROGRAM_START,
                        Memory.PROGRAM_START + bytes.length - 1,
                        -1, -1, addressToLine, lineToAddress);
            } else return;

            runtimeErrorShown.set(false);
            loadedRevision = session.load(this, image, info.start(), info.end(), info.codeStart(), debugConsole::appendByte);
            executionStarted = false;
            lastPc = cpu.getPC() & 0xFFFF;
            programFile = absolute;
            programInfo = info;
            fileLabel.setText(absolute.getFileName().toString());
            disassembler.setProgramRange(programInfo.codeStart(), programInfo.codeEnd());
            memoryPanel.setProgramRange(programInfo.start(), programInfo.end());
            refreshControlState();
        } catch (Exception e) {
            clearProgram();
            debugConsole.appendError(VulcanDialog.errorSummary(e) + "\n");
        }
    }

    /** Loads the current assembly file into the debugger. */
    public void assemble() {
        Path source = settings.getCurrentFile();
        if (source != null) debug(source);
    }

    /** Unloads the current program and clears debugger state. */
    public void clearProgram() {
        runtimeErrorShown.set(false);
        programInfo = ProgramInfo.empty();
        programFile = null;
        fileLabel.setText("");
        session.stop(this);
        if (session.owns(this)) cpu.reset();
        disassembler.clear();
        memoryPanel.clear();
        refreshControlState();
    }

    /** Applies the current theme colors and refreshes this component. */
    public void refreshTheme() {
        Theme.apply(this, settings);
        fileLabel.setFont(new Font(settings.getApplicationFont(), Font.BOLD, settings.getFontSize("debuggerFilename")));
        fileLabel.setForeground(settings.getColor("text"));
        fileSystem.refreshTheme();
        registersPanel.refreshTheme();
        disassembler.refreshTheme();
        memoryPanel.refreshTheme();
        debugConsole.refreshTheme();
        stopButton.setIcon(Icons.vector("stop", 16, settings.getColor("stopButton")));
        pauseButton.setIcon(Icons.vector("pause", 16, settings.getColor("text")));
        repaint();
        revalidate();
    }

    /** Returns the memory shared by the debugger and display. */
    public Memory getMemory() { return memory; }
    WorkspaceViews getViews() { return views; }
    ConsolePanel getConsole() { return debugConsole; }
    public Path getProgramFile() { return programFile; }

    /** Returns the CPU shared by the debugger and display. */
    public Intel8080 getCpu() { return cpu; }

    /** Stops the program currently executing on the debugger CPU. */
    public void stop() { session.stop(this); }

    /** Stops execution and clears debugger output during application shutdown. */
    public void shutdown() {
        shuttingDown = true;
        controlsTimer.stop();
        stop();
        executionStarted = false;
        debugConsole.clear();
        executor.shutdownNow();
    }

    /** Resets CPU registers and refreshes the visible debugger state. */
    public void reset() {
        stop();
        executionStarted = false;
        runtimeErrorShown.set(false);
        if (!session.owns(this)) { if (programFile != null) debug(programFile); return; }
        session.reset(this, programInfo.loaded() ? programInfo.codeStart() : Memory.PROGRAM_START);
        if (programInfo.loaded()) {
            memoryPanel.refresh();
            disassembler.refresh();
        }
    }

    /** Starts the loaded program on the debugger's worker executor. */
    public void run() {
        if (!programInfo.loaded()) return;
        if (!session.owns(this) && programFile != null) debug(programFile);
        if (cpu.isPaused()) { continueExecution(); return; }
        if (cpu.isRunning()) reset();
        if (cpu.isStopped() || cpu.isHalted()) reset();
        debugConsole.clear();
        executionStarted = true;
        runtimeErrorShown.set(false);
        long revision = session.revision();
        executor.submit(() -> {
            try { session.run(this, revision); }
            catch (RuntimeException e) { SwingUtilities.invokeLater(() -> debugConsole.appendError(VulcanDialog.errorSummary(e) + "\n")); }
        });
    }

    /** Executes one instruction while the program is stopped or paused. */
    public void step() {
        if (!programInfo.loaded()) return;
        if (!session.owns(this) && programFile != null) debug(programFile);
        if (!executionStarted) { debugConsole.clear(); executionStarted = true; }
        cpu.step();
    }

    /** Pauses the currently executing program. */
    public void pause() { if (session.owns(this)) cpu.pause(); }

    /** Resumes a paused program or starts it when it is stopped. */
    public void continueExecution() {
        if (!programInfo.loaded()) return;
        long revision = session.revision();
        executor.submit(() -> {
            try {
                session.resume(this, revision);
            } catch (RuntimeException e) {
                SwingUtilities.invokeLater(() -> debugConsole.appendError(VulcanDialog.errorSummary(e) + "\n"));
            }
        });
    }

    /** Sets the callback notified when a file is deleted from this panel. */
    public void setDeletionListener(java.util.function.Consumer<Path> listener) { deletionListener = listener == null ? path -> {} : listener; }

    /** Routes browser moves to the shared workspace without resetting the debugger. */
    public void setMoveListener(java.util.function.BiConsumer<Path, Path> listener) { fileSystem.setMoveListener(listener); }

    /** Keeps the loaded program's filename synchronized after a file or folder move. */
    public void relocateFile(Path source, Path destination) {
        if (programFile != null && programFile.startsWith(source)) {
            programFile = destination.resolve(source.relativize(programFile));
            fileLabel.setText(programFile.getFileName().toString());
        }
    }

    /** Loads a supported file only when it differs from the debugger's current program. */
    public void selectFile(Path path) {
        if (path == null || !Files.isRegularFile(path)) return;
        Path absolute = path.toAbsolutePath().normalize();
        if (absolute.equals(programFile) && session.owns(this)) return;
        debug(absolute);
    }

    /** Receives CPU state notifications and schedules the matching UI refresh. */
    @Override
    public void cpuStateChanged() {
        if (shuttingDown || !session.owns(this)) return;
        String runtimeError = cpu.getRuntimeError();
        if (runtimeError != null && runtimeErrorShown.compareAndSet(false, true)) {
            String errorSummary = VulcanDialog.errorSummary(new IllegalStateException(runtimeError));
            SwingUtilities.invokeLater(() -> {
                if (!shuttingDown) debugConsole.appendError(errorSummary + "\n");
            });
        }

    }
}
