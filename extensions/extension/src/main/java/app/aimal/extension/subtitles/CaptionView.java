package app.aimal.extension.subtitles;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

public final class CaptionView extends View {
    private final CaptionPainter painter = new CaptionPainter();
    private String text = "";

    public CaptionView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setText(String value) {
        if (value == null) value = "";
        if (!text.equals(value)) { text = value; invalidate(); }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        painter.draw(canvas, getWidth(), getHeight(), text, SubtitleSettings.style(getContext()), SubtitleSettings.revision());
    }
}
