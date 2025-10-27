package program;

import assembler.Assembler;
import assembler.FileReader;
import cpu.Intel8080;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        List<String> lines = FileReader.ReadFile("C:\\Users\\stefa\\OneDrive\\Documents\\Intel8080\\src\\program\\program.asm");
        byte[] program = Assembler.assemble(lines);
        Intel8080 vm = new Intel8080();
        vm.loadProgram(program);
        vm.run();
    }
}
