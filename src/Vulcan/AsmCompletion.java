package Vulcan;

import Intel8080.assembler.OpcodeTable;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

public final class AsmCompletion {
    private AsmCompletion() {}

    /** Collects assembler mnemonics and source identifiers for completion. */
    public static Set<String> words(String source) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String mnemonic : OpcodeTable.OPCODES.getMnemonics()) {
            int space = mnemonic.indexOf(' ');
            result.add(space < 0 ? mnemonic : mnemonic.substring(0, space));
        }
        for (String line : source.split("\\R", -1)) {
            int comment = line.indexOf(';');
            String trimmed = (comment < 0 ? line : line.substring(0, comment)).trim();
            if (trimmed.isEmpty()) continue;
            int colon = trimmed.indexOf(':');
            if (colon > 0) addIdentifier(result, trimmed.substring(0, colon));
            String[] tokens = trimmed.split("\\s+", 3);
            if (tokens.length >= 2 && tokens[1].equalsIgnoreCase("EQU")) addIdentifier(result, tokens[0]);
        }
        return result;
    }

    private static void addIdentifier(Set<String> result, String value) {
        String name = value.trim();
        if (name.matches("[A-Za-z_][A-Za-z0-9_]*")) result.add(name);
    }

    /** Returns a completion prefix only at the end of an identifier in assembly code. */
    public static String prefixAt(String source, int caret) {
        if (caret <= 0 || caret > source.length() ||
                (caret < source.length() && identifierPart(source.charAt(caret)))) return "";
        int start = caret;
        while (start > 0 && identifierPart(source.charAt(start-1))) start--;
        if (start == caret || (start > 0 && source.charAt(start-1) == '.')) return "";
        int lineStart = source.lastIndexOf('\n', start-1)+1;
        char quote = 0;
        boolean escaped = false;
        for (int i = lineStart; i < start; i++) {
            char current = source.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (quote != 0 && current == '\\') { escaped = true; continue; }
            if (quote != 0) { if (current == quote) quote = 0; }
            else if (current == ';') return "";
            else if (current == '\'' || current == '"') quote = current;
        }
        return quote == 0 ? source.substring(start, caret) : "";
    }

    private static boolean identifierPart(char value) { return Character.isLetterOrDigit(value) || value == '_'; }

    /** Matches instructions using the typed case and keeps labels/equates as declared. */
    public static List<String> suggestions(String source, String prefix) {
        if (prefix.isEmpty()) return List.of();
        Set<String> instructions = words("");
        LinkedHashSet<String> matches = new LinkedHashSet<>();
        String uppercase = prefix.toUpperCase(Locale.ROOT);
        boolean lowercase = Character.isLowerCase(prefix.charAt(0));
        for (String word : instructions) {
            if (word.startsWith(uppercase)) matches.add(lowercase ? word.toLowerCase(Locale.ROOT) : word);
        }
        for (String word : words(source)) {
            if (!instructions.contains(word) && word.startsWith(prefix)) matches.add(word);
        }
        List<String> result = new ArrayList<>(matches);
        result.sort(String::compareTo);
        return result;
    }
}
