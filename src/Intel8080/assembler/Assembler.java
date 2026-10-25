package Intel8080.assembler;

import Intel8080.cpu.Memory;
import java.util.*;
import java.io.ByteArrayOutputStream;
import static Intel8080.assembler.Assembler.TokenType.*;

/** Two-pass assembler for the Vulcan 8080 dialect: relocatable objects, flat images, linking, and disassembly. */
public class Assembler {
    private static char[] file;
    private static int i,line,lineStart;
    private static List<Token> tokens;
    private static final Map<Statement,int[]> statementPositions=new IdentityHashMap<>();
    private static int diagnosticLine=1,diagnosticColumn=1;
    private static AssemblyInfo lastInfo=new AssemblyInfo(-1,-1,-1,-1,-1,-1);
    /** Inclusive program, code, and data address ranges of an assembled image; -1 marks an absent range. */
    public record AssemblyInfo(long programStart,long programEnd,long codeStart,long codeEnd,long dataStart,long dataEnd) {}
    /** Legacy 64 KiB image with its layout and warnings. */
    public record AssemblyResult(byte[] image,AssemblyInfo info,List<String> warnings) {
        public AssemblyResult {image=image.clone();warnings=List.copyOf(warnings);}
        @Override public byte[] image(){return image.clone();}
    }
    /** Contiguous bytes placed at an address. */
    public record Segment(long address,byte[] bytes) {
        public Segment {bytes=bytes.clone();}
        @Override public byte[] bytes(){return bytes.clone();}
    }
    /** Immutable result of placing relocatable objects into flat address space. */
    public record FlatResult(List<Segment> segments,Map<String,Long> symbols,AssemblyInfo info,long initialCodeAddress) {
        public FlatResult {
            segments = List.copyOf(segments);
            symbols = Map.copyOf(symbols);
        }
    }
    /** Patch of width bytes at offset, filled from an expression at link time. */
    public record ObjectRelocation(String section,int offset,int width,String expression,int sourceLine) {}
    /** One assembled statement in a relocatable object, with its labels and relocations. */
    public record ObjectAtom(String section,int offset,byte[] bytes,String mnemonic,String operand,boolean origin,List<String> labels,List<ObjectRelocation> relocations) {
        public ObjectAtom {bytes=bytes.clone();labels=List.copyOf(labels);relocations=List.copyOf(relocations);}
        @Override public byte[] bytes(){return bytes.clone();}
    }
    /** Equate exported by a relocatable object. */
    public record ObjectEquate(String name,String expression) {}
    /** Relocatable object: atoms, equates, and section sizes. */
    public record ObjectCode(List<ObjectAtom> atoms,List<ObjectEquate> equates,int textSize,int dataSize) {
        public ObjectCode {atoms=List.copyOf(atoms);equates=List.copyOf(equates);}
    }
    public static synchronized byte[] Assemble(String source){return assembleWithInfo(source).image();}
    /** Assembles source into a legacy 64 KiB image; throws IllegalArgumentException when the image does not fit. */
    public static synchronized AssemblyResult assembleWithInfo(String source){
        FlatResult flat=assembleFlat(source);byte[] image=new byte[Memory.MEM_SIZE];
        for(Segment segment:flat.segments()){
            if(Long.compareUnsigned(segment.address(),Memory.MEM_SIZE)>=0 || segment.bytes.length>Memory.MEM_SIZE-segment.address())
                throw new IllegalArgumentException("Image exceeds 64 KiB snapshot; use the flat executable/linker API.");
            System.arraycopy(segment.bytes,0,image,(int)segment.address(),segment.bytes.length);
        }
        lastInfo=flat.info();return new AssemblyResult(image,lastInfo,List.of());
    }
    public static synchronized FlatResult assembleFlat(String source){return assembleFlatObjects(List.of(assembleRelocatable(source)));}
    public static synchronized AssemblyInfo getLastAssemblyInfo(){return lastInfo;}
    public static synchronized long getLastProgramStart(){return lastInfo.programStart();}
    public static synchronized long getLastProgramEnd(){return lastInfo.programEnd();}
    public static synchronized long getLastCodeStart(){return lastInfo.codeStart();}
    public static synchronized long getLastCodeEnd(){return lastInfo.codeEnd();}
    public static synchronized long getLastDataStart(){return lastInfo.dataStart();}
    public static synchronized long getLastDataEnd(){return lastInfo.dataEnd();}
    private static List<Statement> parse(String source){
        file=(source==null?"":source).replace("\r","").toCharArray();i=line=lineStart=0;statementPositions.clear();
        return a_parse(a_lexer());
    }
    /** Parses source and emits section-relative atoms and unresolved relocations. */
    public static synchronized ObjectCode assembleRelocatable(String source) {
        List<Statement> statements = parse(source);
        Set<String> declared = new HashSet<>();
        Map<String, Long> known = new LinkedHashMap<>();
        List<ObjectAtom> atoms = new ArrayList<>();
        List<ObjectEquate> equates = new ArrayList<>();
        List<String> pendingTextLabels = new ArrayList<>();
        List<String> pendingDataLabels = new ArrayList<>();
        Section section = Section.CODE;
        int textSize = 0;
        int dataSize = 0;

        for (Statement statement : statements) {
            setDiagnosticPosition(statement);
            if (statement instanceof SectionStatement sectionStatement) {
                section = sectionStatement.section;
                continue;
            }

            List<String> pendingLabels = section == Section.CODE ? pendingTextLabels : pendingDataLabels;
            if (statement instanceof LabelStatement label) {
                if (!declared.add(label.name)) {
                    throw parseError("Duplicate symbol " + label.name);
                }
                pendingLabels.add(label.name);
                continue;
            }
            if (statement instanceof DirectiveStatement directive
                    && directive.directive instanceof EndDirective) {
                break;
            }
            if (statement instanceof DirectiveStatement directive
                    && directive.directive instanceof LinkDirective) {
                continue;
            }
            if (statement instanceof DirectiveStatement directive
                    && directive.directive instanceof EquDirective equate) {
                if (!declared.add(equate.name)) {
                    throw parseError("Duplicate symbol " + equate.name);
                }
                equates.add(new ObjectEquate(equate.name, expression(equate.value)));
                try {
                    known.put(equate.name, eval(equate.value, known, 0));
                } catch (IllegalArgumentException unresolved) {
                }
                continue;
            }

            String sectionName = section == Section.CODE ? ".text" : ".data";
            int offset = section == Section.CODE ? textSize : dataSize;
            String mnemonic = "";
            String operand = "";
            boolean origin = false;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            List<ObjectRelocation> relocations = new ArrayList<>();
            int sourceLine = statementPositions.get(statement)[0];

            if (statement instanceof InstructionStatement instruction) {
                mnemonic = instruction.mnemonic;
                operand = expression(instruction.arg);
                int opcode = OpcodeTable.OPCODES.geto(mnemonic);
                int operandWidth = OpcodeTable.OPCODES.gets(opcode) - 1;
                bytes.write(opcode);
                if (operandWidth > 0) {
                    encode(bytes, instruction.arg, operandWidth, known, relocations,
                            sectionName, offset, sourceLine);
                }
            } else if (statement instanceof DirectiveStatement directive) {
                if (directive.directive instanceof OrgDirective org) {
                    origin = true;
                    operand = expression(org.address);
                } else if (directive.directive instanceof DBDirective db) {
                    for (Expr value : db.values) {
                        if (value instanceof StringExpr string) {
                            for (char character : string.str.toCharArray()) {
                                if (character > 255) {
                                    throw parseError("DB string must contain byte characters.");
                                }
                                bytes.write(character);
                            }
                        } else {
                            encode(bytes, value, 1, known, relocations,
                                    sectionName, offset, sourceLine);
                        }
                    }
                } else if (directive.directive instanceof DWDirective words) {
                    for (Expr value : words.values) {
                        encode(bytes, value, words.width, known, relocations,
                                sectionName, offset, sourceLine);
                    }
                } else if (directive.directive instanceof DSDirective reserve) {
                    long count = eval(reserve.size, known, 0);
                    if (count < 0 || count > 32 * 1024 * 1024) {
                        throw parseError("Invalid DS allocation.");
                    }
                    bytes.writeBytes(new byte[(int) count]);
                }
            }

            atoms.add(new ObjectAtom(sectionName, offset, bytes.toByteArray(), mnemonic, operand,
                    origin, pendingLabels, relocations));
            pendingLabels.clear();
            if (section == Section.CODE) {
                textSize = Math.addExact(textSize, bytes.size());
            } else {
                dataSize = Math.addExact(dataSize, bytes.size());
            }
        }

        if (!pendingTextLabels.isEmpty()) {
            atoms.add(new ObjectAtom(".text", textSize, new byte[0], "", "", false,
                    pendingTextLabels, List.of()));
        }
        if (!pendingDataLabels.isEmpty()) {
            atoms.add(new ObjectAtom(".data", dataSize, new byte[0], "", "", false,
                    pendingDataLabels, List.of()));
        }
        return new ObjectCode(atoms, equates, textSize, dataSize);
    }
    private static void encode(ByteArrayOutputStream out,Expr value,int width,Map<String,Long> known,List<ObjectRelocation> patches,String section,int offset,int sourceLine){
        if(hasSymbol(value)||hasCurrentAddress(value)){patches.add(new ObjectRelocation(section,offset+out.size(),width,expression(value),sourceLine));out.writeBytes(new byte[width]);return;}
        long number=eval(value,known,0);checkWidth(number,width);for(int n=0;n<width;n++)out.write((byte)(number>>>(8*n)));
    }
    private static void checkWidth(long value,int bytes){
        if(bytes==8)return;
        long max=(1L<<(bytes*8))-1,min=-(1L<<(bytes*8-1));
        if(value<min || value>max)throw new IllegalArgumentException("Value "+value+" does not fit in "+(bytes*8)+" bits.");
    }
    private static boolean hasSymbol(Expr e){return e instanceof SymbolExpr || e instanceof BinaryExpr b&&(hasSymbol(b.left)||hasSymbol(b.right));}
    private static boolean hasCurrentAddress(Expr e){return e instanceof CurrentAddressExpr || e instanceof BinaryExpr b&&(hasCurrentAddress(b.left)||hasCurrentAddress(b.right));}
    private static String expression(Expr e){
        if(e==null)return "";if(e instanceof NumberExpr n)return n.negativeLiteral?Long.toString(n.value):"0x"+Long.toUnsignedString(n.value,16);
        if(e instanceof CharExpr c)return Integer.toString(c.value);if(e instanceof SymbolExpr s)return s.name;
        if(e instanceof CurrentAddressExpr)return "$";
        if(e instanceof BinaryExpr b)return "("+expression(b.left)+switch(b.operator){case ADD->"+";case SUB->"-";case MUL->"*";case DIV->"/";}+expression(b.right)+")";
        throw new IllegalArgumentException("Expected numeric expression.");
    }
    private static Expr expr(String source){if(source.isEmpty())return null;if(source.equals("$"))return new CurrentAddressExpr(1,1);file=(source+"\n").toCharArray();i=line=lineStart=0;tokens=rm_mul_sep(a_lexer());i=0;return p_parse_expr();}
    private static long eval(Expr e,Map<String,Long> symbols,long location){
        if(e==null)return 0;if(e instanceof NumberExpr n)return n.value;if(e instanceof CharExpr c)return c.value;if(e instanceof CurrentAddressExpr)return location;
        if(e instanceof SymbolExpr s){Long v=symbols.get(s.name);if(v==null)throw new IllegalArgumentException("Unresolved symbol "+s.name+" at line "+s.line);return v;}
        if(e instanceof BinaryExpr b){long x=eval(b.left,symbols,location),y=eval(b.right,symbols,location);return switch(b.operator){case ADD->x+y;case SUB->x-y;case MUL->x*y;case DIV->x/y;};}
        throw new IllegalArgumentException("Expected numeric expression.");
    }
    /** Renames exact symbol tokens in an object without changing its encoded bytes. */
    public static ObjectCode renameObject(ObjectCode object, Map<String, String> names) {
        java.util.regex.Pattern symbolToken = java.util.regex.Pattern.compile(
                "(?<![A-Za-z_0-9])[A-Za-z_$][A-Za-z_$0-9]*");
        java.util.function.UnaryOperator<String> renameExpression = expression -> {
            var matcher = symbolToken.matcher(expression);
            StringBuffer renamed = new StringBuffer();
            while (matcher.find()) {
                String replacement = names.getOrDefault(matcher.group(), matcher.group());
                matcher.appendReplacement(renamed,
                        java.util.regex.Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(renamed);
            return renamed.toString();
        };

        List<ObjectAtom> renamedAtoms = new ArrayList<>(object.atoms.size());
        for (ObjectAtom atom : object.atoms) {
            List<String> labels = atom.labels.stream()
                    .map(label -> names.getOrDefault(label, label))
                    .toList();
            List<ObjectRelocation> relocations = atom.relocations.stream()
                    .map(relocation -> new ObjectRelocation(relocation.section, relocation.offset,
                            relocation.width, renameExpression.apply(relocation.expression),
                            relocation.sourceLine))
                    .toList();
            renamedAtoms.add(new ObjectAtom(atom.section, atom.offset, atom.bytes, atom.mnemonic,
                    renameExpression.apply(atom.operand), atom.origin, labels, relocations));
        }

        List<ObjectEquate> renamedEquates = object.equates.stream()
                .map(equate -> new ObjectEquate(names.getOrDefault(equate.name, equate.name),
                        renameExpression.apply(equate.expression)))
                .toList();
        return new ObjectCode(renamedAtoms, renamedEquates, object.textSize, object.dataSize);
    }
    private record Placed(ObjectAtom atom,long address) {}
    /** Places and links relocatable objects into flat segments, resolving symbols across objects. */
    public static synchronized FlatResult assembleFlatObjects(List<ObjectCode> objects){
        Map<String,Long> symbols=new LinkedHashMap<>();List<ObjectEquate> equations=objects.stream().flatMap(o->o.equates.stream()).toList();
        resolveEquates(equations,symbols,false);
        List<Placed> placed=new ArrayList<>();long cursor=Memory.PROGRAM_START;
        long codeStart=-1,codeEnd=-1,dataStart=-1,dataEnd=-1;
        boolean hasCodeStart=false,hasDataStart=false;
        for(String section:List.of(".text",".data")){
            if(section.equals(".data"))cursor=Long.compareUnsigned(cursor,Memory.RAM_START)<0?Memory.RAM_START:(cursor+7)&~7L;
            for(ObjectCode object:objects){
                if(section.equals(".data"))cursor=(cursor+7)&~7L;
                for(ObjectAtom atom:object.atoms){if(!atom.section.equals(section))continue;
                    if(atom.origin)cursor=eval(expr(atom.operand),symbols,cursor);
                    for(String name:atom.labels){if(symbols.putIfAbsent(name,cursor)!=null)throw new IllegalArgumentException("Duplicate symbol "+name);}
                    if(atom.origin)continue;
                    placed.add(new Placed(atom,cursor));
                    if(atom.bytes.length>0){long end=cursor+atom.bytes.length-1;
                        if(Long.compareUnsigned(end,cursor)<0)throw new IllegalArgumentException("Section wraps the 64-bit address space.");
                        if(section.equals(".text")){if(!hasCodeStart){codeStart=cursor;hasCodeStart=true;}codeEnd=end;}else{if(!hasDataStart){dataStart=cursor;hasDataStart=true;}dataEnd=end;}
                        cursor=end+1;
                    }
                }
            }
            resolveEquates(equations,symbols,false);
        }
        resolveEquates(equations,symbols,true);
        List<Segment> chunks=new ArrayList<>();
        for(Placed p:placed){ObjectAtom atom=p.atom;byte[] bytes=atom.bytes.clone();
            for(ObjectRelocation patch:atom.relocations){int at=patch.offset-atom.offset;long location=atom.mnemonic.isEmpty()?p.address+at:p.address;long value=eval(expr(patch.expression),symbols,location);
                checkWidth(value,patch.width);
                for(int n=0;n<patch.width;n++)bytes[at+n]=(byte)(value>>>(8*n));
            }
            if(bytes.length>0)chunks.add(new Segment(p.address,bytes));
        }
        chunks.sort((a,b)->Long.compareUnsigned(a.address,b.address));List<Segment> segments=new ArrayList<>();ByteArrayOutputStream out=new ByteArrayOutputStream();long start=0,end=0;boolean active=false;
        for(Segment chunk:chunks){if(active&&chunk.address!=end){if(Long.compareUnsigned(chunk.address,end)<0)throw new IllegalArgumentException("Overlapping output sections.");segments.add(new Segment(start,out.toByteArray()));out.reset();active=false;}
            if(!active){start=chunk.address;active=true;}out.writeBytes(chunk.bytes);end=chunk.address+chunk.bytes.length;
        }
        if(active)segments.add(new Segment(start,out.toByteArray()));
        long programStart=segments.isEmpty()?-1:segments.get(0).address,programEnd=segments.isEmpty()?-1:end-1;
        lastInfo=new AssemblyInfo(programStart,programEnd,codeStart,codeEnd,dataStart,dataEnd);
        return new FlatResult(List.copyOf(segments),Map.copyOf(symbols),lastInfo,codeStart);
    }
    private static void resolveEquates(List<ObjectEquate> equations,Map<String,Long> symbols,boolean strict){
        boolean changed;do{changed=false;for(ObjectEquate e:equations)if(!symbols.containsKey(e.name)){try{symbols.put(e.name,eval(expr(e.expression),symbols,0));changed=true;}catch(IllegalArgumentException unresolved){}}}while(changed);
        if(strict)for(ObjectEquate e:equations)if(!symbols.containsKey(e.name))throw new IllegalArgumentException("Unresolved or cyclic equate "+e.name);
    }
    private sealed interface Statement permits InstructionStatement, DirectiveStatement, LabelStatement, SectionStatement {}
    private record InstructionStatement(String mnemonic, Expr arg) implements Statement {}
    private record DirectiveStatement(Directive directive) implements Statement {}
    private record LabelStatement(String name, int line, int column) implements Statement {}
    private record SectionStatement(Section section) implements Statement {}

    private enum Section {
        CODE,
        DATA
    }

    private sealed interface Directive permits EquDirective, OrgDirective, DBDirective, DWDirective, DSDirective, EndDirective, LinkDirective {}
    private record EquDirective(String name, Expr value, int line, int column) implements Directive {}
    private record OrgDirective(Expr address) implements Directive {}
    private record DBDirective(List<Expr> values) implements Directive {}
    private record DWDirective(List<Expr> values, int width) implements Directive {}
    private record DSDirective(Expr size) implements Directive {}
    private record EndDirective() implements Directive {}
    private record LinkDirective(String kind, List<String> symbols) implements Directive {}

    private sealed interface Expr permits StringExpr, CharExpr, NumberExpr, SymbolExpr, BinaryExpr, CurrentAddressExpr {}
    private record CurrentAddressExpr(int line, int column) implements Expr {}
    private record StringExpr(String str, int line, int column) implements Expr {}
    private record CharExpr(int value, int line, int column) implements Expr {}
    private record NumberExpr(long value, int line, int column, boolean negativeLiteral) implements Expr {
        NumberExpr(long value, int line, int column) { this(value, line, column, false); }
    }
    private record SymbolExpr(String name, int line, int column) implements Expr {}
    private record BinaryExpr(Expr left, Operator operator, Expr right, int line, int column) implements Expr {}

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
        while (!eof()) {
            Token first = currentToken();
            Statement statement = p_parse_statement();
            stmts.add(statement);
            statementPositions.put(statement, new int[]{first.lineNumber, first.column});
        }
        return stmts;
    }

    private static void setDiagnosticPosition(Statement statement) {
        int[] position = statementPositions.get(statement);
        if (position != null) {
            diagnosticLine = position[0];
            diagnosticColumn = position[1];
        }
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
            case ".TEXT", ".CODE" -> Section.CODE;
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
        String writtenMnemonic = consume(TEXT).text;
        if (!writtenMnemonic.equals(writtenMnemonic.toUpperCase(Locale.ROOT))
                && !writtenMnemonic.equals(writtenMnemonic.toLowerCase(Locale.ROOT))) {
            throw parseError("Instruction mnemonics must be entirely uppercase or entirely lowercase: " + writtenMnemonic);
        }
        String first = writtenMnemonic.toUpperCase(Locale.ROOT);
        String mnemonic = first;
        boolean consumedOperandInMnemonic = false;
        if (!match(SEPARATOR)) {
            String second = tokenText(0);

            if (second != null && (peekType(0) == TEXT || (first.equals("RST") && peekType(0) == LITERAL))) {
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
            arg = p_parse_operand(mnemonic);
        } else if (!match(SEPARATOR) && !consumedOperandInMnemonic) {
            int opcode = OpcodeTable.OPCODES.geto(mnemonic);
            int size = OpcodeTable.OPCODES.gets(opcode);
            if (opcode != -1 && size > 1) {
                arg = p_parse_operand(mnemonic);
            } else {
                throw parseError("Unexpected tokens after instruction " + mnemonic + ".");
            }
        }

        if (arg == null && OpcodeTable.OPCODES.gets(OpcodeTable.OPCODES.geto(mnemonic)) > 1)
            throw parseError(mnemonic + " requires an operand.");
        consume(SEPARATOR);
        return new InstructionStatement(mnemonic, arg);
    }

    private static Expr p_parse_operand(String mnemonic) { return p_parse_expr(); }

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
            String name = nameToken.text;
            consume(PREPROCESSOR_DIRECTIVE);
            Expr expr = p_parse_expr();
            consume(SEPARATOR);
            return new DirectiveStatement(new EquDirective(name, expr, nameToken.lineNumber, nameToken.column));
        }

        Token token = consume(PREPROCESSOR_DIRECTIVE);
        String dir = token.text;

        return switch (dir) {
            case "EXTERN" -> {
                List<String> names = new ArrayList<>();
                if (match(SEPARATOR)) throw parseError("EXTERN requires at least one symbol.");
                names.add(consume(TEXT).text);
                while (!match(SEPARATOR)) { consume(COMMA); names.add(consume(TEXT).text); }
                consume(SEPARATOR);
                yield new DirectiveStatement(new LinkDirective(dir, List.copyOf(names)));
            }
            case "PUBLIC", "GLOBAL" -> {
                List<String> names = new ArrayList<>();
                if (!match(SEPARATOR)) {
                    names.add(consume(TEXT).text);
                    while (!match(SEPARATOR)) { consume(COMMA); names.add(consume(TEXT).text); }
                }
                consume(SEPARATOR);
                yield new DirectiveStatement(new LinkDirective(dir, List.copyOf(names)));
            }
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
            case "DW", "DD", "DQ" -> {
                List<Expr> exprs = new ArrayList<>();
                if (match(SEPARATOR)) throw parseError("DW requires at least one value.");
                exprs.add(p_parse_expr());
                while (!match(SEPARATOR)) {
                    consume(COMMA);
                    exprs.add(p_parse_expr());
                }
                consume(SEPARATOR);
                yield new DirectiveStatement(new DWDirective(exprs, dir.equals("DW")?2:dir.equals("DD")?4:8));
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
        String label = labelToken.text;
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

    private static int[] expressionPosition(Expr expr) {
        if(expr instanceof NumberExpr n)return new int[]{n.line,n.column};
        if(expr instanceof SymbolExpr n)return new int[]{n.line,n.column};
        if(expr instanceof BinaryExpr n)return new int[]{n.line,n.column};
        return new int[]{diagnosticLine,diagnosticColumn};
    }
    private static Expr p_parse_expr() {
        return p_parse_add();
    }

    private static Expr p_parse_add() {
        Expr expr = p_parse_mul();
        while (match(ADD) || match(SUB)) {
            TokenType op = match(ADD) ? consume(ADD).type : consume(SUB).type;
            Expr right = p_parse_mul();
            int[] position = expressionPosition(expr);
            expr = new BinaryExpr(expr, op == ADD ? Operator.ADD : Operator.SUB, right, position[0], position[1]);
        }
        return expr;
    }

    private static Expr p_parse_mul() {
        Expr expr = p_parse_primary();
        while (match(MUL) || match(DIV)) {
            TokenType op = match(MUL) ? consume(MUL).type : consume(DIV).type;
            Expr right = p_parse_primary();
            int[] position = expressionPosition(expr);
            expr = new BinaryExpr(expr, op == MUL ? Operator.MUL : Operator.DIV, right, position[0], position[1]);
        }
        return expr;
    }

    private static Expr p_parse_primary() {
        if (match(OPEN_PAREN)) {
            consume(OPEN_PAREN); Expr value=p_parse_expr(); consume(CLOSE_PAREN); return value;
        }
        if (match(SUB)) {
            Token minus = consume(SUB);
            if (!match(LITERAL)) throw parseError("A negative sign must be followed by a numeric literal.");
            Token number = consume(LITERAL);
            try {
                return new NumberExpr(-p_eval(number.text), minus.lineNumber, minus.column, true);
            } catch (RuntimeException error) {
                throw new RuntimeException(error.getMessage() + " At line " + number.lineNumber + ", character " + number.column + ".", error);
            }
        }
        if (match(STRING)) { Token t = consume(STRING); return new StringExpr(t.text, t.lineNumber, t.column); }
        if (match(CHAR)) { Token t = consume(CHAR); return new CharExpr(p_char(t.text, t.lineNumber, t.column), t.lineNumber, t.column); }
        if (match(LITERAL)) {
            Token t = consume(LITERAL);
            try { return new NumberExpr(p_eval(t.text), t.lineNumber, t.column); }
            catch (RuntimeException error) { throw new RuntimeException(error.getMessage() + " At line " + t.lineNumber + ", character " + t.column + ".", error); }
        }
        if (match(TEXT)) {
            Token symbol = consume(TEXT);
            if(symbol.text.equals("$"))return new CurrentAddressExpr(symbol.lineNumber,symbol.column);
            return new SymbolExpr(symbol.text, symbol.lineNumber, symbol.column);
        }
        throw parseError("Expected a number, symbol, or string in expression, found " + currentToken() + ".");
    }

    private static long p_eval(String literal) {
        int radix=10; String digits=literal;
        if(literal.length()>2 && literal.charAt(0)=='0') {
            char prefix=Character.toUpperCase(literal.charAt(1));
            if(prefix=='X'||prefix=='H'||prefix=='B'){radix=prefix=='B'?2:16;digits=literal.substring(2);}
        }
        for (int index = 0; index < digits.length(); index++) {
            if (Character.digit(digits.charAt(index), radix) < 0) {
                throw new IllegalArgumentException("Invalid numeric literal: " + literal);
            }
        }
        try { return Long.parseUnsignedLong(digits,radix); }
        catch(NumberFormatException error){throw new IllegalArgumentException("Literal exceeds 64 bits: "+literal);}
    }

    private static int p_hex_val(char c) {
        char upper = Character.toUpperCase(c);
        if (upper >= '0' && upper <= '9') return upper - '0';
        if (upper >= 'A' && upper <= 'F') return upper - 'A' + 10;
        return -1;
    }

    /** Lexical category of an assembly token. */
    enum TokenType {
        PREPROCESSOR_DIRECTIVE,
        SECTION,
        TEXT,
        LITERAL,
        CHAR,
        STRING,
        COLON,
        COMMA,
        OPEN_PAREN,
        CLOSE_PAREN,
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
            if (let(file[i]) || file[i] == '_' || file[i] == '$') t_word(result);
            else if (dig(file[i])) t_num(result);
            else if (file[i] == '\'') t_char(result);
            else if (file[i] == '"') t_str(result);
            else if (file[i] == '.') t_section(result);
            else if (file[i] == ':') t_col(result);
            else if (file[i] == ',') t_com(result);
            else if (file[i] == '(' || file[i] == ')') {
                result.add(new Token(file[i]=='('?OPEN_PAREN:CLOSE_PAREN,Character.toString(file[i]),line+1,i-lineStart+1)); i++;
            }
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
        if (!value.equals(".TEXT") && !value.equals(".CODE") && !value.equals(".DATA")) {
            throw new RuntimeException("Unknown section '" + value + "' at line " + tokenLine + ", character " + tokenColumn + ". Expected .text or .data.");
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
        i++;
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
        i++;
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
        i++;
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

    private static int p_char(String value, int sourceLine, int sourceColumn) {
        if (value == null || value.length() != 1) throw new RuntimeException("Invalid character literal at line " + sourceLine + ", character " + sourceColumn + ".");
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
            throw new RuntimeException("Invalid numeric literal at line " + tokenLine + ", character " + tokenColumn + ".");
        }

        tokens.add(new Token(LITERAL, num.toString(), tokenLine, tokenColumn));
    }

    private static boolean dig(char c) {
        return Character.isDigit(c);
    }

    private static boolean is_preprocessor_directive(String s) {
        return switch (s.toUpperCase()) {
            case "ORG", "EQU", "DB", "DW", "DD", "DQ", "DS", "END", "EXTERN", "PUBLIC", "GLOBAL" -> true;
            default -> false;
        };
    }

    private static void t_word(List<Token> tokens) {
        int tokenLine = line + 1;
        int tokenColumn = i - lineStart + 1;
        StringBuilder word = new StringBuilder();

        while (i < file.length &&
                (Character.isLetterOrDigit(file[i]) || file[i] == '_' || file[i] == '$')) {
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

    /** Disassembles an inclusive range of a byte image, treating its first byte as address zero. */
    public static String[] Disassemble(byte[] image, int start, int end) {
        if (start < 0 || end >= image.length) {
            throw new IllegalArgumentException("Disassembly range is outside the byte image.");
        }
        Memory memory = new Memory();
        memory.load(0, image);
        return Disassemble(memory, start, end);
    }

    /**
     * Disassembles an inclusive 64-bit address range. Unknown opcodes and instructions
     * cut off by the requested range are emitted as one-byte {@code DB} entries so no
     * byte outside the window is read or silently discarded.
     */
    public static String[] Disassemble(Memory memory, long start, long end) {
        if (Long.compareUnsigned(end, start) < 0) {
            throw new IllegalArgumentException("Disassembly range must not wrap the address space.");
        }
        long length = end - start + 1;
        if (length <= 0 || length > Memory.MEM_SIZE) {
            throw new IllegalArgumentException("Disassembly window must contain 1 to 65536 bytes.");
        }

        List<String> output = new ArrayList<>();
        long offset = 0;
        while (offset < length) {
            long address = start + offset;
            int opcode = memory.get(address) & 0xff;
            String mnemonic = OpcodeTable.OPCODES.getm(opcode);
            int instructionSize = OpcodeTable.OPCODES.gets(opcode);
            if (mnemonic == null || instructionSize <= 0 || offset + instructionSize > length) {
                output.add(String.format("0x%016X: DB 0x%02X", address, opcode));
                offset++;
                continue;
            }

            String instruction = String.format("0x%016X: %s", address, mnemonic);
            if (instructionSize > 1) {
                long operand = 0;
                for (int byteIndex = 1; byteIndex < instructionSize; byteIndex++) {
                    operand |= (memory.get(address + byteIndex) & 0xffL) << (Byte.SIZE * (byteIndex - 1));
                }
                if (opcode == OpcodeTable.OPCODES.geto("LDBC") || opcode == OpcodeTable.OPCODES.geto("STBC")
                        || opcode == OpcodeTable.OPCODES.geto("LDBC32") || opcode == OpcodeTable.OPCODES.geto("STBC32")
                        || opcode == OpcodeTable.OPCODES.geto("ADDBC32") || opcode == OpcodeTable.OPCODES.geto("SUBBC32")) {
                    instruction = String.format("0x%016X: %s %d", address, mnemonic, (int) operand);
                } else {
                    String separator = mnemonic.startsWith("LXI ") || mnemonic.startsWith("MVI ")
                            ? ", " : " ";
                    int operandDigits = (instructionSize - 1) * 2;
                    instruction += String.format("%s0x%0" + operandDigits + "X", separator, operand);
                }
            }
            output.add(instruction);
            offset += instructionSize;
        }
        return output.toArray(String[]::new);
    }
}
