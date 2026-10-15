package com.wordrivals.game;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

/** Android shell around Game: drawing, touch, sounds, saves, purchases and adverts. */
public class GameView extends View implements Game.Painter, Game.Host, Monetizer.Listener {
    private final SharedPreferences prefs;
    private final Game game;
    private final Monetizer store;
    private final float dp;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint pix = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rectF = new RectF();
    private final Rect src = new Rect();
    private final int[] soundIds = new int[Synth.COUNT];
    /** Picture clues and avatars from assets/pic, least recently used dropped first. */
    private final Map<String, Bitmap> pictures = new LinkedHashMap<String, Bitmap>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Bitmap> e) { return size() > 120; }
    };
    private Typeface bold, regular;
    private SoundPool pool;
    private Canvas canvas;
    private int insetTop, insetBottom, bannerPx;
    private final FrameLayout bannerHolder;
    private long lastFrame;

    public GameView(Context context, FrameLayout bannerHolder) {
        super(context);
        this.bannerHolder = bannerHolder;
        prefs = context.getSharedPreferences("wordrivals", Context.MODE_PRIVATE);
        dp = getResources().getDisplayMetrics().density;
        try {
            bold = Typeface.createFromAsset(context.getAssets(), "Nunito-800.ttf");
            regular = Typeface.createFromAsset(context.getAssets(), "Nunito-600.ttf");
        } catch (RuntimeException e) {
            bold = Typeface.create("sans-serif", Typeface.BOLD);
            regular = Typeface.create("sans-serif", Typeface.NORMAL);
        }
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        ring.setStyle(Paint.Style.STROKE);
        setHapticFeedbackEnabled(true);
        loadSounds(context);
        store = MonetizerFactory.create();
        game = new Game(this);
        store.setWantInterstitials(!game.adFree());
        store.start((Activity) context, this);
        store.attachBanner(bannerHolder);
        if (Reminder.enabled(context)) Reminder.schedule(context, true);
    }

    private void loadSounds(Context context) {
        try {
            pool = new SoundPool.Builder().setMaxStreams(8).setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
            for (int id = 0; id < Synth.COUNT; id++) {
                File f = new File(context.getCacheDir(), "wr_sound" + id + ".wav");
                FileOutputStream out = new FileOutputStream(f);
                try { out.write(Synth.wav(id)); } finally { out.close(); }
                soundIds[id] = pool.load(f.getAbsolutePath(), 1);
            }
        } catch (Exception e) {
            pool = null;
        }
    }

    // ---- lifecycle ----

    void pause() { game.pause(); }

    void resume() {
        lastFrame = 0;
        store.resume();
        invalidate();
    }

    void shutdown() {
        game.save();
        store.stop();
        if (pool != null) { pool.release(); pool = null; }
    }

    boolean back() {
        boolean handled = game.back();
        invalidate();
        return handled;
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        insetTop = insets.getSystemWindowInsetTop();
        insetBottom = insets.getSystemWindowInsetBottom();
        ViewGroup.LayoutParams lp = bannerHolder.getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams && ((FrameLayout.LayoutParams) lp).bottomMargin != insetBottom) {
            ((FrameLayout.LayoutParams) lp).bottomMargin = insetBottom;       // keep the banner above the navigation bar
            bannerHolder.setLayoutParams(lp);
        }
        invalidate();
        return insets;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: game.down(e.getX(), e.getY()); break;
            case MotionEvent.ACTION_MOVE: game.move(e.getX(), e.getY()); break;
            case MotionEvent.ACTION_UP: game.up(e.getX(), e.getY()); break;
            case MotionEvent.ACTION_CANCEL: game.cancel(); break;
            default: break;
        }
        invalidate();
        return true;
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0.016f : (now - lastFrame) / 1000f;
        lastFrame = now;
        game.update(dt);
        canvas = c;
        // very short phones: hide the banner during a match so the crossword stays readable
        boolean showBanner = !(game.screen == Game.PLAY && (getHeight() - insetTop - insetBottom) / dp < 680);
        int vis = showBanner ? View.VISIBLE : View.INVISIBLE;
        if (bannerHolder.getVisibility() != vis) bannerHolder.setVisibility(vis);
        game.draw(this, getWidth(), getHeight(), dp, insetTop, insetBottom + (showBanner ? bannerPx : 0));
        canvas = null;
        postInvalidateOnAnimation();
    }

    // ---- Game.Painter ----

    @Override
    public void roundRect(float l, float t, float r, float b, float rad, int color) {
        rectF.set(l, t, r, b);
        fill.setColor(color);
        if (rad <= 0) canvas.drawRect(rectF, fill); else canvas.drawRoundRect(rectF, rad, rad, fill);
    }

    @Override
    public void circle(float cx, float cy, float r, int color) {
        fill.setColor(color);
        canvas.drawCircle(cx, cy, r, fill);
    }

    @Override
    public void ring(float cx, float cy, float r, float width, int color) {
        ring.setColor(color);
        ring.setStrokeWidth(width);
        canvas.drawCircle(cx, cy, r, ring);
    }

    @Override
    public void poly(float[] pts, int n, int color) {
        path.rewind();
        path.moveTo(pts[0], pts[1]);
        for (int i = 1; i < n; i++) path.lineTo(pts[i * 2], pts[i * 2 + 1]);
        path.close();
        fill.setColor(color);
        canvas.drawPath(path, fill);
    }

    @Override
    public void polyline(float[] pts, int n, float width, int color) {
        path.rewind();
        path.moveTo(pts[0], pts[1]);
        for (int i = 1; i < n; i++) path.lineTo(pts[i * 2], pts[i * 2 + 1]);
        stroke.setColor(color);
        stroke.setStrokeWidth(width);
        canvas.drawPath(path, stroke);
    }

    @Override
    public void text(String s, float x, float y, float size, int color, boolean isBold, int align) {
        text.setTypeface(isBold ? bold : regular);
        text.setTextSize(size);
        text.setColor(color);
        text.setTextAlign(align == 1 ? Paint.Align.LEFT : align == 2 ? Paint.Align.RIGHT : Paint.Align.CENTER);
        canvas.drawText(s, x, y, text);
    }

    @Override
    public void smallText(String s, float x, float y, float size, int color, boolean isBold, int align) {
        text(s, x, y, size, color, isBold, align);
    }

    @Override
    public float textWidth(String s, float size, boolean isBold) {
        text.setTypeface(isBold ? bold : regular);
        text.setTextSize(size);
        return text.measureText(s);
    }

    @Override public void clip(float l, float t, float r, float b) { canvas.save(); canvas.clipRect(l, t, r, b); }
    @Override public void unclip() { canvas.restore(); }

    @Override
    public void gradient(float l, float t, float r, float b, int top, int bottom) {
        shade.setShader(new LinearGradient(0, t, 0, b, top, bottom, Shader.TileMode.CLAMP));
        canvas.drawRect(l, t, r, b, shade);
        shade.setShader(null);
    }

    @Override
    public void glow(float cx, float cy, float r, int color) {
        if (r <= 0) return;
        shade.setShader(new RadialGradient(cx, cy, r, color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r, shade);
        shade.setShader(null);
    }

    @Override
    public void image(String name, float l, float t, float r, float b, int alpha) {
        Bitmap bm = pictures.get(name);
        if (bm == null) {
            if (pictures.containsKey(name)) return;          // known missing
            try {
                InputStream in = getContext().getAssets().open("pic/" + name + ".webp");
                try { bm = BitmapFactory.decodeStream(in); } finally { in.close(); }
            } catch (Exception e) {
                bm = null;
            }
            pictures.put(name, bm);
            if (bm == null) return;
        }
        src.set(0, 0, bm.getWidth(), bm.getHeight());
        rectF.set(l, t, r, b);
        pix.setAlpha(Math.max(0, Math.min(255, alpha)));
        canvas.drawBitmap(bm, src, rectF, pix);
    }

    // ---- Game.Host ----

    @Override public String get(String key) { return prefs.getString(key, null); }
    @Override public void put(String key, String value) { prefs.edit().putString(key, value).apply(); }
    @Override public long now() { return System.currentTimeMillis(); }

    @Override
    public int today() {
        long now = System.currentTimeMillis();
        return (int) Math.floorDiv(now + TimeZone.getDefault().getOffset(now), 86400000L);
    }

    @Override
    public void sound(int id) {
        if (pool == null || id < 0 || id >= soundIds.length) return;
        pool.play(soundIds[id], 1f, 1f, 1, 0, 1f);
    }

    @Override
    public void haptic(boolean strong) {
        performHapticFeedback(strong ? HapticFeedbackConstants.LONG_PRESS : HapticFeedbackConstants.KEYBOARD_TAP);
    }

    @Override
    public String asset(String name) {
        try {
            InputStream in = getContext().getAssets().open(name);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            return out.toString("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void share(String msg) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, msg + "\nhttps://play.google.com/store/apps/details?id=" + getContext().getPackageName());
            getContext().startActivity(Intent.createChooser(i, null));
        } catch (Exception e) {
            game.say("Could not open sharing");
        }
    }

    @Override public void requestReview() { store.requestReview(); }

    @Override
    public void setReminder(boolean on) {
        Context c = getContext();
        if (on && android.os.Build.VERSION.SDK_INT >= 33 && c instanceof Activity
                && c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ((Activity) c).requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 41);
        }
        prefs.edit().putString("reminder", on ? "1" : "0").apply();
        Reminder.schedule(c, on);
    }

    @Override public String price(String product) { return store.price(product); }
    @Override public void buy(String product) { store.buy(product); }
    @Override public void restore() { store.restore(); }
    @Override public boolean videoReady() { return store.videoReady(); }
    @Override public void showVideo(int reward) { store.showVideo(reward); }
    @Override public boolean interstitialReady() { return store.interstitialReady(); }
    @Override public void showInterstitial() { store.showInterstitial(); }
    @Override public boolean privacyOptionsRequired() { return store.privacyOptionsRequired(); }
    @Override public void showPrivacyOptions() { store.showPrivacyOptions(); }
    @Override public String storeNote() { return store.note(); }

    // ---- answers from the store, moved to the main thread ----

    @Override
    public void owned(final String product, final boolean owned) {
        post(new Runnable() {
            public void run() {
                game.onOwned(product, owned);
                store.setWantInterstitials(!game.adFree());
                invalidate();
            }
        });
    }

    @Override public void granted(final String product) { post(new Runnable() { public void run() { game.onGranted(product); invalidate(); } }); }
    @Override public void bannerHeight(final int px) { post(new Runnable() { public void run() { bannerPx = px; invalidate(); } }); }
    @Override public void prices() { post(new Runnable() { public void run() { invalidate(); } }); }
    @Override public void message(final String t) { post(new Runnable() { public void run() { game.onStoreMessage(t); invalidate(); } }); }
    @Override public void reward(final int kind) { post(new Runnable() { public void run() { game.onVideoReward(kind); invalidate(); } }); }
}
