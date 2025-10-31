package io;

import cpu.Memory;

import javax.swing.*;
import java.awt.*;

public class Display extends JPanel {
    public static final float BYTES_PER_PIXEL = 0.25f;

    private final Memory memory;
    private final int screenWidth, screenHeight, pixelSize;

    public Display(Memory memory, int screenWidth, int screenHeight, int pixelSize){
        this.memory = memory;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.pixelSize = pixelSize;

        JFrame frame = new JFrame("Intel8080");
        setSize(screenWidth*pixelSize, screenHeight*pixelSize);
        frame.add(this);
        frame.pack();
        this.requestFocus();

        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setVisible(true);

    }

    public void paintComponent(Graphics g){
        for(int i = 0; i < screenWidth; i++){
            for(int j = 0 ; j < screenHeight; j++){
                int localAddress = j * screenWidth + i;
                int byteIdx = localAddress /4;
                int pixelInByte = localAddress % 4;
                int bitOffset = (3-pixelInByte) * 2;
                byte value = (byte) ((memory.get(byteIdx+Memory.VIDEO_MEMORY_START) >> bitOffset) & 0x3);


                Color color = switch(value){
                    case 0->Color.black;
                    case 1->Color.green;
                    case 2->Color.red;
                    case 3->Color.white;
                    default -> throw new RuntimeException("Unexpected Value : ["+value+"]");                };
                g.setColor(color);
                g.fillRect(i*pixelSize, j*pixelSize, pixelSize, pixelSize);
            }
        }
    }
    public void setSize(int width, int height){
        Dimension d = new Dimension(width, height);
        this.setMinimumSize(d);
        this.setPreferredSize(d);
        this.setMaximumSize(d);
    }
    public void refresh(){
        this.repaint();
    }
}
