package app.aimal.extension.ary.downloads;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.lang.reflect.Field;

/**
 * Adds a download control to each episode row.
 *
 * Both EpisodesViewAll and VideoProfile render their episode lists through
 * AdapterYtProfile, so patching that one adapter covers both screens.
 *
 * The button is built in code and appended to the row's existing `lyt_parent`
 * container rather than added to aryzap_video_profile_item.xml, which keeps this
 * a bytecode-only change with no layout resource patching.
 */
public final class EpisodeRow {

    private static final String TAG = "ary_download_button";
    private static final String VIDEO_PROFILE =
            "com.material.components.aryzap.Activities.VideoProfile";

    /** Offered before each download; values are max rendition height. */
    private static final String[] QUALITY_LABELS =
            {"Low (360p)", "Medium (480p)", "High (720p)", "Full HD (1080p)"};
    private static final int[] QUALITY_HEIGHTS = {360, 480, 720, 1080};

    private EpisodeRow() {
    }

    public static void attach(View itemView, Object episodeElement, String seriesTitle) {
        if (itemView == null) {
            return;
        }
        try {
            String show = seriesTitle != null && seriesTitle.length() > 0
                    ? seriesTitle
                    : currentSeriesTitle(itemView.getContext());

            final DownloadEntry entry = EpisodeMeta.toEntry(episodeElement, show);
            if (entry == null) {
                removeExisting(itemView);
                return;
            }

            ViewGroup container = findContainer(itemView);
            if (container == null) {
                attachLongPressFallback(itemView, entry);
                return;
            }

            ImageView button = (ImageView) container.findViewWithTag(TAG);
            if (button == null) {
                button = createButton(container.getContext());
                container.addView(button);
            }
            bind(button, entry);
        } catch (Throwable t) {
            Logger.e("Could not attach download button", t);
        }
    }

    private static void bind(ImageView button, final DownloadEntry entry) {
        final Context context = button.getContext();
        final AryDownloads downloads = AryDownloads.get(context);

        DownloadEntry known = downloads.store().get(entry.id);
        button.setAlpha(known != null && known.state == DownloadEntry.STATE_COMPLETED ? 1f : 0.7f);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DownloadEntry existing = downloads.store().get(entry.id);
                if (existing != null && existing.state == DownloadEntry.STATE_COMPLETED) {
                    Toast.makeText(context, "Already downloaded", Toast.LENGTH_SHORT).show();
                    return;
                }
                askQualityThenDownload(context, downloads, entry, v);
            }
        });
    }

    /**
     * Quality is chosen per download and remembered as the default for the next
     * one. The list is fixed rather than read from the stream's own renditions:
     * resolving those needs a DownloadHelper prepare pass, which is async and
     * would leave the user waiting on a spinner before the dialog could appear.
     */
    private static void askQualityThenDownload(final Context context,
                                               final AryDownloads downloads,
                                               final DownloadEntry entry,
                                               final View button) {
        int current = AryConfig.downloadHeight(context);
        int checked = 2;
        for (int i = 0; i < QUALITY_HEIGHTS.length; i++) {
            if (QUALITY_HEIGHTS[i] == current) {
                checked = i;
                break;
            }
        }

        final int[] selection = {checked};
        new AlertDialog.Builder(context)
                .setTitle("Download quality")
                .setSingleChoiceItems(QUALITY_LABELS, checked,
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int which) {
                                selection[0] = which;
                            }
                        })
                .setPositiveButton("Download",
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int which) {
                                AryConfig.setDownloadHeight(
                                        context, QUALITY_HEIGHTS[selection[0]]);
                                downloads.enqueue(entry);
                                button.setAlpha(1f);
                                Toast.makeText(context,
                                        "Downloading: " + entry.title,
                                        Toast.LENGTH_SHORT).show();
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void attachLongPressFallback(View itemView, final DownloadEntry entry) {
        final Context context = itemView.getContext();
        itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                askQualityThenDownload(context, AryDownloads.get(context), entry, v);
                return true;
            }
        });
    }

    /**
     * VideoProfile keeps the show name in a private static field set from the
     * launching intent. AdapterYtProfile does not receive it, so it is read
     * reflectively - otherwise Downloads rows show only an episode title with no
     * indication of which show they belong to.
     */
    private static String currentSeriesTitle(Context context) {
        try {
            Class<?> videoProfile = Class.forName(
                    VIDEO_PROFILE, true, context.getClassLoader());
            Field field = videoProfile.getDeclaredField("seriesTitle");
            field.setAccessible(true);
            Object value = field.get(null);
            if (value != null && value.toString().length() > 0) {
                return value.toString();
            }
        } catch (Throwable ignored) {
            // Not launched from VideoProfile, or the field moved.
        }
        return "";
    }

    private static ImageView createButton(Context context) {
        ImageView button = new ImageView(context);
        button.setTag(TAG);
        button.setImageResource(android.R.drawable.stat_sys_download);
        button.setColorFilter(Color.WHITE);
        button.setContentDescription("Download episode");

        int size = dp(context, 28);
        int pad = dp(context, 4);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.gravity = Gravity.CENTER_VERTICAL;
        params.leftMargin = pad;
        button.setLayoutParams(params);
        button.setPadding(pad, pad, pad, pad);
        return button;
    }

    private static ViewGroup findContainer(View itemView) {
        Context context = itemView.getContext();
        Resources resources = context.getResources();
        int id = resources.getIdentifier("lyt_parent", "id", context.getPackageName());
        if (id != 0) {
            View found = itemView.findViewById(id);
            if (found instanceof LinearLayout) {
                return (ViewGroup) found;
            }
        }
        return itemView instanceof LinearLayout ? (ViewGroup) itemView : null;
    }

    private static void removeExisting(View itemView) {
        ViewGroup container = findContainer(itemView);
        if (container == null) return;
        View existing = container.findViewWithTag(TAG);
        if (existing != null) {
            container.removeView(existing);
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
