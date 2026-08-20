package app.aimal.extension.ary.downloads;

import java.lang.reflect.Method;

/**
 * Reads {@code com.material.components.aryzap.Models.Episode$EpisodeElement}
 * reflectively.
 *
 * The extension is compiled independently of the target app, so that model type
 * is not on its compile classpath. The app is not obfuscated, so getter names
 * are stable and reflection is reliable here.
 */
public final class EpisodeMeta {

    private static final String ENCRYPTION_UTIL =
            "com.material.components.data.EncryptionUtil";

    private EpisodeMeta() {
    }

    /**
     * Converts one EpisodeElement into a {@link DownloadEntry}, resolving the
     * playback URL through the app's own decryptor.
     *
     * @param seriesTitle show name for the Downloads tab; the element itself
     *                    only carries a seriesId.
     * @return null when the episode is an ad row or carries no usable source.
     */
    public static DownloadEntry toEntry(Object episodeElement, String seriesTitle) {
        if (episodeElement == null) {
            return null;
        }
        try {
            if (asBoolean(episodeElement, "isAd")) {
                return null;
            }

            String source = asString(episodeElement, "getVideoSource");
            if (isEmpty(source)) {
                // Dailymotion/YouTube-hosted rows have no direct stream.
                return null;
            }

            String resolved = decrypt(episodeElement.getClass().getClassLoader(), source);
            if (isEmpty(resolved)) {
                return null;
            }

            String id = asString(episodeElement, "getId");
            if (isEmpty(id)) {
                id = resolved;
            }

            return new DownloadEntry(
                    id,
                    asString(episodeElement, "getSeriesId"),
                    seriesTitle,
                    asString(episodeElement, "getTitle"),
                    asString(episodeElement, "getDescription"),
                    asString(episodeElement, "getImagePath"),
                    asInt(episodeElement, "getVideoLength"),
                    resolved,
                    source,
                    asBoolean(episodeElement, "getDrmEnabled"));
        } catch (Throwable t) {
            Logger.e("Could not read episode metadata", t);
            return null;
        }
    }

    /**
     * Calls the app's {@code EncryptionUtil.decryptData}, matching what
     * CdnPlayer.decryptMediaUrl does before handing a URL to ExoPlayer.
     * Falls back to the raw value, exactly as the app does on failure.
     */
    private static String decrypt(ClassLoader loader, String value) {
        try {
            Class<?> util = Class.forName(ENCRYPTION_UTIL, true, loader);
            Method decryptData = util.getMethod("decryptData", String.class);
            Object result = decryptData.invoke(null, value);
            return result == null ? value : result.toString();
        } catch (Throwable t) {
            Logger.e("decryptData failed; using raw source", t);
            return value;
        }
    }

    private static String asString(Object target, String getter) {
        Object value = call(target, getter);
        return value == null ? "" : value.toString();
    }

    private static int asInt(Object target, String getter) {
        Object value = call(target, getter);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static boolean asBoolean(Object target, String getter) {
        Object value = call(target, getter);
        return value instanceof Boolean && (Boolean) value;
    }

    private static Object call(Object target, String getter) {
        try {
            Method method = target.getClass().getMethod(getter);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isEmpty(String value) {
        return value == null || value.length() == 0;
    }
}
