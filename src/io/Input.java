package io;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import static cpu.Intel8080.PORT_KEY_DATA;
import static cpu.Intel8080.PORT_KEY_STATUS;

public class Input implements KeyListener {
    private byte[] ioports;
    public Input(byte[] ioports){
        this.ioports = ioports;
    }
    @Override
    public void keyTyped(KeyEvent e) {

    }

    @Override
    public void keyPressed(KeyEvent e) {
        char key = e.getKeyChar();
        ioports[PORT_KEY_DATA] = (byte) key;
        ioports[PORT_KEY_STATUS] = 1;
    }

    @Override
    public void keyReleased(KeyEvent e) {

    }
}
