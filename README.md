# 1day1sec

Record one second a day, stitch the year into a single video. Local by default —
no backend, no account. The only thing that can leave the device is an opt-in
backup to your own Google Drive.

## Getting it running

There is no Gradle wrapper JAR checked in (it is a binary). Either:

- open the project in Android Studio, which generates the wrapper on first
  sync, or
- run `gradle wrapper --gradle-version 8.9` once with a local Gradle install.

Then `./gradlew installDebug` with a device attached. The emulator can record,
but a physical device is worth using — encoder behaviour on the emulator is not
representative.

Requires JDK 17 and Android SDK 35. Minimum runtime is API 26.

## Flow

```
Home (calendar)
  └─ tap a day ──▶ Camera ──▶ Trim ──▶ saved, back to Home
                     └─ or pick an existing video (gallery / Google Photos) ─▶ Trim
  └─ compile    ──▶ Compile ──▶ reel ──▶ Save to gallery
  └─ backup     ──▶ Backup ──▶ Google Drive (a folder per month)  ◀── Restore
```

Recording runs up to 5 seconds, then the trim screen picks which 1-second
window to keep. The kept second is re-encoded to a fixed profile and stored in
app-private `filesDir/clips/<date>.mp4`, with a Room row keyed by date.

## Layout

| Path | What lives there |
| --- | --- |
| `data/` | Room entity, DAO, database, repository, file paths |
| `media/` | Trim, compile, gallery scan/import/export, Transformer plumbing |
| `backup/` | Google Drive sign-in, Drive HTTP calls, folder layout, backup/restore logic |
| `ui/camera/` | CameraX capture, front/back switch |
| `ui/trim/` | 1-second window picker with looping preview |
| `ui/home/` | Month calendar grid |
| `ui/compile/` | Range selection, stitching, export |
| `ui/backup/` | Drive backup screen |
| `ui/gallery/` | One day's gallery videos, to pick from |
| `work/` | Daily reminder worker and scheduling |

Architecture is MVVM with a hand-rolled `AppContainer` for dependencies. No DI
framework — the graph is eight objects.

See [docs/MEDIA_PIPELINE.md](docs/MEDIA_PIPELINE.md) for why Media3 Transformer
rather than FFmpeg, and why clips are normalised on the way in.
See [docs/DRIVE_BACKUP.md](docs/DRIVE_BACKUP.md) for how the Drive backup works
and the one-time Google Cloud setup it needs before it will sign in.

## Behaviour worth knowing

- **One clip per day.** Date is the primary key; re-recording a day replaces it.
  Long-press a day on the calendar to delete it.
- **Backfill works.** Tapping any past day opens the camera for that date.
  Future days are not tappable.
- **Audio is optional.** Denying microphone permission still records; silence is
  inserted at compile time so the reel stays in sync.
- **The reminder is conditional.** The worker fires daily at 20:00 local and
  only notifies if that day has no clip yet.
- **Dates on the reel.** The compile screen has a "Show date on each second"
  switch (on by default). The date is drawn when the reel is built; the stored
  clips themselves are never modified, so the switch can be flipped and the reel
  rebuilt at any time.
- **Move a clip to another date.** Press and hold a day that has a clip, then
  "Move to another date" and pick the right day (days that have not happened yet
  are greyed out). For videos that came from a gallery or download and were filed
  under the wrong date. If the new day already has a clip you are asked before it
  is replaced. The calendar jumps to the new month so you can see where it went.
  The next Drive backup also removes the old day's copy from Drive (see
  `docs/DRIVE_BACKUP.md`), so a restore does not bring the clip back on the wrong day.
- **Calendar thumbnails.** A day with a clip shows a frame from the middle of that
  clip, cropped square. Pictures are made on demand, a few at a time, and kept in
  a small memory cache (not on disk), so a month fills in over a moment after
  opening it. If a clip cannot be read the day keeps its plain coloured tile.
- **Date style.** "Change style" opens a dialog with a live preview to pick the
  font (seven built-in Android families), the side (left, center, right) and the
  edge (top or bottom). The default is the original bottom-left. There is no
  background box; a soft shadow keeps the white text readable on bright footage.
  The choice is remembered, and changing it discards a reel that is already built.
- **Use an existing video.** The photo-library button on the camera screen opens
  Android's Photo Picker (gallery, plus Google Photos where the phone supports
  it; no storage permission needed). The chosen video is copied into app cache and
  goes through the same trim step as a recording, and lands on the day you tapped,
  so it also works for backfilling past days. Videos must be 1 second to 5 minutes
  and under 1 GB. Landscape videos are centre-cropped to the portrait frame.
- **Past days highlighted from your gallery (opt-in).** The gallery icon on the
  home screen asks Android for permission to read the gallery index, then tints
  every day that has videos but no clip yet and puts a dot on it. Tapping such a
  day lists that day's videos with thumbnails; picking one goes to the usual trim
  step. Only dates and lengths are read to build the highlights — nothing is
  copied until you pick a video, and nothing leaves the phone. The permission is
  never requested at launch, and on Android 14+ you can share just some videos.
  A video's day is its capture time, or the file's modified time if the capture
  time is missing (common for downloaded or chat-app videos), in the phone's
  time zone. Reels exported by this app are ignored.
- **Front camera.** The camera screen shows a switch button when the device has
  both cameras. It is locked while recording. Front-camera clips are saved
  unmirrored, as other people would see you.
- **Drive backup is opt-in and manual.** Nothing is uploaded until you tap
  "Back up now". Clips go up as ordinary `.mp4` files, unencrypted, so anyone with
  access to your Google account can watch them. They are filed one folder per
  month: `1day1sec/2026-11/2026-11-07.mp4`. It only ever sees files it created
  (`drive.file` scope).
- **Restore from Drive** adds days that are in Drive but missing on the phone
  (for a new phone) and never overwrites a day already present.

## Not done yet

- Reminder time is hardcoded in `Reminders.REMINDER_TIME`; no settings screen.
- Drive backup has no automatic schedule — it runs when tapped.
- Backup is one-way: deleting a clip on the phone does not delete it from Drive.
- The date label's size and margin are constants in `DateLabelStyle.kt`
  (`DATE_TEXT_SIZE_PX`, `DATE_MARGIN_PX`); there is no setting for them, nor for
  its colour. Fonts are limited to what ships with Android; custom font files
  are not bundled.
- Compile runs in the ViewModel scope, so backgrounding the app during a long
  stitch cancels it. A foreground service is the fix if reels get long.
- Tests run on a plain JVM and cover logic that does not need the phone: how
  backup files are named and filed (`DriveLayoutTest`), which gallery videos count
  and what day they belong to (`GalleryRulesTest`), the date caption's position
  maths (`DateLabelStyleTest`), calendar thumbnail geometry (`ThumbnailRulesTest`),
  and moving a clip between days (`ClipMoveRulesTest`, plus `ClipRepositoryMoveTest`,
  which runs the real file handling against a temporary folder and an in-memory
  stand-in for the database). The trim, compile, camera and Drive paths need
  instrumented tests or a real account; they cannot be meaningfully unit tested,
  and the Drive clean-up after a move has only been checked by reading it.
- Videos that exist only in Google Photos' cloud cannot be detected by date.
  Google closed its library API to other apps on 31 March 2025, so the app can see
  only what is on the phone (which includes Google Photos videos that have not
  been freed from the device). For cloud-only videos use the picker button on the
  camera screen, which can reach Google Photos but cannot be searched by date.
- No "fill every highlighted day automatically" — each day still needs you to
  choose the video and the second.
