package util;


import java.util.HashMap;

public class InstructionInformationList{
    private HashMap<String, Integer> mtoo;
    private HashMap<Integer, String> otom;
    private HashMap<Integer, Integer> otos;

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

//    public void put(String mnemonic, int opcode, int size){
//        mtoo.put(mnemonic, opcode);
//        otom.put(opcode, mnemonic);
//        otos.put(opcode, size);
//    }
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
    public void print(){
        for(int i = 0 ; i < 0xFF; i++){
            System.out.println(i + ": " + getm(i) + " " + gets(i));
        }
    }
}
