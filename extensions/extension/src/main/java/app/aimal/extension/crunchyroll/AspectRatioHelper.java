package app.aimal.extension.crunchyroll;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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

    public static void addAspectRatioButton(View playerView) {
        try {
            if (!(playerView instanceof ViewGroup)) return;

            ViewGroup parent = (ViewGroup) playerView;

            if (parent.findViewById(BUTTON_ID) != null) return;

            Context ctx = playerView.getContext();

            TextView button = new TextView(ctx);
            button.setId(BUTTON_ID);
            button.setText(LABELS[currentIndex]);
            button.setTextColor(Color.WHITE);
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            button.setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 6));
            button.setGravity(Gravity.CENTER);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0x88000000);
            bg.setCornerRadius(dp(ctx, 16));
            button.setBackground(bg);

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
               FrameLayout.LayoutParams.WRAP_CONTENT,
               FrameLayout.LayoutParams.WRAP_CONTENT
);
params.gravity = Gravity.BOTTOM | Gravity.END;
params.bottomMargin = dp(ctx, 72);
params.rightMargin = dp(ctx, 16);

            button.setOnClickListener(v -> {
                currentIndex = (currentIndex + 1) % MODES.length;
                int mode = MODES[currentIndex];
                String label = LABELS[currentIndex];

                try {
                    java.lang.reflect.Method setResize =
                            playerView.getClass().getMethod("setResizeMode", int.class);
                    setResize.invoke(playerView, mode);
                } catch (NoSuchMethodException e) {
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
                    } catch (Exception ex) {
                        // silent
                    }
                } catch (Exception e) {
                    // silent
                }

                button.setText(label);
                Toast.makeText(v.getContext(), "Aspect: " + label, Toast.LENGTH_SHORT).show();
            });

            parent.addView(button, params);

        } catch (Exception e) {
            // silent
        }
    }

    private static int dp(Context ctx, int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                ctx.getResources().getDisplayMetrics()
        );
    }
}
