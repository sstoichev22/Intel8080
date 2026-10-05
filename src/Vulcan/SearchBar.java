package Vulcan;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Element;
import java.awt.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reusable find/replace UI. Search work over the project is deliberately kept
 * off the EDT; the editor itself remains an ordinary Swing document.
 */
public final class SearchBar extends JPanel {
    private static final int LINE_FADE_INTERVAL_MS = 50;
    private static final int LINE_FADE_STEPS = 12;
    public interface ProjectSearchListener { void searchProject(String query, boolean replace, String replacement); }

    private final JTextField find = new JTextField();
    private final JTextField replace = new JTextField();
    private final JLabel status = new JLabel();
    private final JButton close = new JButton("×");
    private final Timer lineFadeTimer;
    private Object lineHighlight;
    private int highlightedLineStart;
    private int highlightedLineEnd;
    private int fadeStep;
    private JTextPane editor;
    private SettingsManager settings;
    private boolean replaceMode;
    private boolean projectMode;
    private ProjectSearchListener projectListener;
    private final JButton projectSearchButton;
    private final JButton projectReplaceButton;

    /** Creates search and replace controls for a source editor. */
    public SearchBar(SettingsManager settings) {
        this.settings = settings;
        setLayout(new BorderLayout(6, 0));
        setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        lineFadeTimer = new Timer(LINE_FADE_INTERVAL_MS, e -> fadeLineHighlight());
        lineFadeTimer.setRepeats(true);
        find.setToolTipText("Find");
        replace.setToolTipText("Replace with");
        add(find, BorderLayout.CENTER);
        replace.setVisible(false);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        JButton previous = button("↑", e -> find(false));
        JButton next = button("↓", e -> find(true));
        JButton replaceOne = button("Replace", e -> replaceOne());
        JButton replaceAll = button("Replace All", e -> replaceAllCurrent());
        projectSearchButton = button("Search Project", e -> runProjectSearch(false));
        projectReplaceButton = button("Replace Project", e -> runProjectSearch(true));
        projectSearchButton.setVisible(false);
        projectReplaceButton.setVisible(false);
        actions.add(previous); actions.add(next); actions.add(replaceOne); actions.add(replaceAll);
        actions.add(projectSearchButton); actions.add(projectReplaceButton);
        add(actions, BorderLayout.WEST);
        JPanel trailing = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        trailing.setOpaque(false);
        trailing.add(status);
        trailing.add(replace);
        trailing.add(close);
        add(trailing, BorderLayout.EAST);
        close.setVisible(false);
        close.setToolTipText("Close find and replace");
        close.setFont(close.getFont().deriveFont(Font.BOLD, 24f));
        close.setPreferredSize(new Dimension(36, 30));
        close.setBorder(null);
        close.setContentAreaFilled(false);
        close.addActionListener(e -> { lineFadeTimer.stop(); removeLineHighlight(); setVisible(false); });
        find.addActionListener(e -> find(true));
    }

    private JButton button(String text, java.awt.event.ActionListener listener) {
        JButton b = new JButton(text);
        b.setFocusPainted(false);
        b.addActionListener(listener);
        return b;
    }

    /** Connects this search bar to an editor and an optional project search callback. */
    public void bind(JTextPane editor, ProjectSearchListener listener) {
        this.editor = editor;
        this.projectListener = listener;
    }

    /** Shows the search controls in the requested current-file or project mode. */
    public void showFind(boolean project, boolean replaceMode) {
        this.replaceMode = replaceMode;
        this.projectMode = project;
        setVisible(true);
        replace.setVisible(replaceMode);
        projectSearchButton.setVisible(project);
        projectReplaceButton.setVisible(project && replaceMode);
        close.setVisible(true);
        if (editor != null) {
            String selected = editor.getSelectedText();
            if (selected != null && !selected.contains("\n")) find.setText(selected);
        }
        revalidate();
        find.requestFocusInWindow();
        if (project && projectListener != null) projectListener.searchProject(find.getText(), false, "");
    }

    private void runProjectSearch(boolean replaceFiles) {
        if (!projectMode || projectListener == null || find.getText().isEmpty()) return;
        if (replaceFiles) {
            int answer = JOptionPane.showConfirmDialog(this,
                    "Replace every occurrence of \"" + find.getText() + "\" with \"" + replace.getText() + "\" in the project?",
                    "Replace Project", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) return;
        }
        projectListener.searchProject(find.getText(), replaceFiles, replace.getText());
    }

    private void find(boolean forward) {
        if (editor == null) return;
        String query = find.getText();
        if (query.isEmpty()) return;
        try {
            String text = editor.getDocument().getText(0, editor.getDocument().getLength());
            int start = editor.getCaretPosition();
            int index = forward ? text.indexOf(query, Math.min(text.length(), start + (editor.getSelectedText() == null ? 0 : 1)))
                    : text.lastIndexOf(query, Math.max(0, start - 1));
            if (index < 0 && forward) index = text.indexOf(query);
            if (index < 0 && !forward) index = text.lastIndexOf(query);
            if (index >= 0) {
                editor.select(index, index + query.length());
                status.setText("Found");
                highlightAndCenterLine(index);
            } else status.setText("Not found");
        } catch (BadLocationException ignored) { status.setText("Search error"); }
    }

    /** Centers the matched source line and briefly paints it with a fading highlight. */
    private void highlightAndCenterLine(int matchOffset) {
        try {
            Element root = editor.getDocument().getDefaultRootElement();
            Element line = root.getElement(root.getElementIndex(matchOffset));
            highlightedLineStart = Math.max(0, line.getStartOffset());
            highlightedLineEnd = Math.min(editor.getDocument().getLength(), line.getEndOffset());

            JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, editor);
            if (viewport != null) {
                Rectangle lineBounds = editor.modelToView2D(highlightedLineStart).getBounds();
                Dimension extent = viewport.getExtentSize();
                Dimension view = viewport.getViewSize();
                int y = lineBounds.y - Math.max(0, (extent.height - lineBounds.height) / 2);
                int maxY = Math.max(0, view.height - extent.height);
                viewport.setViewPosition(new Point(viewport.getViewPosition().x, Math.max(0, Math.min(y, maxY))));
            }

            lineFadeTimer.stop();
            fadeStep = 0;
            paintLineHighlight(170);
            lineFadeTimer.start();
        } catch (BadLocationException ignored) {
            lineFadeTimer.stop();
        }
    }

    /** Reduces the matched-line overlay until it disappears. */
    private void fadeLineHighlight() {
        fadeStep++;
        if (fadeStep >= LINE_FADE_STEPS) {
            lineFadeTimer.stop();
            removeLineHighlight();
            return;
        }
        paintLineHighlight(Math.max(0, 170 - (170 * fadeStep / LINE_FADE_STEPS)));
    }

    /** Replaces the active line overlay with the requested transparency. */
    private void paintLineHighlight(int alpha) {
        removeLineHighlight();
        if (editor == null || highlightedLineEnd <= highlightedLineStart || alpha <= 0) return;
        Color base = settings.getColor("stack");
        Color translucent = new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
        try {
            lineHighlight = editor.getHighlighter().addHighlight(highlightedLineStart, highlightedLineEnd,
                    new DefaultHighlighter.DefaultHighlightPainter(translucent));
        } catch (BadLocationException ignored) {
            lineHighlight = null;
        }
    }

    /** Removes the current transient line overlay from the editor. */
    private void removeLineHighlight() {
        if (editor != null && lineHighlight != null) {
            editor.getHighlighter().removeHighlight(lineHighlight);
            lineHighlight = null;
        }
    }

    private void replaceOne() {
        if (editor == null) return;
        String selected = editor.getSelectedText();
        if (selected != null && selected.equals(find.getText())) {
            editor.replaceSelection(replace.getText());
        }
        find(true);
    }

    private void replaceAllCurrent() {
        if (editor == null || find.getText().isEmpty()) return;
        String query = find.getText();
        String replacement = replace.getText();
        try {
            String text = editor.getDocument().getText(0, editor.getDocument().getLength());
            int count = 0, at = 0;
            StringBuilder out = new StringBuilder(text.length());
            while (true) {
                int hit = text.indexOf(query, at);
                if (hit < 0) { out.append(text, at, text.length()); break; }
                out.append(text, at, hit).append(replacement);
                at = hit + query.length(); count++;
            }
            if (count > 0) editor.setText(out.toString());
            status.setText(count + " replaced");
        } catch (BadLocationException ignored) { status.setText("Replace error"); }
    }

    /** Sets the theme. */
    public void setTheme(SettingsManager settings) {
        this.settings = settings;
        setBackground(settings.getColor("panelBackground"));
        find.setBackground(settings.getColor("inputBackground"));
        replace.setBackground(settings.getColor("inputBackground"));
        find.setForeground(settings.getColor("text"));
        replace.setForeground(settings.getColor("text"));
        status.setForeground(settings.getColor("text"));
        repaint();
    }

    /** Searches readable project files and optionally replaces matching text safely. */
    public static void searchProject(Path root, String query, String replacement, boolean replace, java.util.function.Consumer<String> result) {
        if (result == null) return;
        if (root == null || query == null || query.isEmpty()) { result.accept("Enter a search term."); return; }
        if (replace && replacement == null) { result.accept("Enter replacement text."); return; }
        Thread worker = new Thread(() -> {
            int files = 0, matches = 0;
            int replacedFiles = 0, replacedMatches = 0, skippedFiles = 0;
            try {
                Map<Path, Replacement> changed = new LinkedHashMap<>();
                try (var stream = Files.walk(root)) {
                    for (Path path : (Iterable<Path>) stream::iterator) {
                        if (!Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS) || isIgnored(path)) continue;
                        String text;
                        try { text = readUtf8Text(path); } catch (Exception ignored) { continue; }
                        if (text == null) continue;
                        int count = count(text, query);
                        if (count == 0) continue;
                        files++; matches += count;
                        if (replace) changed.put(path, new Replacement(text, text.replace(query, replacement), count));
                    }
                }
                if (replace) {
                    for (Map.Entry<Path, Replacement> entry : changed.entrySet()) {
                        Path path = entry.getKey();
                        Replacement replacementData = entry.getValue();
                        if (!Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                            skippedFiles++;
                            continue;
                        }
                        String current = readUtf8Text(path);
                        if (current == null || !replacementData.original.equals(current)) {
                            skippedFiles++;
                            continue;
                        }
                        writeAtomically(path, replacementData.updated);
                        replacedFiles++;
                        replacedMatches += replacementData.matches;
                    }
                }
                String message;
                if (replace) {
                    message = "Replaced " + replacedMatches + " matches in " + replacedFiles + " files.";
                    if (skippedFiles > 0) message += " Skipped " + skippedFiles + " files that changed during the operation.";
                } else {
                    message = "Found " + matches + " matches in " + files + " files.";
                }
                String finalMessage = message;
                SwingUtilities.invokeLater(() -> result.accept(finalMessage));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> result.accept("Project search failed: " + VulcanDialog.errorSummary(e)));
            }
        }, "Vulcan-ProjectSearch");
        worker.setDaemon(true);
        worker.start();
    }

    private static int count(String text, String query) {
        int count = 0, at = 0;
        while ((at = text.indexOf(query, at)) >= 0) { count++; at += Math.max(1, query.length()); }
        return count;
    }

    private record Replacement(String original, String updated, int matches) {}

    private static String readUtf8Text(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        for (byte value : bytes) if (value == 0) return null;
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private static void writeAtomically(Path path, String text) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        Path temporary = Files.createTempFile(parent, ".vulcan-search-", ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static boolean isIgnored(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".exe") || name.endsWith(".class") || name.endsWith(".png") ||
                name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif") ||
                name.endsWith(".webp") || name.endsWith(".ico") || name.endsWith(".dll") ||
                name.endsWith(".so") || name.endsWith(".bin") || name.endsWith(".dat") ||
                name.endsWith(".zip") || name.endsWith(".jar") || name.endsWith(".pdf") ||
                name.endsWith(".docx") || name.endsWith(".xlsx") || name.endsWith(".pptx") ||
                name.endsWith(".preset") || path.toString().contains(FileSystems.getDefault().getSeparator() + ".git" + FileSystems.getDefault().getSeparator());
    }
}
