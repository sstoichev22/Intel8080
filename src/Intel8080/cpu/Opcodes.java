package Intel8080.cpu;
import Intel8080.assembler.OpcodeTable;

/** Opcode dispatch table and per-opcode minimum cycle costs. */
public final class Opcodes {
    static final OpcodeHandler[] HANDLERS = new OpcodeHandler[256];
    private static final int[] MINIMUM_CYCLES = {

        4, 10, 7, 5, 5, 5, 7, 4, 11, 10, 7, 5, 5, 5, 7, 4,

        11, 10, 7, 5, 5, 5, 7, 4, 4, 10, 7, 5, 5, 5, 7, 4,

        4, 10, 16, 5, 5, 5, 7, 4, 4, 10, 16, 5, 5, 5, 7, 4,

        4, 10, 13, 5, 10, 10, 10, 4, 4, 10, 13, 5, 5, 5, 7, 4,

        5, 5, 5, 5, 5, 5, 7, 5, 5, 5, 5, 5, 5, 5, 7, 5,

        5, 5, 5, 5, 5, 5, 7, 5, 5, 5, 5, 5, 5, 5, 7, 5,

        5, 5, 5, 5, 5, 5, 7, 5, 5, 5, 5, 5, 5, 5, 7, 5,

        7, 7, 7, 7, 7, 7, 7, 7, 5, 5, 5, 5, 5, 5, 7, 5,

        4, 4, 4, 4, 4, 4, 7, 4, 4, 4, 4, 4, 4, 4, 7, 4,

        4, 4, 4, 4, 4, 4, 7, 4, 4, 4, 4, 4, 4, 4, 7, 4,

        4, 4, 4, 4, 4, 4, 7, 4, 4, 4, 4, 4, 4, 4, 7, 4,

        4, 4, 4, 4, 4, 4, 7, 4, 4, 4, 4, 4, 4, 4, 7, 4,

        5, 10, 10, 10, 11, 11, 7, 11, 5, 10, 10, 4, 11, 17, 7, 11,

        5, 10, 10, 10, 11, 11, 7, 11, 5, 4, 10, 10, 11, 17, 7, 11,

        5, 10, 10, 18, 11, 11, 7, 11, 5, 5, 10, 4, 11, 4, 7, 11,

        5, 10, 10, 4, 11, 11, 7, 11, 5, 5, 10, 4, 11, 4, 7, 11
    };

    private Opcodes() {}
    static {
        MINIMUM_CYCLES[0x07] = 7;
        MINIMUM_CYCLES[0x0f] = 7;
        MINIMUM_CYCLES[0x17] = 7;
        MINIMUM_CYCLES[0x1f] = 7;
        MINIMUM_CYCLES[0x20] = 32;
        MINIMUM_CYCLES[0x28] = 32;
        MINIMUM_CYCLES[0x30] = 53;
        MINIMUM_CYCLES[0x38] = 53;
        for (int n = 0; n < 256; n++) {
            final int opcode = n;
            if (OpcodeTable.OPCODES.getm(n) != null)
                HANDLERS[n] = cpu -> dispatch(cpu, opcode);
        }
    }
    static int minimumCycles(int opcode) {return MINIMUM_CYCLES[opcode&255];}
    private static long fetch(Intel8080 c, int bytes) {
        long value = 0;
        for (int n = 0; n < bytes; n++) { value |= (c.memory.get(c.pc) & 255L) << (8*n); c.pc++; }
        return value;
    }
    private static int reg(Intel8080 c, int r) {
        return switch (r) { case 0 -> c.B; case 1 -> c.C; case 2 -> c.D; case 3 -> c.E;
            case 4 -> c.H; case 5 -> c.L; case 6 -> c.memory.get(c.pair(c.H,c.L)) & 255; default -> c.A; };
    }
    private static void reg(Intel8080 c, int r, int v) {
        switch (r) { case 0 -> c.B=v; case 1 -> c.C=v; case 2 -> c.D=v; case 3 -> c.E=v;
            case 4 -> c.H=v; case 5 -> c.L=v; case 6 -> c.memory.set(c.pair(c.H,c.L),(byte)v); default -> c.A=v; }
    }
    private static long pair(Intel8080 c, int r) {
        return switch (r) { case 0 -> c.pair(c.B,c.C); case 1 -> c.pair(c.D,c.E); case 2 -> c.pair(c.H,c.L); default -> c.memory.getSp(); };
    }
    private static void pair(Intel8080 c, int r, long v) {
        switch (r) { case 0 -> {c.B=(int)(v>>>32); c.C=(int)v;} case 1 -> {c.D=(int)(v>>>32); c.E=(int)v;}
            case 2 -> {c.H=(int)(v>>>32); c.L=(int)v;} default -> c.memory.setSp(v); }
    }
    private static boolean condition(Intel8080 c, int n) {
        return switch(n) { case 0 -> !c.Z; case 1 -> c.Z; case 2 -> !c.CY; case 3 -> c.CY;
            case 4 -> !c.P; case 5 -> c.P; case 6 -> !c.S; default -> c.S; };
    }
    private static int flags(Intel8080 c) {
        return (c.S?128:0)|(c.Z?64:0)|(c.AC?16:0)|(c.P?4:0)|2|(c.CY?1:0);
    }
    private static void flags(Intel8080 c, int f) { c.S=(f&128)!=0; c.Z=(f&64)!=0; c.AC=(f&16)!=0; c.P=(f&4)!=0; c.CY=(f&1)!=0; }
    private static void alu(Intel8080 c, int operation, int operand) {
        switch (operation) {
            case 0 -> c.A=c.addWithFlags(c.A,operand,false);
            case 1 -> c.A=c.addWithFlags(c.A,operand,c.CY);
            case 2 -> c.A=c.subWithFlags(c.A,operand,false);
            case 3 -> c.A=c.subWithFlags(c.A,operand,c.CY);
            case 4 -> c.A=c.andWithFlags(c.A,operand);
            case 5 -> c.A=c.logicWithFlags(c.A^operand);
            case 6 -> c.A=c.logicWithFlags(c.A|operand);
            case 7 -> c.subWithFlags(c.A,operand,false);
        }
    }
    private static void dispatch(Intel8080 c, int op) {
        if(op>=0x40 && op<0x80) { if(op==0x76)c.HLT=true; else reg(c,op>>3&7,reg(c,op&7)); return; }
        if(op>=0x80 && op<0xc0) { alu(c,op>>3&7,reg(c,op&7)); return; }
        if(op<0x40) {
            if (op == 0x08) { c.A = c.memory.get(pair(c, 0) + (int) fetch(c, 4)) & 255; return; }
            if (op == 0x10) { c.memory.set(pair(c, 0) + (int) fetch(c, 4), (byte) c.A); return; }
            if (op == 0x20) { long address = pair(c, 0) + (int) fetch(c, 4); c.A = (c.memory.get(address) & 255) | ((c.memory.get(address + 1) & 255) << 8) | ((c.memory.get(address + 2) & 255) << 16) | ((c.memory.get(address + 3) & 255) << 24); return; }
            if (op == 0x28) { long address = pair(c, 0) + (int) fetch(c, 4); c.memory.set(address, (byte) c.A); c.memory.set(address + 1, (byte) (c.A >>> 8)); c.memory.set(address + 2, (byte) (c.A >>> 16)); c.memory.set(address + 3, (byte) (c.A >>> 24)); return; }
            if (op == 0x30 || op == 0x38) {
                long address = pair(c, 0) + (int) fetch(c, 4);
                int left = (c.memory.get(address) & 0xff)
                        | ((c.memory.get(address + 1) & 0xff) << 8)
                        | ((c.memory.get(address + 2) & 0xff) << 16)
                        | ((c.memory.get(address + 3) & 0xff) << 24);
                int result = op == 0x30 ? c.addWithFlags(left, c.A, false) : c.subWithFlags(left, c.A, false);
                c.memory.set(address, (byte) result);
                c.memory.set(address + 1, (byte) (result >>> 8));
                c.memory.set(address + 2, (byte) (result >>> 16));
                c.memory.set(address + 3, (byte) (result >>> 24));
                c.A = result;
                return;
            }
            int r=op>>3&7, p=op>>4&3;
            switch(op&15) {
                case 1 -> { pair(c,p,fetch(c,8)); return; }
                case 3 -> { pair(c,p,pair(c,p)+1); return; }
                case 11 -> { pair(c,p,pair(c,p)-1); return; }
                case 9 -> { long x=pair(c,2), y=pair(c,p), z=x+y; pair(c,2,z); c.CY=Long.compareUnsigned(z,x)<0; return; }
            }
            if((op&7)==4) { reg(c,r,c.incDecWithFlags(reg(c,r),1)); return; }
            if((op&7)==5) { reg(c,r,c.incDecWithFlags(reg(c,r),-1)); return; }
            if((op&7)==6) { reg(c,r,(int)fetch(c,4)); return; }
        }
        if(op>=0xc0) {
            int p=op>>4&3, n=op>>3&7;
            switch(op&7) {
                case 0 -> { if(condition(c,n)){c.pc=c.memory.pop64();c.instructionCycles=11;} return; }
                case 2 -> { long addr=fetch(c,8); if(condition(c,n))c.pc=addr; return; }
                case 4 -> { long addr=fetch(c,8); if(condition(c,n)){c.memory.push64(c.pc);c.pc=addr;c.instructionCycles=17;} return; }
                case 6 -> { alu(c,n,(int)fetch(c,4)); return; }
                case 7 -> { c.memory.push64(c.pc); c.pc=((op >>> 3) & 7) * 0x10; return; }
            }
            if((op&15)==1) { long v=c.memory.pop64(); if(p==3){c.A=(int)(v>>>32);flags(c,(int)v);}else pair(c,p,v); return; }
            if((op&15)==5) { c.memory.push64(p==3?c.pair(c.A,flags(c)):pair(c,p)); return; }
        }
        switch(op) {
            case 0x00 -> {}
            case 0x02,0x12 -> c.memory.set(pair(c,op==2?0:1),(byte)c.A);
            case 0x0a,0x1a -> c.A=c.memory.get(pair(c,op==10?0:1))&255;
            case 0x22 -> c.memory.write64(fetch(c,8),pair(c,2));
            case 0x2a -> pair(c,2,c.memory.read64(fetch(c,8)));
            case 0x32 -> c.memory.set(fetch(c,8),(byte)c.A);
            case 0x3a -> c.A=c.memory.get(fetch(c,8))&255;
            case 0x07, 0x0f, 0x17, 0x1f -> rotate(c, op, (int) fetch(c, 1));
            case 0x27 -> {
                long value=Integer.toUnsignedLong(c.A); boolean carry=c.CY;
                long corrected=value; boolean lowAC=false;
                for(int n=0;n<8;n++){int digit=(int)(corrected>>>(4*n))&15,originalDigit=(int)(value>>>(4*n))&15;
                    if(digit>9 || originalDigit>9 || (n==0&&c.AC) || (n==7&&carry)){
                        if(n==0)lowAC=digit+6>15; corrected+=6L<<(4*n);
                    }
                }
                c.A=(int)corrected;c.CY=carry||corrected>0xffffffffL;c.AC=lowAC;c.setZSP(c.A);
            }
            case 0x2f -> c.A=~c.A;
            case 0x37 -> c.CY=true;
            case 0x3f -> c.CY=!c.CY;
            case 0xc3 -> c.pc=fetch(c,8);
            case 0xc9 -> c.pc=c.memory.pop64();
            case 0xcd -> {long addr=fetch(c,8);c.memory.push64(c.pc);c.pc=addr;}
            case 0xd3 -> c.outputPort((int)fetch(c,1),(byte)c.A);
            case 0xdb -> c.A=c.inputPort((int)fetch(c,1))&255;
            case 0xe3 -> {long sp=c.memory.getSp(),v=c.memory.read64(sp);c.memory.write64(sp,pair(c,2));pair(c,2,v);}
            case 0xe9 -> c.pc=pair(c,2);
            case 0xeb -> {long v=pair(c,2);pair(c,2,pair(c,1));pair(c,1,v);}
            case 0xf3 -> c.disableInterrupts();
            case 0xf9 -> c.memory.setSp(pair(c,2));
            case 0xfb -> c.enableInterrupts();
            case 0xfd -> c.stopFromInstruction();
            default -> throw new IllegalStateException(String.format("Unknown opcode %02X",op));
        }
    }
    private static void rotate(Intel8080 c, int opcode, int count) {
        for (int step = 0; step < count; step++) {
            switch (opcode) {
                case 0x07 -> { c.CY = c.A < 0; c.A = Integer.rotateLeft(c.A, 1); }
                case 0x0f -> { c.CY = (c.A & 1) != 0; c.A = Integer.rotateRight(c.A, 1); }
                case 0x17 -> { boolean high = c.A < 0; c.A = (c.A << 1) | (c.CY ? 1 : 0); c.CY = high; }
                case 0x1f -> { boolean low = (c.A & 1) != 0; c.A = (c.A >>> 1) | (c.CY ? 0x80000000 : 0); c.CY = low; }
                default -> throw new IllegalArgumentException("Not an accumulator rotate opcode: " + opcode);
            }
        }
        c.instructionCycles += count;
    }
}
