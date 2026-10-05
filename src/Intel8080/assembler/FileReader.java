package Intel8080.assembler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FileReader {
    public static String ReadFile(String path){
        try {
            return  Files.readAllLines(Path.of(path)).stream().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            System.err.println("Cannot read file.");
            e.printStackTrace();
        }
        return "\0";
    }
}
