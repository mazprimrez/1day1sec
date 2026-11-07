package com.onedayonesecond.backup

import com.onedayonesecond.data.ClipEntity
import com.onedayonesecond.data.ClipRepository
import com.onedayonesecond.media.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth

/**
 * Backs every clip up to the user's Drive as an ordinary `.mp4`, one folder per
 * month (see [DriveLayout]), and restores them.
 *
 * There is no local record of what was uploaded. Each run asks Drive what is
 * already there and compares checksums, so a run can be repeated safely and an
 * interrupted one simply continues on the next tap.
 */
class DriveBackup(private val repository: ClipRepository) {

    /** [movedToMonthFolders] counts older flat-layout copies that were tidied up. */
    data class Summary(
        val uploaded: Int,
        val replaced: Int,
        val alreadyBackedUp: Int,
        val movedToMonthFolders: Int = 0,
    ) {
        val total: Int get() = uploaded + replaced + alreadyBackedUp
    }

    /** [onlyEncrypted] counts days present only as old encrypted copies, which cannot be opened. */
    data class RestoreSummary(
        val restored: Int,
        val alreadyOnPhone: Int,
        val unreadable: Int,
        val onlyEncrypted: Int,
    )

    /** Raised when there is nothing in Drive to restore from. */
    class NoBackupException(message: String) : Exception(message)

    suspend fun clipCount(): Int = localClips().size

    suspend fun backup(
        accessToken: String,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Summary = withContext(Dispatchers.IO) {
        val clips = localClips()
        if (clips.isEmpty()) return@withContext Summary(0, 0, 0)

        val api = DriveApi(accessToken)
        val rootId = api.findFolder(DriveLayout.ROOT_FOLDER) ?: api.createFolder(DriveLayout.ROOT_FOLDER)
        val rootChildren = api.listChildren(rootId)

        // Month folders that already exist, and clips left loose in the top folder by
        // the first version of the backup, which had no month folders.
        val monthFolderIds: MutableMap<YearMonth, String> = rootChildren
            .filter { it.isFolder }
            .mapNotNull { folder -> DriveLayout.parseMonthFolder(folder.name)?.let { it to folder.id } }
            .toMap()
            .toMutableMap()
        val looseByName = rootChildren.filter { !it.isFolder }.groupBy { it.name }

        // Each month folder is listed once, the first time a clip of that month is reached.
        val listings = mutableMapOf<YearMonth, List<DriveApi.RemoteFile>>()

        var uploaded = 0
        var replaced = 0
        var skipped = 0
        var moved = 0
        onProgress(0, clips.size)

        clips.forEachIndexed { index, clip ->
            // Blocking network calls do not respond to cancellation, so check
            // between files — the longest uninterruptible stretch is one clip.
            ensureActive()

            val month = YearMonth.from(clip.date)
            val folderId = monthFolderIds.getOrPut(month) {
                api.createFolder(DriveLayout.monthFolderName(clip.date), rootId)
            }
            val inFolder = listings.getOrPut(month) { api.listChildren(folderId) }

            val name = DriveLayout.clipFileName(clip.date)
            val existing = inFolder.filter { it.name == name }
            val bytes = clip.file.readBytes()
            val localMd5 = md5Hex(bytes)

            if (existing.any { it.md5 == localMd5 }) {
                skipped++
            } else {
                api.upload(folderId, name, bytes, MP4_MIME)
                // Only after the new copy is safely up: drop the outdated one.
                existing.forEach { api.delete(it.id) }
                if (existing.isEmpty()) uploaded++ else replaced++
            }

            // The day now has its copy in the right month folder, so the same day's
            // loose copy in the top folder is redundant.
            looseByName[name].orEmpty().forEach {
                api.delete(it.id)
                moved++
            }

            onProgress(index + 1, clips.size)
        }

        Summary(uploaded, replaced, skipped, moved)
    }

    /**
     * Downloads every day that is in Drive but not on this phone. Days already on
     * the phone are left exactly as they are.
     */
    suspend fun restore(
        accessToken: String,
        onProgress: (done: Int, total: Int) -> Unit,
    ): RestoreSummary = withContext(Dispatchers.IO) {
        val api = DriveApi(accessToken)
        val rootId = api.findFolder(DriveLayout.ROOT_FOLDER)
            ?: throw NoBackupException("No backup found in this Google account.")
        val rootChildren = api.listChildren(rootId)

        // One file per day. Loose files from the first backup layout go in first so
        // that a copy in a month folder replaces them.
        val sources = mutableMapOf<LocalDate, DriveApi.RemoteFile>()
        val encryptedDays = mutableSetOf<LocalDate>()

        rootChildren.filter { !it.isFolder }.forEach { file ->
            DriveLayout.dateOfClip(file.name)?.let { sources[it] = file }
            DriveLayout.dateOfEncryptedClip(file.name)?.let { encryptedDays += it }
        }
        rootChildren
            .filter { it.isFolder && DriveLayout.parseMonthFolder(it.name) != null }
            .forEach { folder ->
                ensureActive()
                api.listChildren(folder.id).filter { !it.isFolder }.forEach { file ->
                    DriveLayout.dateOfClip(file.name)?.let { sources[it] = file }
                    DriveLayout.dateOfEncryptedClip(file.name)?.let { encryptedDays += it }
                }
            }

        val onlyEncrypted = encryptedDays - sources.keys
        if (sources.isEmpty()) {
            throw NoBackupException(
                if (onlyEncrypted.isEmpty()) {
                    "The backup folder in Drive has no clips in it."
                } else {
                    "The clips in Drive are in an older encrypted format that this version " +
                        "of the app can no longer open."
                },
            )
        }

        val onPhone = localClips().map { it.date }.toSet()
        val days = sources.keys.sorted()
        var restored = 0
        var already = 0
        var unreadable = 0
        onProgress(0, days.size)

        days.forEachIndexed { index, date ->
            ensureActive()
            if (date in onPhone) {
                already++
            } else if (save(date, api.download(sources.getValue(date).id))) {
                restored++
            } else {
                unreadable++
            }
            onProgress(index + 1, days.size)
        }

        RestoreSummary(restored, already, unreadable, onlyEncrypted.size)
    }

    /** Writes [bytes] as the clip for [date]. False if it does not play as a video. */
    private suspend fun save(date: LocalDate, bytes: ByteArray): Boolean {
        val scratch = repository.storage.newTrimOutputFile()
        scratch.writeBytes(bytes)
        val duration = VideoMetadata.durationMs(scratch)
        if (duration <= 0) {
            scratch.delete()
            return false
        }
        repository.saveClip(date, scratch, duration)
        return true
    }

    private suspend fun localClips(): List<ClipEntity> =
        repository.observeAll().first()
            .filter { it.file.exists() }
            .sortedBy { it.date }

    private companion object {
        const val MP4_MIME = "video/mp4"
    }
}

/** Drive reports an MD5 for every binary file; matching it means "same bytes". Not used for security. */
private fun md5Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
