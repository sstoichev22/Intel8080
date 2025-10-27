package cpu;

public class Memory {
    private byte[] rom;
    private byte[] ram;
    private int sp;
    public Memory(int romSize, int ramSize){
        rom = new byte[romSize];
        ram = new byte[ramSize];
        sp = ramSize-1;
    }
    public void setRom(int address, byte val){
        rom[address] = val;
    }
    public byte getRom(int address){
        return rom[address];
    }
    public void setRam(int address, byte val){
        ram[address] = val;
    }
    public byte getRam(int address){
        return ram[address];
    }
    public void push(byte val){
        ram[--sp] = val;
    }
    public byte pop(){
        return ram[sp++];
    }
    public void incSp(int val){
        sp += val;
    }
    public void setSp(int address){
        sp = address;
    }
    public int getSp(){
        return sp;
    }
}
