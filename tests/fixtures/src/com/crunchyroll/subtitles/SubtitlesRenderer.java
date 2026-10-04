package com.crunchyroll.subtitles;
import com.crunchyroll.subtitles.data.AssFrames;
public interface SubtitlesRenderer { AssFrames renderFrame(long track, long milliseconds); }
