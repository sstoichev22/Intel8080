package Vulcan;

/** Debugger console shares buffered byte rendering, ANSI colors, and input with the other consoles. */
public final class DebugConsolePanel extends ConsolePanel {
    /** Creates the debugger's independent console and font setting. */
    public DebugConsolePanel(SettingsManager settings) {
        super(settings, "debuggerConsole");
        setTitle("Console");
    }
}
