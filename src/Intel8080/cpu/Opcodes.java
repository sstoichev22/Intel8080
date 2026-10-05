package Intel8080.cpu;

/** Explicit 8080 opcode dispatch and instruction implementations. */
public final class Opcodes {
    static final OpcodeHandler[] HANDLERS = new OpcodeHandler[256];

    private static final int[] MINIMUM_CYCLES = {
        // 0x00-0x0F
        4, 10, 7, 5, 5, 5, 7, 4, 4, 10, 7, 5, 5, 5, 7, 4,
        // 0x10-0x1F
        4, 10, 7, 5, 5, 5, 7, 4, 4, 10, 7, 5, 5, 5, 7, 4,
        // 0x20-0x2F
        4, 10, 16, 5, 5, 5, 7, 4, 4, 10, 16, 5, 5, 5, 7, 4,
        // 0x30-0x3F
        4, 10, 13, 5, 10, 10, 10, 4, 4, 10, 13, 5, 5, 5, 7, 4,
        // 0x40-0x4F
        5, 5, 5, 5, 5, 5, 7, 5, 5, 5, 5, 5, 5, 5, 7, 5,
        // 0x50-0x5F
        5, 5, 5, 5, 5, 5, 7, 5, 5, 5, 5, 5, 5, 5, 7, 5,
        // 0x60-0x6F
        5, 5, 5, 5, 5, 5, 7, 5, 5, 5, 5, 5, 5, 5, 7, 5,
        // 0x70-0x7F
        7, 7, 7, 7, 7, 7, 7, 7, 5, 5, 5, 5, 5, 5, 7, 5,
        // 0x80-0x8F
        4, 4, 4, 4, 4, 4, 7, 4, 4, 4, 4, 4, 4, 4, 7, 4,
        // 0x90-0x9F
        4, 4, 4, 4, 4, 4, 7, 4, 4, 4, 4, 4, 4, 4, 7, 4,
        // 0xA0-0xAF
        4, 4, 4, 4, 4, 4, 7, 4, 4, 4, 4, 4, 4, 4, 7, 4,
        // 0xB0-0xBF
        4, 4, 4, 4, 4, 4, 7, 4, 4, 4, 4, 4, 4, 4, 7, 4,
        // 0xC0-0xCF
        5, 10, 10, 10, 11, 11, 7, 11, 5, 10, 10, 4, 11, 17, 7, 11,
        // 0xD0-0xDF
        5, 10, 10, 10, 11, 11, 7, 11, 5, 4, 10, 10, 11, 4, 7, 11,
        // 0xE0-0xEF
        5, 10, 10, 18, 11, 11, 7, 11, 5, 5, 10, 4, 11, 4, 7, 11,
        // 0xF0-0xFF
        5, 10, 10, 4, 11, 11, 7, 11, 5, 5, 10, 4, 11, 4, 7, 11
    };

    private Opcodes() {}

    static {
        HANDLERS[0x00] = Opcodes::nop;
        HANDLERS[0x01] = cpu -> lxi(cpu, 0);
        HANDLERS[0x02] = Opcodes::staxB;
        HANDLERS[0x03] = cpu -> inx(cpu, 0);
        HANDLERS[0x04] = cpu -> inr(cpu, 0);
        HANDLERS[0x05] = cpu -> dcr(cpu, 0);
        HANDLERS[0x06] = cpu -> mvi(cpu, 0);
        HANDLERS[0x07] = Opcodes::rlc;
        HANDLERS[0x08] = Opcodes::nop;
        HANDLERS[0x09] = cpu -> dad(cpu, 0);
        HANDLERS[0x0A] = Opcodes::ldaxB;
        HANDLERS[0x0B] = cpu -> dcx(cpu, 0);
        HANDLERS[0x0C] = cpu -> inr(cpu, 1);
        HANDLERS[0x0D] = cpu -> dcr(cpu, 1);
        HANDLERS[0x0E] = cpu -> mvi(cpu, 1);
        HANDLERS[0x0F] = Opcodes::rrc;
        HANDLERS[0x10] = Opcodes::nop;
        HANDLERS[0x11] = cpu -> lxi(cpu, 1);
        HANDLERS[0x12] = Opcodes::staxD;
        HANDLERS[0x13] = cpu -> inx(cpu, 1);
        HANDLERS[0x14] = cpu -> inr(cpu, 2);
        HANDLERS[0x15] = cpu -> dcr(cpu, 2);
        HANDLERS[0x16] = cpu -> mvi(cpu, 2);
        HANDLERS[0x17] = Opcodes::ral;
        HANDLERS[0x18] = Opcodes::nop;
        HANDLERS[0x19] = cpu -> dad(cpu, 1);
        HANDLERS[0x1A] = Opcodes::ldaxD;
        HANDLERS[0x1B] = cpu -> dcx(cpu, 1);
        HANDLERS[0x1C] = cpu -> inr(cpu, 3);
        HANDLERS[0x1D] = cpu -> dcr(cpu, 3);
        HANDLERS[0x1E] = cpu -> mvi(cpu, 3);
        HANDLERS[0x1F] = Opcodes::rar;
        HANDLERS[0x20] = Opcodes::nop;
        HANDLERS[0x21] = cpu -> lxi(cpu, 2);
        HANDLERS[0x22] = Opcodes::shld;
        HANDLERS[0x23] = cpu -> inx(cpu, 2);
        HANDLERS[0x24] = cpu -> inr(cpu, 4);
        HANDLERS[0x25] = cpu -> dcr(cpu, 4);
        HANDLERS[0x26] = cpu -> mvi(cpu, 4);
        HANDLERS[0x27] = Opcodes::daa;
        HANDLERS[0x28] = Opcodes::nop;
        HANDLERS[0x29] = cpu -> dad(cpu, 2);
        HANDLERS[0x2A] = Opcodes::lhld;
        HANDLERS[0x2B] = cpu -> dcx(cpu, 2);
        HANDLERS[0x2C] = cpu -> inr(cpu, 5);
        HANDLERS[0x2D] = cpu -> dcr(cpu, 5);
        HANDLERS[0x2E] = cpu -> mvi(cpu, 5);
        HANDLERS[0x2F] = Opcodes::cma;
        HANDLERS[0x30] = Opcodes::nop;
        HANDLERS[0x31] = cpu -> lxi(cpu, 3);
        HANDLERS[0x32] = Opcodes::sta;
        HANDLERS[0x33] = cpu -> inx(cpu, 3);
        HANDLERS[0x34] = cpu -> inr(cpu, 6);
        HANDLERS[0x35] = cpu -> dcr(cpu, 6);
        HANDLERS[0x36] = cpu -> mvi(cpu, 6);
        HANDLERS[0x37] = Opcodes::stc;
        HANDLERS[0x38] = Opcodes::nop;
        HANDLERS[0x39] = cpu -> dad(cpu, 3);
        HANDLERS[0x3A] = Opcodes::lda;
        HANDLERS[0x3B] = cpu -> dcx(cpu, 3);
        HANDLERS[0x3C] = cpu -> inr(cpu, 7);
        HANDLERS[0x3D] = cpu -> dcr(cpu, 7);
        HANDLERS[0x3E] = cpu -> mvi(cpu, 7);
        HANDLERS[0x3F] = Opcodes::cmc;
        HANDLERS[0x40] = cpu -> mov(cpu, 0, 0);
        HANDLERS[0x41] = cpu -> mov(cpu, 0, 1);
        HANDLERS[0x42] = cpu -> mov(cpu, 0, 2);
        HANDLERS[0x43] = cpu -> mov(cpu, 0, 3);
        HANDLERS[0x44] = cpu -> mov(cpu, 0, 4);
        HANDLERS[0x45] = cpu -> mov(cpu, 0, 5);
        HANDLERS[0x46] = cpu -> mov(cpu, 0, 6);
        HANDLERS[0x47] = cpu -> mov(cpu, 0, 7);
        HANDLERS[0x48] = cpu -> mov(cpu, 1, 0);
        HANDLERS[0x49] = cpu -> mov(cpu, 1, 1);
        HANDLERS[0x4A] = cpu -> mov(cpu, 1, 2);
        HANDLERS[0x4B] = cpu -> mov(cpu, 1, 3);
        HANDLERS[0x4C] = cpu -> mov(cpu, 1, 4);
        HANDLERS[0x4D] = cpu -> mov(cpu, 1, 5);
        HANDLERS[0x4E] = cpu -> mov(cpu, 1, 6);
        HANDLERS[0x4F] = cpu -> mov(cpu, 1, 7);
        HANDLERS[0x50] = cpu -> mov(cpu, 2, 0);
        HANDLERS[0x51] = cpu -> mov(cpu, 2, 1);
        HANDLERS[0x52] = cpu -> mov(cpu, 2, 2);
        HANDLERS[0x53] = cpu -> mov(cpu, 2, 3);
        HANDLERS[0x54] = cpu -> mov(cpu, 2, 4);
        HANDLERS[0x55] = cpu -> mov(cpu, 2, 5);
        HANDLERS[0x56] = cpu -> mov(cpu, 2, 6);
        HANDLERS[0x57] = cpu -> mov(cpu, 2, 7);
        HANDLERS[0x58] = cpu -> mov(cpu, 3, 0);
        HANDLERS[0x59] = cpu -> mov(cpu, 3, 1);
        HANDLERS[0x5A] = cpu -> mov(cpu, 3, 2);
        HANDLERS[0x5B] = cpu -> mov(cpu, 3, 3);
        HANDLERS[0x5C] = cpu -> mov(cpu, 3, 4);
        HANDLERS[0x5D] = cpu -> mov(cpu, 3, 5);
        HANDLERS[0x5E] = cpu -> mov(cpu, 3, 6);
        HANDLERS[0x5F] = cpu -> mov(cpu, 3, 7);
        HANDLERS[0x60] = cpu -> mov(cpu, 4, 0);
        HANDLERS[0x61] = cpu -> mov(cpu, 4, 1);
        HANDLERS[0x62] = cpu -> mov(cpu, 4, 2);
        HANDLERS[0x63] = cpu -> mov(cpu, 4, 3);
        HANDLERS[0x64] = cpu -> mov(cpu, 4, 4);
        HANDLERS[0x65] = cpu -> mov(cpu, 4, 5);
        HANDLERS[0x66] = cpu -> mov(cpu, 4, 6);
        HANDLERS[0x67] = cpu -> mov(cpu, 4, 7);
        HANDLERS[0x68] = cpu -> mov(cpu, 5, 0);
        HANDLERS[0x69] = cpu -> mov(cpu, 5, 1);
        HANDLERS[0x6A] = cpu -> mov(cpu, 5, 2);
        HANDLERS[0x6B] = cpu -> mov(cpu, 5, 3);
        HANDLERS[0x6C] = cpu -> mov(cpu, 5, 4);
        HANDLERS[0x6D] = cpu -> mov(cpu, 5, 5);
        HANDLERS[0x6E] = cpu -> mov(cpu, 5, 6);
        HANDLERS[0x6F] = cpu -> mov(cpu, 5, 7);
        HANDLERS[0x70] = cpu -> mov(cpu, 6, 0);
        HANDLERS[0x71] = cpu -> mov(cpu, 6, 1);
        HANDLERS[0x72] = cpu -> mov(cpu, 6, 2);
        HANDLERS[0x73] = cpu -> mov(cpu, 6, 3);
        HANDLERS[0x74] = cpu -> mov(cpu, 6, 4);
        HANDLERS[0x75] = cpu -> mov(cpu, 6, 5);
        HANDLERS[0x76] = Opcodes::hlt;
        HANDLERS[0x77] = cpu -> mov(cpu, 6, 7);
        HANDLERS[0x78] = cpu -> mov(cpu, 7, 0);
        HANDLERS[0x79] = cpu -> mov(cpu, 7, 1);
        HANDLERS[0x7A] = cpu -> mov(cpu, 7, 2);
        HANDLERS[0x7B] = cpu -> mov(cpu, 7, 3);
        HANDLERS[0x7C] = cpu -> mov(cpu, 7, 4);
        HANDLERS[0x7D] = cpu -> mov(cpu, 7, 5);
        HANDLERS[0x7E] = cpu -> mov(cpu, 7, 6);
        HANDLERS[0x7F] = cpu -> mov(cpu, 7, 7);
        HANDLERS[0x80] = cpu -> add(cpu, 7, 0);
        HANDLERS[0x81] = cpu -> add(cpu, 7, 1);
        HANDLERS[0x82] = cpu -> add(cpu, 7, 2);
        HANDLERS[0x83] = cpu -> add(cpu, 7, 3);
        HANDLERS[0x84] = cpu -> add(cpu, 7, 4);
        HANDLERS[0x85] = cpu -> add(cpu, 7, 5);
        HANDLERS[0x86] = cpu -> add(cpu, 7, 6);
        HANDLERS[0x87] = cpu -> add(cpu, 7, 7);
        HANDLERS[0x88] = cpu -> adc(cpu, 7, 0);
        HANDLERS[0x89] = cpu -> adc(cpu, 7, 1);
        HANDLERS[0x8A] = cpu -> adc(cpu, 7, 2);
        HANDLERS[0x8B] = cpu -> adc(cpu, 7, 3);
        HANDLERS[0x8C] = cpu -> adc(cpu, 7, 4);
        HANDLERS[0x8D] = cpu -> adc(cpu, 7, 5);
        HANDLERS[0x8E] = cpu -> adc(cpu, 7, 6);
        HANDLERS[0x8F] = cpu -> adc(cpu, 7, 7);
        HANDLERS[0x90] = cpu -> sub(cpu, 7, 0);
        HANDLERS[0x91] = cpu -> sub(cpu, 7, 1);
        HANDLERS[0x92] = cpu -> sub(cpu, 7, 2);
        HANDLERS[0x93] = cpu -> sub(cpu, 7, 3);
        HANDLERS[0x94] = cpu -> sub(cpu, 7, 4);
        HANDLERS[0x95] = cpu -> sub(cpu, 7, 5);
        HANDLERS[0x96] = cpu -> sub(cpu, 7, 6);
        HANDLERS[0x97] = cpu -> sub(cpu, 7, 7);
        HANDLERS[0x98] = cpu -> sbb(cpu, 7, 0);
        HANDLERS[0x99] = cpu -> sbb(cpu, 7, 1);
        HANDLERS[0x9A] = cpu -> sbb(cpu, 7, 2);
        HANDLERS[0x9B] = cpu -> sbb(cpu, 7, 3);
        HANDLERS[0x9C] = cpu -> sbb(cpu, 7, 4);
        HANDLERS[0x9D] = cpu -> sbb(cpu, 7, 5);
        HANDLERS[0x9E] = cpu -> sbb(cpu, 7, 6);
        HANDLERS[0x9F] = cpu -> sbb(cpu, 7, 7);
        HANDLERS[0xA0] = cpu -> ana(cpu, 7, 0);
        HANDLERS[0xA1] = cpu -> ana(cpu, 7, 1);
        HANDLERS[0xA2] = cpu -> ana(cpu, 7, 2);
        HANDLERS[0xA3] = cpu -> ana(cpu, 7, 3);
        HANDLERS[0xA4] = cpu -> ana(cpu, 7, 4);
        HANDLERS[0xA5] = cpu -> ana(cpu, 7, 5);
        HANDLERS[0xA6] = cpu -> ana(cpu, 7, 6);
        HANDLERS[0xA7] = cpu -> ana(cpu, 7, 7);
        HANDLERS[0xA8] = cpu -> xra(cpu, 7, 0);
        HANDLERS[0xA9] = cpu -> xra(cpu, 7, 1);
        HANDLERS[0xAA] = cpu -> xra(cpu, 7, 2);
        HANDLERS[0xAB] = cpu -> xra(cpu, 7, 3);
        HANDLERS[0xAC] = cpu -> xra(cpu, 7, 4);
        HANDLERS[0xAD] = cpu -> xra(cpu, 7, 5);
        HANDLERS[0xAE] = cpu -> xra(cpu, 7, 6);
        HANDLERS[0xAF] = cpu -> xra(cpu, 7, 7);
        HANDLERS[0xB0] = cpu -> ora(cpu, 7, 0);
        HANDLERS[0xB1] = cpu -> ora(cpu, 7, 1);
        HANDLERS[0xB2] = cpu -> ora(cpu, 7, 2);
        HANDLERS[0xB3] = cpu -> ora(cpu, 7, 3);
        HANDLERS[0xB4] = cpu -> ora(cpu, 7, 4);
        HANDLERS[0xB5] = cpu -> ora(cpu, 7, 5);
        HANDLERS[0xB6] = cpu -> ora(cpu, 7, 6);
        HANDLERS[0xB7] = cpu -> ora(cpu, 7, 7);
        HANDLERS[0xB8] = cpu -> cmp(cpu, 7, 0);
        HANDLERS[0xB9] = cpu -> cmp(cpu, 7, 1);
        HANDLERS[0xBA] = cpu -> cmp(cpu, 7, 2);
        HANDLERS[0xBB] = cpu -> cmp(cpu, 7, 3);
        HANDLERS[0xBC] = cpu -> cmp(cpu, 7, 4);
        HANDLERS[0xBD] = cpu -> cmp(cpu, 7, 5);
        HANDLERS[0xBE] = cpu -> cmp(cpu, 7, 6);
        HANDLERS[0xBF] = cpu -> cmp(cpu, 7, 7);
        HANDLERS[0xC0] = Opcodes::rnz;
        HANDLERS[0xC1] = Opcodes::popB;
        HANDLERS[0xC2] = Opcodes::jnz;
        HANDLERS[0xC3] = Opcodes::jmp;
        HANDLERS[0xC4] = Opcodes::cnz;
        HANDLERS[0xC5] = Opcodes::pushB;
        HANDLERS[0xC6] = Opcodes::adi;
        HANDLERS[0xC7] = Opcodes::rst0;
        HANDLERS[0xC8] = Opcodes::rz;
        HANDLERS[0xC9] = Opcodes::ret;
        HANDLERS[0xCA] = Opcodes::jz;
        HANDLERS[0xCB] = Opcodes::nop;
        HANDLERS[0xCC] = Opcodes::cz;
        HANDLERS[0xCD] = Opcodes::call;
        HANDLERS[0xCE] = Opcodes::aci;
        HANDLERS[0xCF] = Opcodes::rst1;
        HANDLERS[0xD0] = Opcodes::rnc;
        HANDLERS[0xD1] = Opcodes::popD;
        HANDLERS[0xD2] = Opcodes::jnc;
        HANDLERS[0xD3] = Opcodes::out;
        HANDLERS[0xD4] = Opcodes::cnc;
        HANDLERS[0xD5] = Opcodes::pushD;
        HANDLERS[0xD6] = Opcodes::sui;
        HANDLERS[0xD7] = Opcodes::rst2;
        HANDLERS[0xD8] = Opcodes::rc;
        HANDLERS[0xD9] = Opcodes::nop;
        HANDLERS[0xDA] = Opcodes::jc;
        HANDLERS[0xDB] = Opcodes::in;
        HANDLERS[0xDC] = Opcodes::cc;
        HANDLERS[0xDD] = Opcodes::nop;
        HANDLERS[0xDE] = Opcodes::sbi;
        HANDLERS[0xDF] = Opcodes::rst3;
        HANDLERS[0xE0] = Opcodes::rpo;
        HANDLERS[0xE1] = Opcodes::popH;
        HANDLERS[0xE2] = Opcodes::jpo;
        HANDLERS[0xE3] = Opcodes::xthl;
        HANDLERS[0xE4] = Opcodes::cpo;
        HANDLERS[0xE5] = Opcodes::pushH;
        HANDLERS[0xE6] = Opcodes::ani;
        HANDLERS[0xE7] = Opcodes::rst4;
        HANDLERS[0xE8] = Opcodes::rpe;
        HANDLERS[0xE9] = Opcodes::pchl;
        HANDLERS[0xEA] = Opcodes::jpe;
        HANDLERS[0xEB] = Opcodes::xchg;
        HANDLERS[0xEC] = Opcodes::cpe;
        HANDLERS[0xED] = Opcodes::nop;
        HANDLERS[0xEE] = Opcodes::xri;
        HANDLERS[0xEF] = Opcodes::rst5;
        HANDLERS[0xF0] = Opcodes::rp;
        HANDLERS[0xF1] = Opcodes::popPsw;
        HANDLERS[0xF2] = Opcodes::jp;
        HANDLERS[0xF3] = Opcodes::di;
        HANDLERS[0xF4] = Opcodes::cp;
        HANDLERS[0xF5] = Opcodes::pushPsw;
        HANDLERS[0xF6] = Opcodes::ori;
        HANDLERS[0xF7] = Opcodes::rst6;
        HANDLERS[0xF8] = Opcodes::rm;
        HANDLERS[0xF9] = Opcodes::sphl;
        HANDLERS[0xFA] = Opcodes::jm;
        HANDLERS[0xFB] = Opcodes::ei;
        HANDLERS[0xFC] = Opcodes::cm;
        HANDLERS[0xFD] = Opcodes::stop;
        HANDLERS[0xFE] = Opcodes::cpi;
        HANDLERS[0xFF] = Opcodes::rst7;
    }

    /** Returns the minimum documented clock-cycle count for an opcode byte. */
    static int minimumCycles(int opcode) {
        return MINIMUM_CYCLES[opcode & 0xFF];
    }

    /** Implements NOP and the unassigned-byte NOP aliases. */
    private static void nop(Intel8080 cpu) { cpu.log("NOP"); }

    /** Returns the low or high byte following the program counter and advances it. */
    private static byte fetch8(Intel8080 cpu) {
        byte value = cpu.memory.get(cpu.pc & 0xFFFF);
        cpu.pc = (cpu.pc + 1) & 0xFFFF;
        return value;
    }

    /** Fetches a little-endian 16-bit operand and advances the program counter twice. */
    private static int fetch16(Intel8080 cpu) {
        int low = fetch8(cpu) & 0xFF;
        int high = fetch8(cpu) & 0xFF;
        return (high << 8) | low;
    }

    /** Reads one of the eight 8080 register codes, including M through HL. */
    private static byte readReg(Intel8080 cpu, int register) {
        if (register == 0) return cpu.B;
        if (register == 1) return cpu.C;
        if (register == 2) return cpu.D;
        if (register == 3) return cpu.E;
        if (register == 4) return cpu.H;
        if (register == 5) return cpu.L;
        if (register == 6) return cpu.memory.get(cpu.pair(cpu.H, cpu.L));
        return cpu.A;
    }

    /** Writes one of the eight 8080 register codes, including M through HL. */
    private static void writeReg(Intel8080 cpu, int register, byte value) {
        if (register == 0) cpu.B = value;
        else if (register == 1) cpu.C = value;
        else if (register == 2) cpu.D = value;
        else if (register == 3) cpu.E = value;
        else if (register == 4) cpu.H = value;
        else if (register == 5) cpu.L = value;
        else if (register == 6) cpu.memory.set(cpu.pair(cpu.H, cpu.L), value);
        else cpu.A = value;
    }

    /** Reads BC, DE, HL, or SP using the opcode's register-pair code. */
    private static int getPair(Intel8080 cpu, int pair) {
        if (pair == 0) return cpu.pair(cpu.B, cpu.C);
        if (pair == 1) return cpu.pair(cpu.D, cpu.E);
        if (pair == 2) return cpu.pair(cpu.H, cpu.L);
        return cpu.memory.getSp();
    }

    /** Writes BC, DE, HL, or SP using the opcode's register-pair code. */
    private static void setPair(Intel8080 cpu, int pair, int value) {
        int word = value & 0xFFFF;
        if (pair == 0) { cpu.B = (byte) (word >>> 8); cpu.C = (byte) word; }
        else if (pair == 1) { cpu.D = (byte) (word >>> 8); cpu.E = (byte) word; }
        else if (pair == 2) { cpu.H = (byte) (word >>> 8); cpu.L = (byte) word; }
        else cpu.memory.setSp(word);
    }

    /** Loads an immediate 16-bit value into the selected register pair. */
    private static void lxi(Intel8080 cpu, int pair) { setPair(cpu, pair, fetch16(cpu)); }

    /** Increments the selected register pair without changing condition flags. */
    private static void inx(Intel8080 cpu, int pair) { setPair(cpu, pair, getPair(cpu, pair) + 1); }

    /** Decrements the selected register pair without changing condition flags. */
    private static void dcx(Intel8080 cpu, int pair) { setPair(cpu, pair, getPair(cpu, pair) - 1); }

    /** Adds a register pair to HL and updates only carry. */
    private static void dad(Intel8080 cpu, int pair) {
        int result = getPair(cpu, 2) + getPair(cpu, pair);
        setPair(cpu, 2, result);
        cpu.CY = result > 0xFFFF;
    }

    /** Copies one encoded register or memory operand to another. */
    private static void mov(Intel8080 cpu, int destination, int source) {
        writeReg(cpu, destination, readReg(cpu, source));
    }

    /** Increments a register or memory operand and preserves carry. */
    private static void inr(Intel8080 cpu, int register) {
        writeReg(cpu, register, cpu.incDecWithFlags(readReg(cpu, register), 1));
    }

    /** Decrements a register or memory operand and preserves carry. */
    private static void dcr(Intel8080 cpu, int register) {
        writeReg(cpu, register, cpu.incDecWithFlags(readReg(cpu, register), -1));
    }

    /** Loads an immediate byte into a register or memory operand. */
    private static void mvi(Intel8080 cpu, int register) { writeReg(cpu, register, fetch8(cpu)); }

    /** Adds source to destination and records the 8080 arithmetic flags. */
    private static void add(Intel8080 cpu, int destination, int source) {
        writeReg(cpu, destination, cpu.addWithFlags(readReg(cpu, destination), readReg(cpu, source), false));
    }

    /** Adds source and carry to destination and records arithmetic flags. */
    private static void adc(Intel8080 cpu, int destination, int source) {
        writeReg(cpu, destination, cpu.addWithFlags(readReg(cpu, destination), readReg(cpu, source), cpu.CY));
    }

    /** Subtracts source from destination and records arithmetic flags. */
    private static void sub(Intel8080 cpu, int destination, int source) {
        writeReg(cpu, destination, cpu.subWithFlags(readReg(cpu, destination), readReg(cpu, source), false));
    }

    /** Subtracts source and borrow from destination and records arithmetic flags. */
    private static void sbb(Intel8080 cpu, int destination, int source) {
        writeReg(cpu, destination, cpu.subWithFlags(readReg(cpu, destination), readReg(cpu, source), cpu.CY));
    }

    /** ANDs source into destination and records logical flags. */
    private static void ana(Intel8080 cpu, int destination, int source) {
        writeReg(cpu, destination, cpu.andWithFlags(readReg(cpu, destination), readReg(cpu, source)));
    }

    /** XORs source into destination and records logical flags. */
    private static void xra(Intel8080 cpu, int destination, int source) {
        int result = (readReg(cpu, destination) & 0xFF) ^ (readReg(cpu, source) & 0xFF);
        writeReg(cpu, destination, cpu.logicWithFlags((byte) result));
    }

    /** ORs source into destination and records logical flags. */
    private static void ora(Intel8080 cpu, int destination, int source) {
        int result = (readReg(cpu, destination) & 0xFF) | (readReg(cpu, source) & 0xFF);
        writeReg(cpu, destination, cpu.logicWithFlags((byte) result));
    }

    /** Compares source with destination without replacing destination. */
    private static void cmp(Intel8080 cpu, int destination, int source) {
        cpu.subWithFlags(readReg(cpu, destination), readReg(cpu, source), false);
    }

    /** Stores the accumulator through BC. */
    private static void staxB(Intel8080 cpu) { cpu.memory.set(cpu.pair(cpu.B, cpu.C), cpu.A); }

    /** Loads the accumulator through BC. */
    private static void ldaxB(Intel8080 cpu) { cpu.A = cpu.memory.get(cpu.pair(cpu.B, cpu.C)); }

    /** Stores the accumulator through DE. */
    private static void staxD(Intel8080 cpu) { cpu.memory.set(cpu.pair(cpu.D, cpu.E), cpu.A); }

    /** Loads the accumulator through DE. */
    private static void ldaxD(Intel8080 cpu) { cpu.A = cpu.memory.get(cpu.pair(cpu.D, cpu.E)); }

    /** Stores L and H at a fetched address in little-endian order. */
    private static void shld(Intel8080 cpu) {
        int address = fetch16(cpu);
        cpu.memory.set(address, cpu.L);
        cpu.memory.set(address + 1, cpu.H);
    }

    /** Loads L and H from a fetched address in little-endian order. */
    private static void lhld(Intel8080 cpu) {
        int address = fetch16(cpu);
        cpu.L = cpu.memory.get(address);
        cpu.H = cpu.memory.get(address + 1);
    }

    /** Stores A at a fetched 16-bit address. */
    private static void sta(Intel8080 cpu) { cpu.memory.set(fetch16(cpu), cpu.A); }

    /** Loads A from a fetched 16-bit address. */
    private static void lda(Intel8080 cpu) { cpu.A = cpu.memory.get(fetch16(cpu)); }

    /** Rotates A left through its outgoing high bit. */
    private static void rlc(Intel8080 cpu) {
        int value = cpu.A & 0xFF;
        boolean carry = (value & 0x80) != 0;
        cpu.A = (byte) ((value << 1) | (carry ? 1 : 0));
        cpu.CY = carry;
    }

    /** Rotates A right through its outgoing low bit. */
    private static void rrc(Intel8080 cpu) {
        int value = cpu.A & 0xFF;
        boolean carry = (value & 1) != 0;
        cpu.A = (byte) ((value >>> 1) | (carry ? 0x80 : 0));
        cpu.CY = carry;
    }

    /** Rotates A left through carry. */
    private static void ral(Intel8080 cpu) {
        int value = cpu.A & 0xFF;
        boolean carry = (value & 0x80) != 0;
        cpu.A = (byte) ((value << 1) | (cpu.CY ? 1 : 0));
        cpu.CY = carry;
    }

    /** Rotates A right through carry. */
    private static void rar(Intel8080 cpu) {
        int value = cpu.A & 0xFF;
        boolean carry = (value & 1) != 0;
        cpu.A = (byte) ((value >>> 1) | (cpu.CY ? 0x80 : 0));
        cpu.CY = carry;
    }

    /** Applies decimal correction to A and updates accumulator result flags. */
    private static void daa(Intel8080 cpu) {
        int value = cpu.A & 0xFF;
        int correction = 0;
        boolean adjustLow = (value & 0x0F) > 9 || cpu.AC;
        boolean adjustHigh = value > 0x99 || cpu.CY;
        if (adjustLow) correction |= 0x06;
        if (adjustHigh) correction |= 0x60;
        int result = value + correction;
        cpu.AC = ((value & 0x0F) + (correction & 0x0F)) > 0x0F;
        cpu.CY = adjustHigh || result > 0xFF;
        cpu.A = (byte) result;
        cpu.setZSP(cpu.A);
    }

    /** Complements A without changing flags. */
    private static void cma(Intel8080 cpu) { cpu.A = (byte) ~cpu.A; }

    /** Sets the carry flag. */
    private static void stc(Intel8080 cpu) { cpu.CY = true; }

    /** Complements the carry flag. */
    private static void cmc(Intel8080 cpu) { cpu.CY = !cpu.CY; }

    /** Adds an immediate byte to A. */
    private static void adi(Intel8080 cpu) { cpu.A = cpu.addWithFlags(cpu.A, fetch8(cpu), false); }

    /** Adds an immediate byte and carry to A. */
    private static void aci(Intel8080 cpu) { cpu.A = cpu.addWithFlags(cpu.A, fetch8(cpu), cpu.CY); }

    /** Subtracts an immediate byte from A. */
    private static void sui(Intel8080 cpu) { cpu.A = cpu.subWithFlags(cpu.A, fetch8(cpu), false); }

    /** Subtracts an immediate byte and borrow from A. */
    private static void sbi(Intel8080 cpu) { cpu.A = cpu.subWithFlags(cpu.A, fetch8(cpu), cpu.CY); }

    /** ANDs an immediate byte into A. */
    private static void ani(Intel8080 cpu) { cpu.A = cpu.andWithFlags(cpu.A, fetch8(cpu)); }

    /** XORs an immediate byte into A. */
    private static void xri(Intel8080 cpu) {
        int result = (cpu.A & 0xFF) ^ (fetch8(cpu) & 0xFF);
        cpu.A = cpu.logicWithFlags((byte) result);
    }

    /** ORs an immediate byte into A. */
    private static void ori(Intel8080 cpu) {
        int result = (cpu.A & 0xFF) | (fetch8(cpu) & 0xFF);
        cpu.A = cpu.logicWithFlags((byte) result);
    }

    /** Compares an immediate byte with A without replacing A. */
    private static void cpi(Intel8080 cpu) { cpu.subWithFlags(cpu.A, fetch8(cpu), false); }

    /** Unconditionally jumps to the fetched 16-bit address. */
    private static void jmp(Intel8080 cpu) { cpu.pc = fetch16(cpu); }

    /** Jumps only when the condition represented by a flag is true. */
    private static void conditionalJump(Intel8080 cpu, boolean condition) {
        int address = fetch16(cpu);
        if (condition) cpu.pc = address;
    }

    /** Returns only when the supplied condition is true. */
    private static void conditionalReturn(Intel8080 cpu, boolean condition) {
        if (condition) {
            cpu.pc = popAddress(cpu);
            cpu.instructionCycles = 11;
        } else {
            cpu.instructionCycles = 5;
        }
    }

    /** Calls a fetched address only when the supplied condition is true. */
    private static void conditionalCall(Intel8080 cpu, boolean condition) {
        int address = fetch16(cpu);
        if (condition) {
            pushAddress(cpu, cpu.pc);
            cpu.pc = address;
            cpu.instructionCycles = 17;
        } else {
            cpu.instructionCycles = 11;
        }
    }

    /** Implements a conditional return when zero is clear. */
    private static void rnz(Intel8080 cpu) { conditionalReturn(cpu, !cpu.Z); }
    /** Implements a conditional return when zero is set. */
    private static void rz(Intel8080 cpu) { conditionalReturn(cpu, cpu.Z); }
    /** Implements a conditional return when carry is clear. */
    private static void rnc(Intel8080 cpu) { conditionalReturn(cpu, !cpu.CY); }
    /** Implements a conditional return when carry is set. */
    private static void rc(Intel8080 cpu) { conditionalReturn(cpu, cpu.CY); }
    /** Implements a conditional return when parity is odd. */
    private static void rpo(Intel8080 cpu) { conditionalReturn(cpu, !cpu.P); }
    /** Implements a conditional return when parity is even. */
    private static void rpe(Intel8080 cpu) { conditionalReturn(cpu, cpu.P); }
    /** Implements a conditional return when sign is positive. */
    private static void rp(Intel8080 cpu) { conditionalReturn(cpu, !cpu.S); }
    /** Implements a conditional return when sign is negative. */
    private static void rm(Intel8080 cpu) { conditionalReturn(cpu, cpu.S); }

    /** Implements the unconditional return instruction. */
    private static void ret(Intel8080 cpu) { cpu.pc = popAddress(cpu); }

    /** Implements a conditional jump when zero is clear. */
    private static void jnz(Intel8080 cpu) { conditionalJump(cpu, !cpu.Z); }
    /** Implements a conditional jump when zero is set. */
    private static void jz(Intel8080 cpu) { conditionalJump(cpu, cpu.Z); }
    /** Implements a conditional jump when carry is clear. */
    private static void jnc(Intel8080 cpu) { conditionalJump(cpu, !cpu.CY); }
    /** Implements a conditional jump when carry is set. */
    private static void jc(Intel8080 cpu) { conditionalJump(cpu, cpu.CY); }
    /** Implements a conditional jump when parity is odd. */
    private static void jpo(Intel8080 cpu) { conditionalJump(cpu, !cpu.P); }
    /** Implements a conditional jump when parity is even. */
    private static void jpe(Intel8080 cpu) { conditionalJump(cpu, cpu.P); }
    /** Implements a conditional jump when sign is positive. */
    private static void jp(Intel8080 cpu) { conditionalJump(cpu, !cpu.S); }
    /** Implements a conditional jump when sign is negative. */
    private static void jm(Intel8080 cpu) { conditionalJump(cpu, cpu.S); }

    /** Implements an unconditional CALL, storing the address of the next opcode. */
    private static void call(Intel8080 cpu) {
        int address = fetch16(cpu);
        pushAddress(cpu, cpu.pc);
        cpu.pc = address;
    }

    /** Implements a conditional CALL when zero is clear. */
    private static void cnz(Intel8080 cpu) { conditionalCall(cpu, !cpu.Z); }
    /** Implements a conditional CALL when zero is set. */
    private static void cz(Intel8080 cpu) { conditionalCall(cpu, cpu.Z); }
    /** Implements a conditional CALL when carry is clear. */
    private static void cnc(Intel8080 cpu) { conditionalCall(cpu, !cpu.CY); }
    /** Implements a conditional CALL when carry is set. */
    private static void cc(Intel8080 cpu) { conditionalCall(cpu, cpu.CY); }
    /** Implements a conditional CALL when parity is odd. */
    private static void cpo(Intel8080 cpu) { conditionalCall(cpu, !cpu.P); }
    /** Implements a conditional CALL when parity is even. */
    private static void cpe(Intel8080 cpu) { conditionalCall(cpu, cpu.P); }
    /** Implements a conditional CALL when sign is positive. */
    private static void cp(Intel8080 cpu) { conditionalCall(cpu, !cpu.S); }
    /** Implements a conditional CALL when sign is negative. */
    private static void cm(Intel8080 cpu) { conditionalCall(cpu, cpu.S); }

    /** Pushes a 16-bit address in 8080 stack order. */
    private static void pushAddress(Intel8080 cpu, int address) {
        cpu.memory.push((byte) ((address >>> 8) & 0xFF));
        cpu.memory.push((byte) (address & 0xFF));
    }

    /** Pops a 16-bit return address in little-endian stack order. */
    private static int popAddress(Intel8080 cpu) {
        int low = cpu.memory.pop() & 0xFF;
        int high = cpu.memory.pop() & 0xFF;
        return (high << 8) | low;
    }

    /** Restarts execution at the fixed vector after saving the current PC. */
    private static void rst(Intel8080 cpu, int vector) {
        pushAddress(cpu, cpu.pc);
        cpu.pc = vector;
    }

    /** Implements RST 0. */
    private static void rst0(Intel8080 cpu) { rst(cpu, 0x00); }
    /** Implements RST 1. */
    private static void rst1(Intel8080 cpu) { rst(cpu, 0x08); }
    /** Implements RST 2. */
    private static void rst2(Intel8080 cpu) { rst(cpu, 0x10); }
    /** Implements RST 3. */
    private static void rst3(Intel8080 cpu) { rst(cpu, 0x18); }
    /** Implements RST 4. */
    private static void rst4(Intel8080 cpu) { rst(cpu, 0x20); }
    /** Implements RST 5. */
    private static void rst5(Intel8080 cpu) { rst(cpu, 0x28); }
    /** Implements RST 6. */
    private static void rst6(Intel8080 cpu) { rst(cpu, 0x30); }
    /** Implements RST 7. */
    private static void rst7(Intel8080 cpu) { rst(cpu, 0x38); }

    /** Pushes BC, DE, HL, or PSW according to the pair code. */
    private static void push(Intel8080 cpu, int pair) {
        if (pair == 3) {
            byte flags = (byte) (((cpu.S ? 1 : 0) << 7) | ((cpu.Z ? 1 : 0) << 6) |
                    ((cpu.AC ? 1 : 0) << 4) | ((cpu.P ? 1 : 0) << 2) | 0x02 | (cpu.CY ? 1 : 0));
            cpu.memory.push(cpu.A);
            cpu.memory.push(flags);
            return;
        }
        int value = getPair(cpu, pair);
        cpu.memory.push((byte) ((value >>> 8) & 0xFF));
        cpu.memory.push((byte) (value & 0xFF));
    }

    /** Pops BC, DE, HL, or PSW according to the pair code. */
    private static void pop(Intel8080 cpu, int pair) {
        byte low = cpu.memory.pop();
        byte high = cpu.memory.pop();
        if (pair == 0) { cpu.B = high; cpu.C = low; }
        else if (pair == 1) { cpu.D = high; cpu.E = low; }
        else if (pair == 2) { cpu.H = high; cpu.L = low; }
        else {
            cpu.A = high;
            cpu.CY = (low & 0x01) != 0;
            cpu.P = (low & 0x04) != 0;
            cpu.AC = (low & 0x10) != 0;
            cpu.Z = (low & 0x40) != 0;
            cpu.S = (low & 0x80) != 0;
        }
    }

    /** Pushes BC onto the stack. */
    private static void pushB(Intel8080 cpu) { push(cpu, 0); }
    /** Pushes DE onto the stack. */
    private static void pushD(Intel8080 cpu) { push(cpu, 1); }
    /** Pushes HL onto the stack. */
    private static void pushH(Intel8080 cpu) { push(cpu, 2); }
    /** Pushes PSW onto the stack. */
    private static void pushPsw(Intel8080 cpu) { push(cpu, 3); }
    /** Pops BC from the stack. */
    private static void popB(Intel8080 cpu) { pop(cpu, 0); }
    /** Pops DE from the stack. */
    private static void popD(Intel8080 cpu) { pop(cpu, 1); }
    /** Pops HL from the stack. */
    private static void popH(Intel8080 cpu) { pop(cpu, 2); }
    /** Pops PSW from the stack. */
    private static void popPsw(Intel8080 cpu) { pop(cpu, 3); }

    /** Exchanges HL with the stack word. */
    private static void xthl(Intel8080 cpu) {
        int sp = cpu.memory.getSp();
        byte low = cpu.memory.get(sp);
        byte high = cpu.memory.get(sp + 1);
        cpu.memory.set(sp, cpu.L);
        cpu.memory.set(sp + 1, cpu.H);
        cpu.L = low;
        cpu.H = high;
    }

    /** Exchanges DE and HL. */
    private static void xchg(Intel8080 cpu) {
        byte high = cpu.H;
        byte low = cpu.L;
        cpu.H = cpu.D;
        cpu.L = cpu.E;
        cpu.D = high;
        cpu.E = low;
    }

    /** Loads PC from HL. */
    private static void pchl(Intel8080 cpu) { cpu.pc = cpu.pair(cpu.H, cpu.L); }

    /** Loads SP from HL. */
    private static void sphl(Intel8080 cpu) { cpu.memory.setSp(cpu.pair(cpu.H, cpu.L)); }

    /** Reads a byte from the selected input port into A. */
    private static void in(Intel8080 cpu) {
        int port = fetch8(cpu) & 0xFF;
        cpu.A = cpu.inputPort(port);
    }

    /** Writes A to the selected output port. */
    private static void out(Intel8080 cpu) { cpu.outputPort(fetch8(cpu) & 0xFF, cpu.A); }

    /** Stops the simulator through Vulcan's custom FD instruction. */
    private static void stop(Intel8080 cpu) { cpu.stopFromInstruction(); }

    /** Stops CPU execution. */
    private static void hlt(Intel8080 cpu) { cpu.HLT = true; }

    /** Disables maskable interrupts. */
    private static void di(Intel8080 cpu) { cpu.IE = false; }

    /** Enables maskable interrupts. */
    private static void ei(Intel8080 cpu) { cpu.IE = true; }
}
