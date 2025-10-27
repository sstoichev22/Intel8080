package program;

import assembler.Assembler;
import assembler.FileReader;
import cpu.Intel8080;

import java.util.Arrays;
import java.util.List;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        List<String> lines = FileReader.ReadFile("C:\\Users\\stefa\\OneDrive\\Documents\\Intel8080\\src\\program\\program.asm");
        byte[] program = Assembler.assemble(lines);
        System.out.println(Arrays.toString(program));
        Intel8080 vm = new Intel8080();
        vm.loadProgram(program);
        Thread vmThread = new Thread(vm::run);
        vmThread.start();
        Thread.sleep(100);
        System.out.println(vm.getMemory().getRam(0x0C00));
    }
}
