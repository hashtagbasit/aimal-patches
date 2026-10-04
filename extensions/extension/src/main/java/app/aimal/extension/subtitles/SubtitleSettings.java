package app.aimal.extension.subtitles;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class SubtitleSettings {
    private static SubtitleStyle style;
    private static long revision;
    private static final List<WeakReference<View>> views = new ArrayList<>();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private SubtitleSettings() { }

    public static synchronized SubtitleStyle style(Context context) {
        if (style == null) style = SubtitleStyle.read(context.getApplicationContext());
        return style;
    }
    public static synchronized long revision() { return revision; }
    public static synchronized void watch(View view) {
        for (WeakReference<View> ref : views) if (ref.get() == view) return;
        views.add(new WeakReference<>(view));
    }
    public static void changed(Context context) {
        synchronized (SubtitleSettings.class) {
            style(context).save(context);
            revision++;
        }
        main.post(() -> {
            synchronized (SubtitleSettings.class) {
                Iterator<WeakReference<View>> it = views.iterator();
                while (it.hasNext()) {
                    View view = it.next().get();
                    if (view == null) it.remove(); else view.invalidate();
                }
            }
            Media3Captions.refresh();
        });
    }
    public static void reset(Context context) {
        synchronized (SubtitleSettings.class) { style = new SubtitleStyle(); }
        changed(context);
    }
}
