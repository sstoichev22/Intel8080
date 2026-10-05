package Vulcan;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyleConstants;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.KeyEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.MouseWheelEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/** Core editor. The 0.0.6 JTextArea split was a regression: it broke styled editing and was intentionally reverted to the proven JTextPane path. */
public class EditorPanel extends JPanel {
    private static final double WHEEL_SCROLL_FONT_FACTOR = 2.4;
    private static final ExecutorService SAVE_POOL = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "Vulcan-Save"); t.setDaemon(true); return t; });
    private final SettingsManager settings;
    private final ConsolePanel console;
    private final ProgramRunner runner;
    private final JTextPane editor;
    private final LineNumberView lineNumbers;
    private final JButton runButton;
    private final JButton debugButton;
    private final JButton stopButton;
    private final JScrollPane scrollPane;
    private final SearchBar searchBar;
    private final Timer presentationTimer;
    private final Timer autosaveTimer;
    private final Timer runnerStateTimer;
    private final Timer runFeedbackTimer;
    private final Runnable debugAction;
    private final Runnable pathChanged;
    private final UndoManager undoManager = new UndoManager();
    private final Timer undoTimer;
    private CompoundEdit compoundEdit;
    private final Timer completionTimer;
    private JWindow completionPopup;
    private JList<String> completionList;
    private int completionStart;
    private int completionEnd;
    private boolean applyingCompletion;
    private boolean runFeedbackActive;
    private Runnable selectionListener = () -> {};

    private volatile Path currentPath;
    private int fontSize;
    private boolean applyingStyles;
    private boolean loading;
    private boolean dirty;
    private final AtomicLong editSerial = new AtomicLong();
    private final Object saveLock = new Object();
    private boolean zoomChanged;
    private int changedStart = Integer.MAX_VALUE;
    private int changedEnd = 0;
    private Boolean lastRunState;

    /** Creates an editor panel with the shared settings and output console. */
    public EditorPanel(SettingsManager settings, ConsolePanel console,
                       Runnable debugAction, Runnable pathChanged) {
        this.settings = settings;
        this.console = console;
        this.runner = new ProgramRunner();
        this.debugAction = debugAction == null ? () -> {} : debugAction;
        this.pathChanged = pathChanged == null ? () -> {} : pathChanged;
        this.fontSize = settings.getEditorFontSize();

        setLayout(new BorderLayout());
        setBackground(settings.getColor("panelBackground"));

        editor = new NoWrapTextPane();
        editor.setOpaque(true);
        applyEditorFont();
        editor.setBackground(settings.getColor("editorBackground"));
        editor.setForeground(settings.getColor("editorText"));
        editor.setMargin(new Insets(0, 0, 0, 0));
        editor.setEditable(false);
        editor.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 10));
        editor.setCaretColor(settings.getColor("editorText"));
        editor.setDragEnabled(true);

        installEditingActions();
        installSearchShortcuts();
        editor.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE &&
                        e.getModifiersEx() == 0 &&
                        editor.getSelectionStart() == editor.getSelectionEnd() &&
                        editor.getCaretPosition() == 0) {
                    e.consume();
                }
            }
        });
        installSafePaste();
        installUndo();
        completionTimer = new Timer(90, e -> showCompletion());
        completionTimer.setRepeats(false);
        installCompletionActions();

        lineNumbers = new LineNumberView(editor, settings);

        searchBar = new SearchBar(settings);
        searchBar.bind(editor, (query, replace, replacement) -> SearchBar.searchProject(settings.getCurrentDirectory(), query, replacement, replace, console::append));

        scrollPane = new JScrollPane(editor);
        scrollPane.getViewport().setOpaque(true);
        scrollPane.getViewport().setBackground(settings.getColor("editorBackground"));
        scrollPane.setRowHeaderView(lineNumbers);
        scrollPane.setBorder(null);
        Theme.styleScrollBar(scrollPane.getVerticalScrollBar(), settings);
        Theme.styleScrollBar(scrollPane.getHorizontalScrollBar(), settings);

        runButton = iconButton(Icons.vector("run", 15, new Color(47, 184, 92)), "Run", this::performRun);
        debugButton = iconButton(Icons.action(Icons.DEBUG_ICON, "debug", 18, settings.getColor("text")), "Debug", this.debugAction);
        stopButton = iconButton(Icons.vector("stop", 15, settings.getColor("stopButton")), "Stop", this::stopRunning);

        JPanel header = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 4));
        header.setBorder(null);
        header.add(runButton);
        header.add(debugButton);
        header.add(stopButton);

        JPanel north = new JPanel(new BorderLayout());
        north.add(searchBar, BorderLayout.NORTH);
        north.add(header, BorderLayout.SOUTH);
        searchBar.setVisible(false);
        add(north, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        presentationTimer = new Timer(180, e -> refreshChangedPresentation());
        presentationTimer.setRepeats(false);
        autosaveTimer = new Timer(900, e -> saveFileAsync());
        autosaveTimer.setRepeats(false);
        runnerStateTimer = new Timer(120, e -> updateRunDebugButtons());
        runFeedbackTimer = new Timer(500, e -> {
            runFeedbackActive = false;
            updateRunDebugButtons();
        });
        runFeedbackTimer.setRepeats(false);
        undoTimer = new Timer(450, e -> finishCompoundEdit());
        undoTimer.setRepeats(false);
        // Poll run state only for a visible editor; hidden tabs need no button updates.
        addHierarchyListener(e -> {
            if (isShowing()) runnerStateTimer.start();
            else runnerStateTimer.stop();
        });

        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { documentChanged(e); }
            @Override public void removeUpdate(DocumentEvent e) { documentChanged(e); }
            @Override public void changedUpdate(DocumentEvent e) { if (!applyingStyles) dirty = true; }
        });

        editor.addMouseWheelListener(this::handleMouseWheel);
        editor.addCaretListener(e -> {
            SwingUtilities.invokeLater(this::keepCaretVisible);
            if (!applyingCompletion) hideCompletionPopup();
            if (!applyingCompletion && currentPathIsAsm() && editor.isEditable()) completionTimer.restart();
        });
        editor.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent event) { hideCompletionPopup(); }
            @Override public void focusGained(java.awt.event.FocusEvent event) { selectionListener.run(); }
        });
        showEditorIfLoaded();
        updateRunDebugButtons();
    }

    private JButton iconButton(Icon icon, String tooltip, Runnable action) {
        JButton button = new JButton(icon);
        button.setToolTipText(tooltip);
        button.setFocusPainted(false);
        button.setPreferredSize(new Dimension(31, 27));
        button.setBorder(BorderFactory.createEmptyBorder(4, 7, 4, 7));
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.addActionListener(e -> action.run());
        return button;
    }

    private JButton actionButton(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(settings.getColor("splitDivider")),
                BorderFactory.createEmptyBorder(3, 9, 3, 9)
        ));
        button.setContentAreaFilled(true);
        button.setOpaque(true);
        button.setBackground(settings.getColor("panelBackground"));
        button.setForeground(settings.getColor("text"));
        button.addActionListener(e -> action.run());
        return button;
    }

    private void installEditingActions() {
        InputMap input = editor.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actions = editor.getActionMap();

        input.put(KeyStroke.getKeyStroke("pressed TAB"), "vulcan-tab");
        actions.put("vulcan-tab", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { editor.replaceSelection("    "); }
        });
        input.put(KeyStroke.getKeyStroke("shift TAB"), "vulcan-shift-tab");
        actions.put("vulcan-shift-tab", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { unindentCurrentLine(); }
        });

        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "vulcan-backspace");
        editor.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "vulcan-backspace");
        actions.put("vulcan-backspace", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                int start = editor.getSelectionStart();
                int end = editor.getSelectionEnd();
                if (start != end) {
                    editor.replaceSelection("");
                    return;
                }
                if (start > 0) {
                    try { editor.getDocument().remove(start - 1, 1); }
                    catch (BadLocationException ignored) { }
                }
            }
        });

        input.put(KeyStroke.getKeyStroke("pressed DELETE"), "vulcan-delete");
        actions.put("vulcan-delete", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                int start = editor.getSelectionStart();
                int end = editor.getSelectionEnd();
                if (start != end) {
                    editor.replaceSelection("");
                    return;
                }
                if (start < editor.getDocument().getLength()) {
                    try { editor.getDocument().remove(start, 1); }
                    catch (BadLocationException ignored) { }
                }
            }
        });
    }

    private void installSearchShortcuts() {
        InputMap input = editor.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actions = editor.getActionMap();
        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK), "find-current");
        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK), "find-project");
        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK), "replace-current");
        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK), "replace-project");
        actions.put("find-current", new AbstractAction() { public void actionPerformed(java.awt.event.ActionEvent e) { searchBar.showFind(false, false); } });
        actions.put("find-project", new AbstractAction() { public void actionPerformed(java.awt.event.ActionEvent e) { searchBar.showFind(true, false); } });
        actions.put("replace-current", new AbstractAction() { public void actionPerformed(java.awt.event.ActionEvent e) { searchBar.showFind(false, true); } });
        actions.put("replace-project", new AbstractAction() { public void actionPerformed(java.awt.event.ActionEvent e) { searchBar.showFind(true, true); } });
    }

    private void unindentCurrentLine() {
        try {
            int caret = editor.getCaretPosition();
            int lineStart = javax.swing.text.Utilities.getRowStart(editor, caret);
            if (lineStart < 0) return;
            int length = Math.min(4, editor.getDocument().getLength() - lineStart);
            String text = editor.getDocument().getText(lineStart, length);
            int remove = 0;
            while (remove < text.length() && remove < 4 && text.charAt(remove) == ' ') remove++;
            if (remove > 0) editor.getDocument().remove(lineStart, remove);
        } catch (BadLocationException ignored) {
        }
    }

    private void documentChanged(DocumentEvent event) {
        if (loading || applyingStyles) return;
        dirty = true;
        editSerial.incrementAndGet();
        changedStart = Math.min(changedStart, event.getOffset());
        int end = event.getOffset() + Math.max(1, event.getLength());
        if (event.getType() == DocumentEvent.EventType.INSERT) end += 1;
        changedEnd = Math.max(changedEnd, end);
        if (event.getType() == DocumentEvent.EventType.INSERT || event.getType() == DocumentEvent.EventType.REMOVE) {
            boolean lineStructureChanged = event.getLength() > 1;
            if (event.getType() == DocumentEvent.EventType.INSERT && event.getLength() > 0) {
                try { lineStructureChanged = editor.getDocument().getText(event.getOffset(), event.getLength()).indexOf('\n') >= 0 || lineStructureChanged; } catch (BadLocationException ignored) {}
            }
            if (event.getType() == DocumentEvent.EventType.REMOVE && event.getOffset() < editor.getDocument().getLength()) {
                try { lineStructureChanged = editor.getDocument().getText(event.getOffset(), 1).indexOf('\n') >= 0 || lineStructureChanged; } catch (BadLocationException ignored) {}
            }
            if (lineStructureChanged) lineNumbers.revalidate();
            lineNumbers.repaint();
        }
        presentationTimer.restart();
        autosaveTimer.restart();
        if (!applyingCompletion && currentPathIsAsm()) completionTimer.restart();
    }

    private void handleMouseWheel(MouseWheelEvent event) {
        if (event.isControlDown()) {
            event.consume();
            int newSize = Math.max(8, Math.min(48, fontSize - event.getWheelRotation()));
            if (newSize != fontSize) {
                fontSize = newSize;
                settings.setFileFontSize(currentPath, fontSize);
                zoomChanged = true;
                applyEditorFont();
                lineNumbers.revalidate();
                lineNumbers.repaint();
                applyingStyles = true;
                try { SyntaxHighlighter.apply(editor, extension(), settings.isSyntaxHighlighting(), settings); }
                finally { applyingStyles = false; }
            }
            return;
        }

        JScrollBar bar = scrollPane.getVerticalScrollBar();
        int delta = (int) Math.round(event.getPreciseWheelRotation() * Math.max(10, fontSize * WHEEL_SCROLL_FONT_FACTOR));
        int value = Math.max(0, Math.min(bar.getMaximum(), bar.getValue() + delta));
        bar.setValue(value);
        event.consume();
    }

    private void installSafePaste() {
        Action paste = new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                try {
                    Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
                    if (data instanceof String text) editor.replaceSelection(text);
                } catch (UnsupportedFlavorException | IOException | IllegalStateException ignored) {
                }
            }
        };
        editor.getActionMap().put("vulcanPaste", paste);
        editor.getInputMap().put(KeyStroke.getKeyStroke("control V"), "vulcanPaste");
        editor.getInputMap().put(KeyStroke.getKeyStroke("shift INSERT"), "vulcanPaste");
    }

    private void installUndo() {
        editor.getDocument().addUndoableEditListener(e -> {
            if (loading || applyingStyles) return;
            if (compoundEdit == null) compoundEdit = new CompoundEdit();
            compoundEdit.addEdit(e.getEdit());
            undoTimer.restart();
        });
        InputMap input = editor.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actions = editor.getActionMap();
        input.put(KeyStroke.getKeyStroke("control Z"), "vulcan-undo");
        input.put(KeyStroke.getKeyStroke("control Y"), "vulcan-redo");
        actions.put("vulcan-undo", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                finishCompoundEdit();
                if (undoManager.canUndo()) undoManager.undo();
            }
        });
        actions.put("vulcan-redo", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                finishCompoundEdit();
                if (undoManager.canRedo()) undoManager.redo();
            }
        });
    }

    private void finishCompoundEdit() {
        if (compoundEdit == null) return;
        compoundEdit.end();
        undoManager.addEdit(compoundEdit);
        compoundEdit = null;
    }

    private void showCompletion() {
        if (!currentPathIsAsm() || !editor.isEditable() || !editor.isFocusOwner()) {
            hideCompletionPopup();
            return;
        }
        try {
            int pos = editor.getCaretPosition();
            String text = documentText();
            String prefix = AsmCompletion.prefixAt(text, pos);
            completionStart = pos-prefix.length();
            completionEnd = pos;
            if (prefix.isEmpty()) { hideCompletionPopup(); return; }
            java.util.List<String> matches = AsmCompletion.suggestions(text, prefix);
            if (matches.isEmpty()) { hideCompletionPopup(); return; }
            hideCompletionPopup();
            completionPopup = new JWindow(SwingUtilities.getWindowAncestor(this));
            completionPopup.setFocusableWindowState(false);
            completionPopup.setAutoRequestFocus(false);
            completionList = new JList<>(matches.toArray(String[]::new));
            completionList.setVisibleRowCount(Math.min(8, matches.size()));
            completionList.setFocusable(false);
            completionList.setSelectedIndex(0);
            completionList.setFont(editor.getFont());
            completionList.setForeground(settings.getColor("text"));
            completionList.setBackground(settings.getColor("panelBackground"));
            completionList.setSelectionBackground(settings.getColor("tabSelected"));
            completionList.setSelectionForeground(settings.getColor("text"));
            completionList.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            completionList.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mousePressed(java.awt.event.MouseEvent event) {
                    completionList.setSelectedIndex(completionList.locationToIndex(event.getPoint()));
                    applyCompletion();
                }
            });
            JScrollPane choices = new JScrollPane(completionList);
            choices.setBorder(null);
            completionPopup.add(choices);
            completionPopup.pack();
            Rectangle r = editor.modelToView2D(pos).getBounds();
            Point point = new Point(r.x, r.y + r.height);
            SwingUtilities.convertPointToScreen(point, editor);
            Rectangle screen = editor.getGraphicsConfiguration().getBounds();
            point.x = Math.max(screen.x, Math.min(point.x, screen.x + screen.width - completionPopup.getWidth()));
            if (point.y + completionPopup.getHeight() > screen.y + screen.height)
                point.y -= completionPopup.getHeight() + r.height;
            completionPopup.setLocation(point);
            completionPopup.setVisible(true);
        } catch (Exception ignored) { hideCompletionPopup(); }
    }

    /** Routes completion keys through Swing actions, retaining ordinary editing when no match exists. */
    private void installCompletionActions() {
        int[] keys = {KeyEvent.VK_ENTER, KeyEvent.VK_TAB, KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_ESCAPE};
        for (int key : keys) {
            KeyStroke stroke = KeyStroke.getKeyStroke(key, 0);
            Object fallbackName = editor.getInputMap().get(stroke);
            Action fallback = fallbackName == null ? null : editor.getActionMap().get(fallbackName);
            String name = "completion-" + key;
            editor.getInputMap().put(stroke, name);
            editor.getActionMap().put(name, new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                    if (completionPopup == null && completionTimer.isRunning() &&
                            (key == KeyEvent.VK_ENTER || key == KeyEvent.VK_TAB)) showCompletion();
                    if (completionPopup != null) {
                        if (key == KeyEvent.VK_ENTER || key == KeyEvent.VK_TAB) { applyCompletion(); return; }
                        if (key == KeyEvent.VK_ESCAPE) { completionTimer.stop(); hideCompletionPopup(); return; }
                        int count = completionList.getModel().getSize();
                        int selected = Math.floorMod(completionList.getSelectedIndex() + (key == KeyEvent.VK_UP ? -1 : 1), count);
                        completionList.setSelectedIndex(selected);
                        completionList.ensureIndexIsVisible(selected);
                        return;
                    }
                    if (fallback != null) fallback.actionPerformed(event);
                }
            });
        }
        editor.setFocusTraversalKeysEnabled(false);
    }

    private void hideCompletionPopup() {
        if (completionPopup == null) return;
        completionPopup.setVisible(false);
        completionPopup.dispose();
        completionPopup = null;
        completionList = null;
    }

    /** Inserts the chosen suggestion while keeping typing and navigation in the editor. */
    private void applyCompletion() {
        if (completionList == null || completionList.getSelectedValue() == null) return;
        String match = completionList.getSelectedValue();
        completionEnd = editor.getCaretPosition();
        String prefix = AsmCompletion.prefixAt(documentText(), completionEnd);
        if (prefix.isEmpty()) { hideCompletionPopup(); return; }
        completionStart = completionEnd - prefix.length();
        completionTimer.stop();
        hideCompletionPopup();
        applyingCompletion = true;
        try {
            editor.getDocument().remove(completionStart, completionEnd-completionStart);
            editor.getDocument().insertString(completionStart, match, null);
            editor.setCaretPosition(completionStart+match.length());
        } catch (BadLocationException ignored) { }
        finally { applyingCompletion = false; }
    }

    private boolean currentPathIsAsm() {
        return currentPath != null && currentPath.getFileName().toString().toLowerCase().endsWith(".asm");
    }

    /** Reads text using document offsets; editor-kit serialization can expand Windows line endings. */
    private String documentText() {
        try { return editor.getDocument().getText(0, editor.getDocument().getLength()); }
        catch (BadLocationException ignored) { return ""; }
    }

    private void keepCaretVisible() {
        try {
            Rectangle r = editor.modelToView2D(editor.getCaretPosition()).getBounds();
            editor.scrollRectToVisible(new Rectangle(Math.max(0, r.x - 24), r.y, r.width + 48, Math.max(1, r.height)));
        } catch (Exception ignored) { }
    }

    private void refreshChangedPresentation() {
        if (!extension().equalsIgnoreCase("asm")) { changedStart = Integer.MAX_VALUE; changedEnd = 0; return; }
        int start = changedStart == Integer.MAX_VALUE ? 0 : changedStart;
        int end = Math.max(start + 1, changedEnd);
        changedStart = Integer.MAX_VALUE;
        changedEnd = 0;
        applyingStyles = true;
        try {
            SyntaxHighlighter.applyRange(editor, extension(), settings.isSyntaxHighlighting(), settings, start, end);
        } finally {
            applyingStyles = false;
        }
    }

    private String extension() {
        if (currentPath == null) return "";
        String name = currentPath.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : "";
    }

    /** Opens a file in this editor, or clears it when the path is null. */
    public void openFile(Path path) {
        if (path == null) {
            runner.stop();
            editSerial.incrementAndGet();
            currentPath = null;
            fontSize = settings.getEditorFontSize();
            applyEditorFont();
            settings.setSettingsEditing(false);
            loading = true;
            try {
                editor.setText("");
                editor.setCaretPosition(0);
                editor.setEditable(true);
                undoManager.discardAllEdits();
                compoundEdit = null;
            } finally {
                loading = false;
            }
            dirty = false;
            updateRunDebugButtons();
            pathChanged.run();
            return;
        }
        runner.stop();
        Path absolute = path.toAbsolutePath().normalize();
        boolean executable = isExecutableFile(absolute);
        final String text;
        try {
            if (executable && Files.size(absolute) > 0xFF00L) {
                throw new IOException("Executable is too large to display in the editor.");
            }
            text = executable
                    ? formatBinary(Files.readAllBytes(absolute))
                    : Files.readString(absolute, StandardCharsets.UTF_8);
        } catch (IOException error) {
            console.appendError("Open error: " + VulcanDialog.errorSummary(error) + "\n");
            return;
        }

        if (isSettingsFile(currentPath) && !isSettingsFile(absolute)) {
            if (!saveFileInternal()) return;
            settings.setSettingsEditing(false);
        }

        currentPath = absolute;
        fontSize = settings.getFileFontSize(currentPath);
        applyEditorFont();
        editSerial.incrementAndGet();
        boolean settingsFile = isSettingsFile(absolute);
        settings.setSettingsEditing(settingsFile);
        if (!settingsFile) settings.setCurrentFile(currentPath);

        loading = true;
        try {
            if (executable) {
                // Bypass the editor kit's CR/LF normalization: every character represents one actual byte.
                editor.getDocument().remove(0, editor.getDocument().getLength());
                editor.getDocument().insertString(0, text, null);
            } else editor.setText(text);
            undoManager.discardAllEdits();
            compoundEdit = null;
            editor.setCaretPosition(0);
            editor.setEditable(!executable);
            scrollPane.setRowHeaderView(executable ? null : lineNumbers);
            loading = false;

            dirty = false;
            updateRunDebugButtons();
            applyingStyles = true;
            try { SyntaxHighlighter.apply(editor, extension(), settings.isSyntaxHighlighting(), settings); }
            finally { applyingStyles = false; }
            lineNumbers.revalidate();
            lineNumbers.repaint();
            restoreSavedScrollPosition(absolute);
            pathChanged.run();
        } catch (BadLocationException error) {
            console.appendError("Open error: " + VulcanDialog.errorSummary(error) + "\n");
        } finally {
            loading = false;
        }
    }

    private boolean isSettingsFile(Path path) {
        return path != null && path.toAbsolutePath().normalize().equals(settings.getPath());
    }

    /** Keeps both the visible font and the caret's typing attributes at this file's current size. */
    private void applyEditorFont() {
        Font font = new Font(settings.getEditorFont(), Font.PLAIN, fontSize);
        editor.setFont(font);
        editor.getInputAttributes().addAttribute(StyleConstants.FontFamily, font.getFamily());
        editor.getInputAttributes().addAttribute(StyleConstants.FontSize, fontSize);
        editor.getInputAttributes().addAttribute(StyleConstants.Bold, false);
        editor.getInputAttributes().addAttribute(StyleConstants.Italic, false);
    }

    private boolean isExecutableFile(Path path) {
        return path != null && path.getFileName().toString().toLowerCase().endsWith(".exe");
    }

    /** Displays one character per byte without inserting addresses or changing the executable on disk. */
    private String formatBinary(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    private void saveFileAsync() {
        if (!dirty || currentPath == null || isExecutableFile(currentPath) || isSettingsFile(currentPath)) return;
        Path path = currentPath;
        String text = editor.getText();
        long serial = editSerial.get();
        SAVE_POOL.submit(() -> {
            try {
                synchronized (saveLock) {
                    if (editSerial.get() != serial || !path.equals(currentPath)) return;
                    writeAtomically(path, text);
                }
                SwingUtilities.invokeLater(() -> {
                    if (editSerial.get() == serial && path.equals(currentPath)) dirty = false;
                });
            } catch (IOException error) {
                SwingUtilities.invokeLater(() -> {
                    if (editSerial.get() == serial && path.equals(currentPath)) {
                        console.appendError("Autosave failed: " + VulcanDialog.errorSummary(error) + "\n");
                    }
                });
            }
        });
    }

    /** Saves the current editable file to disk. */
    public void saveFile() {
        saveFileInternal();
    }

    /** Keeps this buffer and its pending autosaves attached to a moved or renamed file. */
    public void relocateFile(Path destination) {
        synchronized (saveLock) {
            editSerial.incrementAndGet();
            currentPath = destination.toAbsolutePath().normalize();
        }
        settings.setFileFontSize(currentPath, fontSize);
        settings.setFileScrollPosition(currentPath, getScrollPosition());
        pathChanged.run();
    }

    /** Notifies the workspace when this buffer receives typing focus. */
    public void setSelectionListener(Runnable listener) { selectionListener = listener == null ? () -> {} : listener; }

    /** Saves a closing buffer without a prompt, giving untitled content a unique project filename. */
    public boolean saveOnClose(String untitledName) {
        autosaveTimer.stop();
        if (!dirty) return true;
        if (currentPath == null) {
            Path directory = settings.getCurrentDirectory();
            String base = untitledName == null || untitledName.isBlank() ? "Untitled" : untitledName;
            try {
                Files.createDirectories(directory);
                for (int suffix = 0; ; suffix++) {
                    Path target = directory.resolve(base + (suffix == 0 ? "" : " ("+suffix+")") + ".asm");
                    try { Files.createFile(target); currentPath = target; break; }
                    catch (java.nio.file.FileAlreadyExistsException ignored) { }
                }
                settings.setFileFontSize(currentPath, fontSize);
                updateRunDebugButtons();
                pathChanged.run();
            } catch (IOException error) {
                console.appendError("Save error: " + VulcanDialog.errorSummary(error) + "\n");
                return false;
            }
        }
        return saveFileInternal();
    }

    private boolean saveFileInternal() {
        Path target = currentPath;
        if (target == null || isExecutableFile(target)) return true;
        try {
            synchronized (saveLock) {
                writeAtomically(target, editor.getText());
            }
            dirty = false;
            if (isSettingsFile(target)) {
                settings.reloadFromDisk();
                settings.setSettingsEditing(true);
                pathChanged.run();
            } else {
                settings.setSettingsEditing(false);
                settings.setCurrentFile(target);
            }
            return true;
        } catch (IOException ex) {
            console.appendError("Save error: " + VulcanDialog.errorSummary(ex) + "\n");
            return false;
        }
    }

    private static void writeAtomically(Path target, String text) throws IOException {
        Path absolute = target.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null) throw new IOException("The file has no parent directory.");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".vulcan-save-", ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Prompts for a destination and saves the current editor buffer there. */
    public void saveAsFile() {
        JFileChooser chooser = new JFileChooser(
                currentPath == null || currentPath.getParent() == null
                        ? settings.getCurrentDirectory().toFile()
                        : currentPath.getParent().toFile());
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path selected = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        if (isExecutableFile(selected)) {
            VulcanDialog.message(this, settings, "Save As", "Executable files are read-only in the editor. Choose a source file name instead.");
            return;
        }
        currentPath = selected;
        settings.setFileFontSize(selected, fontSize);
        settings.setSettingsEditing(isSettingsFile(selected));
        if (!isSettingsFile(selected)) settings.setCurrentFile(selected);
        editor.setEditable(!isExecutableFile(selected));
        dirty = true;
        saveFile();
        updateRunDebugButtons();
        pathChanged.run();
    }

    @Override public void removeNotify() {
        runnerStateTimer.stop();
        presentationTimer.stop();
        autosaveTimer.stop();
        completionTimer.stop();
        hideCompletionPopup();
        undoTimer.stop();
        super.removeNotify();
    }

    @Override public void addNotify() {
        super.addNotify();
        if (isShowing()) runnerStateTimer.start();
    }

    /** Reports whether the editor has changes that have not been saved. */
    public boolean isDirty() { return dirty; }

    void discardUnsavedChanges() {
        editSerial.incrementAndGet();
        dirty = false;
        autosaveTimer.stop();
    }

    /** Releases this editor buffer after the caller saves its content. */
    public void closeFile() {
        runner.stop();
        runFeedbackTimer.stop();
        runFeedbackActive = false;
        autosaveTimer.stop();
        completionTimer.stop();
        hideCompletionPopup();
        boolean settingsFile = isSettingsFile(currentPath);
        editSerial.incrementAndGet();
        if (settingsFile) settings.setSettingsEditing(false);
        currentPath = null;
        undoManager.discardAllEdits();
        compoundEdit = null;
        loading = true;
        try {
            editor.setText("");
            editor.setEditable(false);
            editor.setCaretPosition(0);
        } finally {
            loading = false;
        }
        dirty = false;
        updateRunDebugButtons();
        pathChanged.run();
    }

    /** Runs the current source or executable file in the editor's runner. */
    public void performRun() {
        if (!canRunCurrentFile()) return;
        console.setInputHandler(runner::submitInput);
        runner.stop();
        console.clear();
        runFeedbackActive = true;
        runFeedbackTimer.restart();
        updateRunDebugButtons();
        if (extension().equalsIgnoreCase("asm")) {
            saveFile();
            runner.runSource(editor.getText(), console::append, console::appendByte, this::refreshRunDebugButtonsOnEdt);
        } else {
            runner.runBinary(currentPath, console::append, console::appendByte, this::refreshRunDebugButtonsOnEdt);
        }
        updateRunDebugButtons();
    }

    /** Stops this editor's active program. */
    public void stopRunning() { runner.stop(); updateRunDebugButtons(); }

    /** Reports whether the current editor file has a supported runnable extension. */
    public boolean canRunCurrentFile() {
        String ext = extension();
        return ext.equalsIgnoreCase("asm") || ext.equalsIgnoreCase("exe");
    }

    /** Reports whether the current editor file can be loaded into the debugger. */
    public boolean canDebugCurrentFile() {
        String ext = extension();
        return ext.equalsIgnoreCase("asm") || ext.equalsIgnoreCase("exe") || ext.equalsIgnoreCase("c");
    }

    /** Restores this file viewport after Swing has measured its document. */
    private void restoreSavedScrollPosition(Path file) {
        Point saved = settings.getFileScrollPosition(file);
        if (saved == null) return;
        Timer restore = new Timer(100, event -> {
            if (!file.equals(currentPath)) return;
            JViewport viewport = scrollPane.getViewport();
            Dimension view = viewport.getViewSize();
            Dimension extent = viewport.getExtentSize();
            int x = Math.min(saved.x, Math.max(0, view.width - extent.width));
            int y = Math.min(saved.y, Math.max(0, view.height - extent.height));
            viewport.setViewPosition(new Point(x, y));
        });
        restore.setRepeats(false);
        restore.start();
    }

    /** Returns the current editor viewport offset for close-time persistence. */
    public Point getScrollPosition() {
        return new Point(scrollPane.getViewport().getViewPosition());
    }
    /** Returns the path opened in this editor, or null for an untitled buffer. */
    public Path getCurrentPath() { return currentPath; }

    /** Applies the current theme colors and refreshes this component. */
    public void refreshTheme() {
        applyEditorFont();
        editor.setBackground(settings.getColor("editorBackground"));
        editor.setForeground(settings.getColor("editorText"));
        editor.setCaretColor(settings.getColor("editorText"));
        scrollPane.getViewport().setBackground(settings.getColor("editorBackground"));
        lineNumbers.refreshTheme();
        applyingStyles = true;
        try { SyntaxHighlighter.apply(editor, extension(), settings.isSyntaxHighlighting(), settings); }
        finally { applyingStyles = false; }
        lastRunState = null;
        updateRunDebugButtons();
        repaint();
    }

    /** Invokes an editor action by its registered action-map name. */
    public void performAction(String actionName) {
        Action action = editor.getActionMap().get(actionName);
        if (action != null) action.actionPerformed(new java.awt.event.ActionEvent(editor,
                java.awt.event.ActionEvent.ACTION_PERFORMED, actionName));
    }

    /** Returns keyboard focus to this file after selecting its tab from the file browser. */
    public void focusEditor() { editor.requestFocusInWindow(); }

    /** Pastes clipboard content into the active editor. */
    public void paste() { performAction("vulcanPaste"); }

    private void updateRunDebugButtons() {
        boolean canRun = canRunCurrentFile();
        boolean running = runner.isRunning();
        if (runButton.isVisible() != canRun) runButton.setVisible(canRun);
        boolean canDebug = canDebugCurrentFile();
        if (debugButton.isVisible() != canDebug) debugButton.setVisible(canDebug);
        boolean canStop = canRun && running;
        if (stopButton.isVisible() != canStop) stopButton.setVisible(canStop);
        boolean showRunningFeedback = running || runFeedbackActive;
        if (lastRunState == null || lastRunState != showRunningFeedback) {
            runButton.setIcon(Icons.vector(showRunningFeedback ? "running" : "run", 18,
                    new Color(47, 184, 92)));
            lastRunState = showRunningFeedback;
        }
        runButton.setToolTipText(running ? "Rerun" : runFeedbackActive ? "Starting…" : "Run");
    }

    /** Applies runner completion changes to Swing controls on the event dispatch thread. */
    private void refreshRunDebugButtonsOnEdt() {
        if (SwingUtilities.isEventDispatchThread()) updateRunDebugButtons();
        else SwingUtilities.invokeLater(this::updateRunDebugButtons);
    }

    private static final class NoWrapTextPane extends JTextPane {
        /** Keeps horizontal scrolling available without querying preferred size recursively. */
        @Override public boolean getScrollableTracksViewportWidth() {
            return false;
        }

        /** Keeps short documents painted across the viewport without enabling line wrapping. */
        @Override public Dimension getPreferredSize() {
            Dimension preferred = new Dimension(super.getPreferredSize());
            if (getParent() instanceof JViewport viewport) {
                Dimension extent = viewport.getExtentSize();
                preferred.width = Math.max(preferred.width, extent.width);
                preferred.height = Math.max(preferred.height, extent.height);
            }
            return preferred;
        }
    }

    private void showEditorIfLoaded() {
        editor.setBackground(settings.getColor("editorBackground"));
    }


}
