package Vulcan;

import javax.swing.*;
import java.awt.*;

/** Non-focusable dragged-file preview and rounded highlight for the actual drop area. */
final class EditorDropPreview implements AutoCloseable {
    enum Side { CENTER, LEFT, RIGHT, TOP, BOTTOM }
    private final JWindow ghost;
    private final Highlight highlight = new Highlight();
    private JRootPane root;
    private Component previousGlass;
    private boolean previousVisible;
    private final Timer frameTimer = new Timer(16, event -> renderLatestPosition());
    private Point cursor;
    private JTabbedPane target;
    private Side side;

    EditorDropPreview(Window owner, String filename, SettingsManager settings) {
        ghost = new JWindow(owner);
        ghost.setFocusableWindowState(false);
        ghost.setAutoRequestFocus(false);
        frameTimer.setCoalesce(true);
        JLabel label = new JLabel(filename);
        label.setFont(new Font(settings.getApplicationFont(), Font.PLAIN, settings.getFontSize("editorTab")));
        label.setForeground(settings.getColor("tabText"));
        label.setOpaque(true);
        label.setBackground(settings.getColor("tabSelected"));
        label.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        ghost.add(label);
        ghost.pack();
    }

    /** Gives tabs in the top strip a center drop and the outer quarters an edge drop. */
    static Side sideAt(Point point, Dimension size) {
        if (point.y < 40) return Side.CENTER;
        double x = (double) point.x / Math.max(1, size.width);
        double y = (double) point.y / Math.max(1, size.height);
        double edge = Math.min(Math.min(x, 1-x), Math.min(y, 1-y));
        if (edge >= .25) return Side.CENTER;
        if (edge == x) return Side.LEFT;
        if (edge == 1-x) return Side.RIGHT;
        return edge == y ? Side.TOP : Side.BOTTOM;
    }

    static Rectangle area(Dimension size, Side side) {
        Rectangle result = new Rectangle(0, 0, size.width, size.height);
        if (side == Side.LEFT || side == Side.RIGHT) {
            result.width /= 2;
            if (side == Side.RIGHT) result.x = size.width-result.width;
        } else if (side == Side.TOP || side == Side.BOTTOM) {
            result.height /= 2;
            if (side == Side.BOTTOM) result.y = size.height-result.height;
        }
        return result;
    }

    void update(Point cursor, JTabbedPane target, Side side) {
        this.cursor = new Point(cursor);
        this.target = target;
        this.side = side;
        if (!frameTimer.isRunning()) { renderLatestPosition(); frameTimer.start(); }
    }

    /** Moves the native preview at most once per frame and repaints only changed drop highlights. */
    private void renderLatestPosition() {
        if (cursor == null) return;
        ghost.setLocation(cursor.x + 14, cursor.y + 18);
        if (!ghost.isVisible()) ghost.setVisible(true);
        JRootPane next = target == null ? null : SwingUtilities.getRootPane(target);
        if (root != next) {
            restoreGlass();
            root = next;
            if (root != null) {
                previousGlass = root.getGlassPane();
                previousVisible = previousGlass.isVisible();
                root.setGlassPane(highlight);
                highlight.setVisible(true);
            }
        }
        if (root != null) {
            Rectangle rectangle = area(target.getSize(), side);
            Point origin = SwingUtilities.convertPoint(target, rectangle.getLocation(), highlight);
            rectangle.setLocation(origin);
            if (!rectangle.equals(highlight.bounds)) {
                Rectangle dirty = highlight.bounds == null ? rectangle : rectangle.union(highlight.bounds);
                highlight.bounds = rectangle;
                highlight.repaint(dirty.x, dirty.y, dirty.width, dirty.height);
            }
        }
    }

    private void restoreGlass() {
        if (root != null) {
            root.setGlassPane(previousGlass);
            previousGlass.setVisible(previousVisible);
            root = null;
        }
    }

    /** Releases the preview and restores the window's original glass pane. */
    @Override public void close() {
        frameTimer.stop(); cursor = null; target = null;
        restoreGlass(); ghost.dispose();
    }

    private static final class Highlight extends JComponent {
        private Rectangle bounds;
        @Override public boolean contains(int x, int y) { return false; }
        @Override protected void paintComponent(Graphics graphics) {
            if (bounds == null) return;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(205, 209, 215, 95));
                g.fillRoundRect(bounds.x+8, bounds.y+8, Math.max(0,bounds.width-16), Math.max(0,bounds.height-16), 18, 18);
            } finally { g.dispose(); }
        }
    }
}
