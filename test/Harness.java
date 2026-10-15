package com.wordrivals.game;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/** Desktop test: checks every crossword, plays matches by taps and drags with a pretend phone, renders the screens. */
public class Harness {
    static Font bold, regular;
    static int fails = 0, checks = 0;
    static Map<String, Integer> seen = new HashMap<>();
    static void check(boolean ok, String msg) { checks++; if (!ok) { fails++; String k = msg.replaceAll("\\d", ""); if (seen.merge(k, 1, Integer::sum) <= 3) System.out.println("FAIL: " + msg); } }
    static String ASSETS, PICS;

    static class Host implements Game.Host {
        Map<String, String> prefs = new HashMap<>();
        long now = 1_791_800_000_000L;
        List<Integer> sounds = new ArrayList<>(); List<String> shared = new ArrayList<>();
        int haptics, buys, videos, restores, interstitials, privacyShown; String lastBuy;
        boolean video = true, inter = true, privacy = false; Game game;
        public String get(String k) { return prefs.get(k); }
        public void put(String k, String v) { prefs.put(k, v); }
        public long now() { return now; }
        public int today() { return (int) (now / 86400000L); }
        public void sound(int id) { sounds.add(id); }
        public void haptic(boolean s) { haptics++; }
        public String asset(String name) { try { return new String(Files.readAllBytes(Paths.get(ASSETS, name)), "UTF-8"); } catch (IOException e) { return null; } }
        public void share(String t) { shared.add(t); }
        public String price(String id) { return id.equals(Game.NOADS_ID) ? "$2.99" : "$1.99"; }
        public void buy(String id) { buys++; lastBuy = id; }
        public void restore() { restores++; }
        public boolean videoReady() { return video; }
        public void showVideo(int r) { videos++; game.onVideoReward(r); }
        public boolean interstitialReady() { return inter; }
        public void showInterstitial() { interstitials++; }
        public boolean privacyOptionsRequired() { return privacy; }
        public void showPrivacyOptions() { privacyShown++; }
        public String storeNote() { return null; }
        int reviews; Boolean reminder;
        public void requestReview() { reviews++; }
        public void setReminder(boolean on) { reminder = on; }
    }

    static Map<String, BufferedImage> picCache = new HashMap<>();
    static Set<String> missingPics = new TreeSet<>();
    static float minClueText = 99; static Map<String, Float> minClueBy = new TreeMap<>();

    static class P implements Game.Painter {
        BufferedImage img; Graphics2D g; int W, H; List<String> problems = new ArrayList<>(); Set<Character> missing = new TreeSet<>();
        Deque<Shape> clips = new ArrayDeque<>();
        P(int w, int h) { W = w; H = h; img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB); g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON); }
        Color c(int argb) { return new Color(argb, true); }
        public void roundRect(float l, float t, float r, float b, float rad, int color) { g.setColor(c(color)); if (rad <= 0) g.fill(new Rectangle2D.Float(l, t, r - l, b - t)); else g.fill(new RoundRectangle2D.Float(l, t, r - l, b - t, rad * 2, rad * 2)); }
        public void circle(float cx, float cy, float r, int color) { g.setColor(c(color)); g.fill(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r)); }
        public void ring(float cx, float cy, float r, float width, int color) { g.setColor(c(color)); g.setStroke(new BasicStroke(Math.max(0.01f, width))); g.draw(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r)); }
        Path2D path(float[] pts, int n) { Path2D.Float p = new Path2D.Float(); p.moveTo(pts[0], pts[1]); for (int i = 1; i < n; i++) p.lineTo(pts[i * 2], pts[i * 2 + 1]); return p; }
        public void poly(float[] pts, int n, int color) { Path2D p = path(pts, n); p.closePath(); g.setColor(c(color)); g.fill(p); }
        public void polyline(float[] pts, int n, float width, int color) { g.setColor(c(color)); g.setStroke(new BasicStroke(Math.max(0.1f, width), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.draw(path(pts, n)); }
        void drawText(String s, float x, float y, float size, int color, boolean b, int align, boolean small) {
            Font f = (b ? bold : regular).deriveFont(size);
            StringBuilder keep = new StringBuilder();
            for (int i = 0; i < s.length(); ) { int cp = s.codePointAt(i); i += Character.charCount(cp); if (!f.canDisplay(cp)) missing.add((char) cp); else keep.appendCodePoint(cp); }
            s = keep.toString().replaceAll("\\s+$", "");
            float tw = textWidth(s, size, b), l = align == 1 ? x : align == 2 ? x - tw : x - tw / 2f;
            Shape clip = g.getClip();
            boolean visible = (clip == null || clip.intersects(l, y - size, Math.max(1, tw), size)) && y > 0 && y - size < H && ((color >>> 24) > 40);
            if (visible && (l < -0.5f || l + tw > W + 0.5f)) problems.add("OFF: " + s);
            if (visible && !small && size < 10.5f * DP && s.trim().length() > 0 && !s.matches("[+\\d]+")) problems.add("TINY " + String.format("%.1f", size / DP) + ": " + s);
            if (visible && small) { minClueText = Math.min(minClueText, size / DP); minClueBy.merge((int) (W / DP) + "x" + (int) (H / DP), size / DP, Math::min); }
            g.setFont(f); g.setColor(c(color)); g.drawString(s, l, y);
        }
        public void text(String s, float x, float y, float size, int color, boolean b, int align) { drawText(s, x, y, size, color, b, align, false); }
        public void smallText(String s, float x, float y, float size, int color, boolean b, int align) { drawText(s, x, y, size, color, b, align, true); }
        public float textWidth(String s, float size, boolean b) { return (float) (b ? bold : regular).deriveFont(size).getStringBounds(s, g.getFontRenderContext()).getWidth(); }
        public void clip(float l, float t, float r, float b) {
            Shape cur = g.getClip();
            clips.push(cur == null ? new Rectangle2D.Float(-1e5f, -1e5f, 2e5f, 2e5f) : cur);
            Area a = new Area(clips.peek()); a.intersect(new Area(new Rectangle2D.Float(l, t, r - l, b - t)));
            g.setClip(a);
        }
        public void unclip() { if (clips.isEmpty()) { problems.add("UNBALANCED unclip"); g.setClip(null); return; } g.setClip(clips.pop()); }
        public void gradient(float l, float t, float r, float b, int top, int bottom) { Paint o = g.getPaint(); g.setPaint(new GradientPaint(0, t, c(top), 0, b, c(bottom))); g.fill(new Rectangle2D.Float(l, t, r - l, b - t)); g.setPaint(o); }
        public void glow(float cx, float cy, float r, int color) { if (r <= 0) return; Paint o = g.getPaint(); g.setPaint(new RadialGradientPaint(cx, cy, r, new float[]{0f, 1f}, new Color[]{c(color), c(color & 0x00FFFFFF)})); g.fill(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r)); g.setPaint(o); }
        public void image(String name, float l, float t, float r, float b, int alpha) {
            BufferedImage im = picCache.computeIfAbsent(name, k -> { try { return ImageIO.read(new File(PICS, k + ".png")); } catch (IOException e) { return null; } });
            if (im == null) { missingPics.add(name); return; }
            Composite old = g.getComposite();
            if (alpha < 255) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha / 255f));
            g.drawImage(im, Math.round(l), Math.round(t), Math.round(r - l), Math.round(b - t), null);
            g.setComposite(old);
        }
    }

    static String OUT; static int W = 1080, H = 2340; static float DP = 3f, IT = 72, IB = 48;
    static Host host; static Game game;
    static Set<String> allProblems = new TreeSet<>(); static Set<Character> allMissing = new TreeSet<>();
    static P draw() {
        P p = new P(W, H);
        game.draw(p, W, H, DP, (int) IT, (int) IB);
        if (!p.clips.isEmpty()) p.problems.add("UNBALANCED clip x" + p.clips.size());
        for (String s : p.problems) allProblems.add((int) (W / DP) + "x" + (int) (H / DP) + " screen " + game.screen + " popup " + game.popup + ": " + s);
        allMissing.addAll(p.missing);
        return p;
    }
    static void frame(String n) throws IOException { if (OUT != null) ImageIO.write(draw().img, "png", new File(OUT, n + ".png")); }
    static void step(float secs) { if (game.w == 0) draw(); for (float t = 0; t < secs; t += 0.033f) { host.now += 33; game.update(0.033f); } }
    static void tapAt(float x, float y) { game.down(x, y); game.up(x, y); }
    static void tapR(String name) {
        if (game.popup != Game.P_NONE) step(0.35f);
        draw();
        float[] r = game.rect(name);
        check(r != null && r[2] > r[0], "button exists: " + name + " on screen " + game.screen + " popup " + game.popup);
        if (r != null) tapAt((r[0] + r[2]) / 2, (r[1] + r[3]) / 2);
    }
    static void tapCell(int i) { draw(); float[] p = game.cellCenter(i); tapAt(p[0], p[1]); }
    static void tapTile(int t) { draw(); float[] p = game.tileCenter(t); tapAt(p[0], p[1]); }
    static void dragTile(int t, int cell) {
        draw();
        float[] a = game.tileCenter(t), b = game.cellCenter(cell);
        float off = game.rackSizeForTest() * 0.35f;
        game.down(a[0], a[1]);
        for (int k = 1; k <= 8; k++) { game.move(a[0] + (b[0] - a[0]) * k / 8, a[1] + (b[1] + off - a[1]) * k / 8); if (k == 4) draw(); }
        game.up(b[0], b[1] + off);
    }
    static boolean BANNER = true;
    /** Phone size; the bottom inset includes the banner advert strip (large adaptive on tall phones, 320x50 on short ones). */
    static void setSize(int wdp, int hdp, float dp) { DP = dp; W = Math.round(wdp * dp); H = Math.round(hdp * dp); IT = 24 * dp; IB = (16 + (BANNER && hdp - 40 >= 680 ? (hdp >= 720 ? 66 : 50) : 0)) * dp; }
    static Game fresh() { host = new Host(); game = new Game(host); host.game = game; return game; }
    static Game reopen() { Host h = host; game = new Game(h); h.game = game; return game; }

    /** A tile of mine and an empty square it fits, or null. */
    static int[] goodMove() {
        List<Character> rack = game.players.get(0).rack;
        for (int t = 0; t < rack.size(); t++) for (int i = 0; i < game.board.length; i++)
            if (game.lv.isLetter(i) && game.board[i] == 0 && game.lv.grid[i] == rack.get(t)) return new int[]{t, i};
        return null;
    }
    static boolean SMART = false;
    static double ERR = 0; static Random human = new Random(5);
    /** Careful player: finish words first, avoid leaving a word one letter short for a rival, else pass. */
    static int[] smartMove() {
        List<Character> rack = game.players.get(0).rack;
        int[] safe = null, any = null;
        for (int t = 0; t < rack.size(); t++) for (int i = 0; i < game.board.length; i++) {
            if (!game.lv.isLetter(i) || game.board[i] != 0 || game.lv.grid[i] != rack.get(t)) continue;
            any = new int[]{t, i};
            game.board[i] = rack.get(t);
            boolean fin = false, gift = false;
            for (Level.Word wd : game.lv.wordsAt(i)) {
                int empty = 0; for (int k : wd.cells) if (game.board[k] == 0) empty++;
                if (empty == 0) fin = true; else if (empty == 1) gift = true;
            }
            game.board[i] = 0;
            if (fin) return new int[]{t, i};
            if (!gift && safe == null) safe = new int[]{t, i};
        }
        return safe;
    }
    static int[] badMove() {
        List<Character> rack = game.players.get(0).rack;
        for (int t = 0; t < rack.size(); t++) for (int i = 0; i < game.board.length; i++)
            if (game.lv.isLetter(i) && game.board[i] == 0 && game.lv.grid[i] != rack.get(t)) return new int[]{t, i};
        return null;
    }

    /** Plays the match to the end: I place every tile that fits (taps, every third by drag), bots play themselves. */
    static boolean playOut(int maxSteps) {
        int n = 0;
        while (!game.over && n++ < maxSteps) {
            if (game.popup != Game.P_NONE && game.popup != Game.P_RESULT) { game.popup = Game.P_NONE; }
            if (game.myTurn()) {
                if (ERR > 0 && human.nextDouble() < ERR) { int[] b = badMove(); if (b != null) { tapTile(b[0]); tapCell(b[1]); check(!game.myTurn() || game.over, "mistake ends the turn"); continue; } }
                int[] m = SMART ? smartMove() : goodMove();
                if (m != null) {
                    int before = game.players.get(0).score;
                    if (n % 3 == 0) dragTile(m[0], m[1]); else { tapTile(m[0]); tapCell(m[1]); }
                    check(game.players.get(0).score > before, "a right square scores");
                } else tapR("done");
            } else step(0.4f);
        }
        return game.over;
    }

    public static void main(String[] a) throws Exception {
        ASSETS = a[0]; PICS = a[1];
        bold = Font.createFont(Font.TRUETYPE_FONT, new File(ASSETS, "Nunito-800.ttf"));
        regular = Font.createFont(Font.TRUETYPE_FONT, new File(ASSETS, "Nunito-600.ttf"));
        OUT = a.length > 2 ? a[2] : null;
        if (OUT != null) new File(OUT).mkdirs();
        content();
        int[][] sizes = {{393, 852}, {360, 780}, {412, 915}, {360, 640}, {600, 960}};
        float[] dens = {3f, 3f, 2.625f, 2f, 2f};
        for (int s = 0; s < sizes.length; s++) {
            setSize(sizes[s][0], sizes[s][1], dens[s]);
            flows(sizes[s][0] + "x" + sizes[s][1], s == 0 || s == 3);
        }
        setSize(393, 852, 3f);
        manyMatches();
        System.out.println("missing glyphs: " + allMissing + "  missing pictures: " + missingPics);
        System.out.println(String.format("smallest clue text %.1fdp ", minClueText) + minClueBy);
        int shown = 0;
        for (String p : allProblems) if (shown++ < 40) System.out.println("text: " + p);
        System.out.println(checks + " checks, " + fails + " failed, text issues " + allProblems.size());
    }

    static void content() {
        Game g = fresh();
        check(g.levelCount() == 400, "levels: " + g.levelCount());
        check(g.dailyLines.size() == 366, "a year of daily puzzles: " + g.dailyLines.size());
        Set<String> dailyGrids = new HashSet<>(g.dailyLines), levelGrids = new HashSet<>(g.levelLines);
        dailyGrids.retainAll(levelGrids);
        check(dailyGrids.isEmpty(), "daily puzzles differ from the levels");
        Set<String> pics = new HashSet<>();
        for (String f : new File(ASSETS, "pic").list()) pics.add(f.replace(".webp", ""));
        int words = 0, picWords = 0; Set<String> distinct = new HashSet<>();
        List<String> all = new ArrayList<>(g.levelLines); all.addAll(g.dailyLines);
        for (int li = 0; li < all.size(); li++) {
            Level lv = Level.parse(all.get(li));
            check(lv.cols >= 6 && lv.rows >= 7 && lv.cols <= 7 && lv.rows <= 9, "size " + li);
            check(lv.words.size() >= 4, "level " + li + " has enough words: " + lv.words.size());
            boolean[] covered = new boolean[lv.grid.length];
            Set<String> inLevel = new HashSet<>();
            for (Level.Word wd : lv.words) {
                words++; distinct.add(wd.text);
                check(inLevel.add(wd.text), "word once per level: " + wd.text + " in " + li);
                check(wd.text.matches("[A-Z]{3,}"), "word letters " + wd.text);
                check(wd.clue.length() > 1 && wd.clue.length() <= 24, "clue length: " + wd.clue);
                check(wd.clueCell >= 0 && wd.clueCell < lv.grid.length && lv.grid[wd.clueCell] == '#', "clue cell in front of " + wd.text + " level " + li);
                for (int k = 0; k < wd.cells.length; k++) {
                    int i = wd.cells[k];
                    check(i >= 0 && i < lv.grid.length && lv.grid[i] == wd.text.charAt(k), "letter " + k + " of " + wd.text + " level " + li);
                    covered[i] = true;
                }
                // the square after the word must not be a letter (else the word would read longer)
                int er = wd.r + (wd.across ? 0 : wd.text.length()), ec = wd.c + (wd.across ? wd.text.length() : 0);
                if (er < lv.rows && ec < lv.cols) check(!lv.isLetter(er * lv.cols + ec), "word ends cleanly: " + wd.text + " level " + li);
                if (wd.picture != null) { picWords++; check(pics.contains(wd.picture), "picture exists " + wd.picture); }
                // a direction is used once per clue cell
                for (Level.Word o : lv.cluesIn(wd.clueCell)) if (o != wd) check(o.across != wd.across, "two clues in one cell point different ways, level " + li);
            }
            for (int i = 0; i < lv.grid.length; i++) if (lv.isLetter(i)) check(covered[i], "every letter belongs to a word, level " + li);
            // every run of 2+ letters in a row or column is a word
            for (int r = 0; r < lv.rows; r++) for (int c = 0; c < lv.cols; c++) {
                int i = r * lv.cols + c;
                if (!lv.isLetter(i)) continue;
                if ((c == 0 || !lv.isLetter(i - 1)) && c + 1 < lv.cols && lv.isLetter(i + 1)) {
                    boolean found = false; for (Level.Word wd : lv.words) if (wd.across && wd.cells[0] == i) found = true;
                    check(found, "across run starting at " + r + "," + c + " is a word, level " + li);
                }
                if ((r == 0 || !lv.isLetter(i - lv.cols)) && r + 1 < lv.rows && lv.isLetter(i + lv.cols)) {
                    boolean found = false; for (Level.Word wd : lv.words) if (!wd.across && wd.cells[0] == i) found = true;
                    check(found, "down run starting at " + r + "," + c + " is a word, level " + li);
                }
            }
            if (li < g.levelCount() && (li < 120 || li % 9 == 0)) {
                g.helpSeen = true;
                g.startLevel(li);
                for (Level.Word wd : g.lv.words) check(!g.complete(wd), "no word complete at the start, level " + li);
                for (Game.Player p : g.players) {
                    check(p.rack.size() == Math.min(Game.RACK, g.emptyCells()), "full rack at the start, level " + li);
                    List<Character> need = new ArrayList<>(); for (int i = 0; i < g.board.length; i++) if (g.lv.isLetter(i) && g.board[i] == 0) need.add(g.lv.grid[i]);
                    for (char ch : p.rack) check(need.remove((Character) ch), "every tile fits a square, level " + li);
                }
                check(g.players.size() == (li < 3 ? 2 : li < 15 ? 3 : 4), "rivals for level " + li);
            }
        }
        // calendar maths for the Daily Puzzle card
        check(Game.dateLabel(0).equals("Thu, Jan 1"), "date 0: " + Game.dateLabel(0));
        check(Game.dateLabel(20739).equals("Tue, Oct 13"), "date 2026-10-13: " + Game.dateLabel(20739));
        check(Game.dateLabel(20513).equals("Sun, Mar 1"), "date 2026-03-01: " + Game.dateLabel(20513));
        check(Game.dateLabel(21243).equals("Tue, Feb 29"), "leap day 2028: " + Game.dateLabel(21243));
        // ranks
        check(Game.rankIndex(0) == 0 && Game.rankIndex(149) == 0 && Game.rankIndex(150) == 1 && Game.rankIndex(99999) == Game.RANKS.length - 1, "rank thresholds");
        check(Game.rankProgress(275) > 0.49f && Game.rankProgress(275) < 0.51f, "rank progress " + Game.rankProgress(275));
        check(Game.RANKS.length == Game.RANK_ICONS.length && Game.RANKS.length == Game.RANK_XP.length, "rank tables line up");
        for (String ic : Game.RANK_ICONS) check(pics.contains(ic), "rank icon exists " + ic);
        for (String ic : new String[]{"1f446", "1f525", "1f381", "1f514"}) check(pics.contains(ic), "ui icon exists " + ic);
        System.out.println("words placed " + words + ", distinct " + distinct.size() + ", picture clues " + picWords);
        for (int id = 0; id < Synth.COUNT; id++) { float pk = 0; for (float v : Synth.samples(id)) pk = Math.max(pk, Math.abs(v)); check(pk > 0.05f && pk <= 1, "sound " + id); }
    }

    static void flows(String tag, boolean shots) throws IOException {
        fresh();
        step(0.6f);
        check(game.screen == Game.HOME && game.coins == Game.START_COINS && game.hints == Game.START_HINTS, "starts at home with gifts");
        if (shots) frame(tag + "-01-home");
        tapR("play");
        check(game.screen == Game.PLAY && game.levelIdx == 0, "play opens level 1");
        check(game.popup == Game.P_HELP, "first game shows how to play");
        step(0.3f);
        if (shots) frame(tag + "-02-help");
        tapR("a");
        check(game.popup == Game.P_NONE && game.helpSeen, "help closes");
        step(0.4f);
        check(game.tutorialActive(), "first level shows the drag guide");
        step(0.9f);
        if (shots) frame(tag + "-03-play");
        // a right letter by taps
        int[] m = goodMove();
        check(m != null, "a first move exists");
        int score0 = game.players.get(0).score, rack0 = game.players.get(0).rack.size();
        tapTile(m[0]);
        check(game.selTile == m[0], "tap selects a tile");
        tapCell(m[1]);
        check(game.board[m[1]] != 0 && game.players.get(0).score > score0, "tap tile then square places it");
        check(game.tutorialDone && !game.tutorialActive(), "guide ends after the first letter");
        check(game.flyFrom.containsKey(m[1]) && game.cellFx.get(m[1]) < 0, "the tile flies to its square");
        step(0.3f);
        check(!game.flyFrom.containsKey(m[1]) && game.cellFx.get(m[1]) >= 0, "the tile lands");
        check(game.players.get(0).rack.size() == rack0 - 1 && game.turn == 0, "still my turn after a right letter");
        // a right letter by drag
        m = goodMove();
        if (m != null && game.myTurn()) {
            int s1 = game.players.get(0).score;
            dragTile(m[0], m[1]);
            check(game.board[m[1]] != 0 && game.players.get(0).score > s1, "drag a tile onto its square");
        }
        step(0.2f);
        if (shots) frame(tag + "-04-placed");
        // combo: the third right letter in a row scores a bonus
        if (game.myTurn() && game.comboRun == 2) {
            int[] m3 = goodMove();
            if (m3 != null) {
                int s3 = game.players.get(0).score;
                tapTile(m3[0]); tapCell(m3[1]);
                check(game.comboRun == 3 && game.players.get(0).score - s3 >= 2, "combo bonus on the third letter: +" + (game.players.get(0).score - s3));
            }
        }
        // tapping a clue cell selects that word
        Level.Word target = game.lv.words.get(game.lv.words.size() - 1);
        tapCell(target.clueCell);
        check(game.lv.words.get(game.selWord).clueCell == target.clueCell, "tap a clue square selects its clue");
        // a wrong square ends the turn
        if (game.myTurn()) {
            int[] bad = badMove();
            int before = game.players.get(0).score;
            tapTile(bad[0]); tapCell(bad[1]);
            check(game.turn != 0 && game.board[bad[1]] == 0 && game.players.get(0).score == before, "wrong square ends the turn without points");
            step(0.1f);
            if (shots) frame(tag + "-05-wrong");
        }
        // bots take their turns and hand back
        int botScore = 0;
        for (int k = 0; k < 400 && !game.myTurn() && !game.over; k++) step(0.1f);
        for (int k = 1; k < game.players.size(); k++) botScore += game.players.get(k).score;
        check(game.myTurn(), "turn comes back to me");
        check(game.players.get(0).rack.size() == Math.min(Game.RACK, game.emptyCells()), "rack topped up for my turn");
        check(botScore > 0, "rivals scored: " + botScore);
        if (shots) { step(0.05f); frame(tag + "-06-myturn"); }
        // hint
        int h0 = game.hints;
        tapR("hint");
        check(game.hints == h0 - 1 && game.hintCell >= 0 && game.lv.grid[game.hintCell] == game.players.get(0).rack.get(game.hintTile), "hint points to a right square");
        if (shots) { step(0.1f); frame(tag + "-07-hint"); }
        int hc = game.hintCell, s2 = game.players.get(0).score;
        tapCell(hc);                     // hint also selected the tile
        check(game.board[hc] != 0 && game.players.get(0).score > s2, "hinted letter placed with one tap");
        // out of hints opens the shop
        game.hints = 0;
        if (game.myTurn()) {
            tapR("hint");
            check(game.popup == Game.P_SHOP, "no hints opens the shop");
            step(0.3f);
            if (shots) frame(tag + "-08-shop");
            int c0 = game.coins;
            tapR("b");
            check(game.hints == 1 && game.coins == c0 - Game.HINT_COST, "buy a hint with coins");
            tapR("c");
            check(host.lastBuy.equals(Game.HINTS_ID), "hint pack goes to the store");
            game.onGranted(Game.HINTS_ID);
            check(game.hints == 1 + Game.PACK_HINTS && game.popup == Game.P_NONE, "hint pack granted");
            game.coins = 10;
            tapR("hint");               // uses a hint now (hints > 0)
            game.hints = 0; tapR("hint");
            if (game.popup == Game.P_SHOP) {
                tapR("b");
                check(game.hints == 0 && game.coins == 10, "not enough coins keeps everything");
                tapR("a");
                check(game.hints == 1 && game.popup == Game.P_NONE, "video gives a free hint");
            }
            game.coins = 300;
        }
        // pass button
        if (game.myTurn()) {
            tapR("done");
            check(!game.myTurn(), "done passes the turn");
        }
        // play to the end
        boolean ended = playOut(3000);
        check(ended, "match finishes");
        check(game.emptyCells() == 0, "board full at the end");
        int coinsBefore = game.coins;
        step(1.6f);
        check(game.popup == Game.P_RESULT, "result shows");
        step(0.4f);
        if (shots) frame(tag + "-09-result");
        check(game.levelIdx == 1 && game.games == 1, "level advanced");
        check(game.xp > 0 && game.xpGained == game.xp, "XP earned: " + game.xp);
        step(1.2f);
        if (shots) frame(tag + "-09b-result-xp");
        int reward = game.rewardCoins;
        check(reward >= 10, "coins for the match: " + reward);
        tapR("b");
        check(game.doubled && game.coins == coinsBefore + reward, "video doubles the coins");
        tapR("a");
        check(game.screen == Game.PLAY && game.levelIdx == 1 && host.interstitials == 0, "next level, no advert this early");
        // pause and leave
        step(0.3f);
        game.back();
        check(game.popup == Game.P_PAUSE, "back asks before leaving");
        step(0.3f);
        if (shots) frame(tag + "-10-pause");
        tapR("b");
        check(game.screen == Game.HOME, "leave goes home");
        check(game.popup == Game.P_REWARDS && game.rewardReady() && game.rewardSlot() == 0, "daily reward calendar opens");
        step(0.4f);
        if (shots) frame(tag + "-10b-rewards");
        int cr0 = game.coins;
        tapR("a");
        check(game.coins == cr0 + Game.REWARDS[0][0] && !game.rewardReady() && game.popup == Game.P_NONE, "day 1 reward claimed");
        tapR("gift");
        check(game.popup == Game.P_NONE, "no second gift today");
        step(2.5f);
        if (shots) frame(tag + "-10c-home");
        // settings
        tapR("gear");
        check(game.popup == Game.P_SETTINGS, "settings open");
        step(0.3f);
        if (shots) frame(tag + "-11-settings");
        boolean snd = game.soundOn;
        tapR("set0");
        check(game.soundOn != snd, "sound toggles");
        int sounds = host.sounds.size();
        game.down(1, 1); game.up(1, 1);
        tapR("set0");
        check(game.soundOn == snd, "sound back on");
        tapR("set2");
        check(game.reminderOn && Boolean.TRUE.equals(host.reminder), "reminder turns on");
        tapR("set2");
        check(!game.reminderOn && Boolean.FALSE.equals(host.reminder), "reminder turns off");
        tapR("set4");
        check(host.lastBuy.equals(Game.NOADS_ID), "remove ads goes to the store");
        tapR("set5");
        check(host.restores == 1, "restore asks the store");
        tapR("close");
        check(game.popup == Game.P_NONE, "settings close");
        // shop from home
        tapR("shop");
        check(game.popup == Game.P_SHOP, "shop from home");
        tapR("close");
        // saves
        int lvl = game.levelIdx, coins = game.coins, hints = game.hints, wins = game.wins;
        game.save();
        reopen();
        check(game.levelIdx == lvl && game.coins == coins && game.hints == hints && game.wins == wins && game.helpSeen, "progress saved");
        check(game.popup == Game.P_NONE, "no reward popup again the same day");
        // next day: day 2 of the calendar, doubled by a video
        host.now += 86400000L;
        reopen();
        check(game.popup == Game.P_REWARDS && game.rewardSlot() == 1, "day 2 reward offered on launch");
        tapR("b");
        check(game.coins == coins + 2 * Game.REWARDS[1][0] && game.popup == Game.P_NONE, "video doubles the daily reward");
        // skip two days: the calendar starts again
        host.now += 3 * 86400000L;
        reopen();
        check(game.rewardSlot() == 0, "missing a day restarts the calendar");
        tapR("a");
        // Daily Puzzle
        game.remindAsked = false; game.reminderOn = false;
        check(!game.dailyDone() && game.streakNow() == 0, "daily open, no streak yet");
        tapR("daily");
        check(game.screen == Game.PLAY && game.dailyMode && game.players.size() == 4, "daily puzzle starts against 3 rivals");
        String dailyGrid = new String(game.lv.grid);
        int lvlBefore = game.levelIdx;
        step(0.5f);
        if (shots) frame(tag + "-14-daily");
        check(playOut(5000), "daily finishes");
        step(1.6f);
        check(game.popup == Game.P_RESULT && game.streak == 1 && game.dailyDone() && game.levelIdx == lvlBefore, "daily done: streak 1, campaign level unchanged");
        step(1.4f);
        if (shots) frame(tag + "-15-daily-result");
        tapR("a");
        check(game.screen == Game.HOME && game.popup == Game.P_REMIND, "after the first daily we offer a reminder");
        step(0.3f);
        if (shots) frame(tag + "-16-remind");
        tapR("a");
        check(game.reminderOn && Boolean.TRUE.equals(host.reminder) && game.popup == Game.P_NONE, "reminder accepted");
        tapR("daily");
        check(game.screen == Game.HOME, "daily cannot be replayed the same day");
        host.now += 86400000L; reopen();
        if (game.popup == Game.P_REWARDS) tapR("a");
        check(game.streakNow() == 1 && !game.dailyDone(), "streak waits for today's puzzle");
        tapR("daily");
        check(!new String(game.lv.grid).equals(dailyGrid), "a new daily puzzle each day");
        playOut(5000); step(1.6f);
        check(game.streak == 2, "streak grows day after day");
        tapR("a");
        host.now += 2 * 86400000L; reopen();
        check(game.streakNow() == 0, "a missed day breaks the streak");
        if (game.popup == Game.P_REWARDS) tapR("a");
        // rank up
        game.xp = Game.RANK_XP[2] - 5; game.helpSeen = true;
        game.startLevel(game.levelIdx); playOut(5000);
        check(game.rankUp && Game.rankIndex(game.xp) >= 2, "rank up after crossing the threshold");
        step(1.6f); step(1.5f);
        if (shots) frame(tag + "-17-rankup");
        // a rating request after a win (once), instead of an advert
        game.wins = 5; game.reviewAsked = false;
        game.players.get(0).score = 999;
        int ads0 = host.interstitials, rv0 = host.reviews;
        tapR("a");
        check(host.reviews == rv0 + 1 && game.reviewAsked && host.interstitials == ads0, "rating asked once after a win");
        game.startLevel(game.levelIdx); playOut(5000); step(1.6f); game.players.get(0).score = 999; tapR("a");
        check(host.reviews == rv0 + 1, "rating never asked twice");
        // adverts: from level 5, at most every 2 games and 2 minutes
        game.levelIdx = 6; game.sinceAd = 0; game.lastAd = 0; game.reviewAsked = true;
        int ads = 0;
        for (int k = 0; k < 6; k++) {
            game.startLevel(game.levelIdx);
            check(playOut(4000), "match " + k + " finishes");
            step(1.6f);
            host.now += 30000;
            tapR("a");
            ads = host.interstitials;
        }
        check(ads >= 1 && ads <= 3, "adverts paced: " + ads);
        game.onOwned(Game.NOADS_ID, true);
        int adsBefore = host.interstitials;
        for (int k = 0; k < 3; k++) { game.startLevel(game.levelIdx); playOut(4000); step(1.6f); host.now += 200000; tapR("a"); }
        check(host.interstitials == adsBefore, "no adverts after buying No Ads");
        // a big late crossword
        game.startLevel(300);
        step(0.5f);
        if (shots) frame(tag + "-12-level301");
        // render while a bot is playing
        game.startLevel(20); game.myEndTurn(); step(1.2f);
        if (shots) frame(tag + "-13-botturn");
    }

    /** Plays many crosswords to the end to catch stalls. */
    static void manyMatches() {
        double[] errs = {0, 0.3};
        for (double e : errs) for (int mode = 0; mode < 2; mode++) { ERR = e; SMART = mode == 1; matches(0, 40, 2); matches(40, 400, 9); }
        SMART = false; ERR = 0;
    }
    static void matches(int from, int to, int stepBy) {
        fresh();
        game.helpSeen = true;
        int myWins = 0, played = 0;
        for (int li = from; li < to; li += stepBy) {
            game.startLevel(li);
            boolean done = playOut(5000);
            check(done, "match on level " + li + " finishes");
            int total = 0; for (Game.Player p : game.players) total += p.score;
            int hidden = 0; for (int i = 0; i < game.given.length; i++) if (game.lv.isLetter(i) && !game.given[i]) hidden++;
            check(total >= hidden, "points handed out on level " + li + ": " + total + " for " + hidden);
            if (game.rank(game.players.get(0)) == 1) myWins++;
            played++;
            step(1.6f);
            game.popup = Game.P_NONE;
        }
        System.out.println((SMART ? "careful" : "casual") + " player" + (ERR > 0 ? " with mistakes" : "") + ", levels " + (from + 1) + "-" + to + ": wins " + myWins + " of " + played);
    }
}
