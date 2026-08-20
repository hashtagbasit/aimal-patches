package app.aimal.extension.ary.downloads;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/**
 * Rows for the Downloads tab, laid out to echo the app's own episode lists:
 * 16:9 thumbnail on the left, title and show name stacked beside it.
 *
 * Built in code rather than from a layout resource so the patch stays
 * bytecode-only.
 */
public final class DownloadsAdapter extends RecyclerView.Adapter<DownloadsAdapter.Row> {

    /** Matches GlideHelper.SIZE_THUMBNAIL_* (160x90) from the host app. */
    private static final int THUMB_WIDTH_DP = 160;
    private static final int THUMB_HEIGHT_DP = 90;

    interface OnPlay {
        void play(DownloadEntry entry);
    }

    private final List<DownloadEntry> items = new ArrayList<>();
    private final OnPlay onPlay;

    DownloadsAdapter(OnPlay onPlay) {
        this.onPlay = onPlay;
    }

    void submit(List<DownloadEntry> entries) {
        items.clear();
        items.addAll(entries);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Row onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Row(parent.getContext());
    }

    @Override
    public void onBindViewHolder(@NonNull Row holder, int position) {
        holder.bind(items.get(position), onPlay);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Row extends RecyclerView.ViewHolder {

        private final ImageView thumbnail;
        private final TextView title;
        private final TextView subtitle;

        Row(Context context) {
            super(buildRoot(context));

            LinearLayout root = (LinearLayout) itemView;
            thumbnail = new ImageView(context);
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnail.setLayoutParams(new LinearLayout.LayoutParams(
                    dp(context, THUMB_WIDTH_DP), dp(context, THUMB_HEIGHT_DP)));
            root.addView(thumbnail);

            LinearLayout text = new LinearLayout(context);
            text.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            textParams.leftMargin = dp(context, 12);
            textParams.gravity = Gravity.CENTER_VERTICAL;
            text.setLayoutParams(textParams);

            title = new TextView(context);
            title.setTextColor(Color.WHITE);
            title.setTextSize(15f);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            text.addView(title);

            subtitle = new TextView(context);
            subtitle.setTextColor(Color.parseColor("#B3FFFFFF"));
            subtitle.setTextSize(12f);
            subtitle.setMaxLines(1);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            text.addView(subtitle);

            root.addView(text);
        }

        private static View buildRoot(Context context) {
            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setGravity(Gravity.CENTER_VERTICAL);
            int pad = dp(context, 8);
            root.setPadding(pad, pad, pad, pad);
            root.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return root;
        }

        void bind(final DownloadEntry entry, final OnPlay onPlay) {
            Context context = itemView.getContext();
            title.setText(entry.title);
            subtitle.setText(describe(entry));

            String url = AryConfig.imageUrl(context, entry.imagePath);
            if (url != null) {
                Glide.with(context).load(url).into(thumbnail);
            } else {
                thumbnail.setImageDrawable(null);
            }

            final boolean playable = entry.state == DownloadEntry.STATE_COMPLETED;
            itemView.setAlpha(playable ? 1f : 0.6f);
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (playable && onPlay != null) {
                        onPlay.play(entry);
                    }
                }
            });
        }

        /** Show name plus state, e.g. "Some Drama - 24 min" or "... - 42%". */
        private String describe(DownloadEntry entry) {
            StringBuilder builder = new StringBuilder();
            if (entry.seriesTitle != null && entry.seriesTitle.length() > 0) {
                builder.append(entry.seriesTitle).append(" - ");
            }
            switch (entry.state) {
                case DownloadEntry.STATE_COMPLETED:
                    builder.append(formatDuration(entry.videoLength));
                    break;
                case DownloadEntry.STATE_RUNNING:
                    builder.append(Math.max(0, Math.round(entry.progress))).append('%');
                    break;
                case DownloadEntry.STATE_FAILED:
                    builder.append("Failed");
                    break;
                case DownloadEntry.STATE_UNAVAILABLE_OFFLINE:
                    builder.append("Not available offline");
                    break;
                default:
                    builder.append("Queued");
                    break;
            }
            return builder.toString();
        }

        private String formatDuration(int seconds) {
            if (seconds <= 0) {
                return "Downloaded";
            }
            int minutes = seconds / 60;
            return minutes > 0 ? minutes + " min" : seconds + " sec";
        }

        private static int dp(Context context, int value) {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        }
    }
}
