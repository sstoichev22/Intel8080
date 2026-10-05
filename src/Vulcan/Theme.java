package Vulcan;

import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.text.JTextComponent;
import java.awt.*;

public final class Theme {
    private Theme() {}

    /** Configures Swing UI defaults to use the current Vulcan theme. */
    public static void configureUI(SettingsManager settings) {
        Font appFont = new Font(settings.getApplicationFont(), Font.PLAIN, settings.getFontSize("application"));
        String[] fontKeys = {
                "defaultFont", "Label.font", "Button.font", "Menu.font", "MenuItem.font",
                "CheckBoxMenuItem.font", "TabbedPane.font", "Tree.font", "TextField.font",
                "TextArea.font", "TextPane.font", "ComboBox.font", "OptionPane.font", "List.font"
        };
        for (String key : fontKeys) UIManager.put(key, appFont);

        Color panel = settings.getColor("panelBackground");
        Color input = settings.getColor("inputBackground");
        Color foreground = settings.getColor("text");
        Color selected = settings.getColor("tabSelected");
        Color fileSelected = settings.getColor("fileSelectionBackground");
        Color fileSelectedText = settings.getColor("fileSelectionText");
        Color divider = settings.getColor("splitDivider");

        UIManager.put("Panel.background", panel);
        UIManager.put("Viewport.background", input);
        UIManager.put("ScrollPane.background", panel);
        UIManager.put("TextArea.background", input);
        UIManager.put("TextArea.foreground", foreground);
        UIManager.put("TextField.background", input);
        UIManager.put("TextField.foreground", foreground);
        UIManager.put("TextPane.background", input);
        UIManager.put("TextPane.foreground", foreground);
        UIManager.put("Tree.background", input);
        UIManager.put("Tree.textForeground", foreground);
        UIManager.put("Tree.selectionBackground", fileSelected);
        UIManager.put("Tree.selectionForeground", fileSelectedText);
        UIManager.put("TabbedPane.background", panel);
        UIManager.put("TabbedPane.foreground", foreground);
        UIManager.put("TabbedPane.selected", selected);
        UIManager.put("TabbedPane.unselectedBackground", panel);
        UIManager.put("TabbedPane.contentAreaColor", panel);
        UIManager.put("TabbedPane.focus", new Color(0, 0, 0, 0));
        UIManager.put("SplitPane.background", panel);
        UIManager.put("SplitPane.dividerSize", 4);
        UIManager.put("Separator.background", divider);
        UIManager.put("MenuBar.background", panel);
        UIManager.put("Menu.background", panel);
        UIManager.put("Menu.foreground", foreground);
        UIManager.put("MenuItem.background", panel);
        UIManager.put("MenuItem.foreground", foreground);
        UIManager.put("CheckBoxMenuItem.background", panel);
        UIManager.put("CheckBoxMenuItem.foreground", foreground);
        UIManager.put("ToolBar.background", panel);
        UIManager.put("ToolBar.foreground", foreground);
        UIManager.put("OptionPane.background", panel);
        UIManager.put("OptionPane.messageForeground", foreground);
        UIManager.put("Button.background", panel);
        UIManager.put("Button.foreground", foreground);
        UIManager.put("ComboBox.background", input);
        UIManager.put("ComboBox.foreground", foreground);
    }

    /** Applies theme colors to the supplied component tree. */
    public static void apply(Component root, SettingsManager settings) {
        applyComponent(root, settings.getColor("applicationBackground"),
                settings.getColor("panelBackground"), settings.getColor("inputBackground"),
                settings.getColor("text"), settings);
    }

    private static void applyComponent(Component component, Color background, Color panel,
                                       Color input, Color foreground, SettingsManager settings) {
        if (component instanceof JToolBar || component instanceof JMenuBar) {
            component.setBackground(panel);
            component.setForeground(foreground);
            if (component instanceof JMenuBar menuBar) menuBar.setBorder(null);
        } else if (component instanceof JTextComponent text) {
            text.setBackground(settings.getColor("editorBackground"));
            text.setForeground(settings.getColor("editorText"));
            text.setCaretColor(settings.getColor("editorText"));
        } else {
            component.setBackground(background);
            component.setForeground(foreground);
        }

        if (component instanceof JButton button) {
            button.setBackground(panel);
            button.setForeground(foreground);
            button.setFocusPainted(false);
            button.setBorder(null);
            button.setContentAreaFilled(false);
            button.setOpaque(false);
        }
        if (component instanceof JTree tree) {
            tree.setBackground(input);
            tree.setForeground(foreground);
        }
        if (component instanceof JTabbedPane tabbedPane) {
            tabbedPane.setBackground(panel);
            tabbedPane.setForeground(foreground);
            tabbedPane.setBorder(null);
            tabbedPane.setUI(new ModernTabbedPaneUI(settings));
        }
        if (component instanceof JScrollPane scrollPane) {
            scrollPane.getViewport().setBackground(input);
            scrollPane.setBackground(input);
            scrollPane.setBorder(null);
            styleScrollBar(scrollPane.getVerticalScrollBar(), settings);
            styleScrollBar(scrollPane.getHorizontalScrollBar(), settings);
        }
        if (component instanceof JSplitPane splitPane) {
            splitPane.setMinimumSize(new Dimension(0, 0));
            if (splitPane.getLeftComponent() != null) splitPane.getLeftComponent().setMinimumSize(new Dimension(0, 0));
            if (splitPane.getRightComponent() != null) splitPane.getRightComponent().setMinimumSize(new Dimension(0, 0));
            splitPane.setContinuousLayout(true);
            splitPane.setBackground(panel);
            splitPane.setBorder(null);
            splitPane.setDividerSize(Boolean.TRUE.equals(splitPane.getClientProperty("vulcan.hiddenDivider")) ? 0 : 4);
            splitPane.setOneTouchExpandable(false);
            splitPane.setUI(new SharpSplitPaneUI(settings.getColor("splitDivider")));
            // Installing a Swing UI delegate can reinstall its default border.
            splitPane.setBorder(null);
        }
        if (component instanceof JPanel panelComponent) panelComponent.setBackground(panel);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                applyComponent(child, background, panel, input, foreground, settings);
            }
        }
    }

    /** Styles a scroll bar with colors from the active Vulcan theme. */
    public static void styleScrollBar(JScrollBar bar, SettingsManager settings) {
        bar.setUnitIncrement(24);
        bar.setBlockIncrement(120);
        bar.setOpaque(false);
        bar.setBorder(null);
        bar.setUI(new FlatScrollBarUI(settings));
    }

    /** Chooses a first divider position from the actual window size, then leaves resizing to the user. */
    public static void initializeDivider(JSplitPane split, double proportion) {
        java.awt.event.ComponentAdapter initialize = new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent event) {
                int size = split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT ? split.getWidth() : split.getHeight();
                if (size <= 0 || split.getLeftComponent() == null || split.getRightComponent() == null) return;
                split.setDividerLocation(proportion);
                split.removeComponentListener(this);
            }
        };
        split.addComponentListener(initialize);
    }

    private static final class SharpSplitPaneUI extends BasicSplitPaneUI {
        private final Color dividerColor;
        private int pendingLocation;
        private final Timer dragTimer = new Timer(16, event -> applyPendingDrag());
        private SharpSplitPaneUI(Color dividerColor) { this.dividerColor = dividerColor; }

        /** Coalesces mouse movement into one live layout per display frame. */
        @Override protected void dragDividerTo(int location) {
            pendingLocation = location;
            if (!dragTimer.isRunning()) dragTimer.start();
        }

        private void applyPendingDrag() { super.dragDividerTo(pendingLocation); }

        /** Always applies the release position, including drags shorter than a frame. */
        @Override protected void finishDraggingTo(int location) {
            dragTimer.stop();
            super.dragDividerTo(location);
            super.finishDraggingTo(location);
        }

        @Override public void uninstallUI(JComponent component) {
            dragTimer.stop();
            super.uninstallUI(component);
        }

        @Override
        public BasicSplitPaneDivider createDefaultDivider() {
            return new BasicSplitPaneDivider(this) {
                { setBorder(null); }
                @Override public void paint(Graphics g) {
                    g.setColor(dividerColor);
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
            };
        }
    }

    private static final class FlatScrollBarUI extends BasicScrollBarUI {
        private final SettingsManager settings;
        private FlatScrollBarUI(SettingsManager settings) { this.settings = settings; }

        @Override protected void configureScrollBarColors() {
            thumbColor = settings.getColor("splitDivider");
            trackColor = settings.getColor("panelBackground");
        }

        @Override protected JButton createDecreaseButton(int orientation) { return zeroButton(); }
        @Override protected JButton createIncreaseButton(int orientation) { return zeroButton(); }

        private JButton zeroButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            button.setBorder(null);
            button.setOpaque(false);
            return button;
        }

        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            g.setColor(settings.getColor("panelBackground"));
            g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
        }

        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            if (!c.isEnabled() || thumbBounds.width <= 0 || thumbBounds.height <= 0) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(settings.getColor("splitDivider"));
            int pad = scrollbar.getOrientation() == Adjustable.VERTICAL ? 3 : 2;
            Rectangle r = new Rectangle(thumbBounds.x + (scrollbar.getOrientation() == Adjustable.VERTICAL ? pad : 1),
                    thumbBounds.y + (scrollbar.getOrientation() == Adjustable.VERTICAL ? 1 : pad),
                    Math.max(4, thumbBounds.width - (scrollbar.getOrientation() == Adjustable.VERTICAL ? pad * 2 : 2)),
                    Math.max(4, thumbBounds.height - (scrollbar.getOrientation() == Adjustable.VERTICAL ? 2 : pad * 2)));
            g2.fillRoundRect(r.x, r.y, r.width, r.height, 8, 8);
            g2.dispose();
        }
    }

    /** Returns the theme color used for section labels. */
    public static Color section(SettingsManager settings) { return settings.getColor("section"); }
    /** Returns the theme color used for call and jump instructions. */
    public static Color callJump(SettingsManager settings) { return settings.getColor("callJump"); }
    /** Returns the theme color used for stack operations. */
    public static Color stack(SettingsManager settings) { return settings.getColor("stack"); }
    /** Returns the theme color used for immediate values. */
    public static Color immediate(SettingsManager settings) { return settings.getColor("immediate"); }
    /** Returns the theme color used for string values. */
    public static Color string(SettingsManager settings) { return settings.getColor("string"); }
    /** Returns the theme color used for register names. */
    public static Color register(SettingsManager settings) { return settings.getColor("register"); }
    /** Returns the theme color used for assembler directives. */
    public static Color directive(SettingsManager settings) { return settings.getColor("directive"); }
    /** Returns the theme color used for instructions. */
    public static Color instruction(SettingsManager settings) { return settings.getColor("instruction"); }
    /** Returns the theme color used for comments. */
    public static Color comment(SettingsManager settings) { return settings.getColor("comment"); }
    /** Returns the theme color used for editor text. */
    public static Color text(SettingsManager settings) { return settings.getColor("editorText"); }
}
