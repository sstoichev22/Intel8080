/** Display is a viewer of CPU memory, not a second memory model; its timer is presentation-only and should be inactive when the tab is hidden. */
package Vulcan;

import Intel8080.assembler.Assembler;
import Intel8080.cpu.Intel8080;
import Intel8080.cpu.Memory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Memory-backed display workspace with presets, file runner, and output console. */
public class DisplayPanel extends JPanel {
    private final SettingsManager settings;
    private final WorkspaceViews views = new WorkspaceViews();
    private final Memory memory;
    private final Intel8080 cpu;
    private final FileSystemPanel files;
    private final ConsolePanel console;
    private final JButton runButton = new JButton();
    private final JButton stopButton = new JButton();
    private final JLabel selectedFileLabel = new JLabel("No file selected");
    private final JComboBox<String> presetBox = new JComboBox<>();
    private final JTextField addressField = new JTextField("9000", 4);
    private final javax.swing.border.Border addressBorder = addressField.getBorder();
    private final JComboBox<Integer> bitsBox = new JComboBox<>(new Integer[]{1, 2, 4, 8});
    private final JSpinner widthSpinner = new JSpinner(new SpinnerNumberModel(16, 1, 512, 1));
    private final JSpinner heightSpinner = new JSpinner(new SpinnerNumberModel(16, 1, 512, 1));
    private final JPanel mappings = new JPanel();
    private final ScreenCanvas canvas = new ScreenCanvas();
    private final Map<String, Preset> presets = new LinkedHashMap<>();
    private final Map<Integer, Color> colors = new LinkedHashMap<>();
    private final javax.swing.Timer refreshTimer;
    private Path selectedFile;
    private java.util.function.Consumer<Path> deletionListener = path -> {};
    private boolean updating;
    private volatile boolean closed;
    private java.util.function.Consumer<Path> runHandler;
    private final EmulationSession session = EmulationSession.SHARED;
    private Boolean lastRunning;
    private String lastRuntimeError;

    /** Creates a standalone display with its own CPU and memory. */
    public DisplayPanel(SettingsManager settings) {
        this(settings, EmulationSession.SHARED.memory, EmulationSession.SHARED.cpu);
    }

    /** Creates a display using the supplied memory and a CPU attached to that memory. */
    public DisplayPanel(SettingsManager settings, Memory memory) {
        this(settings, memory, null);
    }

    /** Creates a display bound to the supplied shared CPU and memory. */
    public DisplayPanel(SettingsManager settings, Intel8080 cpu, Memory memory) {
        this(settings, memory, cpu);
    }

    private DisplayPanel(SettingsManager settings, Memory suppliedMemory, Intel8080 suppliedCpu) {
        this.settings = settings;
        this.memory = suppliedMemory == null ? new Memory() : suppliedMemory;
        this.cpu = suppliedCpu == null ? new Intel8080(this.memory) : suppliedCpu;
        setLayout(new BorderLayout());
        setBackground(settings.getColor("panelBackground"));

        files = new FileSystemPanel(settings, path -> { selectedFile = path.toAbsolutePath().normalize(); settings.setCurrentFile(selectedFile); selectedFileLabel.setText(path.getFileName().toString()); selectedFileLabel.setToolTipText(path.toString()); }, "displayFilePanel");
        console = new ConsolePanel(settings, "displayConsole");
        console.setTitle("Console");
        console.setInputHandler(line -> cpu == session.cpu && session.submitInput(this, line));
        files.setDeletionListener(path -> deletionListener.accept(path));
        loadDefaultPresets();
        loadPresetFiles(settings.getPresetsDirectory());
        selectSavedPreset();

        JPanel header = createHeader();
        JPanel controls = createControls();
        JSplitPane center = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvas, controls);
        center.setDividerLocation(0.78);
        center.setResizeWeight(1.0);
        center.setBorder(null);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(console, BorderLayout.CENTER);
        bottom.setPreferredSize(new Dimension(0, 150));

        JSplitPane right = new JSplitPane(JSplitPane.VERTICAL_SPLIT, center, bottom);
        right.setDividerLocation(650);
        right.setResizeWeight(0.78);
        right.setBorder(null);
        Theme.initializeDivider(right, 0.78);

        JSplitPane main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, files, right);
        main.setDividerLocation(220);
        main.setResizeWeight(0.0);
        main.setBorder(null);
        views.addSplit("Files", main, true);
        views.addSplit("Screen", center, true);
        views.addSplit("Display settings", center, false);
        views.addConsole("Console", right, false, console);
        add(header, BorderLayout.NORTH);
        add(main, BorderLayout.CENTER);

        refreshTimer = new javax.swing.Timer(1000 / 60, e -> { canvas.repaint(); refreshRunStopButtons(); });
        refreshTimer.setCoalesce(true);
        // A hidden 60 FPS timer is needless Swing traffic, especially on high-refresh monitors.
        refreshTimer.stop();
        refreshRunStopButtons();
        installOutputListener();
    }

    /** Sets the callback used to launch a selected display program. */
    public void setRunHandler(java.util.function.Consumer<Path> handler) {
        runHandler = handler;
    }

    /** Builds a filename bar matching the debugger, with the filename centered and Run at the right. */
    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(4, 7, 4, 7));
        header.setBackground(settings.getColor("panelBackground"));
        selectedFileLabel.setHorizontalAlignment(SwingConstants.CENTER);
        selectedFileLabel.setForeground(settings.getColor("text"));
        selectedFileLabel.setFont(new Font(settings.getApplicationFont(), Font.BOLD, settings.getFontSize("displayFilename")));
        runButton.setFocusPainted(false);
        runButton.setIcon(Icons.vector("run", 18, new Color(47, 184, 92)));
        runButton.setToolTipText("Run");
        runButton.setPreferredSize(new Dimension(31, 27));
        stopButton.setIcon(Icons.vector("stop", 16, new Color(220, 65, 75)));
        stopButton.setToolTipText("Stop");
        stopButton.setPreferredSize(new Dimension(31, 27));
        stopButton.setVisible(true);
        stopButton.setEnabled(false);
        runButton.setBorder(BorderFactory.createEmptyBorder(4, 7, 4, 7));
        stopButton.setBorder(BorderFactory.createEmptyBorder(4, 7, 4, 7));
        runButton.setContentAreaFilled(false);
        stopButton.setContentAreaFilled(false);
        runButton.setOpaque(false);
        stopButton.setOpaque(false);
        runButton.addActionListener(e -> runSelected());
        stopButton.addActionListener(e -> stopProgram());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        actions.setOpaque(false);
        actions.add(runButton);
        actions.add(stopButton);
        header.add(Box.createRigidArea(new Dimension(actions.getPreferredSize())), BorderLayout.WEST);
        header.add(selectedFileLabel, BorderLayout.CENTER);
        header.add(actions, BorderLayout.EAST);
        return header;
    }
    private JPanel createControls() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(285, 0));
        panel.setBackground(settings.getColor("panelBackground"));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(new EmptyBorder(8, 8, 8, 8));
        top.setBackground(settings.getColor("panelBackground"));

        JPanel presetRow = new JPanel(new BorderLayout(4, 0));
        presetRow.setOpaque(false);
        presetRow.add(label("Preset"), BorderLayout.NORTH);
        JButton add = new JButton("+");
        JButton delete = new JButton("-");
        add.setToolTipText("Add preset");
        delete.setToolTipText("Delete preset");
        JPanel plusMinus = new JPanel(new GridLayout(1, 2, 2, 0));
        plusMinus.add(add); plusMinus.add(delete);
        presetRow.add(presetBox, BorderLayout.CENTER);
        presetRow.add(plusMinus, BorderLayout.EAST);
        top.add(presetRow);
        top.add(Box.createVerticalStrut(7));

        top.add(label("Memory address (hex)"));
        top.add(addressField);
        top.add(Box.createVerticalStrut(6));
        top.add(label("Screen dimensions"));
        JPanel dimensions = new JPanel(new GridLayout(1, 2, 4, 0));
        dimensions.setOpaque(false);
        dimensions.add(widthSpinner); dimensions.add(heightSpinner);
        top.add(dimensions);
        top.add(Box.createVerticalStrut(6));
        top.add(label("Bits read from each byte"));
        top.add(bitsBox);
        top.add(Box.createVerticalStrut(8));
        top.add(label("Colors"));

        mappings.setLayout(new BoxLayout(mappings, BoxLayout.Y_AXIS));
        mappings.setBackground(settings.getColor("inputBackground"));
        JScrollPane mappingScroll = new JScrollPane(mappings);
        mappingScroll.setBorder(null);
        mappingScroll.setPreferredSize(new Dimension(0, 400));
        Theme.styleScrollBar(mappingScroll.getVerticalScrollBar(), settings);
        top.add(mappingScroll);
        top.add(Box.createVerticalStrut(7));


        top.add(Box.createVerticalGlue());

        add.addActionListener(e -> addPreset());
        delete.addActionListener(e -> deletePreset());
        presetBox.addActionListener(e -> { if (!updating) applyPreset(); });
        bitsBox.addActionListener(e -> { if (!updating) { rebuildColors((Integer) bitsBox.getSelectedItem()); saveCurrentPreset(); } });
        widthSpinner.addChangeListener(e -> { canvas.repaint(); saveCurrentPreset(); });
        heightSpinner.addChangeListener(e -> { canvas.repaint(); saveCurrentPreset(); });
        addressField.addActionListener(e -> { canvas.repaint(); saveCurrentPreset(); });

        panel.add(top, BorderLayout.CENTER);
        return panel;
    }

    private JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(settings.getColor("text"));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private void loadDefaultPresets() {
        presets.put("16x16 Video 2-bit", new Preset("16x16 Video 2-bit", 0x9000, 16, 16, 2, new LinkedHashMap<>()));
        presets.put("32x32 RAM 2-bit", new Preset("32x32 RAM 2-bit", 0x8000, 32, 32, 2, new LinkedHashMap<>()));
        for (Preset preset : presets.values()) preset.colors.putAll(defaultColors(preset.bits));
    }

    private Map<Integer, Color> defaultColors(int bits) {
        Map<Integer, Color> map = new LinkedHashMap<>();
        int count = 1 << Math.min(bits, 8);
        Color[] sample = {new Color(20, 40, 20), new Color(35, 110, 45), new Color(40, 90, 190), new Color(210, 45, 45)};
        for (int i = 0; i < count; i++) map.put(i, i < sample.length ? sample[i] : Color.BLACK);
        return map;
    }

    private void loadPresetFiles(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.preset")) {
            for (Path path : stream) {
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
                try {
                    Preset preset = Preset.load(path);
                    presets.put(preset.name, preset);
                } catch (Exception error) {
                    console.appendError("Skipped preset " + path.getFileName() + ": " + VulcanDialog.errorSummary(error) + "\n");
                }
            }
        } catch (IOException | RuntimeException error) {
            console.appendError("Could not read display presets: " + VulcanDialog.errorSummary(error) + "\n");
        }
        presetBox.removeAllItems();
        for (String name : presets.keySet()) presetBox.addItem(name);
        selectSavedPreset();
    }

    private void selectSavedPreset() {
        String wanted = settings.getDisplayPreset();
        if (wanted != null && presets.containsKey(wanted)) presetBox.setSelectedItem(wanted);
        else if (presetBox.getItemCount() > 0) presetBox.setSelectedIndex(0);
        applyPreset();
    }

    private void applyPreset() {
        Preset p = presets.get((String) presetBox.getSelectedItem());
        if (p == null) return;
        updating = true;
        addressField.setText(String.format("%04X", p.address));
        addressField.setBorder(addressBorder);
        addressField.setToolTipText(null);
        widthSpinner.setValue(p.width);
        heightSpinner.setValue(p.height);
        bitsBox.setSelectedItem(p.bits);
        colors.clear();
        colors.putAll(p.colors);
        ensureColorCount(p.bits);
        rebuildMappings();
        updating = false;
        settings.setDisplayPreset(p.name);
        canvas.repaint();
    }

    private void ensureColorCount(int bits) {
        int count = 1 << Math.min(bits, 8);
        Map<Integer, Color> defaults = defaultColors(bits);
        for (int i = 0; i < count; i++) colors.putIfAbsent(i, defaults.get(i));
        colors.keySet().removeIf(k -> k >= count);
    }

    private void rebuildColors(int bits) {
        ensureColorCount(bits);
        rebuildMappings();
        canvas.repaint();
    }

    private void rebuildMappings() {
        mappings.removeAll();
        int bits = (Integer) bitsBox.getSelectedItem();
        int count = 1 << Math.min(bits, 8);
        for (int i = 0; i < count; i++) {
            final int key = i;
            JPanel row = new JPanel(new BorderLayout(4, 0));
            row.setOpaque(true);
            row.setBackground(settings.getColor("inputBackground"));
            JLabel value = new JLabel(pattern(key, bits));
            value.setForeground(settings.getColor("text"));
            value.setPreferredSize(new Dimension(58, 25));
            JButton color = new JButton();
            color.setOpaque(true);
            color.setBackground(colors.get(key));
            color.setBorder(BorderFactory.createLineBorder(settings.getColor("splitDivider")));
            JTextField hex = new JTextField(toHex(colors.get(key)), 7);
            hex.setForeground(settings.getColor("text"));
            hex.setBackground(settings.getColor("panelBackground"));
            color.addActionListener(e -> editColor(key));
            hex.addActionListener(e -> {
                Color c = parseColor(hex.getText());
                if (c != null) { colors.put(key, c); color.setBackground(c); saveCurrentPreset(); canvas.repaint(); }
            });
            row.add(value, BorderLayout.WEST);
            row.add(color, BorderLayout.CENTER);
            row.add(hex, BorderLayout.EAST);
            mappings.add(row);
        }
        mappings.revalidate();
        mappings.repaint();
    }

    private void editColor(int key) {
        ColorEditor dialog = new ColorEditor(SwingUtilities.getWindowAncestor(this), colors.get(key));
        dialog.setVisible(true);
        if (dialog.result != null) {
            colors.put(key, dialog.result);
            rebuildMappings();
            saveCurrentPreset();
            canvas.repaint();
        }
    }

    private void addPreset() {
        String name = JOptionPane.showInputDialog(this, "Preset name:", "Add Display Preset", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) return;
        String clean = name.trim();
        if (clean.length() > 80) {
            VulcanDialog.message(this, settings, "Invalid Preset Name", "Preset names must be 80 characters or fewer.");
            return;
        }
        if (presets.containsKey(clean)) { JOptionPane.showMessageDialog(this, "A preset with that name already exists."); return; }
        Path cleanedPath = presetPath(clean);
        for (String existing : presets.keySet()) {
            if (presetPath(existing).equals(cleanedPath)) {
                JOptionPane.showMessageDialog(this, "That name maps to the same preset file as " + existing + ".");
                return;
            }
        }
        int bits = (Integer) bitsBox.getSelectedItem();
        final int baseAddress;
        try { baseAddress = address(); }
        catch (IllegalArgumentException error) {
            addressField.setBorder(BorderFactory.createLineBorder(new Color(220, 70, 70)));
            addressField.setToolTipText(error.getMessage());
            VulcanDialog.message(this, settings, "Invalid Address", error.getMessage());
            return;
        }
        Preset preset = new Preset(clean, baseAddress, (Integer) widthSpinner.getValue(), (Integer) heightSpinner.getValue(), bits, new LinkedHashMap<>(colors));
        preset.colors.clear(); preset.colors.putAll(colors);
        presets.put(clean, preset);
        presetBox.addItem(clean);
        presetBox.setSelectedItem(clean);
        saveCurrentPreset();
    }

    private void deletePreset() {
        String name = (String) presetBox.getSelectedItem();
        if (name == null || presets.size() <= 1) return;
        try { Files.deleteIfExists(presetPath(name)); }
        catch (IOException error) {
            console.appendError("Preset delete failed: " + VulcanDialog.errorSummary(error) + "\n");
            return;
        }
        presets.remove(name);
        presetBox.removeItem(name);
        applyPreset();
    }

    private void saveCurrentPreset() {
        if (updating) return;
        String name = (String) presetBox.getSelectedItem();
        if (name == null) return;
        Preset p = presets.get(name);
        if (p == null) return;
        final int parsedAddress;
        try {
            parsedAddress = address();
            addressField.setBorder(addressBorder);
            addressField.setToolTipText(null);
        } catch (IllegalArgumentException error) {
            addressField.setBorder(BorderFactory.createLineBorder(new Color(220, 70, 70)));
            addressField.setToolTipText(error.getMessage());
            return;
        }
        p.address = parsedAddress;
        p.width = (Integer) widthSpinner.getValue();
        p.height = (Integer) heightSpinner.getValue();
        p.bits = (Integer) bitsBox.getSelectedItem();
        p.colors.clear(); p.colors.putAll(colors);
        try { p.save(presetPath(name)); }
        catch (IOException error) {
            console.appendError("Preset save failed: " + VulcanDialog.errorSummary(error) + "\n");
        }
        settings.setDisplayPreset(name);
    }

    private Path presetPath(String name) {
        String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return settings.getPresetsDirectory().resolve(safe + ".preset");
    }

    private void runSelected() {
        if (closed) return;
        if (selectedFile == null || !Files.isRegularFile(selectedFile)) {
            return;
        }
        String name = selectedFile.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".asm") && !name.endsWith(".exe")) return;
        stopProgram();
        console.clear();
        if (runHandler != null) {
            try { runHandler.accept(selectedFile); }
            catch (RuntimeException error) {
                console.appendError("\u001B[31m" + VulcanDialog.errorSummary(error) + "\u001B[0m\n");
            }
            canvas.requestFocusInWindow();
            return;
        }
        try {
            byte[] image;
            int start;
            int end;
            int entry;
            if (name.endsWith(".asm")) {
                Assembler.AssemblyResult result = Assembler.assembleWithInfo(Files.readString(selectedFile, StandardCharsets.UTF_8));
                image = result.image();
                start = result.info().programStart(); end = result.info().programEnd(); entry = result.info().codeStart();
                for (String warning : result.warnings()) console.append("Warning: " + warning + "\n");
            } else if (name.endsWith(".exe")) {
                byte[] bytes = Files.readAllBytes(selectedFile);
                if (bytes.length > Memory.MEM_SIZE - Memory.PROGRAM_START) throw new IllegalArgumentException("Executable is too large for memory.");
                image = new byte[Memory.MEM_SIZE];
                start = entry = Memory.PROGRAM_START; end = start + bytes.length - 1;
                System.arraycopy(bytes, 0, image, start, bytes.length);
            } else {
                console.appendError("Display Run supports .asm and .exe files.\n");
                return;
            }
            if (cpu == session.cpu) {
                long revision = session.load(this, image, start, end, entry, console::appendByte);
                session.run(this, revision);
            } else {
                cpu.stop(); cpu.reset();
                for (int address = start; address <= end; address++) memory.set(address, image[address]);
                cpu.setEntryAddress(entry); cpu.run();
            }
            canvas.requestFocusInWindow();
        } catch (Exception ex) {
            console.appendError("\u001B[31m" + VulcanDialog.errorSummary(ex) + "\u001B[0m\n");
        }
    }

    private void installOutputListener() {
        if (cpu == session.cpu) return;
        cpu.addPortOutputListener((port, value) -> {
            if (!closed && port == Intel8080.PORT_KEY_DATA) console.appendByte(value);
        });
    }

    /** Stops execution on the CPU shared with the debugger. */
    public void stopProgram() {
        if (cpu == session.cpu) session.stop(this); else cpu.stop();
        refreshRunStopButtons();
    }

    /** Keeps the display Run and Stop controls in sync with the shared CPU. */
    private void refreshRunStopButtons() {
        String runtimeError = cpu.getRuntimeError();
        if (!closed && (cpu != session.cpu || session.owns(this)) && runtimeError != null && !Objects.equals(runtimeError, lastRuntimeError)) {
            console.appendError(runtimeError + "\n");
        }
        lastRuntimeError = runtimeError;
        boolean workerRunning = (cpu != session.cpu || session.owns(this)) && cpu.isRunning();
        boolean running = workerRunning && !cpu.isHalted() && !cpu.isPaused();
        // HLT preserves the last frame and waits for interrupts; Stop still releases that worker.
        stopButton.setEnabled(workerRunning);
        if (lastRunning != null && lastRunning == running) return;
        lastRunning = running;
        runButton.setIcon(Icons.vector(running ? "running" : "run", 18, new Color(47, 184, 92)));
        runButton.setToolTipText(running ? "Rerun" : "Run");
        Container actions = stopButton.getParent();
        if (actions != null) {
            actions.revalidate();
            actions.repaint();
        }
    }

    private int address() {
        String value = addressField.getText().trim().replaceFirst("(?i)^0x", "");
        if (!value.matches("(?i)[0-9a-f]{1,4}")) {
            throw new IllegalArgumentException("Enter a hexadecimal address from 0000 to FFFF.");
        }
        return Integer.parseInt(value, 16);
    }

    private String pattern(int value, int bits) { return String.format("%" + bits + "s", Integer.toBinaryString(value)).replace(' ', '0'); }
    private String toHex(Color c) { return String.format("#%02X%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha()); }
    private Color parseColor(String text) {
        try {
            String s = text.trim(); if (s.startsWith("#")) s = s.substring(1);
            if (s.length() != 6 && s.length() != 8) return null;
            long value = Long.parseLong(s, 16);
            if (s.length() == 6) return new Color((int) value);
            return new Color((int)(value >> 24) & 255, (int)(value >> 16) & 255, (int)(value >> 8) & 255, (int)value & 255);
        } catch (Exception e) { return null; }
    }

    /** Sets the callback notified when a file is deleted from this panel. */
    public void setDeletionListener(java.util.function.Consumer<Path> listener) { deletionListener = listener == null ? path -> {} : listener; }

    /** Routes browser moves to the shared workspace. */
    public void setMoveListener(java.util.function.BiConsumer<Path, Path> listener) { files.setMoveListener(listener); }

    /** Keeps the selected display program attached to a moved file or folder. */
    public void relocateFile(Path source, Path destination) {
        if (selectedFile != null && selectedFile.startsWith(source)) {
            selectedFile = destination.resolve(source.relativize(selectedFile));
            selectedFileLabel.setText(selectedFile.getFileName().toString());
            selectedFileLabel.setToolTipText(selectedFile.toString());
        }
    }

    /** Loads the given supported file into this panel when it exists. */
    public void selectFile(Path path) {
        if (path != null && Files.isRegularFile(path)) {
            selectedFile = path.toAbsolutePath().normalize();
            selectedFileLabel.setText(selectedFile.getFileName().toString());
            selectedFileLabel.setToolTipText(selectedFile.toString());
            files.openIfNeeded(selectedFile);
        }
    }

    /** Applies the current theme colors and refreshes this component. */
    public void refreshTheme() {
        setBackground(settings.getColor("panelBackground"));
        files.refreshTheme();
        console.refreshTheme();
        selectedFileLabel.setFont(new Font(settings.getApplicationFont(), Font.BOLD, settings.getFontSize("displayFilename")));
        selectedFileLabel.setForeground(settings.getColor("text"));
        mappings.setBackground(settings.getColor("inputBackground"));
        rebuildMappings();
        repaint();
    }

    /** Refreshes the display while visible and stops CPU execution when hidden. */
    public void setActive(boolean active) {
        if (active) { refreshTimer.start(); canvas.repaint(); }
        else {
            refreshTimer.stop();
        refreshRunStopButtons();
            stopProgram();
        }
    }

    /** Stops display execution and releases the fallback runner during shutdown. */
    public void close() {
        closed = true;
        refreshTimer.stop();
        refreshRunStopButtons();
        stopProgram();
        console.clear();
    }

    /** Requests a repaint of the current display or view. */
    public void refresh() { canvas.repaint(); }
    WorkspaceViews getViews() { return views; }
    ConsolePanel getConsole() { return console; }
    public Path getSelectedFile() { return selectedFile; }
    /** Launches the selected display program from the window's Run menu. */
    public void performRun() { runSelected(); }

    private final class ScreenCanvas extends JPanel {
        /** Sends typed ASCII to the CPU keyboard latch while this screen owns keyboard focus. */
        ScreenCanvas() {
            setBackground(Color.BLACK);
            setOpaque(true);
            setFocusable(true);
            setToolTipText("Click the screen, then type to control the running program.");
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) { requestFocusInWindow(); }
            });
            addKeyListener(new KeyAdapter() {
                @Override public void keyTyped(KeyEvent event) {
                    char character = event.getKeyChar();
                    if (!closed && (cpu != session.cpu || session.owns(DisplayPanel.this)) && character >= 0x20 && character <= 0x7E
                            && !event.isControlDown() && !event.isAltDown() && !event.isMetaDown()) {
                        cpu.offerKeyInput(character);
                        event.consume();
                    }
                }
            });
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Preset p = presets.get((String) presetBox.getSelectedItem());
            if (p == null) return;
            int width = p.width, height = p.height;
            if (width <= 0 || height <= 0) return;
            double scale = Math.min(getWidth() / (double) width, getHeight() / (double) height);
            int screenW = Math.max(1, (int)Math.floor(width * scale));
            int screenH = Math.max(1, (int)Math.floor(height * scale));
            int x = (getWidth() - screenW) / 2, y = (getHeight() - screenH) / 2;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                int bits = p.bits;
                int mask = (1 << bits) - 1;
                int base = p.address;
                for (int py = 0; py < height; py++) for (int px = 0; px < width; px++) {
                    int b = memory.get((base + py * width + px) & 0xFFFF) & 0xFF;
                    int value = b & mask;
                    g.setColor(p.colors.getOrDefault(value, Color.BLACK));
                    int rx = x + (int)Math.round(px * screenW / (double)width);
                    int ry = y + (int)Math.round(py * screenH / (double)height);
                    int rw = Math.max(1, (int)Math.ceil(screenW / (double)width));
                    int rh = Math.max(1, (int)Math.ceil(screenH / (double)height));
                    g.fillRect(rx, ry, rw, rh);
                }
            } finally { g.dispose(); }
        }
    }

    private final class ColorEditor extends JDialog {
        private Color result;
        ColorEditor(Window owner, Color initial) {
            super(owner, "Display Color", ModalityType.APPLICATION_MODAL);
            setUndecorated(true);
            JPanel root = new JPanel(new BorderLayout(8, 8));
            root.setBackground(settings.getColor("panelBackground"));
            JPanel bar = new JPanel(new BorderLayout());
            bar.setBackground(settings.getColor("panelBackground"));
            bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, settings.getColor("splitDivider")));
            JLabel title = new JLabel("Display Color");
            title.setForeground(settings.getColor("text"));
            title.setBorder(new EmptyBorder(7, 10, 7, 4));
            bar.add(title, BorderLayout.CENTER);
            JButton close = new JButton("×");
            close.setForeground(settings.getColor("text"));
            close.setBorder(null);
            close.setContentAreaFilled(false);
            close.addActionListener(e -> dispose());
            bar.add(close, BorderLayout.EAST);
            root.add(bar, BorderLayout.NORTH);

            JColorChooser chooser = new JColorChooser(initial == null ? Color.BLACK : initial);
            root.add(chooser, BorderLayout.CENTER);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttons.setOpaque(false);
            JButton cancel = new JButton("Cancel");
            JButton ok = new JButton("OK");
            buttons.add(cancel); buttons.add(ok);
            root.add(buttons, BorderLayout.SOUTH);
            cancel.addActionListener(e -> dispose());
            ok.addActionListener(e -> { result = chooser.getColor(); dispose(); });
            setContentPane(root);
            setSize(620, 430);
            setLocationRelativeTo(owner);
        }
    }

    private static final class Preset {
        String name; int address, width, height, bits; final Map<Integer, Color> colors;
        Preset(String name, int address, int width, int height, int bits, Map<Integer, Color> colors) { this.name=name; this.address=address; this.width=width; this.height=height; this.bits=bits; this.colors=colors; }
        void save(Path path) throws IOException {
            Properties p = new Properties(); p.setProperty("name", name); p.setProperty("address", Integer.toHexString(address)); p.setProperty("width", Integer.toString(width)); p.setProperty("height", Integer.toString(height)); p.setProperty("bits", Integer.toString(bits));
            for (var e : colors.entrySet()) p.setProperty("color." + e.getKey(), String.format("%08X", e.getValue().getRGB()));
            Path parent = path.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Path temporary = Files.createTempFile(parent, ".vulcan-preset-", ".tmp");
            try {
                try (var out = Files.newOutputStream(temporary)) { p.store(out, "Vulcan Display Preset"); }
                try {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
        static Preset load(Path path) throws Exception {
            Properties p = new Properties(); try (var in = Files.newInputStream(path)) { p.load(in); }
            String name = p.getProperty("name", path.getFileName().toString().replaceFirst("\\.preset$", ""));
            int address = Integer.parseInt(p.getProperty("address", "9000"), 16); int width = Integer.parseInt(p.getProperty("width", "16")); int height = Integer.parseInt(p.getProperty("height", "16")); int bits = Integer.parseInt(p.getProperty("bits", "2"));
            if (name.isBlank() || name.length() > 80) throw new IllegalArgumentException("Preset name must contain 1 to 80 characters.");
            if (address < 0 || address > 0xFFFF) throw new IllegalArgumentException("Preset address must be between 0000 and FFFF.");
            if (width < 1 || width > 512 || height < 1 || height > 512) throw new IllegalArgumentException("Preset dimensions must be between 1 and 512.");
            if (bits != 1 && bits != 2 && bits != 4 && bits != 8) throw new IllegalArgumentException("Preset bit depth must be 1, 2, 4, or 8.");
            Map<Integer, Color> colors = new LinkedHashMap<>();
            int count = 1 << Math.min(bits, 8);
            for (int i=0;i<count;i++) {
                String value=p.getProperty("color."+i);
                if (value!=null) {
                    if (!value.matches("(?i)[0-9a-f]{8}")) throw new IllegalArgumentException("Invalid color value for color." + i + ".");
                    colors.put(i, new Color((int)Long.parseLong(value,16), true));
                }
            }
            Map<Integer, Color> defaults = new DisplayPanelDefaults().defaults(bits);
            for (int i=0;i<count;i++) colors.putIfAbsent(i, defaults.get(i));
            return new Preset(name,address,width,height,bits,colors);
        }
    }

    private static final class DisplayPanelDefaults {
        Map<Integer, Color> defaults(int bits) { Map<Integer,Color> m=new LinkedHashMap<>(); int n=1<<Math.min(bits,8); Color[] c={new Color(20,40,20),new Color(35,110,45),new Color(40,90,190),new Color(210,45,45)}; for(int i=0;i<n;i++)m.put(i,i<c.length?c[i]:Color.BLACK); return m; }
    }
}
