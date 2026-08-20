package app.aimal.extension.ary.downloads;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * The Downloads tab.
 *
 * Hosted in MainPage's existing R.id.fragment_container, the same container the
 * app's own Snips/Favorites/Live fragments use, so it participates in normal tab
 * switching without any Activity or manifest changes.
 */
public final class DownloadsFragment extends Fragment {

    public static final String TAG = "AryDownloadsFragment";

    private DownloadsAdapter adapter;
    private TextView emptyLabel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context context = inflater.getContext();

        FrameLayout root = new FrameLayout(context);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(Color.BLACK);

        RecyclerView list = new RecyclerView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setClipToPadding(false);
        // Keep the last row clear of the translucent bottom navigation bar.
        list.setPadding(0, dp(context, 8), 0, dp(context, 96));

        adapter = new DownloadsAdapter(new DownloadsAdapter.OnPlay() {
            @Override
            public void play(DownloadEntry entry) {
                startPlayback(entry);
            }
        });
        list.setAdapter(adapter);
        root.addView(list);

        emptyLabel = new TextView(context);
        emptyLabel.setText("No downloads yet");
        emptyLabel.setTextColor(Color.parseColor("#B3FFFFFF"));
        emptyLabel.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams emptyParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        emptyParams.gravity = Gravity.CENTER;
        emptyLabel.setLayoutParams(emptyParams);
        root.addView(emptyLabel);

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    /** Re-reads the index so progress made while the tab was hidden shows up. */
    private void refresh() {
        Context context = getContext();
        if (context == null || adapter == null) {
            return;
        }
        List<DownloadEntry> entries = AryDownloads.get(context).store().all();
        adapter.submit(entries);
        emptyLabel.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /**
     * Starts the app's own CdnPlayer with the same extras EpisodesViewAll uses.
     * The player decrypts `vid` itself and the patched data source factory serves
     * the media from the offline cache.
     */
    private void startPlayback(DownloadEntry entry) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        try {
            Class<?> player = Class.forName(
                    "com.material.components.aryzap.Activities.CdnPlayer",
                    true,
                    context.getClassLoader());

            Intent intent = new Intent(context, player);
            intent.putExtra("vid", entry.rawSource);
            intent.putExtra("title", entry.title);
            intent.putExtra("playlist", entry.seriesId);
            intent.putExtra("drmEnabled", entry.drmEnabled);
            intent.putExtra(AryDownloads.EXTRA_OFFLINE, true);
            startActivity(intent);
        } catch (Throwable t) {
            Logger.e("Could not start offline playback", t);
            Toast.makeText(context, "Could not play this download", Toast.LENGTH_SHORT).show();
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
