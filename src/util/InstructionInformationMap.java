package util;


import java.util.HashMap;

public class InstructionInformationMap{
    private HashMap<String, Integer> mtoo;
    private HashMap<Integer, String> otom;
    private HashMap<Integer, Integer> otos;

    public InstructionInformationMap(){
        mtoo = new HashMap<>();
        otom = new HashMap<>();
        otos = new HashMap<>();
    }

    public void put(String mnemonic, int opcode, int size){
        mtoo.put(mnemonic, opcode);
        otom.put(opcode, mnemonic);
        otos.put(opcode, size);
    }
    public int geto(String mnemonic){
        return mtoo.getOrDefault(mnemonic, -1);
    }
    public String getm(int opcode){
        return otom.getOrDefault(opcode, "");
    }
    public int gets(String mnemonic){
        int opcode = geto(mnemonic);
        return otos.getOrDefault(opcode, -1);
    }
    public int gets(int opcode){
        return otos.getOrDefault(opcode, -1);
    }
}
