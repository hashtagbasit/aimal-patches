package app.aimal.extension.netflix;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class NetflixAspectRatioHelper {

    private static final int BUTTON_ID = 0x7f0a9998;
    private static final String[] LABELS = {"Fit", "Fill", "Zoom", "Stretch"};
    private static int currentIndex = 0;
    private static SurfaceView surfaceView;

    public static void addAspectRatioButton(View playerView) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (!(playerView instanceof ViewGroup)) return;
                ViewGroup parent = (ViewGroup) playerView;
                if (parent.findViewById(BUTTON_ID) != null) return;

                surfaceView = findSurfaceView(parent);

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

                button.setAlpha(0.7f);

                button.setOnClickListener(v -> {
                    currentIndex = (currentIndex + 1) % LABELS.length;
                    applyMode(parent);
                    button.setText(LABELS[currentIndex]);
                    Toast.makeText(v.getContext(),
                            "Aspect ratio: " + LABELS[currentIndex], Toast.LENGTH_SHORT).show();
                });

                parent.addView(button, params);
            } catch (Exception ignored) {}
        }, 800);
    }

    private static void applyMode(ViewGroup parent) {
        if (surfaceView == null) {
            surfaceView = findSurfaceView(parent);
        }
        if (surfaceView == null) return;

        switch (currentIndex) {
            case 0: // Fit - default Netflix behavior
                surfaceView.setScaleX(1.0f);
                surfaceView.setScaleY(1.0f);
                break;
            case 1: // Fill - scale uniformly to fill parent
                float[] scale = calculateFillScale(parent, surfaceView);
                surfaceView.setScaleX(scale[0]);
                surfaceView.setScaleY(scale[1]);
                break;
            case 2: // Zoom - 1.33x uniform scale
                surfaceView.setScaleX(1.33f);
                surfaceView.setScaleY(1.33f);
                break;
            case 3: // Stretch - non-uniform scale to fill parent
                float parentW = parent.getWidth();
                float parentH = parent.getHeight();
                float svW = surfaceView.getWidth();
                float svH = surfaceView.getHeight();
                if (svW > 0 && svH > 0) {
                    surfaceView.setScaleX(parentW / svW);
                    surfaceView.setScaleY(parentH / svH);
                }
                break;
        }
    }

    private static float[] calculateFillScale(ViewGroup parent, SurfaceView sv) {
        float parentW = parent.getWidth();
        float parentH = parent.getHeight();
        float svW = sv.getWidth();
        float svH = sv.getHeight();
        if (svW <= 0 || svH <= 0) return new float[]{1f, 1f};
        float scale = Math.max(parentW / svW, parentH / svH);
        return new float[]{scale, scale};
    }

    private static SurfaceView findSurfaceView(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof SurfaceView) {
                return (SurfaceView) child;
            }
            if (child instanceof ViewGroup) {
                SurfaceView found = findSurfaceView((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static int dp(Context ctx, int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                ctx.getResources().getDisplayMetrics()
        );
    }
}
