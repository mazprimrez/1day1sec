# Building the app

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

Two things need setting up outside the code before every feature works:

- Google Drive backup: the one-time Google Cloud setup in
  [DRIVE_BACKUP.md](DRIVE_BACKUP.md).
- Typing out, recaps and book summaries: the backend's address in
  `local.properties`, see [JOURNAL_BACKEND.md](JOURNAL_BACKEND.md).

Book search needs no setup: it asks Open Library, which is free and needs no key.
