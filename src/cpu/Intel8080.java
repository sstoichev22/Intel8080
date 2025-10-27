package cpu;

public class Intel8080 {
    private byte A, B, C, D, E, H, L;
    //sp in rom
    private final Memory memory = new Memory(8192, 4096);
    private int pc=0;

    //S=sign flag
    //Z=zero flag
    //AC=Auxiliary flag(carry from bit 3->4)
    //P=parity flag(even number of 1 bits)
    //CY=carry flag
    private boolean S, Z, AC, P, CY;
    private boolean IE = true;          // global Interrupt Enable
    private boolean[] IM = new boolean[5]; // mask for 5 hardware interrupts (INTR, RST7.5, RST6.5, RST5.5, TRAP)
    private boolean serialInput;  // bit representing serial input
    private boolean HLT = false;
    private boolean interruptPending = false;
    private int interruptVector = 0;           // which interrupt to jump to
    private byte[] outputPorts = new byte[256];

    public void run(){
        pc = 0;
        while(true){
            if(HLT){
                if(interruptPending && IE){
                    HLT = false;
                    handleInterrupt();
                } else continue;
            }
            if(interruptPending && IE){
                handleInterrupt();
                continue;
            }

            byte instruction = memory.getRom(pc);
            execute(instruction);
        }
    }
    public void loadProgram(byte[] program){
        memory.loadRom(program);
    }
    public void execute(byte instruction){
        switch (instruction){
            case 0x00 -> System.out.println("NOP");
            case 0x01 -> {
                //load imm16 into BC
                C = memory.getRom(pc++);
                B = memory.getRom(pc++);
                System.out.printf("LXI B, %d\n", (B << 8) | C);
            }
            case 0x02 -> {
                //content of A stored in memory address BC
                short address = (short) ((B << 8) | C);
                memory.setRam(address, A);
                System.out.println("STAX B");
            }
            case 0x03 -> {
                //increment register pair BC
                int val = (B << 8) |C;
                val++;
                C = (byte) ((val & 0xFF00) >> 8);
                B = (byte) (val & 0x00FF);
                System.out.println("INX B");
            }
            case 0x04 -> {
                //increment register B
                byte old = B;
                B++;
                setFlags("Z,S,P,AC", B, old);
                System.out.println("INR B");
            }
            case 0x05 -> {
                //decrement register B
                byte old = B;
                B--;
                setFlags("Z,S,P,AC", B, old);
                System.out.println("DCR B");
            }
            case 0x06 -> {
                //put imm8 in B
                B = memory.getRom(pc++);
                System.out.printf("MVI B, %d\n", B);
            }
            case 0x07 -> {
                //'rotate' A 1 left
                byte bit7 = (byte) ((A & 0x80) >> 7);
                A <<= 1;
                A |= bit7;
                CY = bit7 != 0;
                System.out.println("RLC");
            }
            case 0x08 -> System.out.println("NOP");
            case 0x09 -> {
                //HL += BC
                short HL = (short) ((H << 8) | L);
                short BC = (short) ((B << 8) | C);
                HL += BC;
                H = (byte) ((HL & 0xFF00) >> 8);
                L = (byte) (HL & 0x00FF);
                CY = (HL & 0x10000) != 0;
                System.out.println("DAD B");
            }
            case 0x0A -> {
                //load A from memory address BC
                short address = (short) ((B << 8) | C);
                A = memory.getRam(address);
                System.out.println("LDAX B");
            }
            case 0x0B -> {
                //BC--
                short BC = (short) ((B << 8) | C);
                BC--;
                B = (byte) ((BC & 0xFF00) >> 8);
                C = (byte) (BC & 0x00FF);
                System.out.println("DCX B");
            }
            case 0x0C -> {
                //C++
                byte old = C;
                C++;
                setFlags("Z,S,P,AC", C, old);
                System.out.println("INR C");
            }
            case 0x0D -> {
                //C--
                byte old = C;
                C--;
                setFlags("Z,S,P,AC", C, old);
                System.out.println("DCR C");
            }
            case 0x0E -> {
                //C = imm8 in next byte
                C = memory.getRom(pc++);
                System.out.printf("MVI C, %d\n", C);
            }
            case 0x0F -> {
                //'rotate' A 1 right
                byte bit0 = (byte) (A & 0x01);
                A = (byte) ((A & 0xFF) >>> 1);
                A |= (byte) (bit0 << 7);
                CY = bit0 != 0;
                System.out.println("RRC");
            }
            case 0x10-> System.out.println("NOP");
            case 0x11->{
                //load DE with imm16 in next 2 bytes
                E = memory.getRom(pc++);
                D = memory.getRom(pc++);
                System.out.printf("LXI D, %d\n", (D << 8) | E);
            }
            case 0x12->{
                //content of A stored at address DE
                short address = (short) ((D << 8) | E);
                memory.setRam(address, A);
                System.out.println("STAX D");
            }
            case 0x13->{
                //DE++
                short DE = (short) ((D << 8) | E);
                DE++;
                D = (byte)((DE & 0xFF00) >> 8);
                E = (byte)(DE & 0x00FF);
                System.out.println("INX D");
            }
            case 0x14->{
                //D++
                byte old = D;
                D++;
                setFlags("Z,S,P,AC", D, old);
                System.out.println("INR D");
            }
            case 0x15->{
                //D--
                byte old = D;
                D--;
                setFlags("Z,S,P,AC", D, old);
                System.out.println("DCR D");
            }
            case 0x16->{
                //load D from imm8 on next byte
                D = memory.getRom(pc++);
                System.out.printf("MVI D, %d\n", D);
            }
            case 0x17->{
                //shift A left 1, bit0 = prevCY, CY = prev bit7
                boolean newCY = (A & 0x80) >>> 7 != 0;
                A = (byte)((A & 0xFF) << 1);
                A |= (byte) (CY?0x01:0x00);
                CY = newCY;
                System.out.println("RAL");
            }
            case 0x18-> System.out.println("NOP");
            case 0x19->{
                //HL += DE
                short HL = (short) ((H << 8) | L);
                short DE = (short) ((D << 8) | E);
                short result = (short) (HL + DE);
                H = (byte)((result & 0xFF00) >> 8);
                L = (byte)(result & 0x00FF);
                CY = (result & 0x10000) != 0;
                System.out.println("DAD D");
            }
            case 0x1A->{
                //address DE stored in A
                short address = (short) ((D << 8) | E);
                A = memory.getRam(address);
                System.out.println("LDAX D");
            }
            case 0x1B->{
                //DE--
                short DE = (short) ((D << 8) | E);
                DE--;
                D = (byte)((DE & 0xFF00) >> 8);
                E = (byte)(DE & 0x00FF);
                System.out.println("DCX D");
            }
            case 0x1C->{
                //E++
                byte old = E;
                E++;
                setFlags("Z,S,P,AC", E, old);
                System.out.println("INR E");
            }
            case 0x1D->{
                //E--
                byte old = E;
                E--;
                setFlags("Z,S,P,AC", E, old);
                System.out.println("DCR E");
            }
            case 0x1E->{
                //load imm8 in next byte into E
                E = memory.getRom(pc++);
                System.out.printf("MVI E, %d\n", E);
            }
            case 0x1F->{
                //shift A right 1, bit7 = prevbit7, CY = prevbit0
                CY = (byte) (A & 0x01) != 0;
                A = (byte)((A & 0xFF) >>> 1);
                A |= (byte)((A & 0x40) >> 6);
                System.out.println("RAR");
            }
            case 0x20->{
                //read interrupt mask/status into A
                A = 0;
                A |= (byte) (IE ? 0x01 : 0x00);          // bit 0 = IE
                if(IM[0]) A |= 0x02;              // bit 1 = RST7.5 mask
                if(IM[1]) A |= 0x04;              // bit 2 = RST6.5 mask
                if(IM[2]) A |= 0x08;              // bit 3 = RST5.5 mask
                if(IM[3]) A |= 0x10;              // bit 4 = TRAP mask
                A |= (byte) (serialInput ? 0x20 : 0x00); // bit 5 = serial input
                System.out.println("RIM");
            }
            case 0x21->{
                //load imm16 from next 2 bytes into HL
                L = memory.getRom(pc++);
                H = memory.getRom(pc++);
                System.out.printf("LXI H, %d", (H << 8) | L);
            }
            case 0x22->{
                //put HL into imm16 address
                byte low = memory.getRom(pc++);
                byte high = memory.getRom(pc++);
                short address = (short) ((high << 8) | low);
                memory.setRam(address, L);
                memory.setRam(address+1, H);
                System.out.printf("SHLD %s\n", Integer.toHexString(address));
            }
            case 0x23->{
                //HL++;
                short HL = (short) ((H << 8) | L);
                HL++;
                H = (byte)((HL & 0xFF00) >> 8);
                L = (byte)(HL & 0x00FF);
                System.out.println("INX H");
            }
            case 0x24->{
                //H++
                byte old = H;
                H++;
                setFlags("Z,S,P,AC", H, old);
                System.out.println("INR H");
            }
            case 0x25->{
                //H--
                byte old = H;
                H--;
                setFlags("Z,S,P,AC", H, old);
                System.out.println("DCR H");
            }
            case 0x26->{
                //load imm8 in next byte to H
                H = memory.getRom(pc++);
                System.out.printf("MVI H, %d\n", H);
            }
            case 0x27->{
                //fix A to be BCD
                byte oldA = A;
                int low = A & 0x0F;
                int high = (A & 0xF0) >> 4;
                int adjust = 0;
                if(low > 9 || AC) adjust += 0x06;
                if(high > 9 || CY || (low+adjust) > 0x0F) adjust += 0x60;
                int result = A+adjust;
                CY = (result & 0x100) != 0;
                A = (byte) (result & 0xFF);
                setFlags("Z,S,P,AC", A, oldA);
                System.out.println("DAA");
            }
            case 0x28-> System.out.println("NOP");
            case 0x29->{
                //HL += HL
                short HL = (short) ((H << 8) | L);
                HL += HL;
                H = (byte)((HL & 0xF0) >> 4);
                L = (byte)(HL & 0x0F);
                CY = (HL & 0x1000) != 0;
                System.out.println("DAD H");
            }
            case 0x2A->{
                //load contents of imm16 address into L and address+1 into H
                byte low = memory.getRom(pc++);
                byte high = memory.getRom(pc++);
                short address = (short) ((high << 8) | low);
                L = memory.getRam(address);
                H = memory.getRam(address+1);
                System.out.printf("LHLD %s\n", Integer.toHexString(address));
            }
            case 0x2B->{
                //HL--;
                short HL = (short) ((H << 8) | L);
                HL--;
                H = (byte)((HL & 0xF0) >> 4);
                L = (byte)(HL & 0x0F);
                System.out.println("DCX H");
            }
            case 0x2C->{
                //L++
                byte old = L;
                L++;
                setFlags("Z,S,P,AC", L, old);
                System.out.println("INR L");
            }
            case 0x2D->{
                //L--
                byte old = L;
                L--;
                setFlags("Z,S,P,AC", L, old);
                System.out.println("DCR L");
            }
            case 0x2E->{
                //load imm8 in next byte into L
                L = memory.getRom(pc++);
                System.out.printf("MVI L, %d\n", L);
            }
            case 0x2F->{
                A ^= 0xFF;
                System.out.println("CMA");
            }

            case 0x30->{
                //set interrupt mask/status into A
                IE = (A & 0x01) != 0;          // bit 0 = IE
                IM[0] = (A & 0x02) != 0;              // bit 1 = RST7.5 mask
                IM[1] = (A & 0x04) != 0;              // bit 2 = RST6.5 mask
                IM[2] = (A & 0x08) != 0;              // bit 3 = RST5.5 mask
                IM[3] = (A & 0x10) != 0;              // bit 4 = TRAP mask
                serialInput = (A & 0x20) != 0; // bit 5 = serial input
                System.out.println("SIM");
            }
            case 0x31->{
                //load imm16 from next 2 bytes into stack pointer
                byte low = memory.getRom(pc++);
                byte high = memory.getRom(pc++);
                short address = (short) ((high << 8) | low);
                memory.setSp(address);
                System.out.printf("LXI SP, %s", Integer.toHexString(address));
            }
            case 0x32->{
                //send A to address of imm16 in next 2 bytes
                byte low = memory.getRom(pc++);
                byte high = memory.getRom(pc++);
                short address = (short) ((high << 8) | low);
                memory.setRam(address, A);
            }
            case 0x33->{
                //sp++
                memory.incSp(1);
                System.out.println("INX SP");
            }
            case 0x34->{
                //val at address = val + 1 to address
                short address = (short) ((H << 8) | L);
                byte oldVal = memory.getRam(address);
                byte newVal = (byte) (oldVal + 1);
                memory.setRam(address, newVal);
                setFlags("Z,S,P,AC", newVal, oldVal);
                System.out.println("INR M");
            }
            case 0x35->{
                //val at address = val - 1 to address
                short address = (short) ((H << 8) | L);
                byte oldVal = memory.getRam(address);
                byte newVal = (byte) (oldVal - 1);
                memory.setRam(address, newVal);
                setFlags("Z,S,P,AC", newVal, oldVal);
                System.out.println("DCR M");
            }
            case 0x36->{
                //next byte to address HL
                short address = (short) ((H << 8) | L);
                byte val = memory.getRom(pc++);
                memory.setRam(address, val);
                System.out.printf("MVI M, %d\n", val);
            }
            case 0x37->{
                //set carry flag = 1
                CY = true;
                System.out.println("STC");
            }
            case 0x38-> System.out.println("NOP");
            case 0x39->{
                //add sp to HL
                short HL = (short) ((H << 8) | L);
                HL += memory.getSp();
                CY = (HL & 0x10000) != 0;
                System.out.println("DAD SP");
            }
            case 0x3A->{
                //imm16 address in next 2 bytes, val at address in A
                byte low = memory.getRom(pc++);
                byte high = memory.getRom(pc++);
                short address = (short) ((high << 8) | low);
                A = memory.getRam(address);
                System.out.printf("LDA %s\n", Integer.toHexString(address));
            }
            case 0x3B->{
                //sp--
                memory.incSp(-1);
                System.out.println("DCX SP");
            }
            case 0x3C->{
                //A++
                byte oldA = A;
                A++;
                setFlags("Z,S,P,AC", A, oldA);
                System.out.println("INR A");
            }
            case 0x3D->{
                //A--
                byte oldA = A;
                A--;
                setFlags("Z,S,P,AC", A, oldA);
                System.out.println("DCR A");
            }
            case 0x3E->{
                //load imm8 in next byte to A
                A = memory.getRom(pc++);
                System.out.printf("MVI A, %d", A);
            }
            case 0x3F->{
                //carry flag = ! carry flag
                CY = !CY;
                System.out.println("CMC");
            }
            case 0x40->{
                //move B->B
                B = B;
                System.out.println("MOV B, B");
            }
            case 0x41->{
                B = C;
                System.out.println("MOV B, C");
            }
            case 0x42->{
                B = D;
                System.out.println("MOV B, D");
            }
            case 0x43->{
                B = E;
                System.out.println("MOV B, E");
            }
            case 0x44->{
                B = H;
                System.out.println("MOV B, H");
            }
            case 0x45->{
                B = L;
                System.out.println("MOV B, L");
            }
            case 0x46->{
                //move value at address HL into B
                short address = (short) ((H << 8) | L);
                B = memory.getRam(address);
                System.out.println("MOV B, M");
            }
            case 0x47->{
                B = A;
                System.out.println("MOV B, A");
            }
            case 0x48->{
                C = B;
                System.out.println("MOV C, B");
            }
            case 0x49->{
                C = C;
                System.out.println("MOV C, C");
            }
            case 0x4A->{
                C = D;
                System.out.println("MOV C, D");
            }
            case 0x4B->{
                C = E;
                System.out.println("MOV C, E");
            }
            case 0x4C->{
                C = H;
                System.out.println("MOV C, H");
            }
            case 0x4D->{
                C = L;
                System.out.println("MOV C, L");
            }
            case 0x4E->{
                //move value at address HL into C
                short address = (short) ((H << 8) | L);
                C = memory.getRam(address);
                System.out.println("MOV C, M");
            }
            case 0x4F->{
                C = A;
                System.out.println("MOV C, A");
            }
            case 0x50->{
                D = B;
                System.out.println("MOV D, B");
            }
            case 0x51->{
                D = C;
                System.out.println("MOV D, C");
            }
            case 0x52->{
                D = D;
                System.out.println("MOV D, D");
            }
            case 0x53->{
                D = E;
                System.out.println("MOV D, E");
            }
            case 0x54->{
                D = H;
                System.out.println("MOV D, H");
            }
            case 0x55->{
                D = L;
                System.out.println("MOV D, L");
            }
            case 0x56->{
                //move value at address HL into D
                short address = (short) ((H << 8) | L);
                D = memory.getRam(address);
                System.out.println("MOV D, M");
            }
            case 0x57->{
                D = A;
                System.out.println("MOV D, A");
            }
            case 0x58->{
                E = B;
                System.out.println("MOV E, B");
            }
            case 0x59->{
                E = C;
                System.out.println("MOV E, C");
            }
            case 0x5A->{
                E = D;
                System.out.println("MOV E, D");
            }
            case 0x5B->{
                E = E;
                System.out.println("MOV E, E");
            }
            case 0x5C->{
                E = H;
                System.out.println("MOV E, H");
            }
            case 0x5D->{
                E = L;
                System.out.println("MOV E, L");
            }
            case 0x5E->{
                //move value at address HL into E
                short address = (short) ((H << 8) | L);
                E = memory.getRam(address);
                System.out.println("MOV E, M");
            }
            case 0x5F->{
                E = A;
                System.out.println("MOV E, A");
            }
            case 0x60->{
                H = B;
                System.out.println("MOV H, B");
            }
            case 0x61->{
                H = C;
                System.out.println("MOV H, C");
            }
            case 0x62->{
                H = D;
                System.out.println("MOV H, D");
            }
            case 0x63->{
                H = E;
                System.out.println("MOV H, E");
            }
            case 0x64->{
                H = H;
                System.out.println("MOV H, H");
            }
            case 0x65->{
                H = L;
                System.out.println("MOV H, L");
            }
            case 0x66->{
                //move value at address HL into H
                short address = (short) ((H << 8) | L);
                H = memory.getRam(address);
                System.out.println("MOV H, M");
            }
            case 0x67->{
                H = A;
                System.out.println("MOV H, A");
            }
            case 0x68->{
                L = B;
                System.out.println("MOV L, B");
            }
            case 0x69->{
                //this is instruction 0x69, nice
                L = C;
                System.out.println("MOV L, C");
            }
            case 0x6A->{
                L = D;
                System.out.println("MOV L, D");
            }
            case 0x6B->{
                L = E;
                System.out.println("MOV L, E");
            }
            case 0x6C->{
                L = H;
                System.out.println("MOV L, H");
            }
            case 0x6D->{
                L = L;
                System.out.println("MOV L, L");
            }
            case 0x6E->{
                //move value at address HL into L
                short address = (short) ((H << 8) | L);
                L = memory.getRam(address);
                System.out.println("MOV L, M");
            }
            case 0x6F->{
                L = A;
                System.out.println("MOV L, A");
            }
            case 0x70->{
                //move value in B to address in HL
                short address = (short) ((H << 8) | L);
                memory.setRam(address, B);
                System.out.println("MOV M, B");
            }
            case 0x71->{
                //move value in C to address in HL
                short address = (short) ((H << 8) | L);
                memory.setRam(address, C);
                System.out.println("MOV M, C");
            }
            case 0x72->{
                //move value in D to address in HL
                short address = (short) ((H << 8) | L);
                memory.setRam(address, D);
                System.out.println("MOV M, D");
            }
            case 0x73->{
                //move value in E to address in HL
                short address = (short) ((H << 8) | L);
                memory.setRam(address, E);
                System.out.println("MOV M, E");
            }
            case 0x74->{
                //move value in H to address in HL
                short address = (short) ((H << 8) | L);
                memory.setRam(address, H);
                System.out.println("MOV M, H");
            }
            case 0x75->{
                //move value in L to address in HL
                short address = (short) ((H << 8) | L);
                memory.setRam(address, L);
                System.out.println("MOV M, L");
            }
            case 0x76->{
                //halts until an interrupt
                HLT = true;
                System.out.println("HLT");
            }
            case 0x77->{
                //move value in A to address in HL
                short address = (short) ((H << 8) | L);
                memory.setRam(address, A);
                System.out.println("MOV M, A");
            }
            case 0x78->{
                A = B;
                System.out.println("MOV A, B");
            }
            case 0x79->{
                A = C;
                System.out.println("MOV A, C");
            }
            case 0x7A->{
                A = D;
                System.out.println("MOV A, D");
            }
            case 0x7B->{
                A = E;
                System.out.println("MOV A, E");
            }
            case 0x7C->{
                A = H;
                System.out.println("MOV A, H");
            }
            case 0x7D->{
                A = L;
                System.out.println("MOV A, L");
            }
            case 0x7E->{
                //move value at address HL into A
                short address = (short) ((H << 8) | L);
                A = memory.getRam(address);
                System.out.println("MOV A, M");
            }
            case 0x7F->{
                A = A;
                System.out.println("MOV A, A");
            }
            case (byte) 0x80 ->{
                //add B to A
                byte oldA = A;
                A += B;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ADD B");
            }
            case (byte) 0x81 ->{
                //add C to A
                byte oldA = A;
                A += C;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ADD C");
            }
            case (byte) 0x82 ->{
                //add D to A
                byte oldA = A;
                A += D;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ADD D");
            }
            case (byte) 0x83 ->{
                //add E to A
                byte oldA = A;
                A += E;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ADD E");
            }
            case (byte) 0x84 ->{
                //add H to A
                byte oldA = A;
                A += H;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ADD H");
            }
            case (byte) 0x85 ->{
                //add L to A
                byte oldA = A;
                A += L;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ADD L");
            }
            case (byte) 0x86 ->{
                //add val at address HL to A
                byte oldA = A;
                A += memory.getRam((H << 8) | L);
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ADD M");
            }
            case (byte) 0x87 ->{
                //add A to A
                byte oldA = A;
                A += A;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ADD A");
            }
            case (byte) 0x88 ->{
                //add B to A with carry
                byte oldA = A;
                A += (byte) (B + (CY?1:0));
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ADC B");
            }
            case (byte) 0x89 ->{
                //add C to A with carry
                byte oldA = A;
                A += (byte) (C + (CY?1:0));
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ADC C");
            }
            case (byte) 0x8A ->{
                //add D to A with carry
                byte oldA = A;
                A += (byte) (D + (CY?1:0));
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ADC D");
            }
            case (byte) 0x8B ->{
                //add E to A with carry
                byte oldA = A;
                A += (byte) (E + (CY?1:0));
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ADC E");
            }
            case (byte) 0x8C ->{
                //add H to A with carry
                byte oldA = A;
                A += (byte) (H + (CY?1:0));
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ADC H");
            }
            case (byte) 0x8D ->{
                //add L to A with carry
                byte oldA = A;
                A += (byte) (L + (CY?1:0));
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ADC L");
            }
            case (byte) 0x8E ->{
                //add val at HL to A with carry
                byte oldA = A;
                A += (byte) (memory.getRam((H << 8) | L) + (CY?1:0));
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ADC M");
            }
            case (byte) 0x8F ->{
                //add A to A with carry
                byte oldA = A;
                A += (byte) (A + (CY?1:0));
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ADC A");
            }
            case (byte) 0x90->{
                //sub B to A
                byte oldA = A;
                A -= B;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("SUB B");
            }
            case (byte) 0x91->{
                //sub C to A
                byte oldA = A;
                A -= C;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("SUB C");
            }
            case (byte) 0x92->{
                //sub D to A
                byte oldA = A;
                A -= D;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("SUB D");
            }
            case (byte) 0x93->{
                //sub E to A
                byte oldA = A;
                A -= E;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("SUB E");
            }
            case (byte) 0x94->{
                //sub H to A
                byte oldA = A;
                A -= H;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("SUB H");
            }
            case (byte) 0x95->{
                //sub L to A
                byte oldA = A;
                A -= L;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("SUB L");
            }
            case (byte) 0x96->{
                //sub val at HL to A
                byte oldA = A;
                A -= memory.getRam((H << 8) | L);
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("SUB M");
            }
            case (byte) 0x97->{
                //sub A to A
                byte oldA = A;
                A -= A;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("SUB A");
            }
            case (byte) 0x98->{
                //sub B to A with borrow
                byte oldA = A;
                A -= (byte) (B - (CY?1:0));
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("SBB B");
            }
            case (byte) 0x99->{
                //sub C to A with borrow
                byte oldA = A;
                A -= (byte) (C - (CY?1:0));
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("SBB C");
            }
            case (byte) 0x9A->{
                //sub D to A with borrow
                byte oldA = A;
                A -= (byte) (D - (CY?1:0));
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("SBB D");
            }
            case (byte) 0x9B->{
                //sub E to A with borrow
                byte oldA = A;
                A -= (byte) (E - (CY?1:0));
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("SBB E");
            }
            case (byte) 0x9C->{
                //sub H to A with borrow
                byte oldA = A;
                A -= (byte) (H - (CY?1:0));
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("SBB H");
            }
            case (byte) 0x9D->{
                //sub L to A with borrow
                byte oldA = A;
                A -= (byte) (L - (CY?1:0));
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("SBB L");
            }
            case (byte) 0x9E->{
                //sub val at HL to A with borrow
                byte oldA = A;
                A -= (byte) (memory.getRam((H << 8) | L) - (CY?1:0));
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("SBB M");
            }
            case (byte) 0x9F->{
                //sub A to A with borrow
                byte oldA = A;
                A -= (byte) (A - (CY?1:0));
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("SBB A");
            }
            case (byte) 0xA0->{
                //A and B
                byte oldA = A;
                A &= B;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ANA B");
            }
            case (byte) 0xA1->{
                //A and C
                byte oldA = A;
                A &= C;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ANA C");
            }
            case (byte) 0xA2->{
                //A and D
                byte oldA = A;
                A &= D;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ANA D");
            }
            case (byte) 0xA3->{
                //A and E
                byte oldA = A;
                A &= E;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ANA E");
            }
            case (byte) 0xA4->{
                //A and H
                byte oldA = A;
                A &= H;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ANA H");
            }
            case (byte) 0xA5->{
                //A and L
                byte oldA = A;
                A &= L;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ANA L");
            }
            case (byte) 0xA6->{
                //A and val at HL
                byte oldA = A;
                A &= memory.getRam((H << 8) | L);
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ANA M");
            }
            case (byte) 0xA7->{
                //A and A
                byte oldA = A;
                A &= A;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ANA A");
            }
            case (byte) 0xA8->{
                //A xor B
                byte oldA = A;
                A ^= B;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("XRA B");
            }
            case (byte) 0xA9->{
                //A xor C
                byte oldA = A;
                A ^= C;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("XRA C");
            }
            case (byte) 0xAA->{
                //A xor D
                byte oldA = A;
                A ^= D;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("XRA D");
            }
            case (byte) 0xAB->{
                //A xor E
                byte oldA = A;
                A ^= E;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("XRA E");
            }
            case (byte) 0xAC->{
                //A xor H
                byte oldA = A;
                A ^= H;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("XRA H");
            }
            case (byte) 0xAD->{
                //A xor L
                byte oldA = A;
                A ^= L;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("XRA L");
            }
            case (byte) 0xAE->{
                //A xor val at HL
                byte oldA = A;
                A ^= memory.getRam((H << 8) | L);
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("XRA M");
            }
            case (byte) 0xAF->{
                //A xor A
                byte oldA = A;
                A ^= A;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("XRA A");
            }
            case (byte) 0xB0->{
                //A or B
                byte oldA = A;
                A |= B;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ORA B");
            }
            case (byte) 0xB1->{
                //A or C
                byte oldA = A;
                A |= C;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ORA C");
            }
            case (byte) 0xB2->{
                //A or D
                byte oldA = A;
                A |= D;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ORA D");
            }
            case (byte) 0xB3->{
                //A or E
                byte oldA = A;
                A |= E;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ORA E");
            }
            case (byte) 0xB4->{
                //A or H
                byte oldA = A;
                A |= H;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ORA H");
            }
            case (byte) 0xB5->{
                //A or L
                byte oldA = A;
                A |= L;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ORA L");
            }
            case (byte) 0xB6->{
                //A or val at HL
                byte oldA = A;
                A |= memory.getRam((H << 8) | L);
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ORA M");
            }
            case (byte) 0xB7->{
                //A or A
                byte oldA = A;
                A |= A;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.println("ORA A");
            }
            case (byte) 0xB8->{
                //compare A and B
                byte result = (byte) (A - B);
                setFlags("Z,S,P,CY,AC", result, A);
                System.out.println("CMP B");
            }
            case (byte) 0xB9->{
                //compare A and C
                byte result = (byte) (A - C);
                setFlags("Z,S,P,CY,AC", result, A);
                System.out.println("CMP C");
            }
            case (byte) 0xBA->{
                //compare A and D
                byte result = (byte) (A - D);
                setFlags("Z,S,P,CY,AC", result, A);
                System.out.println("CMP D");
            }
            case (byte) 0xBB->{
                //compare A and E
                byte result = (byte) (A - E);
                setFlags("Z,S,P,CY,AC", result, A);
                System.out.println("CMP E");
            }
            case (byte) 0xBC->{
                //compare A and H
                byte result = (byte) (A - H);
                setFlags("Z,S,P,CY,AC", result, A);
                System.out.println("CMP H");
            }
            case (byte) 0xBD->{
                //compare A and L
                byte result = (byte) (A - L);
                setFlags("Z,S,P,CY,AC", result, A);
                System.out.println("CMP L");
            }
            case (byte) 0xBE->{
                //compare A and val at HL
                byte result = (byte) (A - memory.getRam((H << 8) | L));
                setFlags("Z,S,P,CY,AC", result, A);
                System.out.println("CMP M");
            }
            case (byte) 0xBF->{
                //compare A and A
                byte result = (byte) (A - A);
                setFlags("Z,S,P,CY,AC", result, A);
                System.out.println("CMP A");
            }
            case (byte) 0xC0->{
                //ret if nz
                if(!Z){
                    byte low = memory.pop();
                    byte high = memory.pop();
                    pc = (high << 8) | low;
                }
                System.out.println("RNZ");
            }
            case (byte) 0xC1->{
                //pop into BC
                C = memory.pop();
                B = memory.pop();
            }
            case (byte) 0xC2->{
                //jump if nz to imm16 address
                byte low = memory.getRom(pc++);
                byte high = memory.getRom(pc++);
                short address = (short) ((high << 8) | low);
                if(!Z){
                    pc = address;
                }
                System.out.printf("JNZ %s\n", Integer.toHexString(address));
            }
            case (byte) 0xC3->{
                //jump to imm16 address
                byte low = memory.getRom(pc++);
                byte high = memory.getRom(pc++);
                short address = (short) ((high << 8) | low);
                pc = address;
                System.out.printf("JMP %s\n", Integer.toHexString(address));
            }
            case (byte) 0xC4->{
                //call if nz to imm16 address
                byte low = memory.getRom(pc++);
                byte high = memory.getRom(pc++);
                short address = (short) ((high << 8) | low);
                pc = address;
                System.out.printf("CNZ %s\n", Integer.toHexString(address));
            }
            case (byte) 0xC5->{
                //push BC to stack (sp-1, sp-2)
                memory.push(B);
                memory.push(C);
                System.out.println("PUSH B");
            }
            case (byte) 0xC6->{
                //add imm8 in next byte to A
                byte imm8 = memory.getRom(pc++);
                A += imm8;
                System.out.printf("ADI %d\n", imm8);
            }
            case (byte) 0xC7->{
                //jumps to address 0x00
                byte low = (byte)((pc & 0xFF00) >> 8);
                byte high = (byte)(pc & 0x00FF);
                memory.push(high);
                memory.push(low);
                pc = 0x00;
                System.out.println("RST 0");
            }
            case (byte) 0xC8->{
                //return if z
                if(Z){
                    byte low = memory.pop();
                    byte high = memory.pop();
                    pc = ((high & 0xFF) << 8) | (low & 0xFF);
                }
                System.out.println("RZ");
            }
            case (byte) 0xC9->{
                //return
                byte low = memory.pop();
                byte high = memory.pop();
                pc = ((high & 0xFF) << 8) | (low & 0xFF);
                System.out.println("RET");
            }
            case (byte) 0xCA->{
                //jumps to imm16 address if z
                byte low = memory.getRom(pc++);
                byte high = memory.getRom(pc++);
                short address = (short) ((high << 8) | low);
                if(Z){
                    pc = address;
                }
                System.out.printf("JZ %s\n", Integer.toHexString(address));
            }
            case (byte) 0xCB-> System.out.println("NOP");
            case (byte) 0xCC->{
                //calls to imm16 address if z
                byte low = memory.getRom(pc++);
                byte high = memory.getRom(pc++);
                short address = (short) ((high << 8) | low);
                if(Z){
                    byte pclow = (byte)((pc & 0xFF00) >> 8);
                    byte pchigh = (byte)(pc & 0x00FF);
                    memory.push(pchigh);
                    memory.push(pclow);
                    pc = address;
                }
                System.out.printf("CZ %s\n", Integer.toHexString(address));
            }
            case (byte) 0xCD->{
                //calls to imm16 address
                byte low = memory.getRom(pc++);
                byte high = memory.getRom(pc++);
                short address = (short) ((high << 8) | low);
                byte pclow = (byte)((pc & 0xFF00) >> 8);
                byte pchigh = (byte)(pc & 0x00FF);
                memory.push(pchigh);
                memory.push(pclow);
                pc = address;
                System.out.printf("CALL %s\n", Integer.toHexString(address));
            }
            case (byte) 0xCE->{
                // add to A with carry immediate
                byte oldA = A;
                byte val = memory.getRom(pc++);
                A = (byte) (A + val + (CY?1:0));
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.printf("ACI %d\n", val);
            }
            case (byte) 0xCF->{
                //jumps to address 0x08
                byte low = (byte)((pc & 0xFF00) >> 8);
                byte high = (byte)(pc & 0x00FF);
                memory.push(high);
                memory.push(low);
                pc = 0x08;
                System.out.println("RST 1");
            }
            case (byte) 0xD0->{
                //if ncy, ret
                if(!CY){
                    byte low = memory.pop();
                    byte high = memory.pop();
                    pc = ((high & 0xFF) << 8 | (low & 0xFF));
                }
                System.out.println("RNC");
            }
            case (byte) 0xD1->{
                //pop into DE
                E = memory.pop();
                D = memory.pop();
                System.out.println("POP D");
            }
            case (byte) 0xD2->{
                //jump if ncy to imm16 address
                byte low = memory.getRom(pc++);
                byte high = memory.getRom(pc++);
                short address = (short) ((high << 8) | low);
                if(!CY){
                    pc = address;
                }
                System.out.printf("JNC %s\n", Integer.toHexString(address));
            }
            case (byte) 0xD3->{
                //send A to output port imm8 in next byte
                byte imm8 = memory.getRom(pc++);
                outputPorts[imm8 & 0xFF] = A;
                System.out.printf("OUT %d\n", imm8);
            }
            case (byte) 0xD4->{
                //calls to imm16 address if ncy
                byte low = memory.getRom(pc++);
                byte high = memory.getRom(pc++);
                short address = (short) ((high << 8) | low);
                if(!CY){
                    byte pclow = (byte)((pc & 0xFF00) >> 8);
                    byte pchigh = (byte)(pc & 0x00FF);
                    memory.push(pchigh);
                    memory.push(pclow);
                    pc = address;
                }
                System.out.printf("CNC %s\n", Integer.toHexString(address));
            }
            case (byte) 0xD5->{
                //push DE to stack (sp-1, sp-2)
                memory.push(D);
                memory.push(E);
                System.out.println("PUSH D");
            }
            case (byte) 0xD6->{
                //A sub imm8
                byte val = memory.getRom(pc++);
                byte oldA = A;
                A -= val;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.printf("SUI %d\n", val);
            }
            case (byte) 0xD7->{
                //jumps to address 0x10
                byte low = (byte)((pc & 0xFF00) >> 8);
                byte high = (byte)(pc & 0x00FF);
                memory.push(high);
                memory.push(low);
                pc = 0x10;
                System.out.println("RST 2");
            }
            case (byte) 0xD8->{
                //if cy, ret
                if(CY){
                    byte low = memory.pop();
                    byte high = memory.pop();
                    pc = ((high & 0xFF) << 8 | (low & 0xFF));
                }
                System.out.println("RC");
            }
            case (byte) 0xD9-> System.out.println("NOP");
            case (byte) 0xDA->{
                //jump if cy to imm16 address
                byte low = memory.getRom(pc++);
                byte high = memory.getRom(pc++);
                short address = (short) ((high << 8) | low);
                if(CY){
                    pc = address;
                }
                System.out.printf("JC %s\n", Integer.toHexString(address));
            }
            case (byte) 0xDB->{
                //send imm8 outputPort in next byte to A
                byte imm8 = memory.getRom(pc++);
                A = outputPorts[imm8 & 0xFF];
                System.out.printf("IN, %d\n", imm8);
            }
            case (byte) 0xDC->{
                //calls to imm16 address if cy
                byte low = memory.getRom(pc++);
                byte high = memory.getRom(pc++);
                short address = (short) ((high << 8) | low);
                if(CY){
                    byte pclow = (byte)((pc & 0xFF00) >> 8);
                    byte pchigh = (byte)(pc & 0x00FF);
                    memory.push(pchigh);
                    memory.push(pclow);
                    pc = address;
                }
                System.out.printf("CC %s\n", Integer.toHexString(address));
            }
            case (byte) 0xDD-> System.out.println("NOP");
            case (byte) 0xDE->{
                //A sub imm8 in next byte and carry
                byte imm8 = memory.getRom(pc++);
                byte oldA = A;
                A = (byte) (A - imm8 - (CY?1:0));
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.printf("SBI %d\n", imm8);
            }
            case (byte) 0xDF->{
                //jumps to address 0x18
                byte low = (byte)((pc & 0xFF00) >> 8);
                byte high = (byte)(pc & 0x00FF);
                memory.push(high);
                memory.push(low);
                pc = 0x18;
                System.out.println("RST 3");
            }
            case (byte) 0xE0->{
                //if odd parity return
                if(!P){
                    byte low = memory.pop();
                    byte high = memory.pop();
                    pc = ((high & 0xFF) << 8 | (low & 0xFF));
                }
                System.out.println("RPO");
            }
            case (byte) 0xE1->{
                //pop into HL
                L = memory.pop();
                H = memory.pop();
                System.out.println("POP H");
            }
            case (byte) 0xE2->{
                //if parity odd, jump to imm16 address in next 2 bytes
                byte low = memory.getRom(pc++);
                byte high = memory.getRom(pc++);
                short address = (short) ((high << 8) & low);
                if(!P){
                    byte pclow = (byte)((pc & 0xFF00) >> 8);
                    byte pchigh = (byte)(pc & 0x00FF);
                    memory.push(pchigh);
                    memory.push(pclow);
                    pc = address;
                }
                System.out.printf("JPO %s\n", Integer.toHexString(address));
            }
            case (byte) 0xE3->{
                //switch sp, sp+1 with HL
                byte l = L, h = H;
                int sp = memory.getSp();
                L = memory.getRam(sp);
                H = memory.getRam(sp+1);
                memory.setRam(sp, l);
                memory.setRam(sp+1, h);
                System.out.println("XTHL");
            }
            case (byte) 0xE4->{
                //calls to imm16 address if parity odd
                byte low = memory.getRom(pc++);
                byte high = memory.getRom(pc++);
                short address = (short) ((high << 8) | low);
                if(!P){
                    byte pclow = (byte)((pc & 0xFF00) >> 8);
                    byte pchigh = (byte)(pc & 0x00FF);
                    memory.push(pchigh);
                    memory.push(pclow);
                    pc = address;
                }
                System.out.printf("CPO %s\n", Integer.toHexString(address));
            }
            case (byte) 0xE5->{
                //push HL to stack (sp-1, sp-2)
                memory.push(H);
                memory.push(L);
                System.out.println("PUSH H");
            }
            case (byte) 0xE6->{
                //A and imm8 in next byte
                byte imm8 = memory.getRom(pc++);
                byte oldA = A;
                A &= imm8;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.printf("ANI %d", imm8);
            }
            case (byte) 0xE7->{
                //jumps to address 0x20
                byte low = (byte)((pc & 0xFF00) >> 8);
                byte high = (byte)(pc & 0x00FF);
                memory.push(high);
                memory.push(low);
                pc = 0x20;
                System.out.println("RST 4");
            }
            case (byte) 0xE8->{
                //if even parity return
                if(P){
                    byte low = memory.pop();
                    byte high = memory.pop();
                    pc = ((high & 0xFF) << 8 | (low & 0xFF));
                }
                System.out.println("RPE");
            }
            case (byte) 0xE9->{
                //put HL in pc
                pc = ((H & 0xFF) << 8) | (L & 0xFF);
                System.out.println("PCHL");
            }
            case (byte) 0xEA->{
                //if parity even, jump to imm16 address in next 2 bytes
                byte low = memory.getRom(pc++);
                byte high = memory.getRom(pc++);
                short address = (short) ((high << 8) & low);
                if(P){
                    byte pclow = (byte)((pc & 0xFF00) >> 8);
                    byte pchigh = (byte)(pc & 0x00FF);
                    memory.push(pchigh);
                    memory.push(pclow);
                    pc = address;
                }
                System.out.printf("JPE %s\n", Integer.toHexString(address));
            }
            case (byte) 0xEB->{
                //exchange H,D and L,E
                byte h = H, l = L;
                H = D;
                L = E;
                D = h;
                E = l;
                System.out.println("XCHG");
            }
            case (byte) 0xEC->{
                //calls to imm16 address if parity even
                byte low = memory.getRom(pc++);
                byte high = memory.getRom(pc++);
                short address = (short) ((high << 8) | low);
                if(P){
                    byte pclow = (byte)((pc & 0xFF00) >> 8);
                    byte pchigh = (byte)(pc & 0x00FF);
                    memory.push(pchigh);
                    memory.push(pclow);
                    pc = address;
                }
                System.out.printf("CPE %s\n", Integer.toHexString(address));
            }
            case (byte) 0xED-> System.out.println("NOP");
            case (byte) 0xEE->{
                //A xor imm8 in next byte
                byte imm8 = memory.getRom(pc++);
                byte oldA = A;
                A ^= imm8;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.printf("XRI %d", imm8);
            }
            case (byte) 0xEF->{
                //jumps to address 0x28
                byte low = (byte)((pc & 0xFF00) >> 8);
                byte high = (byte)(pc & 0x00FF);
                memory.push(high);
                memory.push(low);
                pc = 0x28;
                System.out.println("RST 5");
            }
            case (byte) 0xF0->{
                //if positive return
                if(!S){
                    byte low = memory.pop();
                    byte high = memory.pop();
                    pc = ((high & 0xFF) << 8 | (low & 0xFF));
                }
                System.out.println("RP");
            }
            case (byte) 0xF1->{
                //val at sp to flags, val at sp+1 to A, sp+=2
                byte flags = memory.pop();
                byte acc = memory.pop();
                A = acc;
                CY = (flags & 0x01) != 0;
                P  = (flags & 0x04) != 0;
                AC = (flags & 0x10) != 0;
                Z  = (flags & 0x40) != 0;
                S  = (flags & 0x80) != 0;
                System.out.println("POP PSW");
            }
            case (byte) 0xF2->{
                //jump to imm16 in next 2 bytes if positive
                byte low = memory.getRom(pc++);
                byte high = memory.getRom(pc++);
                short address = (short) ((high << 8) & low);
                if(!S){
                    byte pclow = (byte)((pc & 0xFF00) >> 8);
                    byte pchigh = (byte)(pc & 0x00FF);
                    memory.push(pchigh);
                    memory.push(pclow);
                    pc = address;
                }
                System.out.printf("JP %s\n", Integer.toHexString(address));
            }
            case (byte) 0xF3->{
                //disable interrupts
                IE = false;
                System.out.println("DI");
            }
            case (byte) 0xF4->{
                //calls to imm16 address if positive
                byte low = memory.getRom(pc++);
                byte high = memory.getRom(pc++);
                short address = (short) ((high << 8) | low);
                if(!S){
                    byte pclow = (byte)((pc & 0xFF00) >> 8);
                    byte pchigh = (byte)(pc & 0x00FF);
                    memory.push(pchigh);
                    memory.push(pclow);
                    pc = address;
                }
                System.out.printf("CP %s\n", Integer.toHexString(address));
            }
            case (byte) 0xF5->{
                //flags to val at sp-2, A to val at sp-1, sp-=2
                byte flags = (byte) (((S?1:0) << 7) | ((Z?1:0) << 6) | ((AC?1:0) << 4) | ((P?1:0) << 2) | 0x02 | ((CY?1:0)));
                int sp = memory.getSp();
                memory.setRam(sp-2, flags);
                memory.setRam(sp-1, A);
                System.out.println("PUSH PSW");
            }
            case (byte) 0xF6->{
                //A or imm8 in next byte
                byte imm8 = memory.getRom(pc++);
                byte oldA = A;
                A |= imm8;
                setFlags("Z,S,P,CY,AC", A, oldA);
                System.out.printf("ORI %d", imm8);
            }
            case (byte) 0xF7->{
                //jumps to address 0x30
                byte low = (byte)((pc & 0xFF00) >> 8);
                byte high = (byte)(pc & 0x00FF);
                memory.push(high);
                memory.push(low);
                pc = 0x30;
                System.out.println("RST 6");
            }
            case (byte) 0xF8->{
                //if negative return
                if(S){
                    byte low = memory.pop();
                    byte high = memory.pop();
                    pc = ((high & 0xFF) << 8 | (low & 0xFF));
                }
                System.out.println("RM");
            }
            case (byte) 0xF9->{
                //sp = HL
                short HL = (short)((H & 0xFF) << 8 | (L & 0xFF));
                memory.setSp(HL);
                System.out.println("SPHL");
            }
            case (byte) 0xFA->{
                //jump to imm16 in next 2 bytes if negative
                byte low = memory.getRom(pc++);
                byte high = memory.getRom(pc++);
                short address = (short) ((high << 8) & low);
                if(S){
                    byte pclow = (byte)((pc & 0xFF00) >> 8);
                    byte pchigh = (byte)(pc & 0x00FF);
                    memory.push(pchigh);
                    memory.push(pclow);
                    pc = address;
                }
                System.out.printf("JM %s\n", Integer.toHexString(address));
            }
            case (byte) 0xFB->{
                //enable interrupts
                IE = true;
                System.out.println("EI");
            }
            case (byte) 0xFC->{
                //calls to imm16 address if negative
                byte low = memory.getRom(pc++);
                byte high = memory.getRom(pc++);
                short address = (short) ((high << 8) | low);
                if(S){
                    byte pclow = (byte)((pc & 0xFF00) >> 8);
                    byte pchigh = (byte)(pc & 0x00FF);
                    memory.push(pchigh);
                    memory.push(pclow);
                    pc = address;
                }
                System.out.printf("CM %s\n", Integer.toHexString(address));
            }
            case (byte) 0xFD-> System.out.println("NOP");
            case (byte) 0xFE->{
                //compare A and val in next byte
                byte imm8 = memory.getRom(pc++);
                byte result = (byte) (A - imm8);
                setFlags("Z,S,P,CY,AC", result, A);
                System.out.printf("CPI %d\n", imm8);
            }
            case (byte) 0xFF->{
                //jumps to address 0x38
                byte low = (byte)((pc & 0xFF00) >> 8);
                byte high = (byte)(pc & 0x00FF);
                memory.push(high);
                memory.push(low);
                pc = 0x38;
                System.out.println("RST 7");
            }
        }
    }
    private void setFlags(String flags, byte... args){
        if(flags.contains("Z")) Z = (args[0] & 0xFF) == 0;
        if(flags.contains("S")) S = (args[0] & 0x80) != 0;
        if(flags.contains("P")) P = Integer.bitCount(args[0] & 0xFF) % 2 == 0;
        if(flags.contains("AC")) AC = ((args[0] & 0x0F) + (args[1] & 0x0F)) > 0x0F;
        if(flags.contains("CY")) CY = ((args[0] & 0xFF) + (args[1] & 0xFF)) > 0xFF;
    }
    private void handleInterrupt() {
        //push current pc onto stack
        memory.push((byte)((pc >> 8) & 0xFF));
        memory.push((byte)(pc & 0xFF));

        //jump to interrupt address
        pc = interruptVector;

        //reset interrupt flags
        interruptPending = false;
    }
    public void triggerInterrupt(int vector) {
        if (IE) {
            interruptPending = true;
            interruptVector = vector;
        }
    }


}
