package Intel8080.assembler;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Explicit, byte-ordered metadata for the Intel 8080 instruction set. */
public final class OpcodeTable {
    /** Shared lookup table used by both the assembler and disassembler. */
    public static final Table OPCODES = new Table();

    private OpcodeTable() {}

    /** Read-only instruction metadata after class initialization. */
    public static final class Table {
        private final Map<String, Integer> byMnemonic = new HashMap<>();
        private final String[] mnemonic = new String[256];
        private final int[] size = new int[256];

        private Table() {
            add(0x00, 1, "NOP");
            add(0x01, 3, "LXI B");
            add(0x02, 1, "STAX B");
            add(0x03, 1, "INX B");
            add(0x04, 1, "INR B");
            add(0x05, 1, "DCR B");
            add(0x06, 2, "MVI B");
            add(0x07, 1, "RLC");
            addUnused(0x08);
            add(0x09, 1, "DAD B");
            add(0x0A, 1, "LDAX B");
            add(0x0B, 1, "DCX B");
            add(0x0C, 1, "INR C");
            add(0x0D, 1, "DCR C");
            add(0x0E, 2, "MVI C");
            add(0x0F, 1, "RRC");
            addUnused(0x10);
            add(0x11, 3, "LXI D");
            add(0x12, 1, "STAX D");
            add(0x13, 1, "INX D");
            add(0x14, 1, "INR D");
            add(0x15, 1, "DCR D");
            add(0x16, 2, "MVI D");
            add(0x17, 1, "RAL");
            addUnused(0x18);
            add(0x19, 1, "DAD D");
            add(0x1A, 1, "LDAX D");
            add(0x1B, 1, "DCX D");
            add(0x1C, 1, "INR E");
            add(0x1D, 1, "DCR E");
            add(0x1E, 2, "MVI E");
            add(0x1F, 1, "RAR");
            addUnused(0x20);
            add(0x21, 3, "LXI H");
            add(0x22, 3, "SHLD");
            add(0x23, 1, "INX H");
            add(0x24, 1, "INR H");
            add(0x25, 1, "DCR H");
            add(0x26, 2, "MVI H");
            add(0x27, 1, "DAA");
            addUnused(0x28);
            add(0x29, 1, "DAD H");
            add(0x2A, 3, "LHLD");
            add(0x2B, 1, "DCX H");
            add(0x2C, 1, "INR L");
            add(0x2D, 1, "DCR L");
            add(0x2E, 2, "MVI L");
            add(0x2F, 1, "CMA");
            addUnused(0x30);
            add(0x31, 3, "LXI SP");
            add(0x32, 3, "STA");
            add(0x33, 1, "INX SP");
            add(0x34, 1, "INR M");
            add(0x35, 1, "DCR M");
            add(0x36, 2, "MVI M");
            add(0x37, 1, "STC");
            addUnused(0x38);
            add(0x39, 1, "DAD SP");
            add(0x3A, 3, "LDA");
            add(0x3B, 1, "DCX SP");
            add(0x3C, 1, "INR A");
            add(0x3D, 1, "DCR A");
            add(0x3E, 2, "MVI A");
            add(0x3F, 1, "CMC");
            add(0x40, 1, "MOV B, B");
            add(0x41, 1, "MOV B, C");
            add(0x42, 1, "MOV B, D");
            add(0x43, 1, "MOV B, E");
            add(0x44, 1, "MOV B, H");
            add(0x45, 1, "MOV B, L");
            add(0x46, 1, "MOV B, M");
            add(0x47, 1, "MOV B, A");
            add(0x48, 1, "MOV C, B");
            add(0x49, 1, "MOV C, C");
            add(0x4A, 1, "MOV C, D");
            add(0x4B, 1, "MOV C, E");
            add(0x4C, 1, "MOV C, H");
            add(0x4D, 1, "MOV C, L");
            add(0x4E, 1, "MOV C, M");
            add(0x4F, 1, "MOV C, A");
            add(0x50, 1, "MOV D, B");
            add(0x51, 1, "MOV D, C");
            add(0x52, 1, "MOV D, D");
            add(0x53, 1, "MOV D, E");
            add(0x54, 1, "MOV D, H");
            add(0x55, 1, "MOV D, L");
            add(0x56, 1, "MOV D, M");
            add(0x57, 1, "MOV D, A");
            add(0x58, 1, "MOV E, B");
            add(0x59, 1, "MOV E, C");
            add(0x5A, 1, "MOV E, D");
            add(0x5B, 1, "MOV E, E");
            add(0x5C, 1, "MOV E, H");
            add(0x5D, 1, "MOV E, L");
            add(0x5E, 1, "MOV E, M");
            add(0x5F, 1, "MOV E, A");
            add(0x60, 1, "MOV H, B");
            add(0x61, 1, "MOV H, C");
            add(0x62, 1, "MOV H, D");
            add(0x63, 1, "MOV H, E");
            add(0x64, 1, "MOV H, H");
            add(0x65, 1, "MOV H, L");
            add(0x66, 1, "MOV H, M");
            add(0x67, 1, "MOV H, A");
            add(0x68, 1, "MOV L, B");
            add(0x69, 1, "MOV L, C");
            add(0x6A, 1, "MOV L, D");
            add(0x6B, 1, "MOV L, E");
            add(0x6C, 1, "MOV L, H");
            add(0x6D, 1, "MOV L, L");
            add(0x6E, 1, "MOV L, M");
            add(0x6F, 1, "MOV L, A");
            add(0x70, 1, "MOV M, B");
            add(0x71, 1, "MOV M, C");
            add(0x72, 1, "MOV M, D");
            add(0x73, 1, "MOV M, E");
            add(0x74, 1, "MOV M, H");
            add(0x75, 1, "MOV M, L");
            add(0x76, 1, "HLT");
            add(0x77, 1, "MOV M, A");
            add(0x78, 1, "MOV A, B");
            add(0x79, 1, "MOV A, C");
            add(0x7A, 1, "MOV A, D");
            add(0x7B, 1, "MOV A, E");
            add(0x7C, 1, "MOV A, H");
            add(0x7D, 1, "MOV A, L");
            add(0x7E, 1, "MOV A, M");
            add(0x7F, 1, "MOV A, A");
            add(0x80, 1, "ADD B");
            add(0x81, 1, "ADD C");
            add(0x82, 1, "ADD D");
            add(0x83, 1, "ADD E");
            add(0x84, 1, "ADD H");
            add(0x85, 1, "ADD L");
            add(0x86, 1, "ADD M");
            add(0x87, 1, "ADD A");
            add(0x88, 1, "ADC B");
            add(0x89, 1, "ADC C");
            add(0x8A, 1, "ADC D");
            add(0x8B, 1, "ADC E");
            add(0x8C, 1, "ADC H");
            add(0x8D, 1, "ADC L");
            add(0x8E, 1, "ADC M");
            add(0x8F, 1, "ADC A");
            add(0x90, 1, "SUB B");
            add(0x91, 1, "SUB C");
            add(0x92, 1, "SUB D");
            add(0x93, 1, "SUB E");
            add(0x94, 1, "SUB H");
            add(0x95, 1, "SUB L");
            add(0x96, 1, "SUB M");
            add(0x97, 1, "SUB A");
            add(0x98, 1, "SBB B");
            add(0x99, 1, "SBB C");
            add(0x9A, 1, "SBB D");
            add(0x9B, 1, "SBB E");
            add(0x9C, 1, "SBB H");
            add(0x9D, 1, "SBB L");
            add(0x9E, 1, "SBB M");
            add(0x9F, 1, "SBB A");
            add(0xA0, 1, "ANA B");
            add(0xA1, 1, "ANA C");
            add(0xA2, 1, "ANA D");
            add(0xA3, 1, "ANA E");
            add(0xA4, 1, "ANA H");
            add(0xA5, 1, "ANA L");
            add(0xA6, 1, "ANA M");
            add(0xA7, 1, "ANA A");
            add(0xA8, 1, "XRA B");
            add(0xA9, 1, "XRA C");
            add(0xAA, 1, "XRA D");
            add(0xAB, 1, "XRA E");
            add(0xAC, 1, "XRA H");
            add(0xAD, 1, "XRA L");
            add(0xAE, 1, "XRA M");
            add(0xAF, 1, "XRA A");
            add(0xB0, 1, "ORA B");
            add(0xB1, 1, "ORA C");
            add(0xB2, 1, "ORA D");
            add(0xB3, 1, "ORA E");
            add(0xB4, 1, "ORA H");
            add(0xB5, 1, "ORA L");
            add(0xB6, 1, "ORA M");
            add(0xB7, 1, "ORA A");
            add(0xB8, 1, "CMP B");
            add(0xB9, 1, "CMP C");
            add(0xBA, 1, "CMP D");
            add(0xBB, 1, "CMP E");
            add(0xBC, 1, "CMP H");
            add(0xBD, 1, "CMP L");
            add(0xBE, 1, "CMP M");
            add(0xBF, 1, "CMP A");
            add(0xC0, 1, "RNZ");
            add(0xC1, 1, "POP B");
            add(0xC2, 3, "JNZ");
            add(0xC3, 3, "JMP");
            add(0xC4, 3, "CNZ");
            add(0xC5, 1, "PUSH B");
            add(0xC6, 2, "ADI");
            add(0xC7, 1, "RST 0");
            add(0xC8, 1, "RZ");
            add(0xC9, 1, "RET");
            add(0xCA, 3, "JZ");
            addUnused(0xCB);
            add(0xCC, 3, "CZ");
            add(0xCD, 3, "CALL");
            add(0xCE, 2, "ACI");
            add(0xCF, 1, "RST 1");
            add(0xD0, 1, "RNC");
            add(0xD1, 1, "POP D");
            add(0xD2, 3, "JNC");
            add(0xD3, 2, "OUT");
            add(0xD4, 3, "CNC");
            add(0xD5, 1, "PUSH D");
            add(0xD6, 2, "SUI");
            add(0xD7, 1, "RST 2");
            add(0xD8, 1, "RC");
            addUnused(0xD9);
            add(0xDA, 3, "JC");
            add(0xDB, 2, "IN");
            add(0xDC, 3, "CC");
            addUnused(0xDD);
            add(0xDE, 2, "SBI");
            add(0xDF, 1, "RST 3");
            add(0xE0, 1, "RPO");
            add(0xE1, 1, "POP H");
            add(0xE2, 3, "JPO");
            add(0xE3, 1, "XTHL");
            add(0xE4, 3, "CPO");
            add(0xE5, 1, "PUSH H");
            add(0xE6, 2, "ANI");
            add(0xE7, 1, "RST 4");
            add(0xE8, 1, "RPE");
            add(0xE9, 1, "PCHL");
            add(0xEA, 3, "JPE");
            add(0xEB, 1, "XCHG");
            add(0xEC, 3, "CPE");
            addUnused(0xED);
            add(0xEE, 2, "XRI");
            add(0xEF, 1, "RST 5");
            add(0xF0, 1, "RP");
            add(0xF1, 1, "POP PSW");
            add(0xF2, 3, "JP");
            add(0xF3, 1, "DI");
            add(0xF4, 3, "CP");
            add(0xF5, 1, "PUSH PSW");
            add(0xF6, 2, "ORI");
            add(0xF7, 1, "RST 6");
            add(0xF8, 1, "RM");
            add(0xF9, 1, "SPHL");
            add(0xFA, 3, "JM");
            add(0xFB, 1, "EI");
            add(0xFC, 3, "CM");
            add(0xFD, 1, "STOP");
            add(0xFE, 2, "CPI");
            add(0xFF, 1, "RST 7");
        }

        private void addUnused(int opcode) {
            size[opcode] = 1;
        }

        private void add(int opcode, int bytes, String name) {
            String normalized = normalize(name);
            byMnemonic.put(normalized, opcode);
            mnemonic[opcode] = normalized;
            size[opcode] = bytes;
        }

        /** Returns the opcode for a complete mnemonic and operand pattern, or -1 when unsupported. */
        public int geto(String name) {
            if (name == null) return -1;
            return byMnemonic.getOrDefault(normalize(name), -1);
        }

        /** Returns the encoded instruction size for an opcode, or -1 outside the byte range. */
        public int gets(int opcode) {
            if (opcode < 0 || opcode > 0xFF || size[opcode] == 0) return -1;
            return size[opcode];
        }

        /** Returns an immutable snapshot of the supported instruction mnemonics. */
        public java.util.Set<String> getMnemonics() {
            return java.util.Set.copyOf(byMnemonic.keySet());
        }

        /** Returns the canonical mnemonic for an opcode, or null when unsupported or out of range. */
        public String getm(int opcode) {
            if (opcode < 0 || opcode > 0xFF) return null;
            return mnemonic[opcode];
        }

        private static String normalize(String text) {
            return text.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        }
    }
}
