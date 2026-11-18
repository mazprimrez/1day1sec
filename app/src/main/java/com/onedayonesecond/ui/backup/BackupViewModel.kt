package com.onedayonesecond.ui.backup

import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.onedayonesecond.AppContainer
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.backup.AuthOutcome
import com.onedayonesecond.backup.DriveBackup
import com.onedayonesecond.backup.DriveException
import com.onedayonesecond.backup.Retry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.UnknownHostException

enum class BackupAction { BACKUP, RESTORE }

enum class BackupPhase { IDLE, AUTHORIZING, WORKING }

data class BackupUiState(
    val phase: BackupPhase = BackupPhase.IDLE,
    val action: BackupAction? = null,
    val clipCount: Int = 0,
    /** False when the phone has no second, journal day or recap to back up. */
    val hasAnything: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val notice: String? = null,
    val error: String? = null,
    /**
     * Set when Google needs to show its consent screen. The screen launches it
     * and immediately clears it, so it is only ever launched once.
     */
    val consent: PendingIntent? = null,
)

class BackupViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    private var job: Job? = null

    /** What to do once Google's consent screen (if shown) comes back. */
    private var pending: BackupAction? = null

    init {
        refreshCount()
    }

    fun start(action: BackupAction) {
        if (job?.isActive == true) return
        pending = action
        _state.update {
            it.copy(
                phase = BackupPhase.AUTHORIZING,
                action = action,
                error = null,
                notice = null,
                done = 0,
                total = 0,
            )
        }
        job = viewModelScope.launch {
            guarded {
                when (val outcome = container.driveAuth.authorize()) {
                    is AuthOutcome.Granted -> run(outcome.accessToken)
                    is AuthOutcome.NeedsConsent ->
                        _state.update { it.copy(consent = outcome.pendingIntent) }
                }
            }
        }
    }

    fun consentLaunched() {
        _state.update { it.copy(consent = null) }
    }

    /** [data] is the consent screen's result intent, or null if it was dismissed. */
    fun onConsentResult(data: Intent?) {
        job = viewModelScope.launch {
            guarded {
                val granted = container.driveAuth.fromConsentResult(data)
                if (granted == null) {
                    pending = null
                    _state.update {
                        it.copy(phase = BackupPhase.IDLE, error = "Google Drive access was not granted.")
                    }
                } else {
                    run(granted.accessToken)
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        pending = null
        _state.update { it.copy(phase = BackupPhase.IDLE, consent = null) }
    }

    private suspend fun run(accessToken: String) {
        val action = pending ?: return
        _state.update { it.copy(phase = BackupPhase.WORKING) }
        val onProgress = { done: Int, total: Int ->
            _state.update { it.copy(done = done, total = total) }
        }

        val notice = when (action) {
            BackupAction.BACKUP -> describe(container.driveBackup.backup(accessToken, onProgress))
            BackupAction.RESTORE -> describe(container.driveBackup.restore(accessToken, onProgress))
        }

        // Voice notes and pages restored without their words yet get typed out.
        if (action == BackupAction.RESTORE) container.journalActions.resumeTypingOut()

        pending = null
        _state.update { it.copy(phase = BackupPhase.IDLE, notice = notice) }
        refreshCount()
    }

    /** Also called when the tab is returned to, since things may have been added meanwhile. */
    fun refreshCount() {
        viewModelScope.launch {
            val count = container.driveBackup.clipCount()
            val any = container.driveBackup.hasAnything()
            _state.update { it.copy(clipCount = count, hasAnything = any) }
        }
    }

    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            pending = null
            _state.update { it.copy(phase = BackupPhase.IDLE, error = describe(e, it.done, it.total)) }
        }
    }

    private fun describe(summary: DriveBackup.Summary): String {
        if (summary.nothingToDo) {
            return "Nothing to back up yet. Record a second, write something or add a book first."
        }
        return buildString {
            append("Done. Seconds: ${summary.uploaded} new")
            if (summary.replaced > 0) append(", ${summary.replaced} updated")
            append(", ${summary.alreadyBackedUp} already in Drive.")
            if (summary.journalDays > 0) {
                append(" Journal saved for ${summary.journalDays} day")
                append(if (summary.journalDays == 1) "." else "s.")
            } else if (summary.hadJournal) {
                append(" Your journal was already up to date.")
            }
            if (summary.recaps > 0) {
                append(" ${summary.recaps} recap${if (summary.recaps == 1) "" else "s"} saved.")
            }
            if (summary.books > 0) {
                append(" ${summary.books} book${if (summary.books == 1) "" else "s"} saved.")
            } else if (summary.hadBooks) {
                append(" Your books were already up to date.")
            }
            if (summary.booksRemoved > 0) {
                append(" Took ${summary.booksRemoved} removed book")
                append(if (summary.booksRemoved == 1) "" else "s")
                append(" out of Drive.")
            }
            if (summary.tooLarge > 0) {
                append(" ${summary.tooLarge} file${if (summary.tooLarge == 1) " was" else "s were"}")
                append(" too large to back up and stayed on the phone only.")
            }
            if (summary.movedToMonthFolders > 0) {
                append(" Moved ${summary.movedToMonthFolders} older file")
                append(if (summary.movedToMonthFolders == 1) "" else "s")
                append(" into month folders.")
            }
            if (summary.captionsUpdated) append(" Captions saved.")
            if (summary.removedFromDrive > 0) {
                append(" Removed the old copy of ${summary.removedFromDrive} moved clip")
                append(if (summary.removedFromDrive == 1) "" else "s")
                append(" from Drive.")
            }
        }
    }

    private fun describe(summary: DriveBackup.RestoreSummary): String = buildString {
        val journalBack = summary.journalDays > 0 || summary.recaps > 0 || summary.books > 0
        when {
            summary.restored > 0 ->
                append("Restored ${summary.restored} second${if (summary.restored == 1) "" else "s"}.")
            summary.unreadable == 0 && !journalBack -> append("Everything in Drive is already on this phone.")
        }
        if (summary.journalDays > 0) {
            if (isNotEmpty()) append(' ')
            append("Journal restored for ${summary.journalDays} day${if (summary.journalDays == 1) "" else "s"}.")
        }
        if (summary.recaps > 0) {
            if (isNotEmpty()) append(' ')
            append("${summary.recaps} recap${if (summary.recaps == 1) "" else "s"} restored.")
        }
        if (summary.books > 0) {
            if (isNotEmpty()) append(' ')
            append("${summary.books} book${if (summary.books == 1) "" else "s"} restored.")
        }
        if (summary.restored > 0 && summary.alreadyOnPhone > 0) {
            append(" ${summary.alreadyOnPhone} already on this phone were left as they are.")
        }
        if (summary.unreadable > 0) {
            if (isNotEmpty()) append(' ')
            append("${summary.unreadable} could not be read and were skipped.")
        }
        if (summary.onlyEncrypted > 0) {
            if (isNotEmpty()) append(' ')
            append(
                "${summary.onlyEncrypted} day${if (summary.onlyEncrypted == 1) " is" else "s are"} " +
                    "only in an older encrypted format and cannot be restored.",
            )
        }
    }

    private fun describe(e: Exception, done: Int, total: Int): String {
        // A run that stops part-way loses nothing: what is already in Drive is skipped
        // next time. Saying so matters, because "failed" reads as "start over".
        val carryOn = if (done > 0 && total > 0) {
            " $done of $total clips were already done; tap again to carry on where it stopped."
        } else {
            ""
        }
        // The system's own wording is kept in brackets: not friendly, but it is what
        // helps if the problem needs looking into.
        val detail = e.message?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()

        return when {
            // Failed even after several retries, so the connection or Google is really down.
            e is DriveException && Retry.isRetryable(e) ->
                "Google Drive is busy or not responding right now.$carryOn$detail"
            e is DriveException -> (e.message ?: "Google Drive returned an error.") + carryOn
            e is IOException && done == 0 ->
                "Could not reach Google Drive. Check the internet connection and try again.$detail"
            e is IOException ->
                "The connection to Google Drive was lost.$carryOn$detail"
            else -> describe(e)
        }
    }

    private fun describe(e: Exception): String = when (e) {
        is DriveBackup.NoBackupException -> e.message ?: "No backup found."
        is ApiException -> when (e.statusCode) {
            // Code 10 is what Google returns when the package name / SHA-1 you
            // registered does not match the build that is running.
            CommonStatusCodes.DEVELOPER_ERROR ->
                "Google rejected this app's setup (error 10). The OAuth client's package name " +
                    "or SHA-1 does not match this build. See docs/DRIVE_BACKUP.md."
            CommonStatusCodes.NETWORK_ERROR -> "No internet connection."
            else -> "Google sign-in failed (code ${e.statusCode}). ${e.message.orEmpty()}".trim()
        }
        is UnknownHostException -> "No internet connection."
        is IOException -> e.message ?: "Network error while talking to Google Drive."
        else -> e.message ?: "Something went wrong."
    }

    override fun onCleared() {
        job?.cancel()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as OneDayApp
                BackupViewModel(app.container)
            }
        }
    }
}
