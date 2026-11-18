package com.onedayonesecond.ui.books

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.onedayonesecond.data.BookThought
import com.onedayonesecond.data.ProcessingState
import com.onedayonesecond.data.ThoughtType
import com.onedayonesecond.ui.common.JournalImage
import com.onedayonesecond.ui.common.PHOTO_ASPECT
import com.onedayonesecond.ui.day.VoiceChip
import com.onedayonesecond.ui.day.VoicePlayback
import com.onedayonesecond.ui.theme.BodyText
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.QuietButton
import java.io.File

/**
 * What a thought holds, as shown on the book page and on the day page: a voice note
 * to play, a thumbnail of the page, the words, and for a voice note still being
 * written out, what is happening to it.
 *
 * @param voiceId how this thought's voice note is told from the others on the page.
 * @param canTypeOut false when this build has no AI service to write voice notes out with.
 */
@Composable
fun ThoughtBody(
    thought: BookThought,
    voice: VoicePlayback,
    voiceId: Long,
    canTypeOut: Boolean,
    onRetry: () -> Unit,
    onView: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (thought.type) {
            ThoughtType.VOICE -> VoiceChip(voiceId, thought.mediaFile, voice)
            ThoughtType.PAGE_PHOTO -> thought.mediaFile?.let { file ->
                JournalImage(
                    file = file,
                    description = "Photo of page ${thought.page}. Open it.",
                    modifier = Modifier
                        .width(96.dp)
                        .aspectRatio(PHOTO_ASPECT)
                        .clickable(role = Role.Button) { onView(file) },
                )
            }
            ThoughtType.WRITTEN -> Unit
        }

        val text = thought.text.orEmpty()
        when {
            text.isNotEmpty() -> BodyText(text)
            thought.needsText && thought.processing == ProcessingState.DONE -> MetaText("No words could be made out.")
        }

        if (thought.needsText && thought.processing != ProcessingState.DONE) {
            val (note, action) = when (thought.processing) {
                ProcessingState.PENDING -> "Writing out what you said…" to null
                ProcessingState.WAITING_FOR_NETWORK ->
                    "No internet right now. The voice note is saved on your phone. Its words will be written " +
                        "out as soon as you're back online." to "Try now"
                else -> if (canTypeOut) {
                    "This couldn't be written out. The voice note is saved on your phone." to "Try again"
                } else {
                    "Writing out isn't set up in this build of the app yet. The voice note is saved on your phone." to null
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Announced politely when it changes; it never takes focus.
                MetaText(note, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (action != null) QuietButton(action, onRetry)
            }
        }
    }
}
