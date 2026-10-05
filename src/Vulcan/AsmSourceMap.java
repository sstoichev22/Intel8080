package Vulcan;

import Intel8080.assembler.OpcodeTable;
import Intel8080.cpu.Memory;

import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class AsmSourceMap {

    private static final List<String> INSTRUCTION_NAMES = OpcodeTable.OPCODES.getMnemonics().stream()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();

    private AsmSourceMap() {
    }

    /** Builds line-to-address and address-to-line mappings from assembly source and metadata. */
    public static ProgramInfo build(String source,
                                    int programStart,
                                    int programEnd,
                                    int codeStart,
                                    int codeEnd,
                                    int dataStart,
                                    int dataEnd) {
        Map<Integer, Integer> addressToLine = new HashMap<>();
        Map<Integer, Integer> lineToAddress = new HashMap<>();

        boolean sections = source.lines().anyMatch(line -> {
            String trimmed = stripComment(line).trim();
            return trimmed.equalsIgnoreCase(".code") || trimmed.equalsIgnoreCase(".data");
        });

        if (!sections) {
            buildSequential(source, codeStart >= 0 ? codeStart : programStart, addressToLine, lineToAddress);
        } else {
            buildSections(source,
                    codeStart >= 0 ? codeStart : Memory.PROGRAM_START,
                    dataStart,
                    addressToLine,
                    lineToAddress);
        }

        return new ProgramInfo(
                programStart,
                programEnd,
                codeStart,
                codeEnd,
                dataStart,
                dataEnd,
                addressToLine,
                lineToAddress
        );
    }

    private static void buildSequential(String source,
                                         int start,
                                         Map<Integer, Integer> addressToLine,
                                         Map<Integer, Integer> lineToAddress) {
        int pc = start;
        String[] lines = source.replace("\r", "").split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = stripComment(lines[i]).trim();
            if (line.isEmpty()) continue;

            int colon = line.indexOf(':');
            if (colon >= 0) {
                line = line.substring(colon + 1).trim();
                if (line.isEmpty()) continue;
            }

            String[] parts = line.split("\\s+", 2);
            String mnemonic = parts[0].toUpperCase();
            String operandText = parts.length > 1 ? parts[1].trim() : "";

            if (mnemonic.equals("ORG")) {
                Integer value = parseNumber(operandText);
                if (value != null) pc = value;
                continue;
            }

            if (mnemonic.equals("EQU")) continue;
            if (mnemonic.equals("END")) break;

            if (mnemonic.equals("DB")) {
                lineToAddress.putIfAbsent(i + 1, pc);
                int size = countDbBytes(operandText);
                for (int offset = 0; offset < size; offset++) addressToLine.put(pc + offset, i + 1);
                pc += size;
                continue;
            }

            if (mnemonic.equals("DW")) {
                lineToAddress.putIfAbsent(i + 1, pc);
                int size = countOperands(operandText) * 2;
                for (int offset = 0; offset < size; offset++) addressToLine.put(pc + offset, i + 1);
                pc += size;
                continue;
            }

            if (mnemonic.equals("DS")) {
                lineToAddress.putIfAbsent(i + 1, pc);
                Integer size = parseNumber(operandText);
                if (size != null) pc += Math.max(0, size);
                continue;
            }

            int opcode = instructionOpcode(line);
            if (opcode != -1) {
                int size = OpcodeTable.OPCODES.gets(opcode);
                lineToAddress.put(i + 1, pc);
                addressToLine.put(pc, i + 1);
                pc += size;
            }
        }
    }

    private static void buildSections(String source,
                                      int codeStart,
                                      int dataStart,
                                      Map<Integer, Integer> addressToLine,
                                      Map<Integer, Integer> lineToAddress) {
        int codePc = codeStart;
        int dataPc = dataStart >= 0 ? dataStart : codeStart;
        Section section = Section.CODE;

        String[] lines = source.replace("\r", "").split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String original = stripComment(lines[i]).trim();
            if (original.isEmpty()) continue;

            int colon = original.indexOf(':');
            if (colon >= 0) {
                original = original.substring(colon + 1).trim();
                if (original.isEmpty()) continue;
            }

            if (original.equalsIgnoreCase(".code")) {
                section = Section.CODE;
                continue;
            }

            if (original.equalsIgnoreCase(".data")) {
                section = Section.DATA;
                continue;
            }

            String[] parts = original.split("\\s+", 2);
            String mnemonic = parts[0].toUpperCase();
            String operandText = parts.length > 1 ? parts[1].trim() : "";

            if (mnemonic.equals("EQU")) continue;
            if (mnemonic.equals("END")) break;

            if (section == Section.CODE) {
                if (mnemonic.equals("ORG")) {
                    Integer value = parseNumber(operandText);
                    if (value != null) codePc = value;
                    continue;
                }

                int opcode = instructionOpcode(original);
                if (opcode != -1) {
                    int size = OpcodeTable.OPCODES.gets(opcode);
                    lineToAddress.put(i + 1, codePc);
                    addressToLine.put(codePc, i + 1);
                    codePc += size;
                }
            } else {
                if (mnemonic.equals("DB")) {
                    lineToAddress.put(i + 1, dataPc);
                    int size = countDbBytes(operandText);
                    for (int offset = 0; offset < size; offset++) {
                        addressToLine.put(dataPc + offset, i + 1);
                    }
                    dataPc += size;
                } else if (mnemonic.equals("DW")) {
                    lineToAddress.put(i + 1, dataPc);
                    int size = countOperands(operandText) * 2;
                    for (int offset = 0; offset < size; offset++) {
                        addressToLine.put(dataPc + offset, i + 1);
                    }
                    dataPc += size;
                } else if (mnemonic.equals("DS")) {
                    lineToAddress.put(i + 1, dataPc);
                    Integer size = parseNumber(operandText);
                    if (size != null) dataPc += Math.max(0, size);
                }
            }
        }
    }

    private enum Section {
        CODE,
        DATA
    }

    private static int instructionOpcode(String sourceLine) {
        String text = sourceLine.trim().replaceAll("\\s*,\\s*", ", ")
                .replaceAll("\\s+", " ").toUpperCase(java.util.Locale.ROOT);
        for (String candidate : INSTRUCTION_NAMES) {
            if (!text.startsWith(candidate)) continue;
            if (text.length() == candidate.length() || Character.isWhitespace(text.charAt(candidate.length())) || text.charAt(candidate.length()) == ',') {
                return OpcodeTable.OPCODES.geto(candidate);
            }
        }
        return -1;
    }


    private static String stripComment(String line) {
        boolean quoted = false;
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && quoted && i + 1 < line.length()) {
                i++;
                continue;
            }
            if (c == '\'' || c == '"') {
                if (!quoted) {
                    quoted = true;
                    quote = c;
                } else if (quote == c) {
                    quoted = false;
                }
            } else if (c == ';' && !quoted) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static int countDbBytes(String text) {
        int size = 0;
        boolean quoted = false;
        char quote = 0;
        int tokenStart = 0;

        for (int i = 0; i <= text.length(); i++) {
            char c = i == text.length() ? ',' : text.charAt(i);
            if (c == '\\' && quoted && i + 1 < text.length()) {
                i++;
                continue;
            }
            if (c == '\'' || c == '"') {
                if (!quoted) {
                    quoted = true;
                    quote = c;
                } else if (quote == c) {
                    quoted = false;
                }
            }
            if (c == ',' && !quoted) {
                String token = text.substring(tokenStart, i).trim();
                if (!token.isEmpty()) {
                    if (token.startsWith("\"") && token.endsWith("\"")) {
                        size += countStringBytes(token);
                    } else if (token.startsWith("'") && token.endsWith("'")) {
                        size++;
                    } else {
                        size++;
                    }
                }
                tokenStart = i + 1;
            }
        }

        return size;
    }

    private static int countStringBytes(String token) {
        int count = 0;
        int end = token.length() - 1;
        for (int i = 1; i < end; i++) {
            if (token.charAt(i) == '\\' && i + 1 < end) {
                char escaped = token.charAt(++i);
                if (escaped == 'x' || escaped == 'X') i = Math.min(end - 1, i + 2);
            }
            count++;
        }
        return count;
    }

    private static int countOperands(String text) {
        if (text.isBlank()) return 0;
        return text.split(",").length;
    }

    private static Integer parseNumber(String value) {
        String s = value.trim().replace("_", "");
        try {
            if (s.startsWith("-")) {
                Integer positive = parseNumber(s.substring(1));
                return positive == null ? null : -positive;
            }
            if (s.regionMatches(true, 0, "0x", 0, 2)) return Integer.parseInt(s.substring(2), 16);
            if (s.regionMatches(true, 0, "0h", 0, 2)) return Integer.parseInt(s.substring(2), 16);
            if (s.regionMatches(true, 0, "0b", 0, 2)) return Integer.parseInt(s.substring(2), 2);
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
