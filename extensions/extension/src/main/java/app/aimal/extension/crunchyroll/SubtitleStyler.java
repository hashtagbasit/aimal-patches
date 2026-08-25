package app.aimal.extension.crunchyroll;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Restyles Crunchyroll's subtitles by rewriting the ASS script before libass
 * ever parses it.
 *
 * Crunchyroll does not use media3's SubtitleView. It ships libass and renders
 * subtitles to bitmaps natively — by the time anything reaches the view layer it
 * is a Bitmap in an AssFrame, so there is no text left to restyle. What there
 * is, one step earlier, is the whole ASS document arriving as a String in
 * SubtitlesRendererImpl.loadTrack(String). ASS keeps its styling in plain text:
 *
 *   [V4+ Styles]
 *   Format: Name, Fontname, Fontsize, PrimaryColour, ..., BorderStyle, Outline, ...
 *   Style: Default,Trebuchet MS,32,&H00FFFFFF,...,1,2,...
 *
 * So rewriting those Style lines restyles every subtitle in the track, and
 * libass does the rest.
 *
 * Column positions are read from the Format line rather than assumed, because
 * ASS allows the columns in any order.
 *
 * Size, colour and the outline/box options are pure numbers and colours, so they
 * always work. Changing the font *name* is best-effort: libass is initialised
 * with the app's asset manager and cache dir, and if it cannot resolve the
 * family it silently falls back to its default.
 */
public final class SubtitleStyler {

    private static final String PREFS = "aimal_crunchyroll_subtitles";
    private static final String KEY_SIZE = "size";
    private static final String KEY_FONT = "font";
    private static final String KEY_BORDER = "border";

    /** Multipliers applied to the script's own Fontsize. */
    private static final float[] SIZES = {0.75f, 0.9f, 1.0f, 1.25f, 1.5f};
    private static final String[] SIZE_LABELS = {"0.75×", "0.9×", "1.0×", "1.25×", "1.5×"};

    /** "" keeps whatever font the script asked for. */
    private static final String[] FONTS = {"", "Roboto", "Noto Sans", "Droid Sans Mono", "serif"};
    private static final String[] FONT_LABELS = {"FONT: AS-IS", "ROBOTO", "NOTO", "MONO", "SERIF"};

    private static final String[] BORDER_LABELS = {"BORDER: AS-IS", "OUTLINE", "BOX"};

    private static SharedPreferences preferences;

    private SubtitleStyler() {
    }

    static void init(Context context) {
        if (preferences == null && context != null) {
            preferences = context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }
    }

    // Settings ----------------------------------------------------------------

    static int size() {
        return preferences == null ? 2 : preferences.getInt(KEY_SIZE, 2);
    }

    static int font() {
        return preferences == null ? 0 : preferences.getInt(KEY_FONT, 0);
    }

    static int border() {
        return preferences == null ? 0 : preferences.getInt(KEY_BORDER, 0);
    }

    static int cycleSize() {
        return put(KEY_SIZE, (size() + 1) % SIZES.length);
    }

    static int cycleFont() {
        return put(KEY_FONT, (font() + 1) % FONTS.length);
    }

    static int cycleBorder() {
        return put(KEY_BORDER, (border() + 1) % BORDER_LABELS.length);
    }

    private static int put(String key, int value) {
        if (preferences != null) preferences.edit().putInt(key, value).apply();
        return value;
    }

    static String sizeLabel() {
        return SIZE_LABELS[bound(size(), SIZE_LABELS.length)];
    }

    static String fontLabel() {
        return FONT_LABELS[bound(font(), FONT_LABELS.length)];
    }

    static String borderLabel() {
        return BORDER_LABELS[bound(border(), BORDER_LABELS.length)];
    }

    /** Nothing to do when every option is still on its default. */
    private static boolean isModified() {
        return size() != 2 || font() != 0 || border() != 0;
    }

    // Rewriting ---------------------------------------------------------------

    /**
     * Called from SubtitlesRendererImpl.loadTrack with the raw ASS script.
     * Returns the script to hand to libass. Any failure returns the input
     * untouched — a broken subtitle track is far worse than an unstyled one.
     */
    public static String restyle(String script) {
        try {
            if (script == null || script.length() == 0 || !isModified()) return script;

            String[] lines = script.split("\n", -1);
            List<String> columns = null;
            boolean inStyles = false;
            int rewritten = 0;

            for (int i = 0; i < lines.length; i++) {
                String trimmed = lines[i].trim();

                if (trimmed.startsWith("[")) {
                    // Section header: only the style sections are of interest.
                    inStyles = trimmed.equalsIgnoreCase("[V4+ Styles]")
                            || trimmed.equalsIgnoreCase("[V4 Styles]");
                    columns = null;
                } else if (inStyles && startsWith(trimmed, "Format:")) {
                    columns = split(trimmed.substring("Format:".length()), -1);
                } else if (inStyles && columns != null && startsWith(trimmed, "Style:")) {
                    String rewrittenLine = rewriteStyle(lines[i], columns);
                    if (rewrittenLine != null) {
                        lines[i] = rewrittenLine;
                        rewritten++;
                    }
                }
            }

            if (rewritten == 0) return script;

            StringBuilder out = new StringBuilder(script.length() + 128);
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) out.append('\n');
                out.append(lines[i]);
            }
            return out.toString();
        } catch (Throwable t) {
            return script;
        }
    }

    private static String rewriteStyle(String line, List<String> columns) {
        int colon = line.indexOf(':');
        if (colon < 0) return null;

        String prefix = line.substring(0, colon + 1);
        List<String> values = split(line.substring(colon + 1), columns.size());
        if (values.size() != columns.size()) return null;

        // Only the fields actually being changed are rewritten; everything else
        // keeps its original text, whitespace included, so the rewritten script
        // differs from the original in exactly the places it has to.
        for (int i = 0; i < columns.size(); i++) {
            String column = columns.get(i).trim();
            String value = values.get(i).trim();

            if (column.equalsIgnoreCase("Fontsize")) {
                String scaled = scaleSize(value);
                if (!scaled.equals(value)) values.set(i, scaled);
            } else if (column.equalsIgnoreCase("Fontname") && font() != 0) {
                values.set(i, FONTS[bound(font(), FONTS.length)]);
            } else if (column.equalsIgnoreCase("BorderStyle") && border() != 0) {
                // 1 = outline + drop shadow, 3 = opaque box behind the text.
                values.set(i, border() == 2 ? "3" : "1");
            } else if (column.equalsIgnoreCase("Outline") && border() == 1) {
                // Give the outline enough weight to actually read against video.
                values.set(i, "3");
            } else if (column.equalsIgnoreCase("BackColour") && border() == 2) {
                // Opaque black box. ASS colours are &HAABBGGRR, alpha 00 = solid.
                values.set(i, "&H00000000");
            }
        }

        StringBuilder out = new StringBuilder(line.length() + 32);
        out.append(prefix);
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(',');
            out.append(values.get(i));
        }
        return out.toString();
    }

    private static String scaleSize(String value) {
        float factor = SIZES[bound(size(), SIZES.length)];
        if (factor == 1.0f) return value;
        try {
            float parsed = Float.parseFloat(value);
            int scaled = Math.round(parsed * factor);
            if (scaled < 1) scaled = 1;
            return String.format(Locale.US, "%d", scaled);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    /** ASS fields are comma separated; limit mirrors String.split's semantics. */
    private static List<String> split(String input, int limit) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) != ',') continue;
            if (limit > 0 && parts.size() == limit - 1) break;
            parts.add(input.substring(start, i));
            start = i + 1;
        }
        parts.add(input.substring(start));
        return parts;
    }

    private static boolean startsWith(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static int bound(int index, int length) {
        return (index < 0 || index >= length) ? 0 : index;
    }
}
