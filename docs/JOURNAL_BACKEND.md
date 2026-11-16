# The journal's AI backend

Three things in the journal need an AI model: writing out voice notes, typing out
notebook photos and pen pages, and writing the weekly, monthly and yearly recaps.
The design names the model (Gemini Flash-Lite) and says it is reached **through a
backend**, so the model's key never ships inside the app.

That backend is **not in this repository and does not exist yet.** The app side is
finished and talks to the contract below. Until a backend is running and its address
is set, the app says so in plain words (see "Without a backend") and everything else
works.

## Pointing the app at it

In `local.properties` (not committed):

```
journal.backend.url=https://your-backend.example.com
journal.backend.key=an-optional-shared-secret
```

Both end up in `BuildConfig` (`JOURNAL_BACKEND_URL`, `JOURNAL_BACKEND_KEY`). If the
key is set, every request carries `Authorization: Bearer <key>`. A key baked into an
app can be extracted from it, so treat it as a speed bump for a personal build, not
as real authentication.

The client is `ai/BackendJournalAi.kt`, behind the `JournalAi` interface. Swapping in
a different service means writing another implementation of that interface and
changing one line in `AppContainer`.

## The contract

All three are `POST`. A reply outside 200–299 is an error; its body may be
`{"error": "…"}`.

The app treats 408, 429 and 5xx as "try again later" (the entry waits and is retried
by itself), and any other error as "failed" (the user gets a "Try again" button).

### `POST /v1/transcribe`

Body: the voice note, `Content-Type: audio/mp4` (AAC, mono, 64 kbit/s, at most 5
minutes, so about 2.4 MB).

Reply: `{"text": "What was said, as plain text."}`. Empty text is a valid answer.

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

### The prompt the backend should use for recaps

From the design, in this order:

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
the backend, and from there to the model. Ordinary photos are never sent. The "It's
a notebook page" switch says so where the choice is made.
