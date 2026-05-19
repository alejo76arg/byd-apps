package com.wheregoes.petmode;

import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;

public class PawRemovalListener implements Animation.AnimationListener {
    private final Handler handler;
    private final ViewGroup container;
    private final View paw;

    PawRemovalListener(Handler handler, ViewGroup container, View paw) {
        this.handler = handler;
        this.container = container;
        this.paw = paw;
    }

    @Override public void onAnimationStart(Animation a) {}
    @Override public void onAnimationRepeat(Animation a) {}
    @Override public void onAnimationEnd(Animation a) {
        handler.post(() -> container.removeView(paw));
    }
}
