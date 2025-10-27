package program;

import assembler.Assembler;
import assembler.FileReader;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        List<String> lines = FileReader.ReadFile("C:\\Users\\stefa\\OneDrive\\Documents\\Intel8080\\src\\program\\program.asm");
        byte[] program = Assembler.Assemble(lines);
    }
}
