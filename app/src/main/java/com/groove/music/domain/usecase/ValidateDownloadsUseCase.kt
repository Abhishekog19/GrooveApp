package com.groove.music.domain.usecase

import com.groove.music.domain.repository.DownloadRepository
import javax.inject.Inject

/**
 * Validates all songs marked as isDownloaded = true in Room.
 * If the local audio file no longer exists on disk, clears the download state.
 *
 * Run on app startup (e.g. from GrooveApp or a startup ViewModel).
 * This prevents stale "Downloaded" badges after the user manually deletes files.
 */
class ValidateDownloadsUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) {
    suspend operator fun invoke() = downloadRepository.validateDownloadedFiles()
}
