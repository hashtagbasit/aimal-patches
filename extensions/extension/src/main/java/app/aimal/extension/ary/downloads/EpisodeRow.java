package app.aimal.extension.ary.downloads;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

/**
 * Adds a download control to each episode row.
 *
 * Both EpisodesViewAll and VideoProfile render their episode lists through
 * AdapterYtProfile, so patching that one adapter covers both screens.
 *
 * The button is built in code and appended to the row's existing `lyt_parent`
 * container rather than added to aryzap_video_profile_item.xml, which keeps this
 * a bytecode-only change with no layout resource patching. If that container
 * cannot be found the row falls back to a long-press action so the feature is
 * still reachable.
 */
public final class EpisodeRow {

    /** Tag marking a button this class already attached, so rebinds do not stack. */
    private static final String TAG = "ary_download_button";

    private EpisodeRow() {
    }

    /**
     * @param itemView        the recycled row view
     * @param episodeElement  Models.Episode$EpisodeElement for this row
     * @param seriesTitle     show name, used for grouping in the Downloads tab
     */
    public static void attach(View itemView, Object episodeElement, String seriesTitle) {
        if (itemView == null) {
            return;
        }
        try {
            final DownloadEntry entry = EpisodeMeta.toEntry(episodeElement, seriesTitle);
            if (entry == null) {
                // Ad row, or an episode with no downloadable source.
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
                downloads.enqueue(entry);
                v.setAlpha(1f);
                Toast.makeText(context, "Downloading: " + entry.title, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static void attachLongPressFallback(View itemView, final DownloadEntry entry) {
        final Context context = itemView.getContext();
        itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                AryDownloads.get(context).enqueue(entry);
                Toast.makeText(context, "Downloading: " + entry.title, Toast.LENGTH_SHORT).show();
                return true;
            }
        });
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

    /** Prefers the row's `lyt_parent`, falling back to the row itself. */
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
