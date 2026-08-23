package app.aimal.extension.streaming;

import android.util.Log;

/**
 * Thin logging wrapper. Kept separate so every class in this extension logs
 * under one tag, which makes `adb logcat -s StreamPlayback` enough to debug a
 * patched build on device.
 */
public final class Logger {
    public static final String TAG = "StreamPlayback";

    /** Flipped on by {@link Controls} when the app is debuggable. */
    static boolean verbose = false;

    private Logger() {
    }

    public static void d(String message) {
        if (verbose) Log.d(TAG, message);
    }

    public static void i(String message) {
        Log.i(TAG, message);
    }

    public static void e(String message, Throwable throwable) {
        Log.e(TAG, message, throwable);
    }
}
