package Vulcan;

import Intel8080.cpu.Intel8080;
import Intel8080.cpu.CPUStateListener;
import Intel8080.cpu.Memory;

import javax.swing.*;
import java.awt.*;

public class MemoryPanel extends JPanel implements CPUStateListener {
    private final Intel8080 cpu;
    private final SettingsManager settings;
    private final JTextArea memoryView;
    private final Timer refreshTimer;
    private int programStart = -1;
    private int programEnd = -1;
    private boolean programLoaded;

    /** Creates the memory view attached to the debugger CPU and settings. */
    public MemoryPanel(Intel8080 cpu, SettingsManager settings) {
        this.cpu = cpu;
        this.settings = settings;
        setLayout(new BorderLayout());
        memoryView = new JTextArea();
        memoryView.setEditable(false);
        memoryView.setFocusable(false);
        memoryView.setLineWrap(false);
        memoryView.setFont(new Font(settings.getEditorFont(), Font.PLAIN, settings.getFontSize("debuggerMemory")));
        memoryView.setBorder(BorderFactory.createEmptyBorder(7, 9, 7, 9));
        JScrollPane scroll = new JScrollPane(memoryView);
        scroll.setBorder(null);
        Theme.styleScrollBar(scroll.getVerticalScrollBar(), settings);
        Theme.styleScrollBar(scroll.getHorizontalScrollBar(), settings);
        add(scroll, BorderLayout.CENTER);
        refreshTimer = new Timer(120, e -> refresh());
        refreshTimer.setRepeats(false);
        cpu.addStateListener(this);
    }

    /** Sets the program range. */
    public void setProgramRange(int start, int end) {
        programStart = Math.max(0, start);
        programEnd = Math.min(Memory.MEM_SIZE - 1, end);
        programLoaded = programStart <= programEnd;
        refresh();
    }

    /** Removes the current program range from the memory view. */
    public void clear() {
        programStart = -1;
        programEnd = -1;
        programLoaded = false;
        memoryView.setText("");
    }

    /** Applies the current theme colors and refreshes this component. */
    public void refreshTheme() {
        memoryView.setBackground(settings.getColor("editorBackground"));
        memoryView.setForeground(settings.getColor("editorText"));
        memoryView.setFont(new Font(settings.getEditorFont(), Font.PLAIN, settings.getFontSize("debuggerMemory")));
        repaint();
    }

    /** Refreshes the memory text and selection state from the CPU memory. */
    public void refresh() {
        if (!programLoaded) {
            memoryView.setText("");
            return;
        }
        StringBuilder output = new StringBuilder();
        for (int address = programStart; address <= programEnd; address += 16) {
            int end = Math.min(programEnd, address + 15);
            output.append(String.format("0x%04X  ", address));
            for (int current = address; current <= end; current++) {
                output.append(String.format("%02X ", cpu.getMemory().get(current) & 0xFF));
            }
            output.append('\n');
        }
        memoryView.setText(output.toString());
    }

    /** Receives CPU state notifications and schedules the matching UI refresh. */
    @Override
    public synchronized void cpuStateChanged() { refreshTimer.restart(); }
}
