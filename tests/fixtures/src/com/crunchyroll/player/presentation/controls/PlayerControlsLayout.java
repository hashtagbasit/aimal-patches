package com.crunchyroll.player.presentation.controls;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
public final class PlayerControlsLayout extends FrameLayout {
    private final View toolbar;
    public PlayerControlsLayout(Context context) {
        super(context); toolbar = new View(context);
        addView(toolbar, new FrameLayout.LayoutParams(-1, 48));
    }
    public View getPlayerToolbar() { return toolbar; }
}
