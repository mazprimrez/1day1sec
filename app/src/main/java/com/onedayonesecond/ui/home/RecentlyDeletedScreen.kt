package com.onedayonesecond.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.data.ClipAlreadyExistsException
import com.onedayonesecond.data.ClipEntity
import com.onedayonesecond.data.DeletedClip
import com.onedayonesecond.journal.RecapLabels
import com.onedayonesecond.ui.common.CLIP_ASPECT
import com.onedayonesecond.ui.common.rememberClipThumbnail
import com.onedayonesecond.ui.theme.AppDialog
import com.onedayonesecond.ui.theme.BackBar
import com.onedayonesecond.ui.theme.BodyText
import com.onedayonesecond.ui.theme.Hairline
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.QuietButton
import com.onedayonesecond.ui.theme.RoseButton
import com.onedayonesecond.ui.theme.ScreenPadding
import com.onedayonesecond.ui.theme.TileShape
import com.onedayonesecond.ui.theme.TitleText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Seconds that were deleted or replaced, kept for thirty days. Each can be put back
 * on its day, or removed for good, which is the only thing here that cannot be undone.
 */
@UnstableApi
@Composable
fun RecentlyDeletedScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as OneDayApp).container }
    val repository = container.repository
    val deleted by repository.observeDeleted().collectAsStateWithLifecycle(initialValue = null)

    var confirmReplace by remember { mutableStateOf<DeletedClip?>(null) }
    var confirmForever by remember { mutableStateOf<DeletedClip?>(null) }

    fun restore(clip: DeletedClip, replace: Boolean) {
        container.appScope.launch {
            try {
                repository.restoreDeleted(clip.id, replaceExisting = replace)
                container.messages.show("The second for ${RecapLabels.dayAndMonth(clip.date)} is back on its day.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: ClipAlreadyExistsException) {
                confirmReplace = clip
            } catch (e: Exception) {
                container.messages.show(e.message ?: "That second could not be put back.")
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        BackBar(onBack)
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(horizontal = ScreenPadding).padding(bottom = 16.dp)) {
                    TitleText("Recently deleted")
                    MetaText(
                        "Deleted and replaced seconds stay here for ${DeletedClip.KEEP_DAYS} days. " +
                            "After that they are removed for good.",
                        Modifier.padding(top = 2.dp),
                    )
                }
            }
            val rows = deleted
            if (rows != null && rows.isEmpty()) {
                item {
                    Hairline(Modifier.padding(horizontal = ScreenPadding))
                    MetaText("Nothing here.", Modifier.padding(ScreenPadding))
                }
            }
            items(rows.orEmpty(), key = { it.id }) { clip ->
                Hairline(Modifier.padding(horizontal = ScreenPadding))
                DeletedRow(
                    clip = clip,
                    onRestore = { restore(clip, replace = false) },
                    onForever = { confirmForever = clip },
                )
            }
        }
    }

    confirmReplace?.let { clip ->
        AppDialog(
            onDismiss = { confirmReplace = null },
            title = "${RecapLabels.dayAndMonth(clip.date)} has a second now",
            buttons = {
                QuietButton("Cancel", { confirmReplace = null }, Modifier.weight(1f))
                RoseButton(
                    "Swap them",
                    {
                        confirmReplace = null
                        restore(clip, replace = true)
                    },
                    Modifier.weight(1f),
                )
            },
        ) {
            BodyText(
                "Putting this one back swaps them: the second that is on that day now comes " +
                    "here, and can be brought back for ${DeletedClip.KEEP_DAYS} days.",
            )
        }
    }

    confirmForever?.let { clip ->
        AppDialog(
            onDismiss = { confirmForever = null },
            title = "Remove for good?",
            buttons = {
                QuietButton("Keep it", { confirmForever = null }, Modifier.weight(1f))
                QuietButton(
                    "Remove for good",
                    {
                        confirmForever = null
                        container.appScope.launch { repository.deleteForever(clip.id) }
                    },
                    Modifier.weight(1f),
                )
            },
        ) {
            BodyText(
                "The video for ${RecapLabels.dayAndMonth(clip.date)} ${clip.date.year} will be gone from " +
                    "this phone. This can't be undone.",
            )
        }
    }
}

@UnstableApi
@Composable
private fun DeletedRow(clip: DeletedClip, onRestore: () -> Unit, onForever: () -> Unit) {
    // The thumbnail loader works on clips; a deleted one is the same video in another folder.
    val picture = rememberClipThumbnail(
        remember(clip.id) { ClipEntity(clip.date, clip.filePath, clip.durationMs, clip.deletedAt) },
    )
    val daysLeft = DeletedClip.KEEP_DAYS -
        TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - clip.deletedAt).coerceIn(0, DeletedClip.KEEP_DAYS)

    Row(Modifier.padding(horizontal = ScreenPadding, vertical = 16.dp)) {
        Box(Modifier.width(54.dp).aspectRatio(CLIP_ASPECT).clip(TileShape).background(Palette.Blush)) {
            if (picture != null) {
                Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            BodyText(RecapLabels.fullDate(clip.date))
            if (clip.caption.isNotEmpty()) MetaText(clip.caption, maxLines = 2)
            MetaText(
                when (daysLeft) {
                    0L -> "Removed for good today"
                    1L -> "1 day left"
                    else -> "$daysLeft days left"
                },
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuietButton("Put back", onRestore)
                QuietButton("Remove", onForever, color = Palette.Muted)
            }
        }
    }
}
