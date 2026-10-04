package app.aimal.verify;
import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import java.lang.reflect.*;
import java.util.Collections;

/** Uses the actual 3.74.0 TV SubtitleView and Cue classes, without a login. */
public final class CrunchyrollTvRunner extends Instrumentation {
    public static final class LegacyProvider {
        private final Object stream;
        public LegacyProvider(Object value) { stream=value; }
        public Object g() { return stream; }
    }
    private Object mediaItem, player;
    private View playbackCaptions;
    private Class<?> playerType;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        Throwable[] failure = new Throwable[1];
        runOnMainSync(() -> { try { verify(); } catch (Throwable error) { failure[0] = error; } });
        if (failure[0] == null) {
            try { verifyPlayback(); } catch (Throwable error) { failure[0] = error; }
        }
        runOnMainSync(() -> { try { if (player != null) playerType.getMethod("release").invoke(player); } catch (Exception ignored) { } });
        result.putString("stream", failure[0] == null
            ? "PASS: TV original stream models, clean video selection, separate ASS playback, paused hot reload, cue expiration, native fallback and injected remote hook\n"
            : "FAIL: " + android.util.Log.getStackTraceString(failure[0]));
        finish(failure[0] == null ? -1 : 1, result);
    }
    private void verify() throws Exception {
        Context context = getTargetContext(); ClassLoader loader = context.getClassLoader();
        verifyRouting(loader);
        Class<?> viewClass = loader.loadClass("androidx.media3.ui.SubtitleView");
        View view = (View)viewClass.getConstructor(Context.class, android.util.AttributeSet.class).newInstance(context, null);
        view.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 1920, 1080);
        Object builder = loader.loadClass("androidx.media3.common.text.Cue$Builder").getConstructor().newInstance();
        Object cue = null;
        for (Method method : builder.getClass().getMethods()) {
            if (java.util.Arrays.equals(method.getParameterTypes(), new Class<?>[]{CharSequence.class}))
                method.invoke(builder, "TV subtitle sample");
        }
        for (Method method : builder.getClass().getMethods()) {
            if (method.getParameterCount() == 0 && method.getReturnType().getName().equals("androidx.media3.common.text.Cue"))
                cue = method.invoke(builder);
        }
        check(cue != null, "Original Cue builder missing");
        Class<?> settings = loader.loadClass("app.aimal.extension.subtitles.SubtitleSettings");
        Object style = settings.getMethod("style", Context.class).invoke(null, context);
        Field enabled = style.getClass().getField("enabled"), color = style.getClass().getField("textColor");
        enabled.setBoolean(style, true); color.setInt(style, Color.GREEN);
        viewClass.getMethod("setCues", java.util.List.class).invoke(view, Collections.singletonList(cue));
        Bitmap first = draw(view); check(pixels(first) > 0, "Custom TV text missing");
        color.setInt(style, Color.RED); settings.getMethod("changed", Context.class).invoke(null, context);
        check(!first.sameAs(draw(view)), "Paused TV text did not hot reload");
        enabled.setBoolean(style, false); settings.getMethod("changed", Context.class).invoke(null, context);
        view.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 1920, 1080);
        check(pixels(draw(view)) > 0, "Original TV caption drawing missing");
        enabled.setBoolean(style, true);
        viewClass.getMethod("setCues", java.util.List.class).invoke(view, Collections.emptyList());
        check(pixels(draw(view)) == 0, "Expired TV caption remains");
        loader.loadClass("com.crunchyroll.crunchyroid.player.ui.PlayerActivity")
            .getDeclaredMethod("dispatchKeyEvent", android.view.KeyEvent.class);
    }
    private void verifyRouting(ClassLoader loader) throws Exception {
        java.io.File video = new java.io.File(getTargetContext().getFilesDir(), "aimal-test.mp4");
        try (java.io.InputStream input = getContext().getAssets().open("sample.mp4"); java.io.OutputStream output = new java.io.FileOutputStream(video)) {
            byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        }
        java.io.File ass = new java.io.File(getTargetContext().getFilesDir(), "aimal-test.ass");
        String script = "[Script Info]\nScriptType: v4.00+\nPlayResX: 1920\nPlayResY: 1080\n[V4+ Styles]\nFormat: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\nStyle: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,1,0,2,10,10,10,1\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\nDialogue: 0,0:00:00.10,0:00:02.80,Default,,0,0,0,,Actual TV separate ASS playback\n";
        try (java.io.OutputStream output = new java.io.FileOutputStream(ass)) { output.write(script.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        String clean = android.net.Uri.fromFile(video).toString(), caption = android.net.Uri.fromFile(ass).toString();
        Object cms = allocate(loader.loadClass("com.crunchyroll.cms.component.CMSComponent"));
        Object model = allocate(loader.loadClass("com.crunchyroll.katamari.core.model.VideoModel"));
        Object manifest = allocate(loader.loadClass("com.crunchyroll.katamari.core.model.Manifest"));
        Object source = Enum.valueOf((Class)loader.loadClass("com.crunchyroll.player.eventbus.model.SourceType"), "CURRENT");
        Constructor<?> hard = loader.loadClass("com.crunchyroll.katamari.core.model.HardSub").getConstructor(String.class,String.class,String.class);
        java.util.Map<String,Object> hardSubs = new java.util.HashMap<>();
        hardSubs.put("none", hard.newInstance("none",clean,"1080p"));
        hardSubs.put("es-ES", hard.newInstance("es-ES","https://example.test/burned.mpd","1080p"));
        Object entry = loader.loadClass("com.crunchyroll.katamari.core.model.SubtitleEntry").getConstructor(String.class,String.class,String.class).newInstance("es-ES",caption,"ass");
        set(manifest,"i",hardSubs);set(manifest,"a",clean);
        set(model,"c",manifest);set(model,"j",Collections.singletonMap("es-ES",entry));set(model,"k",Collections.emptyMap());
        set(cms,"s",Collections.singletonMap(source,model));set(cms,"n",Collections.emptyMap());set(cms,"o","es-ES");set(cms,"d",java.util.Locale.forLanguageTag("es-ES"));
        loader.loadClass("app.aimal.extension.crunchyroll.TvPlaybackSubtitles").getMethod("register",Object.class).invoke(null,cms);
        Class<?> eventClass = loader.loadClass("com.crunchyroll.player.eventbus.events.Topic$CMSEvent$VideoUrlReady");
        Constructor<?> constructor = null;
        for (Constructor<?> c : eventClass.getConstructors()) if (c.getParameterCount()==18) constructor=c;
        Object[] values = new Object[18]; Class<?>[] types = constructor.getParameterTypes();
        for(int i=0;i<18;i++) values[i]=types[i]==String.class?"":types[i]==boolean.class?false:types[i].isEnum()?types[i].getEnumConstants()[0]:java.util.List.class.isAssignableFrom(types[i])?Collections.emptyList():null;
        values[0]="https://example.test/burned.mpd";values[1]="";values[12]=source;
        Object event=constructor.newInstance(values);
        check(clean.equals(get(event,"a")) && caption.equals(get(event,"b")),"Actual TV event did not select clean video and external ASS");
        Class<?> builderClass=loader.loadClass("androidx.media3.common.MediaItem$Builder");
        Object builder=builderClass.getConstructor().newInstance();builderClass.getMethod("n",String.class).invoke(builder,clean);
        mediaItem=builderClass.getMethod("a").invoke(builder);
        Object local=get(mediaItem,"b");
        java.util.List<?> subtitles=(java.util.List<?>)get(local,"g");
        check(subtitles.size()==1 && "text/x-ssa".equals(get(subtitles.get(0),"b")),"Actual MediaItem omitted SSA track");
        // Explicit none clears the cached track for this URI.
        values[0]=clean;constructor.newInstance(values);
        Object off=builderClass.getConstructor().newInstance();builderClass.getMethod("n",String.class).invoke(off,clean);
        check(((java.util.List<?>)get(get(builderClass.getMethod("a").invoke(off),"b"),"g")).isEmpty(),"Actual subtitles-off retained track");
        // Re-select for the end-to-end playback test; already-built item remains immutable.
        values[0]="https://example.test/burned.mpd";constructor.newInstance(values);
        // The real Mi Box uses the legacy model: d() exposes captions, while f
        // contains ASS subtitles with no public getter in this optimized APK.
        Object legacy=allocate(loader.loadClass("com.crunchyroll.cms.models.SecureVideoStream"));
        Constructor<?> legacyHard=loader.loadClass("com.crunchyroll.api.models.secureplay.HardSubtitle").getConstructor(String.class,String.class,String.class);
        java.util.Map<String,Object> legacyHardSubs=new java.util.HashMap<>();
        legacyHardSubs.put("none",legacyHard.newInstance("none",clean,"1080p"));
        legacyHardSubs.put("es-ES",legacyHard.newInstance("es-ES","https://example.test/burned.mpd","1080p"));
        Object legacySubtitle=allocate(loader.loadClass("com.crunchyroll.api.models.video.Subtitle"));
        set(legacySubtitle,"url",caption);
        set(legacy,"b",clean);set(legacy,"c",legacyHardSubs);set(legacy,"f",Collections.singletonMap("es-ES",legacySubtitle));set(legacy,"h",Collections.emptyMap());
        set(cms,"s",Collections.emptyMap());set(cms,"n",Collections.singletonMap(source,new LegacyProvider(legacy)));
        event=constructor.newInstance(values);
        check(clean.equals(get(event,"a")) && caption.equals(get(event,"b")),"Legacy ASS map f was ignored in favor of empty closed captions d()");
    }
    private void verifyPlayback() throws Exception {
        Throwable[] error=new Throwable[1];
        runOnMainSync(() -> { try {
            Context context=getTargetContext();ClassLoader loader=context.getClassLoader();
            Class<?> builder=loader.loadClass("androidx.media3.exoplayer.ExoPlayer$Builder");
            player=builder.getMethod("i").invoke(builder.getConstructor(Context.class).newInstance(context));
            playerType=loader.loadClass("androidx.media3.common.Player");
            Class<?> viewType=loader.loadClass("androidx.media3.ui.PlayerView");
            View view=(View)viewType.getConstructor(Context.class,android.util.AttributeSet.class).newInstance(context,null);
            viewType.getMethod("setUseController",boolean.class).invoke(view,false);
            viewType.getMethod("setPlayer",playerType).invoke(view,player);
            playbackCaptions=(View)viewType.getMethod("getSubtitleView").invoke(view);
            playbackCaptions.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));playbackCaptions.layout(0,0,1920,1080);
            playerType.getMethod("K",loader.loadClass("androidx.media3.common.MediaItem")).invoke(player,mediaItem);
            playerType.getMethod("R").invoke(player);playerType.getMethod("I",boolean.class).invoke(player,true);
        } catch(Throwable e){error[0]=e;} });
        if(error[0]!=null)throw new RuntimeException(error[0]);
        boolean[] visible={false};
        for(int i=0;i<35 && !visible[0];i++) { Thread.sleep(150); runOnMainSync(() -> visible[0]=pixels(draw(playbackCaptions))>0); }
        check(visible[0],"Original ExoPlayer never decoded/drew the separate ASS track");
        runOnMainSync(() -> {try{
            playerType.getMethod("I",boolean.class).invoke(player,false);
            Context context=getTargetContext();Class<?> settings=context.getClassLoader().loadClass("app.aimal.extension.subtitles.SubtitleSettings");
            Object style=settings.getMethod("style",Context.class).invoke(null,context);
            Bitmap before=draw(playbackCaptions);style.getClass().getField("textColor").setInt(style,Color.MAGENTA);settings.getMethod("changed",Context.class).invoke(null,context);
            check(!before.sameAs(draw(playbackCaptions)),"Real paused playback caption did not hot reload");
            playerType.getMethod("I",boolean.class).invoke(player,true);
        }catch(Throwable e){error[0]=e;}});
        if(error[0]!=null)throw new RuntimeException(error[0]);
        Thread.sleep(4200);
        runOnMainSync(() -> visible[0]=pixels(draw(playbackCaptions))>0);
        check(!visible[0],"Real playback retained an expired ASS cue");
    }
    private static Object allocate(Class<?> type) throws Exception {
        Class<?> unsafe=Class.forName("sun.misc.Unsafe");Field f=unsafe.getDeclaredField("theUnsafe");f.setAccessible(true);
        return unsafe.getMethod("allocateInstance",Class.class).invoke(f.get(null),type);
    }
    private static void set(Object object,String name,Object value) throws Exception {Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    private static Object get(Object object,String name) throws Exception {Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static Bitmap draw(View view) { Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888); view.draw(new Canvas(b)); return b; }
    private static int pixels(Bitmap bitmap) { int[] values = new int[bitmap.getWidth()*bitmap.getHeight()]; bitmap.getPixels(values,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight()); int n=0; for(int value:values) if(Color.alpha(value)>0)n++; return n; }
    private static void check(boolean value, String message) { if(!value)throw new AssertionError(message); }
}
