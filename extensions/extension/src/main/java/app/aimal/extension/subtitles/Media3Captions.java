package app.aimal.extension.subtitles;

import android.graphics.Canvas;
import android.util.Log;
import android.view.View;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;

/** Hooks SubtitleView without linking to the host's media3 version. */
public final class Media3Captions {
    private static final WeakHashMap<View, State> states = new WeakHashMap<>();
    private static final class State {
        List<?> cues = Collections.emptyList();
        String text = "";
        final CaptionPainter painter = new CaptionPainter();
    }
    private Media3Captions() { }

    /** Keep the original cues in SubtitleView; only replace its text drawing. */
    public static void onCues(View view, List<?> cues) {
        try {
            SubtitleFonts.init(view.getContext());
            SubtitleSettings.watch(view);
            State state = states.get(view);
            if (state == null) { state = new State(); states.put(view, state); }
            state.cues = cues == null ? Collections.emptyList() : new ArrayList<>(cues);
            StringBuilder text = new StringBuilder();
            for (Object cue : state.cues) {
                CharSequence value = text(cue);
                if (value == null || value.length() == 0) continue;
                if (text.length() > 0) text.append('\n');
                text.append(value);
            }
            state.text = text.toString();
            app.aimal.extension.crunchyroll.TvPlaybackSubtitles.onCaption(state.text);
            view.setWillNotDraw(false);
            view.invalidate();
        } catch (Throwable error) { Log.e("AimalSubtitles", "Cannot read media3 cues", error); }
    }

    /** Called instead of dispatchDraw: handles text; falls back for bitmap captions. */
    public static boolean draw(View view, Canvas canvas) {
        try {
            State state = states.get(view);
            SubtitleStyle style = SubtitleSettings.style(view.getContext());
            if (state == null || !style.enabled) { app.aimal.extension.crunchyroll.TvPlaybackSubtitles.onDrawing(false); return false; }
            for (Object cue : state.cues) if (text(cue) == null) { app.aimal.extension.crunchyroll.TvPlaybackSubtitles.onDrawing(false); return false; }
            app.aimal.extension.crunchyroll.TvPlaybackSubtitles.onDrawing(true);
            state.painter.draw(canvas, view.getWidth(), view.getHeight(), state.text, style, SubtitleSettings.revision());
            return true;
        } catch (Throwable error) { Log.e("AimalSubtitles", "Caption draw failed", error); return false; }
    }

    private static CharSequence text(Object cue) throws Exception {
        if (cue == null) return null;
        // Cue.text may be renamed, but its sole CharSequence field has stable shape.
        for (Class<?> c = cue.getClass(); c != null; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers()) && CharSequence.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return (CharSequence) field.get(cue);
                }
            }
        }
        return null;
    }

    static void refresh() {
        for (View view : new ArrayList<>(states.keySet())) if (view != null) view.invalidate();
    }
}
