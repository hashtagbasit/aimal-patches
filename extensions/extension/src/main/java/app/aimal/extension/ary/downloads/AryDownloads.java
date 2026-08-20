package app.aimal.extension.ary.downloads;

import android.content.Context;
import android.net.Uri;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.util.Util;
import androidx.media3.database.DatabaseProvider;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.CacheKeyFactory;
import androidx.media3.datasource.cache.NoOpCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadHelper;
import androidx.media3.exoplayer.offline.DefaultDownloadIndex;
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DownloadRequest;
import androidx.media3.exoplayer.offline.DownloadService;
import androidx.media3.exoplayer.scheduler.Requirements;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Owns the offline media cache and download queue for the patched app.
 *
 * Holds the single {@link DownloadManager}/{@link SimpleCache} pair shared by
 * {@link AryDownloadService} (which runs transfers in the foreground so they
 * survive backgrounding) and by the player, which reads from the same cache.
 */
public final class AryDownloads {

    /**
     * HLS downloads fetch segments through this pool. Media3 parallelises chunk
     * fetches across it, so 3 threads shared by 2 downloads was the reason
     * throughput crawled regardless of link speed.
     */
    private static final int PARALLEL_CHUNK_THREADS = 12;

    /** Cap downloaded renditions so an episode does not pull a full 1080p ladder. */
    private static final int MAX_DOWNLOAD_HEIGHT = 720;

    /**
     * Marks a player launch as coming from the Downloads tab. The player patch
     * uses it to skip ad loading for content the user already has offline.
     */
    public static final String EXTRA_OFFLINE = "ary_offline_playback";

    private static AryDownloads instance;

    private final Context context;
    private final SimpleCache cache;
    private final DownloadManager downloadManager;
    private final HttpDataSource.Factory httpDataSourceFactory;
    private final DownloadStore store;

    private AryDownloads(Context context) {
        this.context = context.getApplicationContext();
        this.store = new DownloadStore(this.context);
        this.httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true);

        DatabaseProvider databaseProvider = new StandaloneDatabaseProvider(this.context);
        File downloadDirectory = new File(this.context.getFilesDir(), "ary_offline");

        this.cache = new SimpleCache(downloadDirectory, new NoOpCacheEvictor(), databaseProvider);

        // The downloader factory is built by hand rather than using the
        // DownloadManager convenience constructor, because that one creates its
        // own CacheDataSource.Factory internally and gives no way to install
        // STABLE_KEY_FACTORY. Download and playback must agree on cache keys or
        // every lookup misses.
        CacheDataSource.Factory downloadCacheFactory = new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(httpDataSourceFactory)
                .setCacheKeyFactory(STABLE_KEY_FACTORY);

        this.downloadManager = new DownloadManager(
                this.context,
                new DefaultDownloadIndex(databaseProvider),
                new DefaultDownloaderFactory(
                        downloadCacheFactory,
                        Executors.newFixedThreadPool(PARALLEL_CHUNK_THREADS)));
        this.downloadManager.setMaxParallelDownloads(3);
        this.downloadManager.addListener(new ProgressListener());
    }

    public static synchronized AryDownloads get(Context context) {
        if (instance == null) {
            instance = new AryDownloads(context);
        }
        return instance;
    }

    public DownloadStore store() {
        return store;
    }

    /** Exposed for {@link AryDownloadService}, which must return this instance. */
    DownloadManager downloadManager() {
        return downloadManager;
    }

    /**
     * Data source factory that reads from the offline cache first and falls back
     * to the network. The player patch swaps the app's factory for this one so a
     * downloaded episode plays back through the app's own PlayerView.
     */
    public DataSource.Factory offlineFirstDataSourceFactory() {
        return new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(httpDataSourceFactory)
                // Read-only: streaming a non-downloaded episode must not silently
                // fill the offline cache.
                .setCacheWriteDataSinkFactory(null)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }

    /**
     * Wraps the player's own DataSource.Factory with the offline cache.
     *
     * Injected where CdnPlayer/PlayerActivity build their
     * {@code DefaultDataSource.Factory}, so a downloaded episode is served from
     * disk while anything else falls through to the original upstream factory
     * untouched. Returns the upstream unchanged if the cache cannot be opened -
     * playback must never break because downloads are unavailable.
     */
    public static DataSource.Factory wrap(DataSource.Factory upstream, Context context) {
        try {
            // Logged unconditionally: silence here is the signal that the patch
            // hooked a DataSource.Factory the player does not actually use.
            Logger.d("wrap() called - attaching offline cache to " + upstream);
            return new CacheDataSource.Factory()
                    .setCache(get(context).cache)
                    .setUpstreamDataSourceFactory(upstream)
                    // Must match the factory the downloader used, or playback
                    // computes different keys and silently streams instead.
                    .setCacheKeyFactory(STABLE_KEY_FACTORY)
                    .setCacheWriteDataSinkFactory(null)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
        } catch (Throwable t) {
            Logger.e("Could not attach offline cache to player", t);
            return upstream;
        }
    }

    /**
     * Cache key that ignores the query string.
     *
     * ARY serves media from a CDN that signs URLs with expiring tokens, so the
     * same segment has a different query every time it is requested. Keying on
     * the full URI meant the key written at download time never matched the key
     * looked up at playback time, and the player fell through to the network -
     * which is why a "downloaded" episode still streamed and could switch to
     * 1080p instantly.
     */
    static final CacheKeyFactory STABLE_KEY_FACTORY = dataSpec -> {
        if (dataSpec.key != null) {
            return dataSpec.key;
        }
        Uri uri = dataSpec.uri;
        String path = uri.getScheme() + "://" + uri.getAuthority() + uri.getPath();
        return path;
    };

    /** True once the episode's media is fully present in the cache. */
    public boolean isDownloaded(String id) {
        DownloadEntry entry = store.get(id);
        return entry != null && entry.state == DownloadEntry.STATE_COMPLETED;
    }

    /** Queue one episode. Safe to call repeatedly - known ids are ignored. */
    public void enqueue(DownloadEntry entry) {
        if (store.contains(entry.id)) {
            Logger.d("Already queued or downloaded: " + entry.title);
            return;
        }
        store.put(entry);
        prepare(entry);
    }

    /** Queue every episode of a show; backs the "Download all episodes" action. */
    public void enqueueAll(List<DownloadEntry> episodes) {
        for (DownloadEntry entry : episodes) {
            enqueue(entry);
        }
    }

    /**
     * Resolves renditions with {@link DownloadHelper}, then hands a
     * {@link DownloadRequest} to the manager. DRM-protected items are skipped.
     */
    private void prepare(final DownloadEntry entry) {
        MediaItem mediaItem = new MediaItem.Builder()
                .setUri(Uri.parse(entry.sourceUrl))
                .setMimeType(inferMimeType(entry.sourceUrl))
                .build();

        TrackSelectionParameters parameters = new TrackSelectionParameters.Builder(context)
                .setMaxVideoSize(Integer.MAX_VALUE, AryConfig.downloadHeight(context))
                .build();

        // The (Context, MediaItem, ...) overload takes a boolean, not track
        // selection parameters. To pass parameters the MediaItem-first overload
        // is required; the null DrmSessionManager is cast to disambiguate it
        // from the sibling overload whose last argument is a boolean.
        DownloadHelper helper = DownloadHelper.forMediaItem(
                mediaItem,
                parameters,
                /* renderersFactory= */ null,
                httpDataSourceFactory,
                /* drmSessionManager= */ (DrmSessionManager) null);

        helper.prepare(new DownloadHelper.Callback() {

            /**
             * ARY Plus bundles an older media3 for its offline classes than the
             * 1.8.0 this extension compiles against, and the runtime dispatches
             * the single-argument callback:
             *
             *   AbstractMethodError: abstract method
             *     "void DownloadHelper$Callback.onPrepared(DownloadHelper)"
             *
             * Deliberately no @Override - this signature does not exist in
             * 1.8.0, so annotating it would fail the build. Both arities are
             * implemented so the class satisfies either runtime.
             */
            public void onPrepared(DownloadHelper downloadHelper) {
                handlePrepared(downloadHelper, false);
            }

            @Override
            public void onPrepared(DownloadHelper downloadHelper, boolean isDrmProtected) {
                handlePrepared(downloadHelper, isDrmProtected);
            }

            private void handlePrepared(DownloadHelper downloadHelper, boolean isDrmProtected) {
                try {
                    // DRM-protected episodes are not downloaded. The sanctioned
                    // offline path needs OfflineLicenseHelper, which R8 stripped
                    // from ARY Plus because the stock app has no offline feature -
                    // referencing it here made this whole class fail to load with
                    // NoClassDefFoundError. ARY's catalogue is DRM-free in
                    // practice, so such episodes are simply marked unavailable.
                    if (entry.drmEnabled || isDrmProtected) {
                        markUnavailableOffline(entry);
                        return;
                    }

                    DownloadRequest request = downloadHelper.getDownloadRequest(
                            entry.id, Util.getUtf8Bytes(entry.title));

                    entry.state = DownloadEntry.STATE_RUNNING;
                    store.put(entry);
                    // Hand off to the foreground service so the transfer keeps
                    // running once the app is backgrounded.
                    DownloadService.sendAddDownload(
                            context, AryDownloadService.class, request, /* foreground= */ true);
                } catch (Exception e) {
                    Logger.e("Could not start download for " + entry.title, e);
                    entry.state = DownloadEntry.STATE_FAILED;
                    store.put(entry);
                } finally {
                    downloadHelper.release();
                }
            }

            @Override
            public void onPrepareError(DownloadHelper downloadHelper, IOException e) {
                Logger.e("Could not resolve renditions for " + entry.title, e);
                entry.state = DownloadEntry.STATE_FAILED;
                store.put(entry);
                downloadHelper.release();
            }
        });
    }

    private void markUnavailableOffline(DownloadEntry entry) {
        entry.state = DownloadEntry.STATE_UNAVAILABLE_OFFLINE;
        store.put(entry);
        Logger.d("Not available offline (DRM licence refused): " + entry.title);
    }

    private static String inferMimeType(String url) {
        switch (Util.inferContentType(Uri.parse(url))) {
            case C.CONTENT_TYPE_HLS:
                return MimeTypes.APPLICATION_M3U8;
            case C.CONTENT_TYPE_DASH:
                return MimeTypes.APPLICATION_MPD;
            default:
                return MimeTypes.VIDEO_MP4;
        }
    }

    /**
     * Mirrors DownloadManager state back into the JSON index for the tab.
     *
     * EVERY method of DownloadManager.Listener is overridden deliberately, even
     * the ones with empty bodies. The interface declares Java 8 default methods,
     * and any method left un-overridden dispatches through the desugared
     * companion class DownloadManager$Listener$-CC. That class is not bundled
     * here (media3 is compileOnly) and R8 dropped it from the host app, which
     * previously crashed with:
     *
     *   NoClassDefFoundError: Landroidx/media3/exoplayer/offline/DownloadManager$Listener$-CC;
     *     at AryDownloads$ProgressListener.onInitialized
     *
     * These callbacks arrive on DownloadManager's own Handler, so the crash
     * lands on the main looper outside any caller's try/catch.
     */
    private final class ProgressListener implements DownloadManager.Listener {

        @Override
        public void onInitialized(DownloadManager manager) {
            // Intentionally empty - see class comment.
        }

        @Override
        public void onDownloadsPausedChanged(DownloadManager manager, boolean downloadsPaused) {
        }

        @Override
        public void onDownloadRemoved(DownloadManager manager, Download download) {
            store.remove(download.request.id);
        }

        @Override
        public void onIdle(DownloadManager manager) {
        }

        @Override
        public void onRequirementsStateChanged(
                DownloadManager manager, Requirements requirements, int notMetRequirements) {
        }

        @Override
        public void onWaitingForRequirementsChanged(
                DownloadManager manager, boolean waitingForRequirements) {
        }

        /**
         * Older media3 declares this without the trailing Exception. Same
         * version-skew reason as DownloadHelper.Callback.onPrepared above, so no
         * @Override here either - it is the runtime that may call this form.
         */
        public void onDownloadChanged(DownloadManager manager, Download download) {
            onDownloadChanged(manager, download, null);
        }

        @Override
        public void onDownloadChanged(DownloadManager manager, Download download, Exception e) {
            DownloadEntry entry = store.get(download.request.id);
            if (entry == null) {
                return;
            }

            entry.progress = download.getPercentDownloaded();
            switch (download.state) {
                case Download.STATE_COMPLETED:
                    entry.state = DownloadEntry.STATE_COMPLETED;
                    entry.progress = 100f;
                    break;
                case Download.STATE_FAILED:
                    entry.state = DownloadEntry.STATE_FAILED;
                    break;
                case Download.STATE_DOWNLOADING:
                    entry.state = DownloadEntry.STATE_RUNNING;
                    break;
                default:
                    break;
            }
            store.put(entry);
        }
    }
}
