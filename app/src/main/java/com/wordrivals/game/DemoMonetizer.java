package com.wordrivals.game;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** A pretend store for test builds without Google's libraries. Nothing is charged. */
final class DemoMonetizer implements Monetizer {
    private final Handler main = new Handler(Looper.getMainLooper());
    private Listener listener;
    private boolean noAds, want = true;
    private TextView banner;

    public void start(Activity activity, Listener l) {
        listener = l;
        main.postDelayed(new Runnable() { public void run() { listener.prices(); } }, 300);
    }

    public void resume() { }
    public void stop() { }
    public String price(String product) { return Game.NOADS_ID.equals(product) ? "$3.99" : "$1.99"; }

    public void buy(final String product) {
        main.postDelayed(new Runnable() {
            public void run() {
                if (Game.NOADS_ID.equals(product)) { noAds = true; listener.owned(product, true); }
                else listener.granted(product);
                listener.message("Demo purchase: no money was taken.");
            }
        }, 700);
    }

    public void restore() {
        if (noAds) listener.owned(Game.NOADS_ID, true);
        listener.message(noAds ? "Demo: purchases restored." : "Demo build: nothing to restore.");
    }

    public boolean videoReady() { return true; }

    public void showVideo(final int kind) {
        listener.message("Demo build: a real video would play here.");
        main.postDelayed(new Runnable() { public void run() { listener.reward(kind); } }, 1200);
    }

    public boolean interstitialReady() { return true; }
    public void showInterstitial() { listener.message("Demo build: a full-screen advert would show here."); }
    public void setWantInterstitials(boolean w) {
        want = w;
        if (banner != null) {
            banner.setVisibility(want ? View.VISIBLE : View.GONE);
            listener.bannerHeight(want ? banner.getLayoutParams().height : 0);
        }
    }

    public void attachBanner(ViewGroup holder) {
        float dp = holder.getResources().getDisplayMetrics().density;
        banner = new TextView(holder.getContext());
        banner.setText("Test banner advert (demo build)");
        banner.setGravity(Gravity.CENTER);
        banner.setTextColor(0xFF8C96AE);
        banner.setBackgroundColor(0xFFE3E8F0);
        int hpx = Math.round(50 * dp);
        holder.addView(banner, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, hpx));
        banner.setVisibility(want ? View.VISIBLE : View.GONE);
        if (want && listener != null) listener.bannerHeight(hpx);
    }
    public boolean privacyOptionsRequired() { return false; }
    public void showPrivacyOptions() { }
    public void requestReview() { listener.message("Demo build: Google's rating card would show here."); }
    public String note() { return "Demo store in this test build: nothing is charged and no real adverts show."; }
}
