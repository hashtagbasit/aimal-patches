package com.crunchyroll.subtitles;
import com.crunchyroll.subtitles.data.AssFrames;
/** Shape fixture, not a replacement for testing the vendor's APK. */
public final class SubtitlesRendererImpl implements SubtitlesRenderer {
    static { System.loadLibrary("aimalfixture"); }
    public int nativeCalls;
    public String originalScript;
    private long nextHandle = 42;
    public long loadTrack(String script) { originalScript = script; return nextHandle++; }
    public native AssFrames renderFrame(long track, long milliseconds);
    public void destroy(long track) { originalScript = null; }
}
