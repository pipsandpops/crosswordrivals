package com.wordrivals.game;

import android.app.Activity;
import android.view.ViewGroup;

/**
 * Purchases and adverts, kept behind one small interface so the game code never touches Google's
 * libraries directly.
 *
 *   PlayMonetizer  the real one: Google Play Billing and Google Mobile Ads (used in the Gradle build)
 *   DemoMonetizer  a pretend store for test builds made without Google's libraries; nothing is charged
 *   None           what the app falls back to if the real store cannot start on a device
 */
interface Monetizer {

    /** Answers from the store. They may arrive on any thread; GameView moves them to the main thread. */
    interface Listener {
        void owned(String product, boolean owned);
        void granted(String product);
        void prices();
        void message(String text);
        void reward(int kind);
        /** Height in pixels the banner now takes at the bottom of the screen (0 = no banner). */
        void bannerHeight(int px);
    }

    void start(Activity activity, Listener listener);
    void resume();
    void stop();
    String price(String product);
    void buy(String product);
    void restore();
    boolean videoReady();
    void showVideo(int kind);
    boolean interstitialReady();
    void showInterstitial();
    /** False once the player owns No Ads: full-screen adverts and the banner stop. */
    void setWantInterstitials(boolean want);
    /** The bottom strip where the banner advert goes. */
    void attachBanner(ViewGroup holder);
    boolean privacyOptionsRequired();
    void showPrivacyOptions();
    String note();
    /** Google Play's in-app review card. */
    void requestReview();

    /** No store at all: prices are unknown and no adverts load. */
    final class None implements Monetizer {
        public void start(Activity activity, Listener listener) { }
        public void resume() { }
        public void stop() { }
        public String price(String product) { return null; }
        public void buy(String product) { }
        public void restore() { }
        public boolean videoReady() { return false; }
        public void showVideo(int kind) { }
        public boolean interstitialReady() { return false; }
        public void showInterstitial() { }
        public void setWantInterstitials(boolean want) { }
        public void attachBanner(ViewGroup holder) { }
        public boolean privacyOptionsRequired() { return false; }
        public void showPrivacyOptions() { }
        public String note() { return null; }
        public void requestReview() { }
    }
}
