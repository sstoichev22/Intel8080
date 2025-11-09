package util;

import cpu.Memory;

import java.util.HashMap;
import java.util.Map;

public class LabelList {
    //name to address
    private HashMap<String, Integer> ntoa;
    //address to size
    private HashMap<Integer, Integer> atos;

    private boolean[] reservedBytes;

    public LabelList(){
        ntoa = new HashMap<>();
        atos = new HashMap<>();
        reservedBytes = new boolean[Memory.ROM_SIZE];
    }

    public void put(String name, int address, int size){
        ntoa.put(name, address & 0xFFFF);
        atos.put(address & 0xFFFF, size);

        for(int i = 0 ; i < size; i++){
            reservedBytes[(address & 0xFFFF)+i] = true;
        }
    }
    public int geta(String name){
        return ntoa.get(name) & 0xFFFF;
    }
    public int gets(String name){
        int address = ntoa.get(name);
        return atos.get(address & 0xFFFF);
    }
    public int gets(int address){
        return atos.get(address);
    }
    public boolean contains(String name){
        return ntoa.containsKey(name);
    }
    public boolean contains(int address){
        return reservedBytes[address];
    }

    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder("[");
        for(Map.Entry<String, Integer> entry: ntoa.entrySet()){
            sb.append(entry.getKey()).append(":").append(entry.getValue()).append(", ");
        }
        sb.setLength(sb.length()-2);
        sb.append("]");
        return sb.toString();
    }
}
