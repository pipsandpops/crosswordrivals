package com.wordrivals.game;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryProductDetailsResult;
import com.android.billingclient.api.QueryPurchasesParams;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.ump.ConsentForm;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.FormError;
import com.google.android.ump.UserMessagingPlatform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The real store: Google Play Billing Library 8 for Remove Ads (one-time, acknowledged) and hint packs
 * (consumed, so they can be bought again), and Google Mobile Ads for rewarded videos and the occasional
 * full-screen advert, behind Google's consent form (UMP) where the law requires one. Hints are only
 * added after the consume succeeds, so a crash cannot give them twice or lose them.
 *
 * This file lives in app/src/play so that builds without Google's libraries can leave it out.
 */
final class PlayMonetizer implements Monetizer, PurchasesUpdatedListener {

    /** BEFORE RELEASE: replace with your own ad unit IDs from AdMob. These are Google's public test units. */
    static final String REWARDED_UNIT = "ca-app-pub-3940256099942544/5224354917";
    static final String INTERSTITIAL_UNIT = "ca-app-pub-3940256099942544/1033173712";
    static final String BANNER_UNIT = "ca-app-pub-3940256099942544/9214589741";
    private static final String[] PRODUCTS = {Game.NOADS_ID, Game.HINTS_ID};
    private static final String[] KEEPS = {Game.NOADS_ID};

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, ProductDetails> products = new ConcurrentHashMap<String, ProductDetails>();
    private Activity activity;
    private Listener listener;
    private BillingClient billing;
    private volatile boolean billingReady;
    private ConsentInformation consent;
    private RewardedAd rewarded;
    private InterstitialAd interstitial;
    private boolean adsStarted, loading, interLoading, wantInterstitials = true;
    private int failures;
    private ViewGroup bannerHolder;
    private AdView banner;
    private AdSize bannerSize;

    // ---------------- purchases ----------------

    public void start(Activity a, Listener l) {
        activity = a;
        listener = l;
        billing = BillingClient.newBuilder(a)
                .setListener(this)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection()
                .build();
        billing.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult result) {
                if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
                billingReady = true;
                queryProducts();
                queryPurchases(false);
            }

            @Override
            public void onBillingServiceDisconnected() { }        // the library reconnects by itself
        });
        startAds();
    }

    public void resume() {
        if (billingReady) queryPurchases(false);
        main.post(new Runnable() { public void run() { loadVideo(); loadInterstitial(); } });
    }

    public void stop() {
        if (banner != null) { banner.destroy(); banner = null; }
        if (billing != null) billing.endConnection();
        billingReady = false;
    }

    private void queryProducts() {
        List<QueryProductDetailsParams.Product> list = new ArrayList<QueryProductDetailsParams.Product>();
        for (String id : PRODUCTS) {
            list.add(QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(BillingClient.ProductType.INAPP).build());
        }
        billing.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(list).build(), new ProductDetailsResponseListener() {
            @Override
            public void onProductDetailsResponse(BillingResult result, QueryProductDetailsResult details) {
                if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
                for (ProductDetails d : details.getProductDetailsList()) products.put(d.getProductId(), d);
                listener.prices();
            }
        });
    }

    public String price(String product) {
        ProductDetails d = products.get(product);
        if (d == null || d.getOneTimePurchaseOfferDetails() == null) return null;
        return d.getOneTimePurchaseOfferDetails().getFormattedPrice();
    }

    public void buy(String product) {
        ProductDetails d = products.get(product);
        if (!billingReady || d == null) { listener.message("Google Play is not available right now."); return; }
        BillingFlowParams params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()))
                .build();
        BillingResult result = billing.launchBillingFlow(activity, params);
        if (result.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) queryPurchases(true);
    }

    public void restore() {
        if (!billingReady) { listener.message("Google Play is not available right now."); return; }
        queryPurchases(true);
    }

    @Override
    public void onPurchasesUpdated(BillingResult result, List<Purchase> purchases) {
        int code = result.getResponseCode();
        if (code == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase p : purchases) handle(p);
        } else if (code == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            queryPurchases(true);
        } else if (code != BillingClient.BillingResponseCode.USER_CANCELED) {
            listener.message("The purchase did not go through. You have not been charged.");
        }
    }

    private void handle(final Purchase p) {
        if (p.getPurchaseState() == Purchase.PurchaseState.PENDING) {
            listener.message("Payment pending. It will be added when it completes.");
            return;
        }
        if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) return;
        for (final String product : p.getProducts()) {
            if (Game.NOADS_ID.equals(product)) {
                if (p.isAcknowledged()) {
                    listener.owned(product, true);
                } else {
                    billing.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.getPurchaseToken()).build(),
                            new AcknowledgePurchaseResponseListener() {
                                @Override
                                public void onAcknowledgePurchaseResponse(BillingResult r) {
                                    if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) listener.owned(product, true);
                                }
                            });
                }
            } else if (Game.HINTS_ID.equals(product)) {
                billing.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(p.getPurchaseToken()).build(), new ConsumeResponseListener() {
                    @Override
                    public void onConsumeResponse(BillingResult r, String token) {
                        if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) listener.granted(product);
                    }
                });
            }
        }
    }

    /** Asks Google Play what this account owns. It also finishes purchases that were interrupted. */
    private void queryPurchases(final boolean report) {
        billing.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(), new PurchasesResponseListener() {
            @Override
            public void onQueryPurchasesResponse(BillingResult result, List<Purchase> purchases) {
                if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                    if (report) listener.message("Could not reach Google Play. Try again later.");
                    return;
                }
                boolean any = false;
                for (String keep : KEEPS) {
                    boolean has = false;
                    for (Purchase p : purchases) if (p.getPurchaseState() == Purchase.PurchaseState.PURCHASED && p.getProducts().contains(keep)) has = true;
                    if (!has) listener.owned(keep, false);                 // also takes it away after a refund
                    any |= has;
                }
                for (Purchase p : purchases) handle(p);
                if (report) listener.message(any ? "Purchases restored." : "No purchases to restore on this Google account.");
            }
        });
    }

    // ---------------- rewarded videos ----------------

    private void startAds() {
        consent = UserMessagingPlatform.getConsentInformation(activity);
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
        consent.requestConsentInfoUpdate(activity, params, new ConsentInformation.OnConsentInfoUpdateSuccessListener() {
            @Override
            public void onConsentInfoUpdateSuccess() {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, new ConsentForm.OnConsentFormDismissedListener() {
                    @Override
                    public void onConsentFormDismissed(FormError error) { initAds(); }
                });
            }
        }, new ConsentInformation.OnConsentInfoUpdateFailureListener() {
            @Override
            public void onConsentInfoUpdateFailure(FormError error) { initAds(); }
        });
        initAds();                                                       // consent from an earlier session may already allow ads
    }

    private void initAds() {
        if (adsStarted || consent == null || !consent.canRequestAds()) return;
        adsStarted = true;
        new Thread(new Runnable() {
            public void run() {
                MobileAds.initialize(activity, new OnInitializationCompleteListener() {
                    @Override
                    public void onInitializationComplete(InitializationStatus status) {
                        main.post(new Runnable() { public void run() { loadVideo(); loadInterstitial(); loadBanner(); } });
                    }
                });
            }
        }).start();
    }

    private void loadVideo() {
        if (!adsStarted || loading || rewarded != null) return;
        loading = true;
        RewardedAd.load(activity, REWARDED_UNIT, new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(RewardedAd ad) {
                rewarded = ad;
                loading = false;
                failures = 0;
            }

            @Override
            public void onAdFailedToLoad(LoadAdError error) {
                loading = false;
                failures++;
                main.postDelayed(new Runnable() { public void run() { loadVideo(); } }, Math.min(120000L, 10000L * failures));
            }
        });
    }

    public boolean videoReady() { return rewarded != null; }

    public void showVideo(final int kind) {
        RewardedAd ad = rewarded;
        if (ad == null) { listener.message("No video available right now. Try again in a minute."); return; }
        rewarded = null;
        final boolean[] earned = {false};
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                if (earned[0]) listener.reward(kind);
                loadVideo();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(AdError error) {
                listener.message("The video could not be shown.");
                loadVideo();
            }
        });
        ad.show(activity, new OnUserEarnedRewardListener() {
            @Override
            public void onUserEarnedReward(RewardItem item) { earned[0] = true; }
        });
    }

    // ---------------- full-screen adverts ----------------

    private void loadInterstitial() {
        if (!adsStarted || !wantInterstitials || interLoading || interstitial != null) return;
        interLoading = true;
        InterstitialAd.load(activity, INTERSTITIAL_UNIT, new AdRequest.Builder().build(), new InterstitialAdLoadCallback() {
            @Override
            public void onAdLoaded(InterstitialAd ad) {
                interstitial = ad;
                interLoading = false;
            }

            @Override
            public void onAdFailedToLoad(LoadAdError error) {
                interLoading = false;
                main.postDelayed(new Runnable() { public void run() { loadInterstitial(); } }, 60000L);
            }
        });
    }

    public boolean interstitialReady() { return wantInterstitials && interstitial != null; }

    public void showInterstitial() {
        InterstitialAd ad = interstitial;
        if (ad == null || !wantInterstitials) return;
        interstitial = null;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() { loadInterstitial(); }

            @Override
            public void onAdFailedToShowFullScreenContent(AdError error) { loadInterstitial(); }
        });
        ad.show(activity);
    }

    public void setWantInterstitials(boolean want) {
        wantInterstitials = want;
        if (!want) {
            interstitial = null;
            main.post(new Runnable() { public void run() { removeBanner(); } });
        } else main.post(new Runnable() { public void run() { loadInterstitial(); loadBanner(); } });
    }

    // ---------------- banner ----------------

    public void attachBanner(ViewGroup holder) {
        bannerHolder = holder;
        main.post(new Runnable() { public void run() { loadBanner(); } });
    }

    /** One banner at the bottom; space is reserved only once an advert has loaded. */
    private void loadBanner() {
        if (!adsStarted || !wantInterstitials || bannerHolder == null || banner != null) return;
        float density = activity.getResources().getDisplayMetrics().density;
        int widthDp = (int) (activity.getResources().getDisplayMetrics().widthPixels / density);
        int heightDp = (int) (activity.getResources().getDisplayMetrics().heightPixels / density);
        // tall phones: Google's large adaptive banner; short phones: the classic 320x50 so the crossword keeps its room
        bannerSize = heightDp >= 720 ? AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, widthDp) : AdSize.BANNER;
        banner = new AdView(activity);
        banner.setAdUnitId(BANNER_UNIT);
        banner.setAdSize(bannerSize);
        banner.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                if (banner != null && wantInterstitials) listener.bannerHeight(bannerSize.getHeightInPixels(activity));
            }
        });
        bannerHolder.addView(banner, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, bannerSize.getHeightInPixels(activity)));
        banner.loadAd(new AdRequest.Builder().build());
    }

    private void removeBanner() {
        if (banner != null) {
            if (bannerHolder != null) bannerHolder.removeView(banner);
            banner.destroy();
            banner = null;
        }
        listener.bannerHeight(0);
    }

    public boolean privacyOptionsRequired() {
        return consent != null && consent.getPrivacyOptionsRequirementStatus() == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
    }

    public void showPrivacyOptions() {
        UserMessagingPlatform.showPrivacyOptionsForm(activity, new ConsentForm.OnConsentFormDismissedListener() {
            @Override
            public void onConsentFormDismissed(FormError error) { initAds(); }
        });
    }

    public String note() { return null; }

    // ---------------- rating ----------------

    /** Google decides whether the card actually shows (it is rate-limited per user). */
    public void requestReview() {
        try {
            final ReviewManager manager = ReviewManagerFactory.create(activity);
            manager.requestReviewFlow().addOnCompleteListener(new OnCompleteListener<ReviewInfo>() {
                @Override
                public void onComplete(Task<ReviewInfo> task) {
                    if (task.isSuccessful()) manager.launchReviewFlow(activity, task.getResult());
                }
            });
        } catch (RuntimeException e) {
            // Play Store missing on this device: nothing to do
        }
    }
}
