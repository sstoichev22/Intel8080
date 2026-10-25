package Intel8080.assembler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads UTF-8 source files for assembler entry points. */
public final class FileReader {
    private FileReader() {}

    /**
     * Reads all text from {@code path}, resolving it to a normalized absolute path first.
     * I/O failures are reported as unchecked exceptions with the resolved path and original
     * cause so callers can add context without losing the operating-system error.
     */
    public static String ReadFile(String path) {
        Path file = Path.of(path).toAbsolutePath().normalize();
        try {
            return Files.readString(file);
        } catch (IOException error) {
            throw new UncheckedIOException(
                    "Cannot read " + file + ". Check that the file exists and is readable.", error);
        }
    }
}
