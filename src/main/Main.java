package main;

import assembler.Assembler;
import assembler.FileReader;
import cpu.Intel8080;
import io.Display;

public class Main {
    public static void main(String[] args){
        String[] lines = FileReader.ReadFile("C:\\Users\\stefa\\OneDrive\\Documents\\Intel8080\\src\\programs\\displayTest.asm");

        byte[] program = Assembler.assemble(lines);
//        System.out.println(Arrays.toString(program));

        Intel8080 vm = new Intel8080();
        vm.loadProgram(program);

        Display display = new Display(vm.getMemory(), 16, 16, 50);
        vm.setDisplay(display);
        Thread vmThread = new Thread(vm::run);
        vmThread.start();


        //this literally is the greatest piece of java code to ever exist
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
                    for (int i = 0; i < vm.getOutputPorts().length; i++) {
                        if(vm.getOutputPorts()[i] != 0){
                            System.out.printf("OUT %d: %d\n", i & 0xFF, vm.getOutputPorts()[i] & 0xFF);
                        }
                    }
                }));



    }
}
