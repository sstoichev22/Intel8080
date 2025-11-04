package main;

import assembler.Assembler;
import assembler.FileReader;
import cpu.Intel8080;
import io.Display;
import io.Input;

public class Main {
    public static void main(String[] args){
        String[] lines = FileReader.ReadFile("C:\\Users\\stefa\\OneDrive\\Documents\\Intel8080\\src\\programs\\keyboardTest.asm");

        byte[] program = Assembler.assemble(lines);
//        System.out.println(Arrays.toString(program));

        Intel8080 vm = new Intel8080();
        vm.loadProgram(program);

        Input input = new Input(vm.getIOPorts());

        Display display = new Display(vm.getMemory(), 16, 16, 50);
        display.addKeyListener(input);
        vm.setDisplay(display);




        Thread vmThread = new Thread(vm::run);
        vmThread.start();

//        display.setPixel(0, 5, (byte) 0b10);
        //this literally is the greatest piece of java code to ever exist
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
                    for (int i = 0; i < vm.getIOPorts().length; i++) {
                        if(vm.getIOPorts()[i] != 0){
                            System.out.printf("OUT %d: %d\n", i & 0xFF, vm.getIOPorts()[i & 0xFF] & 0xFF);
                        }
                    }
                }));



    }
}
