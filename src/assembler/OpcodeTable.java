package assembler;

import util.InstructionInformationMap;

public class OpcodeTable {
    public static final InstructionInformationMap OPCODES;

    static {
        OPCODES = new InstructionInformationMap();
        OPCODES.put("NOP", 0x00, 1);
        OPCODES.put("LXI", 0x01, 3);




    }
}
