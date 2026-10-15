"""Builds Scandinavian-style crossword levels (clues inside the grid).
Usage: python3 gen_levels.py OUT_levels.txt COUNT [SEED] [daily] [ATTEMPTS]
Line format: cols rows|grid|words   grid: A-Z letter, # clue cell, . blank (row by row)
words: r^c^dir^WORD^clue^emoji joined by | (dir R = across, D = down; clue cell is left of / above the word)."""
import sys, random, glob, os
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
WORDS = {}
for f in sorted(glob.glob(os.path.join(HERE, "words*.txt"))):
    for line in open(f):
        line = line.strip()
        if not line or line.startswith("#"): continue
        w, c, e, t = line.split("|")
        if w not in WORDS: WORDS[w] = (c, e, int(t))

def build_index(words):
    idx = defaultdict(set); bylen = defaultdict(set)
    for w in words:
        bylen[len(w)].add(w)
        for i, ch in enumerate(w): idx[(len(w), i, ch)].add(w)
    return idx, bylen

def generate(cols, rows, pool, rnd, usage):
    idx, bylen = build_index(pool)
    grid = [[None] * cols for _ in range(rows)]      # None empty, "!" stop, letter, or dict of clues
    placed = []
    maxL = max(cols, rows) - 1
    def cell(r, c): return grid[r][c] if 0 <= r < rows and 0 <= c < cols else "OUT"
    def is_letter(v): return isinstance(v, str) and len(v) == 1 and v != "!"
    def try_slot(r, c, d, L, first):
        dr, dc = (0, 1) if d == "R" else (1, 0)
        cr, cc = r - dr, c - dc
        cv = cell(cr, cc)
        if cv == "OUT" or is_letter(cv): return None
        if isinstance(cv, dict) and d in cv: return None
        er, ec = r + dr * L, c + dc * L
        ev = cell(er, ec)
        if ev != "OUT" and is_letter(ev): return None
        pattern = []; cross = 0
        for k in range(L):
            v = cell(r + dr * k, c + dc * k)
            if v == "OUT" or v == "!" or isinstance(v, dict): return None
            if is_letter(v): pattern.append(v); cross += 1
            else:
                # a new letter must not touch letters on its sides
                for sr, sc in ((r + dr * k + dc, c + dc * k + dr), (r + dr * k - dc, c + dc * k - dr)):
                    if is_letter(cell(sr, sc)): return None
                pattern.append(None)
        if not first and (cross == 0 or cross == L): return None
        cands = None
        for k, ch in enumerate(pattern):
            if ch is None: continue
            s = idx.get((L, k, ch), set())
            cands = s if cands is None else cands & s
            if not cands: return None
        if cands is None: cands = bylen.get(L, set())
        cands = [w for w in cands if w not in used]
        if not cands: return None
        return cands, cross
    used = set()
    first = True
    while True:
        best = None
        for r in range(rows):
            for c in range(cols):
                for d in "RD":
                    for L in range(3, maxL + 1):
                        res = try_slot(r, c, d, L, first)
                        if not res: continue
                        cands, cross = res
                        w = min(cands, key=lambda x: usage.get(x, 0) * 3 + rnd.random() * 4)
                        score = cross * 10 + L * 1.6 - usage.get(w, 0) * 2 + rnd.random() * 6
                        if first: score = rnd.random() + (6 if (r <= 2 and d == "R") or (c <= 2 and d == "D") else 0) + L
                        if best is None or score > best[0]: best = (score, r, c, d, L, w)
        if not best: break
        _, r, c, d, L, w = best
        dr, dc = (0, 1) if d == "R" else (1, 0)
        cr, cc = r - dr, c - dc
        cv = grid[cr][cc]
        if not isinstance(cv, dict): grid[cr][cc] = {}
        grid[cr][cc][d] = len(placed)
        for k in range(L): grid[r + dr * k][c + dc * k] = w[k]
        er, ec = r + dr * L, c + dc * L
        if 0 <= er < rows and 0 <= ec < cols and grid[er][ec] is None: grid[er][ec] = "!"
        placed.append((r, c, d, w))
        used.add(w)
        first = False
    letters = sum(1 for row in grid for v in row if is_letter(v))
    return grid, placed, letters

def encode(grid, placed, cols, rows, rnd, pic_rate):
    out = []
    for row in grid:
        s = ""
        for v in row:
            if isinstance(v, dict): s += "#"
            elif isinstance(v, str) and len(v) == 1 and v != "!": s += v
            else: s += "."
        out.append(s)
    ws = []
    for (r, c, d, w) in placed:
        clue, emo, _ = WORDS[w]
        if emo and rnd.random() >= pic_rate: emo = ""
        ws.append("^".join([str(r), str(c), d, w, clue, emo]))
    return "%d %d|%s|%s" % (cols, rows, "/".join(out), "|".join(ws))

def main():
    out, count = sys.argv[1], int(sys.argv[2]); seed = int(sys.argv[3]) if len(sys.argv) > 3 else 7
    daily = len(sys.argv) > 4 and sys.argv[4] == "daily"
    attempts = int(sys.argv[5]) if len(sys.argv) > 5 else 24
    shares = []
    rnd = random.Random(seed)
    usage = {}
    tier1 = [w for w, v in WORDS.items() if v[2] == 1]
    every = list(WORDS)
    lines = []
    for lv in range(count):
        cols, rows = (6, 7) if lv < 10 else (7, 8) if lv < 40 else (7, 9)
        pool = tier1 if lv < 30 else every
        pic_rate = 0.5 if lv < 20 else 0.3
        if daily: cols, rows, pool, pic_rate = 7, 9, every, 0.35
        best = None
        for attempt in range(attempts):
            g, p, letters = generate(cols, rows, pool, rnd, usage)
            score = letters / (cols * rows) + (0.02 * len(p))
            if best is None or score > best[0]: best = (score, g, p, letters)
        _, g, p, letters = best
        for (_, _, _, w) in p: usage[w] = usage.get(w, 0) + 1
        lines.append(encode(g, p, cols, rows, rnd, pic_rate))
        shares.append(letters / (cols * rows))
        if lv % 50 == 0: print("level", lv + 1, cols, "x", rows, "words", len(p), "letter share %.2f" % (letters / (cols * rows)), flush=True)
    open(out, "w").write("\n".join(lines) + "\n")
    print("average letter share %.3f, min %.3f" % (sum(shares) / len(shares), min(shares)))

if __name__ == "__main__":
    main()
