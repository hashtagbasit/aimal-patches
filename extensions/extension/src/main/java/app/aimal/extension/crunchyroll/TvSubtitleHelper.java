package app.aimal.extension.crunchyroll;

import android.app.Activity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import java.util.WeakHashMap;
import app.aimal.extension.subtitles.SubtitlePanel;

/** TV entry point independent of the app's Compose controller visibility. */
public final class TvSubtitleHelper {
    private static final WeakHashMap<Activity, Press> presses = new WeakHashMap<>();
    private static final class Press {
        KeyEvent down; boolean replaying;
        java.lang.ref.WeakReference<android.app.Dialog> dialog = new java.lang.ref.WeakReference<>(null);
    }
    private TvSubtitleHelper() { }

    public static boolean onKey(Activity activity, KeyEvent event) {
        if (event == null) return false;
        Press press = presses.get(activity);
        if (press == null) { press = new Press(); presses.put(activity, press); }
        if (press.replaying) return false;
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) open(activity);
            return true;
        }
        if (key != KeyEvent.KEYCODE_DPAD_CENTER && key != KeyEvent.KEYCODE_ENTER) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) press.down = new KeyEvent(event);
            return true;
        }
        if (event.getAction() != KeyEvent.ACTION_UP || press.down == null) return false;
        KeyEvent down = press.down;
        press.down = null;
        if (event.isCanceled()) return true;
        if (event.getEventTime() - down.getDownTime() >= 700) {
            open(activity);
            return true;
        }
        // Deliver a normal click to the native Compose player, exactly once.
        press.replaying = true;
        try {
            activity.dispatchKeyEvent(down);
            activity.dispatchKeyEvent(event);
        } finally { press.replaying = false; }
        return true;
    }

    private static void open(Activity activity) {
        if (activity.isFinishing()) return;
        Press press = presses.get(activity);
        android.app.Dialog existing = press.dialog.get();
        if (existing != null && existing.isShowing()) return;
        press.dialog = new java.lang.ref.WeakReference<>(SubtitlePanel.showTv(activity,
                () -> toggleAspect(activity.getWindow().getDecorView())));
    }

    private static void toggleAspect(View view) {
        if (view.getClass().getName().equals("androidx.media3.ui.PlayerView")) {
            try {
                int current = (Integer) view.getClass().getMethod("getResizeMode").invoke(view);
                view.getClass().getMethod("setResizeMode", int.class).invoke(view, current == 3 ? 0 : 3);
            } catch (ReflectiveOperationException error) {
                android.util.Log.e("AimalSubtitles", "Cannot resize TV player", error);
            }
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) toggleAspect(group.getChildAt(i));
        }
    }
}
