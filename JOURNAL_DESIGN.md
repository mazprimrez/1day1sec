# 1day1sec → daily journal: design handoff

Turn 1day1sec into a diary built around the daily one-second clip. **Keep every
existing feature** (record up to 5 s, trim to 1 s, import from gallery,
captions, compile reel with date/caption style, Google Drive backup and restore,
daily reminder). Add journal entries, mood, place and AI recaps on top. It is a
diary only: no to-do lists anywhere.

Screens and flows were designed in a Claude canvas; exported images live in
`docs/design/` (add them there). This file is the source of truth for code.

---

## Look and feel

Minimal and modern, white background, pink from blog.mazprimrez.com.

### Colors (replace `ui/theme/Theme.kt`, light theme only for now)

| Role | Hex | Use |
|---|---|---|
| Paper | `#FFFFFF` | Every background |
| Ink | `#2E2329` | Text, icons |
| Muted | `#6E6168` | Dates, secondary text, inactive tabs |
| Rose | `#AE4A6B` | The one main action per screen, today's ring, mood faces |
| Blush | `#FBCCDD` | Clip and photo placeholders, selected chips |
| Hairline | `#F1E6EA` | 1 dp dividers, outlines |

Mood scale (only in mood visuals, darker = harder day):
great `#FCE4EC`, good `#F7BDD0`, neutral `#E58AAB`, bad `#B8527A`, rough `#6E2445`.

Rules: one rose button per screen. No shadows, no tilted elements, no card
backgrounds; separate sections with space and hairlines. Only tappable things
get an outline.

### Type

- **Instrument Sans** for everything (Google Fonts, add to `res/font`).
- **Instrument Serif italic** only for the quote of the week/month/year.
- Four sizes: 28 sp titles (weight 600, letter spacing -0.8), 17 sp section
  headings (600), 16 sp body, 14 sp meta (Muted). Tab labels 13 sp.
- The big date on the day page is the one exception: 76 sp, 600, letter spacing -3.

### Icons and mood faces

Thin line icons, 1.4–2 dp stroke, round caps. Mood is shown as a line-drawn
face in Rose, never as an emoji. Five moods: rough, bad, neutral, good, great.

### Media

Photos and videos are mostly vertical. Clip tiles, previews and photo tiles are
portrait (9:16 for clips, 3:4 for photos).

---

## Navigation

Bottom bar with 3 tabs: **Days**, **Recaps**, **Reel**.

```
Days (calendar)
 ├─ tap a kept day ─────────▶ Day page ──⋯──▶ Day options sheet
 │                                          ├─ Replace this second ─▶ Camera
 │                                          ├─ Move to another day ─▶ Move dialog
 │                                          ├─ Edit caption / Change place
 │                                          └─ Delete this second (undo + Recently deleted)
 └─ Add to today ──▶ Add sheet
                      ├─ Record today's second ─▶ Camera ─▶ Trim ─▶ Today page (saved toast + Undo)
                      └─ Write / Voice note / Photo / Draw with S Pen
Recaps (per year, swipe between years)
 ├─ Year recap ─▶ Year page
 ├─ Year in pixels ─▶ Pixels page ─▶ Download PNG
 └─ Month rows (expand to weeks) ─▶ Month recap / Week recap
Reel
 ├─ Range: All time / Last 30 days / This month
 ├─ Text on each second (date, caption, place, style)
 ├─ Google Drive backup (Back up now, Restore)
 └─ Make my reel
```

---

## Screens

### Days tab (`ui/home/HomeScreen.kt`)
- Title = month name, under it "13 of 16 days kept" (Muted). Prev/next month arrows.
- Calendar grid, Monday first, portrait tiles (~60 dp tall), day number bottom-left.
  - Kept: Blush fill. Missed: hairline outline. Today: 2 dp Rose ring. Future: number only.
  - Accessibility label is the full date: "Saturday, 14 November 2026".
- One Rose button "Add to today" above the tab bar.

### Add to today (bottom sheet or screen)
- Title "Add to today", meta "Monday, 16 November, Kemang".
- Rose button "Record today's second" → existing camera flow.
- Rows: Write, Voice note, Photo ("Notebook pages are typed out for you"), Draw with S Pen.

### Camera and Trim (existing, restyle only)
- Camera: up-to-5-seconds progress, front/back switch, "Gallery" import, place under the date.
- Trim: portrait looping preview, 1-second window on a filmstrip, optional caption,
  "Retake" / "Use this second". After saving go to the day page with the saved toast.

### Day page (new)
- Top bar: back, "+" (add to this day), "⋯" (options).
- Header row: portrait clip on the left (~210 × 373 dp). Right column, top to bottom:
  mood face (tap to change), big day number, month, weekday (Muted), caption,
  place (Muted, bottom-aligned with the clip).
- Entries below as plain rows separated by hairlines, each with a Muted line
  "09:40, written" / "13:05, voice note" / "18:20, from my notebook" / "21:30, S Pen" / "2 photos".
- Today with no mood yet: dashed circle with "+" instead of the face, and a
  "How was today?" row of the five faces.

### Entry processing states
- Voice note: playable immediately; text shows "Writing out what you said…" until transcribed.
- Notebook photo offline: "No internet right now. The photo is saved on your phone.
  Its text will be typed out as soon as you're back online." + "Try now". Retry automatically.
- Messages use a polite live region and never take focus.

### Saved / deleted messages (snackbar)
- "Today's second is saved." with **Undo** and **Write a few words**.
- "Second for 14 November deleted." with **Undo** and **Record a new one**.
- At least 10 s, stays while touched or focused, has a close button.

### Day options sheet
Replace this second, Move to another day, Edit caption, Change place, then
separated at the bottom: Delete this second ("Your writing and photos stay. You can
undo, and it stays in Recently deleted for 30 days."). Close button at the bottom.

### Move to another day
- Text field "New day" (type "15 Nov 2026") plus a month calendar.
- Dots on days that already have a second. Days after today are not selectable,
  with the reason shown.
- If the target day has a second: inline warning naming it ("15 November already has
  a second, 'coffee before work'. Moving here replaces it, and that video can't be
  brought back."). Primary button becomes "Move and replace 15 Nov"; Cancel is the
  default focus. (`ClipMoveRules` / `ClipAlreadyExistsException` already cover the logic.)

### Recaps tab (new)
- Big year number (84 sp) with an arrow and swipe to earlier years.
- The year's recap (title + quote), then "Year in pixels".
- "Months and weeks": one row per month (tap → month recap) with an expand toggle
  "4 weeks" that shows week chips ("9 to 15 Nov", tap → week recap). The current
  month says "Letter arrives on 1 December"; the current week chip says "on Sunday".

### Week / Month / Year recap (new, one shared layout)
1. Back, period with a previous arrow ("9 to 15 November").
2. Title (28 sp), then the quote of the period in Rose serif italic + "You, on Saturday".
3. One visual: portrait clip tiles with a thin mood bar under each (week = 7 tiles,
   month = calendar grid). Year shows a small pixel preview linking to Year in pixels.
   One Muted line: "The bar under each day is your mood. Darker is a harder day."
4. "Play this week, 6 seconds" text button.
5. Chapters with the same heading style and a hairline between them:
   **What happened** (short dated lines) · **A note for you** ("Dear Eza," + one short
   paragraph) · **Since last week/month/year** (up/down/same arrows) ·
   **Something to try** (2 gentle bullets, not a checklist). Year adds
   **Where you were** (days per city as bars).
6. Footer line: "A reflection from your own entries, not therapy."

States: still writing (placeholder lines + "Takes about a minute"), not enough
entries ("You've written on 1 day this week. Write on 3 or more…" + "Write about
today"), couldn't be written ("Your phone was offline on Sunday night. Your entries
are safe" + "Write the letter now").

### Year in pixels (new)
- One column per month, one row per day, cells colored by mood; no entry = outline;
  future = faint.
- Legend with day counts per mood.
- Rose "Download PNG" → saves a 1080 × 1920 image to the gallery
  ("2026 in pixels", the grid, legend, small 1day1sec mark).

### Reel tab (existing compile + backup, restyled)
- Portrait preview beside the range radios.
- Rows: "Text on each second" (date, caption, place on/off + existing font/position
  picker) and "Google Drive backup" (status + Back up now / Restore, which only adds
  days missing from the phone).
- Rose "Make my reel", "Saves to Movies/1day1sec".

---

## Data

Keep `ClipEntity` as is. Add (Room migration 2 → 3):

```kotlin
@Entity(tableName = "day_meta")
data class DayMeta(
    @PrimaryKey val date: LocalDate,
    val mood: Mood?,            // ROUGH, BAD, NEUTRAL, GOOD, GREAT
    val placeName: String?,     // e.g. "Kemang, Jakarta Selatan"
)

@Entity(tableName = "entries", indices = [Index("date")])
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val createdAt: Long,
    val type: EntryType,        // WRITTEN, VOICE, NOTEBOOK_PHOTO, PEN, PHOTO
    val text: String?,          // written text, transcript or extracted text
    val mediaPath: String?,     // audio, photo or pen image file
    val placeName: String?,
    val processing: ProcessingState, // DONE, PENDING, WAITING_FOR_NETWORK, FAILED
)

@Entity(tableName = "recaps")
data class Recap(
    @PrimaryKey val key: String, // "week-2026-11-09", "month-2026-10", "year-2026"
    val title: String, val quote: String, val quoteDate: LocalDate,
    val json: String,            // what happened, note, changes, tries, places
    val createdAt: Long,
)
```

- **Location:** fused location once per entry; reverse geocode to a short place
  name. Ask permission on first use; when off, simply hide place lines.
- **Backup:** extend the Drive layout so entries, day_meta and recaps are backed up
  with the clips (same month folders, JSON next to the media).

## AI (Gemini Flash-Lite via the backend)

- Voice note → transcript. Notebook photo / S Pen page → extracted text.
- Weekly recap every Sunday night (WorkManager), monthly on the 1st, yearly on
  31 December. Needs at least 3 days with entries in a week.
- Recap prompt structure: what happened (factual, dated) → a warm, non-judging
  note in the voice of a psychologist → one quote taken verbatim from the user's own
  entries → what changed vs. the previous period (with numbers) → 2 gentle
  suggestions. Return JSON matching the `Recap.json` fields.

## Must-fix behaviour in existing code

1. `ClipRepository.deleteClip` deletes the file immediately. Move the file to a
   `trash/` folder instead, keep a row with a deletion time, offer Undo, list them in
   "Recently deleted", purge after 30 days.
2. Moving with `replaceExisting = true` deletes the target clip. Route it through the
   same trash folder.

## Build order

1. Theme, fonts, bottom navigation, restyled Days / Camera / Trim / Reel.
2. Day page + entries (written, photo), mood, place; migration 2 → 3.
3. Voice notes and transcription; notebook photo extraction; offline states.
4. Recaps (week → month → year), Recaps tab, recap states.
5. Year in pixels + PNG export. 6. Safer delete, move dialog, polish.
