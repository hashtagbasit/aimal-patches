package app.aimal.verify;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import java.lang.reflect.*;

/** Runs against the user's patched 3.117.0 APK, including its actual libass JNI. */
public final class CrunchyrollRunner extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            final Throwable[] failure = new Throwable[1];
            runOnMainSync(() -> { try { verify(); } catch (Throwable error) { failure[0] = error; } });
            if (failure[0] != null) throw new RuntimeException(failure[0]);
            verifyNativeControls();
            result.putString("stream", "PASS: vendor JNI, real controller clock, real caption View, paused hot reload, expiration, native fallback and proprietary playback controls\n");
            finish(-1, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
            finish(1, result);
        }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private void verifyNativeControls() throws Exception {
        android.content.Intent launch = getTargetContext().getPackageManager()
            .getLaunchIntentForPackage("com.crunchyroll.crunchyroid");
        // StartupActivity redirects and detaches its window while waiting for idle.
        // Settings is a stable FragmentActivity host and requires no watch session.
        launch.setClassName("com.crunchyroll.crunchyroid", "com.ellation.crunchyroll.presentation.main.settings.SettingsBottomBarActivity");
        android.app.Activity activity = startActivitySync(launch);
        final View[] player = new View[1];
        final View[] proprietaryControls = new View[1];
        final Throwable[] failure = new Throwable[1];
        runOnMainSync(() -> {
            try {
                ClassLoader loader = getTargetContext().getClassLoader();
                // The complete player requires an authenticated watch-screen host.
                // Mount its actual proprietary control component on media3 without login.
                player[0] = (View) loader.loadClass("androidx.media3.ui.PlayerView")
                    .getConstructor(Context.class, android.util.AttributeSet.class).newInstance(activity, null);
                proprietaryControls[0] = (View) loader.loadClass("com.crunchyroll.player.presentation.controls.PlayerControlsLayout")
                    .getConstructor(Context.class, android.util.AttributeSet.class).newInstance(activity, null);
                // Timeline presenters require a watch session. Keep the real toolbar
                // and its actual controls owner; exclude unrelated timeline widgets.
                View toolbar = (View) proprietaryControls[0].getClass().getMethod("getPlayerToolbar").invoke(proprietaryControls[0]);
                ((android.view.ViewGroup) toolbar.getParent()).removeView(toolbar);
                ((android.view.ViewGroup) proprietaryControls[0]).removeAllViews();
                ((android.view.ViewGroup) proprietaryControls[0]).addView(toolbar,
                    new android.view.ViewGroup.LayoutParams(-1, -2));
                ((android.view.ViewGroup) player[0]).addView(proprietaryControls[0],
                    new android.widget.FrameLayout.LayoutParams(-1, -1));
                ((android.view.ViewGroup) activity.getWindow().getDecorView()).addView(player[0],
                    new android.view.ViewGroup.LayoutParams(800, 400));
                loader.loadClass("app.aimal.extension.crunchyroll.AspectRatioHelper")
                    .getMethod("addAspectRatioButton", View.class).invoke(null, player[0]);
            } catch (Throwable error) { failure[0] = error; }
        });
        if (failure[0] != null) throw new RuntimeException(failure[0]);
        waitForIdleSync();
        runOnMainSync(() -> {
            try {
                View unused = player[0].findViewById(activity.getResources()
                    .getIdentifier("exo_controller", "id", activity.getPackageName()));
                check(unused != null, "media3 controller missing");
                unused.setVisibility(View.GONE);
                View controls = proprietaryControls[0];
                check(controls != null, "Proprietary controls missing");
                View controller = (View) controls.getClass().getMethod("getPlayerToolbar").invoke(controls);
                Class<?> helper = getTargetContext().getClassLoader().loadClass("app.aimal.extension.crunchyroll.AspectRatioHelper");
                Method find = helper.getDeclaredMethod("findController", android.view.ViewGroup.class);
                find.setAccessible(true);
                check(find.invoke(null, player[0]) == controller, "Helper selected media3 instead of proprietary toolbar");
                check(controller != null, "Actual controller ID missing");
                View row = player[0].findViewById(0x7f0a9990);
                check(row != null, "Actual player chips missing");
                check(row.isAttachedToWindow(), "Test host detached before control verification");
                android.graphics.Rect bounds = new android.graphics.Rect();
                check(row.getGlobalVisibleRect(bounds) && !bounds.isEmpty(), "Chip row is outside the visible player");
                check(row.getBottom() <= player[0].getHeight(), "Chip row falls below the video viewport");
                controller.setVisibility(View.VISIBLE);
                player[0].getViewTreeObserver().dispatchOnPreDraw();
                check(row.getVisibility() == View.VISIBLE, "Actual controller failed to reveal chips");
                controller.setAlpha(0f);
                player[0].getViewTreeObserver().dispatchOnPreDraw();
                check(row.getVisibility() == View.GONE, "Actual controller left ghost chips; toolbar alpha=" + controller.getAlpha() + ", attached=" + row.isAttachedToWindow());
                controller.setAlpha(1f);
                player[0].getViewTreeObserver().dispatchOnPreDraw();
                check(row.getVisibility() == View.VISIBLE, "Actual controller failed to restore chips");
                // End instrumentation with the component attached: the standalone
                // timeline has no authenticated watch-screen presenter to detach.
            } catch (Throwable error) { failure[0] = error; }
        });
        if (failure[0] != null) throw new RuntimeException(failure[0]);
    }
    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static Bitmap draw(View view) {
        Bitmap bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap)); return bitmap;
    }
    private static int pixels(Bitmap image) {
        int[] pixels = new int[image.getWidth() * image.getHeight()];
        image.getPixels(pixels, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
        int count = 0; for (int pixel : pixels) if (Color.alpha(pixel) != 0) count++; return count;
    }
    private void verify() throws Exception {
        Context context = getTargetContext(); ClassLoader loader = context.getClassLoader();
        Class<?> viewClass = loader.loadClass("com.crunchyroll.subtitles.SubtitlesView");
        View view = (View) viewClass.getConstructor(Context.class).newInstance(context);
        Object renderer = field(viewClass, "subtitlesRenderer").get(view);
        Class<?> rendererClass = renderer.getClass();
        for (Method method : rendererClass.getMethods()) if (method.getName().equals("initialize")) {
            check(Boolean.TRUE.equals(method.invoke(renderer, false, null)), "libass initialization failed");
        }
        rendererClass.getMethod("setFrameSize", int.class, int.class).invoke(renderer, 1920, 1080);
        String script = "[Script Info]\nScriptType: v4.00+\nPlayResX: 1920\nPlayResY: 1080\n"
            + "[V4+ Styles]\nFormat: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n"
            + "Style: Default,Arial,48,&H00FFFFFF,&H00FFFFFF,&H00000000,&H80000000,0,0,0,0,100,100,0,0,1,2,0,2,20,20,20,1\n"
            + "[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n"
            + "Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,Vendor JNI sample\n";
        long handle = (Long) rendererClass.getMethod("loadTrack", String.class).invoke(renderer, script);
        check(handle != 0, "Native track was not loaded");
        Class<?> controllerClass = loader.loadClass("com.crunchyroll.subtitles.presentation.SubtitlesControllerImpl");
        Object controller = null;
        for (Constructor<?> constructor : controllerClass.getConstructors()) if (constructor.getParameterCount() == 5) {
            controller = constructor.newInstance(view, renderer, null, 4, null);
        }
        check(controller != null, "Controller constructor missing");
        field(controllerClass, "trackPointer").setLong(controller, handle);
        Method update = controllerClass.getDeclaredMethod("updateSubtitlesPosition", long.class);
        update.setAccessible(true);
        view.layout(0, 0, 1920, 1080);
        Class<?> settings = loader.loadClass("app.aimal.extension.subtitles.SubtitleSettings");
        Object style = settings.getMethod("style", Context.class).invoke(null, context);
        Field enabled = style.getClass().getField("enabled");
        Field color = style.getClass().getField("textColor");
        enabled.setBoolean(style, true); color.setInt(style, Color.GREEN);
        settings.getMethod("changed", Context.class).invoke(null, context);
        update.invoke(controller, 1500L);
        Bitmap first = draw(view);
        check(pixels(first) > 0, "Real view has no custom captions");
        // Assert the call-site hook captured the correct native track and millisecond clock.
        Class<?> styler = loader.loadClass("app.aimal.extension.crunchyroll.SubtitleStyler");
        Object current = field(styler, "current").get(null);
        check(current != null && "Vendor JNI sample".equals(field(current.getClass(), "text").get(current)), "Real controller clock hook missed");
        color.setInt(style, Color.RED); settings.getMethod("changed", Context.class).invoke(null, context);
        check(!first.sameAs(draw(view)), "Paused caption did not hot reload");
        update.invoke(controller, 4500L);
        check(pixels(draw(view)) == 0, "Expired caption remains visible");
        update.invoke(controller, 1500L);
        Bitmap customized = draw(view);
        enabled.setBoolean(style, false); settings.getMethod("changed", Context.class).invoke(null, context);
        Bitmap original = draw(view);
        check(pixels(original) > 0 && !customized.sameAs(original), "Native libass fallback did not render");
        rendererClass.getMethod("destroy", long.class).invoke(renderer, handle);
        check(field(styler, "current").get(null) == null, "Native destroy did not clear captured track");
    }
}
