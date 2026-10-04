package app.aimal.extension.subtitles;
import java.util.*;
import java.util.regex.*;

/** Immutable, indexed ASS text timeline. Advanced ASS drawing is kept native. */
public final class AssTimeline {
    private static final class Cue {
        final long start, end; final String text;
        Cue(long start, long end, String text) { this.start = start; this.end = end; this.text = text; }
    }
    private final Cue[] cues;
    private final long[] maximumEnd;
    public AssTimeline(String script) {
        List<Cue> parsed = new ArrayList<>();
        boolean events = false;
        List<String> columns = Arrays.asList("layer", "start", "end", "style", "name", "marginl", "marginr", "marginv", "effect", "text");
        for (String line : script.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[")) { events = trimmed.equalsIgnoreCase("[Events]"); continue; }
            if (!events) continue;
            if (trimmed.regionMatches(true, 0, "Format:", 0, 7)) {
                columns = new ArrayList<>();
                for (String column : trimmed.substring(7).split(",")) columns.add(column.trim().toLowerCase(Locale.ROOT));
            } else if (trimmed.regionMatches(true, 0, "Dialogue:", 0, 9)) {
                try {
                    if (columns.indexOf("text") != columns.size() - 1) continue;
                    String[] fields = trimmed.substring(9).split(",", columns.size());
                    if (fields.length != columns.size()) continue;
                    long start = time(fields[columns.indexOf("start")]), end = time(fields[columns.indexOf("end")]);
                    String text = plain(fields[columns.indexOf("text")]);
                    if (start >= 0 && end > start && !text.isEmpty()) parsed.add(new Cue(start, end, text));
                } catch (RuntimeException ignored) { }
            }
        }
        parsed.sort(Comparator.comparingLong(cue -> cue.start));
        cues = parsed.toArray(new Cue[0]); maximumEnd = new long[cues.length];
        long maximum = 0;
        for (int i = 0; i < cues.length; i++) { maximum = Math.max(maximum, cues[i].end); maximumEnd[i] = maximum; }
    }
    public String at(long milliseconds) {
        int low = 0, high = cues.length;
        while (low < high) { int middle = (low + high) >>> 1; if (cues[middle].start <= milliseconds) low = middle + 1; else high = middle; }
        List<String> active = new ArrayList<>();
        for (int i = low - 1; i >= 0 && maximumEnd[i] > milliseconds; i--) if (cues[i].end > milliseconds) active.add(0, cues[i].text);
        return String.join("\n", active);
    }
    public boolean isEmpty() { return cues.length == 0; }
    private static long time(String value) {
        String[] parts = value.trim().split(":");
        if (parts.length != 3) throw new IllegalArgumentException("ASS time");
        return Long.parseLong(parts[0]) * 3600000 + Long.parseLong(parts[1]) * 60000 + Math.round(Double.parseDouble(parts[2]) * 1000);
    }
    static String plain(String value) {
        StringBuilder result = new StringBuilder(); boolean drawing = false;
        for (int i = 0; i < value.length();) {
            if (value.charAt(i) == '{') {
                int end = value.indexOf('}', i + 1);
                if (end < 0) { if (!drawing) result.append(value.substring(i)); break; }
                Matcher mode = Pattern.compile("\\\\p(\\d+)(?=\\\\|$)").matcher(value.substring(i + 1, end));
                while (mode.find()) drawing = !mode.group(1).equals("0");
                i = end + 1;
            } else if (value.charAt(i) == '\\' && i + 1 < value.length()) {
                char next = value.charAt(i + 1);
                if (!drawing) {
                    if (next == 'N' || next == 'n') result.append('\n');
                    else if (next == 'h') result.append('\u00A0');
                    else result.append('\\').append(next);
                }
                i += 2;
            } else { if (!drawing) result.append(value.charAt(i)); i++; }
        }
        return result.toString().trim();
    }
}
