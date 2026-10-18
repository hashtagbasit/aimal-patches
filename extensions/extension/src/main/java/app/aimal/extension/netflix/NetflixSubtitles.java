package app.aimal.extension.netflix;

import android.app.Activity;
import android.app.Dialog;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import app.aimal.extension.subtitles.CaptionView;
import app.aimal.extension.subtitles.SubtitleFonts;
import app.aimal.extension.subtitles.SubtitlePanel;
import app.aimal.extension.subtitles.SubtitleSettings;

/** Integration for the inspected Netflix 8.3.11 ARM Gibbon renderer. */
public final class NetflixSubtitles {
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static WeakReference<Activity> activity = new WeakReference<>(null);
    private static CaptionView overlay;
    private static Dialog dialog;
    private static KeyEvent down;
    private static boolean replaying, listening, loaded;
    private static final LinkedHashMap<Long, String> cues = new LinkedHashMap<>();
    private static long received;
    private static native void setEnabled(boolean enabled);

    public static void nativeLoaded() {
        loaded = true;
        main.post(NetflixSubtitles::refresh);
    }

    public static void attach(Activity current) {
        if (activity.get() != current) {
            activity = new WeakReference<>(current);
            overlay = null;
            cues.clear();
        }
        current.getWindow().getDecorView().post(() -> {
            if (current.isFinishing()) return;
            if (overlay == null) {
                overlay = new CaptionView(current);
                overlay.setClickable(false);
                overlay.setFocusable(false);
                current.addContentView(overlay, new FrameLayout.LayoutParams(-1, -1));
                SubtitleFonts.init(current);
                SubtitleSettings.watch(overlay);
            }
            if (!listening) {
                listening = true;
                SubtitleSettings.onChanged(NetflixSubtitles::refresh);
            }
            refresh();
        });
    }

    public static void detach(Activity current) {
        if (activity.get() == current && overlay != null) {
            cues.clear();
            overlay.setText("");
        }
    }

    // Called only for native subtitle nodes that reached the renderer, never menu text.
    public static void onCue(long node, byte[] utf8) {
        String markup = new String(utf8, StandardCharsets.UTF_8);
        String text = Html.fromHtml(markup, Html.FROM_HTML_MODE_LEGACY).toString().trim();
        main.post(() -> {
            received++;
            if (text.isEmpty()) cues.remove(node); else cues.put(node, text);
            if (overlay != null) overlay.setText(String.join("\n", cues.values()));
        });
    }

    private static void refresh() {
        Activity current = activity.get();
        if (current == null) return;
        boolean enabled = SubtitleSettings.style(current).enabled;
        if (loaded) setEnabled(enabled);
        if (overlay != null) {
            overlay.setVisibility(enabled ? View.VISIBLE : View.INVISIBLE);
            overlay.invalidate();
        }
    }

    public static boolean onKey(Activity current, KeyEvent event) {
        if (replaying) return false;
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) open(current);
            return true;
        }
        if (key != KeyEvent.KEYCODE_DPAD_CENTER && key != KeyEvent.KEYCODE_ENTER) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) down = new KeyEvent(event);
            return true;
        }
        if (event.getAction() != KeyEvent.ACTION_UP || down == null) return false;
        KeyEvent initial = down;
        down = null;
        if (event.isCanceled()) return true;
        if (event.getEventTime() - initial.getDownTime() >= 700) { open(current); return true; }
        replaying = true;
        try { current.dispatchKeyEvent(initial); current.dispatchKeyEvent(event); }
        finally { replaying = false; }
        return true;
    }

    private static void open(Activity current) {
        if (dialog != null && dialog.isShowing()) return;
        dialog = SubtitlePanel.showTv(current, () -> {
            boolean found = aspect(current.getWindow().getDecorView());
            if (!found) Toast.makeText(current, "Video surface unavailable", Toast.LENGTH_SHORT).show();
        }, () -> "Netflix 8.3.11 ARM\nNative bridge: " + loaded + "\nSubtitle updates: " + received
                + "\nActive subtitle nodes: " + cues.size());
    }

    private static boolean aspect(View view) {
        if (view.getClass().getName().equals("com.netflix.mediaclient.android.widget.TappableSurfaceView")) {
            try {
                int mode = (Integer) view.getClass().getMethod("getMode").invoke(view);
                view.getClass().getMethod("setMode", int.class).invoke(view, mode == 2 ? 0 : 2);
                Toast.makeText(view.getContext(), mode == 2 ? "FIT" : "STRETCH", Toast.LENGTH_SHORT).show();
                return true;
            } catch (ReflectiveOperationException error) {
                android.util.Log.e("AimalNetflix", "Cannot resize video", error);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (aspect(group.getChildAt(i))) return true;
        }
        return false;
    }
}
