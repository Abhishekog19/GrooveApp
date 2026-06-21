package com.groove.music.data.download

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Provides the target [File] for a downloaded audio track.
 *
 * Storage location: /sdcard/Music/Groove/<Artist - Title.ext>
 * Files are flat inside Groove/ — no artist subfolders.
 *
 * This directory is visible in the system file manager and
 * survives an app uninstall/reinstall.
 *
 * Requires READ_MEDIA_AUDIO (API 33+) or READ_EXTERNAL_STORAGE (API ≤32).
 */
object DownloadStorageHelper {

    /**
     * @param artist  Track artist (used in filename only)
     * @param title   Track title
     * @param ext     File extension, e.g. "flac", "m4a" (default: "flac")
     * @return        File reference inside Music/Groove/ on shared storage
     */
    fun getTargetFile(artist: String, title: String, ext: String = "flac"): File {
        val safeArtist = sanitize(artist).take(40)
        val safeTitle  = sanitize(title).take(80)

        val musicDir  = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val grooveDir = File(musicDir, "Groove")
        grooveDir.mkdirs()

        return File(grooveDir, "$safeArtist - $safeTitle.$ext")
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()
}
