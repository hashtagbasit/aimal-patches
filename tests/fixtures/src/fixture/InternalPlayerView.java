package fixture;
import android.content.Context;
import android.widget.FrameLayout;
public final class InternalPlayerView extends FrameLayout {
    public final FrameLayout controller;
    public int resizeMode;
    public InternalPlayerView(Context context) {
        super(context); controller = new com.crunchyroll.player.presentation.controls.PlayerControlsLayout(context);
        FrameLayout unused = new FrameLayout(context);
        unused.setId(context.getResources().getIdentifier("exo_controller", "id", context.getPackageName()));
        unused.setVisibility(GONE); addView(unused);
        addView(controller, new FrameLayout.LayoutParams(-1, -1));
    }
    public void setupAdView(boolean enabled) { android.util.Log.d("fixture", "getAdViewGroup(...)"); }
    @Override public final void onAttachedToWindow() { super.onAttachedToWindow(); }
    public void setResizeMode(int mode) { resizeMode = mode; }
}
