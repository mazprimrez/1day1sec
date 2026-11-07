# Media pipeline

## Why Media3 Transformer instead of FFmpeg

The trim and stitch steps both run through `androidx.media3:media3-transformer`.

- **FFmpegKit, the usual Android FFmpeg wrapper, was retired upstream.** Its
  binaries were pulled from Maven Central and the project stopped shipping
  releases. Building FFmpeg for Android by hand is possible but it is a
  standing maintenance cost for an app whose entire video workload is "cut one
  second" and "concatenate N one-second files".
- **Licensing.** FFmpeg builds that include the useful encoders land on LGPL or
  GPL terms, which is a real decision for a shipped app. Media3 is Apache 2.0.
- **Size.** An FFmpeg build adds tens of megabytes of native libraries. Media3
  drives the codecs already on the device via `MediaCodec`.
- **Hardware codecs by default.** Transformer uses the platform encoder, so a
  trim on a mid-range phone takes well under a second. A software FFmpeg encode
  would be several times slower and burn battery.

FFmpeg would win if the app needed filters, formats or container surgery that
`MediaCodec` cannot express. Trimming and concatenating do not qualify.

MediaCodec directly (no Media3) was the third option. It works, but it means
hand-writing the extractor, decoder, encoder, muxer and the timestamp
bookkeeping across clip boundaries — which is exactly the code Transformer
already is.

## Why every clip is re-encoded at trim time

`VideoSpec` pins all clips to 720x1280 H.264/AAC. `VideoTrimmer` applies a
`Presentation` effect to enforce it. This costs one encode per clip and buys
two things:

1. **Frame-accurate cuts.** A pure transmux can only cut on a keyframe, so
   asking for "the second starting at 2.3s" would silently give you something
   starting at 2.0s and running long. Because an effect is attached, Transformer
   must decode and re-encode, and the cut lands where it was asked to.
2. **A stitchable library.** Concatenating clips with mixed resolutions,
   rotations or codec profiles is where video stitching usually breaks. Paying
   for normalisation once per day, at capture time, keeps the compile step
   boring — which matters because that step gets slower every day the app is
   used.

## Importing an existing video

`GalleryImporter` takes the Uri from the Android Photo Picker and copies it into
`cacheDir/captures`, the same place a camera recording is written. From there it
is indistinguishable from a recording and the trim step handles it unchanged.

- **Why copy instead of reading the Uri directly.** Trim and Transformer work on
  files, the trim route passes a file path, and the access the picker grants to
  the original is temporary. A private copy is simpler than threading Uris
  through the navigation graph.
- **Limits.** 1 second to 5 minutes and 1 GB. Under a second there is nothing to
  keep; over five minutes the trim slider becomes too coarse to place a one-second
  window and the copy becomes heavy. The size cap is checked up front when the
  provider reports a size, and again while copying because some providers do not.
- **Cloud-only videos.** For a Google Photos item that is not on the phone,
  Android downloads it while the stream is read, so the copy can take a while; the
  camera screen shows "Importing video…" and the copy is cancelled, and the
  partial file removed, if the user leaves the screen.
- **Shape.** Normalisation to 720x1280 happens in the trim step as for any other
  clip, so a landscape video is centre-cropped to portrait.

## Compile step

`VideoCompiler` builds one `EditedMediaItemSequence` and exports it in a single
Transformer pass. `experimentalSetForceAudioTrack(true)` inserts silence for
clips recorded without microphone permission; without it, one silent clip
desynchronises everything after it.

Each clip's date is drawn at this step, not at trim time, using a Transformer
`OverlayEffect` with a `TextOverlay`. The effect list is `Presentation` first,
then the overlay, so the text is laid out against the final 720x1280 frame. Doing
it here rather than baking it into the stored clips keeps the library clean: the
date can be switched off, restyled or reformatted and the reel simply rebuilt.

Font and position come from a `DateLabelStyle` (`media/DateLabelStyle.kt`, pure
Kotlin and unit-tested). Two anchors place the caption, both in the -1..1 frame
coordinates Transformer uses: the point on the caption itself that is pinned (its
corner or edge-midpoint on the aligned side, so it grows away from the edge), and
the point on the frame it is pinned to, pulled in by a margin so it never touches
the edge. The text is white with a soft shadow rather than a background box
(`media/DateLabelSpans.kt`); the font is a `Typeface` applied through a custom
span, because the platform's own typeface span needs API 28 and minSdk is 26. One
space either side of the text leaves room for the shadow, which is drawn outside
the text's bounds and would otherwise be clipped.

The overlay holds up to four lines: the clip's caption, if it has one, wrapped over
at most three lines, and the date on the line below. Transformer's `TextOverlay`
draws with an Android `StaticLayout` (checked in the 1.5.1 classes), which is what
makes the line breaks work, but it does no wrapping of its own: text wider than the
frame is simply cut off. So `Captions.wrap` (pure, unit-tested) breaks the caption
into lines, measured with the real font, before it is handed over. An
`AlignmentSpan` on the whole text keeps the lines aligned to each other on the
chosen side; without it a shorter line would hug the left edge of a centred or
right-aligned block. The caption is drawn smaller than the date
(`CAPTION_TEXT_SIZE_PX` of 32 against `DATE_TEXT_SIZE_PX` of 40), so a smaller font
also fits more of it on each line. A clip with no caption on a
reel with dates switched off gets no overlay at all.

Runtime grows linearly with clip count — roughly a second of encoding per few
seconds of reel on current hardware. A year of clips is a multi-minute job, so
the compile screen reports progress, can be cancelled, and holds the screen on
(`keepScreenOn`) while it works, so the phone does not slow or stop the job when
the display would otherwise time out. The screen is released when the stitch
finishes or is cancelled, or when the screen is left. The job still belongs to the
screen's ViewModel, so leaving the app or the screen is not safe; a foreground
service would be needed for that.

## Export

The compiled reel lives in `cacheDir` until the user explicitly saves it.
`MediaStoreExporter` copies it into `Movies/1day1sec/` through `MediaStore`,
using `IS_PENDING` on API 29+ so gallery apps do not index a half-written file.
