package app.aimal.extension.subtitles;

import android.content.Context;
import android.graphics.Color;
import org.json.JSONObject;

/** The complete Noir style schema. Pixel measurements are Android display pixels. */
public final class SubtitleStyle {
    // Default captured from the user's TV, before adding the style library.
    public float fontSize = 54, backgroundOpacity = .23f, bottomOffset = 4;
    public int textColor = Color.WHITE, backgroundColor = Color.BLACK, fontWeight = 500;
    public String fontFamily = "GothamPro", fontCssUrl = SubtitleFonts.DEFAULT_CSS;
    public boolean useCustomMaxWidth, textShadow = true, enabled = true;
    public float maxWidth = 78, paddingX = 12, paddingY = 8, borderRadius = 8;
    public float lineHeight = 1.18f, letterSpacing = .2f;
    public float outlineWidth = 0;
    public int outlineColor = Color.BLACK;

    public static SubtitleStyle read(Context context) {
        return fromJson(new JSONObject(context.getSharedPreferences("aimal_subtitle_style_v2", 0).getAll()));
    }

    public SubtitleStyle copy() { return fromJson(toJson()); }

    public JSONObject toJson() {
        JSONObject p = new JSONObject();
        try {
            p.put("fontSize", fontSize).put("backgroundOpacity", backgroundOpacity)
                .put("bottomOffset", bottomOffset).put("textColor", textColor)
                .put("backgroundColor", backgroundColor).put("fontWeight", fontWeight)
                .put("fontFamily", fontFamily).put("fontCssUrl", fontCssUrl)
                .put("useCustomMaxWidth", useCustomMaxWidth).put("textShadow", textShadow)
                .put("enabled", enabled).put("maxWidth", maxWidth).put("paddingX", paddingX)
                .put("paddingY", paddingY).put("borderRadius", borderRadius)
                .put("lineHeight", lineHeight).put("letterSpacing", letterSpacing)
                .put("outlineWidth", outlineWidth).put("outlineColor", outlineColor);
        } catch (org.json.JSONException invalid) { throw new IllegalArgumentException("Invalid subtitle style", invalid); }
        return p;
    }

    public static SubtitleStyle fromJson(JSONObject p) {
        SubtitleStyle s = new SubtitleStyle();
        s.fontSize = bounded((float)p.optDouble("fontSize", s.fontSize), 8, 200, s.fontSize);
        s.backgroundOpacity = bounded((float)p.optDouble("backgroundOpacity", s.backgroundOpacity), 0, 1, .23f);
        s.bottomOffset = bounded((float)p.optDouble("bottomOffset", s.bottomOffset), 0, 100, 4);
        s.textColor = p.optInt("textColor", s.textColor);
        s.backgroundColor = p.optInt("backgroundColor", s.backgroundColor);
        s.fontWeight = Math.max(100, Math.min(900, p.optInt("fontWeight", s.fontWeight)));
        s.fontFamily = p.optString("fontFamily", s.fontFamily);
        if (s.fontFamily.trim().isEmpty()) s.fontFamily = "sans-serif";
        s.fontCssUrl = p.optString("fontCssUrl", s.fontFamily.equals("GothamPro") ? SubtitleFonts.DEFAULT_CSS : "");
        s.useCustomMaxWidth = p.optBoolean("useCustomMaxWidth", false);
        s.textShadow = p.optBoolean("textShadow", true);
        s.enabled = p.optBoolean("enabled", true);
        s.maxWidth = bounded((float)p.optDouble("maxWidth", s.maxWidth), 10, 100, 78);
        s.paddingX = bounded((float)p.optDouble("paddingX", s.paddingX), 0, 80, 12);
        s.paddingY = bounded((float)p.optDouble("paddingY", s.paddingY), 0, 80, 8);
        s.borderRadius = bounded((float)p.optDouble("borderRadius", s.borderRadius), 0, 80, 8);
        s.lineHeight = bounded((float)p.optDouble("lineHeight", s.lineHeight), .5f, 3, 1.18f);
        s.letterSpacing = bounded((float)p.optDouble("letterSpacing", s.letterSpacing), -5, 10, .2f);
        s.outlineWidth = bounded((float)p.optDouble("outlineWidth", 0), 0, 8, 0);
        s.outlineColor = p.optInt("outlineColor", Color.BLACK);
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
                .putBoolean("enabled", enabled)
                .putFloat("outlineWidth", outlineWidth).putInt("outlineColor", outlineColor).apply();
    }

    private static float bounded(float value, float min, float max, float fallback) {
        return Float.isNaN(value) || Float.isInfinite(value) ? fallback : Math.max(min, Math.min(max, value));
    }
}
