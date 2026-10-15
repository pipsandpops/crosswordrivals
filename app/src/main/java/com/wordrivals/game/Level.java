package com.wordrivals.game;

import java.util.ArrayList;
import java.util.List;

/** One crossword: grid (letters, clue cells, blanks) and its words with clues. */
final class Level {
    static final class Word {
        int r, c;
        boolean across;
        String text, clue, picture;
        int clueCell;
        int[] cells;
    }

    final int cols, rows;
    final char[] grid;              // 'A'..'Z' letter, '#' clue cell, '.' blank
    final List<Word> words = new ArrayList<Word>();

    Level(int cols, int rows, char[] grid) { this.cols = cols; this.rows = rows; this.grid = grid; }

    boolean isLetter(int i) { return grid[i] >= 'A' && grid[i] <= 'Z'; }

    /** Words through a cell (0, 1 or 2). */
    List<Word> wordsAt(int cell) {
        List<Word> out = new ArrayList<Word>();
        for (Word w : words) for (int k : w.cells) if (k == cell) { out.add(w); break; }
        return out;
    }

    /** Words whose clue sits in this cell. */
    List<Word> cluesIn(int cell) {
        List<Word> out = new ArrayList<Word>();
        for (Word w : words) if (w.clueCell == cell) out.add(w);
        return out;
    }

    int letterCount() { int n = 0; for (int i = 0; i < grid.length; i++) if (isLetter(i)) n++; return n; }

    static Level parse(String line) {
        String[] p = line.split("\\|");
        String[] hdr = p[0].trim().split(" ");
        int cols = Integer.parseInt(hdr[0]), rows = Integer.parseInt(hdr[1]);
        String[] gr = p[1].split("/");
        char[] g = new char[cols * rows];
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) g[r * cols + c] = gr[r].charAt(c);
        Level lv = new Level(cols, rows, g);
        for (int i = 2; i < p.length; i++) {
            String[] f = p[i].split("\\^", -1);
            Word w = new Word();
            w.r = Integer.parseInt(f[0]); w.c = Integer.parseInt(f[1]);
            w.across = f[2].equals("R");
            w.text = f[3]; w.clue = f[4]; w.picture = f.length > 5 && f[5].length() > 0 ? f[5] : null;
            w.cells = new int[w.text.length()];
            for (int k = 0; k < w.cells.length; k++) w.cells[k] = (w.r + (w.across ? 0 : k)) * cols + w.c + (w.across ? k : 0);
            w.clueCell = w.across ? w.r * cols + w.c - 1 : (w.r - 1) * cols + w.c;
            lv.words.add(w);
        }
        return lv;
    }

    static List<String> lines(String text) {
        List<String> out = new ArrayList<String>();
        if (text == null) return out;
        for (String l : text.split("\n")) if (l.trim().length() > 0 && !l.startsWith("#")) out.add(l.trim());
        return out;
    }
}
