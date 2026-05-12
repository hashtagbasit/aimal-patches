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
    private static TextView cachedButton = null;

    public static void addAspectRatioButton(View playerView) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (!(playerView instanceof ViewGroup)) return;
                ViewGroup parent = (ViewGroup) playerView;

                // Remove old button if exists (in case S3 fires multiple times)
                View existing = parent.findViewById(BUTTON_ID);
                if (existing != null) {
                    // Just show/hide based on player state instead of re-adding
                    toggleButtonVisibility(existing, playerView);
                    return;
                }

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

                // Hide by default — only show when controls are visible
                button.setVisibility(View.GONE);
                cachedButton = button;

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

            } catch (Exception ignored) {}
        }, 500);
    }

    /**
     * Called from S3(boolean) hook — shows button when controls visible,
     * hides when controls hidden.
     * S3 is called with true when showing controls, false when hiding.
     */
    public static void setButtonVisible(boolean visible) {
        try {
            if (cachedButton == null) return;
            cachedButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        } catch (Exception ignored) {}
    }

    private static void toggleButtonVisibility(View button, View playerView) {
        // Fallback: just show it briefly
        button.setVisibility(View.VISIBLE);
    }

    private static int dp(Context ctx, int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                ctx.getResources().getDisplayMetrics()
        );
    }
}
