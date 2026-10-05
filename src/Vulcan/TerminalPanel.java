package Vulcan;

import javax.swing.*;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

public class TerminalPanel extends JPanel {
    private final SettingsManager settings;
    private final JTextArea terminal;
    private final ProgramRunner runner = new ProgramRunner();
    private final List<String> history = new ArrayList<>();
    private final ExecutorService processExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Vulcan-Terminal");
        t.setDaemon(true);
        return t;
    });
    private final Object outputLock = new Object();
    private final StringBuilder pendingOutput = new StringBuilder();
    private final AtomicLong commandGeneration = new AtomicLong();
    private boolean outputFlushScheduled;
    private final Timer outputTimer = new Timer(33, event -> flushPendingOutput());
    private static final int MAX_OUTPUT_CHARS = 65_536;

    private int historyIndex;
    private int promptStart;
    private Path workingDirectory;
    private volatile Process activeProcess;
    private volatile boolean closed;
    private Runnable refreshFiles = () -> {};
    private java.util.function.Consumer<Path> deletionListener = path -> {};
    private boolean commandRunning;

    /** Creates an interactive terminal using the shared settings manager. */
    public TerminalPanel(SettingsManager settings) {
        this.settings = settings;
        outputTimer.setRepeats(false);
        setLayout(new BorderLayout());
        workingDirectory = settings.getTerminalWorkingDirectory();
        if (!Files.isDirectory(workingDirectory)) workingDirectory = settings.getCurrentDirectory();

        terminal = new JTextArea();
        terminal.setEditable(true);
        terminal.setLineWrap(false);
        terminal.setFont(new Font(settings.getEditorFont(), Font.PLAIN, settings.getFontSize("terminal")));
        terminal.setMargin(new Insets(7, 9, 7, 9));
        terminal.setBorder(null);
        terminal.setTabSize(4);
        terminal.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                SwingUtilities.invokeLater(() -> {
                    if (terminal.getCaretPosition() < promptStart) terminal.setCaretPosition(terminal.getDocument().getLength());
                });
            }
        });
        terminal.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                SwingUtilities.invokeLater(() -> {
                    if (terminal.getCaretPosition() < promptStart) terminal.setCaretPosition(promptStart);
                });
            }
        });

        JScrollPane scroll = new JScrollPane(terminal);
        scroll.setBorder(null);
        Theme.styleScrollBar(scroll.getVerticalScrollBar(), settings);
        Theme.styleScrollBar(scroll.getHorizontalScrollBar(), settings);
        add(scroll, BorderLayout.CENTER);

        InputMap input = terminal.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actions = terminal.getActionMap();
        input.put(KeyStroke.getKeyStroke("TAB"), "vulcan-complete");
        actions.put("vulcan-complete", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { autocomplete(); }
        });
        input.put(KeyStroke.getKeyStroke("ENTER"), "vulcan-enter");
        actions.put("vulcan-enter", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { executeCommand(); }
        });
        input.put(KeyStroke.getKeyStroke("UP"), "vulcan-up");
        actions.put("vulcan-up", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { showPreviousHistory(); }
        });
        input.put(KeyStroke.getKeyStroke("DOWN"), "vulcan-down");
        actions.put("vulcan-down", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { showNextHistory(); }
        });
        input.put(KeyStroke.getKeyStroke("HOME"), "vulcan-home");
        actions.put("vulcan-home", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { terminal.setCaretPosition(promptStart); }
        });
        input.put(KeyStroke.getKeyStroke("LEFT"), "vulcan-left");
        actions.put("vulcan-left", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (terminal.getCaretPosition() > promptStart) terminal.setCaretPosition(terminal.getCaretPosition() - 1);
            }
        });
        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "vulcan-bs");
        terminal.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "vulcan-bs");
        terminal.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE && e.getModifiersEx() == 0 &&
                        terminal.getSelectionStart() == terminal.getSelectionEnd() &&
                        terminal.getCaretPosition() <= promptStart) e.consume();
            }
        });
        actions.put("vulcan-bs", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (commandRunning) return;
                int caret = terminal.getCaretPosition();
                if (caret > promptStart) {
                    try { terminal.getDocument().remove(caret - 1, 1); }
                    catch (Exception ignored) { }
                }
            }
        });
        input.put(KeyStroke.getKeyStroke("DELETE"), "vulcan-del");
        actions.put("vulcan-del", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (commandRunning) return;
                int caret = terminal.getCaretPosition();
                if (caret >= promptStart && caret < terminal.getDocument().getLength()) {
                    try { terminal.getDocument().remove(caret, 1); }
                    catch (Exception ignored) { }
                }
            }
        });

        terminal.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { enforceCommandArea(e); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { enforceCommandArea(e); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { }
        });
        appendPrompt();
    }

    private void enforceCommandArea(javax.swing.event.DocumentEvent event) {
        if (commandRunning || event.getOffset() >= promptStart) return;
        SwingUtilities.invokeLater(() -> {
            try {
                if (terminal.getCaretPosition() < promptStart) terminal.setCaretPosition(promptStart);
            } catch (Exception ignored) { }
        });
    }

    /** Sets the callback used to refresh the project file view. */
    public void setRefreshListener(Runnable listener) { refreshFiles = listener == null ? () -> {} : listener; }
    /** Sets the callback notified when a file is deleted from this panel. */
    public void setDeletionListener(java.util.function.Consumer<Path> listener) { deletionListener = listener == null ? path -> {} : listener; }

    /** Stops the active program or shell command. */
    public void stop() {
        commandGeneration.incrementAndGet();
        runner.stop();
        Process process = activeProcess;
        if (process != null) {
            process.destroy();
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    /** Stops active work and releases resources owned by this component. */
    public void close() {
        closed = true;
        stop();
        processExecutor.shutdownNow();
        outputTimer.stop();
    }

    /** Synchronizes the terminal directory with the project browser selection. */
    public void setWorkingDirectoryFromFilePanel(Path path) {
        if (path == null || !Files.isDirectory(path)) return;
        workingDirectory = path.toAbsolutePath().normalize();
        settings.setTerminalWorkingDirectory(workingDirectory);
    }

    /** Changes the terminal working directory and displays a new prompt. */
    public void setWorkingDirectory(Path path) {
        if (path == null || !Files.isDirectory(path)) return;
        workingDirectory = path.toAbsolutePath().normalize();
        settings.setTerminalWorkingDirectory(workingDirectory);
        appendPromptAfterCommand();
        focusTerminal();
    }

    private void autocomplete() {
        if (commandRunning) return;
        try {
            String command = terminal.getDocument().getText(promptStart, terminal.getDocument().getLength() - promptStart);
            int end = terminal.getCaretPosition();
            if (end < promptStart) return;
            String beforeCaret = terminal.getDocument().getText(promptStart, end - promptStart);
            int start = beforeCaret.length();
            while (start > 0 && !Character.isWhitespace(beforeCaret.charAt(start - 1))) start--;
            String prefix = beforeCaret.substring(start);
            if (prefix.isEmpty()) return;
            List<Path> matches = new ArrayList<>();
            try (var stream = Files.list(workingDirectory)) {
                for (Path path : stream.toList()) {
                    if (path.getFileName().toString().startsWith(prefix)) matches.add(path);
                }
            }
            if (matches.size() != 1) return;
            String replacement = matches.get(0).getFileName().toString();
            int replaceStart = promptStart + start;
            terminal.getDocument().remove(replaceStart, prefix.length());
            terminal.getDocument().insertString(replaceStart, replacement, null);
            terminal.setCaretPosition(replaceStart + replacement.length());
        } catch (Exception ignored) { }
    }

    private void executeCommand() {
        if (commandRunning) return;
        terminal.setCaretPosition(terminal.getDocument().getLength());
        String command;
        try {
            Document doc = terminal.getDocument();
            command = doc.getText(promptStart, doc.getLength() - promptStart).trim();
        } catch (Exception e) { return; }

        terminal.append("\n");
        if (command.isEmpty()) {
            finishCommand();
            return;
        }

        history.add(command);
        historyIndex = history.size();
        List<String> args = tokenize(command);
        if (args.isEmpty()) {
            finishCommand();
            return;
        }

        String name = args.get(0).toLowerCase(Locale.ROOT);
        boolean asynchronous = false;
        try {
            switch (name) {
                case "clear", "cls" -> clearTerminalAndPrompt();
                case "pwd", "get-location" -> appendOutput(workingDirectory + "\n");
                case "cd", "set-location", "sl" -> changeDirectory(args);
                case "dir", "ls", "gci", "get-childitem" -> listDirectory(args);
                case "mkdir", "md", "ni", "new-item" -> newItem(args);
                case "rm", "del", "rmdir", "ri", "remove-item" -> removeItem(args);
                case "cp", "copy", "copy-item" -> copyOrMove(args, false);
                case "mv", "move", "move-item" -> copyOrMove(args, true);
                case "ren", "rename-item" -> renameItem(args);
                case "cat", "type", "gc", "get-content" -> cat(args);
                case "touch" -> touch(args);
                case "test-path" -> testPath(args);
                case "echo" -> echo(args);
                case "asm" -> assembleFile(args);
                case "help" -> appendOutput("cd ls pwd mkdir rm cp mv cat touch echo asm ./program\n");
                default -> {
                    Path local = findExecutable(args.get(0));
                    if (local != null) runBinary(local);
                    else { runShell(command, workingDirectory); asynchronous = true; }
                }
            }
        } catch (Exception e) {
            appendOutput(formatError(e) + "\n");
        }
        if (!asynchronous && !commandRunning) finishCommand();
        focusTerminal();
    }

    private Path findExecutable(String command) {
        String target = stripQuotes(command);
        if (target.startsWith("./") || target.startsWith(".\\")) target = target.substring(2);
        Path exact = workingDirectory.resolve(target).normalize();
        if (Files.isRegularFile(exact) && exact.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".exe")) return exact;
        Path exe = exact.resolveSibling(exact.getFileName() + ".exe");
        if (Files.isRegularFile(exe)) return exe;
        return null;
    }

    private void runBinary(Path path) {
        commandRunning = true;
        terminal.setEditable(false);
        runner.runBinary(path, this::appendOutput,
                value -> appendOutput(Character.toString((char) (value & 0xFF))), this::finishCommand);
    }

    private void assembleFile(List<String> args) {
        if (args.size() < 2) {
            appendOutput("usage: asm file.asm [-n name]\n");
            return;
        }

        String sourceName = null;
        String outputName = null;
        for (int i = 1; i < args.size(); i++) {
            if (args.get(i).equalsIgnoreCase("-n")) {
                if (i + 1 >= args.size()) {
                    appendOutput("usage: asm file.asm [-n name]\n");
                    return;
                }
                outputName = stripQuotes(args.get(++i));
            } else if (sourceName == null) sourceName = stripQuotes(args.get(i));
        }

        if (sourceName == null || !sourceName.toLowerCase(Locale.ROOT).endsWith(".asm")) {
            appendOutput("usage: asm file.asm [-n name]\n");
            return;
        }
        Path source = workingDirectory.resolve(sourceName).normalize();
        if (!Files.isRegularFile(source)) {
            appendOutput("file not found\n");
            return;
        }

        try {
            ProgramRunner.assembleToBinary(source, outputName);
        } catch (Exception e) {
            appendOutput("assembly error: " + formatError(e) + "\n");
        }
    }

    private void changeDirectory(List<String> args) {
        String target = args.size() < 2 ? "" : stripQuotes(String.join(" ", args.subList(1, args.size()))).trim();
        if (target.equals("~")) target = System.getProperty("user.home");
        if (target.isEmpty()) target = System.getProperty("user.home");
        Path path = Path.of(target);
        if (!path.isAbsolute()) path = workingDirectory.resolve(path);
        path = path.normalize().toAbsolutePath();
        if (!Files.isDirectory(path)) { appendOutput("directory not found\n"); return; }
        workingDirectory = path;
        settings.setTerminalWorkingDirectory(workingDirectory);
    }

    private void listDirectory(List<String> args) {
        Path directory = workingDirectory;
        if (args.size() > 1) {
            Path requested = Path.of(stripQuotes(String.join(" ", args.subList(1, args.size()))));
            if (!requested.isAbsolute()) requested = workingDirectory.resolve(requested);
            directory = requested.normalize();
        }
        if (!Files.isDirectory(directory)) { appendOutput("directory not found\n"); return; }
        try (var stream = Files.list(directory)) {
            stream.sorted((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()))
                    .forEach(path -> appendOutput((Files.isDirectory(path) ? "d " : "  ") + path.getFileName() + "\n"));
        } catch (IOException e) { appendOutput("directory error: " + VulcanDialog.errorSummary(e) + "\n"); }
    }

    private void newItem(List<String> args) {
        boolean directory = args.get(0).equalsIgnoreCase("mkdir") || args.get(0).equalsIgnoreCase("md");
        String name = args.size() > 1 ? stripQuotes(args.get(args.size() - 1)) : "";
        if (name.isBlank()) return;
        Path path = workingDirectory.resolve(name).normalize();
        try { if (directory) Files.createDirectories(path); else Files.createFile(path); }
        catch (Exception e) { appendOutput("create error: " + VulcanDialog.errorSummary(e) + "\n"); }
    }

    private void removeItem(List<String> args) {
        if (args.size() < 2) return;
        Path path = workingDirectory.resolve(stripQuotes(args.get(1))).normalize();
        try {
            ProjectFileOperations.deleteRecursively(path);
            deletionListener.accept(path.toAbsolutePath().normalize());
            refreshFiles.run();
        } catch (Exception e) { appendOutput("remove error: " + VulcanDialog.errorSummary(e) + "\n"); }
    }

    private void copyOrMove(List<String> args, boolean move) {
        if (args.size() < 3) return;
        Path source = workingDirectory.resolve(stripQuotes(args.get(1))).normalize();
        Path destination = workingDirectory.resolve(stripQuotes(args.get(2))).normalize();
        try {
            if (move) Files.move(source, destination);
            else ProjectFileOperations.copy(source, destination, false);
        } catch (Exception e) { appendOutput((move ? "move" : "copy") + " error: " + VulcanDialog.errorSummary(e) + "\n"); }
    }

    private void renameItem(List<String> args) {
        if (args.size() < 3) return;
        try { Files.move(workingDirectory.resolve(stripQuotes(args.get(1))).normalize(),
                workingDirectory.resolve(stripQuotes(args.get(2))).normalize()); }
        catch (IOException e) { appendOutput("rename error: " + VulcanDialog.errorSummary(e) + "\n"); }
    }

    private void cat(List<String> args) {
        if (args.size() < 2) return;
        try { appendOutput(Files.readString(workingDirectory.resolve(stripQuotes(args.get(1))).normalize())); }
        catch (Exception e) { appendOutput("read error: " + VulcanDialog.errorSummary(e) + "\n"); }
    }

    private void touch(List<String> args) {
        if (args.size() < 2) return;
        Path path = workingDirectory.resolve(stripQuotes(args.get(1))).normalize();
        try { if (!Files.exists(path)) Files.createFile(path); else Files.setLastModifiedTime(path, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis())); }
        catch (IOException e) { appendOutput("touch error: " + VulcanDialog.errorSummary(e) + "\n"); }
    }

    private void testPath(List<String> args) {
        appendOutput(Boolean.toString(args.size() > 1 && Files.exists(workingDirectory.resolve(stripQuotes(args.get(1))).normalize())) + "\n");
    }

    private void echo(List<String> args) {
        if (args.size() <= 1) appendOutput("\n");
        else appendOutput(String.join(" ", args.subList(1, args.size())) + "\n");
    }

    private void runShell(String command, Path directory) {
        if (closed) throw new RejectedExecutionException("The terminal is closed.");
        commandRunning = true;
        terminal.setEditable(false);
        long generation = commandGeneration.incrementAndGet();
        try {
            processExecutor.submit(() -> {
                Process process = null;
                try {
                    if (closed || commandGeneration.get() != generation || Thread.currentThread().isInterrupted()) return;
                    boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
                    ProcessBuilder builder = windows
                            ? new ProcessBuilder("cmd.exe", "/d", "/s", "/c", command)
                            : new ProcessBuilder("/bin/sh", "-c", command);
                    builder.directory(directory.toFile());
                    builder.redirectErrorStream(true);
                    process = builder.start();
                    activeProcess = process;
                    if (closed || commandGeneration.get() != generation || Thread.currentThread().isInterrupted()) {
                        process.destroyForcibly();
                        return;
                    }
                    process.getOutputStream().close();
                    StringBuilder buffer = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                        char[] chars = new char[1024];
                        int count;
                        while ((count = reader.read(chars)) != -1) {
                            if (closed || commandGeneration.get() != generation) {
                                process.destroyForcibly();
                                break;
                            }
                            buffer.append(chars, 0, count);
                            if (buffer.length() >= 1024) flushProcessOutput(buffer);
                        }
                    }
                    if (!buffer.isEmpty()) appendOutput(buffer.toString());
                    process.waitFor();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    if (process != null) process.destroyForcibly();
                } catch (Exception e) {
                    appendOutput("shell error: " + VulcanDialog.errorSummary(e) + "\n");
                } finally {
                    if (process != null && process.isAlive()) process.destroyForcibly();
                    if (activeProcess == process) activeProcess = null;
                    finishCommand();
                }
            });
        } catch (RejectedExecutionException e) {
            commandRunning = false;
            terminal.setEditable(true);
            throw e;
        }
    }

    private void flushProcessOutput(StringBuilder buffer) {
        String text = buffer.toString();
        buffer.setLength(0);
        appendOutput(text);
    }

    private void clearTerminalAndPrompt() {
        terminal.setText("");
        promptStart = 0;
    }

    private void finishCommand() {
        SwingUtilities.invokeLater(() -> {
            if (closed) return;
            flushPendingOutput();
            commandRunning = false;
            terminal.setEditable(true);
            refreshFiles.run();
            appendPrompt();
            focusTerminal();
        });
    }

    private void appendOutput(String text) {
        if (text == null || text.isEmpty()) return;
        synchronized (outputLock) {
            pendingOutput.append(text);
            if (pendingOutput.length() > MAX_OUTPUT_CHARS)
                pendingOutput.delete(0, pendingOutput.length() - MAX_OUTPUT_CHARS);
            if (outputFlushScheduled) return;
            outputFlushScheduled = true;
        }
        SwingUtilities.invokeLater(() -> { if (!closed) outputTimer.restart(); });
    }

    private void flushPendingOutput() {
        String text;
        synchronized (outputLock) {
            if (pendingOutput.isEmpty()) {
                outputFlushScheduled = false;
                return;
            }
            text = pendingOutput.toString();
            pendingOutput.setLength(0);
            outputFlushScheduled = false;
        }
        terminal.insert(text, terminal.getDocument().getLength());
        int excess = terminal.getDocument().getLength() - MAX_OUTPUT_CHARS;
        if (excess > 0) {
            try { terminal.getDocument().remove(0, excess); promptStart = Math.max(0, promptStart - excess); }
            catch (javax.swing.text.BadLocationException ignored) { }
        }
        terminal.setCaretPosition(terminal.getDocument().getLength());
    }

    private void appendPromptAfterCommand() {
        SwingUtilities.invokeLater(() -> {
            if (terminal.getDocument().getLength() > promptStart && !terminal.getText().endsWith("\n")) terminal.append("\n");
            appendPrompt();
        });
    }

    private void appendPrompt() {
        if (terminal.getDocument().getLength() > 0 && !terminal.getText().endsWith("\n")) terminal.append("\n");
        terminal.append(workingDirectory + "\n> ");
        promptStart = terminal.getDocument().getLength();
        terminal.moveCaretPosition(promptStart);
        terminal.setCaretPosition(promptStart);
    }

    private void showPreviousHistory() {
        if (commandRunning || history.isEmpty()) return;
        historyIndex = Math.max(0, historyIndex - 1);
        replaceCurrentCommand(history.get(historyIndex));
    }

    private void showNextHistory() {
        if (commandRunning || history.isEmpty()) return;
        historyIndex++;
        replaceCurrentCommand(historyIndex >= history.size() ? "" : history.get(historyIndex));
        if (historyIndex > history.size()) historyIndex = history.size();
    }

    private void replaceCurrentCommand(String command) {
        try {
            Document doc = terminal.getDocument();
            doc.remove(promptStart, doc.getLength() - promptStart);
            doc.insertString(doc.getLength(), command, null);
            terminal.setCaretPosition(doc.getLength());
        } catch (Exception ignored) { }
    }

    private void focusTerminal() {
        SwingUtilities.invokeLater(() -> {
            terminal.requestFocusInWindow();
            terminal.setCaretPosition(terminal.getDocument().getLength());
        });
    }

    /** Applies the current theme colors and refreshes this component. */
    public void refreshTheme() {
        terminal.setBackground(settings.getColor("editorBackground"));
        terminal.setForeground(settings.getColor("editorText"));
        terminal.setCaretColor(settings.getColor("editorText"));
        terminal.setFont(new Font(settings.getEditorFont(), Font.PLAIN, settings.getFontSize("terminal")));
        repaint();
    }

    private String formatError(Exception e) { return VulcanDialog.errorSummary(e); }

    private String stripQuotes(String text) {
        return text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")
                ? text.substring(1, text.length() - 1) : text;
    }

    private List<String> tokenize(String command) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (char c : command.toCharArray()) {
            if (c == '"') quoted = !quoted;
            else if (Character.isWhitespace(c) && !quoted) {
                if (!current.isEmpty()) { tokens.add(current.toString()); current.setLength(0); }
            } else current.append(c);
        }
        if (!current.isEmpty()) tokens.add(current.toString());
        return tokens;
    }
}
