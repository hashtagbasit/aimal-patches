package app.aimal.extension.ary.downloads;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Reads configuration the host app already persists, so the patch does not have
 * to hardcode endpoints that ARY rotates server-side.
 *
 * aryzap_splash writes its whole remote config into SharedPreferences named
 * "MyAppPreferences" on launch, including "drmLicenseServer". PlayerActivity
 * then builds its Widevine licence URI as:
 *
 *     aryzap_splash.drmLicenseServer + "widevine"
 *
 * so the same concatenation is reproduced here.
 */
public final class AryConfig {

    private static final String PREFS = "MyAppPreferences";
    private static final String KEY_DRM_LICENSE_SERVER = "drmLicenseServer";
    private static final String KEY_IMAGE_PATH = "appImagePath";
    private static final String WIDEVINE_SUFFIX = "widevine";

    private AryConfig() {
    }

    /**
     * Builds a thumbnail URL the same way AdapterYtProfile does: absolute paths
     * are used as-is, relative ones are prefixed with the configured image host.
     */
    public static String imageUrl(Context context, String imagePath) {
        if (imagePath == null || imagePath.length() == 0) {
            return null;
        }
        if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
            return imagePath;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getString(KEY_IMAGE_PATH, "") + imagePath;
    }

    /** Full Widevine licence endpoint, or null when the app has not configured one. */
    public static String widevineLicenseUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String base = prefs.getString(KEY_DRM_LICENSE_SERVER, "");
        if (base == null || base.isEmpty()) {
            return null;
        }
        return base + WIDEVINE_SUFFIX;
    }
}
