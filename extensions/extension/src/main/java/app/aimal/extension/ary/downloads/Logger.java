package app.aimal.extension.ary.downloads;

import android.util.Log;

/** Single log tag so patched-in behaviour is greppable in logcat. */
public final class Logger {
    private static final String TAG = "AryDownloads";

    private Logger() {
    }

    public static void d(String message) {
        Log.d(TAG, message);
    }

    public static void e(String message, Throwable t) {
        Log.e(TAG, message, t);
    }
}
