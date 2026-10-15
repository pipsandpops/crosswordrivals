# Crossword Rivals: Word Battle

Offline crossword-battle game for Android. Scandinavian-style grids (clues inside the grid, some as pictures); the player takes turns against 1–3 computer rivals, placing letter tiles from a 5-tile rack. Right square +1, finishing a word + its length (+5 for two words at once), a wrong square ends the turn. Highest score when the grid is full wins.

- Package `com.crosswordrivals.app` (Java package `com.wordrivals.game`) · minSdk 24 · targetSdk 36 · versionCode 1 (1.0.0)
- 400 levels (6×7 → 7×8 → 7×9) + 366 Daily Puzzles (7×9, `assets/daily.txt`) built from a ~1,230-word bank written for this game (`tools/words*.txt`, format `WORD|clue|emoji|tier`). Words whose spelling differs US/UK are left out.
- Retention: Daily Puzzle with streak and evening reminder (`Reminder.java`), 7-day reward calendar, XP ranks (Rookie → Grandmaster), first-level drag guide, combos, flying tiles, confetti, Google Play in-app review after the 3rd win.
- Picture clues and rival avatars: Noto Emoji by Google (Apache 2.0), credit in Settings and `assets/LICENSE-NotoEmoji.txt`. Font: Nunito (OFL). Sounds generated in code (`Synth`). Pure Java on a Canvas, no server.

## Code
| File | What it does |
|---|---|
| `Level.java` | Grid + words parsing of `assets/levels.txt` |
| `Game.java` | Screens (home, play, popups), rules, rivals (bots), racks, hints, coins, saving, ad pacing |
| `GameView.java` | Android drawing, picture loading (`assets/pic/*.webp`), touch, sound, store |
| `Monetizer*`, `DemoMonetizer`, `play/PlayMonetizer` | Billing 8.3.0 (`no_ads` kept, `hints_25` consumed), AdMob 25.5.0 rewarded + interstitial + bottom banner (large adaptive on tall phones, 320x50 on short; hidden during matches on very short phones), UMP 3.1.0 |

Racks: each player draws from the letters the board still needs; tiles that no longer fit are swapped at the start of the turn, so nobody runs dry. Rival skill rises with the level.

Test ad IDs are set: replace `admobAppId` in `app/build.gradle` and the unit IDs in `PlayMonetizer.java` before production.

## Content pipeline
```
python3 tools/gen_levels.py app/src/main/assets/levels.txt 400 7 x 30
python3 tools/gen_levels.py app/src/main/assets/daily.txt 366 2026 daily 30
```
Add words to `tools/words*.txt` (tier 1 = easy, used before level 30), regenerate, then make sure every emoji used has `assets/pic/<codepoint>.webp` (72 px, from Noto Emoji `2D/png/128`).

## Build
GitHub Actions ("Build Android") with secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, or Android Studio.

## Tests
`test/Harness.java` (~102,000 checks: daily puzzles, streaks, reward calendar, ranks, combos, tutorial, rating and reminder flows, every level's grid and words, play by taps and drags, wrong squares, bots, hints, shop, purchases, coins doubling, ad pacing, saves, daily coins, all screens at 5 sizes, many full matches with balance stats). `test/Shots.java` makes store screenshots, feature graphic and trailer. Harness needs the pictures as PNG (Java cannot read WebP):
```
mkdir -p pngs && python3 -c "import glob,os;from PIL import Image;[Image.open(f).save('pngs/'+os.path.basename(f)[:-5]+'.png') for f in glob.glob('app/src/main/assets/pic/*.webp')]"
javac -d out app/src/main/java/com/wordrivals/game/{Game,Level,Synth}.java test/*.java
java -cp out com.wordrivals.game.Harness app/src/main/assets pngs
```
