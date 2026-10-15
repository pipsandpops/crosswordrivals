package com.wordrivals.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Crossword Rivals: a Scandinavian-style crossword (clues inside the grid) played in turns against friendly
 * computer players. Everyone holds up to 5 letter tiles; placing a tile in its right square scores a
 * point, completing a word scores its length. All drawing goes through Painter so the same code runs on
 * Android and in the desktop tests.
 */
final class Game {

    interface Painter {
        void roundRect(float l, float t, float r, float b, float rad, int color);
        void circle(float cx, float cy, float r, int color);
        void ring(float cx, float cy, float r, float width, int color);
        void poly(float[] pts, int n, int color);
        void polyline(float[] pts, int n, float width, int color);
        void text(String s, float x, float y, float size, int color, boolean bold, int align);
        /** Clue text inside grid cells (allowed to be smaller than normal text). */
        void smallText(String s, float x, float y, float size, int color, boolean bold, int align);
        float textWidth(String s, float size, boolean bold);
        void clip(float l, float t, float r, float b);
        void unclip();
        void gradient(float l, float t, float r, float b, int top, int bottom);
        void glow(float cx, float cy, float r, int color);
        /** Picture from assets/pic/<name>.webp. */
        void image(String name, float l, float t, float r, float b, int alpha);
    }

    interface Host {
        String get(String key);
        void put(String key, String value);
        long now();
        int today();
        void sound(int id);
        void haptic(boolean strong);
        String asset(String name);
        void share(String text);
        String price(String product);
        void buy(String product);
        void restore();
        boolean videoReady();
        void showVideo(int reward);
        boolean interstitialReady();
        void showInterstitial();
        boolean privacyOptionsRequired();
        void showPrivacyOptions();
        String storeNote();
        /** Google Play's in-app rating card (shown at most a few times; Google decides). */
        void requestReview();
        /** Turns the evening Daily Puzzle reminder on or off. */
        void setReminder(boolean on);
    }

    // ------------------------------------------------------------------ constants
    static final String NOADS_ID = "no_ads", HINTS_ID = "hints_25";
    static final int R_DOUBLE = 1, R_HINT = 2;
    static final int RACK = 5, START_COINS = 100, START_HINTS = 3, HINT_COST = 60, PACK_HINTS = 25, DAILY_COINS = 25;
    static final int HOME = 0, PLAY = 1;
    static final int P_NONE = 0, P_RESULT = 1, P_SETTINGS = 2, P_SHOP = 3, P_HELP = 4, P_PAUSE = 5, P_REWARDS = 6, P_REMIND = 7;
    static final int R_REWARD2 = 3;
    /** 7-day login calendar: {coins, hints}. Missing a day starts again at day 1. */
    static final int[][] REWARDS = {{25, 0}, {40, 0}, {0, 1}, {60, 0}, {80, 0}, {0, 2}, {150, 3}};
    static final String[] RANKS = {"Rookie", "Bronze", "Silver", "Gold", "Platinum", "Diamond", "Master", "Grandmaster"};
    static final String[] RANK_ICONS = {"1f331", "1f949", "1f948", "1f947", "1f3c6", "1f48e", "1f451", "1f31f"};
    static final int[] RANK_XP = {0, 150, 400, 800, 1400, 2300, 3600, 5500};
    static final float FLY = 0.22f;
    static final String[] DAYS = {"Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Wed"};
    static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    static final String[] BOT_NAMES = {"Maya", "Leo", "Aarav", "Sofia", "Kenji", "Zara", "Omar", "Lucas", "Priya", "Emma", "Diego", "Anya", "Noah", "Isla", "Ravi", "Mila"};
    static final String[] AVATARS = {"1f98a", "1f43c", "1f989", "1f981", "1f428", "1f405", "1f427", "1f407", "1f984"};
    static final String ME_AVATAR = "1f60e";

    // palette: fresh blue and sunny yellow
    // light, calm palette: soft grey-blue page, white cards, one blue accent
    static final int BG = 0xFFF6F8FC, BG2 = 0xFFEDF1F8, CARD = 0xFFFFFFFF, INK = 0xFF27324D, MUTED = 0xFF8C96AE, PAPER = 0xFFF1F4F9;
    static final int ACCENT = 0xFF4C7DF0, ACCENT_D = 0xFF3A67D4, ACCENT_SOFT = 0xFFE3ECFF, SHADOW = 0x14203060;
    static final int BOARD = 0xFFE8ECF3, CLUE = 0xFFDDE3EE, CLUE_INK = 0xFF3A4562, CLUE_DONE = 0xFFEEF2F9, CLUE_SEL = ACCENT;
    static final int CELL = 0xFFFFFFFF, CELL_SEL = 0xFFDCE7FF, TILE_SHADE = 0xFFD3DBE8;
    static final int YELLOW = 0xFFFFC93C, YELLOW_D = 0xFFE6A821, GREEN = 0xFF2EB67D, GREEN_D = 0xFF22986A, RED = 0xFFF0544F, WHITE = 0xFFFFFFFF;
    static final int CLUE_D = ACCENT_D;
    static final int[] PLAYER_COLOR = {0xFF2EB67D, 0xFFF26B4F, 0xFF9B6BEA, 0xFFEC5C9A};

    final Host host;
    final List<String> levelLines, dailyLines;

    // saved
    int levelIdx, coins, hints, wins, games, sinceAd, lastBonusDay = -1;
    int xp, streak, bestStreak, lastDailyDay = -1, rewardDay, lastRewardDay = -1;
    boolean noAds, soundOn = true, vibeOn = true, helpSeen, reviewAsked, reminderOn, remindAsked, tutorialDone;
    long lastAd;

    // match
    static final class Player {
        String name, avatar;
        int score, color;
        boolean bot;
        float skill;          // 0..1, how many letters a bot finds per turn and how rarely it errs
        final List<Character> rack = new ArrayList<Character>();
    }
    Level lv;
    char[] board;             // placed letters (0 = empty) for letter cells
    boolean[] given;          // letters shown from the start
    final List<Player> players = new ArrayList<Player>();
    int turn, placedThisTurn, selTile = -1, selWord = -1, hintTile = -1, hintCell = -1, rewardCoins;
    boolean over, doubled, dailyMode, rankUp;
    int comboRun, xpGained, rankBefore;
    float displayCoins;
    final Map<Integer, Float> wordFx = new HashMap<Integer, Float>();       // cell -> time of the word "wave"
    final Map<Integer, float[]> flyFrom = new HashMap<Integer, float[]>(); // cell -> where its tile flew from
    private float flyX = -1, flyY = -1;                                     // source for the next placement
    private final float[] sbX = new float[4], sbY = new float[4];          // scoreboard avatar centres
    final List<float[]> confetti = new ArrayList<float[]>();       // x, y, vx, vy, angle, spin, colour, size
    float botT, overT = -1;
    private Random rnd = new Random();
    final Map<Integer, Float> cellFx = new HashMap<Integer, Float>();     // cell -> time since placed
    final Map<Integer, Integer> cellOwner = new HashMap<Integer, Integer>();
    private final List<float[]> floaters = new ArrayList<float[]>();       // x, y, age, value, colour
    private final List<String> floaterText = new ArrayList<String>();
    private float wrongT, shakeTile = -1;
    private String banner;
    private float bannerT;

    // ui
    int screen = HOME, popup = P_NONE;
    float time, popupAge, screenAge, toastT;
    String toast;
    float w, h, dp;
    int insetTop, insetBottom;
    float gl, gt, cell;
    private final float[] rackX = new float[RACK], rackY = new float[RACK];
    private float rackSize;
    private int dragTile = -1;
    private float dragX, dragY, downX, downY;
    private boolean touching, moved;
    private final float[] q = new float[16];

    private final float[] bPlay = new float[4], bGear = new float[4], bShopHome = new float[4], bBack = new float[4], bHint = new float[4], bDone = new float[4];
    private final float[] bA = new float[4], bB = new float[4], bC = new float[4], bClose = new float[4], bHelp = new float[4];
    private final float[] bDaily = new float[4], bGift = new float[4];
    private final float[][] bSet = new float[8][4];
    private final float[][] allButtons = {bPlay, bGear, bShopHome, bBack, bHint, bDone, bA, bB, bC, bClose, bHelp, bDaily, bGift};
    private float[] pressed;
    private float homeToastY;

    Game(Host host) {
        this.host = host;
        levelLines = Level.lines(host.asset("levels.txt"));
        dailyLines = Level.lines(host.asset("daily.txt"));
        load();
        displayCoins = coins;
        if (games >= 1 && rewardReady()) openPopup(P_REWARDS);
    }

    // =====================================================================================
    // saving

    private int geti(String k, int d) {
        String v = host.get(k);
        if (v == null) return d;
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return d; }
    }

    private void load() {
        levelIdx = geti("level", 0);
        coins = geti("coins", START_COINS);
        hints = geti("hints", START_HINTS);
        wins = geti("wins", 0);
        games = geti("games", 0);
        sinceAd = geti("sinceAd", 0);
        lastBonusDay = geti("bonusDay", -1);
        noAds = geti("noads", 0) == 1;
        soundOn = geti("sound", 1) == 1;
        vibeOn = geti("vibe", 1) == 1;
        helpSeen = geti("help", 0) == 1;
        xp = geti("xp", 0);
        streak = geti("streak", 0);
        bestStreak = geti("bestStreak", 0);
        lastDailyDay = geti("dailyDay", -1);
        rewardDay = geti("rewardDay", 0);
        lastRewardDay = geti("rewardLast", -1);
        reviewAsked = geti("reviewAsked", 0) == 1;
        reminderOn = geti("reminder", 0) == 1;
        remindAsked = geti("remindAsked", 0) == 1;
        tutorialDone = geti("tutorial", 0) == 1;
        String la = host.get("lastAd");
        lastAd = la == null ? 0 : Long.parseLong(la);
    }

    void save() {
        host.put("level", "" + levelIdx);
        host.put("coins", "" + coins);
        host.put("hints", "" + hints);
        host.put("wins", "" + wins);
        host.put("games", "" + games);
        host.put("sinceAd", "" + sinceAd);
        host.put("bonusDay", "" + lastBonusDay);
        host.put("noads", noAds ? "1" : "0");
        host.put("sound", soundOn ? "1" : "0");
        host.put("vibe", vibeOn ? "1" : "0");
        host.put("help", helpSeen ? "1" : "0");
        host.put("lastAd", "" + lastAd);
        host.put("xp", "" + xp);
        host.put("streak", "" + streak);
        host.put("bestStreak", "" + bestStreak);
        host.put("dailyDay", "" + lastDailyDay);
        host.put("rewardDay", "" + rewardDay);
        host.put("rewardLast", "" + lastRewardDay);
        host.put("reviewAsked", reviewAsked ? "1" : "0");
        host.put("reminder", reminderOn ? "1" : "0");
        host.put("remindAsked", remindAsked ? "1" : "0");
        host.put("tutorial", tutorialDone ? "1" : "0");
    }

    boolean adFree() { return noAds; }

    // ---- 7-day reward calendar

    boolean rewardReady() { return lastRewardDay != host.today(); }

    /** Which calendar day (0..6) the next claim is: missing a day starts again from day 1. */
    int rewardSlot() {
        int d = host.today();
        if (lastRewardDay >= 0 && lastRewardDay < d - 1) return 0;
        return rewardDay % REWARDS.length;
    }

    void claimReward(boolean twice) {
        if (!rewardReady()) { popup = P_NONE; return; }
        int slot = rewardSlot(), m = twice ? 2 : 1;
        coins += REWARDS[slot][0] * m;
        hints += REWARDS[slot][1] * m;
        lastRewardDay = host.today();
        rewardDay = (slot + 1) % REWARDS.length;
        String what = REWARDS[slot][0] > 0 ? "+" + REWARDS[slot][0] * m + " coins" : "";
        if (REWARDS[slot][1] > 0) what += (what.length() > 0 ? " and " : "+") + REWARDS[slot][1] * m + (REWARDS[slot][1] * m == 1 ? " hint" : " hints");
        say("Day " + (slot + 1) + " reward: " + what);
        snd(Synth.HINT);
        popup = P_NONE;
        spawnConfetti(50);
        save();
    }

    // ---- ranks

    static int rankIndex(int xp) { int r = 0; for (int i = 0; i < RANK_XP.length; i++) if (xp >= RANK_XP[i]) r = i; return r; }

    static float rankProgress(int xp) {
        int r = rankIndex(xp);
        if (r == RANK_XP.length - 1) return 1;
        return (xp - RANK_XP[r]) / (float) (RANK_XP[r + 1] - RANK_XP[r]);
    }

    // ---- daily puzzle

    boolean dailyDone() { return lastDailyDay == host.today(); }

    /** The streak as it stands today: it is lost once a whole day is missed. */
    int streakNow() { int d = host.today(); return lastDailyDay == d || lastDailyDay == d - 1 ? streak : 0; }

    void startDaily() {
        if (dailyLines.isEmpty()) return;
        int d = host.today(), n = dailyLines.size();
        Level daily = Level.parse(dailyLines.get(((d % n) + n) % n));
        setupMatch(daily, new Random(d * 31L + 7), 0.25f, 3, 0.3f, true);
    }

    /** "Tue, Oct 13" for a day number counted from 1 Jan 1970. */
    static String dateLabel(int day) {
        int z = day + 719468, era = (z >= 0 ? z : z - 146096) / 146097, doe = z - era * 146097;
        int yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365, doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        int mp = (5 * doy + 2) / 153, dd = doy - (153 * mp + 2) / 5 + 1, mm = mp < 10 ? mp + 3 : mp - 9;
        return DAYS[((day % 7) + 7) % 7] + ", " + MONTHS[mm - 1] + " " + dd;
    }

    // =====================================================================================
    // match setup

    int levelCount() { return levelLines.size(); }

    void startLevel(int index) {
        levelIdx = Math.max(0, index);
        float share = levelIdx < 5 ? 0.3f : levelIdx < 30 ? 0.25f : 0.2f;
        int bots = levelIdx < 3 ? 1 : levelIdx < 15 ? 2 : 3;
        float base = levelIdx < 10 ? 0.08f : levelIdx < 40 ? 0.2f : levelIdx < 120 ? 0.3f : 0.4f;
        setupMatch(Level.parse(levelLines.get(levelIdx % levelLines.size())), new Random(levelIdx * 7919L + 13 + games), share, bots, base, false);
    }

    private void setupMatch(Level level, Random random, float share, int bots, float base, boolean daily) {
        lv = level;
        rnd = random;
        dailyMode = daily;
        int n = lv.grid.length;
        board = new char[n];
        given = new boolean[n];
        // reveal a share of letters (more early on) but leave every word something to find
        List<Integer> letters = new ArrayList<Integer>();
        for (int i = 0; i < n; i++) if (lv.isLetter(i)) letters.add(i);
        Collections.shuffle(letters, rnd);
        int reveal = Math.round(letters.size() * share);
        for (int k = 0; k < letters.size() && reveal > 0; k++) {
            int i = letters.get(k);
            given[i] = true; board[i] = lv.grid[i];
            boolean ok = true;
            for (Level.Word wd : lv.wordsAt(i)) if (complete(wd)) ok = false;
            if (!ok) { given[i] = false; board[i] = 0; continue; }
            reveal--;
        }
        players.clear();
        Player me = new Player();
        me.name = "You"; me.avatar = ME_AVATAR; me.color = PLAYER_COLOR[0];
        players.add(me);
        List<Integer> names = new ArrayList<Integer>();
        for (int i = 0; i < BOT_NAMES.length; i++) names.add(i);
        Collections.shuffle(names, rnd);
        for (int b = 0; b < bots; b++) {
            Player p = new Player();
            p.bot = true;
            p.name = BOT_NAMES[names.get(b)];
            p.avatar = AVATARS[(names.get(b) + b) % AVATARS.length];
            p.color = PLAYER_COLOR[(b + 1) % PLAYER_COLOR.length];
            p.skill = Math.min(0.75f, base + rnd.nextFloat() * 0.15f);
            players.add(p);
        }
        for (Player p : players) refill(p);
        turn = 0; placedThisTurn = 0; selTile = -1; selWord = -1; hintTile = -1; hintCell = -1;
        over = false; doubled = false; rankUp = false; overT = -1; botT = 0; comboRun = 0;
        cellFx.clear(); cellOwner.clear(); floaters.clear(); floaterText.clear(); wordFx.clear(); flyFrom.clear(); confetti.clear();
        screen = PLAY; screenAge = 0; popup = P_NONE;
        selWord = firstOpenWord();
        if (!helpSeen) openPopup(P_HELP);
    }

    private int firstOpenWord() {
        for (int k = 0; k < lv.words.size(); k++) if (!complete(lv.words.get(k))) return k;
        return -1;
    }

    /**
     * Tops a rack up to RACK tiles. Every player draws from the letters the board still needs, so nobody runs
     * dry before the crossword is full; tiles that no longer fit anywhere (a rival filled the square) are swapped.
     */
    void refill(Player p) {
        List<Character> need = new ArrayList<Character>();
        for (int i = 0; i < board.length; i++) if (lv.isLetter(i) && board[i] == 0) need.add(lv.grid[i]);
        for (int t = p.rack.size() - 1; t >= 0; t--) {
            if (!need.remove(p.rack.get(t))) p.rack.remove(t);       // dead tile
        }
        while (p.rack.size() < RACK && !need.isEmpty()) p.rack.add(need.remove(rnd.nextInt(need.size())));
    }

    boolean complete(Level.Word wd) { for (int k : wd.cells) if (board[k] == 0) return false; return true; }

    int emptyCells() { int e = 0; for (int i = 0; i < board.length; i++) if (lv.isLetter(i) && board[i] == 0) e++; return e; }

    Player current() { return players.get(turn); }
    boolean myTurn() { return screen == PLAY && !over && turn == 0; }

    // =====================================================================================
    // playing

    /** Places a tile from a player's rack. Returns false when the square is wrong. */
    boolean place(int playerIdx, int tile, int cellIdx) {
        Player p = players.get(playerIdx);
        if (tile < 0 || tile >= p.rack.size() || !lv.isLetter(cellIdx) || board[cellIdx] != 0) return false;
        char ch = p.rack.get(tile);
        if (lv.grid[cellIdx] != ch) return false;
        p.rack.remove(tile);
        board[cellIdx] = ch;
        if (flyX >= 0) { flyFrom.put(cellIdx, new float[]{flyX, flyY}); cellFx.put(cellIdx, -FLY); }
        else cellFx.put(cellIdx, 0f);
        flyX = flyY = -1;
        cellOwner.put(cellIdx, playerIdx);
        int gained = 1, completed = 0;
        for (Level.Word wd : lv.wordsAt(cellIdx)) if (complete(wd)) { gained += wd.text.length(); completed++; flashWord(wd); }
        if (completed >= 2) gained += 5;
        if (playerIdx == 0) {                         // combo: from the 3rd right letter in a row, +1, +2, +3 extra
            comboRun++;
            if (comboRun >= 3) gained += Math.min(comboRun - 2, 3);
        }
        p.score += gained;
        placedThisTurn++;
        floater(cellIdx, "+" + gained, p.color);
        if (completed > 0) { snd(Synth.WORD); if (playerIdx == 0) buzz(true); }
        else snd(playerIdx == 0 ? Synth.PLACE : Synth.BOT);
        if (playerIdx == 0 && completed > 0) {
            String[] cheer = {"Nice!", "Great word!", "Brilliant!", "Super!", "Well done!"};
            showBanner(completed >= 2 ? "Double word! +5" : cheer[rnd.nextInt(cheer.length)]);
        } else if (playerIdx == 0 && comboRun >= 3) showBanner("Combo x" + comboRun + "!");
        if (hintCell == cellIdx) { hintCell = -1; hintTile = -1; }
        if (emptyCells() == 0) finish();
        return true;
    }

    /** A finished word pops letter by letter. */
    private void flashWord(Level.Word wd) { for (int k = 0; k < wd.cells.length; k++) wordFx.put(wd.cells[k], -(FLY + k * 0.06f)); }

    private void endTurn() {
        if (over) return;
        refill(current());
        placedThisTurn = 0;
        selTile = -1; hintTile = -1; hintCell = -1;
        turn = (turn + 1) % players.size();
        botT = 0;
        comboRun = 0;
        refill(current());
        if (turn == 0 && players.size() > 1) showBanner("Your turn!");
    }

    /** The player taps Done or makes a mistake. */
    void myEndTurn() { if (myTurn()) endTurn(); }

    private void finish() {
        over = true;
        overT = 0;
        Player me = players.get(0);
        int rank = rank(me);
        if (dailyMode) {
            int d = host.today();
            if (lastDailyDay != d) streak = lastDailyDay == d - 1 ? streak + 1 : 1;
            lastDailyDay = d;
            bestStreak = Math.max(bestStreak, streak);
            rewardCoins = (rank == 1 ? 80 : rank == 2 ? 40 : 20) + me.score / 4 + Math.min(streak, 10) * 5;
        } else {
            rewardCoins = (rank == 1 ? 40 : rank == 2 ? 20 : 10) + me.score / 4;
            levelIdx++;
        }
        rankBefore = rankIndex(xp);
        xpGained = me.score + (rank == 1 ? 30 : rank == 2 ? 15 : 5);
        xp += xpGained;
        rankUp = rankIndex(xp) > rankBefore;
        coins += rewardCoins;
        games++;
        if (rank == 1) wins++;
        sinceAd++;
        save();
        snd(Synth.WIN);
    }

    int rank(Player p) {
        int r = 1;
        for (Player o : players) if (o != p && o.score > p.score) r++;
        return r;
    }

    private void botStep() {
        Player p = current();
        // find tiles that fit somewhere
        List<int[]> moves = new ArrayList<int[]>();
        for (int t = 0; t < p.rack.size(); t++) for (int i = 0; i < board.length; i++)
            if (lv.isLetter(i) && board[i] == 0 && lv.grid[i] == p.rack.get(t)) moves.add(new int[]{t, i});
        int maxThisTurn = 1 + (int) (p.skill * 3);
        boolean miss = rnd.nextFloat() > 0.55f + p.skill * 0.4f;
        if (moves.isEmpty() || placedThisTurn >= maxThisTurn || (placedThisTurn > 0 && miss)) {
            if (placedThisTurn == 0 && !moves.isEmpty()) {         // a bot can also miss its first try
                if (miss) showBanner(p.name + " missed!");
                else { int[] m = moves.get(rnd.nextInt(moves.size())); flyX = sbX[turn]; flyY = sbY[turn]; place(turn, m[0], m[1]); botT = -0.15f; return; }
            }
            endTurn();
            return;
        }
        // prefer squares that finish a word, like a good player would
        int[] best = moves.get(rnd.nextInt(moves.size()));
        for (int[] m : moves) {
            board[m[1]] = lv.grid[m[1]];
            boolean fin = false;
            for (Level.Word wd : lv.wordsAt(m[1])) if (complete(wd)) fin = true;
            board[m[1]] = 0;
            if (fin && rnd.nextFloat() < p.skill * 0.8f) { best = m; break; }
        }
        flyX = sbX[turn]; flyY = sbY[turn];
        place(turn, best[0], best[1]);
    }

    void useHint() {
        if (!myTurn()) return;
        if (hints <= 0) { openPopup(P_SHOP); return; }
        Player me = players.get(0);
        int bestT = -1, bestC = -1;
        Level.Word sw = selWord >= 0 ? lv.words.get(selWord) : null;
        for (int t = 0; t < me.rack.size() && bestT < 0; t++) {
            if (sw != null) for (int k : sw.cells) if (board[k] == 0 && lv.grid[k] == me.rack.get(t)) { bestT = t; bestC = k; break; }
        }
        for (int t = 0; t < me.rack.size() && bestT < 0; t++)
            for (int i = 0; i < board.length; i++) if (lv.isLetter(i) && board[i] == 0 && lv.grid[i] == me.rack.get(t)) { bestT = t; bestC = i; break; }
        if (bestT < 0) return;
        hints--;
        hintTile = bestT; hintCell = bestC; selTile = bestT;
        List<Level.Word> ws = lv.wordsAt(bestC);
        if (!ws.isEmpty()) selWord = lv.words.indexOf(ws.get(0));
        snd(Synth.HINT);
        save();
    }

    // =====================================================================================
    // input

    void down(float x, float y) {
        touching = true; moved = false; downX = x; downY = y; dragX = x; dragY = y;
        pressed = null;
        for (float[] b : allButtons) if (b[2] > b[0] && in(b, x, y)) pressed = b;
        if (popup != P_NONE || screen != PLAY || !myTurn()) return;
        int t = tileAt(x, y);
        if (t >= 0) { dragTile = t; }
    }

    void move(float x, float y) {
        if (!touching) return;
        dragX = x; dragY = y;
        if (Math.abs(x - downX) + Math.abs(y - downY) > 10 * dp) moved = true;
    }

    void cancel() { touching = false; dragTile = -1; }

    void up(float x, float y) {
        if (!touching) return;
        touching = false;
        int dt = dragTile;
        dragTile = -1;
        if (dt >= 0 && moved) {            // dropped a dragged tile
            int c = cellAt(x, y - rackSize * 0.35f);
            if (c >= 0) { flyX = x; flyY = y - rackSize * 0.35f; tryPlace(dt, c); flyX = flyY = -1; }
            return;
        }
        tap(x, y);
    }

    boolean back() {
        if (popup == P_RESULT) { closeResult(false); return true; }
        if (popup != P_NONE) { popup = P_NONE; return true; }
        if (screen == PLAY) { if (over) { go(HOME); return true; } openPopup(P_PAUSE); return true; }
        return false;
    }

    private void go(int s) {
        screen = s; screenAge = 0;
        if (s == HOME && games >= 1 && rewardReady()) openPopup(P_REWARDS);
    }
    private static boolean in(float[] r, float x, float y) { return x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3]; }
    private void set(float[] r, float l, float t, float rr, float b) { r[0] = l; r[1] = t; r[2] = rr; r[3] = b; }
    private void click() { snd(Synth.CLICK); }
    private void snd(int id) { if (soundOn) host.sound(id); }
    private void buzz(boolean s) { if (vibeOn) host.haptic(s); }
    void say(String s) { toast = s; toastT = 2.6f; }
    private void showBanner(String s) { banner = s; bannerT = 1.3f; }
    private void openPopup(int p) { popup = p; popupAge = 0; }

    private void tap(float x, float y) {
        if (popup != P_NONE) { if (popupAge > 0.25f) tapPopup(x, y); return; }
        if (screen == HOME) { tapHome(x, y); return; }
        if (in(bBack, x, y)) { click(); back(); return; }
        if (in(bHelp, x, y)) { click(); openPopup(P_HELP); return; }
        if (!myTurn()) return;
        if (in(bHint, x, y)) { click(); useHint(); return; }
        if (in(bDone, x, y)) { click(); myEndTurn(); return; }
        int t = tileAt(x, y);
        if (t >= 0) { click(); selTile = selTile == t ? -1 : t; return; }
        int c = cellAt(x, y);
        if (c >= 0) {
            if (selTile >= 0 && lv.isLetter(c) && board[c] == 0) {
                if (selTile < RACK) { flyX = rackX[selTile]; flyY = rackY[selTile]; }
                tryPlace(selTile, c);
                flyX = flyY = -1;
                return;
            }
            selectAt(c);
        }
    }

    private void tryPlace(int tile, int c) {
        if (!myTurn()) return;
        if (!lv.isLetter(c) || board[c] != 0) { selectAt(c); return; }
        if (place(0, tile, c)) {
            selTile = -1;
            if (!tutorialDone && !dailyMode && levelIdx == 0) { tutorialDone = true; save(); }
            if (!over && players.get(0).rack.isEmpty()) endTurn();
            List<Level.Word> ws = lv.wordsAt(c);
            if (!over && !ws.isEmpty() && selWord >= 0 && complete(lv.words.get(selWord))) selWord = firstOpenWord();
        } else {
            flyX = flyY = -1;
            wrongT = 0.5f; shakeTile = tile;
            snd(Synth.WRONG); buzz(true);
            showBanner("Not there! Turn over");
            endTurn();
        }
    }

    /** Tapping a letter or clue cell shows that word's clue (tap again to switch direction). */
    private void selectAt(int c) {
        List<Level.Word> ws = lv.grid[c] == '#' ? lv.cluesIn(c) : lv.wordsAt(c);
        if (ws.isEmpty()) return;
        int a = lv.words.indexOf(ws.get(0));
        if (ws.size() > 1 && selWord == a) a = lv.words.indexOf(ws.get(1));
        selWord = a;
        click();
    }

    private void tapHome(float x, float y) {
        if (in(bPlay, x, y)) { click(); startLevel(levelIdx); return; }
        if (in(bDaily, x, y)) {
            click();
            if (dailyDone()) say("Today's puzzle is done. A new one comes tomorrow!");
            else startDaily();
            return;
        }
        if (in(bGift, x, y)) { click(); if (rewardReady()) openPopup(P_REWARDS); else say("Your next gift is ready tomorrow"); return; }
        if (in(bGear, x, y)) { click(); openPopup(P_SETTINGS); return; }
        if (in(bShopHome, x, y)) { click(); openPopup(P_SHOP); }
    }

    private void tapPopup(float x, float y) {
        switch (popup) {
            case P_RESULT:
                if (in(bA, x, y)) { click(); closeResult(!dailyMode); }
                else if (in(bB, x, y) && !doubled) { click(); if (host.videoReady()) host.showVideo(R_DOUBLE); else say("No video right now"); }
                else if (in(bC, x, y)) { click(); closeResult(false); }
                break;
            case P_SETTINGS:
                if (in(bClose, x, y)) { click(); popup = P_NONE; return; }
                for (int i = 0; i < bSet.length; i++) {
                    if (!in(bSet[i], x, y)) continue;
                    click();
                    if (i == 0) soundOn = !soundOn;
                    else if (i == 1) vibeOn = !vibeOn;
                    else if (i == 2) { reminderOn = !reminderOn; remindAsked = true; host.setReminder(reminderOn); }
                    else if (i == 3) openPopup(P_HELP);
                    else if (i == 4) { if (noAds) say("Ads are already removed"); else host.buy(NOADS_ID); }
                    else if (i == 5) host.restore();
                    else if (i == 6 && host.privacyOptionsRequired()) host.showPrivacyOptions();
                    save();
                    return;
                }
                break;
            case P_SHOP:
                if (in(bA, x, y)) { click(); if (host.videoReady()) host.showVideo(R_HINT); else say("No video right now"); }
                else if (in(bB, x, y)) { click(); if (coins >= HINT_COST) { coins -= HINT_COST; hints++; say("+1 hint"); save(); } else say("Not enough coins"); }
                else if (in(bC, x, y)) { click(); host.buy(HINTS_ID); }
                else if (in(bClose, x, y)) { click(); popup = P_NONE; }
                break;
            case P_HELP:
                if (in(bA, x, y) || in(bClose, x, y)) { click(); popup = P_NONE; helpSeen = true; save(); }
                break;
            case P_REWARDS:
                if (in(bA, x, y)) { click(); claimReward(false); }
                else if (in(bB, x, y)) { click(); if (host.videoReady()) host.showVideo(R_REWARD2); else say("No video right now"); }
                else if (in(bClose, x, y)) { click(); popup = P_NONE; }
                break;
            case P_REMIND:
                if (in(bA, x, y)) { click(); reminderOn = true; remindAsked = true; host.setReminder(true); say("We'll remind you each evening"); popup = P_NONE; save(); }
                else if (in(bB, x, y) || in(bClose, x, y)) { click(); remindAsked = true; popup = P_NONE; save(); }
                break;
            case P_PAUSE:
                if (in(bA, x, y)) { click(); popup = P_NONE; }
                else if (in(bB, x, y)) { click(); popup = P_NONE; go(HOME); }
                break;
            default: break;
        }
    }

    private void closeResult(boolean next) {
        popup = P_NONE;
        boolean won = rank(players.get(0)) == 1;
        boolean wasDaily = dailyMode;
        if (won && wins >= 3 && !reviewAsked) {          // a happy moment: ask for a rating once, instead of an advert
            reviewAsked = true; save();
            host.requestReview();
        } else {
            boolean adTime = !noAds && levelIdx >= 4 && sinceAd >= 2 && host.now() - lastAd > 120000 && host.interstitialReady();
            if (adTime) { sinceAd = 0; lastAd = host.now(); save(); host.showInterstitial(); }
        }
        if (next) startLevel(levelIdx);
        else {
            go(HOME);
            if (wasDaily && !remindAsked && !reminderOn) openPopup(P_REMIND);
        }
    }

    int tileAt(float x, float y) {
        if (lv == null || rackSize <= 0) return -1;
        List<Character> rack = players.get(0).rack;
        for (int t = 0; t < rack.size(); t++) if (Math.abs(x - rackX[t]) <= rackSize * 0.55f && Math.abs(y - rackY[t]) <= rackSize * 0.6f) return t;
        return -1;
    }

    int cellAt(float x, float y) {
        if (lv == null || cell <= 0) return -1;
        int c = (int) Math.floor((x - gl) / cell), r = (int) Math.floor((y - gt) / cell);
        if (c < 0 || r < 0 || c >= lv.cols || r >= lv.rows) return -1;
        return r * lv.cols + c;
    }

    // ---- store answers

    void onOwned(String product, boolean owned) {
        if (NOADS_ID.equals(product)) { if (owned && !noAds) say("Ads removed. Thank you!"); noAds = owned; }
        save();
    }

    void onGranted(String product) {
        if (HINTS_ID.equals(product)) { hints += PACK_HINTS; snd(Synth.HINT); say("+" + PACK_HINTS + " hints. Thank you!"); if (popup == P_SHOP) popup = P_NONE; save(); }
    }

    void onStoreMessage(String m) { say(m); }

    void onVideoReward(int kind) {
        if (kind == R_DOUBLE && popup == P_RESULT && !doubled) { doubled = true; coins += rewardCoins; say("+" + rewardCoins + " bonus coins"); save(); }
        else if (kind == R_HINT) { hints++; if (popup == P_SHOP) popup = P_NONE; say("+1 hint"); save(); }
        else if (kind == R_REWARD2 && popup == P_REWARDS) claimReward(true);
    }

    void pause() { save(); }

    // =====================================================================================
    // update

    void update(float dt) {
        dt = Math.min(dt, 0.05f);
        time += dt; popupAge += dt; screenAge += dt;
        if (toastT > 0) toastT -= dt;
        if (bannerT > 0) bannerT -= dt;
        if (wrongT > 0) wrongT -= dt;
        for (Map.Entry<Integer, Float> e : cellFx.entrySet()) e.setValue(e.getValue() + dt);
        for (java.util.Iterator<Map.Entry<Integer, Float>> it = wordFx.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Float> e = it.next();
            e.setValue(e.getValue() + dt);
            if (e.getValue() > 0.6f) it.remove();
        }
        for (java.util.Iterator<Map.Entry<Integer, float[]>> it = flyFrom.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, float[]> e = it.next();
            Float fx = cellFx.get(e.getKey());
            if (fx == null || fx >= 0) it.remove();
        }
        for (int k = confetti.size() - 1; k >= 0; k--) {
            float[] c = confetti.get(k);
            c[3] += 700 * dp * dt; c[0] += c[2] * dt; c[1] += c[3] * dt; c[4] += c[5] * dt; c[2] *= (1 - dt * 0.8f);
            if (c[1] > h + 40 * dp) confetti.remove(k);
        }
        float target = coins;
        displayCoins += (target - displayCoins) * Math.min(1, dt * 5);
        if (Math.abs(target - displayCoins) < 0.6f) displayCoins = target;
        for (int k = floaters.size() - 1; k >= 0; k--) {
            float[] f = floaters.get(k);
            f[2] += dt; f[1] -= 40 * dp * dt;
            if (f[2] > 1.1f) { floaters.remove(k); floaterText.remove(k); }
        }
        if (screen != PLAY || lv == null) return;
        if (over) {
            overT += dt;
            if (overT > 1.4f && popup == P_NONE) {
                openPopup(P_RESULT);
                if (rank(players.get(0)) == 1 || rankUp || dailyMode) spawnConfetti(110);
            }
            return;
        }
        if (popup != P_NONE) return;
        if (current().bot) {
            botT += dt;
            if (botT > (placedThisTurn == 0 ? 0.9f : 0.55f)) { botT = 0; botStep(); }
        }
    }

    void spawnConfetti(int n) {
        int[] cols = {ACCENT, YELLOW, GREEN, 0xFFF26B4F, 0xFF9B6BEA, 0xFFEC5C9A};
        Random r = new Random((long) (time * 1000) + n);
        for (int k = 0; k < n; k++) {
            float x = w * (0.1f + 0.8f * r.nextFloat()), y = -20 * dp - r.nextFloat() * h * 0.35f;
            confetti.add(new float[]{x, y, (r.nextFloat() - 0.5f) * 260 * dp, 60 * dp + r.nextFloat() * 220 * dp, r.nextFloat() * 6.28f,
                    (r.nextFloat() - 0.5f) * 12, cols[r.nextInt(cols.length)], (5 + r.nextFloat() * 5) * dp});
        }
    }

    private void floater(int cellIdx, String text, int color) {
        if (cell <= 0) return;
        float x = gl + (cellIdx % lv.cols + 0.5f) * cell, y = gt + (cellIdx / lv.cols + 0.2f) * cell;
        floaters.add(new float[]{x, y, 0, 0, color});
        floaterText.add(text);
    }

    // =====================================================================================
    // drawing helpers

    void draw(Painter p, float width, float height, float density, int top, int bottom) {
        w = width; h = height; dp = density; insetTop = top; insetBottom = bottom;
        for (float[] b : allButtons) b[0] = b[1] = b[2] = b[3] = 0;
        for (float[] b : bSet) b[0] = b[1] = b[2] = b[3] = 0;
        p.gradient(0, 0, w, h, BG, BG2);
        if (screen == HOME) drawHome(p); else drawPlay(p);
        if (popup != P_NONE) drawPopup(p);
        drawConfetti(p);
        if (toastT > 0 && toast != null) drawToast(p);
    }

    private void drawConfetti(Painter p) {
        for (float[] c : confetti) {
            float cs = (float) Math.cos(c[4]), sn = (float) Math.sin(c[4]), a = c[7], b = c[7] * 0.55f;
            float sq = Math.abs((float) Math.sin(c[4] * 1.7f)) * 0.7f + 0.3f;   // flutter
            q[0] = c[0] + (-a * cs + b * sn) * sq; q[1] = c[1] + (-a * sn - b * cs);
            q[2] = c[0] + (a * cs + b * sn) * sq;  q[3] = c[1] + (a * sn - b * cs);
            q[4] = c[0] + (a * cs - b * sn) * sq;  q[5] = c[1] + (a * sn + b * cs);
            q[6] = c[0] + (-a * cs - b * sn) * sq; q[7] = c[1] + (-a * sn + b * cs);
            p.poly(q, 4, (int) c[6]);
        }
    }

    private void fit(Painter p, String s, float x, float y, float maxW, float size, int color, boolean bold, int align) {
        float tw = p.textWidth(s, size, bold);
        if (tw > maxW && tw > 0) size *= maxW / tw;
        p.text(s, x, y, size, color, bold, align);
    }

    private void line(Painter p, float x1, float y1, float x2, float y2, float wd, int color) { q[0] = x1; q[1] = y1; q[2] = x2; q[3] = y2; p.polyline(q, 2, wd, color); }

    private void button(Painter p, float[] rect, float l, float t, float r, float b, String label, float size, int color, int dark, int textColor) {
        set(rect, l, t, r, b);
        float rad = Math.min(18 * dp, (b - t) / 2);
        boolean down = touching && pressed == rect && in(rect, dragX, dragY);
        if (down) { t += 2.5f * dp; b += 2.5f * dp; }               // pressed: the button sinks into its shadow
        p.roundRect(l, t + (down ? 0.5f : 3) * dp, r, b + (down ? 0.5f : 3) * dp, rad, dark);
        p.roundRect(l, t, r, b, rad, color);
        if (label.startsWith("\u25B6")) {               // video button: draw the play mark instead of the glyph
            label = label.substring(1).trim();
            float tw = Math.min(p.textWidth(label, size, true), r - l - 40 * dp), ic = size * 0.75f;
            float x0 = (l + r) / 2 - (tw + ic + 8 * dp) / 2, cy = (t + b) / 2;
            q[0] = x0; q[1] = cy - ic / 2; q[2] = x0 + ic * 0.9f; q[3] = cy; q[4] = x0; q[5] = cy + ic / 2;
            p.poly(q, 3, textColor);
            fit(p, label, x0 + ic + 8 * dp, cy + size * 0.36f, r - l - 40 * dp, size, textColor, true, 1);
            return;
        }
        fit(p, label, (l + r) / 2, (t + b) / 2 + size * 0.36f, r - l - 16 * dp, size, textColor, true, 0);
    }

    private void coinIcon(Painter p, float cx, float cy, float r) {
        p.circle(cx, cy, r, YELLOW_D);
        p.circle(cx, cy - r * 0.08f, r * 0.9f, YELLOW);
        p.ring(cx, cy - r * 0.08f, r * 0.58f, r * 0.14f, YELLOW_D);
    }

    private void bulb(Painter p, float cx, float cy, float s, int color) {
        p.circle(cx, cy - s * 0.2f, s * 0.62f, color);
        p.roundRect(cx - s * 0.3f, cy + s * 0.25f, cx + s * 0.3f, cy + s * 0.7f, s * 0.1f, color);
        p.roundRect(cx - s * 0.22f, cy + s * 0.76f, cx + s * 0.22f, cy + s * 0.9f, s * 0.07f, color);
    }

    private void pill(Painter p, float l, float t, float r, float b, int color) { p.roundRect(l, t, r, b, (b - t) / 2, color); }

    /** White card with a soft shadow. */
    private void card(Painter p, float l, float t, float r, float b, float rad, int color) {
        p.roundRect(l, t + 2 * dp, r, b + 3 * dp, rad, SHADOW);
        p.roundRect(l, t, r, b, rad, color);
    }

    /** Small round icon button (back, help). */
    private void roundIcon(Painter p, float cx, float cy, float r) {
        p.circle(cx, cy + 1.5f * dp, r, SHADOW);
        p.circle(cx, cy, r, CARD);
    }

    private void avatar(Painter p, Player pl, float cx, float cy, float r, boolean active) {
        if (active) { p.glow(cx, cy, r * 1.9f, (0x88 << 24) | (pl.color & 0xFFFFFF)); p.circle(cx, cy, r + 3 * dp, WHITE); }
        p.circle(cx, cy, r, pl.color);
        p.image(pl.avatar, cx - r * 0.78f, cy - r * 0.78f, cx + r * 0.78f, cy + r * 0.78f, 255);
    }

    // =====================================================================================
    // home

    private void drawHome(Painter p) {
        float cx = w / 2, top = insetTop;
        // top bar: coins, hints, gift, settings
        card(p, 14 * dp, top + 14 * dp, 112 * dp, top + 50 * dp, 18 * dp, CARD);
        coinIcon(p, 34 * dp, top + 32 * dp, 12 * dp);
        fit(p, "" + Math.round(displayCoins), 52 * dp, top + 38 * dp, 56 * dp, 17 * dp, INK, true, 1);
        set(bShopHome, 120 * dp, top + 14 * dp, 192 * dp, top + 50 * dp);
        card(p, bShopHome[0], bShopHome[1], bShopHome[2], bShopHome[3], 18 * dp, CARD);
        bulb(p, 140 * dp, top + 31 * dp, 11 * dp, YELLOW);
        fit(p, "" + hints, 158 * dp, top + 38 * dp, 30 * dp, 17 * dp, INK, true, 1);
        set(bGift, w - 110 * dp, top + 10 * dp, w - 62 * dp, top + 56 * dp);
        boolean gift = rewardReady();
        float gb = gift ? (float) Math.abs(Math.sin(time * 4)) * 3 * dp : 0;
        roundIcon(p, w - 86 * dp, top + 32 * dp, 19 * dp);
        p.image("1f381", w - 99 * dp, top + 19 * dp - gb, w - 73 * dp, top + 45 * dp - gb, 255);
        if (gift) p.circle(w - 72 * dp, top + 17 * dp, 5 * dp, RED);
        set(bGear, w - 58 * dp, top + 10 * dp, w - 10 * dp, top + 56 * dp);
        roundIcon(p, w - 34 * dp, top + 32 * dp, 19 * dp);
        gear(p, w - 34 * dp, top + 32 * dp, 9 * dp);
        // rank card
        float rt = top + 62 * dp, rb = rt + 64 * dp;
        card(p, 14 * dp, rt, w - 14 * dp, rb, 18 * dp, CARD);
        int ri = rankIndex(xp);
        p.circle(46 * dp, (rt + rb) / 2, 22 * dp, ACCENT_SOFT);
        p.image(RANK_ICONS[ri], 31 * dp, (rt + rb) / 2 - 15 * dp, 61 * dp, (rt + rb) / 2 + 15 * dp, 255);
        fit(p, RANKS[ri], 78 * dp, rt + 27 * dp, w * 0.4f, 16 * dp, INK, true, 1);
        String nextTxt = ri < RANKS.length - 1 ? xp + " / " + RANK_XP[ri + 1] + " XP" : xp + " XP";
        fit(p, nextTxt, w - 30 * dp, rt + 27 * dp, w * 0.35f, 12 * dp, MUTED, true, 2);
        float bl = 78 * dp, br = w - 30 * dp, by = rt + 38 * dp;
        p.roundRect(bl, by, br, by + 9 * dp, 4.5f * dp, BOARD);
        float fr = rankProgress(xp);
        if (fr > 0) p.roundRect(bl, by, bl + Math.max(9 * dp, (br - bl) * fr), by + 9 * dp, 4.5f * dp, ACCENT);
        fit(p, "Wins " + wins + "  ·  Best streak " + bestStreak, bl, rt + 60 * dp, br - bl, 11 * dp, MUTED, false, 1);
        // bottom: footnote, daily card, play button
        float foot = h - insetBottom - 14 * dp;
        float db = foot - 20 * dp, dt = db - 76 * dp;
        float pb = dt - 18 * dp, pt = pb - 70 * dp;
        homeToastY = pt - 24 * dp;
        // logo between the rank card and the play button
        float avail = pt - 14 * dp - (rb + 16 * dp) - 34 * dp;
        float lw = Math.min(Math.min(w * 0.86f, 340 * dp), avail / 6 * 9);
        float lt = rb + 16 * dp + Math.max(0, (avail - logoHeight(lw)) * 0.5f);
        logo(p, cx, lt, lw);
        fit(p, "Crossword battles with friendly rivals", cx, lt + logoHeight(lw) + 26 * dp, w - 40 * dp, 14 * dp, MUTED, true, 0);
        // play button (gently breathing)
        float pulse = 1 + 0.025f * (float) Math.sin(time * 3);
        float bw = Math.min(w - 48 * dp, 320 * dp) * pulse, bh = 70 * dp;
        button(p, bPlay, cx - bw / 2, pt, cx + bw / 2, pt + bh, "Play  ·  Level " + (levelIdx + 1), 22 * dp, ACCENT, ACCENT_D, WHITE);
        // daily puzzle card
        float dl = cx - Math.min(w - 48 * dp, 320 * dp) / 2, dr = cx + Math.min(w - 48 * dp, 320 * dp) / 2;
        set(bDaily, dl, dt, dr, db);
        boolean done = dailyDone(), dDown = touching && pressed == bDaily && in(bDaily, dragX, dragY);
        float off = dDown ? 2 * dp : 0;
        card(p, dl, dt + off, dr, db + off, 20 * dp, CARD);
        // calendar icon
        float ix = dl + 40 * dp, iy = (dt + db) / 2 + off;
        p.roundRect(ix - 22 * dp, iy - 22 * dp, ix + 22 * dp, iy + 22 * dp, 10 * dp, 0xFFFFEFEA);
        p.roundRect(ix - 22 * dp, iy - 22 * dp, ix + 22 * dp, iy - 5 * dp, 10 * dp, 0xFFF26B4F);
        p.roundRect(ix - 22 * dp, iy - 12 * dp, ix + 22 * dp, iy - 5 * dp, 0, 0xFFF26B4F);
        String dl2 = dateLabel(host.today());
        fit(p, dl2.substring(dl2.lastIndexOf(' ') + 1), ix, iy + 16 * dp, 36 * dp, 19 * dp, INK, true, 0);
        fit(p, dl2.substring(5, 8).toUpperCase(), ix, iy - 9.5f * dp, 38 * dp, 11 * dp, WHITE, true, 0);
        fit(p, "Daily Puzzle", dl + 74 * dp, dt + 33 * dp + off, dr - dl - 170 * dp, 17 * dp, INK, true, 1);
        fit(p, done ? "Done! Back tomorrow" : dl2, dl + 74 * dp, dt + 54 * dp + off, dr - dl - 172 * dp, 12 * dp, done ? GREEN_D : MUTED, true, 1);
        // streak and status
        int st = streakNow();
        p.image("1f525", dr - 92 * dp, iy - 13 * dp, dr - 66 * dp, iy + 13 * dp, st > 0 ? 255 : 90);
        fit(p, "" + st, dr - 62 * dp, iy + 7 * dp, 24 * dp, 18 * dp, st > 0 ? 0xFFF26B4F : MUTED, true, 1);
        if (done) {
            p.circle(dr - 22 * dp, iy, 13 * dp, GREEN);
            line(p, dr - 28 * dp, iy, dr - 24 * dp, iy + 5 * dp, 2.6f * dp, WHITE);
            line(p, dr - 24 * dp, iy + 5 * dp, dr - 16 * dp, iy - 5 * dp, 2.6f * dp, WHITE);
        } else {
            p.circle(dr - 22 * dp, iy, 13 * dp, ACCENT);
            q[0] = dr - 25 * dp; q[1] = iy - 6 * dp; q[2] = dr - 16 * dp; q[3] = iy; q[4] = dr - 25 * dp; q[5] = iy + 6 * dp;
            p.poly(q, 3, WHITE);
            p.circle(dr - 6 * dp, dt + 6 * dp + off, 5 * dp, RED);
        }
        fit(p, "Offline · " + levelCount() + " crosswords · new Daily Puzzle every day", cx, foot, w - 40 * dp, 11 * dp, MUTED, false, 0);
    }

    /** RIVALS down and CROSSWORD across, crossing at the middle S, like a tiny crossword. */
    private void logo(Painter p, float cx, float t, float width) {
        String a = "RIVALS", b = "CROSSWORD";
        float c = width / 9f;
        float l = cx - c * 4.5f;
        int k = 0;
        for (int i = 0; i < 5; i++) letterTile(p, l + 4 * c, t + i * c, c, a.charAt(i), screenAge * 11 > k++, false);
        for (int i = 0; i < 9; i++) letterTile(p, l + i * c, t + 5 * c, c, b.charAt(i), screenAge * 11 > k++, i == 4);
    }

    float logoHeight(float width) { return width / 9f * 6; }

    private void letterTile(Painter p, float l, float t, float c, char ch, boolean show, boolean accent) {
        float g = c * 0.05f;
        if (show) p.roundRect(l + g, t + g + c * 0.05f, l + c - g, t + c - g + c * 0.05f, c * 0.2f, accent ? ACCENT_D : TILE_SHADE);
        p.roundRect(l + g, t + g, l + c - g, t + c - g, c * 0.2f, show ? (accent ? ACCENT : CARD) : BOARD);
        if (show) fit(p, "" + ch, l + c / 2, t + c * 0.68f, c * 0.8f, c * 0.55f, accent ? WHITE : INK, true, 0);
    }

    private void gear(Painter p, float cx, float cy, float r) {
        for (int k = 0; k < 8; k++) { double a = k * Math.PI / 4; p.circle(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, r * 0.33f, MUTED); }
        p.circle(cx, cy, r * 0.95f, MUTED);
        p.circle(cx, cy, r * 0.4f, BG);
    }

    // =====================================================================================
    // play

    private void drawPlay(Painter p) {
        if (lv == null) return;
        float top = insetTop + 6 * dp;
        // top bar
        set(bBack, 6 * dp, top, 52 * dp, top + 44 * dp);
        roundIcon(p, 29 * dp, top + 22 * dp, 18 * dp);
        q[0] = 33 * dp; q[1] = top + 14 * dp; q[2] = 25 * dp; q[3] = top + 22 * dp; q[4] = 33 * dp; q[5] = top + 30 * dp;
        p.polyline(q, 3, 2.8f * dp, INK);
        fit(p, dailyMode ? "Daily Puzzle" : "Level " + (levelIdx + (over ? 0 : 1)), w / 2, top + 28 * dp, w * 0.5f, 18 * dp, INK, true, 0);
        set(bHelp, w - 52 * dp, top, w - 6 * dp, top + 44 * dp);
        roundIcon(p, w - 29 * dp, top + 22 * dp, 18 * dp);
        fit(p, "?", w - 29 * dp, top + 29 * dp, 20 * dp, 19 * dp, INK, true, 0);
        // scoreboard
        boolean shortScreen = (h - insetTop - insetBottom) / dp < 700;
        float sy = top + (shortScreen ? 46 : 52) * dp, sh = 58 * dp;
        int np = players.size();
        float sw = (w - 24 * dp - (np - 1) * 8 * dp) / np;
        for (int k = 0; k < np; k++) {
            Player pl = players.get(k);
            float l = 12 * dp + k * (sw + 8 * dp);
            boolean act = !over && k == turn;
            if (act) {                                       // the player whose turn it is: white card, coloured outline
                p.roundRect(l, sy + 2 * dp, l + sw, sy + sh + 3 * dp, 16 * dp, SHADOW);
                p.roundRect(l, sy, l + sw, sy + sh, 16 * dp, pl.color);
                p.roundRect(l + 2 * dp, sy + 2 * dp, l + sw - 2 * dp, sy + sh - 2 * dp, 14 * dp, CARD);
            } else p.roundRect(l, sy, l + sw, sy + sh, 16 * dp, 0xFFE9EDF5);
            if (sw >= 120 * dp) {
                sbX[k] = l + 26 * dp; sbY[k] = sy + sh / 2;
                avatar(p, pl, l + 26 * dp, sy + sh / 2, 17 * dp, false);
                fit(p, pl.name, l + 50 * dp, sy + 24 * dp, sw - 56 * dp, 13 * dp, act ? INK : MUTED, true, 1);
                fit(p, "" + pl.score, l + 50 * dp, sy + 46 * dp, sw - 56 * dp, 19 * dp, act ? pl.color : INK, true, 1);
            } else {                                        // narrow card: avatar and score on top, name below
                sbX[k] = l + 20 * dp; sbY[k] = sy + 20 * dp;
                avatar(p, pl, l + 20 * dp, sy + 20 * dp, 13 * dp, false);
                fit(p, "" + pl.score, l + 39 * dp, sy + 27 * dp, sw - 44 * dp, 19 * dp, act ? pl.color : INK, true, 1);
                fit(p, pl.name, l + sw / 2, sy + 50 * dp, sw - 10 * dp, 12 * dp, act ? INK : MUTED, true, 0);
            }
            if (act && pl.bot) {
                for (int d = 0; d < 3; d++) p.circle(l + sw - 20 * dp + d * 6 * dp, sy + 10 * dp, 2.4f * dp * (0.6f + 0.4f * (float) Math.abs(Math.sin(time * 5 + d))), pl.color);
            }
        }
        // layout: grid, clue strip, rack
        float rackH = (shortScreen ? 76 : 86) * dp, clueH = (shortScreen ? 52 : 58) * dp;
        float gridTop = sy + sh + (shortScreen ? 6 : 12) * dp, gridBottom = h - insetBottom - rackH - clueH - (shortScreen ? 8 : 14) * dp;
        cell = Math.min((w - 12 * dp) / lv.cols, (gridBottom - gridTop) / lv.rows);
        gl = (w - cell * lv.cols) / 2;
        gt = gridTop + ((gridBottom - gridTop) - cell * lv.rows) / 2;
        drawGrid(p);
        // clue strip
        float cy0 = gt + cell * lv.rows + 10 * dp;
        drawClueStrip(p, cy0, clueH - 6 * dp);
        // rack
        float ry = h - insetBottom - rackH + 4 * dp;
        drawRack(p, ry, rackH - 10 * dp);
        drawFlights(p);
        if (tutorialActive()) drawTutorial(p);
        // floaters and banner
        for (int k = 0; k < floaters.size(); k++) {
            float[] f = floaters.get(k);
            int a = (int) (255 * Math.max(0, Math.min(1, (1.1f - f[2]) / 0.4f)));
            float s = 20 * dp * (f[2] < 0.15f ? 0.6f + f[2] * 2.7f : 1);
            for (int d = 0; d < 4; d++) fit(p, floaterText.get(k), f[0] + (d == 0 ? -1.5f : d == 1 ? 1.5f : 0) * dp, f[1] + (d == 2 ? -1.5f : d == 3 ? 1.5f : 0) * dp, cell * 2, s, (a << 24) | 0xFFFFFF, true, 0);
            fit(p, floaterText.get(k), f[0], f[1], cell * 2, s, (a << 24) | ((int) f[4] & 0xFFFFFF), true, 0);
        }
        if (bannerT > 0 && banner != null) {
            float a = Math.min(1, bannerT / 0.3f);
            float tw = p.textWidth(banner, 20 * dp, true) + 40 * dp;
            float by = gt + cell * lv.rows * 0.42f;
            p.roundRect(w / 2 - tw / 2, by - 24 * dp, w / 2 + tw / 2, by + 23 * dp, 23 * dp, ((int) (a * 0x22)) << 24 | 0x203060);
            p.roundRect(w / 2 - tw / 2, by - 26 * dp, w / 2 + tw / 2, by + 20 * dp, 23 * dp, ((int) (a * 255)) << 24 | 0xFFFFFF);
            fit(p, banner, w / 2, by + 4 * dp, tw - 20 * dp, 20 * dp, ((int) (a * 255)) << 24 | (ACCENT_D & 0xFFFFFF), true, 0);
        }
        // dragged tile follows the finger
        if (dragTile >= 0 && moved && dragTile < players.get(0).rack.size()) {
            float s = rackSize * 1.05f;
            float x = dragX, y = dragY - rackSize * 0.35f;
            int over2 = cellAt(x, y);
            if (over2 >= 0 && lv.isLetter(over2) && board[over2] == 0) {
                float cl = gl + (over2 % lv.cols) * cell, ct = gt + (over2 / lv.cols) * cell;
                p.roundRect(cl + 1, ct + 1, cl + cell - 1, ct + cell - 1, cell * 0.16f, 0x804C7DF0);
            }
            tile(p, x - s / 2, y - s / 2, s, players.get(0).rack.get(dragTile), true, false);
        }
    }

    private void drawGrid(Painter p) {
        Level.Word sw = selWord >= 0 && selWord < lv.words.size() ? lv.words.get(selWord) : null;
        card(p, gl - 6 * dp, gt - 6 * dp, gl + cell * lv.cols + 6 * dp, gt + cell * lv.rows + 6 * dp, 16 * dp, BOARD);
        for (int r = 0; r < lv.rows; r++) for (int c = 0; c < lv.cols; c++) {
            int i = r * lv.cols + c;
            float l = gl + c * cell, t = gt + r * cell, rr = l + cell, b = t + cell;
            char g = lv.grid[i];
            float m = Math.max(1, cell * 0.035f);
            if (g == '.') continue;
            if (g == '#') { drawClueCell(p, i, l + m, t + m, rr - m, b - m, sw); continue; }
            boolean inSel = false;
            if (sw != null) for (int k : sw.cells) if (k == i) inSel = true;
            int bgc = inSel ? CELL_SEL : CELL;
            if (hintCell == i) bgc = blend(0xFFFFF4CF, YELLOW, 0.5f + 0.5f * (float) Math.sin(time * 8));
            Float wf = wordFx.get(i);
            float pop = 1;
            if (wf != null && wf >= 0 && wf < 0.45f) {                     // finished word: each letter pops in turn
                pop = 1 + 0.25f * (float) Math.sin(Math.PI * wf / 0.45f);
                bgc = blend(0xFFD5F5E3, bgc, wf / 0.45f);
            }
            float pm = (pop - 1) * cell * 0.25f;
            p.roundRect(l + m - pm, t + m - pm, rr - m + pm, b - m + pm, cell * 0.14f, bgc);
            Float fx0 = cellFx.get(i);
            if (board[i] != 0 && fx0 != null && fx0 < 0) continue;             // its tile is still flying in
            if (board[i] != 0) {
                Float fx = cellFx.get(i);
                float s = (fx == null ? 1 : fx < 0.18f ? 0.75f + fx * 1.4f : 1) * pop;
                Integer owner = cellOwner.get(i);
                int col = given[i] ? INK : owner != null ? darker(players.get(owner).color) : INK;
                if (fx != null && fx < 0.6f) {
                    float a = 1 - fx / 0.6f;
                    p.roundRect(l + m, t + m, rr - m, b - m, cell * 0.14f, ((int) (a * 160)) << 24 | (owner != null ? players.get(owner).color & 0xFFFFFF : 0xFFC93C));
                }
                fit(p, "" + board[i], l + cell / 2, t + cell * 0.7f, cell * 0.8f, cell * 0.58f * s, col, true, 0);
                if (owner != null && !given[i]) p.circle(rr - cell * 0.16f, t + cell * 0.16f, cell * 0.06f, players.get(owner).color);
            }
        }
    }

    static int darker(int c) { return blend(c, 0xFF000000, 0.25f); }

    /** Tiles flying from a rack (or a rival's avatar) to their square. */
    private void drawFlights(Painter p) {
        for (Map.Entry<Integer, float[]> e : flyFrom.entrySet()) {
            Float fx = cellFx.get(e.getKey());
            if (fx == null || fx >= 0) continue;
            float k = 1 + fx / FLY, ease = 1 - (1 - k) * (1 - k);
            int i = e.getKey();
            float tx = gl + (i % lv.cols + 0.5f) * cell, ty = gt + (i / lv.cols + 0.5f) * cell;
            float x = e.getValue()[0] + (tx - e.getValue()[0]) * ease, y = e.getValue()[1] + (ty - e.getValue()[1]) * ease - (float) Math.sin(Math.PI * k) * 30 * dp;
            float sz = cell * (0.75f + 0.25f * ease);
            tile(p, x - sz / 2, y - sz / 2, sz, board[i], true, false);
        }
    }

    /** First level only: a hand shows how to drag a letter to its square, until the first letter is placed. */
    boolean tutorialActive() { return !tutorialDone && !dailyMode && levelIdx == 0 && myTurn() && popup == P_NONE && !touching && placedThisTurn == 0; }

    private void drawTutorial(Painter p) {
        List<Character> rack = players.get(0).rack;
        int bt = -1, bc = -1;
        Level.Word sw = selWord >= 0 ? lv.words.get(selWord) : null;
        for (int t = 0; t < rack.size() && bt < 0; t++) {
            if (sw != null) for (int k : sw.cells) if (board[k] == 0 && lv.grid[k] == rack.get(t)) { bt = t; bc = k; break; }
        }
        for (int t = 0; t < rack.size() && bt < 0; t++)
            for (int i = 0; i < board.length; i++) if (lv.isLetter(i) && board[i] == 0 && lv.grid[i] == rack.get(t)) { bt = t; bc = i; break; }
        if (bt < 0) return;
        float cyc = (time % 2.2f) / 2.2f;
        float k = cyc < 0.15f ? 0 : cyc < 0.7f ? (cyc - 0.15f) / 0.55f : 1;
        float ease = k * k * (3 - 2 * k);
        float x0 = rackX[bt], y0 = rackY[bt], x1 = gl + (bc % lv.cols + 0.5f) * cell, y1 = gt + (bc / lv.cols + 0.5f) * cell;
        float x = x0 + (x1 - x0) * ease, y = y0 + (y1 - y0) * ease;
        int a = cyc > 0.88f ? (int) (255 * (1 - (cyc - 0.88f) / 0.12f)) : 255;
        // target square glows, ghost tile follows the hand
        float cl = gl + (bc % lv.cols) * cell, ct = gt + (bc / lv.cols) * cell;
        p.roundRect(cl + 2, ct + 2, cl + cell - 2, ct + cell - 2, cell * 0.16f, (a / 3) << 24 | (ACCENT & 0xFFFFFF));
        if (k > 0) { float sz = rackSize * 0.9f; p.roundRect(x - sz / 2, y - sz / 2, x + sz / 2, y + sz / 2, sz * 0.22f, (a * 3 / 4) << 24 | 0xFFFFFF); fit(p, "" + rack.get(bt), x, y + sz * 0.21f, sz, sz * 0.58f, (a << 24) | (INK & 0xFFFFFF), true, 0); }
        float hs = 44 * dp;
        p.image("1f446", x - hs * 0.3f, y + 2 * dp, x + hs * 0.7f, y + hs + 2 * dp, a);
        // tip above the rack
        String tip = "Drag the letter to its square";
        float tw = p.textWidth(tip, 14 * dp, true) + 28 * dp, ty = rackY[0] - rackSize * 0.5f - 22 * dp;
        p.roundRect(w / 2 - tw / 2, ty - 16 * dp, w / 2 + tw / 2, ty + 14 * dp, 15 * dp, 0xF0273250);
        fit(p, tip, w / 2, ty + 4 * dp, tw - 16 * dp, 14 * dp, WHITE, true, 0);
    }

    private void drawClueCell(Painter p, int i, float l, float t, float r, float b, Level.Word sel) {
        List<Level.Word> ws = lv.cluesIn(i);
        if (ws.isEmpty()) { p.roundRect(l, t, r, b, cell * 0.14f, CLUE_DONE); return; }
        Level.Word across = null, down = null;
        for (Level.Word wd : ws) { if (wd.across) across = wd; else down = wd; }
        if (across != null && down != null) {
            float mid = (t + b) / 2;
            clueBox(p, across, l, t, r, mid - 0.5f * dp, sel == across);
            clueBox(p, down, l, mid + 0.5f * dp, r, b, sel == down);
        } else clueBox(p, across != null ? across : down, l, t, r, b, sel == ws.get(0));
    }

    private void clueBox(Painter p, Level.Word wd, float l, float t, float r, float b, boolean selected) {
        boolean done = complete(wd);
        int bgc = selected ? CLUE_SEL : done ? CLUE_DONE : CLUE;
        p.roundRect(l, t, r, b, cell * 0.12f, bgc);
        int tc = selected ? WHITE : done ? MUTED : CLUE_INK;
        float bw = r - l, bh = b - t;
        if (wd.picture != null) {
            float s = Math.min(bw, bh) * 0.78f;
            p.image(wd.picture, (l + r) / 2 - s / 2, (t + b) / 2 - s / 2, (l + r) / 2 + s / 2, (t + b) / 2 + s / 2, done ? 150 : 255);
        } else {
            float size = Math.max(7.5f * dp, Math.min(cell * 0.2f, 11 * dp) * (bh < cell * 0.6f ? 0.85f : 1));
            List<String> ls = wrapWords(p, wd.clue, bw - 4 * dp, size);
            while (ls.size() * size * 1.05f > bh - 4 * dp && size > 6.5f * dp) { size *= 0.9f; ls = wrapWords(p, wd.clue, bw - 4 * dp, size); }
            float y0 = (t + b) / 2 - ls.size() * size * 1.02f / 2 + size * 0.82f;
            for (int k = 0; k < ls.size(); k++) {
                String s = ls.get(k);
                float sz = size, tw = p.textWidth(s, size, true);
                if (tw > bw - 3 * dp) sz = size * (bw - 3 * dp) / tw;
                p.smallText(s, (l + r) / 2, y0 + k * size * 1.02f, sz, tc, true, 0);
            }
        }
        // direction arrow
        float a = Math.max(3 * dp, cell * 0.09f);
        if (wd.across) { float cy = (t + b) / 2; q[0] = r + a * 0.2f; q[1] = cy - a; q[2] = r + a * 1.2f; q[3] = cy; q[4] = r + a * 0.2f; q[5] = cy + a; }
        else { float cx = (l + r) / 2; q[0] = cx - a; q[1] = b + a * 0.2f; q[2] = cx; q[3] = b + a * 1.2f; q[4] = cx + a; q[5] = b + a * 0.2f; }
        p.poly(q, 3, selected ? ACCENT : done ? 0xFFCDD4E1 : 0xFF9AA5BD);
    }

    private final Map<String, List<String>> wrapCache = new HashMap<String, List<String>>();

    private List<String> wrapWords(Painter p, String s, float maxW, float size) {
        String key = s + "|" + (int) maxW + "|" + (int) (size * 10);
        List<String> c = wrapCache.get(key);
        if (c != null) return c;
        List<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String t = cur.length() == 0 ? word : cur + " " + word;
            if (p.textWidth(t, size, true) <= maxW || cur.length() == 0) { cur.setLength(0); cur.append(t); }
            else { out.add(cur.toString()); cur.setLength(0); cur.append(word); }
        }
        out.add(cur.toString());
        if (wrapCache.size() > 600) wrapCache.clear();
        wrapCache.put(key, out);
        return out;
    }

    private void drawClueStrip(Painter p, float t, float hh) {
        card(p, 12 * dp, t, w - 12 * dp, t + hh, 16 * dp, CARD);
        Level.Word wd = selWord >= 0 && selWord < lv.words.size() ? lv.words.get(selWord) : null;
        if (over) { fit(p, "Crossword complete!", w / 2, t + hh / 2 + 7 * dp, w - 60 * dp, 19 * dp, GREEN_D, true, 0); return; }
        if (!myTurn()) { fit(p, current().name + " is playing…", w / 2, t + hh / 2 + 6 * dp, w - 60 * dp, 17 * dp, MUTED, true, 0); return; }
        if (wd == null) { fit(p, "Tap a clue to read it", w / 2, t + hh / 2 + 6 * dp, w - 60 * dp, 16 * dp, MUTED, true, 0); return; }
        float x = 26 * dp;
        p.roundRect(x - 6 * dp, t + 8 * dp, x + 34 * dp, t + hh - 8 * dp, 10 * dp, ACCENT_SOFT);
        float ax = x + 14 * dp, ay = t + hh / 2;
        if (wd.across) { q[0] = ax - 7 * dp; q[1] = ay - 7 * dp; q[2] = ax + 8 * dp; q[3] = ay; q[4] = ax - 7 * dp; q[5] = ay + 7 * dp; }
        else { q[0] = ax - 7 * dp; q[1] = ay - 6 * dp; q[2] = ax + 7 * dp; q[3] = ay - 6 * dp; q[4] = ax; q[5] = ay + 8 * dp; }
        p.poly(q, 3, ACCENT);
        float tx = x + 46 * dp;
        String s = wd.picture != null ? "Picture clue" : wd.clue;
        if (wd.picture != null) p.image(wd.picture, tx, t + 6 * dp, tx + hh - 12 * dp, t + hh - 6 * dp, 255);
        if (wd.picture != null) tx += hh;
        fit(p, s, tx, t + hh / 2 + 3 * dp, w - tx - 24 * dp, 17 * dp, INK, true, 1);
        fit(p, wd.text.length() + " letters", tx, t + hh / 2 + 19 * dp, w - tx - 24 * dp, 11.5f * dp, MUTED, false, 1);
    }

    private void drawRack(Painter p, float t, float hh) {
        List<Character> rack = players.get(0).rack;
        boolean mine = myTurn();
        float side = 64 * dp;
        // hint (left) and done (right)
        set(bHint, 10 * dp, t + 8 * dp, 10 * dp + side - 8 * dp, t + hh - 6 * dp);
        card(p, bHint[0], bHint[1], bHint[2], bHint[3], 16 * dp, mine ? CARD : 0xFFEEF1F6);
        bulb(p, (bHint[0] + bHint[2]) / 2, t + hh / 2 - 6 * dp, 12 * dp, mine ? YELLOW : 0xFFE3D6AE);
        p.circle(bHint[2] - 4 * dp, bHint[1] + 4 * dp, 10 * dp, hints > 0 ? RED : GREEN);
        fit(p, hints > 0 ? "" + hints : "+", bHint[2] - 4 * dp, bHint[1] + 8.5f * dp, 16 * dp, 11 * dp, WHITE, true, 0);
        fit(p, "Hint", (bHint[0] + bHint[2]) / 2, bHint[3] - 9 * dp, side, 11 * dp, mine ? INK : MUTED, true, 0);
        set(bDone, w - 10 * dp - side + 8 * dp, t + 8 * dp, w - 10 * dp, t + hh - 6 * dp);
        if (mine) p.roundRect(bDone[0], bDone[1] + 3 * dp, bDone[2], bDone[3] + 3 * dp, 16 * dp, ACCENT_D);
        p.roundRect(bDone[0], bDone[1], bDone[2], bDone[3], 16 * dp, mine ? ACCENT : 0xFFC9D6F3);
        float dx = (bDone[0] + bDone[2]) / 2, dyy = t + hh / 2 - 6 * dp;
        line(p, dx - 9 * dp, dyy, dx - 2 * dp, dyy + 7 * dp, 3.2f * dp, WHITE);
        line(p, dx - 2 * dp, dyy + 7 * dp, dx + 10 * dp, dyy - 7 * dp, 3.2f * dp, WHITE);
        fit(p, placedThisTurn > 0 ? "Done" : "Pass", dx, bDone[3] - 9 * dp, side, 11 * dp, WHITE, true, 0);
        // tiles
        float area = w - 2 * (10 * dp + side) - 12 * dp;
        rackSize = Math.min(hh - 8 * dp, (area - 4 * 6 * dp) / RACK);
        float total = RACK * rackSize + (RACK - 1) * 6 * dp;
        float x0 = (w - total) / 2;
        for (int k = 0; k < RACK; k++) {
            float l = x0 + k * (rackSize + 6 * dp), tt = t + (hh - rackSize) / 2;
            rackX[k] = l + rackSize / 2; rackY[k] = tt + rackSize / 2;
            if (k >= rack.size()) { p.roundRect(l, tt, l + rackSize, tt + rackSize, rackSize * 0.2f, 0xFFE6EAF2); continue; }
            if (k == dragTile && moved) { p.roundRect(l, tt, l + rackSize, tt + rackSize, rackSize * 0.2f, 0xFFE6EAF2); continue; }
            float off = selTile == k ? -8 * dp : 0;
            if (wrongT > 0 && shakeTile == k) off += (float) Math.sin(wrongT * 60) * 3 * dp;
            tile(p, l, tt + off, rackSize, rack.get(k), mine, hintTile == k);
            if (selTile == k && mine) p.circle(l + rackSize / 2, tt + rackSize + 6 * dp, 2.5f * dp, ACCENT);
        }

    }

    private void tile(Painter p, float l, float t, float s, char ch, boolean active, boolean glow) {
        if (glow) p.glow(l + s / 2, t + s / 2, s, 0x99FFC93C);
        p.roundRect(l, t + s * 0.07f, l + s, t + s * 1.07f, s * 0.22f, active ? TILE_SHADE : 0xFFE3E7EF);
        p.roundRect(l, t, l + s, t + s, s * 0.22f, active ? CARD : 0xFFF2F4F8);
        fit(p, "" + ch, l + s / 2, t + s * 0.71f, s * 0.8f, s * 0.58f, active ? INK : 0xFFAEB6C8, true, 0);
    }

    static int blend(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    // =====================================================================================
    // popups

    private void drawPopup(Painter p) {
        float a = Math.min(1, popupAge / 0.18f);
        p.roundRect(0, 0, w, h, 0, ((int) (a * 0x73)) << 24 | 0x1B2540);
        float pw = Math.min(w - 32 * dp, 360 * dp);
        float l = (w - pw) / 2, r = l + pw, cx = w / 2;
        switch (popup) {
            case P_RESULT: drawResult(p, l, r, cx); break;
            case P_SETTINGS: drawSettings(p, l, r, cx); break;
            case P_SHOP: drawShop(p, l, r, cx); break;
            case P_HELP: drawHelp(p, l, r, cx); break;
            case P_PAUSE: drawPause(p, l, r, cx); break;
            case P_REWARDS: drawRewards(p, l, r, cx); break;
            case P_REMIND: drawRemind(p, l, r, cx); break;
            default: break;
        }
    }

    private void popCard(Painter p, float l, float r, float t, float ht) {
        p.roundRect(l, t + 4 * dp, r, t + ht + 6 * dp, 26 * dp, 0x22000000);
        p.roundRect(l, t, r, t + ht, 26 * dp, CARD);
    }

    private void closeX(Painter p, float r, float t) {
        set(bClose, r - 50 * dp, t + 6 * dp, r - 6 * dp, t + 50 * dp);
        float cx = r - 28 * dp, cy = t + 28 * dp;
        p.circle(cx, cy, 16 * dp, PAPER);
        line(p, cx - 6 * dp, cy - 6 * dp, cx + 6 * dp, cy + 6 * dp, 2.4f * dp, MUTED);
        line(p, cx + 6 * dp, cy - 6 * dp, cx - 6 * dp, cy + 6 * dp, 2.4f * dp, MUTED);
    }

    private void drawResult(Painter p, float l, float r, float cx) {
        int np = players.size();
        float ht = 98 * dp + np * 52 * dp + 4 * dp + (dailyMode ? 36 * dp : 0) + 50 * dp + 38 * dp + 50 * dp + (dailyMode ? 24 * dp : 58 * dp);
        float t = Math.max(insetTop + 10 * dp, (h - ht) / 2);
        // the card springs in
        float sc = popupAge < 0.25f ? 0.9f + 0.1f * (float) Math.sin(popupAge / 0.25f * Math.PI / 2) : 1;
        float pad = (r - l) * (1 - sc) / 2;
        l += pad; r -= pad; t += ht * (1 - sc) / 2;
        popCard(p, l, r, t, ht);
        Player me = players.get(0);
        int rank = rank(me);
        String title = rank == 1 ? "You won!" : rank == 2 ? "2nd place!" : rank == 3 ? "3rd place" : "Good game!";
        if (dailyMode) title = rank == 1 ? "Daily Puzzle won!" : "Daily Puzzle done!";
        if (rank == 1) for (int k = 0; k < 3; k++) { float sx = cx + (k - 1) * 30 * dp, sy = t + 30 * dp - (k == 1 ? 8 * dp : 0); star(p, sx, sy, (k == 1 ? 14 * dp : 11 * dp) * Math.min(1, popupAge * 3 - k * 0.3f + 0.3f), YELLOW); }
        fit(p, title, cx, t + 76 * dp, r - l - 40 * dp, 28 * dp, rank == 1 ? GREEN_D : INK, true, 0);
        // ranking
        List<Player> order = new ArrayList<Player>(players);
        Collections.sort(order, new java.util.Comparator<Player>() { public int compare(Player x, Player y) { return y.score - x.score; } });
        float y = t + 98 * dp;
        for (int k = 0; k < order.size(); k++) {
            Player pl = order.get(k);
            boolean isMe = pl == me;
            p.roundRect(l + 18 * dp, y, r - 18 * dp, y + 44 * dp, 14 * dp, isMe ? 0xFFE8F8F0 : PAPER);
            fit(p, (k + 1) + ".", l + 38 * dp, y + 28 * dp, 24 * dp, 16 * dp, MUTED, true, 0);
            avatar(p, pl, l + 72 * dp, y + 22 * dp, 15 * dp, false);
            fit(p, pl.name, l + 96 * dp, y + 28 * dp, r - l - 180 * dp, 16 * dp, INK, true, 1);
            fit(p, pl.score + " pts", r - 32 * dp, y + 28 * dp, 80 * dp, 16 * dp, pl.color == PLAYER_COLOR[0] ? GREEN_D : INK, true, 2);
            y += 52 * dp;
        }
        y += 4 * dp;
        if (dailyMode) {                                     // streak
            p.image("1f525", cx - 70 * dp, y, cx - 44 * dp, y + 26 * dp, 255);
            fit(p, streak + (streak == 1 ? "-day streak" : "-day streak!"), cx - 38 * dp, y + 19 * dp, r - l - 120 * dp, 17 * dp, 0xFFF26B4F, true, 1);
            y += 36 * dp;
        }
        // XP bar filling up
        float k = Math.min(1, Math.max(0, (popupAge - 0.3f) / 0.9f));
        int shown = xp - xpGained + Math.round(xpGained * k);
        int ri = rankIndex(shown);
        p.circle(l + 40 * dp, y + 18 * dp, 16 * dp, ACCENT_SOFT);
        p.image(RANK_ICONS[ri], l + 28 * dp, y + 6 * dp, l + 52 * dp, y + 30 * dp, 255);
        float bl = l + 64 * dp, br = r - 24 * dp;
        boolean up = rankUp && k >= 1;
        fit(p, up ? "Rank up! " + RANKS[ri] : RANKS[ri], bl, y + 12 * dp, (br - bl) * 0.62f, 13 * dp, up ? ACCENT_D : INK, true, 1);
        fit(p, "+" + Math.round(xpGained * k) + " XP", br, y + 12 * dp, (br - bl) * 0.35f, 13 * dp, ACCENT, true, 2);
        p.roundRect(bl, y + 20 * dp, br, y + 29 * dp, 4.5f * dp, BOARD);
        float fr = rankProgress(shown);
        if (fr > 0) p.roundRect(bl, y + 20 * dp, bl + Math.max(9 * dp, (br - bl) * fr), y + 29 * dp, 4.5f * dp, ACCENT);
        y += 50 * dp;
        coinIcon(p, cx - 40 * dp, y + 14 * dp, 12 * dp);
        fit(p, "+" + (doubled ? rewardCoins * 2 : rewardCoins), cx - 22 * dp, y + 21 * dp, 90 * dp, 20 * dp, INK, true, 1);
        y += 38 * dp;
        float half = (r - l - 52 * dp) / 2;
        if (!doubled) button(p, bB, l + 18 * dp, y, l + 18 * dp + half, y + 50 * dp, "\u25B6 Coins x2", 15 * dp, YELLOW, YELLOW_D, INK);
        button(p, bA, doubled ? l + 18 * dp : r - 18 * dp - half, y, r - 18 * dp, y + 50 * dp, dailyMode ? "Continue" : "Next level", 16 * dp, ACCENT, ACCENT_D, WHITE);
        if (!dailyMode) {
            set(bC, cx - 60 * dp, y + 58 * dp, cx + 60 * dp, y + 90 * dp);
            fit(p, "Home", cx, y + 80 * dp, 100 * dp, 14 * dp, MUTED, true, 0);
        }
    }

    private void drawRewards(Painter p, float l, float r, float cx) {
        float ht = 420 * dp, t = Math.max(insetTop + 10 * dp, (h - ht) / 2);
        popCard(p, l, r, t, ht);
        closeX(p, r, t);
        fit(p, "Daily Reward", cx, t + 46 * dp, r - l - 100 * dp, 23 * dp, INK, true, 0);
        fit(p, "Come back every day for bigger gifts", cx, t + 70 * dp, r - l - 40 * dp, 13 * dp, MUTED, true, 0);
        int slot = rewardSlot();
        boolean ready = rewardReady();
        float gx = l + 18 * dp, gw = r - l - 36 * dp, gap = 8 * dp, cw = (gw - 3 * gap) / 4, ch = 92 * dp;
        for (int d = 0; d < 7; d++) {
            int row = d < 4 ? 0 : 1, col = d < 4 ? d : d - 4;
            float cl = gx + col * (cw + gap), ct = t + 92 * dp + row * (ch + gap), cr = d == 6 ? gx + gw : cl + cw, cb = ct + ch;
            boolean past = d < slot, now = d == slot && ready;
            if (now) {
                float pulse = 1.5f * dp * (float) Math.abs(Math.sin(time * 3));
                p.roundRect(cl - 2 * dp - pulse, ct - 2 * dp - pulse, cr + 2 * dp + pulse, cb + 2 * dp + pulse, 16 * dp, ACCENT);
            }
            p.roundRect(cl, ct, cr, cb, 14 * dp, now ? CARD : past ? 0xFFE8F8F0 : PAPER);
            fit(p, "Day " + (d + 1), (cl + cr) / 2, ct + 20 * dp, cr - cl - 8 * dp, 12 * dp, now ? ACCENT_D : MUTED, true, 0);
            float icy = ct + 48 * dp, icx = (cl + cr) / 2;
            if (d == 6) p.image("1f381", icx - 20 * dp, icy - 20 * dp, icx + 20 * dp, icy + 20 * dp, past ? 120 : 255);
            else if (REWARDS[d][0] > 0) coinIcon(p, icx, icy, 14 * dp);
            else bulb(p, icx, icy - 2 * dp, 16 * dp, YELLOW);
            String amt = REWARDS[d][0] > 0 ? "" + REWARDS[d][0] : "";
            if (REWARDS[d][1] > 0) amt += (amt.length() > 0 ? " + " : "") + REWARDS[d][1] + (REWARDS[d][1] == 1 ? " hint" : " hints");
            fit(p, amt, icx, ct + 82 * dp, cr - cl - 8 * dp, 13 * dp, INK, true, 0);
            if (past) {
                p.circle(cr - 12 * dp, ct + 12 * dp, 8 * dp, GREEN);
                line(p, cr - 16 * dp, ct + 12 * dp, cr - 13 * dp, ct + 15 * dp, 2 * dp, WHITE);
                line(p, cr - 13 * dp, ct + 15 * dp, cr - 8 * dp, ct + 9 * dp, 2 * dp, WHITE);
            }
        }
        float by = t + ht - 116 * dp;
        if (ready) {
            button(p, bA, l + 22 * dp, by, r - 22 * dp, by + 48 * dp, "Claim", 17 * dp, ACCENT, ACCENT_D, WHITE);
            button(p, bB, l + 22 * dp, by + 58 * dp, r - 22 * dp, by + 102 * dp, "\u25B6 Claim x2 (watch video)", 15 * dp, YELLOW, YELLOW_D, INK);
        } else fit(p, "Come back tomorrow for Day " + (slot + 1), cx, by + 40 * dp, r - l - 40 * dp, 15 * dp, MUTED, true, 0);
    }

    private void drawRemind(Painter p, float l, float r, float cx) {
        float ht = 300 * dp, t = (h - ht) / 2;
        popCard(p, l, r, t, ht);
        closeX(p, r, t);
        p.circle(cx, t + 58 * dp, 32 * dp, 0xFFFFEFEA);
        p.image("1f514", cx - 22 * dp, t + 36 * dp, cx + 22 * dp, t + 80 * dp, 255);
        fit(p, "Keep your streak!", cx, t + 124 * dp, r - l - 40 * dp, 22 * dp, INK, true, 0);
        fit(p, "Get a gentle reminder each evening", cx, t + 150 * dp, r - l - 40 * dp, 14 * dp, MUTED, true, 0);
        fit(p, "when the new Daily Puzzle is ready.", cx, t + 170 * dp, r - l - 40 * dp, 14 * dp, MUTED, true, 0);
        button(p, bA, l + 24 * dp, t + 192 * dp, r - 24 * dp, t + 242 * dp, "Remind me", 17 * dp, ACCENT, ACCENT_D, WHITE);
        set(bB, cx - 70 * dp, t + 250 * dp, cx + 70 * dp, t + 290 * dp);
        fit(p, "Not now", cx, t + 276 * dp, 120 * dp, 14 * dp, MUTED, true, 0);
    }

    private void star(Painter p, float cx, float cy, float s, int color) {
        float[] pts = new float[20];
        for (int k = 0; k < 10; k++) { double a = -Math.PI / 2 + k * Math.PI / 5; float rr = k % 2 == 0 ? s : s * 0.45f; pts[k * 2] = cx + (float) Math.cos(a) * rr; pts[k * 2 + 1] = cy + (float) Math.sin(a) * rr; }
        p.poly(pts, 10, color);
    }

    private void drawSettings(Painter p, float l, float r, float cx) {
        boolean priv = host.privacyOptionsRequired();
        String[] labels = {"Sound", "Vibration", "Daily reminder", "How to play", "Remove ads", "Restore purchases", "Privacy options"};
        int rows = priv ? 7 : 6;
        float rowH = 48 * dp, ht = 76 * dp + rows * rowH + 68 * dp, t = Math.max(insetTop + 10 * dp, (h - ht) / 2);
        popCard(p, l, r, t, ht);
        closeX(p, r, t);
        p.text("Settings", cx, t + 42 * dp, 21 * dp, INK, true, 0);
        float y = t + 64 * dp;
        for (int i = 0; i < rows; i++) {
            set(bSet[i], l + 14 * dp, y, r - 14 * dp, y + rowH - 6 * dp);
            p.roundRect(l + 14 * dp, y, r - 14 * dp, y + rowH - 6 * dp, 12 * dp, PAPER);
            fit(p, labels[i], l + 28 * dp, y + 27 * dp, (r - l) * 0.6f, 15 * dp, INK, true, 1);
            if (i <= 2) {
                boolean on = i == 0 ? soundOn : i == 1 ? vibeOn : reminderOn;
                float tx = r - 72 * dp, ty = y + 8 * dp;
                p.roundRect(tx, ty, tx + 46 * dp, ty + 26 * dp, 13 * dp, on ? ACCENT : 0xFFD3D9E5);
                p.circle(on ? tx + 33 * dp : tx + 13 * dp, ty + 13 * dp, 10 * dp, WHITE);
            } else {
                String np = host.price(NOADS_ID);
                String v = i == 4 ? (noAds ? "Done ✓" : np != null ? np : "›") : "›";
                fit(p, v, r - 30 * dp, y + 27 * dp, (r - l) * 0.3f, 14 * dp, CLUE_D, true, 2);
            }
            y += rowH;
        }
        p.text("Crossword Rivals · v1.0", cx, t + ht - 48 * dp, 11 * dp, MUTED, true, 0);
        fit(p, "Pictures: Noto Emoji by Google (Apache 2.0)", cx, t + ht - 31 * dp, r - l - 24 * dp, 11 * dp, MUTED, false, 0);
        fit(p, "Font: Nunito (SIL Open Font License)", cx, t + ht - 15 * dp, r - l - 24 * dp, 11 * dp, MUTED, false, 0);
    }

    private void drawShop(Painter p, float l, float r, float cx) {
        float ht = 380 * dp, t = (h - ht) / 2;
        popCard(p, l, r, t, ht);
        closeX(p, r, t);
        p.glow(cx, t + 62 * dp, 46 * dp, 0x66FFC93C);
        bulb(p, cx, t + 60 * dp, 26 * dp, YELLOW);
        fit(p, "Get hints", cx, t + 130 * dp, r - l - 40 * dp, 23 * dp, INK, true, 0);
        fit(p, "A hint shows where one of your letters goes", cx, t + 154 * dp, r - l - 40 * dp, 13 * dp, MUTED, true, 0);
        fit(p, "You have " + hints + " hints and " + coins + " coins", cx, t + 176 * dp, r - l - 40 * dp, 13 * dp, INK, true, 0);
        button(p, bA, l + 22 * dp, t + 194 * dp, r - 22 * dp, t + 244 * dp, "\u25B6 Free hint (watch video)", 16 * dp, ACCENT, ACCENT_D, WHITE);
        button(p, bB, l + 22 * dp, t + 256 * dp, r - 22 * dp, t + 306 * dp, "1 hint for " + HINT_COST + " coins", 16 * dp, YELLOW, YELLOW_D, INK);
        String price = host.price(HINTS_ID);
        button(p, bC, l + 22 * dp, t + 318 * dp, r - 22 * dp, t + 364 * dp, PACK_HINTS + " hints" + (price != null ? "  ·  " + price : ""), 15 * dp, PAPER, 0xFFDDE3EE, INK);
    }

    private void drawHelp(Painter p, float l, float r, float cx) {
        float body = 0;
        for (String[] s : HELP) body += Math.max(42 * dp, wrapWords(p, s[1], r - l - 86 * dp, 14 * dp).size() * 20 * dp + 18 * dp);
        float ht = 70 * dp + body + 90 * dp, t = Math.max(insetTop + 10 * dp, (h - ht) / 2);
        popCard(p, l, r, t, ht);
        closeX(p, r, t);
        fit(p, "How to play", cx, t + 44 * dp, r - l - 90 * dp, 23 * dp, INK, true, 0);
        String[][] steps = HELP;
        float y = t + 70 * dp;
        for (String[] s : steps) {
            p.circle(l + 36 * dp, y + 14 * dp, 14 * dp, ACCENT);
            fit(p, s[0], l + 36 * dp, y + 20 * dp, 20 * dp, 15 * dp, WHITE, true, 0);
            List<String> ls = wrapWords(p, s[1], r - l - 86 * dp, 14 * dp);
            for (int k = 0; k < ls.size(); k++) p.text(ls.get(k), l + 62 * dp, y + 18 * dp + k * 20 * dp, 14 * dp, INK, true, 1);
            y += Math.max(42 * dp, ls.size() * 20 * dp + 18 * dp);
        }
        button(p, bA, l + 24 * dp, t + ht - 72 * dp, r - 24 * dp, t + ht - 22 * dp, "Let's play!", 17 * dp, ACCENT, ACCENT_D, WHITE);
    }

    static final String[][] HELP = {
            {"1", "Blue squares are clues. The arrow shows where the answer goes. Some clues are pictures."},
            {"2", "On your turn, drag a letter tile (or tap it, then tap a square) to where it belongs."},
            {"3", "Right square: +1 point. Finish a word to win points for every letter in it. A wrong square ends your turn."},
            {"4", "Tap Done to pass. Highest score when the crossword is full wins!"}};

    private void drawPause(Painter p, float l, float r, float cx) {
        float ht = 240 * dp, t = (h - ht) / 2;
        popCard(p, l, r, t, ht);
        fit(p, "Leave this game?", cx, t + 54 * dp, r - l - 40 * dp, 22 * dp, INK, true, 0);
        fit(p, "This crossword will start again next time", cx, t + 80 * dp, r - l - 40 * dp, 13 * dp, MUTED, true, 0);
        button(p, bA, l + 24 * dp, t + 104 * dp, r - 24 * dp, t + 154 * dp, "Keep playing", 17 * dp, ACCENT, ACCENT_D, WHITE);
        button(p, bB, l + 24 * dp, t + 168 * dp, r - 24 * dp, t + 214 * dp, "Leave", 15 * dp, PAPER, 0xFFDDE3EE, INK);
    }

    private void drawToast(Painter p) {
        float a = Math.min(1, toastT / 0.3f);
        float tw = Math.min(w - 32 * dp, p.textWidth(toast, 14.5f * dp, true) + 36 * dp);
        float cy = popup != P_NONE ? insetTop + 40 * dp : screen == HOME ? homeToastY : h - insetBottom - 120 * dp;
        p.roundRect(w / 2 - tw / 2, cy - 21 * dp, w / 2 + tw / 2, cy + 21 * dp, 21 * dp, ((int) (a * 0xEE)) << 24 | 0x1F2A44);
        fit(p, toast, w / 2, cy + 5 * dp, tw - 24 * dp, 14.5f * dp, ((int) (a * 255)) << 24 | 0xFFFFFF, true, 0);
    }

    // ---- test hooks
    float[] rect(String n) {
        switch (n) {
            case "play": return bPlay; case "gear": return bGear; case "shop": return bShopHome; case "back": return bBack; case "hint": return bHint; case "done": return bDone;
            case "a": return bA; case "b": return bB; case "c": return bC; case "close": return bClose; case "help": return bHelp; case "daily": return bDaily; case "gift": return bGift;
            default: if (n.startsWith("set")) return bSet[Integer.parseInt(n.substring(3))]; return null;
        }
    }
    float rackSizeForTest() { return rackSize; }
    float[] tileCenter(int t) { return new float[]{rackX[t], rackY[t]}; }
    float[] cellCenter(int i) { return new float[]{gl + (i % lv.cols + 0.5f) * cell, gt + (i / lv.cols + 0.5f) * cell}; }
}
