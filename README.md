# Kilasan

Kilasan is an Android diary built around one second of video a day. ("Kilasan" is
Indonesian for a glimpse.) You record the day's second, add as much or as little as
you like around it (a few lines, a voice note, a photo, a page from your notebook)
and note how the day felt. Every week, month and year it writes you a letter about
what you wrote, and at any time it can stitch your seconds into a single video.
It also keeps a reading log: the books you are reading, and what you thought on
which page.

Everything is kept on your phone, and there is no account to make. Three things can
leave the phone, and each is your choice: a backup to your own Google Drive, the
features that need an AI model (typing out voice notes and notebook pages, writing
the letters and book summaries), and searching for a book, which sends what you
typed or the ISBN you scanned to Open Library.

## What it looks like

![Three screens of Kilasan. Days: November as a calendar with a tile for each day that has a second, today outlined, and an Add to today button. Add: record today's second, write, voice note, photo, or draw with S Pen. Reel: choose the days, the text on each second and the Google Drive backup, then Make my reel.](docs/screenshots/screens-tabs.png)

![Three more screens, each showing the top of a longer page. A day: the day's second with its mood, caption and place, then the first entries: writing, a voice note and a notebook page. A letter: a title, a quote from the week's entries, each day's mood and what happened. Year in pixels: one square per day of 2026, coloured by mood.](docs/screenshots/screens-pages.png)

These are the screens as designed, with made-up entries, not captures from a phone.
In the app the pink tiles are frames from your own videos and photos.

## What it is for

A diary is easy to start and hard to keep. One second of video is small enough to do
every day, including the days with nothing to say, and a whole year of them plays
back in about six minutes.

Kilasan is for:

- **Keeping a record of ordinary days** without it becoming a chore. The second is
  the only thing a day needs, and a reminder at 8 pm asks for it only if it is
  still missing.
- **Saying more on the days that have more.** Write, talk, add photos, or
  photograph what you wrote on paper. Talking and handwriting are typed out for
  you, so everything ends up as words you can read back.
- **Looking back.** A letter about each week, month and year, a picture of the
  year's moods, and a reel of your seconds.

It was built for one person's own use, and it shows in a few places: there is one
language pairing it is tuned for (Indonesian mixed with English), and the AI
backend answers a single Google account.

## What it can do

**Each day**

- Record up to five seconds and keep the best one, or pick a video already on the
  phone. One second per day; taking a day again replaces it.
- Give the second a caption, the day a mood (five faces, rough to great) and a
  place ("Kemang, Jakarta Selatan"; the coordinates are not stored).
- Add entries: writing, voice notes of up to five minutes, photos, photos of
  notebook pages, and pages drawn with the S Pen.
- Have voice notes, notebook pages and pen pages typed out. Speech is written word
  for word in the languages it was spoken in.
- Fill in past days, with a dot on the days that have videos in your gallery but
  no second yet.
- Undo: a deleted or replaced second waits in "Recently deleted" for 30 days.

**Looking back**

- A month calendar with a frame from each day's second.
- A letter for each week, month and year: what happened, a short note, a quote in
  your own words, what changed since the period before, and two things to try.
- The year in pixels: one cell per day in the colour of its mood, saved as a
  picture.

**Reading**

- Keep a shelf: what you are reading now, with a bookmark and a progress bar, and
  what you finished this year. Find a book by title, author or ISBN, or scan the
  barcode on its back cover.
- Log a thought on a page: a few words, a voice note or a photo of the page. It can
  also show on that day's diary page, linking back to the book.
- Once a book has three thoughts, read a summary of how your thoughts went, with
  points you could build a review from. It quotes you, never the book, and it does
  not write the review.

**The reel**

- Stitch any range of days into one video, with the date, caption and place drawn
  on each second, in a font and position you choose. Saved to `Movies/Kilasan`.

**Keeping it safe**

- Back up to a `Kilasan` folder in your own Google Drive, one folder per month,
  and restore from it on a new phone.

## How it works

```
 camera ─▶ trim ─▶ one second per day ──┐
 writing, voice notes, photos, pages ───┼─▶ kept on the phone ─▶ reel ─▶ Movies/Kilasan
 mood, place ───────────────────────────┘      │        │
                                                │        └─▶ your Google Drive (when you tap Back up now)
                                                ▼
                  voice notes, notebook and pen pages, a period's entries
                                                │
                                                ▼
                        the backend (Cloud Run) ─▶ Gemini Flash-Lite
                                                │
                                                ▼
                              typed-out words and letters, back on the phone
```

- **The second.** The camera records up to five seconds, and the trim screen picks
  which one-second window to keep. That second is re-encoded to one fixed format, so
  that any two days can later be joined without re-encoding trouble, and stored in
  the app's private storage under its date.
- **The journal.** Entries, moods and places live in a database on the phone, with
  voice notes, photos and pen pages as files beside it. An entry is saved on the
  phone before anything else is attempted, so nothing below can lose one.
- **Typing out.** A voice note, a photo you mark as a notebook page, or a pen page
  is sent to a small backend, which asks Google's Gemini Flash-Lite model for the
  words and sends them back. Ordinary photos are never sent. Offline, the entry
  waits and is retried by itself.
- **Letters.** A job on the phone runs each night after 9 pm and writes whichever
  letter is due: the week's on Sunday, the month's on the 1st, the year's on 31
  December. It sends that period's entries and numbers to the backend, which
  returns the letter. A period needs three days with an entry. The quote is checked
  against your entries, so a letter never shows words you did not write.
- **The backend** is a FastAPI service on Cloud Run ([backend/](backend/README.md)).
  It holds no data. It answers one Google account only: the app sends proof of who
  is signed in with each request, and Google confirms it. The model is named by one
  setting, so moving to a newer one is a one-line change. Without a backend
  configured, nothing is sent and those two features simply say so.
- **Books.** Search goes straight from the phone to Open Library, which is free and
  needs no key; covers come from there too. Barcodes are read on
  the phone. Books and thoughts live in the same database as the journal. A book's
  summary is written by the backend from your thoughts only, and kept until you
  add or delete a thought.
- **The reel** is built on the phone. The text is drawn as the reel is made and the
  stored seconds are never changed, so it can be made again differently at any time.
- **Backup** copies plain files to your own Drive, and the app can only see the
  files it put there itself.

More detail: [docs/JOURNAL_BACKEND.md](docs/JOURNAL_BACKEND.md) (the backend and what
is sent to it), [docs/DRIVE_BACKUP.md](docs/DRIVE_BACKUP.md) (the backup),
[docs/MEDIA_PIPELINE.md](docs/MEDIA_PIPELINE.md) (recording, trimming and stitching),
and [JOURNAL_DESIGN.md](JOURNAL_DESIGN.md) (the design the journal was built from).

## The screens

Four tabs at the bottom: **Days**, **Recaps**, **Books**, **Reel**.

```
Days (calendar)
  ├─ tap a day ──▶ Day page ──⋯──▶ Replace / Move to another day / Caption / Place / Delete
  │                  ├─ the second, playable ── mood ── place
  │                  └─ entries: written, voice note, notebook page, pen page, photos
  └─ Add to today ──▶ Record ──▶ Camera ──▶ Trim ──▶ Day page ("saved", Undo)
                      │            └─ or pick an existing video (gallery / Google Photos)
                      └─ Write / Voice note / Photo / Draw with S Pen
Recaps (one page per year, swipe between years)
  ├─ the year's letter ── Year in pixels ──▶ Download PNG
  └─ months, opening out into weeks ──▶ Week / Month / Year recap
Books
  ├─ + ──▶ Add a book (search / scan the barcode / add by hand) ──▶ "added", Undo
  ├─ Reading now ──▶ Book page ──⋯──▶ Mark as finished / Edit book / Change cover / Remove book
  │    │               ├─ your thoughts, by page ──▶ Summary and review ideas ──▶ Start my review
  │    │               └─ Add a thought ──▶ Log a thought
  │    └─ "Log a thought" on each row ──▶ Log a thought (that book)
  └─ Finished this year ──▶ Book page
Reel
  ├─ range ── text on each second (date, caption, place, style)
  ├─ Google Drive backup (a folder per month)  ◀── Restore
  ├─ About Kilasan (where book data comes from)
  └─ Make my reel ──▶ saved to Movies/Kilasan
```

## In the code

On the phone, a second is `filesDir/clips/<date>.mp4` with a Room row keyed by date.
Journal media (voice notes, photos, pen pages) lives in `filesDir/journal/`, book
covers and thoughts' media in `filesDir/books/`, and deleted seconds wait in
`filesDir/trash/`.


| Path | What lives there |
| --- | --- |
| `data/` | Room entities, DAOs, database and migrations, repositories, file paths |
| `journal/` | Recap periods and rules, recap writing, typing out entries, adding entries |
| `books/` | Book search (Open Library), ISBNs, adding books and thoughts, the book summary |
| `ai/` | The client for the AI backend |
| `location/` | The place line: one location fix per entry, turned into a short name |
| `media/` | Trim, compile, gallery scan/import/export, Transformer plumbing |
| `backup/` | Google Drive sign-in, Drive HTTP calls, folder layout, backup/restore logic |
| `ui/camera/` | CameraX capture, front/back switch |
| `ui/trim/` | 1-second window picker with looping preview |
| `ui/theme/` | Palette, type scale, line icons, mood faces, shared components, the message bar |
| `ui/nav/` | Routes, the navigation host and the tab bar |
| `ui/home/` | Days tab: month calendar; Recently deleted |
| `ui/day/` | Day page, Add sheet, move dialog, write / voice / pen screens |
| `ui/recaps/` | Recaps tab, recap page, year in pixels |
| `ui/books/` | Books tab, Add a book and the barcode scanner, book page, Log a thought, summary |
| `ui/about/` | About page, with the credit for book data |
| `ui/reel/` | Reel tab (uses the view models in `ui/compile/` and `ui/backup/`) |
| `ui/compile/` | Range selection, stitching, export; the text style dialog |
| `ui/backup/` | Drive backup state |
| `ui/gallery/` | One day's gallery videos, to pick from |
| `work/` | Daily reminder; nightly recaps; typing out entries once back online; covers and book summaries once back online |

Architecture is MVVM with a hand-rolled `AppContainer` for dependencies. No DI
framework — the graph is a couple of dozen objects.

See [docs/MEDIA_PIPELINE.md](docs/MEDIA_PIPELINE.md) for why Media3 Transformer
rather than FFmpeg, and why clips are normalised on the way in. Building the app
from source is covered in [docs/BUILDING.md](docs/BUILDING.md).

## Behaviour in detail

### The second

- **One clip per day.** Date is the primary key; re-recording a day replaces it.
- **Nothing is deleted on the spot.** Deleting a second (the ⋯ menu on its day),
  taking a day again, or moving a second onto a day that already has one all send
  the old video to a trash folder. A message offers Undo straight away; after that
  it is listed under "Recently deleted" on the Days tab, from where it can be put
  back, for 30 days. Only then, or with "Remove for good" there, is the file
  really gone. Writing and photos are never touched by deleting a second.
- **Backfill works.** Tapping any past day opens its page, which offers to record
  that day's second. Future days are not tappable.
- **Audio is optional.** Denying microphone permission still records; silence is
  inserted at compile time so the reel stays in sync.
- **The reminder is conditional.** The worker fires daily at 20:00 local and
  only notifies if that day has no clip yet.
- **Move a second to another day.** ⋯ on the day page, then "Move to another day".
  Type the day ("15 Nov 2026", "15/11/2026", or "15 Nov" for the most recent one)
  or pick it from the calendar, where a dot marks days that already have a second.
  Days after today cannot be chosen and the dialog says why. Choosing a day that
  has a second shows what is there, by its caption, before anything happens; the
  button then reads "Move and replace", and the replaced second goes to Recently
  deleted. Cancel has the focus when the dialog opens. The next Drive backup also
  removes the old day's copy from Drive (see `docs/DRIVE_BACKUP.md`), so a restore
  does not bring the clip back on the wrong day.
- **Thumbnails.** A day with a clip shows a frame from the middle of that clip, in
  portrait. Pictures are made on demand, a few at a time, and kept in a small
  memory cache (not on disk), so a month fills in over a moment after opening it.
  If a clip cannot be read the day keeps its plain blush tile.
- **Captions.** Up to 80 characters per second, typed on the trim screen or later
  from the day's ⋯ menu. It is shown on the day page and, on the reel, drawn above
  the date, wrapped over at most three lines; a caption that would not fit ends
  with "…". Taking a day again, or moving its clip to another day, keeps the
  caption. Saving it empty removes it. Captions are backed up too, in a small
  `captions.json` file in the Drive folder (see `docs/DRIVE_BACKUP.md`).
- **Use an existing video.** "Gallery" on the camera screen opens Android's Photo
  Picker (gallery, plus Google Photos where the phone supports it; no storage
  permission needed). The chosen video is copied into app cache and goes through
  the same trim step as a recording, and lands on the day you opened, so it also
  works for backfilling past days. Videos must be 1 second to 5 minutes and under
  1 GB. Landscape videos are centre-cropped to the portrait frame, and the trim
  preview shows that crop.
- **Past days highlighted from your gallery (opt-in).** "Find videos for missed
  days" on the Days tab asks Android for permission to read the gallery index, then
  puts a dot on every day that has videos but no clip yet. Such a day's page offers
  "Choose from N videos of this day", which lists them with thumbnails; picking one
  goes to the usual trim step. Only dates and lengths are read to build the
  highlights — nothing is copied until you pick a video, and nothing leaves the
  phone. The permission is never requested at launch, and on Android 14+ you can
  share just some videos. A video's day is its capture time, or the file's
  modified time if the capture time is missing (common for downloaded or chat-app
  videos), in the phone's time zone. Reels exported by this app are ignored.
- **Front camera.** The camera screen shows a switch button when the device has
  both cameras. It is locked while recording. Front-camera clips are saved
  unmirrored, as other people would see you.
- **Tap to focus.** Tapping the camera preview focuses (and sets exposure) at that
  point and shows a ring there: white while it works, red if the camera could not
  lock. The camera drops back to continuous autofocus by itself after a few
  seconds. The app has to forward taps to the camera itself, because CameraX only
  does this automatically when driven through a `CameraController`, which this
  app does not use. Focus has a physical limit: the main lens of the phone this
  was built on cannot focus closer than about 10 cm.

### The journal

- **Entries.** A day can have any number of entries: writing, voice notes (up to
  five minutes), photos, notebook pages and pen pages. They are listed on the day
  page under the second, oldest first, each with its time and kind. Photos chosen
  together are shown as one row. An entry is saved on the phone before anything
  else is attempted.
- **Typed out for you.** A voice note, a photo marked "It's a notebook page" and a
  pen page have their words typed out by the AI backend. The voice note can be
  played the moment it is saved; its text follows. Offline, the entry says so and
  is retried by itself once the phone is back online, or at once with "Try now".
  Ordinary photos are never sent anywhere. The typed-out words can be corrected,
  or typed by hand, from the entry's ⋯ menu.
- **Mood.** Five line-drawn faces, from rough to great. Today's page asks "How was
  today?" until one is chosen; any day's face can be tapped to change or remove it.
- **Place.** The first time something is added, the app asks for location. With it,
  each entry made today and each second saved today gets a short place name
  ("Kemang, Jakarta Selatan") from one location fix; the coordinates are not
  stored and nothing is tracked in between. A day's place can be changed or
  removed from its ⋯ menu. Without the permission, place lines are simply left out.
- **Recaps.** A letter about each week (written on Sunday night), each month (on
  the 1st of the next) and each year (on 31 December): what happened, a note, a
  quote in your own words, what changed since the period before, and two things to
  try. A period needs at least three days with an entry. The quote is checked
  against your entries on the phone, so it is never something you did not write.
  See [docs/JOURNAL_BACKEND.md](docs/JOURNAL_BACKEND.md).
- **Year in pixels.** One column per month, one row per day, each cell the colour of
  that day's mood. "Download PNG" saves a 1080 × 1920 picture to Pictures/Kilasan.
- **Messages.** "Today's second is saved." and its like stay for at least ten
  seconds, wait while they are being touched or have keyboard focus, can be closed
  by hand, and are announced politely by screen readers without taking focus.

### Books

- **The shelf.** "Reading now" lists each book with its cover, where the bookmark
  is, and a "Log a thought" link of its own. "Finished this year" is a grid of
  covers; earlier years are behind a button. An empty shelf says so and offers
  "Add a book".
- **Adding a book.** Search by title, author or ISBN, or scan the barcode on the
  back cover; only ISBN barcodes are accepted, so a price sticker is ignored. Each
  result is one edition, with its publisher, year and page count, because progress
  depends on the right one. Results come from Open Library, with the title you
  typed first; a page count it gives for
  all editions of a book is shown as "about 322 pages" and can be corrected under
  "Edit book". When search finds nothing, has no connection or is not
  answering, the screen says which and offers adding by hand, with a photo of the
  cover. A book with an ISBN and no cover gets one once the phone is online; until
  then, and if none is found, its title is printed on a plain cover.
- **Thoughts.** A thought belongs to a page. The page field starts on the page
  after the bookmark. Saving moves the bookmark only forward; it can be moved back
  under "Edit book". A thought can have a photo of the page or a voice note. A
  voice note with no typed words is typed out like a diary voice note; typed words
  are always kept as they are. Closing "Log a thought" keeps what was started, per
  book. A logged thought's page and words can be corrected from its ⋯ menu on the
  book page; if a mistyped page had put the bookmark too far on, correcting it
  brings the bookmark back to the furthest page thought about.
- **In the diary.** With "Also show it in today's diary" on, the thought appears on
  that day's page as "21:10, from Laut Bercerita, page 214" and opens the book. It
  is not copied: it stays the book's, is changed or deleted there, and does not
  count as a diary entry for recaps.
- **Summary and review ideas.** Needs three thoughts with words. It is kept until a
  thought is added or deleted, then written again when next opened. The quote is
  checked on the phone against your thoughts, and a line you copied out of the book
  in quotation marks is never shown as your quote. "Start my review" opens today's
  write screen with the ticked points as starting lines; the review is saved as an
  ordinary diary entry.

### The reel and the backup

- **One button.** "Make my reel" stitches the chosen range and saves the result to
  Movies/Kilasan in one go; the finished reel can be played on the Reel tab.
- **Text on each second.** Date, caption and place can each be switched on or off.
  The text is drawn when the reel is built; the stored clips themselves are never
  modified, so the switches can be flipped and the reel made again at any time.
  The place is the day's place, on one line under the date.
- **Text style.** "Change style" opens a dialog with a live preview to pick the
  font (seven built-in Android families), the side (left, center, right) and the
  edge (top or bottom) for the text as a block. The default is the original
  bottom-left. There is no background box; a soft shadow keeps the white text
  readable on bright footage. The choice is remembered, and changing it discards a
  reel that is already built.
- **Drive backup is opt-in and manual.** Nothing is uploaded until you tap
  "Back up now". Clips go up as ordinary `.mp4` files, unencrypted, so anyone with
  access to your Google account can watch them. They are filed one folder per
  month: `Kilasan/2026-11/2026-11-07.mp4`. Each day's journal goes beside its
  clip: a small JSON file with the mood, place and entries, and the entries' voice
  notes, photos and pen pages; recaps are saved too. Books go in `Kilasan/books/`,
  each with its cover and its thoughts. It only ever sees files it
  created (`drive.file` scope). A dropped connection is retried automatically, the
  screen stays on during a backup, and a run that still fails can simply be tapped
  again: it skips what is already uploaded and carries on.
- **Restore from Drive** adds days that are in Drive but missing on the phone
  (for a new phone) and never overwrites a day already present. For the journal,
  "missing" means the phone has no mood, place or entry at all for that day. Books
  that are in Drive and not on the phone come back too.
