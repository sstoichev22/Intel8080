package Intel8080.cpu;

import java.util.ArrayList;
import java.util.List;

/** Exposes a normalized mouse position and active button through byte-wide ports. */
public final class MousePeripheral {
    public static final int COMMAND_PORT = 0x22;
    public static final int DATA_PORT = 0x23;
    public static final int BUTTON_PORT = 0x24;

    private final List<Integer> pressedButtons = new ArrayList<>(3);
    private float x;
    private float y;
    private long snapshot;
    private int selectedByte;
    private int snapshotButton;
    private long clickSnapshot;
    private int clickButton;
    private boolean clickSnapshotPending;

    /** Sets the current pointer position in the inclusive normalized range [-1, 1]. */
    public synchronized void setPosition(float x, float y) {
        requireNormalized(x, "x");
        requireNormalized(y, "y");
        this.x = x;
        this.y = y;
    }

    /** Marks one-based button 1 (left), 2 (middle), or 3 (right) as pressed. */
    public synchronized void pressButton(int oneBasedButton) {
        requireButton(oneBasedButton);
        pressedButtons.remove(Integer.valueOf(oneBasedButton));
        pressedButtons.add(oneBasedButton);
        clickSnapshot = coordinateSnapshot(x, y);
        clickButton = oneBasedButton;
        clickSnapshotPending = true;
    }

    /** Marks one-based button 1 (left), 2 (middle), or 3 (right) as released. */
    public synchronized void releaseButton(int oneBasedButton) {
        requireButton(oneBasedButton);
        pressedButtons.remove(Integer.valueOf(oneBasedButton));
    }

    /** Releases every currently held button. */
    public synchronized void releaseAllButtons() {
        pressedButtons.clear();
    }

    /** Selects one byte of the latched X-then-Y float snapshot. Command zero prefers an unconsumed click snapshot. */
    public synchronized void command(int value) {
        if (value < 0 || value > 7) return;
        if (value == 0) {
            if (clickSnapshotPending) {
                snapshot = clickSnapshot;
                snapshotButton = clickButton;
                clickSnapshotPending = false;
            } else {
                snapshot = coordinateSnapshot(x, y);
                snapshotButton = pressedButtons.isEmpty() ? 0 : pressedButtons.get(pressedButtons.size() - 1);
            }
        }
        selectedByte = value;
    }

    /** Returns the selected byte of the little-endian X-then-Y snapshot. */
    public synchronized byte readData() {
        return (byte) (snapshot >>> (selectedByte * Byte.SIZE));
    }

    /** Returns the button paired with the selected coordinate snapshot, including a just-released click. */
    public synchronized byte readButton() {
        return (byte) snapshotButton;
    }

    /** Resets pointer coordinates, pressed buttons, and the latched snapshot. */
    public synchronized void reset() {
        x = 0.0f;
        y = 0.0f;
        pressedButtons.clear();
        snapshot = 0;
        selectedByte = 0;
        snapshotButton = 0;
        clickSnapshot = 0;
        clickButton = 0;
        clickSnapshotPending = false;
    }

    private static long coordinateSnapshot(float x, float y) {
        return Integer.toUnsignedLong(Float.floatToRawIntBits(x))
                | (Integer.toUnsignedLong(Float.floatToRawIntBits(y)) << Integer.SIZE);
    }

    private static void requireNormalized(float value, String name) {
        if (!Float.isFinite(value) || value < -1.0f || value > 1.0f) {
            throw new IllegalArgumentException(name + " must be finite and between -1 and 1.");
        }
    }

    private static void requireButton(int oneBasedButton) {
        if (oneBasedButton < 1 || oneBasedButton > 3) {
            throw new IllegalArgumentException("Mouse button must be 1 (left), 2 (middle), or 3 (right).");
        }
    }
}
