package app.aimal.extension.ary;

import java.util.Arrays;
import java.util.List;

/**
 * Replacement speed table for the ARY Plus player settings overlay.
 *
 * The stock app ships {.25, .50, 1.0 Normal, 1.5, 2.0, 4.0}. This inserts 1.25x
 * between "1.0 Normal" and "1.5".
 *
 * Index 2 is deliberately kept as 1.0x: both PlayerActivity and CdnPlayer
 * initialise `selectedSpeedIndex = 2` and never re-derive it, so preserving that
 * slot keeps the default playback speed correct.
 */
public final class PlayerSpeed {
    private static final String[] LABELS = {".25", ".50", "1.0 Normal", "1.25", "1.5", "2.0", "4.0"};
    private static final float[] VALUES = {0.25f, 0.5f, 1.0f, 1.25f, 1.5f, 2.0f, 4.0f};

    private PlayerSpeed() {
    }

    /** Replaces {@code Arrays.asList(this.speedOptions)} in the settings overlay. */
    public static List<String> labels() {
        return Arrays.asList(LABELS);
    }

    /**
     * Replaces {@code this.speedValues[index]} at the setPlaybackSpeed call site.
     * Falls back to 1.0x rather than throwing if the adapter ever reports an
     * index outside the table.
     */
    public static float value(int index) {
        if (index < 0 || index >= VALUES.length) {
            return 1.0f;
        }
        return VALUES[index];
    }
}
