package Intel8080.main;

import Intel8080.assembler.Assembler;
import Intel8080.assembler.FileReader;
import Intel8080.cpu.Intel8080;
import Intel8080.cpu.Memory;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;

public class Main {
    public static void main(String[] args){
        String file = FileReader.ReadFile("src\\Intel8080\\programs\\variableTest.asm");

        JFrame frame = new JFrame("Intel8080");
        frame.setLayout(new BorderLayout());


        byte[] program = Assembler.Assemble(file);
//        System.out.println(Arrays.toString(program));
//        console.println(2,Arrays.toString(program));

        //streams:
        // 0:opcodes
        // 1:displayInfo
        // 2:

        Memory memory = new Memory();
        Intel8080 vm = new Intel8080(memory);
        vm.loadProgram(program);

//        Input input = new Input(vm.getIOPorts());
//
//        Display display = new Display(vm.getMemory(), 16, 16, 50);
//        display.addKeyListener(input);
//        vm.setDisplay(display);
//
//
//
//        frame.add(display, BorderLayout.WEST);
//        frame.pack();
//
//        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
//        frame.setLocationRelativeTo(null);
//        frame.setResizable(false);
//        frame.setVisible(true);


        vm.run();



    }
}
