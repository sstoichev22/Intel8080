package assembler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FileReader {
    public static List<String> ReadFile(String path){
        try {
            return  Files.readAllLines(Path.of(path));
        } catch (Exception e) {
            System.err.println("Cannot read file.");
            e.printStackTrace();
        }
        return new ArrayList<>();
    }
}
