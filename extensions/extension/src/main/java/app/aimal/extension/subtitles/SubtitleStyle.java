package app.aimal.extension.subtitles;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/** The complete Noir style schema. Pixel measurements are Android display pixels. */
public final class SubtitleStyle {
    public float fontSize = 38, backgroundOpacity = .23f, bottomOffset = 4;
    public int textColor = Color.WHITE, backgroundColor = Color.BLACK, fontWeight = 500;
    public String fontFamily = "sans-serif", fontCssUrl = "";
    public boolean useCustomMaxWidth, textShadow = true, enabled = true;
    public float maxWidth = 78, paddingX = 12, paddingY = 8, borderRadius = 8;
    public float lineHeight = 1.18f, letterSpacing = .2f;

    public static SubtitleStyle read(Context context) {
        SharedPreferences p = context.getSharedPreferences("aimal_subtitle_style_v2", 0);
        SubtitleStyle s = new SubtitleStyle();
        s.fontSize = bounded(p.getFloat("fontSize", s.fontSize), 8, 200, 38);
        s.backgroundOpacity = bounded(p.getFloat("backgroundOpacity", s.backgroundOpacity), 0, 1, .23f);
        s.bottomOffset = bounded(p.getFloat("bottomOffset", s.bottomOffset), 0, 100, 4);
        s.textColor = p.getInt("textColor", s.textColor);
        s.backgroundColor = p.getInt("backgroundColor", s.backgroundColor);
        s.fontWeight = Math.max(100, Math.min(900, p.getInt("fontWeight", s.fontWeight)));
        s.fontFamily = p.getString("fontFamily", s.fontFamily);
        s.fontCssUrl = p.getString("fontCssUrl", "");
        s.useCustomMaxWidth = p.getBoolean("useCustomMaxWidth", false);
        s.textShadow = p.getBoolean("textShadow", true);
        s.enabled = p.getBoolean("enabled", true);
        s.maxWidth = bounded(p.getFloat("maxWidth", s.maxWidth), 10, 100, 78);
        s.paddingX = bounded(p.getFloat("paddingX", s.paddingX), 0, 80, 12);
        s.paddingY = bounded(p.getFloat("paddingY", s.paddingY), 0, 80, 8);
        s.borderRadius = bounded(p.getFloat("borderRadius", s.borderRadius), 0, 80, 8);
        s.lineHeight = bounded(p.getFloat("lineHeight", s.lineHeight), .5f, 3, 1.18f);
        s.letterSpacing = bounded(p.getFloat("letterSpacing", s.letterSpacing), -5, 10, .2f);
        return s;
    }

    public void save(Context context) {
        context.getSharedPreferences("aimal_subtitle_style_v2", 0).edit()
                .putFloat("fontSize", fontSize).putInt("textColor", textColor)
                .putInt("backgroundColor", backgroundColor).putFloat("backgroundOpacity", backgroundOpacity)
                .putFloat("bottomOffset", bottomOffset).putInt("fontWeight", fontWeight)
                .putString("fontFamily", fontFamily).putString("fontCssUrl", fontCssUrl)
                .putBoolean("useCustomMaxWidth", useCustomMaxWidth).putFloat("maxWidth", maxWidth)
                .putFloat("paddingX", paddingX).putFloat("paddingY", paddingY)
                .putFloat("borderRadius", borderRadius).putFloat("lineHeight", lineHeight)
                .putFloat("letterSpacing", letterSpacing).putBoolean("textShadow", textShadow)
                .putBoolean("enabled", enabled).apply();
    }

    private static float bounded(float value, float min, float max, float fallback) {
        return Float.isNaN(value) || Float.isInfinite(value) ? fallback : Math.max(min, Math.min(max, value));
    }
}
