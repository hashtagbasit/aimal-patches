package com.crunchyroll.crunchyroid.player.ui;
import android.os.Bundle;
import android.view.KeyEvent;
public final class PlayerActivity extends fixture.TestActivity {
    public static final class Entry { public String a() { return "https://example.test/es.ass"; } }
    public static final class Hard { private final String url; Hard(String value){url=value;} public String a(){return url;} }
    public static final class Manifest {
        public java.util.Map<String,Hard> d(){ java.util.Map<String,Hard> m=new java.util.HashMap<>(); m.put("none",new Hard("https://example.test/clean.mpd"));m.put("es-ES",new Hard("https://example.test/burned.mpd"));return m; }
        public String h(){return "https://example.test/clean.mpd";}
    }
    public static final class Model {
        public Manifest g(){return new Manifest();}
        public java.util.Map<String,Entry> i(){return java.util.Collections.singletonMap("es-ES",new Entry());}
        public java.util.Map<String,Entry> e(){return java.util.Collections.emptyMap();}
    }
    public static final class LegacyEntry { public String getUrl(){return "https://example.test/es.ass";} }
    public static final class LegacyHard { private final String url; LegacyHard(String value){url=value;} public String getUrl(){return url;} }
    public static final class LegacyStream {
        public java.util.Map<String,LegacyEntry> f=java.util.Collections.singletonMap("es-ES",new LegacyEntry());
        public java.util.Map<String,LegacyEntry> d(){return java.util.Collections.emptyMap();}
        public java.util.Map<String,LegacyHard> e(){java.util.Map<String,LegacyHard> m=new java.util.HashMap<>();m.put("none",new LegacyHard("https://example.test/clean.mpd"));m.put("es-ES",new LegacyHard("https://example.test/burned.mpd"));return m;}
        public String j(){return "https://example.test/clean.mpd";}
    }
    public static final class LegacyProvider {public LegacyStream g(){return new LegacyStream();}}
    private void verifyRouting() {
        com.crunchyroll.cms.component.CMSComponent cms=new com.crunchyroll.cms.component.CMSComponent();
        cms.s.put("CURRENT",new Model());
        com.crunchyroll.player.eventbus.events.Topic.CMSEvent.VideoUrlReady event=new com.crunchyroll.player.eventbus.events.Topic.CMSEvent.VideoUrlReady("https://example.test/burned.mpd","",null,null,null,null,null,null,null,null,null,null,"CURRENT",null,null,null,null,null);
        if (!event.a.equals("https://example.test/clean.mpd") || !event.b.endsWith("es.ass")) throw new AssertionError("Burned stream not replaced with selected soft track");
        androidx.media3.common.MediaItem.Builder builder=new androidx.media3.common.MediaItem.Builder();builder.b=android.net.Uri.parse(event.a);
        androidx.media3.common.MediaItem item=builder.a();
        if (item.subtitles.size()!=1 || !item.subtitles.get(0).mime.equals("text/x-ssa")) throw new AssertionError("Separate SSA track not attached");
        event=new com.crunchyroll.player.eventbus.events.Topic.CMSEvent.VideoUrlReady("https://example.test/clean.mpd","",null,null,null,null,null,null,null,null,null,null,"CURRENT",null,null,null,null,null);
        builder=new androidx.media3.common.MediaItem.Builder();builder.b=android.net.Uri.parse(event.a);
        if (!builder.a().subtitles.isEmpty()) throw new AssertionError("Subtitles-off retained previous track");
        cms.s.clear();cms.n.put("CURRENT",new LegacyProvider());
        event=new com.crunchyroll.player.eventbus.events.Topic.CMSEvent.VideoUrlReady("https://example.test/burned.mpd","",null,null,null,null,null,null,null,null,null,null,"CURRENT",null,null,null,null,null);
        if (!event.a.equals("https://example.test/clean.mpd") || !event.b.endsWith("es.ass")) throw new AssertionError("Legacy ASS with empty closed captions was ignored");
    }
    private int nativeKeys;
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER) nativeKeys++;
        return super.dispatchKeyEvent(event);
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().postDelayed(() -> {
            try {
                verifyRouting();
                long time = android.os.SystemClock.uptimeMillis();
                dispatchKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0));
                dispatchKeyEvent(new KeyEvent(time, time + 100, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0));
                if (nativeKeys != 2) throw new AssertionError("Short OK not replayed exactly once: " + nativeKeys);
                dispatchKeyEvent(new KeyEvent(time + 200, time + 200, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0));
                dispatchKeyEvent(new KeyEvent(time + 200, time + 1000, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0));
                if (nativeKeys != 2) throw new AssertionError("Long OK leaked to native player");
                getWindow().getDecorView().postDelayed(() -> {
                    String result;
                    try {
                        java.lang.reflect.Field states = app.aimal.extension.crunchyroll.TvSubtitleHelper.class.getDeclaredField("presses");
                        states.setAccessible(true);
                        Object press = ((java.util.Map<?, ?>)states.get(null)).get(this);
                        java.lang.reflect.Field dialogField = press.getClass().getDeclaredField("dialog");
                        dialogField.setAccessible(true);
                        android.app.Dialog dialog = (android.app.Dialog)((java.lang.ref.WeakReference<?>)dialogField.get(press)).get();
                        if (dialog == null || !dialog.isShowing()) throw new AssertionError("Long OK did not open editor");
                        if (dialog.getWindow().getDecorView().findFocus() == null) throw new AssertionError("TV editor has no initial focus");
                        android.view.View focus = dialog.getWindow().getDecorView().findFocus();
                        if (!(focus instanceof android.widget.Button) || !((android.widget.Button)focus).getText().toString().equals("Close")) throw new AssertionError("Initial focus did not reach Close");
                        dialog.dismiss();
                        dispatchKeyEvent(new KeyEvent(time + 2000, time + 2000, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU, 0));
                        dispatchKeyEvent(new KeyEvent(time + 2000, time + 2100, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU, 0));
                        dialog = (android.app.Dialog)((java.lang.ref.WeakReference<?>)dialogField.get(press)).get();
                        if (dialog == null || !dialog.isShowing()) throw new AssertionError("Menu did not reopen editor");
                        result = "PASS: TV clean video/SSA routing, subtitles-off, cue/draw hooks, short OK replay, long OK/Menu editor and initial D-pad focus";
                    } catch (Throwable error) { result = "FAIL: " + error; }
                    try (java.io.OutputStream out = openFileOutput("tv-result.txt", MODE_PRIVATE)) { out.write(result.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
                    catch (Exception error) { throw new RuntimeException(error); }
                }, 500);
            } catch (Throwable error) {
                try (java.io.OutputStream out = openFileOutput("tv-result.txt", MODE_PRIVATE)) { out.write(("FAIL: " + error).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
                catch (Exception ignored) { }
            }
        }, 500);
    }
}
