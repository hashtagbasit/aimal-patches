package app.aimal.extension.crunchyroll;

import android.net.Uri;
import android.util.Log;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;

/** 3.74.0 TV stream routing: clean video plus an independently decoded text track. */
public final class TvPlaybackSubtitles {
    private static volatile WeakReference<Object> cms = new WeakReference<>(null);
    private static volatile String routingStatus = "No video selection received yet";
    private static volatile int events, attached, unmatched, cueUpdates, textLength;
    private static volatile boolean drawing;
    private static final Map<String, Track> tracks = new LinkedHashMap<>();
    private static final class Track {
        final String url, language;
        Track(String url, String language) { this.url = url; this.language = language; }
    }
    private TvPlaybackSubtitles() { }
    public static void register(Object component) {
        cms = new WeakReference<>(component);
        Log.i("AimalSubtitles", "TV CMS registered");
    }
    private static void status(String value) {
        routingStatus = value;
        Log.i("AimalSubtitles", "TV route: " + value);
    }
    public static String diagnostics() {
        return "TV playback diagnostics\n" + routingStatus + "\nEvents: " + events +
            " · tracks attached: " + attached + " · unmatched URIs: " + unmatched +
            "\nCue updates: " + cueUpdates + " · characters: " + textLength +
            " · custom drawing: " + drawing;
    }
    public static void onCaption(String text) { cueUpdates++; textLength = text.length(); }
    public static void onDrawing(boolean custom) { drawing = custom; }

    public static void onVideo(Object event) {
        try {
            events++;
            Object component = cms.get();
            if (component == null) { status("CMS unavailable"); return; }
            Object source = field(event, "m");
            String video = string(field(event, "a"));
            String caption = string(field(event, "b"));
            Object model = map(field(component, "s")).get(source);
            String clean, language;
            if (model != null) {
                Object manifest = call(model, "g");
                Map<?, ?> hard = map(call(manifest, "d"));
                Map<?, ?> subtitles = new LinkedHashMap<>(map(call(model, "i")));
                // Closed captions take the same precedence as CMSComponent.c0.
                ((Map)subtitles).putAll(map(call(model, "e")));
                status("Modern model · source=" + source + " · videos=" + hard.size() + " · tracks=" + subtitles.size() + " · caption=" + !caption.isEmpty());
                language = language(component, hard, subtitles, video, caption, "a");
                Object plain = hard.get("none");
                clean = plain == null ? string(call(manifest, "h")) : string(call(plain, "a"));
                if (caption.isEmpty() && subtitles.containsKey(language)) caption = string(call(subtitles.get(language), "a"));
            } else {
                Object provider = map(field(component, "n")).get(source);
                if (provider == null) { status("No model for source=" + source + " · modern=" + map(field(component,"s")).size() + " · legacy=" + map(field(component,"n")).size()); return; }
                Object stream = call(provider, "g");
                if (stream == null) { status("Provider has no stream · source=" + source); return; }
                Map<?, ?> hard = map(call(stream, "e"));
                // In the TV build d() is CLOSED CAPTIONS, not ASS subtitles.
                // R8 removed the unused subtitles getter; its constructor still stores f.
                Map<Object,Object> subtitles = new LinkedHashMap<>();
                subtitles.putAll(map(field(stream, "f")));
                subtitles.putAll(map(call(stream, "d")));
                language = language(component, hard, subtitles, video, caption, "getUrl");
                status("Legacy model · source=" + source + " · videos=" + hard.size() + " · tracks=" + subtitles.size() + " · language=" + language);
                Object plain = hard.get("none");
                clean = plain == null ? string(call(stream, "j")) : string(call(plain, "getUrl"));
                if (caption.isEmpty() && subtitles.containsKey(language)) caption = string(call(subtitles.get(language), "getUrl"));
            }
            // Keep unavailable languages and genuinely burned-only content on the native path.
            if (language.equals("none")) {
                status("Subtitles disabled");
                synchronized (tracks) { tracks.remove(clean); }
                return;
            }
            if (clean.isEmpty() || caption.isEmpty()) { status(routingStatus + " · clean=" + !clean.isEmpty() + " · text=" + !caption.isEmpty() + " · language=" + language); return; }
            set(event, "a", clean);
            set(event, "b", caption);
            status("Clean video + external track selected · language=" + language);
            synchronized (tracks) {
                tracks.put(clean, new Track(caption, language));
                while (tracks.size() > 32) tracks.remove(tracks.keySet().iterator().next());
            }
        } catch (Throwable error) { status("Selection error: " + error.getClass().getSimpleName()); Log.e("AimalSubtitles", "Cannot route TV text track", error); }
    }

    private static String language(Object component, Map<?, ?> hard, Map<?, ?> subtitles,
            String video, String caption, String getter) throws Exception {
        for (Map.Entry<?, ?> entry : subtitles.entrySet()) {
            if (!caption.isEmpty() && caption.equals(string(call(entry.getValue(), getter)))) return string(entry.getKey());
        }
        // With no external URL, the selected burned URL identifies the requested language.
        for (Map.Entry<?, ?> entry : hard.entrySet()) {
            if (video.equals(string(call(entry.getValue(), getter)))) return string(entry.getKey());
        }
        String selected = string(field(component, "o"));
        if (selected.equals("none") || subtitles.containsKey(selected)) return selected;
        Object locale = field(component, "d");
        String preferred = locale instanceof Locale ? ((Locale)locale).toLanguageTag() : "";
        return subtitles.containsKey(preferred) ? preferred : "";
    }

    /** Called before MediaItem.Builder builds, preserving DRM, audio and all other fields. */
    public static void onBuild(Object builder) {
        try {
            Object uri = field(builder, "b");
            Track track;
            synchronized (tracks) { track = tracks.get(string(uri)); }
            if (track == null) { unmatched++; return; }
            ClassLoader loader = builder.getClass().getClassLoader();
            Class<?> type = loader.loadClass("androidx.media3.common.MediaItem$SubtitleConfiguration$Builder");
            Object subtitle = type.getConstructor(Uri.class).newInstance(Uri.parse(track.url));
            String path = Uri.parse(track.url).getPath();
            String mime = path != null && path.toLowerCase(Locale.ROOT).endsWith(".vtt") ? "text/vtt"
                : path != null && path.toLowerCase(Locale.ROOT).endsWith(".srt") ? "application/x-subrip" : "text/x-ssa";
            type.getMethod("n", String.class).invoke(subtitle, mime);
            type.getMethod("m", String.class).invoke(subtitle, track.language);
            type.getMethod("p", int.class).invoke(subtitle, 1); // DEFAULT selection
            Object configuration = type.getMethod("i").invoke(subtitle);
            builder.getClass().getMethod("k", List.class).invoke(builder, Collections.singletonList(configuration));
            attached++;
            Log.i("AimalSubtitles", "TV MediaItem attached text track: " + track.language + " / " + mime);
        } catch (Throwable error) { status("Track attachment error: " + error.getClass().getSimpleName()); Log.e("AimalSubtitles", "Cannot attach TV text track", error); }
    }
    private static Object call(Object object, String name) throws Exception {
        return object == null ? null : object.getClass().getMethod(name).invoke(object);
    }
    private static Map<?, ?> map(Object object) { return object instanceof Map ? (Map<?, ?>)object : Collections.emptyMap(); }
    private static String string(Object object) { return object == null ? "" : object.toString(); }
    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
}
