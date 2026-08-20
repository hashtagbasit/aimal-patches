package app.aimal.extension.ary.downloads;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.reflect.Field;

/**
 * "Download all episodes" control for a show.
 *
 * Anchored to the Activity's content root rather than to a view inside
 * VideoProfile's layout: that layout varies by content type (series, movie,
 * live) and hunting for a stable container in each variant would be far more
 * fragile than a floating control that always has somewhere to sit.
 */
public final class SeriesDownloadButton {

    private static final String TAG = "ary_download_all";
    private static final String VIDEO_PROFILE =
            "com.material.components.aryzap.Activities.VideoProfile";

    private SeriesDownloadButton() {
    }

    /**
     * Called from AdapterYtProfile's constructor, which receives both the
     * episode list and the hosting Context. That covers VideoProfile and
     * EpisodesViewAll - the two screens that list a show's episodes - from a
     * single injection point, instead of matching compiler-generated inner
     * classes like VideoProfile$9 whose names shift between builds.
     *
     * @param context      the hosting Activity
     * @param episodeModel Models.Episode, holding the full episode list
     */
    public static void attach(final Context context, final Object episodeModel) {
        final Activity activity = activityOf(context);
        if (activity == null || episodeModel == null) {
            return;
        }
        try {
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null || root.findViewWithTag(TAG) != null) {
                return;
            }

            final String seriesTitle = readSeriesTitle(activity);

            TextView button = new TextView(activity);
            button.setTag(TAG);
            button.setText("Download all");
            button.setTextColor(Color.WHITE);
            button.setTextSize(13f);
            button.setGravity(Gravity.CENTER);

            int padH = dp(activity, 16);
            int padV = dp(activity, 10);
            button.setPadding(padH, padV, padH, padV);
            button.setBackground(pill(activity));

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.BOTTOM | Gravity.END;
            params.rightMargin = dp(activity, 16);
            // Clear of the bottom navigation bar.
            params.bottomMargin = dp(activity, 88);
            button.setLayoutParams(params);

            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SeriesDownloads.enqueueAll(activity, episodeModel, seriesTitle);
                }
            });

            root.addView(button);
        } catch (Throwable t) {
            Logger.e("Could not attach download-all button", t);
        }
    }

    /**
     * VideoProfile keeps the show name in a private static field, set from the
     * launching intent. Read reflectively so the injected bytecode does not have
     * to touch a private member from an inner class.
     */
    private static String readSeriesTitle(Activity activity) {
        try {
            Class<?> videoProfile = Class.forName(
                    VIDEO_PROFILE, true, activity.getClassLoader());
            Field field = videoProfile.getDeclaredField("seriesTitle");
            field.setAccessible(true);
            Object value = field.get(null);
            if (value != null && value.toString().length() > 0) {
                return value.toString();
            }
        } catch (Throwable ignored) {
            // Fall through to the intent extra below.
        }
        String fromIntent = activity.getIntent() == null
                ? null
                : activity.getIntent().getStringExtra("title");
        return fromIntent == null ? "" : fromIntent;
    }

    /** Unwraps ContextWrapper chains to reach the hosting Activity. */
    private static Activity activityOf(Context context) {
        while (context instanceof android.content.ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((android.content.ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    private static GradientDrawable pill(Context context) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#D32027"));
        background.setCornerRadius(dp(context, 24));
        return background;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
