/**
 * Two-pass 8080 assembler. Symbol names are case-insensitive; normalize at declaration and lookup.
 * Earlier iterations failed duplicate EQU detection because declarations and references could use different case.
 */
package Intel8080.assembler;

import Intel8080.cpu.Memory;

import static Intel8080.assembler.Assembler.TokenType.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Assembler {

    private static char[] file;
    private static int i;
    private static int line;
    private static int lineStart;
    private static List<Token> tokens;

    private static int lastProgramStart = -1;
    private static int lastProgramEnd = -1;
    private static int lastCodeStart = -1;
    private static int lastCodeEnd = -1;
    private static int lastDataStart = -1;
    private static int lastDataEnd = -1;
    private static final List<String> lastWarnings = new ArrayList<>();

    /** Assembles source into a complete 64 KiB memory image. */
    public static synchronized byte[] Assemble(String source) {
        return assembleWithInfo(source).image();
    }

    /** Returns an image and its metadata as one atomic assembly result. */
    public static synchronized AssemblyResult assembleWithInfo(String source) {
        file = (source == null ? "" : source).replace("\r", "").toCharArray();
        i = 0;
        line = 0;
        lineStart = 0;
        lastProgramStart = -1;
        lastProgramEnd = -1;
        lastCodeStart = -1;
        lastCodeEnd = -1;
        lastDataStart = -1;
        lastDataEnd = -1;
        lastWarnings.clear();

        List<Token> lexed = a_lexer();
        List<Statement> stmts = a_parse(lexed);
        validateSymbolDeclarations(stmts);

        Map<String, Integer> labels = new HashMap<>();
        Map<String, Integer> equates = new HashMap<>();

        a_addr_res_pass(stmts, labels, equates);
        byte[] image = a_code_gen_pass(stmts, labels, equates);
        return new AssemblyResult(image,
                new AssemblyInfo(lastProgramStart, lastProgramEnd,
                        lastCodeStart, lastCodeEnd, lastDataStart, lastDataEnd),
                List.copyOf(lastWarnings));
    }

    /** Returns the first address written by the most recent assembly, or -1 when empty. */
    public static synchronized int getLastProgramStart() {
        return lastProgramStart;
    }

    /** Returns the last address written by the most recent assembly, or -1 when empty. */
    public static synchronized int getLastProgramEnd() {
        return lastProgramEnd;
    }

    /** Returns the first code address from the most recent assembly, or -1 when absent. */
    public static synchronized int getLastCodeStart() {
        return lastCodeStart;
    }

    /** Returns the last code address from the most recent assembly, or -1 when absent. */
    public static synchronized int getLastCodeEnd() {
        return lastCodeEnd;
    }

    /** Returns the first data address from the most recent assembly, or -1 when absent. */
    public static synchronized int getLastDataStart() {
        return lastDataStart;
    }

    /** Returns the last data address from the most recent assembly, or -1 when absent. */
    public static synchronized int getLastDataEnd() {
        return lastDataEnd;
    }

    /** Returns an immutable copy of warnings from the most recent assembly. */
    public static synchronized List<String> getLastWarnings() {
        return List.copyOf(lastWarnings);
    }

    /** Returns address metadata from the most recent assembly. */
    public static synchronized AssemblyInfo getLastAssemblyInfo() {
        return new AssemblyInfo(
                lastProgramStart, lastProgramEnd,
                lastCodeStart, lastCodeEnd,
                lastDataStart, lastDataEnd);
    }

    /** Immutable address ranges reported by a completed assembly. */
    public record AssemblyInfo(int programStart, int programEnd,
                               int codeStart, int codeEnd,
                               int dataStart, int dataEnd) {}

    /** Immutable assembly output; its image is copied at construction and access boundaries. */
    public record AssemblyResult(byte[] image, AssemblyInfo info, List<String> warnings) {
        public AssemblyResult {
            image = image.clone();
            warnings = List.copyOf(warnings);
        }

        @Override public byte[] image() { return image.clone(); }
    }

    /** Checks all symbol declarations before either address resolution or code generation. */
    private static void validateSymbolDeclarations(List<Statement> statements) {
        Map<String, SymbolDefinition> definitions = new LinkedHashMap<>();
        for (Statement statement : statements) {
            if (statement instanceof LabelStatement label) {
                registerSymbolDefinition(definitions, label.name, "label", label.line, label.column);
            } else if (statement instanceof DirectiveStatement directiveStatement) {
                Directive directive = directiveStatement.directive;
                if (directive instanceof EquDirective equate) {
                    registerSymbolDefinition(definitions, equate.name, "equate", equate.line, equate.column);
                } else if (directive instanceof EndDirective) {
                    break;
                }
            }
        }

        for (Statement statement : statements) {
            if (statement instanceof DirectiveStatement directiveStatement
                    && directiveStatement.directive instanceof EndDirective) break;
            validateStatementSymbols(statement, definitions);
        }
    }

    /** Records one case-insensitive symbol and rejects duplicate or conflicting declarations. */
    private static void registerSymbolDefinition(Map<String, SymbolDefinition> definitions,
                                                 String name, String kind, int line, int column) {
        String normalized = name.toUpperCase(Locale.ROOT);
        SymbolDefinition previous = definitions.putIfAbsent(normalized,
                new SymbolDefinition(name, kind, line, column));
        if (previous != null) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "Duplicate %s '%s'; '%s' was first declared as a %s at line %d, character %d. "
                            + "Repeated declaration at line %d, character %d.",
                    kind, name, previous.name, previous.kind, previous.line, previous.column, line, column));
        }
    }

    /** Checks every parsed expression against the declaration table before layout begins. */
    private static void validateStatementSymbols(Statement statement,
                                                Map<String, SymbolDefinition> definitions) {
        if (statement instanceof InstructionStatement instruction) {
            validateExpressionSymbols(instruction.arg, definitions);
            return;
        }
        if (!(statement instanceof DirectiveStatement directiveStatement)) return;

        Directive directive = directiveStatement.directive;
        if (directive instanceof EquDirective equate) {
            validateExpressionSymbols(equate.value, definitions);
        } else if (directive instanceof OrgDirective origin) {
            validateExpressionSymbols(origin.address, definitions);
        } else if (directive instanceof DBDirective bytes) {
            for (Expr value : bytes.values) validateExpressionSymbols(value, definitions);
        } else if (directive instanceof DWDirective words) {
            for (Expr value : words.values) validateExpressionSymbols(value, definitions);
        } else if (directive instanceof DSDirective reserve) {
            validateExpressionSymbols(reserve.size, definitions);
        }
    }

    /** Rejects undeclared symbols and recursively checks compound expressions. */
    private static void validateExpressionSymbols(Expr expression,
                                                  Map<String, SymbolDefinition> definitions) {
        if (expression instanceof SymbolExpr symbol) {
            if (!definitions.containsKey(symbol.name)) {
                throw new IllegalArgumentException(String.format(Locale.ROOT,
                        "Unknown symbol '%s' at line %d, character %d.",
                        symbol.name, symbol.line, symbol.column));
            }
        } else if (expression instanceof BinaryExpr binary) {
            validateExpressionSymbols(binary.left, definitions);
            validateExpressionSymbols(binary.right, definitions);
        }
    }

    private static byte[] a_code_gen_pass(List<Statement> stmts,
                                           Map<String, Integer> labels,
                                           Map<String, Integer> equates) {
        if (hasSections(stmts)) {
            return a_code_gen_sections(stmts, labels, equates);
        }

        byte[] program = new byte[Memory.MEM_SIZE];
        int pc = Memory.PROGRAM_START;

        for (Statement stmt : stmts) {
            if (stmt instanceof InstructionStatement is) {
                int opcode = OpcodeTable.OPCODES.geto(is.mnemonic);
                int size = OpcodeTable.OPCODES.gets(opcode);
                markCodeRange(pc, size);
                program[pc++] = (byte) (opcode & 0xFF);

                if (size == 2) {
                    int arg = a_eval_expr(is.arg, labels, equates);
                    requireRange(arg, -128, 255, "8-bit operand for " + is.mnemonic);
                    program[pc++] = (byte) (arg & 0xFF);
                } else if (size == 3) {
                    int arg = a_eval_expr(is.arg, labels, equates);
                    requireRange(arg, -32768, 65535, "16-bit operand for " + is.mnemonic);
                    program[pc++] = (byte) (arg & 0xFF);
                    program[pc++] = (byte) ((arg >> 8) & 0xFF);
                }
            } else if (stmt instanceof DirectiveStatement ds) {
                if (ds.directive instanceof EquDirective) {
                    continue;
                } else if (ds.directive instanceof OrgDirective od) {
                    pc = a_eval_expr(od.address, labels, equates);
                    checkAddress(pc, "ORG");
                } else if (ds.directive instanceof DBDirective db) {
                    for (Expr expr : db.values) {
                        if (expr instanceof StringExpr se) {
                            for (char c : se.str.toCharArray()) {
                                markCodeRange(pc, 1);
                                program[pc++] = (byte) (c & 0xFF);
                            }
                        } else {
                            markCodeRange(pc, 1);
                            program[pc++] = (byte) (a_eval_expr(expr, labels, equates) & 0xFF);
                        }
                    }
                } else if (ds.directive instanceof DWDirective dw) {
                    for (Expr expr : dw.values) {
                        int val = a_eval_expr(expr, labels, equates);
                        markCodeRange(pc, 2);
                        program[pc++] = (byte) (val & 0xFF);
                        program[pc++] = (byte) ((val >> 8) & 0xFF);
                    }
                } else if (ds.directive instanceof DSDirective dd) {
                    int size = a_eval_expr(dd.size, labels, equates);
                    if (size < 0) throw new RuntimeException("Negative DS size: " + size);
                    if (size > 0) markCodeRange(pc, size);
                    pc += size;
                    checkAddress(pc, "DS");
                } else if (ds.directive instanceof EndDirective) {
                    break;
                }
            }
        }

        return program;
    }

    private static byte[] a_code_gen_sections(List<Statement> stmts,
                                               Map<String, Integer> labels,
                                               Map<String, Integer> equates) {
        SectionLayout layout = calculateSectionLayout(stmts, new HashMap<>(equates));
        byte[] program = new byte[Memory.MEM_SIZE];
        int codePc = Memory.PROGRAM_START;
        int dataPc = layout.dataStart;
        Section section = Section.CODE;

        for (Statement stmt : stmts) {
            if (stmt instanceof SectionStatement ss) {
                section = ss.section;
                continue;
            }

            if (stmt instanceof LabelStatement) {
                continue;
            }

            if (stmt instanceof InstructionStatement is) {
                if (section != Section.CODE) {
                    throw new RuntimeException("Instruction " + is.mnemonic + " cannot appear inside .data.");
                }

                int opcode = OpcodeTable.OPCODES.geto(is.mnemonic);
                int size = OpcodeTable.OPCODES.gets(opcode);
                markCodeRange(codePc, size);
                program[codePc++] = (byte) (opcode & 0xFF);

                if (size == 2) {
                    int arg = a_eval_expr(is.arg, labels, equates);
                    requireRange(arg, -128, 255, "8-bit operand for " + is.mnemonic);
                    program[codePc++] = (byte) (arg & 0xFF);
                } else if (size == 3) {
                    int arg = a_eval_expr(is.arg, labels, equates);
                    requireRange(arg, -32768, 65535, "16-bit operand for " + is.mnemonic);
                    program[codePc++] = (byte) (arg & 0xFF);
                    program[codePc++] = (byte) ((arg >> 8) & 0xFF);
                }
                continue;
            }

            if (!(stmt instanceof DirectiveStatement ds)) {
                continue;
            }

            if (ds.directive instanceof EquDirective || ds.directive instanceof EndDirective) {
                if (ds.directive instanceof EndDirective) break;
                continue;
            }

            if (ds.directive instanceof OrgDirective od) {
                if (section != Section.CODE) {
                    throw new RuntimeException("ORG is only supported inside .code.");
                }
                codePc = a_eval_expr(od.address, labels, equates);
                checkAddress(codePc, "ORG");
                continue;
            }

            if (section == Section.CODE) {
                throw new RuntimeException("Data directive found inside .code. Move it into .data.");
            }

            if (ds.directive instanceof DBDirective db) {
                for (Expr expr : db.values) {
                    if (expr instanceof StringExpr se) {
                        for (char c : se.str.toCharArray()) {
                            markDataRange(dataPc, 1);
                            program[dataPc++] = (byte) (c & 0xFF);
                        }
                    } else {
                        markDataRange(dataPc, 1);
                        program[dataPc++] = (byte) (a_eval_expr(expr, labels, equates) & 0xFF);
                    }
                }
            } else if (ds.directive instanceof DWDirective dw) {
                for (Expr expr : dw.values) {
                    int value = a_eval_expr(expr, labels, equates);
                    markDataRange(dataPc, 2);
                    program[dataPc++] = (byte) (value & 0xFF);
                    program[dataPc++] = (byte) ((value >> 8) & 0xFF);
                }
            } else if (ds.directive instanceof DSDirective dd) {
                int size = a_eval_expr(dd.size, labels, equates);
                if (size < 0) throw new RuntimeException("Negative DS size: " + size);
                if (size > 0) markDataRange(dataPc, size);
                dataPc += size;
                checkAddress(dataPc, "DS");
            }
        }

        return program;
    }

    private static void a_addr_res_pass(List<Statement> stmts,
                                        Map<String, Integer> labels,
                                        Map<String, Integer> equates) {
        if (hasSections(stmts)) {
            a_addr_res_sections(stmts, labels, equates);
            return;
        }

        int pc = Memory.PROGRAM_START;

        for (Statement stmt : stmts) {
            if (stmt instanceof InstructionStatement is) {
                pc += OpcodeTable.OPCODES.gets(OpcodeTable.OPCODES.geto(is.mnemonic));
            } else if (stmt instanceof DirectiveStatement ds) {
                if (ds.directive instanceof EquDirective ed) {
                    equates.put(ed.name.toUpperCase(Locale.ROOT), a_eval_expr(ed.value, equates));
                } else if (ds.directive instanceof OrgDirective od) {
                    pc = a_eval_expr(od.address, equates);
                    checkAddress(pc, "ORG");
                } else if (ds.directive instanceof DBDirective db) {
                    pc += a_eval_size_db(db.values);
                } else if (ds.directive instanceof DWDirective dw) {
                    pc += dw.values.size() * 2;
                } else if (ds.directive instanceof DSDirective dd) {
                    int size = a_eval_expr(dd.size, equates);
                    if (size < 0) throw new RuntimeException("Negative DS size: " + size);
                    pc += size;
                } else if (ds.directive instanceof EndDirective) {
                    break;
                }
            } else if (stmt instanceof LabelStatement ls) {
                labels.put(ls.name.toUpperCase(Locale.ROOT), pc);
            }
        }
    }

    private static void a_addr_res_sections(List<Statement> stmts,
                                            Map<String, Integer> labels,
                                            Map<String, Integer> equates) {
        SectionLayout layout = calculateSectionLayout(stmts, equates);
        int codePc = Memory.PROGRAM_START;
        int dataPc = layout.dataStart;
        Section section = Section.CODE;

        for (Statement stmt : stmts) {
            if (stmt instanceof SectionStatement ss) {
                section = ss.section;
                continue;
            }

            if (stmt instanceof LabelStatement ls) {
                labels.put(ls.name.toUpperCase(Locale.ROOT), section == Section.CODE ? codePc : dataPc);
                continue;
            }

            if (stmt instanceof InstructionStatement is) {
                if (section != Section.CODE) {
                    throw new RuntimeException("Instruction " + is.mnemonic + " cannot appear inside .data.");
                }
                codePc += OpcodeTable.OPCODES.gets(OpcodeTable.OPCODES.geto(is.mnemonic));
                continue;
            }

            if (!(stmt instanceof DirectiveStatement ds)) continue;

            if (ds.directive instanceof EquDirective ed) {
                continue;
            }

            if (ds.directive instanceof EndDirective) break;

            if (ds.directive instanceof OrgDirective od) {
                if (section != Section.CODE) {
                    throw new RuntimeException("ORG is only supported inside .code.");
                }
                codePc = a_eval_expr(od.address, equates);
                checkAddress(codePc, "ORG");
                continue;
            }

            if (section == Section.CODE) {
                throw new RuntimeException("Data directive found inside .code. Move it into .data.");
            }

            if (ds.directive instanceof DBDirective db) {
                dataPc += a_eval_size_db(db.values);
            } else if (ds.directive instanceof DWDirective dw) {
                dataPc += dw.values.size() * 2;
            } else if (ds.directive instanceof DSDirective dd) {
                int size = a_eval_expr(dd.size, equates);
                if (size < 0) throw new RuntimeException("Negative DS size: " + size);
                dataPc += size;
            }
        }
    }

    private static SectionLayout calculateSectionLayout(List<Statement> stmts,
                                                        Map<String, Integer> equates) {
        int codePc = Memory.PROGRAM_START;
        int codeStart = -1;
        int codeEnd = -1;
        int dataSize = 0;
        Section section = Section.CODE;

        for (Statement stmt : stmts) {
            if (stmt instanceof SectionStatement ss) {
                section = ss.section;
                continue;
            }

            if (stmt instanceof LabelStatement) continue;

            if (stmt instanceof InstructionStatement is) {
                if (section != Section.CODE) {
                    throw new RuntimeException("Instruction " + is.mnemonic + " cannot appear inside .data.");
                }

                int size = OpcodeTable.OPCODES.gets(OpcodeTable.OPCODES.geto(is.mnemonic));
                if (size <= 0) throw new RuntimeException("Invalid instruction size for " + is.mnemonic + ".");

                if (codeStart == -1 || codePc < codeStart) codeStart = codePc;
                codeEnd = Math.max(codeEnd, codePc + size - 1);
                codePc += size;
                continue;
            }

            if (!(stmt instanceof DirectiveStatement ds)) continue;

            if (ds.directive instanceof EquDirective ed) {
                equates.put(ed.name.toUpperCase(Locale.ROOT), a_eval_expr(ed.value, equates));
                continue;
            }

            if (ds.directive instanceof EndDirective) break;

            if (ds.directive instanceof OrgDirective od) {
                if (section != Section.CODE) {
                    throw new RuntimeException("ORG is only supported inside .code.");
                }
                codePc = a_eval_expr(od.address, equates);
                checkAddress(codePc, "ORG");
                continue;
            }

            if (section == Section.CODE) {
                throw new RuntimeException("Data directive found inside .code. Move it into .data.");
            }

            if (ds.directive instanceof DBDirective db) {
                dataSize += a_eval_size_db(db.values);
            } else if (ds.directive instanceof DWDirective dw) {
                dataSize += dw.values.size() * 2;
            } else if (ds.directive instanceof DSDirective dd) {
                int size = a_eval_expr(dd.size, equates);
                if (size < 0) throw new RuntimeException("Negative DS size: " + size);
                dataSize += size;
            }
        }

        int dataStart = -1;
        int dataEnd = -1;
        if (dataSize > 0) {
            dataStart = codeEnd >= 0 ? codeEnd + 1 : Memory.PROGRAM_START;
            dataEnd = dataStart + dataSize - 1;
            if (dataEnd >= Memory.MEM_SIZE) {
                throw new RuntimeException(String.format(
                        "Data section is too large: 0x%04X - 0x%04X",
                        dataStart & 0xFFFF,
                        dataEnd & 0xFFFF
                ));
            }
        }

        return new SectionLayout(codeStart, codeEnd, dataStart, dataEnd);
    }

    private static boolean hasSections(List<Statement> stmts) {
        for (Statement stmt : stmts) {
            if (stmt instanceof SectionStatement) return true;
        }
        return false;
    }

    private static int a_eval_size_db(List<Expr> exprs) {
        int size = 0;
        for (Expr e : exprs) {
            if (e instanceof StringExpr se) size += se.str.length();
            else size++;
        }
        return size;
    }

    @SafeVarargs
    private static int a_eval_expr(Expr expr, Map<String, Integer>... maps) {
        Map<String, Integer> defined = new HashMap<>();
        for (Map<String, Integer> map : maps) defined.putAll(map);

        if (expr instanceof NumberExpr ne) return ne.value;
        if (expr instanceof CharExpr ce) return ce.value;
        if (expr instanceof SymbolExpr se) {
            if (defined.containsKey(se.name)) return defined.get(se.name);
            throw new RuntimeException("Unknown symbol: " + se.name + ". At line "
                    + se.line + ", character " + se.column + ".");
        }
        if (expr instanceof StringExpr) {
            throw new RuntimeException("String literals cannot be used as numeric expressions. At line " + ((StringExpr) expr).line + ", character " + ((StringExpr) expr).column + ".");
        }
        if (expr instanceof BinaryExpr be) {
            int l = a_eval_expr(be.left, defined);
            int r = a_eval_expr(be.right, defined);
            if (be.operator == Operator.DIV && r == 0) {
                throw new RuntimeException("Division by zero in expression.");
            }
            return switch (be.operator) {
                case ADD -> l + r;
                case SUB -> l - r;
                case MUL -> l * r;
                case DIV -> l / r;
            };
        }
        throw new RuntimeException("Could not evaluate expression.");
    }

    private static void requireRange(int value, int min, int max, String description) {
        if (value < min || value > max) {
            lastWarnings.add(description + " overflow/underflow: " + value + " (will wrap).");
        }
    }

    private static void checkAddress(int address, String operation) {
        if (address < 0 || address >= Memory.MEM_SIZE) {
            throw new RuntimeException(String.format(
                    "%s address out of range: 0x%04X.",
                    operation,
                    address & 0xFFFF
            ));
        }
    }

    private static void markRange(int address, int size) {
        if (size <= 0) return;
        if (address < 0 || address + size > Memory.MEM_SIZE) {
            throw new RuntimeException(String.format(
                    "Program address out of range: 0x%04X - 0x%04X",
                    address & 0xFFFF,
                    (address + size - 1) & 0xFFFF
            ));
        }

        if (lastProgramStart == -1 || address < lastProgramStart) lastProgramStart = address;

        int end = address + size - 1;
        if (end > lastProgramEnd) lastProgramEnd = end;
    }

    private static void markCodeRange(int address, int size) {
        markRange(address, size);
        if (size <= 0) return;
        if (lastCodeStart == -1 || address < lastCodeStart) lastCodeStart = address;
        int end = address + size - 1;
        if (end > lastCodeEnd) lastCodeEnd = end;
    }

    private static void markDataRange(int address, int size) {
        markRange(address, size);
        if (size <= 0) return;
        if (lastDataStart == -1 || address < lastDataStart) lastDataStart = address;
        int end = address + size - 1;
        if (end > lastDataEnd) lastDataEnd = end;
    }

    private sealed interface Statement permits InstructionStatement, DirectiveStatement, LabelStatement, SectionStatement {}
    private record InstructionStatement(String mnemonic, Expr arg) implements Statement {}
    private record DirectiveStatement(Directive directive) implements Statement {}
    private record LabelStatement(String name, int line, int column) implements Statement {}
    private record SymbolDefinition(String name, String kind, int line, int column) {}
    private record SectionStatement(Section section) implements Statement {}

    private enum Section {
        CODE,
        DATA
    }

    private record SectionLayout(int codeStart, int codeEnd, int dataStart, int dataEnd) {}

    private sealed interface Directive permits EquDirective, OrgDirective, DBDirective, DWDirective, DSDirective, EndDirective {}
    private record EquDirective(String name, Expr value, int line, int column) implements Directive {}
    private record OrgDirective(Expr address) implements Directive {}
    private record DBDirective(List<Expr> values) implements Directive {}
    private record DWDirective(List<Expr> values) implements Directive {}
    private record DSDirective(Expr size) implements Directive {}
    private record EndDirective() implements Directive {}

    private sealed interface Expr permits StringExpr, CharExpr, NumberExpr, SymbolExpr, BinaryExpr {}
    private record StringExpr(String str, int line, int column) implements Expr {}
    private record CharExpr(int value, int line, int column) implements Expr {}
    private record NumberExpr(int value) implements Expr {}
    private record SymbolExpr(String name, int line, int column) implements Expr {}
    private record BinaryExpr(Expr left, Operator operator, Expr right) implements Expr {}

    private enum Operator {
        ADD,
        SUB,
        MUL,
        DIV
    }

    private static List<Statement> a_parse(List<Token> source) {
        tokens = rm_mul_sep(source);
        i = 0;

        List<Statement> stmts = new ArrayList<>();
        while (!eof()) stmts.add(p_parse_statement());
        return stmts;
    }

    private static List<Token> rm_mul_sep(List<Token> source) {
        List<Token> result = new ArrayList<>();
        int index = 0;

        while (index < source.size() && source.get(index).type == SEPARATOR) index++;

        boolean lastSep = false;
        for (; index < source.size(); index++) {
            Token token = source.get(index);
            if (token.type == SEPARATOR) {
                if (!lastSep) {
                    result.add(token);
                    lastSep = true;
                }
            } else {
                result.add(token);
                lastSep = false;
            }
        }

        return result;
    }

    private static Statement p_parse_statement() {
        if (match(TEXT, COLON)) return p_parse_label();
        if (match(SECTION)) return p_parse_section();
        if (match(TEXT, PREPROCESSOR_DIRECTIVE) || match(PREPROCESSOR_DIRECTIVE)) return p_parse_directive();
        if (match(TEXT)) return p_parse_instruction();

        throw parseError("Unexpected token while parsing statement: " + currentToken());
    }

    private static Statement p_parse_section() {
        Token token = consume(SECTION);
        Section section = switch (token.text.toUpperCase()) {
            case ".CODE" -> Section.CODE;
            case ".DATA" -> Section.DATA;
            default -> throw parseError("Unknown section: " + token.text);
        };

        if (!match(SEPARATOR)) {
            throw parseError("Section " + token.text + " must be on its own line.");
        }
        consume(SEPARATOR);
        return new SectionStatement(section);
    }

    private static Statement p_parse_instruction() {
        String first = consume(TEXT).text.toUpperCase(Locale.ROOT);
        String mnemonic = first;
        boolean consumedOperandInMnemonic = false;

        if (!match(SEPARATOR)) {
            String second = tokenText(0);
            if (second != null) {
                String pairCandidate = first + " " + second;
                if (OpcodeTable.OPCODES.geto(pairCandidate) != -1) {
                    consume();
                    mnemonic = pairCandidate;
                } else if (peekType(1) == COMMA) {
                    String third = tokenText(2);
                    String moveCandidate = third == null ? null : first + " " + second + ", " + third;
                    if (moveCandidate != null && OpcodeTable.OPCODES.geto(moveCandidate) != -1) {
                        consume();
                        consume(COMMA);
                        consume();
                        mnemonic = moveCandidate;
                        consumedOperandInMnemonic = true;
                    }
                }
            }
        }

        if (OpcodeTable.OPCODES.geto(mnemonic) == -1) {
            throw parseError("Unknown instruction: " + mnemonic);
        }

        Expr arg = null;
        if (match(COMMA)) {
            consume(COMMA);
            arg = p_parse_expr();
        } else if (!match(SEPARATOR) && !consumedOperandInMnemonic) {
            int opcode = OpcodeTable.OPCODES.geto(mnemonic);
            int size = OpcodeTable.OPCODES.gets(opcode);
            if (opcode != -1 && size > 1) {
                arg = p_parse_expr();
            } else {
                throw parseError("Unexpected tokens after instruction " + mnemonic + ".");
            }
        }

        consume(SEPARATOR);
        return new InstructionStatement(mnemonic, arg);
    }

    private static String tokenText(int offset) {
        if (i + offset >= tokens.size()) return null;
        return tokens.get(i + offset).text;
    }

    private static TokenType peekType(int offset) {
        if (i + offset >= tokens.size()) return null;
        return tokens.get(i + offset).type;
    }

    private static Statement p_parse_directive() {
        if (match(TEXT, PREPROCESSOR_DIRECTIVE)) {
            Token nameToken = consume(TEXT);
            String name = nameToken.text.toUpperCase(Locale.ROOT);
            consume(PREPROCESSOR_DIRECTIVE);
            Expr expr = p_parse_expr();
            consume(SEPARATOR);
            return new DirectiveStatement(new EquDirective(name, expr, nameToken.lineNumber, nameToken.column));
        }

        Token token = consume(PREPROCESSOR_DIRECTIVE);
        String dir = token.text;

        return switch (dir) {
            case "ORG" -> {
                Expr expr = p_parse_expr();
                consume(SEPARATOR);
                yield new DirectiveStatement(new OrgDirective(expr));
            }
            case "DB" -> {
                List<Expr> exprs = new ArrayList<>();
                if (match(SEPARATOR)) throw parseError("DB requires at least one value.");
                exprs.add(p_parse_expr());
                while (!match(SEPARATOR)) {
                    consume(COMMA);
                    exprs.add(p_parse_expr());
                }
                consume(SEPARATOR);
                yield new DirectiveStatement(new DBDirective(exprs));
            }
            case "DW" -> {
                List<Expr> exprs = new ArrayList<>();
                if (match(SEPARATOR)) throw parseError("DW requires at least one value.");
                exprs.add(p_parse_expr());
                while (!match(SEPARATOR)) {
                    consume(COMMA);
                    exprs.add(p_parse_expr());
                }
                consume(SEPARATOR);
                yield new DirectiveStatement(new DWDirective(exprs));
            }
            case "DS" -> {
                Expr expr = p_parse_expr();
                consume(SEPARATOR);
                yield new DirectiveStatement(new DSDirective(expr));
            }
            case "END" -> {
                if (!match(SEPARATOR)) throw parseError("END cannot have operands.");
                consume(SEPARATOR);
                yield new DirectiveStatement(new EndDirective());
            }
            default -> throw parseError("Unknown directive: " + dir);
        };
    }

    private static Statement p_parse_label() {
        Token labelToken = consume(TEXT);
        String label = labelToken.text.toUpperCase(Locale.ROOT);
        consume(COLON);
        if (match(SEPARATOR)) consume(SEPARATOR);
        return new LabelStatement(label, labelToken.lineNumber, labelToken.column);
    }

    private static Token consume(TokenType type) {
        if (eof()) {
            throw parseError("Unexpected end of input; expected " + type + ".");
        }
        if (!match(type)) {
            throw parseError("Expected " + type + " but found " + currentToken() + ".");
        }
        return tokens.get(i++);
    }

    private static Token consume() {
        if (eof()) throw parseError("Unexpected end of input.");
        return tokens.get(i++);
    }

    private static boolean match(TokenType... types) {
        if (i + types.length > tokens.size()) return false;
        for (int j = 0; j < types.length; j++) {
            if (tokens.get(i + j).type != types[j]) return false;
        }
        return true;
    }

    private static Token peek(int p) {
        if (i + p < 0 || i + p >= tokens.size()) {
            throw parseError("Unexpected end of input while looking ahead.");
        }
        return tokens.get(i + p);
    }

    private static Token currentToken() {
        return eof() ? new Token(SEPARATOR, "<eof>", line + 1, 1) : tokens.get(i);
    }

    private static RuntimeException parseError(String message) {
        Token token = currentToken();
        return new RuntimeException(
                message + " At line " + token.lineNumber + ", character " + token.column + "."
        );
    }

    private static boolean eof() {
        return i >= tokens.size();
    }

    private static Expr p_parse_expr() {
        return p_parse_add();
    }

    private static Expr p_parse_add() {
        Expr expr = p_parse_mul();
        while (match(ADD) || match(SUB)) {
            TokenType op = match(ADD) ? consume(ADD).type : consume(SUB).type;
            Expr right = p_parse_mul();
            expr = new BinaryExpr(expr, op == ADD ? Operator.ADD : Operator.SUB, right);
        }
        return expr;
    }

    private static Expr p_parse_mul() {
        Expr expr = p_parse_primary();
        while (match(MUL) || match(DIV)) {
            TokenType op = match(MUL) ? consume(MUL).type : consume(DIV).type;
            Expr right = p_parse_primary();
            expr = new BinaryExpr(expr, op == MUL ? Operator.MUL : Operator.DIV, right);
        }
        return expr;
    }

    private static Expr p_parse_primary() {
        if (match(STRING)) { Token t = consume(STRING); return new StringExpr(t.text, t.lineNumber, t.column); }
        if (match(CHAR)) { Token t = consume(CHAR); return new CharExpr(p_char(t.text), t.lineNumber, t.column); }
        if (match(LITERAL)) return new NumberExpr(p_eval(consume(LITERAL).text));
        if (match(TEXT)) {
            Token symbol = consume(TEXT);
            return new SymbolExpr(symbol.text.toUpperCase(Locale.ROOT), symbol.lineNumber, symbol.column);
        }
        throw parseError("Expected a number, symbol, or string in expression, found " + currentToken() + ".");
    }

    private static int p_eval(String literal) {
        if (literal.length() <= 2) {
            if (p_dec_num(literal)) return Integer.parseInt(literal);
            throw new RuntimeException("Unknown literal: " + literal + ".");
        }

        char first = literal.charAt(0);
        char second = literal.charAt(1);

        if (first == '0' && (second == 'B' || second == 'H' || second == 'X')) {
            if (second == 'B') return p_bin_num(literal);
            return p_hex_num(literal);
        }

        if (p_dec_num(literal)) return Integer.parseInt(literal);
        throw new RuntimeException("Unknown literal: " + literal + ".");
    }

    private static int p_bin_num(String bin) {
        String value = bin.substring(2);
        if (value.isEmpty()) throw new RuntimeException("Invalid binary literal: " + bin + ".");

        int sum = 0;
        for (int j = value.length() - 1, multiplier = 1; j >= 0; j--, multiplier *= 2) {
            if (!bin(value.charAt(j))) throw new RuntimeException("Invalid binary literal: " + bin + ".");
            sum += p_bin_val(value.charAt(j)) * multiplier;
        }
        return sum;
    }

    private static int p_bin_val(char c) {
        return c - '0';
    }

    private static boolean bin(char c) {
        return c == '0' || c == '1';
    }

    private static int p_hex_num(String hex) {
        String value = hex.substring(2);
        if (value.isEmpty()) throw new RuntimeException("Invalid hexadecimal literal: " + hex + ".");

        int sum = 0;
        for (int j = value.length() - 1, multiplier = 1; j >= 0; j--, multiplier *= 16) {
            if (!hex(value.charAt(j))) throw new RuntimeException("Invalid hexadecimal literal: " + hex + ".");
            sum += p_hex_val(value.charAt(j)) * multiplier;
        }
        return sum;
    }

    private static int p_hex_val(char c) {
        char upper = Character.toUpperCase(c);
        if (upper >= '0' && upper <= '9') return upper - '0';
        if (upper >= 'A' && upper <= 'F') return upper - 'A' + 10;
        return -1;
    }

    private static boolean hex(char c) {
        char upper = Character.toUpperCase(c);
        return (upper >= '0' && upper <= '9') || (upper >= 'A' && upper <= 'F');
    }

    private static boolean p_dec_num(String dec) {
        try {
            Integer.parseInt(dec);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    enum TokenType {
        PREPROCESSOR_DIRECTIVE,
        SECTION,
        TEXT,
        LITERAL,
        CHAR,
        STRING,
        COLON,
        COMMA,
        ADD,
        SUB,
        MUL,
        DIV,
        SEPARATOR
    }

    private record Token(TokenType type, String text, int lineNumber, int column) {}

    private static List<Token> a_lexer() {
        List<Token> result = new ArrayList<>();

        while (i < file.length) {
            if (let(file[i]) || file[i] == '_') t_word(result);
            else if (dig(file[i])) t_num(result);
            else if (file[i] == '\'') t_char(result);
            else if (file[i] == '"') t_str(result);
            else if (file[i] == '.') t_section(result);
            else if (file[i] == ':') t_col(result);
            else if (file[i] == ',') t_com(result);
            else if (file[i] == '\n') t_sep(result);
            else if (file[i] == ';') t_comment();
            else if (Character.isWhitespace(file[i])) i++;
            else if (op(file[i])) t_op(result);
            else throw new RuntimeException("Unknown character '" + file[i] + "' at line " + (line + 1) + ", character " + (i - lineStart + 1) + ".");
        }

        if (result.isEmpty() || result.get(result.size() - 1).type != SEPARATOR) {
            result.add(new Token(SEPARATOR, "\\n", line + 1, 1));
        }

        return result;
    }

    private static void t_section(List<Token> tokens) {
        int tokenLine = line + 1;
        int tokenColumn = i - lineStart + 1;
        i++;

        StringBuilder section = new StringBuilder(".");
        while (i < file.length && (Character.isLetter(file[i]) || file[i] == '_')) {
            section.append(file[i++]);
        }

        String value = section.toString().toUpperCase();
        if (!value.equals(".CODE") && !value.equals(".DATA")) {
            throw new RuntimeException("Unknown section '" + value + "' at line " + tokenLine + ". Expected .code or .data.");
        }

        tokens.add(new Token(SECTION, value, tokenLine, tokenColumn));
    }

    private static void t_op(List<Token> tokens) {
        int tokenLine = line + 1;
        int tokenColumn = i - lineStart + 1;
        switch (file[i]) {
            case '+' -> tokens.add(new Token(ADD, "+", tokenLine, tokenColumn));
            case '-' -> tokens.add(new Token(SUB, "-", tokenLine, tokenColumn));
            case '*' -> tokens.add(new Token(MUL, "*", tokenLine, tokenColumn));
            case '/' -> tokens.add(new Token(DIV, "/", tokenLine, tokenColumn));
        }
        i++;
    }

    private static boolean op(char c) {
        return switch (c) {
            case '+', '-', '/', '*' -> true;
            default -> false;
        };
    }

    private static void t_comment() {
        while (i < file.length && file[i] != '\n') i++;
    }

    private static void t_sep(List<Token> tokens) {
        tokens.add(new Token(SEPARATOR, "\\n", line + 1, 1));
        i++;
        line++;
        lineStart = i;
    }

    private static void t_com(List<Token> tokens) {
        tokens.add(new Token(COMMA, ",", line + 1, i - lineStart + 1));
        i++;
    }

    private static void t_col(List<Token> tokens) {
        tokens.add(new Token(COLON, ":", line + 1, i - lineStart + 1));
        i++;
    }

    private static void t_char(List<Token> tokens) {
        int tokenLine = line + 1;
        int tokenColumn = i - lineStart + 1;
        i++; // opening quote
        if (i >= file.length || file[i] == '\n') {
            throw new RuntimeException("Unterminated character literal at line " + tokenLine + ", character " + tokenColumn + ".");
        }
        char value = file[i] == '\\' ? t_escape(tokenLine, tokenColumn) : file[i++];
        if (i >= file.length || file[i] != '\'') {
            throw new RuntimeException("Character literal must contain exactly one character at line " + tokenLine + ", character " + tokenColumn + ".");
        }
        i++;
        tokens.add(new Token(CHAR, Character.toString(value), tokenLine, tokenColumn));
    }

    private static void t_str(List<Token> tokens) {
        int tokenLine = line + 1;
        int tokenColumn = i - lineStart + 1;
        StringBuilder str = new StringBuilder();
        i++; // opening double quote
        while (i < file.length && file[i] != '"') {
            if (file[i] == '\n') throw new RuntimeException("String literals cannot span lines at line " + tokenLine + ", character " + tokenColumn + ".");
            if (file[i] == '\\') str.append(t_escape(tokenLine, tokenColumn));
            else str.append(file[i++]);
        }
        if (i >= file.length) throw new RuntimeException("Unterminated string literal at line " + tokenLine + ", character " + tokenColumn + ".");
        i++;
        tokens.add(new Token(STRING, str.toString(), tokenLine, tokenColumn));
    }

    private static char t_escape(int tokenLine, int tokenColumn) {
        i++; // backslash
        if (i >= file.length || file[i] == '\n') {
            throw new RuntimeException("Incomplete escape sequence at line " + tokenLine + ", character " + tokenColumn + ".");
        }
        char escaped = file[i++];
        return switch (escaped) {
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case '0' -> '\0';
            case '\\' -> '\\';
            case '"' -> '"';
            case '\'' -> '\'';
            case 'x', 'X' -> {
                if (i + 2 > file.length) throw new RuntimeException("Hex escape requires two digits at line " + tokenLine + ", character " + tokenColumn + ".");
                int high = p_hex_val(file[i]);
                int low = p_hex_val(file[i + 1]);
                if (high < 0 || low < 0) throw new RuntimeException("Invalid hex escape at line " + tokenLine + ", character " + tokenColumn + ".");
                i += 2;
                yield (char) ((high << 4) | low);
            }
            default -> throw new RuntimeException("Unknown escape sequence \\" + escaped + " at line " + tokenLine + ", character " + tokenColumn + ".");
        };
    }

    private static int p_char(String value) {
        if (value == null || value.length() != 1) throw new RuntimeException("Invalid character literal.");
        return value.charAt(0) & 0xFF;
    }

    private static void t_num(List<Token> tokens) {
        int tokenLine = line + 1;
        int tokenColumn = i - lineStart + 1;
        StringBuilder num = new StringBuilder();
        char first = file[i];
        char second = i + 1 < file.length ? Character.toUpperCase(file[i + 1]) : 0;

        if (first == '0' && (second == 'X' || second == 'B' || second == 'H')) {
            num.append(first).append(second);
            i += 2;
        }

        while (i < file.length && (Character.isDigit(file[i]) ||
                (Character.toUpperCase(file[i]) >= 'A' && Character.toUpperCase(file[i]) <= 'F'))) {
            num.append(file[i++]);
        }

        if (num.length() == 0 || (num.length() == 2 && first == '0')) {
            throw new RuntimeException("Invalid numeric literal at line " + tokenLine + ".");
        }

        tokens.add(new Token(LITERAL, num.toString(), tokenLine, tokenColumn));
    }

    private static boolean dig(char c) {
        return Character.isDigit(c);
    }

    private static boolean is_preprocessor_directive(String s) {
        return switch (s.toUpperCase()) {
            case "ORG", "EQU", "DB", "DW", "DS", "END" -> true;
            default -> false;
        };
    }

    private static void t_word(List<Token> tokens) {
        int tokenLine = line + 1;
        int tokenColumn = i - lineStart + 1;
        StringBuilder word = new StringBuilder();

        while (i < file.length &&
                (Character.isLetterOrDigit(file[i]) || file[i] == '_')) {
            word.append(file[i++]);
        }

        String value = word.toString();
        if (is_preprocessor_directive(value)) {
            tokens.add(new Token(PREPROCESSOR_DIRECTIVE, value.toUpperCase(), tokenLine, tokenColumn));
        } else {
            tokens.add(new Token(TEXT, value, tokenLine, tokenColumn));
        }
    }

    private static boolean let(char c) {
        return Character.isLetter(c);
    }

    /** Disassembles memory from the requested address through its final byte. */
    public static String[] Disassemble(byte[] memory, int _start) {
        return Disassemble(memory, _start, memory.length - 1);
    }

    /** Disassembles a bounded memory range and marks incomplete instructions. */
    public static String[] Disassemble(byte[] memory, int _start, int _end) {
        if (memory == null || memory.length == 0) return new String[0];

        List<String> output = new ArrayList<>();
        int pc = Math.max(0, _start);
        int end = Math.min(memory.length - 1, _end);

        while (pc <= end) {
            int opcode = memory[pc] & 0xFF;
            String mnemonic = OpcodeTable.OPCODES.getm(opcode);
            int size = OpcodeTable.OPCODES.gets(opcode);

            if (mnemonic == null || mnemonic.isBlank() || size <= 0) {
                output.add(String.format("0x%04X: DB     0x%02X", pc & 0xFFFF, opcode));
                pc++;
                continue;
            }

            StringBuilder instruction = new StringBuilder();
            instruction.append(String.format("0x%04X: %s", pc & 0xFFFF, mnemonic));

            if (size == 2) {
                if (pc + 1 > end) {
                    instruction.append(" <missing operand>");
                    output.add(instruction.toString());
                    break;
                }
                instruction.append(String.format(" 0x%02X", memory[pc + 1] & 0xFF));
            } else if (size == 3) {
                if (pc + 2 > end) {
                    instruction.append(" <missing operand>");
                    output.add(instruction.toString());
                    break;
                }
                int addr = (memory[pc + 1] & 0xFF) |
                        ((memory[pc + 2] & 0xFF) << 8);
                instruction.append(String.format(" 0x%04X", addr & 0xFFFF));
            }

            output.add(instruction.toString());
            pc += size;
        }

        return output.toArray(String[]::new);
    }
}
