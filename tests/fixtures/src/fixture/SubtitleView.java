package fixture;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.widget.FrameLayout;
public final class SubtitleView extends FrameLayout {
    public SubtitleView(Context context) {
        super(context);
        addView(new android.view.View(context) {
            @Override protected void onDraw(Canvas canvas) { canvas.drawColor(Color.RED); }
        }, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }
    public void setApplyEmbeddedStyles(boolean enabled) { }
    public void setCues(java.util.List<?> cues) { }
}
