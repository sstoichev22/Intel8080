package Vulcan;

import javax.swing.*;

public class Vulcan {

    /** The current Vulcan application version. */
    public static final String VERSION = "0.2.0";

    /**
     * Uses the tested Java2D setup unless a VM option explicitly selects another mode.
     * This must run before Swing initializes the graphics pipeline.
     */
    public static void configureRenderingDefaults() {
        if (System.getProperty("sun.java2d.d3d") == null) {
            System.setProperty("sun.java2d.d3d", "false");
        }
    }
    /** Starts the Vulcan desktop application on the Swing event thread. */
    public static void main(String[] args) {
        configureRenderingDefaults();

        SwingUtilities.invokeLater(() -> {
            MainWindow window = new MainWindow();
            window.setVisible(true);
        });
    }
}
