package app.aimal.extension.ary.downloads;

import android.content.Context;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * "Download all episodes" for a show.
 *
 * Reads the episode list straight off the {@code Models.Episode} instance that
 * AdapterYtProfile was constructed with, so it queues exactly the episodes the
 * user can see rather than re-fetching the series.
 */
public final class SeriesDownloads {

    private SeriesDownloads() {
    }

    /**
     * @param episodeModel a com.material.components.aryzap.Models.Episode
     * @param seriesTitle  show name, stored on each entry for the Downloads tab
     */
    public static void enqueueAll(Context context, Object episodeModel, String seriesTitle) {
        if (context == null || episodeModel == null) {
            return;
        }
        try {
            List<?> episodes = episodesOf(episodeModel);
            if (episodes == null || episodes.isEmpty()) {
                Toast.makeText(context, "No episodes to download", Toast.LENGTH_SHORT).show();
                return;
            }

            List<DownloadEntry> queue = new ArrayList<>();
            for (Object element : episodes) {
                DownloadEntry entry = EpisodeMeta.toEntry(element, seriesTitle);
                // Skips ad rows and embed-only episodes.
                if (entry != null) {
                    queue.add(entry);
                }
            }

            if (queue.isEmpty()) {
                Toast.makeText(context, "No downloadable episodes", Toast.LENGTH_SHORT).show();
                return;
            }

            AryDownloads.get(context).enqueueAll(queue);
            Toast.makeText(context,
                    "Downloading " + queue.size() + " episodes",
                    Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Logger.e("Could not queue series download", t);
        }
    }

    private static List<?> episodesOf(Object episodeModel) {
        try {
            Method getEpisodes = episodeModel.getClass().getMethod("getEpisodes");
            Object value = getEpisodes.invoke(episodeModel);
            return value instanceof List ? (List<?>) value : null;
        } catch (Throwable t) {
            Logger.e("Could not read episode list", t);
            return null;
        }
    }
}
