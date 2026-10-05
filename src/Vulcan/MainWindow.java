/** Top-level lifecycle/window manager. Keep monitor/fullscreen and shutdown behavior here so child tabs cannot leak processes. */
package Vulcan;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;

public class MainWindow extends JFrame {
    private static final int FULLSCREEN_SNAP_ZONE_PX = 14;
    private final SettingsManager settings;
    private final JTabbedPane tabs;
    private final EditorTab editorTab;
    private final DebuggerTab debuggerTab;
    private final DisplayPanel displayPanel;
    private Rectangle restoreBounds;
    private boolean fullscreen;
    private int previousMainTab = -1;
    private JButton fullscreenButton;
    private JLabel titleLabel;
    private Point dragOffset;
    private Point dragStartScreen;
    private GraphicsConfiguration snapCandidateConfiguration;
    private JWindow fullscreenSnapPrompt;
    private Timer fullscreenSnapPromptTimer;
    private final JPanel deletionBanner = new JPanel(new BorderLayout(8, 0));

    /** Creates the main window and wires the editor, debugger, and display panels. */
    public MainWindow() {
        super("Vulcan " + Vulcan.VERSION);
        settings = SettingsManager.load();
        Theme.configureUI(settings);
        setUndecorated(true);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setIconImage(iconImage());
        setLayout(new BorderLayout());
        getContentPane().setBackground(settings.getColor("applicationBackground"));

        tabs = new JTabbedPane();
        tabs.setUI(new ModernTabbedPaneUI(settings));
        editorTab = new EditorTab(settings);
        debuggerTab = new DebuggerTab(settings);
        displayPanel = new DisplayPanel(settings, debuggerTab.getCpu(), debuggerTab.getMemory());
        editorTab.setDebugListener(path -> openDebugger(path));
        editorTab.setSettingsChangedListener(this::applyTheme);
        tabs.addTab("Editor", editorTab);
        tabs.addTab("Debugger", debuggerTab);
        tabs.addTab("Display", displayPanel);
        tabs.addChangeListener(e -> {
            int selectedTab = tabs.getSelectedIndex();
            if (previousMainTab == 2 && selectedTab != 2) displayPanel.stopProgram();
            previousMainTab = selectedTab;
            settings.setSelectedMainTab(selectedTab);
            Path current = settings.getCurrentFile();
            if (selectedTab == 1) debuggerTab.selectFile(current);
            if (selectedTab == 2) displayPanel.selectFile(current);
            displayPanel.setActive(selectedTab == 2);
        });

        JPanel chrome = new JPanel(new BorderLayout());
        chrome.add(createTitleBar(), BorderLayout.NORTH);
        chrome.add(createMenuBar(), BorderLayout.SOUTH);
        add(chrome, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        createDeletionBanner();
        editorTab.setDeletionListener(this::handleDeletedFile);
        debuggerTab.setDeletionListener(this::handleDeletedFile);
        displayPanel.setDeletionListener(this::handleDeletedFile);
        editorTab.setMoveListener(this::handleMovedFile);
        debuggerTab.setMoveListener(this::handleMovedFile);
        displayPanel.setMoveListener(this::handleMovedFile);

        GraphicsConfiguration initialScreen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
        setBounds(initialWindowBounds(initialScreen));
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                if (!editorTab.prepareToClose()) return;
                hideFullscreenSnapPrompt();
                editorTab.shutdown();
                debuggerTab.shutdown();
                displayPanel.close();
                editorTab.saveAll();
                settings.setWindowBounds(fullscreen && restoreBounds != null ? restoreBounds : getBounds());
                settings.save();
                dispose();
            }
            @Override public void windowDeactivated(WindowEvent e) {
                editorTab.saveAll();
                settings.setWindowBounds(fullscreen && restoreBounds != null ? restoreBounds : getBounds());
                settings.save();
            }
        });
        applyTheme();
        // Do not rebuild the Swing tree on every graphics-device transition.
        // The old implementation caused a large input hitch when dragging the window between monitors.
        displayPanel.setActive(false);
        updateWindowControlVisibility();
        SwingUtilities.invokeLater(() -> {
            int saved = settings.getSelectedMainTab();
            if (saved >= 0 && saved < tabs.getTabCount()) tabs.setSelectedIndex(saved);
        });

    }

    private void createDeletionBanner() {
        deletionBanner.setBackground(new Color(150, 55, 55));
        deletionBanner.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        JLabel text = new JLabel();
        text.setForeground(Color.WHITE);
        deletionBanner.add(text, BorderLayout.CENTER);
        JButton close = new JButton("Close");
        close.addActionListener(e -> deletionBanner.setVisible(false));
        deletionBanner.add(close, BorderLayout.EAST);
        deletionBanner.setVisible(false);
        add(deletionBanner, BorderLayout.SOUTH);
        deletionBanner.putClientProperty("messageLabel", text);
    }

    private void handleDeletedFile(Path deleted) {
        if (deleted == null) return;
        Path target = deleted.toAbsolutePath().normalize();
        if (!editorTab.hasOpenPath(target) && !target.equals(settings.getCurrentFile())) return;
        JLabel label = (JLabel) deletionBanner.getClientProperty("messageLabel");
        label.setText("The current file has been deleted: " + target.getFileName());
        deletionBanner.setVisible(true);
        revalidate();
    }

    /** Keeps editor buffers and debugger/display selection attached to moved project entries. */
    private void handleMovedFile(Path source, Path destination) {
        Path from = source.toAbsolutePath().normalize();
        Path to = destination.toAbsolutePath().normalize();
        Path current = settings.getCurrentFile();
        editorTab.relocateOpenFiles(from, to);
        debuggerTab.relocateFile(from, to);
        displayPanel.relocateFile(from, to);
        if (current != null && current.startsWith(from)) settings.setCurrentFile(to.resolve(from.relativize(current)));
    }

    private Image iconImage() {
        return Icons.load("vulcan-icon.png");
    }

    private JPanel createTitleBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, settings.getColor("splitDivider")));
        bar.setPreferredSize(new Dimension(0, 32));

        JPanel titleArea = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 5));
        JLabel logo = new JLabel(Icons.fit("vulcan-icon.png", 16, 16));
        titleLabel = new JLabel("Vulcan " + Vulcan.VERSION);
        titleLabel.setFont(new Font(settings.getApplicationFont(), Font.BOLD, settings.getFontSize("applicationTitle")));
        titleArea.setOpaque(false);
        titleArea.add(logo);
        titleArea.add(titleLabel);
        bar.add(titleArea, BorderLayout.WEST);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 2));
        controls.setOpaque(false);
        JButton minimize = titleButton("mz-icon.png", "Minimize");
        fullscreenButton = titleButton("fs-icon.png", "Fullscreen");
        JButton close = titleButton("x-icon.png", "Close");
        minimize.addActionListener(e -> setState(Frame.ICONIFIED));
        fullscreenButton.addActionListener(e -> {
            if (fullscreen) exitFullscreen();
            else enterFullscreen();
        });
        close.addActionListener(e -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)));
        controls.add(minimize);
        controls.add(fullscreenButton);
        controls.add(close);
        bar.add(controls, BorderLayout.EAST);

        MouseAdapter drag = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    dragOffset = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), MainWindow.this);
                    dragStartScreen = e.getLocationOnScreen();
                }
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (dragOffset == null) return;
                Point screen = e.getLocationOnScreen();
                if (fullscreen) {
                    if (dragStartScreen != null && dragStartScreen.distance(screen) < 5) return;
                    exitFullscreen();
                }
                int x = screen.x - dragOffset.x;
                int y = screen.y - dragOffset.y;
                GraphicsConfiguration target = graphicsConfigurationAt(screen);
                if (target != null) {
                    Rectangle usable = usableBounds(target);
                    snapCandidateConfiguration = y <= usable.y + FULLSCREEN_SNAP_ZONE_PX ? target : null;
                    int titleHeight = Math.max(1, bar.getHeight());
                    int lowestVisibleTop = usable.y + Math.max(0, usable.height - titleHeight);
                    y = Math.max(usable.y, Math.min(y, lowestVisibleTop));
                } else snapCandidateConfiguration = null;
                setLocation(x, y);
            }
            @Override public void mouseReleased(MouseEvent e) {
                dragOffset = null;
                dragStartScreen = null;
                if (snapCandidateConfiguration != null && !fullscreen) {
                    showFullscreenSnapPrompt(snapCandidateConfiguration);
                }
                snapCandidateConfiguration = null;
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    if (fullscreen) exitFullscreen(); else enterFullscreen();
                }
            }
        };
        bar.addMouseListener(drag);
        bar.addMouseMotionListener(drag);
        titleArea.addMouseListener(drag);
        titleArea.addMouseMotionListener(drag);
        titleLabel.addMouseListener(drag);
        titleLabel.addMouseMotionListener(drag);
        return bar;
    }

    private JButton titleButton(String iconName, String tooltip) {
        return Icons.windowControl(iconName, tooltip);
    }

    /** Finds the monitor currently under the dragged title bar. */
    private GraphicsConfiguration graphicsConfigurationAt(Point point) {
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            GraphicsConfiguration configuration = device.getDefaultConfiguration();
            if (configuration.getBounds().contains(point)) return configuration;
        }
        return getGraphicsConfiguration();
    }

    /** Restores saved window geometry when it remains visible, otherwise centers a windowed default. */
    private Rectangle initialWindowBounds(GraphicsConfiguration preferredScreen) {
        Rectangle saved = settings.getWindowBounds();
        Rectangle preferredUsable = usableBounds(preferredScreen);
        if (saved == null) return centeredWindowBounds(preferredUsable);

        Rectangle bestUsable = null;
        long bestVisibleArea = 0;
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            Rectangle usable = usableBounds(device.getDefaultConfiguration());
            Rectangle visible = saved.intersection(usable);
            long visibleArea = Math.max(0L, (long) visible.width) * Math.max(0L, (long) visible.height);
            if (visibleArea > bestVisibleArea) {
                bestVisibleArea = visibleArea;
                bestUsable = usable;
            }
        }

        if (bestUsable == null || bestVisibleArea < 100L * 100L) {
            return centeredWindowBounds(preferredUsable);
        }
        int width = Math.min(saved.width, bestUsable.width);
        int height = Math.min(saved.height, bestUsable.height);
        int x = Math.max(bestUsable.x, Math.min(saved.x, bestUsable.x + bestUsable.width - width));
        int y = Math.max(bestUsable.y, Math.min(saved.y, bestUsable.y + bestUsable.height - height));
        return new Rectangle(x, y, width, height);
    }

    /** Creates a usable windowed default for the first launch. */
    private static Rectangle centeredWindowBounds(Rectangle usable) {
        int width = Math.min(usable.width, Math.min(1440, Math.max(800, usable.width * 3 / 4)));
        int height = Math.min(usable.height, Math.min(900, Math.max(600, usable.height * 3 / 4)));
        int x = usable.x + Math.max(0, (usable.width - width) / 2);
        int y = usable.y + Math.max(0, (usable.height - height) / 2);
        return new Rectangle(x, y, width, height);
    }
    /** Returns a monitor's usable bounds after excluding taskbars and reserved screen areas. */
    private static Rectangle usableBounds(GraphicsConfiguration configuration) {
        Rectangle bounds = new Rectangle(configuration.getBounds());
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        bounds.x += insets.left;
        bounds.y += insets.top;
        bounds.width -= insets.left + insets.right;
        bounds.height -= insets.top + insets.bottom;
        return bounds;
    }

    private void enterFullscreen() {
        if (fullscreen) return;
        hideFullscreenSnapPrompt();
        restoreBounds = getBounds();
        setExtendedState(Frame.NORMAL);
        GraphicsConfiguration gc = getGraphicsConfiguration();
        if (gc == null) gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        setBounds(usableBounds(gc));
        fullscreen = true;
        updateWindowControlVisibility();
    }

    private void showFullscreenSnapPrompt(GraphicsConfiguration configuration) {
        hideFullscreenSnapPrompt();
        fullscreenSnapPrompt = new JWindow(this);
        JPanel prompt = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 7));
        prompt.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(settings.getColor("splitDivider")),
                BorderFactory.createEmptyBorder(2, 7, 2, 7)));
        prompt.setBackground(settings.getColor("panelBackground"));
        JLabel message = new JLabel("Snap Vulcan to fullscreen?");
        message.setForeground(settings.getColor("text"));
        JButton maximize = new JButton("Fullscreen");
        maximize.addActionListener(e -> enterFullscreen());
        JButton dismiss = new JButton("×");
        dismiss.setToolTipText("Dismiss");
        dismiss.addActionListener(e -> hideFullscreenSnapPrompt());
        prompt.add(message);
        prompt.add(maximize);
        prompt.add(dismiss);
        fullscreenSnapPrompt.setContentPane(prompt);
        fullscreenSnapPrompt.pack();
        Rectangle usable = usableBounds(configuration);
        int x = usable.x + Math.max(0, (usable.width - fullscreenSnapPrompt.getWidth()) / 2);
        fullscreenSnapPrompt.setLocation(x, usable.y + 8);
        fullscreenSnapPrompt.setVisible(true);
        fullscreenSnapPrompt.toFront();
        fullscreenSnapPromptTimer = new Timer(5000, e -> hideFullscreenSnapPrompt());
        fullscreenSnapPromptTimer.setRepeats(false);
        fullscreenSnapPromptTimer.start();
    }

    private void hideFullscreenSnapPrompt() {
        if (fullscreenSnapPromptTimer != null) {
            fullscreenSnapPromptTimer.stop();
            fullscreenSnapPromptTimer = null;
        }
        if (fullscreenSnapPrompt != null) {
            fullscreenSnapPrompt.setVisible(false);
            fullscreenSnapPrompt.dispose();
            fullscreenSnapPrompt = null;
        }
    }

    private void exitFullscreen() {
        fullscreen = false;
        setExtendedState(Frame.NORMAL);
        if (restoreBounds != null) setBounds(restoreBounds);
        updateWindowControlVisibility();
    }

    private void updateWindowControlVisibility() {
        if (fullscreenButton != null) {
            fullscreenButton.setVisible(true);
            fullscreenButton.setIcon(Icons.fit(fullscreen ? "wd-icon.png" : "fs-icon.png", 14, 14));
            fullscreenButton.setToolTipText(fullscreen ? "Windowed" : "Fullscreen");
        }
    }

    /** Accepts only source types that the debugger supports or is reserved to support. */
    private static boolean isDebuggerFile(Path path) {
        if (path == null || !Files.isRegularFile(path)) return false;
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".asm") || name.endsWith(".exe") || name.endsWith(".c");
    }
    private void openDebugger(Path path) {
        if (!isDebuggerFile(path)) return;
        editorTab.saveAll();
        settings.setCurrentFile(path);
        debuggerTab.debug(path);
        tabs.setSelectedIndex(1);
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        file.add(item("New", KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK, e -> editorTab.newFile()));
        file.add(item("Open Project", KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK, e -> editorTab.openFile()));
        file.add(item("Save", KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK, e -> editorTab.saveFile()));
        file.add(item("Save As", KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, e -> editorTab.saveAsFile()));
        file.add(actionItem("Close File", e -> editorTab.closeFile()));
        file.addSeparator();
        file.add(actionItem("Exit", e -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING))));

        JMenu edit = new JMenu("Edit");
        edit.add(actionItem("Undo", e -> editorTab.performAction("vulcan-undo")));
        edit.add(actionItem("Redo", e -> editorTab.performAction("vulcan-redo")));
        edit.addSeparator();
        edit.add(actionItem("Cut", e -> editorTab.performAction("cut-to-clipboard")));
        edit.add(actionItem("Copy", e -> editorTab.performAction("copy-to-clipboard")));
        edit.add(actionItem("Paste", e -> editorTab.paste()));
        edit.add(actionItem("Select All", e -> editorTab.performAction("select-all")));

        JMenu run = new JMenu("Run");
        run.add(actionItem("Run", e -> runActiveProgram()));
        run.add(actionItem("Assemble", e -> assembleActiveFile()));
        run.add(actionItem("Stop", e -> { editorTab.stopAllRunning(); debuggerTab.stop(); displayPanel.stopProgram(); }));
        run.add(actionItem("Reset", e -> {
            if (tabs.getSelectedIndex() == 1) debuggerTab.reset(); else runActiveProgram();
        }));

        JMenu view = new JMenu("View");
        view.addMenuListener(new javax.swing.event.MenuListener() {
            public void menuSelected(javax.swing.event.MenuEvent event) { populateViewMenu(view); }
            public void menuDeselected(javax.swing.event.MenuEvent event) { }
            public void menuCanceled(javax.swing.event.MenuEvent event) { }
        });
        populateViewMenu(view);

        JMenu settingsMenu = new JMenu("Settings");
        settingsMenu.add(actionItem("Open settings.json", e -> openSettingsFile()));

        JMenu tools = new JMenu("Tools");
        tools.add(actionItem("Assembler", e -> assembleActiveFile()));
        tools.add(actionItem("Disassembler", e -> showDebuggerView("Instructions")));
        tools.add(actionItem("Memory Viewer", e -> showDebuggerView("Memory")));
        tools.add(actionItem("CPU Information", e -> openDocument("cpu.md")));

        JMenu help = new JMenu("Help");
        help.add(actionItem("Documentation", e -> openDocument("documentation.md")));
        help.add(actionItem("Keyboard Shortcuts", e -> openDocument("shortcuts.md")));
        help.add(actionItem("About Vulcan", e -> openDocument("about.md")));

        menuBar.add(file);
        menuBar.add(edit);
        menuBar.add(run);
        menuBar.add(view);
        menuBar.add(settingsMenu);
        menuBar.add(tools);
        menuBar.add(help);
        return menuBar;
    }

    private JMenuItem item(String name, int key, int modifiers, java.awt.event.ActionListener listener) {
        JMenuItem item = new JMenuItem(name);
        item.setAccelerator(KeyStroke.getKeyStroke(key, modifiers));
        item.addActionListener(listener);
        return item;
    }

    /** Rebuilds the View menu from the current workspace's independently toggleable regions. */
    private void populateViewMenu(JMenu menu) {
        menu.removeAll();
        WorkspaceViews views = switch (tabs.getSelectedIndex()) {
            case 1 -> debuggerTab.getViews();
            case 2 -> displayPanel.getViews();
            default -> editorTab.getViews();
        };
        views.options().forEach((name, visible) -> {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(name, visible);
            item.addActionListener(event -> views.set(name, item.isSelected()));
            menu.add(item);
        });
        menu.addSeparator();
        JMenu workspace = new JMenu("Workspace");
        for (int i = 0; i < tabs.getTabCount(); i++) {
            int index = i;
            workspace.add(actionItem(tabs.getTitleAt(i), event -> tabs.setSelectedIndex(index)));
        }
        menu.add(workspace);
    }

    private JMenuItem actionItem(String name, java.awt.event.ActionListener listener) {
        JMenuItem item = new JMenuItem(name);
        item.addActionListener(listener);
        return item;
    }

    private Path activeFile() {
        return switch (tabs.getSelectedIndex()) {
            case 1 -> debuggerTab.getProgramFile();
            case 2 -> displayPanel.getSelectedFile();
            default -> editorTab.getCurrentPath();
        };
    }

    /** Runs the selected workspace's program using its own output destination. */
    private void runActiveProgram() {
        switch (tabs.getSelectedIndex()) {
            case 1 -> { if (debuggerTab.getCpu().isPaused()) debuggerTab.continueExecution(); else debuggerTab.run(); }
            case 2 -> displayPanel.performRun();
            default -> editorTab.runCurrentFile();
        }
    }

    private void assembleActiveFile() {
        if (tabs.getSelectedIndex() == 0) { editorTab.assembleCurrentFile(); return; }
        Path file = activeFile();
        if (file == null || !file.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".asm")) return;
        try { ProgramRunner.assembleToBinary(file); }
        catch (Exception error) {
            (tabs.getSelectedIndex() == 1 ? debuggerTab.getConsole() : displayPanel.getConsole())
                    .appendError("Assembly error: " + VulcanDialog.errorSummary(error) + "\n");
        }
    }

    private void showDebuggerView(String name) {
        Path file = activeFile();
        editorTab.saveAll();
        if (isDebuggerFile(file)) { settings.setCurrentFile(file); debuggerTab.selectFile(file); }
        tabs.setSelectedIndex(1);
        debuggerTab.getViews().set(name, true);
    }

    /** Opens project help as a regular Markdown editor tab. */
    private void openDocument(String name) {
        try {
            Path document = ProjectDocuments.find(name);
            tabs.setSelectedIndex(0);
            editorTab.openPath(document);
        } catch (Exception error) {
            VulcanDialog.message(this, settings, "Documentation", VulcanDialog.errorSummary(error));
        }
    }

    private void openSettingsFile() {
        try {
            Path path = settings.getPath();
            if (!Files.exists(path)) settings.save();
            tabs.setSelectedIndex(0);
            editorTab.openPathFromSettings(path);
        } catch (Exception error) {
            VulcanDialog.message(this, settings, "Settings", VulcanDialog.errorSummary(error));
        }
    }

    private void applyTheme() {
        Theme.configureUI(settings);
        SwingUtilities.updateComponentTreeUI(this);
        Theme.apply(this, settings);
        titleLabel.setFont(new Font(settings.getApplicationFont(), Font.BOLD, settings.getFontSize("applicationTitle")));
        editorTab.refreshTheme();
        debuggerTab.refreshTheme();
        displayPanel.refreshTheme();
        getContentPane().setBackground(settings.getColor("applicationBackground"));
        repaint();
    }

    /** Starts the Vulcan desktop application on the Swing event thread. */
    public static void main(String[] args) {
        Vulcan.configureRenderingDefaults();
        SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
    }
}

