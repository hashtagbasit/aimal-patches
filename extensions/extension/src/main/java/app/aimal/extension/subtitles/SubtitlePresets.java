package app.aimal.extension.subtitles;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Detached snapshots: editing the current style never changes a saved entry. */
public final class SubtitlePresets {
    private static final String DEFAULT = "default", ANIME = "anime-classic";
    private static final String KEY = "entries";
    private SubtitlePresets() { }

    public static final class Entry {
        public final String id, name;
        public final SubtitleStyle style;
        public final boolean builtin;
        private Entry(String id, String name, SubtitleStyle style, boolean builtin) {
            this.id = id; this.name = name; this.style = style.copy(); this.builtin = builtin;
        }
    }

    private static SharedPreferences storage(Context context) {
        return context.getSharedPreferences("aimal_subtitle_presets_v1", 0);
    }

    public static List<Entry> all(Context context) {
        List<Entry> result = new ArrayList<>();
        result.add(new Entry(DEFAULT, "Default", new SubtitleStyle(), true));
        // Noir Player 29759cd, src/lib/subtitle-styles.ts. Arial/Helvetica
        // fall back to Android's sans-serif unless the user imports Arial.
        SubtitleStyle anime = new SubtitleStyle();
        anime.fontFamily = "Arial"; anime.fontCssUrl = "";
        anime.fontSize = 46; anime.fontWeight = 700; anime.backgroundOpacity = 0;
        anime.bottomOffset = 10; anime.paddingX = 0; anime.paddingY = 0;
        anime.borderRadius = 0; anime.lineHeight = 1.23f; anime.letterSpacing = 0;
        anime.textShadow = false; anime.outlineWidth = 2.4f;
        result.add(new Entry(ANIME, "Anime Classic", anime, true));
        Set<String> ids = new HashSet<>(); ids.add(DEFAULT); ids.add(ANIME);
        try {
            JSONArray entries = new JSONArray(storage(context).getString(KEY, "[]"));
            for (int i = 0; i < entries.length(); i++) {
                JSONObject item = entries.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("id", ""), name = item.optString("name", "").trim();
                JSONObject style = item.optJSONObject("style");
                if (id.isEmpty() || name.isEmpty() || style == null || !ids.add(id)) continue;
                result.add(new Entry(id, name.substring(0, Math.min(80, name.length())), SubtitleStyle.fromJson(style), false));
            }
        } catch (org.json.JSONException ignored) { /* Built-ins remain usable if storage is damaged. */ }
        return result;
    }

    public static boolean matches(SubtitleStyle left, SubtitleStyle right) {
        JSONObject a = left.toJson(), b = right.toJson();
        java.util.Iterator<String> keys = a.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object x = a.opt(key), y = b.opt(key);
            if (x instanceof Number && y instanceof Number) {
                if (Double.compare(((Number)x).doubleValue(), ((Number)y).doubleValue()) != 0) return false;
            } else if (!java.util.Objects.equals(x, y)) return false;
        }
        return true;
    }

    public static synchronized String saveNew(Context context, String name, SubtitleStyle style) {
        List<Entry> entries = all(context);
        String id = UUID.randomUUID().toString();
        entries.add(new Entry(id, validName(name), style, false));
        persist(context, entries);
        return id;
    }

    public static synchronized void rename(Context context, String id, String name) {
        modify(context, id, validName(name), null, false);
    }
    public static synchronized void update(Context context, String id, SubtitleStyle style) {
        modify(context, id, null, style, false);
    }
    public static synchronized void delete(Context context, String id) { modify(context, id, null, null, true); }

    private static void modify(Context context, String id, String name, SubtitleStyle style, boolean delete) {
        List<Entry> entries = all(context);
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (!entry.id.equals(id)) continue;
            if (entry.builtin) throw new IllegalArgumentException("Included styles cannot be changed");
            if (delete) entries.remove(i);
            else entries.set(i, new Entry(id, name == null ? entry.name : name, style == null ? entry.style : style, false));
            persist(context, entries);
            return;
        }
        throw new IllegalArgumentException("Style no longer exists");
    }

    private static String validName(String name) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Enter a style name");
        String value = name.trim();
        if (value.length() > 80) throw new IllegalArgumentException("Use at most 80 characters");
        return value;
    }

    private static void persist(Context context, List<Entry> entries) {
        JSONArray array = new JSONArray();
        try {
            for (Entry entry : entries) if (!entry.builtin) array.put(new JSONObject()
                    .put("id", entry.id).put("name", entry.name).put("style", entry.style.toJson()));
        } catch (org.json.JSONException invalid) { throw new IllegalArgumentException("Invalid subtitle style", invalid); }
        if (!storage(context).edit().putString(KEY, array.toString()).commit()) {
            throw new IllegalStateException("Could not save styles. Storage may be full.");
        }
    }
}
