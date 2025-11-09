package util;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Console {
    private static final int MAX_LINES = 100;
    private List<JTextArea> streams = new ArrayList<>();
    private final List<ConcurrentLinkedQueue<String>> buffers = new ArrayList<>();

    public Console(int numStreams, int screenWidth, int screenHeight, String...titles){
        JFrame frame = new JFrame("Console");

        Dimension d = new Dimension(screenWidth, screenHeight);
        frame.setMinimumSize(d);
        frame.setPreferredSize(d);
        frame.setMaximumSize(d);

        frame.setLayout(new GridLayout(1, numStreams));

        for(int i = 0; i < numStreams; i++){
            JPanel panel = new JPanel(new BorderLayout());
            JLabel title = new JLabel(i+") "+titles[i], SwingConstants.CENTER);

            JTextArea area = new JTextArea();
            area.setEditable(true);
            area.setLineWrap(true);
            area.setForeground(Color.green);
            area.setBackground(Color.black);

            JScrollPane scrollPane = new JScrollPane(area);
            panel.add(title, BorderLayout.NORTH);
            panel.add(scrollPane, BorderLayout.CENTER);

            streams.add(area);
            buffers.add(new ConcurrentLinkedQueue<>());
            frame.add(panel);
        }
        frame.setLocation(0,0);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setVisible(true);

    }
    public void print(int stream, String message) {
        buffers.get(stream).add(message);
    }
    public void println(int stream, String message) {
        buffers.get(stream).add(message + "\n");
    }
    public void printf(int stream, String message, Object... items) {
        buffers.get(stream).add(message.formatted(items));
    }
    private void trimLines(JTextArea area) {
        int lines = area.getLineCount();
        if (lines > MAX_LINES) {
            try {
                int start = area.getLineStartOffset(lines - MAX_LINES - 1);
                area.getDocument().remove(0, start);
            } catch (Exception ignored) {}
        }
    }
    public void flush() {
        for(int i = 0 ; i < streams.size(); i++) {
            if (buffers.get(i).isEmpty()) return;

            StringBuilder sb = new StringBuilder();
            while (!buffers.get(i).isEmpty()) sb.append(buffers.get(i).poll());

            streams.get(i).append(sb.toString());
            trimLines(streams.get(i));
            streams.get(i).setCaretPosition(streams.get(i).getDocument().getLength());
        }
    }
    public List<JTextArea> getStreams(){
        return streams;
    }
    public List<ConcurrentLinkedQueue<String>> getBuffers(){
        return buffers;
    }
}
