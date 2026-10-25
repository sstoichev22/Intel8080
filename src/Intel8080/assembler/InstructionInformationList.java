package Intel8080.assembler;

import java.util.HashMap;
import java.util.Locale;

/** Legacy opcode/mnemonic/size lookup table kept for compatibility with older tooling. */
public class InstructionInformationList{
    private final HashMap<String, Integer> mtoo;
    private final HashMap<Integer, String> otom;
    private final HashMap<Integer, Integer> otos;

    /** Builds opcode lookup tables from repeating opcode, mnemonic, and size triplets. */
    @SafeVarargs
    public <T> InstructionInformationList(T...args){
        mtoo = new HashMap<>();
        otom = new HashMap<>();
        otos = new HashMap<>();
        for(int i = 0 ; i < args.length; i+=3){
            int opcode = (int) args[i];
            String mnemonic = (String) args[i+1];
            int size = (int) args[i+2];
            mtoo.put(mnemonic, opcode);
            otom.put(opcode, mnemonic);
            otos.put(opcode, size);
        }
    }

    /** Returns the opcode for a mnemonic, ignoring case, or {@code -1} when it is unknown. */
    public int geto(String mnemonic){
        return mtoo.getOrDefault(mnemonic.toUpperCase(Locale.ROOT), -1);
    }
    /** Returns the mnemonic for an opcode, or an empty string when it is unknown. */
    public String getm(int opcode){
        return otom.getOrDefault(opcode, "");
    }
    /** Returns the encoded size for a mnemonic, ignoring case, or {@code -1} when it is unknown. */
    public int gets(String mnemonic){
        int opcode = geto(mnemonic);
        return otos.getOrDefault(opcode, -1);
    }
    /** Returns the encoded size for an opcode, or {@code -1} when it is unknown. */
    public int gets(int opcode){
        return otos.getOrDefault(opcode, -1);
    }
    /** Prints one mnemonic-and-size row for every byte opcode from {@code 0x00} through {@code 0xFF}. */
    public void print(){
        for(int i = 0 ; i <= 0xFF; i++){
            System.out.println(i + ": " + getm(i) + " " + gets(i));
        }
    }
}
