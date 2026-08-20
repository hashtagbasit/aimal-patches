package app.aimal.extension.ary.downloads;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flat JSON index of downloads, kept next to the media cache.
 *
 * Deliberately not Room/Realm: the extension is merged into an app that already
 * bundles Realm, and adding a second persistence engine (plus its annotation
 * processor) to a patch extension is a lot of risk for a list that is only ever
 * a few hundred rows.
 */
public final class DownloadStore {

    private static final String FILE_NAME = "ary_downloads.json";
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final File file;
    /** Insertion-ordered so the Downloads tab shows newest-last consistently. */
    private final Map<String, DownloadEntry> entries = new LinkedHashMap<>();

    DownloadStore(Context context) {
        this.file = new File(context.getFilesDir(), FILE_NAME);
        load();
    }

    private synchronized void load() {
        if (!file.exists()) return;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r");
            byte[] buffer = new byte[(int) raf.length()];
            raf.readFully(buffer);
            JSONArray array = new JSONArray(new String(buffer, UTF8));
            for (int i = 0; i < array.length(); i++) {
                DownloadEntry entry = DownloadEntry.fromJson(array.getJSONObject(i));
                entries.put(entry.id, entry);
            }
        } catch (Exception e) {
            Logger.e("Could not read download index", e);
        } finally {
            closeQuietly(raf);
        }
    }

    synchronized void persist() {
        FileOutputStream out = null;
        try {
            JSONArray array = new JSONArray();
            for (DownloadEntry entry : entries.values()) {
                array.put(entry.toJson());
            }
            out = new FileOutputStream(file);
            out.write(array.toString().getBytes(UTF8));
        } catch (Exception e) {
            Logger.e("Could not write download index", e);
        } finally {
            closeQuietly(out);
        }
    }

    public synchronized void put(DownloadEntry entry) {
        entries.put(entry.id, entry);
        persist();
    }

    public synchronized DownloadEntry get(String id) {
        return entries.get(id);
    }

    public synchronized boolean contains(String id) {
        return entries.containsKey(id);
    }

    public synchronized void remove(String id) {
        if (entries.remove(id) != null) {
            persist();
        }
    }

    /** Snapshot copy - callers iterate this on the UI thread. */
    public synchronized List<DownloadEntry> all() {
        return new ArrayList<>(entries.values());
    }

    /** Completed downloads for one series, for "already downloaded" checks. */
    public synchronized List<DownloadEntry> forSeries(String seriesId) {
        List<DownloadEntry> result = new ArrayList<>();
        if (seriesId == null) return result;
        for (DownloadEntry entry : entries.values()) {
            if (seriesId.equals(entry.seriesId)) result.add(entry);
        }
        return Collections.unmodifiableList(result);
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException ignored) {
        }
    }
}
