package main;

import assembler.Assembler;
import assembler.FileReader;
import cpu.Intel8080;
import io.Display;
import io.Input;
import util.Console;

import java.util.ArrayList;
import java.util.Arrays;

public class Main {
    public static void main(String[] args){
        String[] lines = FileReader.ReadFile("C:\\Users\\stefa\\OneDrive\\Documents\\Intel8080\\src\\programs\\snakeGame.asm");


        Console console = new Console(5, 1800, 500, "Instructions:", "Ticks per second:", "Memory and logs:", "Assembler debug:", "OUT");
        console.println(4, "Hello");
        Assembler.attachConsole(console);

        byte[] program = Assembler.assemble(lines);
//        console.println(2,Arrays.toString(program));

        //streams:
        // 0:opcodes
        // 1:displayInfo
        // 2:

        Intel8080 vm = new Intel8080(console);
        vm.loadProgram(program);

        Input input = new Input(vm.getIOPorts());

        Display display = new Display(vm.getMemory(), 16, 16, 50);
        display.addKeyListener(input);
        vm.setDisplay(display);

        Thread vmThread = new Thread(vm::run);
        vmThread.start();

//        display.setPixel(0, 5, (byte) 0b10);
        //this literally is the greatest piece of java code to ever exist
        if(display.isShowing()){
            int n = 0;
            while(n < program.length){
                if(program[n] == 0) {
                    n++;
                    continue;
                }
                console.print(2,"0x"+Integer.toHexString(n)+": ");
                ArrayList<Integer> arr = new ArrayList<>();
                while(program[n] != 0){
                    arr.add(program[n++] & 0xFF);
                }
                console.println(2,Arrays.toString(arr.toArray()));
            }
            ArrayList<Byte> mem = new ArrayList<>();
            int len = 5;
            for(int i = 0x7000; i < 0x7000+len; i++){
                mem.add(vm.getMemory().get(i & 0xFFFF));
            }
            console.println(2,"MEM@0x7000: " + Arrays.toString(mem.toArray()));


            for (int i = 0; i < vm.getIOPorts().length; i++) {
                if(vm.getIOPorts()[i] != 0){
                    console.printf(2,"OUT %d: %d\n", i & 0xFF, vm.getIOPorts()[i & 0xFF] & 0xFF);
                }
            }
        }



    }
}
