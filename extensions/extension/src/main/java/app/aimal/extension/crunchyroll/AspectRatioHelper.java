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
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class AspectRatioHelper {

    private static final int BUTTON_ID = 0x7f0a9999;
    private static final int[] MODES = {0, 3, 4, 1};
    private static final String[] LABELS = {"Fit", "Fill", "Crop", "16:9"};
    private static int currentIndex = 0;

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static Runnable visibilityChecker = null;

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

                // Poll every 300ms: find PlayerControlsLayout by class name
                // and check its alpha (CR fades it out instead of hiding)
                visibilityChecker = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            float controlsAlpha = getControlsAlpha(parent, button);
                            // Show our button only when controls are visible (alpha > 0.5)
                            button.setVisibility(controlsAlpha > 0.5f ? View.VISIBLE : View.GONE);
                        } catch (Exception ignored) {}
                        handler.postDelayed(this, 300);
                    }
                };
                handler.post(visibilityChecker);

            } catch (Exception ignored) {}
        }, 500);
    }

    private static float getControlsAlpha(ViewGroup parent, View button) {
        // Walk the view hierarchy to find PlayerControlsLayout
        // It's a direct or near-direct child of the player FrameLayout
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child == button) continue;
            String className = child.getClass().getName();
            // PlayerControlsLayout is the controls overlay
            if (className.contains("PlayerControlsLayout") ||
                className.contains("PlayerControls")) {
                return child.getAlpha();
            }
            // Also check one level deeper
            if (child instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) child;
                for (int j = 0; j < vg.getChildCount(); j++) {
                    View grandChild = vg.getChildAt(j);
                    String gcName = grandChild.getClass().getName();
                    if (gcName.contains("PlayerControlsLayout") ||
                        gcName.contains("PlayerControls")) {
                        return grandChild.getAlpha();
                    }
                }
            }
        }
        // Fallback: check if any non-surface child has alpha > 0
        // Surface views (video) have no alpha concept
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child == button) continue;
            if (!child.getClass().getName().contains("Surface") &&
                child.getAlpha() > 0.5f &&
                child.getVisibility() == View.VISIBLE) {
                return child.getAlpha();
            }
        }
        return 0f;
    }

    public static void setButtonVisible(boolean visible) {}

    private static int dp(Context ctx, int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                ctx.getResources().getDisplayMetrics()
        );
    }
}
