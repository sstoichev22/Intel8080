package cpu;

public class Memory {
    public static int ROM_SIZE = 0x1000;
    //ram and rom are together, rom starts at 0x00, ram starts at 0x10000
    private byte[] rom;
    private int sp;
    public Memory(){
        rom = new byte[ROM_SIZE];
        sp = ROM_SIZE-1;
    }
    public byte get(int address){
        return rom[address];
    }
    public void set(int address, byte val){
        rom[address] = val;
    }
    public void push(byte val){
        rom[ROM_SIZE - (--sp)] = val;
    }
    public byte pop(){
        return rom[ROM_SIZE - sp++];
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
    public void loadRom(byte[] program){
        System.arraycopy(program, 0, rom, 0, program.length);
    }
}
