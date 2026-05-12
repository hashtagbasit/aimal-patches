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

import java.lang.reflect.Field;

public final class AspectRatioHelper {

    private static final int BUTTON_ID = 0x7f0a9999;
    private static final int[] MODES = {0, 3, 4, 1};
    private static final String[] LABELS = {"Fit", "Fill", "Crop", "16:9"};
    private static int currentIndex = 0;
    private static final Handler handler = new Handler(Looper.getMainLooper());

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

                // Find PlayerControlsLayout via reflection on f40977I.f16300b
                // f40977I = LayoutInternalPlayerBinding field on InternalPlayerViewLayout
                // f16300b = PlayerControlsLayout field on LayoutInternalPlayerBinding
                View[] controlsRef = new View[1];
                try {
                    for (Field f : playerView.getClass().getDeclaredFields()) {
                        f.setAccessible(true);
                        Object binding = f.get(playerView);
                        if (binding == null) continue;
                        for (Field bf : binding.getClass().getDeclaredFields()) {
                            bf.setAccessible(true);
                            Object v = bf.get(binding);
                            if (v instanceof View) {
                                String name = v.getClass().getName();
                                if (name.contains("PlayerControlsLayout")) {
                                    controlsRef[0] = (View) v;
                                    break;
                                }
                            }
                        }
                        if (controlsRef[0] != null) break;
                    }
                } catch (Exception ignored) {}

                // Poll every 250ms watching the controls alpha
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (controlsRef[0] != null) {
                                float alpha = controlsRef[0].getAlpha();
                                button.setVisibility(alpha > 0.1f ? View.VISIBLE : View.GONE);
                            }
                        } catch (Exception ignored) {}
                        handler.postDelayed(this, 250);
                    }
                });

            } catch (Exception ignored) {}
        }, 500);
    }

    public static void setButtonVisible(boolean visible) {}

    private static int dp(Context ctx, int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                ctx.getResources().getDisplayMetrics()
        );
    }
}
