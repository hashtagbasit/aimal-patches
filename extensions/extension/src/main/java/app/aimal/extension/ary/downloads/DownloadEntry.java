package app.aimal.extension.ary.downloads;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * One downloaded (or downloading) episode.
 *
 * Field names mirror com.material.components.aryzap.Models.Episode$EpisodeElement
 * so the Downloads tab can render rows that look identical to the in-app lists.
 */
public final class DownloadEntry {

    public static final int STATE_QUEUED = 0;
    public static final int STATE_RUNNING = 1;
    public static final int STATE_COMPLETED = 2;
    public static final int STATE_FAILED = 3;
    /** DRM episode whose license server refused an offline (persistable) licence. */
    public static final int STATE_UNAVAILABLE_OFFLINE = 4;

    public final String id;
    public final String seriesId;
    public final String seriesTitle;
    public final String title;
    public final String description;
    public final String imagePath;
    public final int videoLength;
    /** Resolved playback URL, already run through the app's decryptMediaUrl(). */
    public final String sourceUrl;
    /**
     * The original, still-encrypted videoSource as it came from the API.
     *
     * Kept so the Downloads tab can start CdnPlayer through the app's normal
     * intent path (which decrypts it itself) instead of a separate playback
     * route - the cache then serves the media offline transparently.
     */
    public final String rawSource;
    public final boolean drmEnabled;

    public int state;
    public float progress;
    /** Base64 Widevine offline licence key-set id; null for clear content. */
    public String offlineLicenseKeySetId;

    public DownloadEntry(String id, String seriesId, String seriesTitle, String title,
                         String description, String imagePath, int videoLength,
                         String sourceUrl, String rawSource, boolean drmEnabled) {
        this.id = id;
        this.seriesId = seriesId;
        this.seriesTitle = seriesTitle;
        this.title = title;
        this.description = description;
        this.imagePath = imagePath;
        this.videoLength = videoLength;
        this.sourceUrl = sourceUrl;
        this.rawSource = rawSource;
        this.drmEnabled = drmEnabled;
        this.state = STATE_QUEUED;
        this.progress = 0f;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("seriesId", seriesId);
        o.put("seriesTitle", seriesTitle);
        o.put("title", title);
        o.put("description", description);
        o.put("imagePath", imagePath);
        o.put("videoLength", videoLength);
        o.put("sourceUrl", sourceUrl);
        o.put("rawSource", rawSource);
        o.put("drmEnabled", drmEnabled);
        o.put("state", state);
        o.put("progress", progress);
        o.put("keySetId", offlineLicenseKeySetId == null ? JSONObject.NULL : offlineLicenseKeySetId);
        return o;
    }

    public static DownloadEntry fromJson(JSONObject o) {
        DownloadEntry e = new DownloadEntry(
                o.optString("id"),
                o.optString("seriesId"),
                o.optString("seriesTitle"),
                o.optString("title"),
                o.optString("description"),
                o.optString("imagePath"),
                o.optInt("videoLength"),
                o.optString("sourceUrl"),
                o.optString("rawSource"),
                o.optBoolean("drmEnabled"));
        e.state = o.optInt("state", STATE_QUEUED);
        e.progress = (float) o.optDouble("progress", 0d);
        e.offlineLicenseKeySetId = o.isNull("keySetId") ? null : o.optString("keySetId");
        return e;
    }
}
