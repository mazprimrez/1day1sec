# Google Drive backup

The backup screen (cloud icon on the home screen) copies every clip into a
`1day1sec` folder in the user's own Google Drive, filed by month, and can restore
them on a new phone. It is opt-in: the app makes no network calls until you tap
**Back up now** or **Restore from Drive** and approve access.

## What it can and cannot see

The app asks for a single scope, `drive.file`. Google restricts that scope to
files **the app itself created** — it cannot list, read or change anything else in
the Drive. That also means the `1day1sec` folder must be created by the app; a
folder you make by hand in the Drive web UI is invisible to it.

## Privacy

Clips are uploaded as ordinary, **unencrypted** `.mp4` files, so anyone with access
to your Google account can watch them, and you can play them in the Drive app. (An
earlier version encrypted them with a passphrase; that was removed.)

## Folder layout

One folder per month, named `yyyy-MM` so they sort chronologically, holding that
month's clips named by day:

```
1day1sec/
  2026-10/
    2026-10-31.mp4
  2026-11/
    2026-11-01.mp4
    2026-11-02.mp4
```

The day is always read from the file name, never from the folder, so a file that
ends up in the wrong folder is still restored to the right day. The layout lives
in `backup/DriveLayout.kt` and is unit-tested (`DriveLayoutTest`).

## How a backup run works

There is no local record of what was uploaded. Each run:

1. Gets an access token from Google (silent after the first approval).
2. Finds or creates the `1day1sec` folder and lists what is directly inside it.
3. For every local clip, finds or creates that month's folder and lists it (once
   per month), then compares checksums with the clip in that folder:
   - same name, same MD5 → skipped;
   - same name, different MD5 (the day was re-recorded) → uploaded again, then
     the old copy is deleted;
   - not there → uploaded.
4. Once a day has its copy in the right month folder, a loose `<date>.mp4` for the
   same day in the top folder is deleted. The first version of the backup put
   everything flat in the top folder, and this tidies that up as it goes.

Because Drive is the source of truth, a run can be repeated safely and an
interrupted run continues where it stopped.

Backup is one-way: clips you delete on the phone are **not** deleted from Drive.

## How a restore works

1. Finds the folder and reads the clips in the month folders, plus any loose ones in
   the top folder from the first layout. If a day is in both, the month folder wins.
2. For each day in Drive that is **not** on the phone: downloads it, checks that it
   really plays as a video, and adds it to the calendar.
3. Days already on the phone are never touched. A file that is not a playable video
   is counted as "could not be read" and skipped without stopping the rest.

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

5. **Run the app, open the cloud icon, tap Back up now.** Google shows its consent
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
| Sign-in fails immediately on an emulator | Use an emulator image with the **Google Play** icon; images without it have no Play Services. |

## Files

| File | Role |
| --- | --- |
| `backup/DriveAuth.kt` | Authorization API: consent screen + access token |
| `backup/DriveApi.kt` | The Drive v3 HTTPS calls (no Google client library) |
| `backup/DriveLayout.kt` | Folder and file naming; pure, unit-tested |
| `backup/DriveBackup.kt` | Backup and restore algorithms above |
| `ui/backup/` | Screen and ViewModel |
