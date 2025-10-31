package cpu;

public class Memory {
    public static int ROM_SIZE = 0x10000;

    //ram and rom are together, rom starts at 0x00, ram starts at 0x10000
    private byte[] memory;
    private int sp;
    //0x0000 → 0x7FFF : ROM (code)
    //0x8000 → 0xBFFF : Video memory
    //0xC000 → 0xFFFF : RAM (data, stack, variables)
    public static int ROM_START = 0;
    public static int ROM_END = 0x7FFF;
    public static int VIDEO_MEMORY_START = 0x8000;
    public static int VIDEO_MEMORY_END = 0xBFFF;
    public static int RAM_START = 0xC000;
    public static int RAM_END = 0xFFFF;
    public Memory(){
        memory = new byte[ROM_SIZE];
        sp = ROM_SIZE-1;
    }
    public byte get(int address){
        return memory[address];
    }
    public void set(int address, byte val){
        memory[address & 0xFFFF] = val;
    }
    public void push(byte val){
        sp--;
        int address = sp & 0xFFFF;
        if(address >= RAM_START && address <= RAM_END)
            memory[address] = val;
        else throw new RuntimeException(String.format("Ram out of bounds: %d for range: [%d,%d]\n", address, RAM_START, RAM_END));
    }
    public byte pop(){
        int address = sp;
        sp++;
        if(address >= RAM_START && address <= RAM_END)
            return memory[address];
        else throw new RuntimeException(String.format("Ram out of bounds: %d for range: [%d,%d]\n", address, RAM_START, RAM_END));
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
        System.arraycopy(program, 0, memory, 0, program.length);
    }
}
