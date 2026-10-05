package Vulcan;

import Intel8080.assembler.Assembler;
import Intel8080.cpu.Intel8080;
import Intel8080.cpu.CPUStateListener;
import Intel8080.cpu.Memory;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import java.awt.*;

public class DisassemblerPanel extends JPanel implements CPUStateListener {

    private final Intel8080 cpu;
    private final JTextArea disassembly;
    private final SettingsManager settings;

    private int programStart = -1;
    private int programEnd = -1;
    private boolean programLoaded;
    private Object currentHighlight;
    private boolean updatePending;

    /** Creates a disassembly view for the supplied CPU. */
    public DisassemblerPanel(Intel8080 cpu, SettingsManager settings) {
        this.cpu = cpu;
        this.settings = settings;

        setLayout(new BorderLayout());

        disassembly = new JTextArea();
        disassembly.setEditable(false);
        disassembly.setFocusable(false);
        disassembly.setLineWrap(false);
        disassembly.setFont(new Font(Font.MONOSPACED, Font.PLAIN, settings.getFontSize("debuggerInstructions")));

        JScrollPane scroll = new JScrollPane(disassembly);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);
        cpu.addStateListener(this);
    }

    /** Sets the program range. */
    public void setProgramRange(int start, int end) {
        programStart = start;
        programEnd = end;
        programLoaded = start >= 0 && end >= start;
        clearCurrentHighlight();
        refresh();
    }

    /** Clears the disassembly view and resets its displayed address range. */
    public void clear() {
        programStart = -1;
        programEnd = -1;
        programLoaded = false;
        clearCurrentHighlight();
        disassembly.setText("");
    }

    /** Applies the current debugger instruction font and theme colors. */
    public void refreshTheme() {
        disassembly.setFont(new Font(Font.MONOSPACED, Font.PLAIN, settings.getFontSize("debuggerInstructions")));
        disassembly.setBackground(settings.getColor("editorBackground"));
        disassembly.setForeground(settings.getColor("editorText"));
        repaint();
    }

    /** Refreshes the disassembly text and instruction highlight from the CPU state. */
    public void refresh() {
        if (!programLoaded) {
            disassembly.setText("");
            clearCurrentHighlight();
            return;
        }

        byte[] snapshot = new byte[Memory.MEM_SIZE];
        for (int i = 0; i < snapshot.length; i++) snapshot[i] = cpu.getMemory().get(i);

        String[] instructions = Assembler.Disassemble(snapshot, programStart, programEnd);
        StringBuilder output = new StringBuilder();
        for (String instruction : instructions) output.append(instruction).append('\n');

        disassembly.setText(output.toString());
        clearCurrentHighlight();
        highlightCurrentInstruction();
    }

    private void highlightCurrentInstruction() {
        clearCurrentHighlight();
        if (!programLoaded) return;

        int pc = cpu.getPC() & 0xFFFF;
        String target = String.format("0x%04X:", pc);
        int index = disassembly.getText().indexOf(target);
        if (index < 0) return;

        try {
            int line = disassembly.getLineOfOffset(index);
            int start = disassembly.getLineStartOffset(line);
            int end = Math.max(start, disassembly.getLineEndOffset(line));

            Color base = settings.getColor("currentInstruction");
            Color color = new Color(base.getRed(), base.getGreen(), base.getBlue(), 210);

            currentHighlight = disassembly.getHighlighter().addHighlight(
                    start,
                    end,
                    new DefaultHighlighter.DefaultHighlightPainter(color)
            );
            // Display can load a program while this debugger view has no layout yet.
            java.awt.geom.Rectangle2D bounds = disassembly.modelToView2D(start);
            if (bounds != null) disassembly.scrollRectToVisible(bounds.getBounds());
        } catch (BadLocationException ignored) {
        }
    }

    private void clearCurrentHighlight() {
        if (currentHighlight != null) {
            disassembly.getHighlighter().removeHighlight(currentHighlight);
            currentHighlight = null;
        }
    }

    /** Receives CPU state notifications and schedules the matching UI refresh. */
    @Override
    public synchronized void cpuStateChanged() {
        if (updatePending) return;
        updatePending = true;
        SwingUtilities.invokeLater(() -> {
            try {
                highlightCurrentInstruction();
            } finally {
                synchronized (this) {
                    updatePending = false;
                }
            }
        });
    }
}
