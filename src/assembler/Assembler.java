package assembler;

import java.io.ByteArrayOutputStream;
import java.util.*;

public class Assembler {

    private static final Set<String> REGISTERS = Set.of("A","B","C","D","E","H","L","M");

    public static byte[] assemble(List<String> lines) {
        Map<String, Integer> labels = new HashMap<>();
        List<Instruction> instructions = new ArrayList<>();
        int pc = 0;

        // === PASS 1: collect labels and calculate PC ===
        for (String line : lines) {
            line = line.toUpperCase().split(";")[0].trim(); // remove comments
            if (line.isEmpty()) continue;

            // Label
            if (line.endsWith(":")) {
                labels.put(line.substring(0, line.length() - 1), pc);
                continue;
            }

            // Tokenize
            String[] tokens = line.replaceAll(",", " ").split("\\s+");
            String mnemonic = tokens[0];
            String[] args = Arrays.copyOfRange(tokens, 1, tokens.length);

            int size = OpcodeTable.OPCODES.gets(mnemonic);
            if (size == -1) throw new RuntimeException("Unknown instruction: " + mnemonic);

            instructions.add(new Instruction(mnemonic, args));
            pc += size;
        }

        // === PASS 2: generate machine code ===
        ByteArrayOutputStream program = new ByteArrayOutputStream();

        for (Instruction instr : instructions) {
            String mnemonic = instr.getMnemonic();
            String[] args = instr.getArgs();

            int opcode = OpcodeTable.OPCODES.geto(mnemonic);
            if (opcode == -1) throw new RuntimeException("Unknown opcode: " + mnemonic);

            // Check each argument for register and encode it
            for (String arg : args) {
                if (REGISTERS.contains(arg)) {
                    opcode |= registerCode(arg);
                    break; // only encode the first register argument for simplicity
                }
            }

            program.write(opcode);

            int size = OpcodeTable.OPCODES.gets(mnemonic);

            // Immediate 8-bit
            if (size == 2) {
                String arg = args[args.length - 1];
                int value = labels.containsKey(arg) ? labels.get(arg) : Integer.decode(arg);
                program.write(value & 0xFF);
            }

            // Immediate 16-bit / address
            if (size == 3) {
                String arg = args[args.length - 1];
                int value = labels.containsKey(arg) ? labels.get(arg) : Integer.decode(arg);
                program.write(value & 0xFF);          // low byte
                program.write((value >> 8) & 0xFF);   // high byte
            }
        }

        return program.toByteArray();
    }

    // Map register to code 0–7
    private static int registerCode(String reg) {
        return switch (reg) {
            case "B" -> 0;
            case "C" -> 1;
            case "D" -> 2;
            case "E" -> 3;
            case "H" -> 4;
            case "L" -> 5;
            case "M" -> 6; // memory at HL
            case "A" -> 7;
            default -> throw new RuntimeException("Unknown register: " + reg);
        };
    }

}
