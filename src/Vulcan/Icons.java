package Vulcan;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import java.awt.*;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * Icon resource inventory (paths are relative to {@code Vulcan/res}):
 *
 * <p>Packaged file icons: asm, c, h, txt, exe, json, md, java, png, zip, dll,
 * bin, default, dir and dir-op, each named {@code name-icon.png}.</p>
 * <p>Packaged controls: ctc-icon.png (copy), fs-icon.png (fullscreen),
 * wd-icon.png (windowed), step-icon.png, reset-icon.png, debug-icon.png,
 * mz-icon.png (minimize), x-icon.png (close), and vulcan-icon.png (application).
 * ASM is exported from VS Code Seti; file and control icons use installed Windows assets.
 * Seti attribution is stored in res/Seti-ThirdPartyNotices.txt.</p>
 * <p>Run, running, rerun, pause, stop, close, and back controls are drawn as vector icons
 * and do not require PNG resources.</p>
 */
public final class Icons {
    private static final java.util.Map<String, BufferedImage> IMAGES = new java.util.concurrent.ConcurrentHashMap<>();
    public static final String DEBUG_ICON = "debug-icon.png";
    public static final String STEP_ICON = "step-icon.png";
    public static final String RESET_ICON = "reset-icon.png";

    private Icons() {}

    /** Creates a window control with Windows-style hover feedback. */
    public static javax.swing.JButton windowControl(String resource, String tooltip) {
        boolean close = "x-icon.png".equals(resource);
        Icon icon = close ? vector("close", 14, new Color(202, 205, 210)) : fit(resource, 14, 14);
        javax.swing.JButton button = new javax.swing.JButton(icon) {
            @Override protected void paintComponent(Graphics graphics) {
                if (getModel().isRollover() || getModel().isPressed()) {
                    graphics.setColor(close ? new Color(196, 43, 52) : new Color(170, 175, 182));
                    graphics.fillRect(0, 0, getWidth(), getHeight());
                }
                super.paintComponent(graphics);
            }
        };
        button.setToolTipText(tooltip);
        button.setPreferredSize(new Dimension(29, 27));
        button.setBorder(null);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        return button;
    }

    /** Creates a light-gray tab close mark, lowered one pixel, with a compact circular hover target. */
    public static javax.swing.JButton tabClose() {
        Icon cross = vector("close", 11, new Color(202, 205, 210));
        Icon lowered = new Icon() {
            public int getIconWidth() { return cross.getIconWidth(); }
            public int getIconHeight() { return cross.getIconHeight(); }
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                cross.paintIcon(component, graphics, x, y + 1);
            }
        };
        javax.swing.JButton button = new javax.swing.JButton(lowered) {
            @Override protected void paintComponent(Graphics graphics) {
                if (getModel().isRollover() || getModel().isPressed()) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(new Color(93, 99, 108));
                    g.fillOval((getWidth() - 16) / 2, (getHeight() - 16) / 2, 16, 16);
                    g.dispose();
                }
                super.paintComponent(graphics);
            }
        };
        button.setPreferredSize(new Dimension(18, 18));
        button.setToolTipText("Close tab");
        button.setBorder(null);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        return button;
    }

    /** Loads an icon image from the Vulcan resources, returning a drawn fallback if unavailable. */
    public static BufferedImage load(String path) {
        return IMAGES.computeIfAbsent(path, Icons::readImage);
    }

    private static BufferedImage readImage(String path) {
        try (InputStream input = Icons.class.getResourceAsStream("res/" + path)) {
            if (input != null) {
                BufferedImage image = ImageIO.read(input);
                if (image != null) return image;
            }
        } catch (Exception ignored) {
        }
        BufferedImage defaultIcon = loadDefaultIcon();
        return defaultIcon == null ? fallbackImage(path) : defaultIcon;
    }

    /** Loads the packaged image used whenever a requested icon resource is missing. */
    private static BufferedImage loadDefaultIcon() {
        try (InputStream input = Icons.class.getResourceAsStream("res/default-icon.png")) {
            return input == null ? null : ImageIO.read(input);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static BufferedImage fallbackImage(String path) {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(140, 148, 160));
            g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            String name = path == null ? "" : path.toLowerCase(java.util.Locale.ROOT);
            if (name.contains("x-icon")) {
                g.drawLine(10, 10, 22, 22);
                g.drawLine(22, 10, 10, 22);
            } else if (name.contains("run-icon")) {
                g.fillPolygon(new int[]{11, 11, 23}, new int[]{7, 25, 16}, 3);
            } else if (name.contains("dir")) {
                g.drawRoundRect(5, 10, 22, 16, 2, 2);
                g.drawLine(6, 10, 13, 10);
                g.drawLine(13, 10, 16, 13);
            } else {
                g.drawRoundRect(5, 5, 22, 22, 5, 5);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
                g.drawString("V", 10, 22);
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    /** Scales a named resource image to the requested icon dimensions. */
    public static Icon fit(String path, int width, int height) {
        return fit(load(path), width, height);
    }

    /** Uses a packaged action icon when available, with a recognizable vector fallback. */
    public static Icon action(String path, String type, int size, Color color) {
        return Icons.class.getResource("res/" + path) == null
                ? vector(type, size, color) : fit(path, size, size);
    }

    /** Selects the exported Windows association icon, with the project's default for unknown types. */
    public static Icon file(java.io.File file, int size) {
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String resource = dot < 0 ? "default-icon.png" : name.substring(dot + 1) + "-icon.png";
        if (Icons.class.getResource("res/" + resource) == null) resource = "default-icon.png";
        return fit(resource, size, size);
    }

    /** Creates a proportionally scaled icon from an image. */
    public static Icon fit(BufferedImage image, int width, int height) {
        if (image == null || width <= 0 || height <= 0) return null;
        return new FittedIcon(image, width, height);
    }

    /** Creates a small vector icon for a named editor action. */
    public static Icon vector(String type, int size, Color color) {
        return new Icon() {
            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
            @Override public void paintIcon(Component c, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(color == null ? Color.WHITE : color);
                    double s = size / 16.0;
                    g.translate(x, y);
                    g.scale(s, s);
                    switch (type) {
                        case "close" -> {
                            g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                            g.drawLine(4, 4, 12, 12); g.drawLine(12, 4, 4, 12);
                        }
                        case "running" -> {
                            g.fillRoundRect(1, 1, 14, 14, 4, 4);
                            g.setColor(new Color(194, 199, 205));
                            Polygon p = new Polygon(new int[]{5, 5, 12}, new int[]{4, 12, 8}, 3);
                            g.fill(p);
                        }
                        case "run", "rerun" -> {
                            Polygon p = new Polygon(new int[]{4, 4, 13}, new int[]{2, 14, 8}, 3);
                            g.fill(p);
                        }
                        case "debug" -> {
                            g.drawRect(4, 4, 8, 8);
                            g.fillRect(2, 6, 2, 1); g.fillRect(12, 6, 2, 1);
                            g.fillRect(6, 2, 1, 2); g.fillRect(9, 2, 1, 2);
                            g.fillRect(6, 12, 1, 2); g.fillRect(9, 12, 1, 2);
                        }
                        case "stop" -> g.fillRoundRect(3, 3, 10, 10, 3, 3);
                        case "pause" -> { g.fillRect(3, 2, 4, 12); g.fillRect(9, 2, 4, 12); }
                        case "back" -> {
                            g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                            g.drawLine(12, 8, 4, 8); g.drawLine(4, 8, 8, 4); g.drawLine(4, 8, 8, 12);
                        }
                        default -> g.drawRect(3, 3, 10, 10);
                    }
                } finally { g.dispose(); }
            }
        };
    }

    private static final class FittedIcon implements Icon {
        private final BufferedImage image;
        private final int width;
        private final int height;

        private FittedIcon(BufferedImage image, int width, int height) {
            this.image = image;
            this.width = width;
            this.height = height;
        }

        @Override
        public int getIconWidth() {
            return width;
        }

        @Override
        public int getIconHeight() {
            return height;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            double scale = Math.min(
                    (double) width / image.getWidth(),
                    (double) height / image.getHeight()
            );

            int drawWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
            int drawHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
            int drawX = x + (width - drawWidth) / 2;
            int drawY = y + (height - drawHeight) / 2;

            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
                g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                        RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
                g.drawImage(image, drawX, drawY, drawWidth, drawHeight, null);
            } finally {
                g.dispose();
            }
        }
    }
}
