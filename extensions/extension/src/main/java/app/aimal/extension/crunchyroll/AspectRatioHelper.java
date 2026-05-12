package app.aimal.extension.crunchyroll;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class AspectRatioHelper {

    private static final int BUTTON_ID = 0x7f0a9999;
    private static final int[] MODES = {0, 3, 4, 1};
    private static final String[] LABELS = {"Fit", "Fill", "Crop", "16:9"};
    private static int currentIndex = 0;

    public static void addAspectRatioButton(View playerView) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (!(playerView instanceof ViewGroup)) return;
                ViewGroup parent = (ViewGroup) playerView;
                if (parent.findViewById(BUTTON_ID) != null) return;

                Context ctx = playerView.getContext();
                TextView button = new TextView(ctx);
                button.setId(BUTTON_ID);
                button.setText(LABELS[currentIndex]);
                button.setTextColor(Color.WHITE);
                button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                button.setPadding(dp(ctx, 12), dp(ctx, 7), dp(ctx, 12), dp(ctx, 7));
                button.setGravity(Gravity.CENTER);

                GradientDrawable bg = new GradientDrawable();
                bg.setColor(0xAA000000);
                bg.setCornerRadius(dp(ctx, 20));
                button.setBackground(bg);

                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );
                params.gravity = Gravity.BOTTOM | Gravity.START;
                params.bottomMargin = dp(ctx, 80);
                params.leftMargin = dp(ctx, 16);

                // Start hidden
                button.setVisibility(View.GONE);

                button.setOnClickListener(v -> {
                    currentIndex = (currentIndex + 1) % MODES.length;
                    int mode = MODES[currentIndex];
                    String label = LABELS[currentIndex];
                    try {
                        Class<?> cls = playerView.getClass();
                        while (cls != null) {
                            try {
                                java.lang.reflect.Method m =
                                        cls.getDeclaredMethod("setResizeMode", int.class);
                                m.setAccessible(true);
                                m.invoke(playerView, mode);
                                break;
                            } catch (NoSuchMethodException ignored) {
                                cls = cls.getSuperclass();
                            }
                        }
                    } catch (Exception ignored) {}
                    button.setText(label);
                    Toast.makeText(v.getContext(),
                            "Aspect ratio: " + label, Toast.LENGTH_SHORT).show();
                });

                parent.addView(button, params);

                // Watch for any child visibility changes to detect controls show/hide
                parent.getViewTreeObserver().addOnGlobalLayoutListener(
                        new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        try {
                            boolean controlsVisible = areControlsVisible(parent, button);
                            button.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
                        } catch (Exception ignored) {}
                    }
                });

            } catch (Exception ignored) {}
        }, 500);
    }

    /**
     * Checks if player controls are currently visible by scanning
     * sibling views for any visible view that looks like a controls overlay.
     * Controls layout has alpha > 0 and is visible when showing.
     */
    private static boolean areControlsVisible(ViewGroup parent, View button) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child == button) continue;
            if (child.getVisibility() == View.VISIBLE && child.getAlpha() > 0.1f) {
                // Found a visible sibling — controls are showing
                return true;
            }
        }
        return false;
    }

    public static void setButtonVisible(boolean visible) {
        // Kept for compatibility, no-op now
    }

    private static int dp(Context ctx, int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                ctx.getResources().getDisplayMetrics()
        );
    }
}
