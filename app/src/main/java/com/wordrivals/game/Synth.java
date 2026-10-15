package com.wordrivals.game;

/** Game sounds made from maths (no audio files). */
final class Synth {
    static final int RATE = 22050;
    static final int CLICK = 0, PLACE = 1, BOT = 2, WORD = 3, WRONG = 4, WIN = 5, HINT = 6, COUNT = 7;

    static byte[] wav(int id) {
        float[] s = samples(id);
        byte[] out = new byte[44 + s.length * 2];
        int dataLen = s.length * 2;
        put(out, 0, "RIFF"); le32(out, 4, 36 + dataLen); put(out, 8, "WAVE"); put(out, 12, "fmt ");
        le32(out, 16, 16); le16(out, 20, 1); le16(out, 22, 1); le32(out, 24, RATE); le32(out, 28, RATE * 2);
        le16(out, 32, 2); le16(out, 34, 16); put(out, 36, "data"); le32(out, 40, dataLen);
        for (int i = 0; i < s.length; i++) le16(out, 44 + i * 2, (int) (Math.max(-1f, Math.min(1f, s[i])) * 32000));
        return out;
    }

    static double duration(int id) {
        switch (id) {
            case CLICK: return 0.05;
            case PLACE: return 0.09;
            case BOT: return 0.07;
            case WORD: return 0.5;
            case WRONG: return 0.32;
            case WIN: return 1.6;
            case HINT: return 0.7;
            default: return 0.1;
        }
    }

    static float[] samples(int id) {
        double dur = duration(id);
        int n = (int) (dur * RATE);
        float[] s = new float[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE, v;
            switch (id) {
                case CLICK: v = Math.sin(2 * Math.PI * 1400 * t) * Math.exp(-80 * t) * 0.3; break;
                case PLACE:   // soft wooden "tock"
                    v = (Math.sin(2 * Math.PI * 520 * t) * 0.6 + Math.sin(2 * Math.PI * 1040 * t) * 0.2) * Math.exp(-45 * t) * 0.5;
                    break;
                case BOT: v = Math.sin(2 * Math.PI * 1900 * t) * Math.exp(-70 * t) * 0.22; break;
                case WORD: {
                    double[] f = {783.99, 1046.5};
                    int k = t < 0.09 ? 0 : 1;
                    double tt = t - k * 0.09;
                    v = (Math.sin(2 * Math.PI * f[k] * tt) + 0.3 * Math.sin(2 * Math.PI * f[k] * 2 * tt)) * Math.exp(-7 * tt) * 0.22;
                    break;
                }
                case WRONG:
                    v = Math.sin(2 * Math.PI * (190 - 80 * t) * t) * Math.exp(-9 * t) * 0.45;
                    break;
                case WIN: {
                    double[] f = {523.25, 659.25, 783.99, 1046.5, 1318.5, 1567.98};
                    v = 0;
                    for (int k = 0; k < f.length; k++) {
                        double st = k * 0.11;
                        if (t < st) continue;
                        double tt = t - st;
                        v += (Math.sin(2 * Math.PI * f[k] * tt) + 0.25 * Math.sin(2 * Math.PI * f[k] * 2 * tt)) * Math.exp(-3.2 * tt) * 0.13;
                    }
                    break;
                }
                case HINT: {
                    double[] f = {1318.5, 1760, 2093};
                    int k = Math.min(2, (int) (t / 0.08));
                    v = Math.sin(2 * Math.PI * f[k] * t) * Math.exp(-6 * (t - k * 0.08)) * 0.2;
                    break;
                }
                default: v = 0;
            }
            double edge = Math.min(1.0, t / 0.003) * Math.min(1.0, (dur - t) / 0.02);
            s[i] = (float) (v * edge);
        }
        return s;
    }

    private static void put(byte[] b, int at, String s) { for (int i = 0; i < s.length(); i++) b[at + i] = (byte) s.charAt(i); }
    private static void le16(byte[] b, int at, int v) { b[at] = (byte) v; b[at + 1] = (byte) (v >> 8); }
    private static void le32(byte[] b, int at, int v) { le16(b, at, v); le16(b, at + 2, v >> 16); }
}
