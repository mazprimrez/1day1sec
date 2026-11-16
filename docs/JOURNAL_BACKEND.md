# The journal's AI backend

Three things in the journal need an AI model: writing out voice notes, typing out
notebook photos and pen pages, and writing the weekly, monthly and yearly recaps.
The design names the model (Gemini Flash-Lite) and says it is reached **through a
backend**, so nothing that can reach the model ships inside the app.

The backend is in [`backend/`](../backend/README.md): a FastAPI service for Cloud Run
that calls Gemini through Vertex AI as its own service account. How to test and
deploy it is in its README. Until a backend's address is set in the app, the app says
so in plain words (see "Without a backend") and everything else works.

## The model

All three endpoints use one model, named by one setting on the backend: the
environment variable `GEMINI_MODEL`, which defaults to `gemini-3.5-flash-lite`.
Moving to a newer model is changing that value; the command is in the backend's
README.

## Pointing the app at it

In `local.properties` (not committed):

```
journal.backend.url=https://your-backend.example.com
```

It ends up in `BuildConfig.JOURNAL_BACKEND_URL`. Nothing secret is built into the app.

## Who may call it

Only one Google account, named in the backend's `ALLOWED_EMAIL`. Every request
carries `Authorization: Bearer <token>`, where the token is one of:

- a **Google access token**, which is what the app sends. The backend asks Google
  whom it belongs to and which app it was given to, and lets it through only if it
  was given to this app (`GOOGLE_CLIENT_ID`, the app's Android OAuth client), belongs
  to the allowed account with a verified address, and has not expired;
- a **Google ID token** for the same client and account, checked against Google's
  public keys.

Anyone else gets 401 (no token Google vouches for) or 403 (another account).

The app gets its token in `ai/BackendAuth.kt`, the same way it gets one for Drive
(`backup/DriveAuth.kt`), asking only for the account's email address. The first time,
Google has to ask: the app shows "Typing out and letters need to confirm your Google
account once." with an **Allow** button. After that a token comes back with no screen,
which is what lets the nightly recap job run with the app closed. If the backend
refuses a token (they last an hour), the app asks Google for a fresh one and tries
once more.

Why an access token and not an ID token: on a real phone, the calls that return an ID
token without showing a screen either were refused by Google or belong to its
deprecated sign-in library, and the nightly job cannot show a screen. The Android
OAuth client is the one made for Drive backup (docs/DRIVE_BACKUP.md); no other client
is needed.

The client is `ai/BackendJournalAi.kt`, behind the `JournalAi` interface. Swapping in
a different service means writing another implementation of that interface and
changing one line in `AppContainer`.

## The contract

All three are `POST`. A reply outside 200–299 is an error; its body may be
`{"error": "…"}`. The backend also answers each path without the `/v1` prefix.

The app treats 408, 429 and 5xx as "try again later" (the entry waits and is retried
by itself), and any other error as "failed" (the user gets a "Try again" button).
The backend chooses its statuses with that in mind:

| Status | When |
| --- | --- |
| 400 | An empty request, or a recap request that is not a period with days |
| 401, 403 | No Google token the backend accepts, or a token for another account |
| 413 | Over 10 MB of audio or image, or 2 MB of recap request |
| 415 | Not audio for `/transcribe`; not JPEG or PNG for `/extract-text` |
| 422 | The model could not read this file, or declined to |
| 429 | The model is busy |
| 500 | The model name or the service account's access is wrong. Retried by the app, so entries recover once the setting is fixed |
| 502 | The model's answer could not be used (cut short, not the agreed shape, a recap with no title or note) |
| 503 | The model could not be reached |

### `POST /v1/transcribe`

Body: the voice note, `Content-Type: audio/mp4` (AAC, mono, 64 kbit/s, at most 5
minutes, so about 2.4 MB).

Reply: `{"text": "What was said, as plain text."}`. Empty text is a valid answer.

The transcript is verbatim. The speaker mostly mixes Indonesian and English, and the
model is told to write each word in the language it was spoken in and never to
translate. Who does the transcribing is behind one function,
`transcribe(audio, mime_type)` in `backend/app/transcription.py`; using another
provider means rewriting that function only.

### `POST /v1/extract-text`

Body: one image, `Content-Type: image/jpeg` (a photo, longest side 2048 px) or
`image/png` (a pen page).

Reply: `{"text": "The handwriting or print on the page."}`. Line breaks are kept.
Empty text means nothing was written on the page.

### `POST /v1/recap`

Body, `application/json`:

```json
{
  "period": "week",
  "key": "week-2026-11-09",
  "start": "2026-11-09",
  "end": "2026-11-15",
  "name": "Eza",
  "language": "en-ID",
  "days": [
    {
      "date": "2026-11-14",
      "weekday": "Saturday",
      "mood": "GOOD",
      "place": "Kemang, Jakarta Selatan",
      "caption": "coffee before work",
      "entries": ["Coffee before work. It was the first quiet morning in weeks."]
    }
  ],
  "stats": {"daysKept": 6, "daysWritten": 5, "words": 812, "averageMood": 3.8},
  "previousStats": {"daysKept": 4, "daysWritten": 3, "words": 420, "averageMood": 3.1}
}
```

- `period` is `week`, `month` or `year`.
- `days` lists only days that have something. `mood` is `ROUGH`, `BAD`, `NEUTRAL`,
  `GOOD`, `GREAT` or `null`; `place` may be `null`; `caption` may be empty.
- `entries` are the words of that day's entries, oldest first: written text,
  transcripts and typed-out pages. Each is cut to 4000 characters for a week, 1500
  for a month and 400 for a year.
- `averageMood` is the mean on a 1 (rough) to 5 (great) scale, or `null`.
  `previousStats` is `null` when there was nothing in the period before.
- `name` may be empty.

Reply:

```json
{
  "title": "A quieter week",
  "quote": "It was the first quiet morning in weeks.",
  "quoteDate": "2026-11-14",
  "whatHappened": [{"date": "2026-11-14", "text": "A slow morning before work."}],
  "note": "One short paragraph, without a greeting.",
  "changes": [{"direction": "up", "text": "You wrote on 5 days, up from 3."}],
  "tries": ["Write one line before bed.", "Take Wednesday's walk again."]
}
```

`title` and `note` are required; without them the app treats the recap as failed.
`direction` is `up`, `down` or `same`.

### The recap prompt

The prompt is a file of its own, [`backend/prompts/recap.md`](../backend/prompts/recap.md),
so it can be tuned without touching code. It asks for what the design asks for, in
this order:

1. **What happened**: factual, short, dated lines. Nothing that is not in the entries.
2. **A note**: warm and non-judging, in the voice of a psychologist. One short
   paragraph. No greeting: the app writes "Dear Eza," itself.
3. **One quote**, taken **word for word** from the user's own entries, with the date
   of the entry it came from.
4. **What changed** against the previous period, with the numbers from `stats` and
   `previousStats`.
5. **Two gentle suggestions.** Not tasks, not a checklist.

Reply in the language the entries are written in (`language` is the phone's setting,
as a fallback), and return exactly the JSON above.

The backend holds the model to that JSON shape, cuts the suggestions to two, and
checks the quote: if the words are not in the entries it sends an empty `quote` and
`quoteDate`, and if they are in another day's entries it corrects the date.

## What the app checks for itself

- **The quote.** The model is asked for the user's own words, but models paraphrase.
  The app looks for the quote in that period's entries (ignoring case, spacing and
  quotation marks). If it is not there, the app takes a sentence from the entries
  itself. A recap never shows words the user did not write. (`JournalMath.verifyQuote`.)
- **Places.** "Where you were" on the year recap is counted on the phone from each
  day's place. Anything the model says about places is dropped.
- **Suggestions** are cut to two.
- **Enough to write from.** No request is sent for a period with fewer than three
  days that have an entry.

## When recaps are written

A WorkManager job runs each night a little after 21:00 and writes whichever of these
is due and missing (`RecapRules`):

| Recap | Due |
| --- | --- |
| Week (Monday to Sunday) | Sunday, 21:00 |
| Month | the 1st of the next month |
| Year | 31 December, 21:00 |

It also catches up the newest one of each kind after a night the phone was off. If
the phone is offline that night the failure is recorded (the recap page then says
"Your phone was offline on Sunday night. Your entries are safe.") and a second job
waits for a connection. "Write the letter now" on a recap page does the same by hand.

## Without a backend

With `journal.backend.url` empty, which is the default:

- Voice notes, notebook photos and pen pages are saved and can be played and viewed.
  In place of their text the day page says typing out is not set up in this build.
  The words can be typed by hand from the entry's options.
- Recap pages show the days, moods and "Play this week", and say that letters cannot
  be written yet.
- No request of any kind is made.

## Privacy

Everything else in the journal stays on the phone (and in the user's own Drive, if
they back up). These three calls are the exception: voice notes, photos the user
marks as notebook pages, pen pages, and the text of entries for a recap are sent to
the backend, and from there to the model (Gemini on Vertex AI, in the Google Cloud
project the backend is deployed to). The backend keeps nothing: it does not store or
log what it is sent, only that a request came and how it ended. Ordinary photos are
never sent. The "It's a notebook page" switch says so where the choice is made.
