# 1day1sec

A diary built around one second of video a day. Record the day's second, add some
writing, a voice note, a photo or a pen page, note how the day felt, read a letter
about your week, and stitch the year into a single video.

Local by default, no account. Two things can leave the device, and both are opt-in:
a backup to your own Google Drive, and the features that need an AI model (typing
out voice notes and notebook pages, writing recaps), which send what they need to
the app's backend. No backend is configured by default, and then nothing is sent:
see [docs/JOURNAL_BACKEND.md](docs/JOURNAL_BACKEND.md).

The design this was built from is [JOURNAL_DESIGN.md](JOURNAL_DESIGN.md).

## Getting it running

There is no Gradle wrapper JAR checked in (it is a binary). Either:

- open the project in Android Studio, which generates the wrapper on first
  sync, or
- run `gradle wrapper --gradle-version 8.9` once with a local Gradle install.

Then `./gradlew installDebug` with a device attached. (Once Android Studio has
synced, the Gradle it downloaded can also be run directly from
`~/.gradle/wrapper/dists/gradle-8.9-bin/*/gradle-8.9/bin/gradle`.) The emulator can record,
but a physical device is worth using — encoder behaviour on the emulator is not
representative.

Requires JDK 17 and Android SDK 35. Minimum runtime is API 26.

## Flow

Three tabs at the bottom: **Days**, **Recaps**, **Reel**.

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
Reel
  ├─ range ── text on each second (date, caption, place, style)
  ├─ Google Drive backup (a folder per month)  ◀── Restore
  └─ Make my reel ──▶ saved to Movies/1day1sec
```

Recording runs up to 5 seconds, then the trim screen picks which 1-second
window to keep. The kept second is re-encoded to a fixed profile and stored in
app-private `filesDir/clips/<date>.mp4`, with a Room row keyed by date. Journal
media (voice notes, photos, pen pages) lives in `filesDir/journal/`, and deleted
seconds wait in `filesDir/trash/`.

## Layout

| Path | What lives there |
| --- | --- |
| `data/` | Room entities, DAOs, database and migrations, repositories, file paths |
| `journal/` | Recap periods and rules, recap writing, typing out entries, adding entries |
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
| `ui/reel/` | Reel tab (uses the view models in `ui/compile/` and `ui/backup/`) |
| `ui/compile/` | Range selection, stitching, export; the text style dialog |
| `ui/backup/` | Drive backup state |
| `ui/gallery/` | One day's gallery videos, to pick from |
| `work/` | Daily reminder; nightly recaps; typing out entries once back online |

Architecture is MVVM with a hand-rolled `AppContainer` for dependencies. No DI
framework — the graph is a couple of dozen objects.

See [docs/MEDIA_PIPELINE.md](docs/MEDIA_PIPELINE.md) for why Media3 Transformer
rather than FFmpeg, and why clips are normalised on the way in.
See [docs/DRIVE_BACKUP.md](docs/DRIVE_BACKUP.md) for how the Drive backup works
and the one-time Google Cloud setup it needs before it will sign in.
See [docs/JOURNAL_BACKEND.md](docs/JOURNAL_BACKEND.md) for the AI backend the
journal talks to; its code is in [backend/](backend/README.md).

## Behaviour worth knowing

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
  that day's mood. "Download PNG" saves a 1080 × 1920 picture to Pictures/1day1sec.
- **Messages.** "Today's second is saved." and its like stay for at least ten
  seconds, wait while they are being touched or have keyboard focus, can be closed
  by hand, and are announced politely by screen readers without taking focus.

### The reel and the backup

- **One button.** "Make my reel" stitches the chosen range and saves the result to
  Movies/1day1sec in one go; the finished reel can be played on the Reel tab.
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
  month: `1day1sec/2026-11/2026-11-07.mp4`. Each day's journal goes beside its
  clip: a small JSON file with the mood, place and entries, and the entries' voice
  notes, photos and pen pages; recaps are saved too. It only ever sees files it
  created (`drive.file` scope). A dropped connection is retried automatically, the
  screen stays on during a backup, and a run that still fails can simply be tapped
  again: it skips what is already uploaded and carries on.
- **Restore from Drive** adds days that are in Drive but missing on the phone
  (for a new phone) and never overwrites a day already present. For the journal,
  "missing" means the phone has no mood, place or entry at all for that day.

## Not done yet

- Reminder time is hardcoded in `Reminders.REMINDER_TIME`; no settings screen.
- Drive backup has no automatic schedule — it runs when tapped.
- Backup is one-way: deleting a clip on the phone does not delete it from Drive.
- The date and caption sizes and the margin are constants in `DateLabelStyle.kt`
  (`DATE_TEXT_SIZE_PX`, `CAPTION_TEXT_SIZE_PX`, `DATE_MARGIN_PX`); there is no
  setting for them, nor for their colour. Fonts are limited to what ships with
  Android; custom font files are not bundled.
- Making a reel runs in the ViewModel scope, so backgrounding the app during a long
  stitch cancels it. A foreground service is the fix if reels get long.
- The journal work (everything in [JOURNAL_DESIGN.md](JOURNAL_DESIGN.md)) compiles,
  builds a debug APK and passes the unit tests, but at the time of writing has not
  been run on a phone or emulator. Layout, the camera and microphone paths, the
  photo picker, location, and the version 2 to 3 database migration on a real
  install are the things to check first. (The migration's SQL was compared,
  statement by statement, with the schema Room generates.)
- Typing out and recaps need the AI backend (`backend/`) deployed and its address
  set in `local.properties`; without that nothing is typed out and no recap is
  written. See [docs/JOURNAL_BACKEND.md](docs/JOURNAL_BACKEND.md).
- Light theme only. The app ignores the phone's dark mode.
- Deleting an entry (writing, voice note, photo) is confirmed first but cannot be
  undone; only seconds go through Recently deleted.
- The screens from the design canvas are meant to be exported into `docs/design/`;
  that folder has not been added.
- A recap is written once. Entries added to a week after its letter was written are
  not reflected, and there is no "write it again".
- Tests run on a plain JVM and cover logic that does not need the phone: how
  backup files are named and filed (`DriveLayoutTest`), which gallery videos count
  and what day they belong to (`GalleryRulesTest`), the date caption's position
  maths (`DateLabelStyleTest`), calendar thumbnail geometry (`ThumbnailRulesTest`),
  and moving a clip between days (`ClipMoveRulesTest`, plus `ClipRepositoryMoveTest`,
  which runs the real file handling against a temporary folder and an in-memory
  stand-in for the database), captions (`CaptionsTest` for tidying and wrapping,
  `ClipRepositoryCaptionTest`, and `CaptionSyncTest` for the Drive merge), the
  trash (`ClipRepositoryTrashTest`: delete, undo, replace, restore, the 30-day
  purge), recap periods and when they are due (`RecapPeriodTest`), the journal's
  arithmetic and JSON (`JournalMathTest`: quote checking, stats, places, pixels,
  recap content and the request to the backend), and the journal's backup files
  and merge rule (`JournalFileTest`). The
  trim, compile, camera and Drive paths need instrumented tests or a real account;
  they cannot be meaningfully unit tested, and the Drive clean-up after a move and
  the captions file have only been checked by reading them. The version 1 to 2
  database migration was checked by running its SQL on a copy of the old schema,
  not through Room on a device.
- Videos that exist only in Google Photos' cloud cannot be detected by date.
  Google closed its library API to other apps on 31 March 2025, so the app can see
  only what is on the phone (which includes Google Photos videos that have not
  been freed from the device). For cloud-only videos use the picker button on the
  camera screen, which can reach Google Photos but cannot be searched by date.
- No "fill every highlighted day automatically" — each day still needs you to
  choose the video and the second.
