package util;

import cpu.Memory;

import java.util.HashMap;

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
        ntoa.put(name.toLowerCase(), address);
        atos.put(address, size);

        for(int i = 0 ; i < size; i++){
            reservedBytes[address+i] = true;
        }
    }
    public int geta(String name){
        return ntoa.get(name.toLowerCase());
    }
    public int gets(String name){
        int address = ntoa.get(name.toLowerCase());
        return atos.get(address);
    }
    public int gets(int address){
        return atos.get(address);
    }
    public boolean contains(String name){
        return ntoa.containsKey(name.toLowerCase());
    }
    public boolean contains(int address){
        return reservedBytes[address];
    }
}
