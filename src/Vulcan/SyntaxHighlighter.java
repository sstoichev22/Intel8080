package Vulcan;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SyntaxHighlighter {

    private static final Set<String> DIRECTIVES = Set.of("ORG", "EQU", "DB", "DW", "DS", "END");
    private static final Set<String> SECTIONS = Set.of(".CODE", ".DATA");
    private static final Set<String> CALL_JUMP = Set.of(
            "CALL", "RET", "RST", "PCHL",
            "JNZ", "JZ", "JNC", "JC", "JPO", "JPE", "JP", "JM", "JMP",
            "CNZ", "CZ", "CNC", "CC", "CPO", "CPE", "CP", "CM",
            "RNZ", "RZ", "RNC", "RC", "RPO", "RPE", "RP", "RM"
    );
    private static final Set<String> STACK = Set.of("PUSH", "POP", "XTHL", "SPHL", "SP", "PSW");
    private static final Set<String> REGISTERS = Set.of("A", "B", "C", "D", "E", "H", "L", "M");
    private static final Set<String> INSTRUCTIONS = Set.of(
            "NOP", "LXI", "STAX", "LDAX", "INX", "DCX", "INR", "DCR", "MVI", "MOV",
            "DAD", "RLC", "RRC", "RAL", "RAR", "DAA", "CMA", "STC", "CMC",
            "HLT", "STOP",
            "ADD", "ADC", "SUB", "SBB", "ANA", "XRA", "ORA", "CMP",
            "ADI", "ACI", "SUI", "SBI", "ANI", "XRI", "ORI", "CPI",
            "IN", "OUT", "XCHG", "SHLD", "LHLD", "STA", "LDA", "DI", "EI"
    );

    private static final Pattern TOKEN = Pattern.compile(
            "\\.?(?:[A-Za-z_][A-Za-z0-9_-]*|(?:0x|0X|0h|0H|0b|0B)[0-9A-Fa-f]+|\\d+)"
    );

    private SyntaxHighlighter() {
    }

    /** Applies syntax highlighting to the editor for the selected file type. */
    public static void apply(JTextPane editor, String extension, boolean enabled, SettingsManager settings) {
        applyRange(editor, extension, enabled, settings, 0, Integer.MAX_VALUE, true);
    }

    /** Reapplies syntax styling only to the source range affected by an edit. */
    public static void applyRange(JTextPane editor, String extension, boolean enabled,
                                  SettingsManager settings, int changedStart, int changedEnd) {
        applyRange(editor, extension, enabled, settings, changedStart, changedEnd, false);
    }

    private static void applyRange(JTextPane editor, String extension, boolean enabled,
                                   SettingsManager settings, int changedStart, int changedEnd,
                                   boolean fullReset) {
        StyledDocument document = editor.getStyledDocument();

        Style base = document.getStyle("vulcan-base");
        if (base == null) base = document.addStyle("vulcan-base", null);
        StyleConstants.setFontFamily(base, editor.getFont().getFamily());
        StyleConstants.setFontSize(base, editor.getFont().getSize());
        StyleConstants.setForeground(base, Theme.text(settings));
        StyleConstants.setBold(base, false);

        int start;
        int end;
        if (fullReset) {
            start = 0;
            end = document.getLength();
        } else {
            int safeStart = Math.max(0, Math.min(changedStart, document.getLength()));
            int safeEnd = Math.max(safeStart, Math.min(changedEnd, document.getLength()));
            var root = document.getDefaultRootElement();
            var startElement = root.getElement(root.getElementIndex(safeStart));
            var endElement = root.getElement(root.getElementIndex(Math.max(0, safeEnd - 1)));
            start = startElement.getStartOffset();
            end = Math.min(document.getLength(), endElement.getEndOffset());
        }

        if (end > start) document.setCharacterAttributes(start, end - start, base, true);
        if (!enabled || !extension.equalsIgnoreCase("asm") || end <= start) return;

        var root = document.getDefaultRootElement();
        int firstLine = root.getElementIndex(start);
        int lastLine = root.getElementIndex(Math.max(start, end - 1));
        int lineStart = root.getElement(firstLine).getStartOffset();
        int lineEnd = Math.min(document.getLength(), root.getElement(lastLine).getEndOffset());

        String localText;
        try {
            localText = document.getText(lineStart, Math.max(0, lineEnd - lineStart));
        } catch (BadLocationException e) {
            return;
        }

        Set<String> equates = collectEquates(localText);
        String[] lines = localText.split("\\n", -1);
        int offset = lineStart;
        for (String line : lines) {
            applyLine(document, line, offset, settings, equates);
            offset += line.length() + 1;
        }
    }


    /** Schedules incremental syntax styling and invokes the completion callback afterward. */
    public static void applyRangeAsync(JTextPane editor, String extension, boolean enabled,
                                       SettingsManager settings, int changedStart, int changedEnd,
                                       Runnable finished) {
        if (!enabled || !extension.equalsIgnoreCase("asm")) {
            SwingUtilities.invokeLater(() -> { applyRange(editor, extension, enabled, settings, changedStart, changedEnd); if (finished != null) finished.run(); });
            return;
        }
        SwingUtilities.invokeLater(() -> {
            int length = editor.getDocument().getLength();
            int safeStart = Math.max(0, Math.min(changedStart, length));
            int safeEnd = Math.max(safeStart, Math.min(changedEnd, length));
            try {
                var root = editor.getDocument().getDefaultRootElement();
                var a = root.getElement(root.getElementIndex(safeStart));
                var b = root.getElement(root.getElementIndex(Math.max(0, safeEnd - 1)));
                int start = a.getStartOffset();
                int end = Math.min(length, b.getEndOffset());
                String snapshot = editor.getDocument().getText(start, Math.max(0, end - start));
                int fontSize = editor.getFont().getSize();
                SwingWorker<Void, Void> worker = new SwingWorker<>() {
                    @Override protected Void doInBackground() { return null; }
                    @Override protected void done() {
                        applyRange(editor, extension, enabled, settings, start, end);
                        if (finished != null) finished.run();
                    }
                };
                worker.execute();
            } catch (BadLocationException e) {
                if (finished != null) finished.run();
            }
        });
    }

    private static Set<String> collectEquates(String text) {
        Set<String> result = new HashSet<>();
        for (String line : text.split("\n", -1)) {
            Matcher m = Pattern.compile("^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s+EQU\\b", Pattern.CASE_INSENSITIVE).matcher(line);
            if (m.find()) result.add(m.group(1).toUpperCase());
        }
        return result;
    }

    private static void applyLine(StyledDocument document, String line, int offset, SettingsManager settings, Set<String> equates) {
        int commentStart = findCommentStart(line);
        int codeEnd = commentStart >= 0 ? commentStart : line.length();

        if (commentStart >= 0) {
            applyColor(document, offset + commentStart, line.length() - commentStart,
                    Theme.comment(settings), false);
        }

        List<int[]> strings = findStrings(line, codeEnd);
        for (int[] range : strings) {
            applyColor(document, offset + range[0], range[1] - range[0], Theme.string(settings), false);
            for (int i = range[0] + 1; i + 1 < range[1] - 1; i++) {
                if (line.charAt(i) != '\\') continue;
                int length = (line.charAt(i + 1) == 'x' || line.charAt(i + 1) == 'X') && i + 3 < range[1] - 1 ? 4 : 2;
                applyColor(document, offset + i, length, settings.getColor("escape"), false);
                i += length - 1;
            }
        }

        String code = line.substring(0, codeEnd);
        String upperCode = code.toUpperCase();
        int equIndex = upperCode.indexOf(" EQU ");
        String equateName = equIndex > 0 ? code.substring(0, equIndex).trim() : null;
        Matcher matcher = TOKEN.matcher(code);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            if (insideAny(start, strings)) continue;

            String token = matcher.group();
            String upper = token.toUpperCase();
            Color color = null;
            boolean bold = true;

            if (equates.contains(upper)) color = settings.getColor("equate");
            else if (token.startsWith(".") && SECTIONS.contains(upper)) color = Theme.section(settings);
            else if (DIRECTIVES.contains(upper)) color = Theme.directive(settings);
            else if (CALL_JUMP.contains(upper)) color = Theme.callJump(settings);
            else if (STACK.contains(upper)) color = Theme.stack(settings);
            else if (REGISTERS.contains(upper)) color = Theme.register(settings);
            else if (INSTRUCTIONS.contains(upper)) color = Theme.instruction(settings);
            else if (isNumber(token)) color = Theme.immediate(settings);
            else bold = false;

            if (color != null) applyColor(document, offset + start, end - start, color, bold);
        }
    }

    private static int findCommentStart(String line) {
        boolean quoted = false;
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted && c == '\\') { i++; continue; }
            if (c == '\'' || c == '"') {
                if (!quoted) {
                    quoted = true;
                    quote = c;
                } else if (quote == c) {
                    quoted = false;
                }
            } else if (c == ';' && !quoted) return i;
        }
        return -1;
    }

    private static List<int[]> findStrings(String line, int end) {
        List<int[]> result = new ArrayList<>();
        boolean quoted = false;
        char quote = 0;
        int start = -1;
        for (int i = 0; i < end; i++) {
            char c = line.charAt(i);
            if (quoted && c == '\\') { i++; continue; }
            if (c == '\'' || c == '"') {
                if (!quoted) {
                    quoted = true;
                    quote = c;
                    start = i;
                } else if (quote == c) {
                    quoted = false;
                    result.add(new int[]{start, i + 1});
                }
            }
        }
        return result;
    }

    private static boolean insideAny(int position, List<int[]> ranges) {
        for (int[] range : ranges) {
            if (position >= range[0] && position < range[1]) return true;
        }
        return false;
    }

    private static boolean isNumber(String token) {
        return token.matches("(?i)(0x[0-9a-f]+|0h[0-9a-f]+|0b[01]+|\\d+)");
    }

    private static void applyColor(StyledDocument document, int start, int length, Color color, boolean bold) {
        if (length <= 0) return;
        String key = "vulcan-color-" + color.getRGB() + "-" + bold;
        Style style = document.getStyle(key);
        if (style == null) style = document.addStyle(key, null);
        StyleConstants.setForeground(style, color);
        StyleConstants.setBold(style, bold);
        document.setCharacterAttributes(start, length, style, false);
    }
}
