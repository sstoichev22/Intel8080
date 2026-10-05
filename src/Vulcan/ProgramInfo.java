package Vulcan;

import java.util.Collections;
import java.util.Map;

/** Immutable program address ranges and source-line mappings used by the debugger. */
public record ProgramInfo(
        int start,
        int end,
        int codeStart,
        int codeEnd,
        int dataStart,
        int dataEnd,
        Map<Integer, Integer> addressToLine,
        Map<Integer, Integer> lineToAddress
) {
    /** Creates program metadata with code spanning the supplied start and end addresses. */
    public ProgramInfo(int start, int end,
                       Map<Integer, Integer> addressToLine,
                       Map<Integer, Integer> lineToAddress) {
        this(start, end, start, end, -1, -1, addressToLine, lineToAddress);
    }

    /** Creates an empty program description with no source mappings. */
    public static ProgramInfo empty() {
        return new ProgramInfo(
                -1, -1,
                -1, -1,
                -1, -1,
                Collections.emptyMap(),
                Collections.emptyMap()
        );
    }

    /** Reports whether the program has a valid loaded address range. */
    public boolean loaded() {
        return start >= 0 && end >= start;
    }

    /** Reports whether the program contains a valid code range. */
    public boolean codeLoaded() {
        return codeStart >= 0 && codeEnd >= codeStart;
    }

    /** Reports whether the program contains a valid data range. */
    public boolean dataLoaded() {
        return dataStart >= 0 && dataEnd >= dataStart;
    }
}
