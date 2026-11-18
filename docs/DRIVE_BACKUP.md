# Google Drive backup

The backup section of the Reel tab copies every clip, and each day's journal, into
a `Kilasan` folder in the user's own Google Drive, filed by month, and can restore
them on a new phone. It is opt-in: the app makes no network calls until you tap
**Back up now** or **Restore from Drive** and approve access.

## What it can and cannot see

The app asks for a single scope, `drive.file`. Google restricts that scope to
files **the app itself created** — it cannot list, read or change anything else in
the Drive. That also means the `Kilasan` folder must be created by the app; a
folder you make by hand in the Drive web UI is invisible to it.

## Privacy

Clips are uploaded as ordinary, **unencrypted** `.mp4` files, so anyone with access
to your Google account can watch them, and you can play them in the Drive app. (An
earlier version encrypted them with a passphrase; that was removed.)

## Folder layout

One folder per month, named `yyyy-MM` so they sort chronologically, holding that
month's clips named by day:

```
Kilasan/
  2026-10/
    2026-10-31.mp4
  2026-11/
    2026-11-01.mp4
    2026-11-01.json                  the day's mood, place and entries
    2026-11-01-1793500000000.m4a     an entry's voice note, photo or pen page
    2026-11-02.mp4
    recap-week-2026-11-02.json
    recap-month-2026-11.json
  books/
    2026-11-02-laut-bercerita-7k2q.json                the book and its thoughts
    2026-11-02-laut-bercerita-7k2q.jpg                 its cover
    2026-11-02-laut-bercerita-7k2q-1793500000000.m4a   a thought's voice note or page photo
  recap-year-2026.json
  captions.json
```

`captions.json` holds the captions the user wrote for their clips (see below).
The journal files are described under "The journal" further down, and the books
under "Books".

The day is always read from the file name, never from the folder, so a file that
ends up in the wrong folder is still restored to the right day. The layout lives
in `backup/DriveLayout.kt` and is unit-tested (`DriveLayoutTest`).

## How a backup run works

There is no local record of what was uploaded. Each run:

1. Gets an access token from Google (silent after the first approval).
2. Finds or creates the `Kilasan` folder and lists what is directly inside it. A
   backup made while the app was called 1day1sec sits in a `1day1sec` folder; that
   folder is renamed to `Kilasan` first, so the backup carries on in place. (If the
   renaming fails, the folder is used under its old name and renamed next time.)
3. For every local clip, finds or creates that month's folder and lists it (once
   per month), then compares checksums with the clip in that folder:
   - same name, same MD5 → skipped;
   - same name, different MD5 (the day was re-recorded) → uploaded again, then
     the old copy is deleted;
   - not there → uploaded.
4. Once a day has its copy in the right month folder, a loose `<date>.mp4` for the
   same day in the top folder is deleted. The first version of the backup put
   everything flat in the top folder, and this tidies that up as it goes.

5. **Moved clips.** Moving a clip to another date on the phone (⋯ on the day's
   page) leaves the clip's old day empty, but its copy in Drive would still be
   there, and a restore would bring it back on the wrong day. So a move notes the
   old day in a small local list (`PendingDriveRemovals`), and the backup deletes
   that day's file from Drive once all the uploads are done, so the clip is safely
   in Drive under its new day first. The note is cleared only after the deletion
   worked, so a failed run simply tries again. If a clip is on that day again by
   then (retaken, or moved back) nothing is deleted: the normal upload has already
   made Drive match it.

6. **Captions.** A clip's caption lives in the app's database, not inside its `.mp4`,
   so it would be lost on a restore. The backup keeps the captions in one small,
   readable JSON file next to the month folders, from day to caption. It is a
   **merge**, not a copy of the phone: captions of days the phone has no clip for are
   kept as they are in Drive, so tapping "Back up now" on a new phone that has a
   single clip does not wipe a year of captions. For a day the phone does have, the
   phone wins (a caption changed or cleared here is saved), and the old day of a
   moved clip loses its caption (step 5). The file is only written when its content
   changed, found by comparing checksums like the clips. A failed download of the
   existing file fails the backup instead of being treated as "no captions yet",
   which would overwrite the real file with the phone's partial one.

Because Drive is the source of truth, a run can be repeated safely and an
interrupted run continues where it stopped.

Backup is otherwise one-way: clips you **delete** on the phone are not deleted
from Drive. Only the old copy of a clip you **moved** is removed (step 5).

## The journal

A day's mood, place and entries are in the app's database, and its voice notes,
photos and pen pages are files in the app's private storage. None of that is in the
clip, so the backup puts it beside the clip, in the same month folder:

- **`<date>.json`**, one per day that has a mood, a place or an entry. Readable by
  anyone who opens it: the mood, the place, and each entry with the moment it was
  made, its kind, its words, and the name of its media file if it has one.
- **The media files**, named by day and the moment the entry was made
  (`2026-11-01-1793500000000.m4a`), which is also their name on the phone. They go
  up before the day's file, so the file never points at media that is not there yet.
- **`recap-<key>.json`**, one per recap. A week's is filed in the month it is listed
  under in the app, a month's in its own folder, and a year's beside the month folders.

Like everything else, each is compared by checksum and only uploaded when it changed.

The day's file is a **merge**, for the same reason the captions are. If Drive's copy
differs from the phone's, it is downloaded and combined: an entry that is in Drive
but not on the phone is kept, an entry on both is taken from the phone (it may have
been edited, or typed out since), and the phone's mood and place win where it has
them. So a new phone that has only a mood for some day cannot wipe that day's
entries from the backup. The other side of that: an entry **deleted** on the phone
stays in Drive, as a deleted clip does.

A journal file larger than Drive's single-request limit (just under 5 MB) is left
out and counted in the result, instead of failing the whole run. Photos are shrunk
on the way in and voice notes are capped at five minutes, so in practice nothing
reaches that size.

**Restore** brings back the journal of any day for which the phone has nothing at
all: no mood, no place, no entry. Each entry's media is downloaded with it; an entry
whose media is missing from Drive is skipped. Recaps the phone does not have are
restored too. A voice note or page that was backed up before its words were typed
out comes back still waiting, and is typed out once the phone is online.

The file formats and the merge are in `backup/JournalFile.kt`, unit-tested
(`JournalFileTest`). As with the clips, the real calls to Drive have not been tested
against a live account.

## Books

Books, the thoughts logged on them and their covers are backed up with everything
else, in one folder of their own beside the month folders:

- **`<key>.json`**, one per book: its title, author, ISBN, edition, page count,
  where the bookmark is, whether it is finished, the days it was started and
  finished, and each thought with its page, the moment it was logged, its kind, its
  words, whether it shows in the diary, and the name of its media file if it has one.
- **`<key>.jpg`**, the cover, if the book has one.
- **`<key>-<moment>.m4a` or `.jpg`**, a thought's voice note or photo of the page.

The **key** is made when the book is added: the day it was started, its title in
plain letters, and four random letters (`2026-11-02-laut-bercerita-7k2q`). It never
changes afterwards, so correcting a title does not orphan the files, and two copies
of one book started on the same day stay apart.

A book goes up the same way a day's journal does. The cover and the media go first,
then the file that refers to them. Each is compared by checksum and only uploaded
when it changed. The book's file is a **merge**: a thought that is in Drive but not
on the phone is kept, a thought on both is taken from the phone (a thought is known
by the moment it was logged, so one whose page was corrected replaces its old copy),
and the book's details are the phone's. So a thought **deleted** on the phone stays in Drive, as a
deleted entry does.

A **removed book** is different. Removing a book on the phone is remembered, and
the next backup takes its file, its cover and its thoughts' media out of Drive, so
that a later restore does not bring it back. Only files named with exactly that
book's key are touched.

The "Summary and review ideas" page is not backed up. It is written from the
thoughts, and is written again when it is next opened.

**Restore** brings back every book that is in Drive and not on the phone, with its
cover and its thoughts. A book already on the phone is left exactly as it is, and a
book removed on this phone since the last backup stays removed. A thought whose
media is missing from Drive comes back with its words if it has any. A voice note
that was backed up before its words were typed out comes back still waiting.

The file format and the merge are in `backup/BookFile.kt`, and the names in
`backup/DriveLayout.kt`; both are unit-tested (`BookFileTest`). As with the clips,
the real calls to Drive have not been tested against a live account.

## When the connection drops

A backup is around a hundred requests in a row on a phone's network, so something
will occasionally go wrong part-way. The first version ended the whole run on the
first dropped request, which with 82 clips is a real problem. Now:

- **Retries.** Every Drive call that fails for a reason that can pass is tried again,
  up to five tries in all, waiting 1, 2, 4 and 8 seconds in between (about 15 seconds
  before giving up). That covers dropped or reset connections, timeouts, DNS failing
  while the phone reconnects, Google answering 5xx, 408 or 429, and Drive's
  "slow down" reply, which it sends as a 403 with the reason `userRateLimitExceeded`.
  A rejected sign-in (401), a bad request, a missing file, a full Drive and
  permission problems are **not** retried, since they would fail the same way again.
  The rules are in `backup/Retry.kt`, unit-tested.
- **No duplicates from a lost reply.** A connection that drops while the answer is on
  its way back looks the same as one that dropped mid-upload, and the file may have
  arrived. Before repeating an upload the app looks in the folder for a file with
  that name *and* checksum and, if it is there, uses it. Creating a folder does the
  same. Deleting a file that is already gone counts as success.
- **The screen stays on** while a backup or restore runs (the phone may cut the
  network when the screen turns off). The run keeps going if you switch to another tab, but closing the app cancels it.
- **Nothing is lost if it still fails.** The next run skips what is already in Drive
  (see "How a backup run works"), so tapping again carries on where it stopped. The
  message says how many clips were already done and shows the system's own wording
  in brackets, which is what to look at if the problem needs investigating.

The retry loop and its rules are unit-tested. The real HTTP calls have not been
tested against a server that drops connections on purpose.

## How a restore works

1. Finds the folder and reads the clips in the month folders, plus any loose ones in
   the top folder from the first layout. If a day is in both, the month folder wins.
2. For each day in Drive that is **not** on the phone: downloads it, checks that it
   really plays as a video, and adds it to the calendar. A file that is not a
   playable video is counted as "could not be read" and skipped without stopping
   the rest.
3. Each restored day also gets its caption from `captions.json`, if there is one. A
   captions file with damaged content is skipped rather than failing the clips; a
   network error while fetching it fails the restore like any other, and running
   it again continues.
4. Days already on the phone are never touched, captions included.

Days that exist only as `.1d1s` files — a short-lived encrypted version of the
backup — cannot be opened any more. Restore reports how many there are instead of
ignoring them silently. Such files and the `1day1sec.keyinfo` file beside them can
be deleted by hand in the Drive app; the app never touches them.

## One-time Google Cloud setup

Google identifies the app by its **package name + signing certificate**. There is
no API key or client secret in the source; instead you register those two facts
in Google Cloud Console. It is free, and no billing account is needed. Google's
console is redesigned from time to time, so menu names below may differ a little.

1. **Create a project.** <https://console.cloud.google.com> → project picker →
   *New project* → name it `1day1sec`.

2. **Enable the Drive API.** With that project selected, open
   <https://console.cloud.google.com/apis/library/drive.googleapis.com> and click
   *Enable*. Skipping this gives a "Google Drive API has not been used in
   project…" error when you back up.

3. **Configure the consent screen** (called *Google Auth Platform* in the newer
   console, <https://console.cloud.google.com/auth/overview>):
   - App name and your email as support/contact email.
   - Audience: **External**.
   - Under *Audience → Test users*, **add the Google account you will back up
     with.** While the app is in *Testing* status only listed accounts can sign
     in, and Google may ask you to re-approve access periodically.
   - Under *Data access*, add the scope
     `https://www.googleapis.com/auth/drive.file`.

4. **Create an Android OAuth client.** *Clients → Create client* →
   application type **Android**, then enter:

   | Field | Value |
   | --- | --- |
   | Package name | `com.onedayonesecond` |
   | SHA-1 certificate fingerprint | the debug fingerprint below |

5. **Run the app, open the Reel tab, tap Back up now.** Google shows its consent
   screen the first time (an "unverified app" warning is normal in Testing mode:
   *Advanced → Go to 1day1sec*). Nothing needs to be pasted into the project.

### SHA-1 for debug builds

Debug builds are signed with the key in `~/.android/debug.keystore`. Print its
fingerprint with:

```
keytool -list -v -keystore ~/.android/debug.keystore \
  -alias androiddebugkey -storepass android -keypass android | grep SHA1
```

On the machine this was written on it is:

```
21:6C:80:5E:D8:0C:00:21:F2:C6:D6:46:80:DF:1D:31:D7:2D:5E:23
```

That fingerprint is **per machine**. Building on another computer, or a release
build signed with your own keystore, has a different SHA-1 — register each one
(an Android OAuth client holds one SHA-1, so create one client per fingerprint).
Installing the app on a *new phone* from the same computer does not change it.

## Troubleshooting

| Symptom | Cause |
| --- | --- |
| "No backup found in this Google account" | Restore is signed into a different Google account than the one that backed up, or nothing was backed up yet. |
| "Google rejected this app's setup (error 10)" | Package name or SHA-1 in the OAuth client does not match the build you are running. |
| "Google Drive API has not been used in project…" | Setup step 2 was skipped. The message includes the link to enable it. |
| "Access blocked: …has not completed the Google verification process" | The account is not in the *Test users* list (step 3). |
| "Google hasn't verified this app" | Normal in Testing mode. *Advanced → Go to 1day1sec (unsafe)*. |
| "The connection to Google Drive was lost" part-way through | The network dropped for longer than the automatic retries cover (about 15 seconds). Tap again: it skips the clips already uploaded and carries on. If it keeps happening at the same point, note the system wording shown in brackets. |
| "Google Drive is busy or not responding right now" | Google answered with a server error or "slow down" on every try. Wait a few minutes and tap again. |
| Sign-in fails immediately on an emulator | Use an emulator image with the **Google Play** icon; images without it have no Play Services. |

## Files

| File | Role |
| --- | --- |
| `backup/DriveAuth.kt` | Authorization API: consent screen + access token |
| `backup/DriveApi.kt` | The Drive v3 HTTPS calls (no Google client library) |
| `backup/DriveLayout.kt` | Folder and file naming; pure, unit-tested |
| `backup/JournalFile.kt` | A day's journal file and its merge; pure, unit-tested |
| `backup/BookFile.kt` | A book's file and its merge; pure, unit-tested |
| `backup/DriveBackup.kt` | Backup and restore algorithms above |
| `ui/backup/` | Screen and ViewModel |
