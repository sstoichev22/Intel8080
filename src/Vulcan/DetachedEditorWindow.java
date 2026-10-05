package Vulcan;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/** A lightweight editor-only window that keeps detached tabs inside the current editor workspace. */
final class DetachedEditorWindow extends JFrame {
    private final EditorTab owner;
    private final SettingsManager settings;
    private final JTabbedPane editorTabs = new JTabbedPane();
    private final JLabel fileTitle = new JLabel("Vulcan");
    private Point dragOffset;

    DetachedEditorWindow(EditorTab owner, SettingsManager settings) {
        super("Vulcan");
        this.owner = owner;
        this.settings = settings;
        setUndecorated(true);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setIconImage(Icons.load("vulcan-icon.png"));
        setLayout(new BorderLayout());
        getContentPane().setBackground(settings.getColor("applicationBackground"));

        JPanel titleBar = createTitleBar();
        editorTabs.setUI(new ModernTabbedPaneUI(settings));
        editorTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        owner.installTabContextMenu(editorTabs);
        editorTabs.addChangeListener(e -> { refreshWindowTitle(); owner.onTabChanged(editorTabs); });
        add(titleBar, BorderLayout.NORTH);
        add(editorTabs, BorderLayout.CENTER);
        setSize(960, 680);
        setLocationRelativeTo(owner);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { owner.closeDetachedWindow(DetachedEditorWindow.this); }
        });
        Theme.apply(this, settings);
    }

    private JPanel createTitleBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, settings.getColor("splitDivider")));
        bar.setPreferredSize(new Dimension(0, 32));

        JPanel titleArea = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 5));
        titleArea.setOpaque(false);
        titleArea.add(new JLabel(Icons.fit("vulcan-icon.png", 16, 16)));
        fileTitle.setForeground(settings.getColor("text"));
        fileTitle.setFont(new Font(settings.getApplicationFont(), Font.BOLD, settings.getFontSize("applicationTitle")));
        titleArea.add(fileTitle);
        bar.add(titleArea, BorderLayout.WEST);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 2));
        controls.setOpaque(false);
        JButton minimize = titleButton("mz-icon.png", "Minimize");
        JButton maximize = titleButton("fs-icon.png", "Maximize");
        JButton close = titleButton("x-icon.png", "Close");
        minimize.addActionListener(e -> setState(Frame.ICONIFIED));
        maximize.addActionListener(e -> setExtendedState(
                (getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH ? Frame.NORMAL : Frame.MAXIMIZED_BOTH));
        close.addActionListener(e -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)));
        controls.add(minimize);
        controls.add(maximize);
        controls.add(close);
        bar.add(controls, BorderLayout.EAST);

        MouseAdapter drag = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) {
                    dragOffset = SwingUtilities.convertPoint((Component) event.getSource(), event.getPoint(), DetachedEditorWindow.this);
                }
            }
            @Override public void mouseDragged(MouseEvent event) {
                if (dragOffset != null && (getExtendedState() & Frame.MAXIMIZED_BOTH) != Frame.MAXIMIZED_BOTH) {
                    Point screen = event.getLocationOnScreen();
                    setLocation(screen.x - dragOffset.x, screen.y - dragOffset.y);
                }
            }
            @Override public void mouseReleased(MouseEvent event) { dragOffset = null; }
        };
        bar.addMouseListener(drag);
        bar.addMouseMotionListener(drag);
        titleArea.addMouseListener(drag);
        titleArea.addMouseMotionListener(drag);
        fileTitle.addMouseListener(drag);
        fileTitle.addMouseMotionListener(drag);
        return bar;
    }

    private JButton titleButton(String iconName, String tooltip) {
        return Icons.windowControl(iconName, tooltip);
    }

    JTabbedPane getEditorTabs() { return editorTabs; }

    void showNear(Point point) {
        GraphicsConfiguration configuration = null;
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            GraphicsConfiguration candidate = device.getDefaultConfiguration();
            if (candidate.getBounds().contains(point)) { configuration = candidate; break; }
        }
        if (configuration == null) configuration = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
        Rectangle usable = new Rectangle(configuration.getBounds());
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        usable.x += insets.left;
        usable.y += insets.top;
        usable.width -= insets.left + insets.right;
        usable.height -= insets.top + insets.bottom;
        setSize(Math.min(getWidth(), Math.max(1, usable.width)),
                Math.min(getHeight(), Math.max(1, usable.height)));
        int x = Math.max(usable.x, Math.min(point.x - getWidth() / 2, usable.x + usable.width - getWidth()));
        int y = Math.max(usable.y, Math.min(point.y - 24, usable.y + usable.height - getHeight()));
        setLocation(x, y);
        setVisible(true);
        toFront();
    }

    void refreshWindowTitle() {
        int index = editorTabs.getSelectedIndex();
        String title = index < 0 ? "Vulcan" : editorTabs.getTitleAt(index);
        fileTitle.setText(title);
        setTitle(title);
    }

    void refreshTheme() {
        fileTitle.setForeground(settings.getColor("text"));
        fileTitle.setFont(new Font(settings.getApplicationFont(), Font.BOLD, settings.getFontSize("applicationTitle")));
        editorTabs.setUI(new ModernTabbedPaneUI(settings));
        Theme.apply(this, settings);
        repaint();
    }
}

