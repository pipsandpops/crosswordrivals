package com.wordrivals.game;

import android.app.Activity;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

public class MainActivity extends Activity {
    private GameView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        FrameLayout root = new FrameLayout(this);
        FrameLayout banner = new FrameLayout(this);          // banner advert strip at the bottom
        banner.setBackgroundColor(0xFFEDF1F8);
        view = new GameView(this, banner);
        root.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(banner, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, new OnBackInvokedCallback() {
                @Override
                public void onBackInvoked() { goBack(); }
            });
        }
    }

    @Override
    public void onBackPressed() { goBack(); }

    private void goBack() { if (!view.back()) finish(); }

    @Override
    protected void onPause() {
        super.onPause();
        if (view != null) view.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (view != null) view.resume();
    }

    @Override
    protected void onDestroy() {
        if (view != null) view.shutdown();
        super.onDestroy();
    }
}
