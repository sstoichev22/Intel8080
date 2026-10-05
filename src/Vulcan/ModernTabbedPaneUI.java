package Vulcan;

import javax.swing.*;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

public final class ModernTabbedPaneUI extends BasicTabbedPaneUI {
    private final SettingsManager settings;
    private final MouseWheelListener tabWheel = this::scrollTabs;

    @Override protected void installListeners() {
        super.installListeners();
        tabPane.addMouseWheelListener(tabWheel);
    }

    @Override protected void uninstallListeners() {
        tabPane.removeMouseWheelListener(tabWheel);
        super.uninstallListeners();
    }

    /** Scrolls overflowing tab headers without changing the selected editor. */
    private void scrollTabs(MouseWheelEvent event) {
        if (tabPane.getTabLayoutPolicy() != JTabbedPane.SCROLL_TAB_LAYOUT || tabPane.getTabCount() == 0) return;
        Rectangle first = getTabBounds(tabPane, 0);
        if (event.getY() > first.y + first.height) return;
        String name = event.getWheelRotation() > 0 ? "scrollTabsForwardAction" : "scrollTabsBackwardAction";
        Action action = tabPane.getActionMap().get(name);
        if (action == null) return;
        for (int i = 0; i < Math.abs(event.getWheelRotation()); i++)
            action.actionPerformed(new java.awt.event.ActionEvent(tabPane, 0, name));
        event.consume();
    }

    /** Creates a tab-pane delegate that draws tabs using the current Vulcan theme. */
    public ModernTabbedPaneUI(SettingsManager settings) { this.settings = settings; }

    @Override protected void installDefaults() {
        super.installDefaults();
        tabAreaInsets = new Insets(2, 4, 0, 4);
        contentBorderInsets = new Insets(0, 0, 0, 0);
        selectedTabPadInsets = new Insets(0, 0, 0, 0);
        tabInsets = new Insets(7, 13, 7, 13);
        // BasicTabbedPaneUI uses this color for its jagged cropped-tab outline.
        shadow = new Color(0, 0, 0, 0);
    }

    /** Wheel navigation replaces the small arrow controls in the scrolling tab strip. */
    @Override protected JButton createScrollButton(int direction) {
        return new InvisibleScrollButton();
    }

    private static final class InvisibleScrollButton extends JButton implements javax.swing.plaf.UIResource {
        @Override public Dimension getPreferredSize() { return new Dimension(0, 0); }
        @Override public void setVisible(boolean visible) { super.setVisible(false); }
    }

    @Override protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex,
                                                 int x, int y, int w, int h, boolean isSelected) {
        g.setColor(isSelected ? settings.getColor("tabSelected") : settings.getColor("panelBackground"));
        g.fillRoundRect(x, y + 1, w, Math.max(1, h - 2), 8, 8);
    }

    @Override protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex,
                                             int x, int y, int w, int h, boolean isSelected) { }

    @Override protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) { }

    @Override protected void paintText(Graphics g, int tabPlacement, Font font, FontMetrics metrics,
                                       int tabIndex, String title, Rectangle textRect,
                                       boolean isSelected) {
        g.setFont(font);
        g.setColor(isSelected ? settings.getColor("tabText") : settings.getColor("tabTextMuted"));
        g.drawString(title, textRect.x, textRect.y + metrics.getAscent());
    }

    @Override protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects,
                                                   int tabIndex, Rectangle iconRect, Rectangle textRect,
                                                   boolean isSelected) { }
}
