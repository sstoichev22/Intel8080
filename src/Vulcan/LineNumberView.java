package Vulcan;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * Line-number gutter that uses the editor's real text view geometry.
 * The gutter width is intentionally fixed so adding digits to a line number
 * can never move the editor text horizontally.
 */
public class LineNumberView extends JComponent {
    private static final int GUTTER_WIDTH = 60;
    private static final int LINE_NUMBER_EXTRA_DOWN_PX = 3;
    private final JTextComponent editor;
    private final SettingsManager settings;

    /** Creates a line-number gutter attached to the supplied text component. */
    public LineNumberView(JTextComponent editor, SettingsManager settings) {
        this.editor = editor;
        this.settings = settings;

        setOpaque(true);
        setPreferredSize(new Dimension(GUTTER_WIDTH, 1));

        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refreshGeometry(); }
            @Override public void removeUpdate(DocumentEvent e) { refreshGeometry(); }
            @Override public void changedUpdate(DocumentEvent e) { refreshGeometry(); }
        });

        editor.addPropertyChangeListener("font", e -> refreshGeometry());
        editor.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { refreshGeometry(); }
        });


    }

    private void refreshGeometry() {
        repaint();
    }

    private int lineAtY(int y) {
        try {
            Point2D point = new Point2D.Double(Math.max(0, editor.getInsets().left + 1), Math.max(0, y));
            int model = editor.viewToModel2D(point);
            if (model < 0) return -1;
            Element root = editor.getDocument().getDefaultRootElement();
            return root.getElementIndex(model) + 1;
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(settings.getColor("editorBackground"));
            g.fillRect(0, 0, getWidth(), getHeight());

            FontMetrics metrics = editor.getFontMetrics(editor.getFont());
            Element root = editor.getDocument().getDefaultRootElement();
            if (root == null || root.getElementCount() == 0) {
                drawDivider(g);
                return;
            }

            Rectangle clip = g.getClipBounds();
            int firstLine = lineFromY(clip.y);
            int lastLine = lineFromY(clip.y + clip.height + metrics.getHeight());

            firstLine = Math.max(0, firstLine - 1);
            lastLine = Math.min(root.getElementCount() - 1, lastLine + 1);

            g.setFont(editor.getFont());

            for (int lineIndex = firstLine; lineIndex <= lastLine; lineIndex++) {
                Element line = root.getElement(lineIndex);
                int start = Math.min(line.getStartOffset(), editor.getDocument().getLength());
                Rectangle2D row;
                try {
                    row = editor.modelToView2D(start);
                } catch (BadLocationException | IllegalArgumentException e) {
                    continue;
                }
                if (row == null) continue;

                double y = row.getY();
                double height = Math.max(1.0, row.getHeight());
                if (y + height < clip.y - metrics.getHeight() || y > clip.y + clip.height + metrics.getHeight()) continue;

                int baseline = (int) Math.round(y + metrics.getAscent()) + 1 + LINE_NUMBER_EXTRA_DOWN_PX;

                int lineNumber = lineIndex + 1;
                g.setColor(settings.getColor("lineNumber"));
                String text = Integer.toString(lineNumber);
                int textWidth = metrics.stringWidth(text);
                g.drawString(text, GUTTER_WIDTH - textWidth - 8, baseline);
            }

            drawDivider(g);
        } finally {
            g.dispose();
        }
    }

    private int lineFromY(int y) {
        try {
            Point2D point = new Point2D.Double(Math.max(0, editor.getInsets().left + 1), Math.max(0, y));
            int model = editor.viewToModel2D(point);
            if (model < 0) return 0;
            return editor.getDocument().getDefaultRootElement().getElementIndex(model);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private void drawDivider(Graphics2D g) {
        g.setColor(settings.getColor("splitDivider"));
        g.fillRect(GUTTER_WIDTH - 1, 0, 1, getHeight());
    }

    /** Returns the preferred size required by this component. */
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(GUTTER_WIDTH, Math.max(1, editor.getPreferredSize().height));
    }

    /** Returns the minimum size required by this component. */
    @Override
    public Dimension getMinimumSize() {
        return new Dimension(GUTTER_WIDTH, 1);
    }

    /** Applies the current theme colors and refreshes this component. */
    public void refreshTheme() {
        refreshGeometry();
    }
}
