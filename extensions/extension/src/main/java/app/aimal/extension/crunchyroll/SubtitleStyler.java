package app.aimal.extension.crunchyroll;
import android.content.Context;
import android.graphics.Canvas;
import android.util.Log;
import android.view.View;
import app.aimal.extension.subtitles.*;
import java.util.WeakHashMap;
import java.util.HashMap;
import java.util.Map;

/** Keeps native tracks intact; uses their exact render clock for the custom text. */
public final class SubtitleStyler {
    private static final WeakHashMap<Object, Map<Long, Track>> tracks = new WeakHashMap<>();
    private static final WeakHashMap<Object, Long> latest = new WeakHashMap<>();
    private static final WeakHashMap<View, CaptionPainter> painters = new WeakHashMap<>();
    private static volatile Track current;
    private static final class Track {
        final AssTimeline timeline; volatile String text = "";
        Track(String script) { timeline = new AssTimeline(script); }
    }
    private SubtitleStyler() { }
    static void init(Context context) { SubtitleFonts.init(context); SubtitleSettings.style(context); }
    public static void onLoaded(Object renderer, String script, long handle) {
        try {
            synchronized (tracks) {
                Track track = new Track(script == null ? "" : script);
                Map<Long, Track> rendererTracks = tracks.get(renderer);
                if (rendererTracks == null) { rendererTracks = new HashMap<>(); tracks.put(renderer, rendererTracks); }
                rendererTracks.put(handle, track); latest.put(renderer, handle);
            }
        } catch (Throwable error) { Log.e("AimalSubtitles", "Cannot parse ASS track", error); current = null; }
    }
    public static void onRender(Object renderer, long handle, long milliseconds) {
        synchronized (tracks) {
            Map<Long, Track> rendererTracks = tracks.get(renderer);
            Long selected = handle == Long.MIN_VALUE ? latest.get(renderer) : handle;
            Track track = rendererTracks == null ? null : rendererTracks.get(selected);
            if (track == null) { current = null; return; }
            track.text = track.timeline.at(milliseconds); current = track;
        }
    }
    public static void clear(Object renderer) {
        synchronized (tracks) {
            Map<Long, Track> old = tracks.remove(renderer); latest.remove(renderer);
            if (old != null && old.containsValue(current)) current = null;
        }
    }
    public static void clearTrack(Object renderer, long handle) {
        synchronized (tracks) {
            Map<Long, Track> rendererTracks = tracks.get(renderer);
            if (rendererTracks != null && rendererTracks.remove(handle) == current) current = null;
        }
    }
    public static boolean draw(View view, Canvas canvas) {
        try {
            init(view.getContext()); SubtitleSettings.watch(view);
            if (!SubtitleSettings.style(view.getContext()).enabled) return false;
            Track track = current;
            if (track == null || track.timeline.isEmpty()) return false;
            CaptionPainter painter = painters.get(view);
            if (painter == null) { painter = new CaptionPainter(); painters.put(view, painter); }
            painter.draw(canvas, view.getWidth(), view.getHeight(), track.text, SubtitleSettings.style(view.getContext()), SubtitleSettings.revision());
            return true;
        } catch (Throwable error) { Log.e("AimalSubtitles", "ASS caption draw failed", error); return false; }
    }
}
