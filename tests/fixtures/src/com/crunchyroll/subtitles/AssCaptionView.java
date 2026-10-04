package com.crunchyroll.subtitles;
import com.crunchyroll.subtitles.data.AssFrames;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
public final class AssCaptionView extends View {
    public AssFrames frame;
    public AssCaptionView(Context context) { super(context); }
    @Override protected void onDraw(Canvas canvas) { canvas.drawColor(Color.RED); }
}
