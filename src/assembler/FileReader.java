package assembler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FileReader {
    public static String[] ReadFile(String path){
        try {
            return  Files.readAllLines(Path.of(path)).toArray(String[]::new);
        } catch (Exception e) {
            System.err.println("Cannot read file.");
            e.printStackTrace();
        }
        return new String[0];
    }
}
