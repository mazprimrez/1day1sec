package com.onedayonesecond.ui.books

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.onedayonesecond.data.BookThought
import com.onedayonesecond.data.ProcessingState
import com.onedayonesecond.data.ThoughtType
import com.onedayonesecond.ui.common.JournalImage
import com.onedayonesecond.ui.common.PHOTO_ASPECT
import com.onedayonesecond.ui.day.TypingOutNote
import com.onedayonesecond.ui.day.VoiceChip
import com.onedayonesecond.ui.day.VoicePlayback
import com.onedayonesecond.ui.theme.BodyText
import com.onedayonesecond.ui.theme.MetaText
import java.io.File

/**
 * What a thought holds, as shown on the book page and on the day page: a voice note
 * to play, the words, a thumbnail of the page, and for a voice note still being
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
        if (thought.type == ThoughtType.VOICE) VoiceChip(voiceId, thought.mediaFile, voice)

        val text = thought.text.orEmpty()
        when {
            text.isNotEmpty() -> BodyText(text)
            thought.needsText && thought.processing == ProcessingState.DONE -> MetaText("No words could be made out.")
        }

        if (thought.type == ThoughtType.PAGE_PHOTO) {
            thought.mediaFile?.let { file ->
                JournalImage(
                    file = file,
                    description = "Photo of page ${thought.page}. Open it.",
                    modifier = Modifier
                        .width(60.dp)
                        .aspectRatio(PHOTO_ASPECT)
                        .clickable(role = Role.Button) { onView(file) },
                )
            }
        }

        if (thought.needsText && thought.processing != ProcessingState.DONE) {
            TypingOutNote(
                processing = thought.processing,
                thing = "voice note",
                spoken = true,
                canTypeOut = canTypeOut,
                onRetry = onRetry,
            )
        }
    }
}
