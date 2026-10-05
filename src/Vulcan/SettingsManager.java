/** Single owner for persistent UI/project state; panels must consume this initialized instance rather than constructing their own settings. */
package Vulcan;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Point;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SettingsManager {

    private static final Path SETTINGS_PATH = resolveSettingsPath();
    private static final Path PRESETS_DIRECTORY = SETTINGS_PATH.getParent().resolve("presets");
    private static final int SETTINGS_VERSION = 1;

    private static Path resolveSettingsPath() {
        String configuredHome = System.getProperty("vulcan.home");
        Path base = configuredHome == null || configuredHome.isBlank()
                ? Path.of(System.getProperty("user.home"), ".vulcan")
                : Path.of(configuredHome);
        return base.resolve("settings.json").toAbsolutePath().normalize();
    }

    private static Path legacySettingsPath() {
        return Path.of(System.getProperty("user.dir"), "settings.json").toAbsolutePath().normalize();
    }

    private boolean darkMode = true;
    private boolean syntaxHighlighting = true;
    private final Map<String, Integer> fontSizes = new LinkedHashMap<>();
    private String editorFont = "Consolas";
    private String applicationFont = "Segoe UI";
    private Path currentDirectory;
    private Path currentFile;
    private Path terminalWorkingDirectory;
    private final List<Path> openFiles = new ArrayList<>();
    private final Map<Path, Integer> fileFontSizes = new LinkedHashMap<>();
    private int selectedMainTab = 0;
    private int selectedEditorTab = 0;
    private String displayPreset = "16x16 Video 2-bit";
    private Rectangle windowBounds;
    private int filePanelDividerLocation = 230;
    private int consoleDividerLocation = 600;
    private final Map<Path, Point> fileScrollPositions = new LinkedHashMap<>();
    private boolean settingsEditing;
    private boolean loadFailed;
    private final Map<String, Color> colors = new LinkedHashMap<>();

    // Settings are intentionally instance-owned; UI components receive the fully initialized object.
    // This avoids the old initialization-order bug where a panel could read colors before SettingsManager loaded them.
    /** Creates settings with a default project path and theme palette. */
    public SettingsManager() {
        Path defaultDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        currentDirectory = defaultDirectory;
        terminalWorkingDirectory = defaultDirectory;
        loadDefaultFontSizes();
        loadDefaultColors();
    }

    /** Defines the persisted font-size keys and their first-run values. */
    private void loadDefaultFontSizes() {
        fontSizes.put("application", 15);
        fontSizes.put("applicationTitle", 15);
        fontSizes.put("editor", 16);
        fontSizes.put("editorFilePanel", 15);
        fontSizes.put("editorFilePanelTitle", 14);
        fontSizes.put("editorFilePanelPath", 12);
        fontSizes.put("debuggerFilePanel", 15);
        fontSizes.put("debuggerFilePanelTitle", 14);
        fontSizes.put("debuggerFilePanelPath", 12);
        fontSizes.put("displayFilePanel", 15);
        fontSizes.put("displayFilePanelTitle", 14);
        fontSizes.put("displayFilePanelPath", 12);
        fontSizes.put("editorTab", 14);
        fontSizes.put("editorConsole", 15);
        fontSizes.put("displayConsole", 15);
        fontSizes.put("debuggerConsole", 15);
        fontSizes.put("terminal", 15);
        fontSizes.put("debuggerMemory", 15);
        fontSizes.put("debuggerInstructions", 15);
        fontSizes.put("debuggerRegisters", 17);
        fontSizes.put("debuggerFilename", 15);
        fontSizes.put("displayFilename", 15);
    }

    private void loadDefaultColors() {
        colors.put("applicationBackground", new Color(38, 41, 46));
        colors.put("panelBackground", new Color(46, 49, 55));
        colors.put("inputBackground", new Color(31, 34, 39));
        colors.put("text", new Color(225, 228, 235));
        colors.put("editorBackground", new Color(18, 20, 23));
        colors.put("editorText", new Color(225, 228, 235));
        colors.put("tabSelected", new Color(62, 66, 74));
        colors.put("tabText", new Color(235, 238, 244));
        colors.put("tabTextMuted", new Color(160, 166, 176));
        colors.put("fileSelectionBackground", new Color(68, 73, 82));
        colors.put("fileSelectionText", new Color(248, 249, 252));
        colors.put("splitDivider", new Color(72, 76, 84));
        colors.put("section", new Color(255, 105, 180));
        colors.put("callJump", new Color(255, 100, 100));
        colors.put("stack", new Color(255, 215, 75));
        colors.put("immediate", new Color(72, 195, 225));
        colors.put("escape", new Color(65, 220, 195));
        colors.put("string", new Color(85, 220, 135));
        colors.put("register", new Color(92, 165, 255));
        colors.put("directive", new Color(195, 125, 255));
        colors.put("instruction", new Color(255, 160, 70));
        colors.put("comment", new Color(125, 135, 145));
        colors.put("label", colors.get("editorText"));
        colors.put("equate", new Color(175, 220, 120));
        colors.put("lineNumber", new Color(133, 139, 150));
        colors.put("equate", new Color(175, 220, 120));
        colors.put("stopButton", new Color(192, 54, 64));
        colors.put("currentInstruction", new Color(52, 72, 100));
    }

    /** Loads saved settings, migrates legacy state when needed, and returns the initialized manager. */
    public static SettingsManager load() {
        SettingsManager settings = new SettingsManager();

        try {
            if (!Files.exists(SETTINGS_PATH)) {
                Path legacyPath = legacySettingsPath();
                if (!legacyPath.equals(SETTINGS_PATH) && Files.isRegularFile(legacyPath)) {
                    Path parent = SETTINGS_PATH.getParent();
                    if (parent != null) Files.createDirectories(parent);
                    Files.copy(legacyPath, SETTINGS_PATH);
                }
            }

            if (!Files.exists(SETTINGS_PATH)) {
                settings.migrateLegacyPresets();
                settings.ensurePresetsDirectory();
                settings.save();
                return settings;
            }

            String json = Files.readString(SETTINGS_PATH, StandardCharsets.UTF_8);
            if (!hasBalancedJsonContainer(json)) {
                settings.loadFailed = true;
                System.err.println("Vulcan: couldn't load settings (the settings file has invalid JSON structure).");
                return settings;
            }
            settings.darkMode = readBoolean(json, "darkMode", settings.darkMode);
            settings.syntaxHighlighting = readBoolean(json, "syntaxHighlighting", settings.syntaxHighlighting);
            String fontSizesJson = extractJsonObject(json, "fontSizes");
            for (String name : settings.fontSizes.keySet()) {
                int fallback = settings.fontSizes.get(name);
                int saved = name.equals("editor")
                        ? readInteger(fontSizesJson, name, readInteger(json, "editorFontSize", fallback))
                        : readInteger(fontSizesJson, name, fallback);
                settings.fontSizes.put(name, clampFontSize(saved));
            }
            settings.selectedMainTab = Math.max(0, readInteger(json, "selectedMainTab", 0));
            settings.selectedEditorTab = Math.max(0, readInteger(json, "selectedEditorTab", 0));
            Matcher windowMatcher = Pattern.compile("\"window\"\\s*:\\s*\\{([^{}]*)\\}").matcher(json);
            if (windowMatcher.find()) {
                String windowJson = windowMatcher.group(1);
                int x = readInteger(windowJson, "x", Integer.MIN_VALUE);
                int y = readInteger(windowJson, "y", Integer.MIN_VALUE);
                int width = readInteger(windowJson, "width", 0);
                int height = readInteger(windowJson, "height", 0);
                if (x != Integer.MIN_VALUE && y != Integer.MIN_VALUE &&
                        width >= 320 && height >= 240 && width <= 20000 && height <= 20000) {
                    settings.windowBounds = new Rectangle(x, y, width, height);
                }
            }
            Matcher layoutMatcher = Pattern.compile("\"layout\"\\s*:\\s*\\{([^{}]*)\\}").matcher(json);
            if (layoutMatcher.find()) {
                String layoutJson = layoutMatcher.group(1);
                settings.filePanelDividerLocation = Math.max(80, Math.min(5000,
                        readInteger(layoutJson, "filePanelDivider", settings.filePanelDividerLocation)));
                settings.consoleDividerLocation = Math.max(120, Math.min(5000,
                        readInteger(layoutJson, "consoleDivider", settings.consoleDividerLocation)));
            }
            String savedPreset = readString(json, "displayPreset");
            if (savedPreset != null && !savedPreset.isBlank()) settings.displayPreset = savedPreset;

            String editorFont = readString(json, "editorFont");
            if (editorFont != null && !editorFont.isBlank()) settings.editorFont = editorFont;

            String applicationFont = readString(json, "applicationFont");
            if (applicationFont != null && !applicationFont.isBlank()) settings.applicationFont = applicationFont;

            for (String name : settings.colors.keySet()) {
                Color color = readRgb(json, name, settings.colors.get(name));
                settings.colors.put(name, color);
            }
            if (readInteger(json, "settingsVersion", 0) < SETTINGS_VERSION &&
                    hasRgb(json, "immediate") && hasRgb(json, "register")) {
                settings.swapNumberAndRegisterColors();
            }

            if (!settings.darkMode) settings.applyLightPalette();

            Path directory = readPath(json, "fileProjectDirectory");
            if (directory == null) directory = readPath(json, "currentDirectory");
            if (directory != null && Files.isDirectory(directory)) settings.currentDirectory = directory;

            Path currentFile = readStoredPath(json, "currentFile", settings.currentDirectory);
            if (currentFile != null && Files.isRegularFile(currentFile)) {
                settings.currentFile = currentFile;
                Path parent = currentFile.getParent();
                if (directory == null && parent != null && Files.isDirectory(parent)) settings.currentDirectory = parent;
            }

            String openFilesText = readString(json, "openFiles");
            if (openFilesText != null) {
                for (String value : openFilesText.split("\\|", -1)) {
                    if (value.isBlank()) continue;
                    try {
                        Path candidate = Path.of(value);
                        if (!candidate.isAbsolute()) candidate = settings.currentDirectory.resolve(candidate);
                        candidate = candidate.toAbsolutePath().normalize();
                        if (Files.isRegularFile(candidate)) settings.openFiles.add(candidate);
                    } catch (RuntimeException ignored) {
                        // Ignore one invalid saved path without losing the rest of the settings.
                    }
                }
            }

            settings.loadFileFontSizes(json, fontSizesJson);
            settings.loadFileScrollPositions(json);
            Path terminalDirectory = readPath(json, "terminalWorkingDirectory");
            if (terminalDirectory != null && Files.isDirectory(terminalDirectory)) {
                settings.terminalWorkingDirectory = terminalDirectory;
            } else {
                settings.terminalWorkingDirectory = settings.currentDirectory;
            }
        } catch (IOException error) {
            settings.loadFailed = true;
            System.err.println("Vulcan: couldn't load settings (" + VulcanDialog.errorSummary(error) + ").");
            return settings;
        }

        settings.migrateLegacyPresets();
        settings.ensurePresetsDirectory();
        settings.save();
        return settings;
    }

    private void migrateLegacyPresets() {
        Path legacyDirectory = Path.of(System.getProperty("user.dir"), "presets").toAbsolutePath().normalize();
        if (legacyDirectory.equals(PRESETS_DIRECTORY) || !Files.isDirectory(legacyDirectory)) return;
        try {
            Files.createDirectories(PRESETS_DIRECTORY);
            try (var entries = Files.list(legacyDirectory)) {
                for (Path source : entries.toList()) {
                    if (!Files.isRegularFile(source, java.nio.file.LinkOption.NOFOLLOW_LINKS) ||
                            !source.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".preset")) continue;
                    Path target = PRESETS_DIRECTORY.resolve(source.getFileName().toString());
                    if (!Files.exists(target)) Files.copy(source, target);
                }
            }
        } catch (IOException e) {
            System.err.println("Vulcan: couldn't import presets (" + VulcanDialog.errorSummary(e) + ").");
        }
    }

    private static boolean hasBalancedJsonContainer(String json) {
        if (json == null) return false;
        String value = json.trim();
        if (value.startsWith("\uFEFF")) value = value.substring(1).trim();
        if (!value.startsWith("{") || !value.endsWith("}")) return false;

        Deque<Character> openings = new ArrayDeque<>();
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == '"') quoted = false;
                continue;
            }
            if (current == '"') quoted = true;
            else if (current == '{' || current == '[') openings.push(current);
            else if (current == '}' || current == ']') {
                if (openings.isEmpty()) return false;
                char opening = openings.pop();
                if ((current == '}' && opening != '{') || (current == ']' && opening != '[')) return false;
            }
        }
        return !quoted && openings.isEmpty();
    }

    /** Loads per-file sizes only for files present in the restored open-file list. */
    private void loadFileFontSizes(String json, String fontSizesJson) {
        fileFontSizes.clear();
        String sectionJson = extractJsonObject(fontSizesJson, "editorFiles");
        if (sectionJson == null) sectionJson = extractJsonObject(json, "fileFontSizes");
        if (sectionJson == null) return;
        Matcher entry = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"\\s*:\\s*(\\d+)").matcher(sectionJson);
        while (entry.find()) {
            try {
                Path path = Path.of(unescape(entry.group(1)));
                if (!path.isAbsolute()) path = currentDirectory.resolve(path);
                path = path.toAbsolutePath().normalize();
                int size = clampFontSize(Integer.parseInt(entry.group(2)));
                if (size != getEditorFontSize() && openFiles.contains(path)) fileFontSizes.put(path, size);
            } catch (RuntimeException ignored) {
                // Ignore one malformed path or size without losing the other settings.
            }
        }
    }
    /** Loads scroll offsets only for files restored as open editor tabs. */
    private void loadFileScrollPositions(String json) {
        fileScrollPositions.clear();
        Matcher section = Pattern.compile("\"fileScrollPositions\"\\s*:\\s*\\{([^{}]*)\\}").matcher(json);
        if (!section.find()) return;
        Matcher entry = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"\\s*:\\s*\\[\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\]").matcher(section.group(1));
        while (entry.find()) {
            try {
                Path path = Path.of(unescape(entry.group(1)));
                if (!path.isAbsolute()) path = currentDirectory.resolve(path);
                path = path.toAbsolutePath().normalize();
                if (openFiles.contains(path)) {
                    fileScrollPositions.put(path, new Point(
                            Integer.parseInt(entry.group(2)), Integer.parseInt(entry.group(3))));
                }
            } catch (RuntimeException ignored) {
                // Ignore malformed per-file offsets and keep loading the rest.
            }
        }
    }
    private static boolean readBoolean(String json, String name, boolean fallback) {
        Matcher matcher = Pattern.compile(
                "\\\"" + Pattern.quote(name) + "\\\"\\s*:\\s*(true|false)",
                Pattern.CASE_INSENSITIVE
        ).matcher(json);
        return matcher.find() ? Boolean.parseBoolean(matcher.group(1)) : fallback;
    }

    private static int readInteger(String json, String name, int fallback) {
        if (json == null) return fallback;
        Matcher matcher = Pattern.compile(
                "\\\"" + Pattern.quote(name) + "\\\"\\s*:\\s*(-?\\d+)"
        ).matcher(json);
        if (!matcher.find()) return fallback;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Returns the contents of a named JSON object while respecting quoted braces. */
    private static String extractJsonObject(String json, String name) {
        if (json == null) return null;
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(name) + "\\\"\\s*:\\s*\\{").matcher(json);
        if (!matcher.find()) return null;
        int open = matcher.end() - 1;
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = open; i < json.length(); i++) {
            char c = json.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '\"') quoted = false;
                continue;
            }
            if (c == '\"') quoted = true;
            else if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return json.substring(open + 1, i);
        }
        return null;
    }

    /** Keeps loaded and user-adjusted font sizes within a readable UI range. */
    private static int clampFontSize(int size) {
        return Math.max(8, Math.min(48, size));
    }

    private static String readString(String json, String name) {
        Matcher matcher = Pattern.compile(
                "\\\"" + Pattern.quote(name) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\""
        ).matcher(json);
        return matcher.find() ? unescape(matcher.group(1)) : null;
    }

    private static Color readRgb(String json, String name, Color fallback) {
        Matcher matcher = Pattern.compile(
                "\\\"" + Pattern.quote(name) + "\\\"\\s*:\\s*\\[\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\]"
        ).matcher(json);

        if (!matcher.find()) return fallback;

        try {
            int r = Integer.parseInt(matcher.group(1));
            int g = Integer.parseInt(matcher.group(2));
            int b = Integer.parseInt(matcher.group(3));
            if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) return fallback;
            return new Color(r, g, b);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Checks whether a saved palette explicitly contains a color before migration. */
    private static boolean hasRgb(String json, String name) {
        return Pattern.compile("\\\"" + Pattern.quote(name) + "\\\"\\s*:\\s*\\[\\s*\\d+\\s*,\\s*\\d+\\s*,\\s*\\d+\\s*\\]")
                .matcher(json).find();
    }

    private static Path readPath(String json, String name) {
        String value = readString(json, name);
        if (value == null || value.isBlank()) return null;
        try {
            return Path.of(value).toAbsolutePath().normalize();
        } catch (Exception e) {
            return null;
        }
    }

    private static Path readStoredPath(String json, String name, Path base) {
        String value = readString(json, name);
        if (value == null || value.isBlank()) return null;
        try {
            Path path = Path.of(value);
            return (path.isAbsolute() ? path : base.resolve(path)).toAbsolutePath().normalize();
        } catch (Exception e) { return null; }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String unescape(String value) {
        StringBuilder result = new StringBuilder();
        boolean escaped = false;

        for (char c : value.toCharArray()) {
            if (escaped) {
                switch (c) {
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case '"' -> result.append('"');
                    case '\\' -> result.append('\\');
                    default -> result.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                result.append(c);
            }
        }

        if (escaped) result.append('\\');
        return result.toString();
    }

    /** Atomically writes the current UI and project settings to disk. */
    public synchronized void save() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"settingsVersion\": ").append(SETTINGS_VERSION).append(",\n");
        json.append("  \"look\": {\n");
        json.append("    \"darkMode\": ").append(darkMode).append(",\n");
        json.append("    \"syntaxHighlighting\": ").append(syntaxHighlighting).append(",\n");
        json.append("    \"editorFont\": \"").append(escape(editorFont)).append("\",\n");
        json.append("    \"applicationFont\": \"").append(escape(applicationFont)).append("\"\n");
        json.append("  },\n");
        appendFontSizes(json);
        json.append("  \"tabs\": {\n");
        json.append("    \"selectedMainTab\": ").append(selectedMainTab).append(",\n");
        json.append("    \"selectedEditorTab\": ").append(selectedEditorTab).append(",\n");
        json.append("    \"displayPreset\": \"").append(escape(displayPreset)).append("\"\n");
        json.append("  },\n");
        json.append("  \"layout\": {\"filePanelDivider\": ").append(filePanelDividerLocation)
                .append(", \"consoleDivider\": ").append(consoleDividerLocation).append("},\n");
        json.append("  \"window\": ");
        if (windowBounds == null) {
            json.append("null,\n");
        } else {
            json.append("{\"x\": ").append(windowBounds.x)
                    .append(", \"y\": ").append(windowBounds.y)
                    .append(", \"width\": ").append(windowBounds.width)
                    .append(", \"height\": ").append(windowBounds.height).append("},\n");
        }
        appendFileScrollPositions(json);
        json.append("  \"paths\": {\n");
        json.append("    \"fileProjectDirectory\": "); appendPath(json, currentDirectory); json.append(",\n");
        json.append("    \"terminalWorkingDirectory\": "); appendPath(json, terminalWorkingDirectory); json.append(",\n");
        json.append("    \"currentFile\": "); appendStoredPath(json, currentFile, currentDirectory); json.append(",\n");
        json.append("    \"openFiles\": \"");
        List<String> relative = new ArrayList<>();
        for (Path path : openFiles) {
            try { relative.add(currentDirectory.relativize(path.toAbsolutePath().normalize()).toString()); }
            catch (Exception e) { relative.add(path.toAbsolutePath().normalize().toString()); }
        }
        json.append(escape(String.join("|", relative))).append("\"\n");
        json.append("  },\n");
        json.append("  \"colors\": {\n");
        int index = 0;
        for (String name : colors.keySet()) {
            Color color = colors.get(name);
            json.append("    \"").append(name).append("\": [")
                    .append(color.getRed()).append(", ").append(color.getGreen()).append(", ").append(color.getBlue()).append("]")
                    .append(++index == colors.size() ? "\n" : ",\n");
        }
        json.append("  }\n");
        json.append("}\n");
        Path temporary = null;
        try {
            Path parent = SETTINGS_PATH.getParent();
            if (parent != null) Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, "settings-", ".tmp");
            Files.writeString(temporary, json.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, SETTINGS_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, SETTINGS_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("Vulcan: couldn't save settings to " + SETTINGS_PATH + " (" + VulcanDialog.errorSummary(e) + ").");
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }

    /** Writes named panel sizes and nondefault per-file sizes in one discoverable section. */
    private void appendFontSizes(StringBuilder json) {
        json.append("  \"fontSizes\": {\n");
        for (Map.Entry<String, Integer> entry : fontSizes.entrySet()) {
            json.append("    \"").append(entry.getKey()).append("\": ")
                    .append(entry.getValue()).append(",\n");
        }
        json.append("    \"editorFiles\": {");
        boolean first = true;
        for (Path path : openFiles) {
            Integer size = fileFontSizes.get(path.toAbsolutePath().normalize());
            if (size == null || size == getEditorFontSize()) continue;
            if (first) json.append("\n");
            else json.append(",\n");
            String key;
            try { key = currentDirectory.relativize(path.toAbsolutePath().normalize()).toString(); }
            catch (Exception e) { key = path.toAbsolutePath().normalize().toString(); }
            json.append("      \"").append(escape(key)).append("\": ").append(size);
            first = false;
        }
        if (!first) json.append("\n    }\n");
        else json.append("}\n");
        json.append("  },\n");
    }
    /** Writes scroll offsets only for files that remain open. */
    private void appendFileScrollPositions(StringBuilder json) {
        json.append("  \"fileScrollPositions\": {");
        boolean first = true;
        for (Path path : openFiles) {
            Point position = fileScrollPositions.get(path.toAbsolutePath().normalize());
            if (position == null) continue;
            if (first) json.append("\n");
            else json.append(",\n");
            String key;
            try { key = currentDirectory.relativize(path.toAbsolutePath().normalize()).toString(); }
            catch (Exception e) { key = path.toAbsolutePath().normalize().toString(); }
            json.append("    \"").append(escape(key)).append("\": [")
                    .append(Math.max(0, position.x)).append(", ").append(Math.max(0, position.y)).append("]");
            first = false;
        }
        if (!first) json.append("\n  }");
        else json.append("}");
        json.append(",\n");
    }
    private void appendOpenFiles(StringBuilder json) {
        json.append('[');
        boolean first = true;
        for (Path path : openFiles) {
            if (!first) json.append(", ");
            String value;
            try { value = currentDirectory.relativize(path.toAbsolutePath().normalize()).toString(); }
            catch (Exception e) { value = path.toAbsolutePath().normalize().toString(); }
            json.append('\"').append(escape(value)).append('\"');
            first = false;
        }
        json.append(']');
    }

    private static void appendStoredPath(StringBuilder json, Path path, Path base) {
        if (path == null) { json.append("null"); return; }
        try { json.append('"').append(escape(base.relativize(path.toAbsolutePath().normalize()).toString())).append('"'); }
        catch (Exception e) { appendPath(json, path); }
    }

    private static void appendPath(StringBuilder json, Path path) {
        if (path == null) json.append("null");
        else json.append('"').append(escape(path.toString())).append('"');
    }

    /** Reloads saved settings while retaining the current settings object. */
    public synchronized void reloadFromDisk() {
        if (!Files.exists(SETTINGS_PATH)) return;
        try {
            // Do not construct/load a second SettingsManager from UI code.
            // Earlier iterations allowed panels to observe settings before the singleton-like owner was ready.
            SettingsManager fresh = load();
            if (fresh.loadFailed) return;
            this.darkMode = fresh.darkMode;
            this.syntaxHighlighting = fresh.syntaxHighlighting;
            this.fontSizes.clear();
            this.fontSizes.putAll(fresh.fontSizes);
            this.editorFont = fresh.editorFont;
            this.applicationFont = fresh.applicationFont;
            this.colors.clear();
            this.colors.putAll(fresh.colors);
            this.currentDirectory = fresh.currentDirectory;
            this.currentFile = fresh.currentFile;
            this.terminalWorkingDirectory = fresh.terminalWorkingDirectory;
            this.openFiles.clear();
            this.openFiles.addAll(fresh.openFiles);
            this.fileFontSizes.clear();
            this.fileFontSizes.putAll(fresh.fileFontSizes);
            this.fileScrollPositions.clear();
            this.fileScrollPositions.putAll(fresh.fileScrollPositions);
            this.filePanelDividerLocation = fresh.filePanelDividerLocation;
            this.consoleDividerLocation = fresh.consoleDividerLocation;
            this.selectedMainTab = fresh.selectedMainTab;
            this.selectedEditorTab = fresh.selectedEditorTab;
            this.displayPreset = fresh.displayPreset;
            this.windowBounds = fresh.windowBounds == null ? null : new Rectangle(fresh.windowBounds);
        } catch (Exception error) {
            System.err.println("Vulcan: couldn't reload settings (" + VulcanDialog.errorSummary(error) + ").");
        }
    }

    /** Marks whether settings should be saved while the settings file is being edited. */
    public synchronized void setSettingsEditing(boolean editing) {
        settingsEditing = editing;
    }

    /** Reports whether the settings file is currently being edited in the editor. */
    public synchronized boolean isSettingsEditing() {
        return settingsEditing;
    }

    /** Reports whether the dark theme is selected. */
    public boolean isDarkMode() {
        return darkMode;
    }

    /** Reports whether syntax highlighting is enabled. */
    public boolean isSyntaxHighlighting() {
        return syntaxHighlighting;
    }

    /** Returns the editor font size. */
    public int getEditorFontSize() {
        return getFontSize("editor");
    }

    /** Returns a named UI font size, or a safe default for an unknown component key. */
    public synchronized int getFontSize(String component) {
        return component == null ? 15 : fontSizes.getOrDefault(component, 15);
    }

    /** Updates a known UI font size; saving is deferred to the normal settings save. */
    public synchronized void setFontSize(String component, int size) {
        if (component == null || !fontSizes.containsKey(component)) return;
        fontSizes.put(component, clampFontSize(size));
    }

    /** Returns the editor font. */
    public String getEditorFont() {
        return editorFont;
    }

    /** Returns the application font. */
    public String getApplicationFont() {
        return applicationFont;
    }

    /** Returns the configured color, or the default text color for an unknown key. */
    public Color getColor(String name) {
        return colors.getOrDefault(name, colors.get("text"));
    }

    /** Returns the current directory. */
    public Path getCurrentDirectory() {
        return currentDirectory;
    }

    /** Returns the current file. */
    public Path getCurrentFile() {
        return currentFile;
    }

    /** Returns the terminal working directory. */
    public Path getTerminalWorkingDirectory() { return terminalWorkingDirectory; }
    /** Returns an immutable snapshot of the currently saved open-file list. */
    public synchronized List<Path> getOpenFiles() { return List.copyOf(openFiles); }
    /** Returns the saved file-panel divider position. */
    public synchronized int getFilePanelDividerLocation() { return filePanelDividerLocation; }

    /** Returns the saved console divider position. */
    public synchronized int getConsoleDividerLocation() { return consoleDividerLocation; }

    /** Stores the editor workspace split positions for the next close-time save. */
    public synchronized void setEditorDividerLocations(int filePanel, int console) {
        filePanelDividerLocation = Math.max(80, Math.min(5000, filePanel));
        consoleDividerLocation = Math.max(120, Math.min(5000, console));
    }

    /** Returns the saved per-file viewport position, or null when none was saved. */
    public synchronized Point getFileScrollPosition(Path path) {
        if (path == null) return null;
        Point point = fileScrollPositions.get(path.toAbsolutePath().normalize());
        return point == null ? null : new Point(point);
    }

    /** Stores a file viewport position for the next settings save. */
    public synchronized void setFileScrollPosition(Path path, Point position) {
        if (path == null || position == null) return;
        fileScrollPositions.put(path.toAbsolutePath().normalize(),
                new Point(Math.max(0, position.x), Math.max(0, position.y)));
    }
    /** Returns the last saved normal-window bounds, if present. */
    public synchronized Rectangle getWindowBounds() {
        return windowBounds == null ? null : new Rectangle(windowBounds);
    }

    /** Saves normal-window bounds so the next launch restores the same position and size. */
    public synchronized void setWindowBounds(Rectangle bounds) {
        if (bounds == null || bounds.width < 320 || bounds.height < 240) return;
        windowBounds = new Rectangle(bounds);
        if (!settingsEditing) save();
    }
    /** Returns a file's saved editor size, falling back to the project default. */
    public synchronized int getFileFontSize(Path path) {
        if (path == null) return getEditorFontSize();
        return fileFontSizes.getOrDefault(path.toAbsolutePath().normalize(), getEditorFontSize());
    }

    /** Records a nondefault size for an open file without writing once per wheel event. */
    public synchronized void setFileFontSize(Path path, int size) {
        if (path == null) return;
        Path normalized = path.toAbsolutePath().normalize();
        int bounded = clampFontSize(size);
        if (bounded == getEditorFontSize()) fileFontSizes.remove(normalized);
        else fileFontSizes.put(normalized, bounded);
    }
    /** Returns the selected main tab. */
    public synchronized int getSelectedMainTab() { return selectedMainTab; }
    /** Returns the selected editor tab. */
    public synchronized int getSelectedEditorTab() { return selectedEditorTab; }
    /** Returns the name of the selected display preset. */
    public synchronized String getDisplayPreset() { return displayPreset; }
    /** Stores the main application tab selected for the next launch. */
    public synchronized void setSelectedMainTab(int index) { selectedMainTab = Math.max(0, index); if (!settingsEditing) save(); }
    /** Stores the editor tab selected for the next launch. */
    public synchronized void setSelectedEditorTab(int index) { selectedEditorTab = Math.max(0, index); if (!settingsEditing) save(); }
    /** Selects the display preset that should be restored at startup. */
    public synchronized void setDisplayPreset(String preset) { if (preset != null && !preset.isBlank()) { displayPreset = preset; if (!settingsEditing) save(); } }
    /** Replaces the persisted list of open editor files. */
    public synchronized void setOpenFiles(List<Path> paths) {
        openFiles.clear();
        if (paths != null) for (Path path : paths) if (path != null) openFiles.add(path.toAbsolutePath().normalize());
        fileFontSizes.keySet().retainAll(openFiles);
        fileScrollPositions.keySet().retainAll(openFiles);
        if (!settingsEditing) save();
    }

    /** Resolves a user-entered path against the current project directory. */
    public Path resolveLocalPath(String local) {
        if (local == null || local.isBlank()) return currentDirectory;
        Path p = Path.of(local);
        return (p.isAbsolute() ? p : currentDirectory.resolve(p)).normalize().toAbsolutePath();
    }

    private void applyLightPalette() {
        colors.put("applicationBackground", new Color(238, 240, 243));
        colors.put("panelBackground", new Color(248, 249, 251));
        colors.put("inputBackground", new Color(255, 255, 255));
        colors.put("text", new Color(35, 38, 43));
        colors.put("editorBackground", new Color(255, 255, 255));
        colors.put("editorText", new Color(30, 33, 38));
        colors.put("tabSelected", new Color(225, 229, 235));
        colors.put("tabText", new Color(25, 28, 32));
        colors.put("tabTextMuted", new Color(100, 106, 116));
        colors.put("fileSelectionBackground", new Color(210, 220, 235));
        colors.put("fileSelectionText", new Color(20, 24, 30));
        colors.put("splitDivider", new Color(190, 195, 202));
        colors.put("lineNumber", new Color(105, 112, 122));
    }

    /** Applies the one-time migration requested for number and register syntax colors. */
    private void swapNumberAndRegisterColors() {
        Color numberColor = colors.get("immediate");
        colors.put("immediate", colors.get("register"));
        colors.put("register", numberColor);
    }

    /** Stores the dark-mode preference and persists it. */
    public synchronized void setDarkMode(boolean darkMode) {
        this.darkMode = darkMode;
        if (!settingsEditing) save();
    }

    /** Enables or disables editor syntax highlighting. */
    public synchronized void setSyntaxHighlighting(boolean syntaxHighlighting) {
        this.syntaxHighlighting = syntaxHighlighting;
        if (!settingsEditing) save();
    }

    /** Sets the editor font size within the supported range. */
    public synchronized void setEditorFontSize(int editorFontSize) {
        setFontSize("editor", editorFontSize);
        if (!settingsEditing) save();
    }

    /** Sets the font used for source text. */
    public synchronized void setEditorFont(String editorFont) {
        if (editorFont != null && !editorFont.isBlank()) {
            this.editorFont = editorFont;
            if (!settingsEditing) save();
        }
    }

    /** Sets the font used by application controls. */
    public synchronized void setApplicationFont(String applicationFont) {
        if (applicationFont != null && !applicationFont.isBlank()) {
            this.applicationFont = applicationFont;
            if (!settingsEditing) save();
        }
    }

    /** Updates a known theme color and persists the change. */
    public synchronized void setColor(String name, Color color) {
        if (name == null || color == null || !colors.containsKey(name)) return;
        colors.put(name, color);
        if (!settingsEditing) save();
    }

    /** Sets the directory used as the current project location. */
    public synchronized void setCurrentDirectory(Path currentDirectory) {
        if (currentDirectory == null) return;
        this.currentDirectory = currentDirectory.toAbsolutePath().normalize();
        if (!settingsEditing) save();
    }

    /** Sets the file that the application should restore as current. */
    public synchronized void setCurrentFile(Path currentFile) {
        this.currentFile = currentFile == null
                ? null
                : currentFile.toAbsolutePath().normalize();
        if (!settingsEditing) save();
    }

    /** Sets the terminal directory restored at startup. */
    public synchronized void setTerminalWorkingDirectory(Path terminalWorkingDirectory) {
        if (terminalWorkingDirectory == null) return;
        this.terminalWorkingDirectory = terminalWorkingDirectory.toAbsolutePath().normalize();
        if (!settingsEditing) save();
    }

    /** Returns the directory used to store display presets. */
    public Path getPresetsDirectory() {
        ensurePresetsDirectory();
        return PRESETS_DIRECTORY;
    }

    private void ensurePresetsDirectory() {
        try { Files.createDirectories(PRESETS_DIRECTORY); }
        catch (IOException error) {
            System.err.println("Vulcan: couldn't create the presets folder (" + VulcanDialog.errorSummary(error) + ").");
        }
    }

    /** Returns the location of the Vulcan settings file. */
    public Path getPath() {
        return SETTINGS_PATH.toAbsolutePath();
    }
}
