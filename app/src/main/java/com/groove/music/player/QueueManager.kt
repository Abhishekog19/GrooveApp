package com.groove.music.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.groove.music.domain.model.RepeatMode
import com.groove.music.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "QueueManager"

/**
 * Wraps MediaController to expose a clean API for queue operations.
 *
 * Key improvements over the original:
 *  - connect() now has try/catch so a failed future is logged, not silently swallowed.
 *  - pendingPlay holds a (song, queue) pair that is replayed the moment the controller
 *    becomes available — fixes the race condition where playSong() is called before
 *    the MediaController finishes its async bind to GroovePlaybackService.
 */
@Singleton
class QueueManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    var controller: MediaController? = null
        private set

    // Buffered play request — replayed once controller connects
    private var pendingPlay: Pair<Song, List<Song>>? = null

    fun connect(onReady: (MediaController) -> Unit) {
        val token = SessionToken(
            context,
            ComponentName(context, GroovePlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, token).buildAsync()
        controllerFuture!!.addListener({
            try {
                controller = controllerFuture!!.get()
                Log.d(TAG, "MediaController connected successfully")
                onReady(controller!!)

                // Replay any play command that arrived before the controller was ready
                pendingPlay?.let { (song, queue) ->
                    Log.d(TAG, "Replaying pending play: ${song.title}")
                    play(song, queue)
                    pendingPlay = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "MediaController connection failed: ${e.message}", e)
            }
        }, context.mainExecutor)
    }

    fun release() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        pendingPlay = null
    }

    // ── Playback control ──────────────────────────────────────────────────────

    /**
     * Play a song with a given queue.
     * If the controller isn't ready yet, buffers the request and replays it on connect.
     * Mirrors: playSong(song) / playWithQueue(song, queue) in usePlayerStore.
     */
    fun play(song: Song, queue: List<Song>) {
        val ctrl = controller
        if (ctrl == null) {
            // Controller not connected yet — buffer the request
            Log.w(TAG, "Controller not ready — buffering play for: ${song.title}")
            pendingPlay = Pair(song, queue)
            return
        }

        val mediaItems = queue.map { it.toMediaItem() }
        val startIndex = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        ctrl.setMediaItems(mediaItems, startIndex, 0L)
        ctrl.prepare()
        ctrl.play()
        Log.d(TAG, "Playing: ${song.title} (index $startIndex of ${queue.size})")
    }

    /** Mirrors: enqueueNext(song) — inserts immediately after current track. */
    fun enqueueNext(song: Song) {
        val ctrl = controller ?: return
        val insertIndex = (ctrl.currentMediaItemIndex + 1).coerceAtMost(ctrl.mediaItemCount)
        ctrl.addMediaItem(insertIndex, song.toMediaItem())
    }

    /** Mirrors: addToQueue(song) — appends to end of queue. */
    fun addToQueue(song: Song) {
        val ctrl = controller ?: return
        ctrl.addMediaItem(song.toMediaItem())
    }

    /** Mirrors: removeFromQueue(songId). */
    fun removeFromQueue(song: Song) {
        val ctrl = controller ?: return
        for (i in 0 until ctrl.mediaItemCount) {
            if (ctrl.getMediaItemAt(i).mediaId == song.id.toString()) {
                ctrl.removeMediaItem(i)
                break
            }
        }
    }

    fun setRepeatMode(mode: RepeatMode) {
        val ctrl = controller ?: return
        ctrl.repeatMode = when (mode) {
            RepeatMode.NONE -> Player.REPEAT_MODE_OFF
            RepeatMode.ONE  -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL  -> Player.REPEAT_MODE_ALL
        }
    }

    fun setShuffleEnabled(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun setVolume(volume: Float) {
        controller?.volume = volume.coerceIn(0f, 1f)
    }

    val isConnected: Boolean get() = controller != null
}

/**
 * Convert a domain Song to a Media3 MediaItem.
 * The filePath is used as the media URI (content:// or file://).
 */
fun Song.toMediaItem(): MediaItem {
    val uri = Uri.parse(filePath ?: return MediaItem.EMPTY)
    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(coverUri?.let { Uri.parse(it) })
                .build()
        )
        .build()
}
