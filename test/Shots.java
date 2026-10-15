package com.wordrivals.game;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/** Play Store screenshots, feature graphic and trailer frames (plus sound track) from the real game code. */
public class Shots extends Harness {
    static String KIT;

    static class TimedHost extends Host {
        List<float[]> events = new ArrayList<>(); float clock;
        public void sound(int id) { super.sound(id); events.add(new float[]{clock, id}); }
    }

    static Font f(boolean b, float size) { return (b ? bold : regular).deriveFont(size); }

    static Graphics2D nice(BufferedImage im) {
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return g;
    }

    static void centered(Graphics2D g, String s, float cx, float y, Font font, Color c, float maxW) {
        Font ff = font;
        float tw = (float) ff.getStringBounds(s, g.getFontRenderContext()).getWidth();
        if (tw > maxW) { ff = ff.deriveFont(ff.getSize2D() * maxW / tw); tw = maxW; }
        g.setFont(ff); g.setColor(c); g.drawString(s, cx - tw / 2, y);
    }

    static void shot(int n, String name, String head, String sub, int bgTop, int bgBottom, boolean keepToast) throws IOException {
        if (!keepToast) game.toastT = 0;
        BufferedImage app = draw().img;
        BufferedImage out = new BufferedImage(1080, 1920, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = nice(out);
        g.setPaint(new GradientPaint(0, 0, new Color(bgTop), 0, 1920, new Color(bgBottom)));
        g.fillRect(0, 0, 1080, 1920);
        // scattered letter tiles
        Random r = new Random(n * 31 + 7);
        String abc = "ABCDEFGHIJKLMNOPRSTUW";
        for (int k = 0; k < 9; k++) {
            int s = 46 + r.nextInt(30), x = r.nextInt(1040), y = r.nextInt(300);
            AffineTransform old = g.getTransform();
            g.rotate(Math.toRadians(r.nextInt(40) - 20), x + s / 2, y + s / 2);
            g.setColor(new Color(255, 255, 255, 38)); g.fill(new RoundRectangle2D.Float(x, y, s, s, 14, 14));
            centered(g, "" + abc.charAt(r.nextInt(abc.length())), x + s / 2f, y + s * 0.72f, f(true, s * 0.6f), new Color(255, 255, 255, 70), s);
            g.setTransform(old);
        }
        centered(g, head, 540, 165, f(true, 88), Color.WHITE, 990);
        centered(g, sub, 540, 244, f(false, 44), new Color(255, 255, 255, 235), 980);
        float sc = 1440f / app.getHeight();
        int pw = Math.round(app.getWidth() * sc), ph = 1440, px = (1080 - pw) / 2, py = 330;
        g.setColor(new Color(0, 0, 0, 60));
        g.fill(new RoundRectangle2D.Float(px - 4, py + 16, pw + 8, ph + 8, 70, 70));
        g.setColor(new Color(0x1F2A44));
        g.fill(new RoundRectangle2D.Float(px - 14, py - 14, pw + 28, ph + 28, 78, 78));
        g.setClip(new RoundRectangle2D.Float(px, py, pw, ph, 60, 60));
        g.drawImage(app, px, py, pw, ph, null);
        g.setClip(null);
        ImageIO.write(out, "png", new File(KIT, "screenshots/" + n + "-" + name + ".png"));
    }

    /** Plays turns (me placing everything that fits, carefully) until a share of the hidden letters is on the board. */
    static void playUntil(float share) {
        int start = game.emptyCells();
        int guard = 0;
        while (!game.over && game.emptyCells() > start * (1 - share) && guard++ < 3000) {
            if (game.myTurn()) {
                int[] m = smartMove();
                if (m == null) m = goodMove();
                if (m != null && game.players.get(0).rack.size() > 1) { tapTile(m[0]); tapCell(m[1]); }
                else tapR("done");
            } else step(0.2f);
        }
        while (!game.myTurn() && !game.over && guard++ < 6000) step(0.1f);
        step(1.4f);                               // let floaters and flashes fade
    }

    static int levelWithPicture(int from) {
        for (int i = from; i < game.levelCount(); i++) {
            Level lv = Level.parse(game.levelLines.get(i));
            int pics = 0; for (Level.Word w : lv.words) if (w.picture != null) pics++;
            if (pics >= 2) return i;
        }
        return from;
    }

    public static void main(String[] a) throws Exception {
        ASSETS = a[0]; PICS = a[1];
        bold = Font.createFont(Font.TRUETYPE_FONT, new File(ASSETS, "Nunito-800.ttf"));
        regular = Font.createFont(Font.TRUETYPE_FONT, new File(ASSETS, "Nunito-600.ttf"));
        KIT = a[2];
        new File(KIT, "screenshots").mkdirs();
        new File(KIT, "trailer/frames").mkdirs();
        BANNER = false;               // store screenshots show the game without the advert strip
        screenshots();
        feature();
        trailer();
        System.out.println("done");
    }

    static void screenshots() throws IOException {
        setSize(360, 760, 3f);
        IT = 30 * DP; IB = 12 * DP;
        fresh();
        game.soundOn = false; game.helpSeen = true;
        game.coins = 1240; game.hints = 4; game.wins = 23; game.games = 31;
        // 1. battle in progress, three rivals
        game.startLevel(levelWithPicture(17));
        draw();
        playUntil(0.35f);
        int[] m = smartMove(); if (m == null) m = goodMove();
        if (m != null) { tapTile(m[0]); game.selWord = game.lv.words.indexOf(game.lv.wordsAt(m[1]).get(0)); }
        draw();
        shot(1, "battle", "Crossword battles", "Take turns with friendly rivals", 0xFF4B80EE, 0xFF2850B8, false);
        // 2. picture clues
        for (int k = 0; k < game.lv.words.size(); k++) if (game.lv.words.get(k).picture != null && !game.complete(game.lv.words.get(k))) { game.selWord = k; break; }
        game.selTile = -1;
        shot(2, "pictures", "Picture clues too", "Some clues are emoji pictures, easy and fun", 0xFFFF8A5B, 0xFFF2613F, false);
        // 3. drag a tile
        m = goodMove();
        if (m != null) {
            draw();
            float[] t0 = game.tileCenter(m[0]), c1 = game.cellCenter(m[1]);
            float off = game.rackSizeForTest() * 0.35f;
            game.down(t0[0], t0[1]);
            for (int k = 1; k <= 6; k++) game.move(t0[0] + (c1[0] - t0[0]) * k * 0.8f / 6, t0[1] + (c1[1] + off - t0[1]) * k * 0.8f / 6);
            shot(4, "drag", "Drag letters into place", "A right square scores, a wrong one ends your turn", 0xFF2DBE7E, 0xFF1F9A63, false);
            game.cancel();
        }
        // 5. finish a word: big points
        int[] fin = null;
        List<Character> rack = game.players.get(0).rack;
        outer:
        for (int t = 0; t < rack.size(); t++) for (int i = 0; i < game.board.length; i++) {
            if (!game.lv.isLetter(i) || game.board[i] != 0 || game.lv.grid[i] != rack.get(t)) continue;
            game.board[i] = rack.get(t);
            boolean done = false; for (Level.Word w : game.lv.wordsAt(i)) if (game.complete(w)) done = true;
            game.board[i] = 0;
            if (done) { fin = new int[]{t, i}; break outer; }
        }
        if (fin == null) { playUntil(0.6f); m = smartMove(); fin = m; }
        if (fin != null) { tapTile(fin[0]); tapCell(fin[1]); step(0.5f); }
        shot(5, "words", "Finish words, win big", "Combos and double words for bonus points", 0xFFAB6CF0, 0xFF8A4FD6, false);
        // 8. hint
        step(1.5f);
        if (!game.myTurn()) while (!game.myTurn() && !game.over) step(0.1f);
        step(1.2f);
        if (game.myTurn()) { tapR("hint"); step(0.25f); }
        shot(8, "hints", "Hints when you're stuck", "A hint shows where one of your letters goes", 0xFFFFB938, 0xFFF29A12, false);
        // 7. win with a rank up
        game.hintCell = -1; game.hintTile = -1;
        SMART = true;
        for (int li = 18; li < 80; li++) {          // a match the careful player genuinely wins
            game.xp = Game.RANK_XP[4] - 20;
            game.startLevel(li); draw(); playOut(4000);
            if (game.rank(game.players.get(0)) == 1 && game.rankUp) break;
        }
        SMART = false;
        step(1.6f); step(1.5f); game.confetti.clear(); game.spawnConfetti(28); step(0.75f);
        shot(7, "ranks", "Climb the ranks", "From Rookie to Grandmaster", 0xFF2DBE7E, 0xFF1F9A63, false);
        // 3. home with the Daily Puzzle and a streak
        int today = host.today();
        game.popup = Game.P_NONE; game.confetti.clear();
        game.streak = 12; game.bestStreak = 12; game.lastDailyDay = today - 1;
        game.rewardDay = 4; game.lastRewardDay = today - 1;
        game.screen = Game.HOME; game.screenAge = 3; game.displayCoins = game.coins; step(0.2f);
        shot(3, "daily", "A new puzzle every day", "Daily Puzzle, streaks and bonus coins, offline", 0xFFF26B4F, 0xFFD9503B, false);
        // 6. reward calendar
        game.popup = Game.P_REWARDS; game.popupAge = 1; step(0.1f);
        shot(6, "rewards", "Daily rewards", "Come back every day for bigger gifts", 0xFFFFB938, 0xFFF29A12, false);
    }

    static void feature() throws IOException {
        // a mid-game frame for the phone
        setSize(360, 740, 2f); IT = 24 * DP; IB = 10 * DP;
        fresh(); game.soundOn = false; game.helpSeen = true; game.coins = 840;
        game.startLevel(levelWithPicture(12)); draw(); playUntil(0.4f);
        game.toastT = 0;
        BufferedImage app = draw().img;
        BufferedImage out = new BufferedImage(1024, 500, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = nice(out);
        g.setPaint(new GradientPaint(0, 0, new Color(0x4B80EE), 1024, 500, new Color(0x2850B8)));
        g.fillRect(0, 0, 1024, 500);
        Random r = new Random(3);
        for (int k = 0; k < 10; k++) { int s = 40 + r.nextInt(30), x = 480 + r.nextInt(520), y = r.nextInt(460); g.setColor(new Color(255, 255, 255, 30)); g.fill(new RoundRectangle2D.Float(x, y, s, s, 12, 12)); }
        // phone, tilted
        int pw = 250, ph = Math.round(app.getHeight() * 250f / app.getWidth());
        AffineTransform old = g.getTransform();
        g.rotate(Math.toRadians(-6), 790, 260);
        int px = 790 - pw / 2, py = 40;
        g.setColor(new Color(0, 0, 0, 70)); g.fill(new RoundRectangle2D.Float(px + 8, py + 14, pw + 16, ph + 16, 48, 48));
        g.setColor(new Color(0x1F2A44)); g.fill(new RoundRectangle2D.Float(px - 8, py - 8, pw + 16, ph + 16, 48, 48));
        g.setClip(new RoundRectangle2D.Float(px, py, pw, ph, 40, 40));
        g.drawImage(app, px, py, pw, ph, null);
        g.setClip(null);
        g.setTransform(old);
        // title as letter tiles
        String t1 = "CROSSWORD", t2 = "RIVALS";
        int s = 52, gap = 6, x0 = 56, y1 = 96;
        for (int row = 0; row < 2; row++) {
            String w = row == 0 ? t1 : t2;
            for (int k = 0; k < w.length(); k++) {
                int x = x0 + k * (s + gap), y = y1 + row * (s + gap + 4);
                g.setColor(new Color(0xD9A41A)); g.fill(new RoundRectangle2D.Float(x, y + 6, s, s, 16, 16));
                g.setColor(new Color(0xFFF4D6)); g.fill(new RoundRectangle2D.Float(x, y, s, s, 16, 16));
                centered(g, "" + w.charAt(k), x + s / 2f, y + s * 0.73f, f(true, s * 0.62f), new Color(0x1F2A44), s);
            }
        }
        g.setFont(f(true, 38)); g.setColor(Color.WHITE); g.drawString("Crossword battles", 58, 316);
        g.setFont(f(false, 27)); g.setColor(new Color(255, 255, 255, 230)); g.drawString("Play crosswords against friendly rivals", 58, 362);
        g.drawString("400 levels · Daily Puzzle · offline", 58, 400);
        ImageIO.write(out, "png", new File(KIT, "feature-graphic-1024x500.png"));
    }

    static void trailer() throws IOException {
        setSize(360, 640, 2f);
        IT = 12 * DP; IB = 8 * DP;
        TimedHost h = new TimedHost(); host = h; game = new Game(h); h.game = game;
        game.helpSeen = true; game.coins = 320;
        int fps = 30;
        String[] caps = {"Crossword battles!", "Place letters on your turn", "Rivals play too", "Finish words for big points", ""};
        float[] segStart = {0, 1.8f, 6.2f, 9.6f, 13};
        int lvl = levelWithPicture(16);
        float nextAct = 0;
        for (int frame = 0; frame < 15 * fps; frame++) {
            float t = frame / (float) fps;
            h.clock = t;
            int seg = 0; for (int k = 0; k < segStart.length; k++) if (t >= segStart[k]) seg = k;
            if (frame == (int) (1.8f * fps)) { game.startLevel(lvl); draw(); }
            if (game.screen == Game.PLAY && !game.over && t >= nextAct && seg >= 1 && seg <= 3) {
                if (game.myTurn()) {
                    if (game.selTile >= 0) {
                        int tile = game.selTile;
                        int cell = -1;
                        for (int i = 0; i < game.board.length; i++) if (game.lv.isLetter(i) && game.board[i] == 0 && game.lv.grid[i] == game.players.get(0).rack.get(tile)) { cell = i; break; }
                        if (cell >= 0) tapCell(cell); else tapR("done");
                        nextAct = t + 0.45f;
                    } else {
                        int[] m = smartMove();
                        if (m == null || (seg == 1 && t > 5.6f)) { tapR("done"); nextAct = t + 0.3f; }
                        else { tapTile(m[0]); nextAct = t + 0.35f; }
                    }
                }
            }
            if (frame == (int) (11.6f * fps) && !game.over) {          // fast-forward to the finish
                for (int g2 = 0; g2 < 4000 && !game.over; g2++) { if (game.myTurn()) { int[] m = goodMove(); if (m != null) game.place(0, m[0], m[1]); else game.myEndTurn(); } else { host.now += 33; game.update(0.6f); } }
                game.overT = 1.3f;
            }
            h.now += 33;
            game.update(1f / fps);
            if (frame == 0) { game.screenAge = 0; }
            BufferedImage im = seg == 4 ? endCard(t - 13) : draw().img;
            Graphics2D g = nice(im);
            if (seg < 4) {
                String c = caps[seg];
                float aa = Math.min(1, (t - segStart[seg]) / 0.3f);
                int y = seg == 0 ? (int) (IT + 150 * DP) : (int) (IT + 38 * DP);
                Font ff = f(true, 21 * DP);
                float tw = Math.min((float) ff.getStringBounds(c, g.getFontRenderContext()).getWidth(), im.getWidth() * 0.86f);
                g.setColor(new Color(31, 42, 68, (int) (235 * aa)));
                g.fill(new RoundRectangle2D.Float(im.getWidth() / 2f - tw / 2 - 18 * DP, y - 30 * DP, tw + 36 * DP, 42 * DP, 21 * DP, 21 * DP));
                centered(g, c, im.getWidth() / 2f, y, ff, new Color(255, 201, 60, (int) (255 * aa)), im.getWidth() * 0.86f);
            }
            ImageIO.write(im, "png", new File(KIT, String.format("trailer/frames/f%04d.png", frame)));
        }
        int rate = Synth.RATE, n = rate * 15;
        float[] mix = new float[n];
        for (int i = 0; i < n; i++) { double tt = i / (double) rate; double env = Math.min(1, tt / 1.0) * Math.min(1, (15 - tt) / 1.0);
            double[] chord = (int) (tt / 2) % 2 == 0 ? new double[]{261.63, 329.63, 392.0} : new double[]{293.66, 349.23, 440.0};
            double v = 0; for (double f0 : chord) v += Math.sin(2 * Math.PI * f0 * tt) * 0.035;
            mix[i] += (float) (v * env * (0.75 + 0.25 * Math.sin(2 * Math.PI * 0.5 * tt))); }
        float[][] cache = new float[Synth.COUNT][];
        h.events.add(new float[]{13.1f, Synth.WIN});
        float last = -1;
        for (float[] e : h.events) {
            int id = (int) e[1];
            if (id == Synth.CLICK) continue;
            if (e[0] - last < 0.05f) continue; last = e[0];
            if (cache[id] == null) cache[id] = Synth.samples(id);
            int at = (int) (e[0] * rate);
            for (int i = 0; i < cache[id].length && at + i < n; i++) mix[at + i] += cache[id][i] * 0.8f;
        }
        float peak = 0; for (float v : mix) peak = Math.max(peak, Math.abs(v));
        byte[] wav = new byte[44 + n * 2];
        System.arraycopy(Synth.wav(Synth.CLICK), 0, wav, 0, 44);
        le32(wav, 4, 36 + n * 2); le32(wav, 40, n * 2);
        for (int i = 0; i < n; i++) { int v = (int) (mix[i] / Math.max(1, peak / 0.9f) * 32000); wav[44 + i * 2] = (byte) v; wav[45 + i * 2] = (byte) (v >> 8); }
        try (FileOutputStream o = new FileOutputStream(KIT + "/trailer/sound.wav")) { o.write(wav); }
    }

    static void le32(byte[] b, int at, int v) { for (int k = 0; k < 4; k++) b[at + k] = (byte) (v >> (8 * k)); }

    static BufferedImage endCard(float t) throws IOException {
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = nice(out);
        g.setPaint(new GradientPaint(0, 0, new Color(0x4B80EE), 0, H, new Color(0x2850B8)));
        g.fillRect(0, 0, W, H);
        float a = Math.min(1, t / 0.4f);
        BufferedImage icon = ImageIO.read(new File(KIT, "icon_full.png"));
        int s = (int) (W * 0.42f * (0.85f + 0.15f * a));
        g.setClip(new RoundRectangle2D.Float(W / 2f - s / 2f, H * 0.3f - s / 2f, s, s, s * 0.22f, s * 0.22f));
        g.drawImage(icon, W / 2 - s / 2, (int) (H * 0.3f) - s / 2, s, s, null);
        g.setClip(null);
        centered(g, "Crossword Rivals", W / 2f, H * 0.56f, f(true, 38 * DP), Color.WHITE, W * 0.9f);
        centered(g, "Word Battles", W / 2f, H * 0.62f, f(true, 19 * DP), new Color(0xFFC93C), W * 0.9f);
        centered(g, "400 levels · Daily Puzzle · Offline", W / 2f, H * 0.68f, f(false, 14 * DP), new Color(255, 255, 255, 230), W * 0.9f);
        g.setColor(new Color(255, 201, 60, (int) (255 * a)));
        g.fill(new RoundRectangle2D.Float(W * 0.2f, H * 0.76f, W * 0.6f, 52 * DP, 26 * DP, 26 * DP));
        centered(g, "Free on Google Play", W / 2f, H * 0.76f + 34 * DP, f(true, 19 * DP), new Color(0x1F2A44), W * 0.55f);
        return out;
    }
}
