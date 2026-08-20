package app.aimal.extension.ary.downloads;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/**
 * Lets the app start with no network so downloads remain watchable.
 *
 * aryzap_splash gates startup on {@code NetworkUtil.isConnected(Context)} and
 * routes to the NoInternet screen when it returns false, which made downloaded
 * episodes unreachable offline - the exact situation downloads exist for.
 *
 * This replaces that check rather than forcing it permanently true: the real
 * connectivity result is returned whenever the device is online, and only when
 * genuinely offline AND at least one completed download exists does it report
 * connected. Normal no-network error handling is therefore untouched for users
 * with nothing downloaded.
 */
public final class OfflineMode {

    private OfflineMode() {
    }

    /** Drop-in replacement for the app's own connectivity check. */
    public static boolean isConnected(Context context) {
        if (reallyConnected(context)) {
            return true;
        }
        try {
            for (DownloadEntry entry : AryDownloads.get(context).store().all()) {
                if (entry.state == DownloadEntry.STATE_COMPLETED) {
                    Logger.d("Offline, but downloads exist - allowing startup");
                    return true;
                }
            }
        } catch (Throwable t) {
            Logger.e("Could not check downloads for offline startup", t);
        }
        return false;
    }

    /** The original implementation, preserved verbatim. */
    private static boolean reallyConnected(Context context) {
        try {
            ConnectivityManager manager = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) {
                return false;
            }
            NetworkInfo active = manager.getActiveNetworkInfo();
            return active != null && active.isConnectedOrConnecting();
        } catch (Throwable t) {
            return false;
        }
    }
}
