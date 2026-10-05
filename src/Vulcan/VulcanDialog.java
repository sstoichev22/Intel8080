package Vulcan;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Provides consistently styled, concise dialogs for the Vulcan interface. */
public final class VulcanDialog extends JDialog {
    private static final int MAX_ERROR_LENGTH = 240;

    private final SettingsManager settings;

    private VulcanDialog(Window owner, String title, SettingsManager settings) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.settings = settings;
        setUndecorated(true);
        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    /** Shows a styled text prompt and returns null if it is canceled. */
    public static String prompt(Component owner, SettingsManager settings, String title,
                                String message, String initialValue) {
        Window window = SwingUtilities.getWindowAncestor(owner);
        VulcanDialog dialog = new VulcanDialog(window, title, settings);
        JPanel root = shell(dialog, title);
        JPanel content = body(settings);
        JLabel prompt = label(settings, message);
        JTextField input = new JTextField(initialValue == null ? "" : initialValue);
        styleInput(settings, input);

        JPanel buttons = buttons();
        JButton cancel = new JButton("Cancel");
        JButton accept = new JButton("OK");
        buttons.add(cancel);
        buttons.add(accept);
        content.add(prompt, BorderLayout.NORTH);
        content.add(input, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        root.add(content, BorderLayout.CENTER);

        String[] result = {null};
        cancel.addActionListener(event -> dialog.dispose());
        accept.addActionListener(event -> {
            result[0] = input.getText();
            dialog.dispose();
        });
        input.addActionListener(event -> accept.doClick());
        dialog.getRootPane().setDefaultButton(accept);
        dialog.setSize(390, 150);
        dialog.setLocationRelativeTo(window);
        SwingUtilities.invokeLater(() -> {
            input.requestFocusInWindow();
            input.selectAll();
        });
        dialog.setVisible(true);
        return result[0];
    }

    /** Shows a styled informational message. */
    public static void message(Component owner, SettingsManager settings,
                               String title, String message) {
        Window window = SwingUtilities.getWindowAncestor(owner);
        VulcanDialog dialog = new VulcanDialog(window, title, settings);
        JPanel root = shell(dialog, title);
        JPanel content = body(settings);
        content.add(label(settings, htmlMessage(message)), BorderLayout.CENTER);
        JPanel buttons = buttons();
        JButton close = new JButton("Close");
        buttons.add(close);
        content.add(buttons, BorderLayout.SOUTH);
        root.add(content, BorderLayout.CENTER);
        close.addActionListener(event -> dialog.dispose());
        dialog.setSize(460, 190);
        dialog.setLocationRelativeTo(window);
        dialog.setVisible(true);
    }

    /** Shows a styled confirmation dialog and returns the user choice. */
    public static boolean confirm(Component owner, SettingsManager settings,
                                 String title, String message) {
        Window window = SwingUtilities.getWindowAncestor(owner);
        VulcanDialog dialog = new VulcanDialog(window, title, settings);
        JPanel root = shell(dialog, title);
        JPanel content = body(settings);
        content.add(label(settings, htmlMessage(message)), BorderLayout.CENTER);
        JPanel buttons = buttons();
        JButton cancel = new JButton("Cancel");
        JButton accept = new JButton("OK");
        buttons.add(cancel);
        buttons.add(accept);
        content.add(buttons, BorderLayout.SOUTH);
        root.add(content, BorderLayout.CENTER);

        boolean[] result = {false};
        cancel.addActionListener(event -> dialog.dispose());
        accept.addActionListener(event -> {
            result[0] = true;
            dialog.dispose();
        });
        dialog.setSize(460, 190);
        dialog.setLocationRelativeTo(window);
        dialog.setVisible(true);
        return result[0];
    }

    /** Returns a short, single-line failure description without a stack trace. */
    public static String errorSummary(Throwable failure) {
        if (failure == null) return "Unexpected error.";

        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        String summary = null;
        for (int depth = 0; current != null && depth < 8 && seen.add(current); depth++) {
            String message = current.getMessage();
            if (message != null && !message.isBlank()) summary = message;
            current = current.getCause();
        }

        if (summary == null) summary = failure.getClass().getSimpleName();
        summary = summary.replaceAll("[\\r\\n\\t\\p{Cntrl}]+", " ").trim();
        if (summary.isEmpty()) summary = "Unexpected error.";
        if (summary.length() > MAX_ERROR_LENGTH) {
            summary = summary.substring(0, MAX_ERROR_LENGTH - 1).trim() + "…";
        }
        return summary;
    }

    private static JPanel shell(VulcanDialog dialog, String title) {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(dialog.settings.getColor("panelBackground"));

        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(dialog.settings.getColor("panelBackground"));
        titleBar.setBorder(BorderFactory.createMatteBorder(
                0, 0, 1, 0, dialog.settings.getColor("splitDivider")));
        JLabel heading = label(dialog.settings, title);
        heading.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 4));
        titleBar.add(heading, BorderLayout.CENTER);

        JButton close = new JButton("×");
        close.setForeground(dialog.settings.getColor("text"));
        close.setPreferredSize(new Dimension(34, 28));
        close.setBorder(null);
        close.setContentAreaFilled(false);
        close.addActionListener(event -> dialog.dispose());
        titleBar.add(close, BorderLayout.EAST);
        root.add(titleBar, BorderLayout.NORTH);
        dialog.setContentPane(root);
        return root;
    }

    private static JPanel body(SettingsManager settings) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.setBackground(settings.getColor("panelBackground"));
        return panel;
    }

    private static JPanel buttons() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        panel.setOpaque(false);
        return panel;
    }

    private static JLabel label(SettingsManager settings, String text) {
        JLabel label = new JLabel(text == null ? "" : text);
        label.setForeground(settings.getColor("text"));
        return label;
    }

    private static void styleInput(SettingsManager settings, JTextField input) {
        input.setBackground(settings.getColor("inputBackground"));
        input.setForeground(settings.getColor("text"));
        input.setCaretColor(settings.getColor("text"));
    }

    private static String htmlMessage(String message) {
        String escaped = message == null ? "" : message.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;");
        return "<html>" + escaped.replace("\r\n", "\n").replace("\r", "\n")
                .replace("\n", "<br>") + "</html>";
    }
}
