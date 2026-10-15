"""Launcher icon: a tiny crossword (clue square + WIN across) and a rack tile dropping in. Usage: make_icon.py FONT OUT_DIR RES_DIR"""
import sys, os
from PIL import Image, ImageDraw, ImageFont
FONT, OUT, RES = sys.argv[1], sys.argv[2], sys.argv[3]
BG1, BG2 = (75, 128, 238), (40, 80, 184)
def art(N, scale):
    im = Image.new("RGBA", (N, N)); d = ImageDraw.Draw(im)
    for y in range(N):
        t = y / N; d.line([0, y, N, y], fill=tuple(int(BG1[i] + (BG2[i] - BG1[i]) * t) for i in range(3)) + (255,))
    c = N * 0.19 * scale; gap = c * 0.08
    x0 = N / 2 - c * 2.1; y0 = N / 2 - c * 2.05
    f = ImageFont.truetype(FONT, int(c * 0.62))
    def tile(col, row, ch, fill=(255, 255, 255), ink=(31, 42, 68), dx=0, dy=0, shadow=True):
        l, t = x0 + col * c + gap + dx, y0 + row * c + gap + dy; r, b = l + c - 2 * gap, t + c - 2 * gap
        if shadow: d.rounded_rectangle([l, t + c * 0.06, r, b + c * 0.06], radius=c * 0.18, fill=(20, 40, 110, 120))
        d.rounded_rectangle([l, t, r, b], radius=c * 0.18, fill=fill)
        if ch: d.text(((l + r) / 2, (t + b) / 2 + c * 0.03), ch, font=f, fill=ink, anchor="mm")
    # clue squares
    for (col, row, arrow) in [(0, 1, "R"), (1, 0, "D")]:
        l, t = x0 + col * c + gap, y0 + row * c + gap; r, b = l + c - 2 * gap, t + c - 2 * gap
        d.rounded_rectangle([l, t, r, b], radius=c * 0.16, fill=(255, 201, 60) if arrow == "R" else (110, 150, 245))
        a = c * 0.11
        if arrow == "R": cy = (t + b) / 2; d.polygon([(r + a * 0.2, cy - a), (r + a * 1.3, cy), (r + a * 0.2, cy + a)], fill=(224, 164, 26))
        else: cx = (l + r) / 2; d.polygon([(cx - a, b + a * 0.2), (cx, b + a * 1.3), (cx + a, b + a * 0.2)], fill=(53, 97, 201))
        # little clue lines
        for k in range(3):
            yy = t + c * (0.3 + k * 0.17); ww = c * (0.5 if k < 2 else 0.32)
            d.rounded_rectangle([(l + r) / 2 - ww / 2, yy, (l + r) / 2 + ww / 2, yy + c * 0.07], radius=c * 0.035, fill=(255, 255, 255, 230) if arrow == "D" else (31, 42, 68, 200))
    tile(1, 1, "W"); tile(2, 1, "I"); tile(3, 1, None)
    tile(1, 2, "O"); tile(1, 3, None)
    # the N tile on its way into the empty square, tilted
    t_im = Image.new("RGBA", (int(c * 1.3), int(c * 1.3))); td = ImageDraw.Draw(t_im)
    s = c * 0.92; o = (c * 1.3 - s) / 2
    td.rounded_rectangle([o, o + s * 0.07, o + s, o + s * 1.07], radius=s * 0.2, fill=(217, 164, 26))
    td.rounded_rectangle([o, o, o + s, o + s], radius=s * 0.2, fill=(255, 244, 214))
    td.text((o + s / 2, o + s / 2 + c * 0.03), "N", font=f, fill=(31, 42, 68), anchor="mm")
    t_im = t_im.rotate(-12, resample=Image.BICUBIC)
    im.alpha_composite(t_im, (int(x0 + 3 * c - c * 0.1), int(y0 + 1.9 * c)))
    return im
full = art(1024, 1.0); fg = art(1024, 0.7)
full.save(OUT + "/icon_full.png"); full.convert("RGB").resize((512, 512), Image.LANCZOS).save(OUT + "/play-icon-512.png")
for dd, legacy, fore in [("mdpi", 48, 108), ("hdpi", 72, 162), ("xhdpi", 96, 216), ("xxhdpi", 144, 324), ("xxxhdpi", 192, 432)]:
    p = RES + "/mipmap-" + dd; os.makedirs(p, exist_ok=True)
    L = full.resize((legacy, legacy), Image.LANCZOS)
    m = Image.new("L", (legacy * 4, legacy * 4), 0); ImageDraw.Draw(m).rounded_rectangle([0, 0, legacy * 4 - 1, legacy * 4 - 1], radius=legacy * 4 * 0.2, fill=255)
    L.putalpha(m.resize((legacy, legacy), Image.LANCZOS)); L.save(p + "/ic_launcher.png")
    fg.resize((fore, fore), Image.LANCZOS).save(p + "/ic_launcher_foreground.png")
