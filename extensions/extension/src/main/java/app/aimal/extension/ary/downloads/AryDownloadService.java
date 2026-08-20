package app.aimal.extension.ary.downloads;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DownloadService;
import androidx.media3.exoplayer.scheduler.Scheduler;

import java.util.List;

/**
 * Foreground service that keeps episode downloads running after the app is
 * backgrounded or swept from recents.
 *
 * Registered in AndroidManifest.xml by the manifest patch. Media3 requires the
 * service be declared with the RESTART action so downloads resume after the
 * process is killed.
 *
 * The notification is built by hand rather than through the
 * {@code channelNameResourceId} constructor, because an extension has no R class
 * of its own to reference string resources from.
 */
public final class AryDownloadService extends DownloadService {

    private static final int NOTIFICATION_ID = 0x41525944; // "ARYD"
    private static final String CHANNEL_ID = "ary_downloads";
    private static final String CHANNEL_NAME = "Episode downloads";

    public AryDownloadService() {
        super(NOTIFICATION_ID, DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL);
    }

    @Override
    protected DownloadManager getDownloadManager() {
        // Shares the single manager/cache pair the rest of the patch uses.
        return AryDownloads.get(this).downloadManager();
    }

    @Nullable
    @Override
    protected Scheduler getScheduler() {
        // No JobScheduler requirement: downloads resume when the app next runs.
        return null;
    }

    @Override
    protected Notification getForegroundNotification(List<Download> downloads, int notMetRequirements) {
        ensureChannel(this);

        int downloading = 0;
        float total = 0f;
        for (Download download : downloads) {
            if (download.state == Download.STATE_DOWNLOADING) {
                downloading++;
                float percent = download.getPercentDownloaded();
                if (percent > 0f) {
                    total += percent;
                }
            }
        }

        int progress = downloading > 0 ? Math.round(total / downloading) : 0;
        String text = downloading > 0
                ? downloading + (downloading == 1 ? " episode" : " episodes")
                : "Preparing";

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setContentTitle("Downloading")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setProgress(100, progress, downloading == 0)
                .build();
    }

    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }
}
