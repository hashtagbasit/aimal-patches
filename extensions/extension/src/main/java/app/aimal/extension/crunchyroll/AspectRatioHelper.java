package app.aimal.extension.crunchyroll;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Method;

/**
 * A persistent aspect-ratio toggle for Crunchyroll's player.
 *
 * Crunchyroll's InternalPlayerViewLayout extends androidx.media3.ui.PlayerView,
 * so the picture is reshaped through media3's own {@code setResizeMode(int)} —
 * reached by reflection so nothing here has to link against media3-ui. That
 * keeps the app's own layout logic intact and works on DRM output.
 *
 * The chip follows the native controller visibility and disappears entirely
 * during playback. Native touch and D-pad handling reveal both sets of controls.
 */
public final class AspectRatioHelper {

    /** Unlikely to collide with the app's own view ids; used for idempotency. */
    private static final int BUTTON_ID = 0x7f0a9990;

    /** androidx.media3.ui.PlayerView / AspectRatioFrameLayout resize modes. */
    private static final int RESIZE_FIT = 0;
    private static final int RESIZE_FILL = 3;

    private static final int[] MODES = {RESIZE_FIT, RESIZE_FILL};
    private static final String[] LABELS = {"FIT", "STRETCH"};



    /** Remembered across player re-creations within the process. */
    private static int index = 0;

    private AspectRatioHelper() {
    }

    /**
     * Called from InternalPlayerViewLayout.onAttachedToWindow with the player
     * view as the argument. Must not throw — it runs on the view's attach path.
     */
    public static void addAspectRatioButton(final View playerView) {
        try {
            if (!(playerView instanceof ViewGroup)) return;
            final ViewGroup parent = (ViewGroup) playerView;

            // Defer to a laid-out state so the player's own overlays exist and
            // the view has a usable size.
            playerView.post(new Runnable() {
                @Override
                public void run() {
                    attach(playerView, parent);
                }
            });
        } catch (Throwable ignored) {
            // Never take the player down for a control chip.
        }
    }

    private static void attach(final View playerView, final ViewGroup parent) {
        try {
            if (parent.findViewById(BUTTON_ID) != null) return;

            final Context ctx = playerView.getContext();
            SubtitleStyler.init(ctx);

            final LinearLayout row = new LinearLayout(ctx);
            row.setId(BUTTON_ID);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            // Draw above the media3 controller, which the app adds in-tree.
            row.setElevation(dp(ctx, 10));

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            // Top-left: the top-right corner is where Crunchyroll puts cast,
            // settings and close, and the chips were landing on top of them.
            params.gravity = Gravity.TOP | Gravity.START;
            params.topMargin = dp(ctx, 64);
            params.leftMargin = dp(ctx, 16);
            row.setLayoutParams(params);

            // Observe the real controller's view, including its ancestor visibility.
            // No touch/key listeners are replaced, so the app keeps its native input handling.
            final View controller = findController(parent);
            row.setVisibility(View.GONE);
            final android.view.ViewTreeObserver.OnPreDrawListener visibility = () -> {
                boolean shown = visibleController(controller);
                if (shown && controller.getHeight() > 0) {
                    int[] anchor = new int[2], origin = new int[2];
                    controller.getLocationOnScreen(anchor);
                    playerView.getLocationOnScreen(origin);
                    int top = Math.max(dp(ctx, 24), anchor[1] - origin[1] + controller.getHeight() + dp(ctx, 4));
                    if (params.topMargin != top) {
                        params.topMargin = top;
                        row.setLayoutParams(params);
                    }
                }
                int next = shown ? View.VISIBLE : View.GONE;
                if (row.getVisibility() != next) row.setVisibility(next);
                return true;
            };
            row.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) {
                    playerView.getViewTreeObserver().addOnPreDrawListener(visibility);
                }
                @Override public void onViewDetachedFromWindow(View view) {
                    if (playerView.getViewTreeObserver().isAlive())
                        playerView.getViewTreeObserver().removeOnPreDrawListener(visibility);
                }
            });

            final TextView aspect = chip(ctx, LABELS[index]);
            aspect.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    index = (index + 1) % MODES.length;
                    aspect.setText(LABELS[index]);
                    applyResizeMode(playerView, MODES[index]);

                }
            });
            row.addView(aspect);

            final TextView cc = chip(ctx, "SUBTITLES");
            cc.setMinHeight(dp(ctx, 48));
            cc.setContentDescription("Open subtitle customization");
            cc.setOnClickListener(v -> {
                app.aimal.extension.subtitles.SubtitlePanel.show(ctx);

            });
            row.addView(cc);
            parent.addView(row);

            // Re-assert the current choice (a fresh player defaults to FIT).
            applyResizeMode(playerView, MODES[index]);

        } catch (Throwable ignored) {
        }
    }

    private static View findController(ViewGroup root) {
        // Crunchyroll disables exo_controller and fades its proprietary toolbar.
        View toolbar = findCrunchyrollToolbar(root);
        if (toolbar != null) return toolbar;
        int id = root.getResources().getIdentifier("exo_controller", "id", root.getContext().getPackageName());
        return id == 0 ? null : root.findViewById(id);
    }

    private static View findCrunchyrollToolbar(View view) {
        if (view.getClass().getName().equals("com.crunchyroll.player.presentation.controls.PlayerControlsLayout")) {
            try {
                return (View) view.getClass().getMethod("getPlayerToolbar").invoke(view);
            } catch (ReflectiveOperationException ignored) { }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findCrunchyrollToolbar(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean visibleController(View controller) {
        if (controller == null || !controller.isShown()) return false;
        for (View view = controller; view != null; ) {
            if (view.getAlpha() <= 0f) return false;
            android.view.ViewParent parent = view.getParent();
            view = parent instanceof View ? (View) parent : null;
        }
        return true;
    }

    private static TextView chip(Context ctx, String text) {
        TextView view = new TextView(ctx);
        view.setText(text);
        view.setFocusable(true);
        view.setMinHeight(dp(ctx, 48));
        view.setTextColor(Color.WHITE);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 6));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xB3000000);
        bg.setCornerRadius(dp(ctx, 18));
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(0xE6335555); focused.setCornerRadius(dp(ctx, 18));
        focused.setStroke(dp(ctx, 2), 0xFF5ED6D1);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, bg);
        view.setBackground(states);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(ctx, 3), 0, dp(ctx, 3), 0);
        view.setLayoutParams(params);
        return view;
    }

    private static void applyResizeMode(View playerView, int mode) {
        Method method = findSetResizeMode(playerView.getClass());
        if (method == null) return;
        try {
            method.setAccessible(true);
            method.invoke(playerView, mode);
        } catch (Throwable ignored) {
        }
    }

    /** setResizeMode(int) is declared on media3's PlayerView, a superclass. */
    private static Method findSetResizeMode(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod("setResizeMode", int.class);
            } catch (NoSuchMethodException ignored) {
                // Keep walking up.
            }
        }
        return null;
    }

    private static int dp(Context ctx, int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, ctx.getResources().getDisplayMetrics());
    }
}

