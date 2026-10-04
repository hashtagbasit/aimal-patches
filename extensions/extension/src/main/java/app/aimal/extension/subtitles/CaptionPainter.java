package app.aimal.extension.subtitles;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

/** One rendering path for the example, media3 cues and Crunchyroll ASS text. */
public final class CaptionPainter {
    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
    private StaticLayout layout;
    private String previousText;
    private int previousWidth;
    private long previousRevision = -1;

    public void draw(Canvas canvas, int width, int height, String text, SubtitleStyle s, long revision) {
        if (width <= 0 || height <= 0 || text == null || text.isEmpty()) return;
        // Noir uses fixed pixel typography: resizing changes wrapping, not font size.
        float scale = 1f;
        float textSize = s.fontSize * scale;
        float px = s.paddingX * scale, py = s.paddingY * scale;
        int available = Math.max(1, (int) (width * (s.useCustomMaxWidth ? s.maxWidth : 100) / 100f - px * 2));
        if (layout == null || !text.equals(previousText) || previousWidth != width || previousRevision != revision) {
            paint.setTextSize(textSize);
            paint.setColor(s.textColor);
            paint.setTypeface(SubtitleFonts.resolve(s.fontFamily, s.fontWeight));
            paint.setLetterSpacing(s.letterSpacing / s.fontSize);
            if (s.textShadow) paint.setShadowLayer(3 * scale, 0, 2 * scale, 0xC7000000);
            else paint.clearShadowLayer();
            StaticLayout measure = StaticLayout.Builder.obtain(text, 0, text.length(), paint, available)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false)
                    .setLineSpacing(0, s.lineHeight).build();
            float widest = 0;
            for (int i = 0; i < measure.getLineCount(); i++) widest = Math.max(widest, measure.getLineWidth(i));
            int tightWidth = Math.min(available, Math.max(1, (int) Math.ceil(widest)));
            layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, tightWidth)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false)
                    .setLineSpacing(0, s.lineHeight).build();
            previousText = text;
            previousWidth = width;
            previousRevision = revision;
        }
        float boxWidth = layout.getWidth() + px * 2, boxHeight = layout.getHeight() + py * 2;
        float left = (width - boxWidth) / 2f;
        float top = Math.max(0, height * (1 - s.bottomOffset / 100f) - boxHeight);
        background.setColor((s.backgroundColor & 0x00FFFFFF) | (Math.round(s.backgroundOpacity * 255) << 24));
        canvas.drawRoundRect(left, top, left + boxWidth, top + boxHeight,
                s.borderRadius * scale, s.borderRadius * scale, background);
        canvas.save();
        canvas.translate(left + px, top + py);
        layout.draw(canvas);
        canvas.restore();
    }
}
