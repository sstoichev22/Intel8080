package Vulcan;

import Intel8080.cpu.Intel8080;
import Intel8080.cpu.CPUStateListener;

import javax.swing.*;
import java.awt.*;

public class RegistersPanel extends JPanel implements CPUStateListener {

    private final Intel8080 cpu;
    private final SettingsManager settings;
    private final JTextArea registers;
    private boolean updatePending;

    /** Creates the register view attached to the debugger CPU. */
    public RegistersPanel(Intel8080 cpu, SettingsManager settings) {
        this.cpu = cpu;
        this.settings = settings;

        setLayout(new BorderLayout());

        registers = new JTextArea();
        registers.setEditable(false);
        registers.setFocusable(false);
        registers.setFont(new Font(Font.MONOSPACED, Font.PLAIN, settings.getFontSize("debuggerRegisters")));
        registers.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JScrollPane scroll = new JScrollPane(registers);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);

        cpu.addStateListener(this);
        updateRegisters();
    }

    /** Applies the current register font size and theme colors. */
    public void refreshTheme() {
        registers.setFont(new Font(Font.MONOSPACED, Font.PLAIN, settings.getFontSize("debuggerRegisters")));
        registers.setBackground(settings.getColor("editorBackground"));
        registers.setForeground(settings.getColor("editorText"));
        repaint();
    }

    private void updateRegisters() {
        registers.setText(
                String.format(
                        "A   0x%02X\n" +
                        "B   0x%02X\n" +
                        "C   0x%02X\n" +
                        "D   0x%02X\n" +
                        "E   0x%02X\n" +
                        "H   0x%02X\n" +
                        "L   0x%02X\n" +
                        "\n" +
                        "SP  0x%04X\n" +
                        "PC  0x%04X",
                        cpu.getA() & 0xFF,
                        cpu.getB() & 0xFF,
                        cpu.getC() & 0xFF,
                        cpu.getD() & 0xFF,
                        cpu.getE() & 0xFF,
                        cpu.getH() & 0xFF,
                        cpu.getL() & 0xFF,
                        cpu.getMemory().getSp() & 0xFFFF,
                        cpu.getPC() & 0xFFFF
                )
        );
    }

    /** Receives CPU state notifications and schedules the matching UI refresh. */
    @Override
    public synchronized void cpuStateChanged() {
        if (updatePending) return;
        updatePending = true;
        SwingUtilities.invokeLater(() -> {
            updateRegisters();
            synchronized (this) {
                updatePending = false;
            }
        });
    }
}
