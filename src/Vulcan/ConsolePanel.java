/** Raw-byte console: rendering modes transform the same byte stream instead of re-encoding displayed text. */
package Vulcan;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ConsolePanel extends JPanel {
    private static final int MAX_CONSOLE_BYTES = 32_768;
    private static final int MAX_PENDING_BYTES = 8_192;
    private static final int DRAIN_BATCH_BYTES = 4_096;
    private static final int OUTPUT_REFRESH_INTERVAL_MS = 100;
    private final SettingsManager settings;
    private final String fontKey;
    private final JTextPane output;
    private final JComboBox<String> encoding;
    private final JTextField inputField = new JTextField();
    private final JLabel titleLabel = new JLabel();
    private java.util.function.Predicate<String> inputHandler = text -> false;
    private final List<Integer> bytes = new ArrayList<>();
    private final List<int[]> errorRanges = new ArrayList<>();
    private final Deque<Integer> pendingBytes = new ArrayDeque<>();
    private final AtomicBoolean outputDrainScheduled = new AtomicBoolean();
    private final AtomicLong outputGeneration = new AtomicLong();
    private final Timer outputDrainTimer;
    private boolean rendering;
    private boolean asciiLockedByError;
    private long droppedPendingBytes;
    private boolean truncationNoticeAdded;

    /** Creates a program output console using the shared application settings. */
    public ConsolePanel(SettingsManager settings) {
        this(settings, "editorConsole");
    }

    /** Creates a console with an independent persisted font-size setting. */
    public ConsolePanel(SettingsManager settings, String fontKey) {
        this.settings = settings;
        this.fontKey = fontKey == null ? "editorConsole" : fontKey;
        outputDrainTimer = new Timer(OUTPUT_REFRESH_INTERVAL_MS, e -> drainPendingOutput());
        outputDrainTimer.setCoalesce(true);
        setLayout(new BorderLayout());
        setBorder(null);
        output = new JTextPane();
        output.setEditable(false);
        output.setFont(new Font(settings.getEditorFont(), Font.PLAIN, settings.getFontSize(this.fontKey)));
        output.setBorder(BorderFactory.createEmptyBorder(7, 9, 7, 9));
        output.setBackground(settings.getColor("editorBackground"));
        output.setForeground(settings.getColor("editorText"));

        encoding = new JComboBox<>(new String[]{"ASCII", "Hex", "Decimal"});
        encoding.addActionListener(e -> refreshView());
        JPanel controls = new JPanel(new BorderLayout(5, 0));
        controls.setBorder(BorderFactory.createEmptyBorder(3, 9, 3, 5));
        controls.add(titleLabel, BorderLayout.WEST);
        controls.add(encoding, BorderLayout.EAST);

        JScrollPane scroll = new JScrollPane(output);
        scroll.setBorder(null);
        Theme.styleScrollBar(scroll.getVerticalScrollBar(), settings);
        Theme.styleScrollBar(scroll.getHorizontalScrollBar(), settings);
        add(controls, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        inputField.setFont(output.getFont());
        inputField.setBorder(BorderFactory.createEmptyBorder(5, 9, 5, 9));
        inputField.setToolTipText("Program input: type a line and press Enter (IN 1 checks readiness; IN 0 reads bytes).");
        inputField.addActionListener(event -> {
            if (inputHandler.test(inputField.getText())) inputField.setText("");
        });
        JPanel inputBar = new JPanel(new BorderLayout(6, 0));
        inputBar.setBorder(BorderFactory.createEmptyBorder(0, 9, 0, 0));
        inputBar.add(new JLabel("Input"), BorderLayout.WEST);
        inputBar.add(inputField, BorderLayout.CENTER);
        add(inputBar, BorderLayout.SOUTH);
    }

    /** Routes submitted input to the program belonging to this console. */
    public void setInputHandler(java.util.function.Predicate<String> handler) {
        inputHandler = handler == null ? text -> false : handler;
    }

    /** Adds a workspace console title beside its output-format control. */
    public void setTitle(String title) { titleLabel.setText(title); }

    /** Appends a character received from the emulated output port. */
    public void appendByte(int value) {
        synchronized (pendingBytes) {
            if (pendingBytes.size() >= MAX_PENDING_BYTES) {
                pendingBytes.removeFirst();
                droppedPendingBytes++;
            }
            pendingBytes.addLast(value & 0xFF);
        }
        scheduleOutputDrain();
    }

    /** Appends text to the console output. */
    public void append(String text) { appendInternal(text, false); }
    /** Appends error text using the console error style. */
    public void appendError(String text) { appendInternal(text, true); }

    private void appendInternal(String text, boolean error) {
        if (text == null || text.isEmpty()) return;
        long generation = outputGeneration.get();
        SwingUtilities.invokeLater(() -> {
            if (generation != outputGeneration.get()) return;
            int start = bytes.size();
            for (int i = 0; i < text.length(); i++) bytes.add(text.charAt(i) & 0xFF);
            // Runner failures may arrive as ANSI red output instead of appendError().
            // Treat those ranges as errors too so every console has identical diagnostics.
            if (error || text.contains("\u001B[31m") || text.contains("\u001B[91m")) errorRanges.add(new int[]{start, bytes.size()});
            if (error || text.contains("\u001B[31m") || text.contains("\u001B[91m")) lockEncodingToAscii();
            trimOutputBuffer();
            refreshView();
        });
    }

    /** Clears the console output. */
    public void clear() {
        outputGeneration.incrementAndGet();
        Runnable action = () -> {
            outputDrainTimer.stop();
            synchronized (pendingBytes) {
                pendingBytes.clear();
                droppedPendingBytes = 0;
                outputDrainScheduled.set(false);
            }
            bytes.clear();
            inputField.setText("");
            errorRanges.clear();
            output.setText("");
            asciiLockedByError = false;
            truncationNoticeAdded = false;
            encoding.setSelectedItem("ASCII");
            encoding.setEnabled(true);
            encoding.setVisible(true);
            Container parent = encoding.getParent();
            if (parent != null) {
                parent.revalidate();
                parent.repaint();
            }
        };
        if (SwingUtilities.isEventDispatchThread()) action.run(); else SwingUtilities.invokeLater(action);
    }

    /** Starts one coalesced Swing timer when queued program output needs rendering. */
    private void scheduleOutputDrain() {
        if (!outputDrainScheduled.compareAndSet(false, true)) return;
        SwingUtilities.invokeLater(() -> {
            if (!outputDrainTimer.isRunning()) outputDrainTimer.start();
        });
    }

    /** Applies one bounded output batch so a chatty program cannot monopolize the EDT. */
    private void drainPendingOutput() {
        List<Integer> batch = new ArrayList<>(DRAIN_BATCH_BYTES);
        long dropped;
        synchronized (pendingBytes) {
            while (batch.size() < DRAIN_BATCH_BYTES && !pendingBytes.isEmpty()) batch.add(pendingBytes.removeFirst());
            dropped = droppedPendingBytes;
            droppedPendingBytes = 0;
        }
        if (dropped > 0 && !truncationNoticeAdded) {
            appendTextBytes("\n[console output truncated]\n");
            truncationNoticeAdded = true;
        }
        for (int value : batch) bytes.add(value);
        if (dropped > 0 || !batch.isEmpty()) {
            trimOutputBuffer();
            refreshView();
        }

        boolean hasMore;
        synchronized (pendingBytes) { hasMore = !pendingBytes.isEmpty(); }
        if (!hasMore) {
            outputDrainTimer.stop();
            outputDrainScheduled.set(false);
            synchronized (pendingBytes) { hasMore = !pendingBytes.isEmpty(); }
            if (hasMore) scheduleOutputDrain();
        }
    }

    /** Appends an internal notice using the same byte representation as program output. */
    private void appendTextBytes(String text) {
        for (int i = 0; i < text.length(); i++) bytes.add(text.charAt(i) & 0xFF);
    }

    /** Retains only recent console data and adjusts error ranges after trimming. */
    private void trimOutputBuffer() {
        int excess = bytes.size() - MAX_CONSOLE_BYTES;
        if (excess <= 0) return;
        bytes.subList(0, excess).clear();
        for (int i = errorRanges.size() - 1; i >= 0; i--) {
            int[] range = errorRanges.get(i);
            if (range[1] <= excess) errorRanges.remove(i);
            else {
                range[0] = Math.max(0, range[0] - excess);
                range[1] -= excess;
            }
        }
    }

    private void copySelectedOrAll() {
        String text = output.getSelectedText();
        if (text == null || text.isEmpty()) text = output.getText();
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    /** Applies the current theme colors and refreshes this component. */
    public void refreshTheme() {
        titleLabel.setFont(new Font(settings.getApplicationFont(), Font.PLAIN, settings.getFontSize("application")));
        titleLabel.setForeground(settings.getColor("text"));
        output.setBackground(settings.getColor("editorBackground"));
        output.setForeground(settings.getColor("editorText"));
        output.setFont(new Font(settings.getEditorFont(), Font.PLAIN, settings.getFontSize(fontKey)));
        inputField.setFont(output.getFont());
        inputField.setBackground(settings.getColor("inputBackground"));
        inputField.setForeground(settings.getColor("editorText"));
        inputField.setCaretColor(settings.getColor("editorText"));
        refreshView();
        repaint();
    }

    /** Hides non-ASCII output modes while an error is visible, until the console is cleared. */
    private void lockEncodingToAscii() {
        if (asciiLockedByError) return;
        asciiLockedByError = true;
        encoding.setSelectedItem("ASCII");
        encoding.setEnabled(false);
        encoding.setVisible(false);
        Container parent = encoding.getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }
    }
    private void refreshView() {
        if (rendering) return;
        rendering = true;
        try {
            String mode = (String) encoding.getSelectedItem();
            if ("Hex".equals(mode)) renderPlain(formatHex());
            else if ("Decimal".equals(mode)) renderPlain(formatDecimal());
            else renderAscii();
        } finally { rendering = false; }
    }

    private String formatHex() {
        StringBuilder s = new StringBuilder();
        for (int b : bytes) { if (!s.isEmpty()) s.append(' '); s.append(String.format("%02X", b)); }
        return s.toString();
    }
    private String formatDecimal() {
        StringBuilder s = new StringBuilder();
        for (int b : bytes) { if (!s.isEmpty()) s.append(' '); s.append(b); }
        return s.toString();
    }
    private void renderPlain(String text) {
        output.setText(text);
        output.setCaretPosition(output.getDocument().getLength());
    }
    private void renderAscii() {
        StyledDocument doc = output.getStyledDocument();
        try { doc.remove(0, doc.getLength()); } catch (BadLocationException ignored) {}
        Style normal = baseStyle();
        Style ansi = normal;
        Style runStyle = null;
        Style errorStyle = null;
        StringBuilder run = new StringBuilder();
        int errorRangeIndex = 0;
        int i = 0;
        while (i < bytes.size()) {
            if (bytes.get(i) == 0x1B && i + 1 < bytes.size() && bytes.get(i + 1) == '[') {
                int end = i + 2; while (end < bytes.size() && bytes.get(end) != 'm') end++;
                if (end < bytes.size()) {
                    appendStyledRun(doc, run, runStyle);
                    runStyle = null;
                    ansi = styleForAnsi(bytes.subList(i + 2, end), normal);
                    i = end + 1;
                    continue;
                }
            }
            while (errorRangeIndex < errorRanges.size() && errorRanges.get(errorRangeIndex)[1] <= i) errorRangeIndex++;
            boolean isError = errorRangeIndex < errorRanges.size()
                    && i >= errorRanges.get(errorRangeIndex)[0] && i < errorRanges.get(errorRangeIndex)[1];
            Style nextStyle = ansi;
            if (isError) {
                if (errorStyle == null) errorStyle = createErrorStyle();
                nextStyle = errorStyle;
            }
            if (runStyle != nextStyle) {
                appendStyledRun(doc, run, runStyle);
                runStyle = nextStyle;
            }
            run.append((char) (bytes.get(i) & 0xFF));
            i++;
        }
        appendStyledRun(doc, run, runStyle);
        output.setCaretPosition(doc.getLength());
    }
    private void appendStyledRun(StyledDocument doc, StringBuilder run, Style style) {
        if (run.isEmpty()) return;
        try { doc.insertString(doc.getLength(), run.toString(), style); }
        catch (BadLocationException ignored) { }
        run.setLength(0);
    }
    private Style createErrorStyle() {
        Style error = output.addStyle("error", null);
        StyleConstants.setForeground(error, new Color(255, 90, 90));
        return error;
    }
    private Style styleForAnsi(List<Integer> codes, Style base) {
        if (codes.isEmpty()) return base;
        StringBuilder sequence = new StringBuilder(codes.size());
        for (int value : codes) sequence.append((char) (value & 0xFF));
        String[] parameters = sequence.toString().split(";", -1);
        Style style = output.addStyle("ansi-" + sequence, base);
        boolean changed = false;
        for (int i = 0; i < parameters.length; i++) {
            int code;
            try { code = parameters[i].isEmpty() ? 0 : Integer.parseInt(parameters[i]); }
            catch (NumberFormatException ignored) { continue; }
            if (code == 0) {
                StyleConstants.setForeground(style, settings.getColor("editorText"));
                StyleConstants.setBold(style, false);
                changed = false;
            } else if (code >= 30 && code <= 37) {
                StyleConstants.setForeground(style, ansiColor(code - 30));
                changed = true;
            } else if (code >= 90 && code <= 97) {
                StyleConstants.setForeground(style, ansiBrightColor(code - 90));
                changed = true;
            } else if (code == 39) {
                StyleConstants.setForeground(style, settings.getColor("editorText"));
                changed = true;
            } else if (code == 1) {
                StyleConstants.setBold(style, true);
                changed = true;
            } else if (code == 22) {
                StyleConstants.setBold(style, false);
                changed = true;
            } else if (code == 38 && i + 2 < parameters.length && "5".equals(parameters[i + 1])) {
                try { StyleConstants.setForeground(style, ansi256Color(Integer.parseInt(parameters[i + 2]))); changed = true; }
                catch (NumberFormatException ignored) { }
                i += 2;
            } else if (code == 38 && i + 4 < parameters.length && "2".equals(parameters[i + 1])) {
                try {
                    StyleConstants.setForeground(style, new Color(
                            clampColor(Integer.parseInt(parameters[i + 2])),
                            clampColor(Integer.parseInt(parameters[i + 3])),
                            clampColor(Integer.parseInt(parameters[i + 4]))));
                    changed = true;
                } catch (NumberFormatException ignored) { }
                i += 4;
            }
        }
        return changed ? style : base;
    }
    private static int clampColor(int value) { return Math.max(0, Math.min(255, value)); }
    private static Color ansi256Color(int value) {
        int index = clampColor(value);
        if (index < 16) {
            Color[] palette = {Color.BLACK, Color.RED, Color.GREEN, Color.YELLOW, Color.BLUE, Color.MAGENTA, Color.CYAN, Color.WHITE,
                    Color.DARK_GRAY, Color.PINK, new Color(100, 255, 100), Color.ORANGE, new Color(100, 180, 255), Color.MAGENTA, Color.CYAN, Color.WHITE};
            return palette[index];
        }
        if (index < 232) {
            int cube = index - 16;
            return new Color(colorCube(cube / 36), colorCube((cube / 6) % 6), colorCube(cube % 6));
        }
        int gray = 8 + (index - 232) * 10;
        return new Color(gray, gray, gray);
    }
    private static int colorCube(int component) { return component == 0 ? 0 : 55 + component * 40; }
    private Color ansiColor(int n) { Color[] c={Color.BLACK,Color.RED,Color.GREEN,Color.YELLOW,Color.BLUE,Color.MAGENTA,Color.CYAN,Color.WHITE}; return c[n]; }
    private Color ansiBrightColor(int n) { Color[] c={Color.DARK_GRAY,Color.PINK,Color.GREEN,Color.ORANGE,Color.CYAN,Color.MAGENTA,Color.CYAN,Color.WHITE}; return c[n]; }
    private Style baseStyle() {
        Style s = output.addStyle("base", null);
        StyleConstants.setFontFamily(s, settings.getEditorFont());
        StyleConstants.setFontSize(s, settings.getFontSize(fontKey));
        StyleConstants.setForeground(s, settings.getColor("editorText"));
        return s;
    }
}
