package androidx.media3.common;
import android.net.Uri; import java.util.*;
public final class MediaItem {
 public final java.util.List<SubtitleConfiguration> subtitles;
 MediaItem(List<SubtitleConfiguration> list){subtitles=list;}
 public static final class Builder {
  public Uri b; private List<SubtitleConfiguration> subtitles=Collections.emptyList();
  public Builder k(List<SubtitleConfiguration> list){subtitles=list;return this;}
  public MediaItem a(){return new MediaItem(subtitles);}
 }
 public static final class SubtitleConfiguration {
  public Uri uri; public String mime,language; public int flags;
  public static final class Builder {
   private final SubtitleConfiguration value=new SubtitleConfiguration();
   public Builder(Uri uri){value.uri=uri;}
   public Builder n(String mime){value.mime=mime;return this;}
   public Builder m(String language){value.language=language;return this;}
   public Builder p(int flags){value.flags=flags;return this;}
   public SubtitleConfiguration i(){return value;}
  }
 }
}
