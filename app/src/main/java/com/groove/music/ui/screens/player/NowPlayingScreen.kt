package com.groove.music.ui.screens.player

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import coil3.request.crossfade
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.groove.music.core.network.dto.RecommendedTrack
import com.groove.music.domain.model.PlayerState
import com.groove.music.domain.model.RepeatMode
import com.groove.music.domain.model.Song
import com.groove.music.ui.screens.library.CoverArt
import com.groove.music.ui.theme.*

// ── Tab enum (Queue | Recommendations | Lyrics) ──────────────────────────────
private enum class PlayerTab { QUEUE, RECOMMENDATIONS, LYRICS }

/**
 * Full-screen Now Playing screen.
 * Mirrors Player.jsx + AudioPlayer.jsx + RecommendationsPanel.jsx combined.
 *
 * Now has three swappable panels:
 *   QUEUE           — existing queue list
 *   RECOMMENDATIONS — "You May Also Like" from /api/recommendations
 *   LYRICS          — plain-text lyrics from /api/lyrics
 */
@Composable
fun NowPlayingScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val state by playerViewModel.state.collectAsStateWithLifecycle()

    // ── Scoped ViewModels — Hilt creates one per NowPlayingScreen backstack entry
    val recommendationsVm: RecommendationsViewModel = hiltViewModel()
    val lyricsVm: LyricsViewModel = hiltViewModel()

    var activeTab by remember { mutableStateOf(PlayerTab.QUEUE) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Kick off recommendations + lyrics whenever the song changes
    val currentSong = state.currentSong
    LaunchedEffect(currentSong?.id, currentSong?.title) {
        recommendationsVm.onSongChanged(currentSong)
        lyricsVm.onSongChanged(currentSong)
    }

    val recommendationsState by recommendationsVm.state.collectAsStateWithLifecycle()
    val lyricsState          by lyricsVm.state.collectAsStateWithLifecycle()
    val playingKey           by recommendationsVm.playingKey.collectAsStateWithLifecycle()

    // Collect play errors → Snackbar
    LaunchedEffect(recommendationsVm) {
        recommendationsVm.playError.collect { msg ->
            snackbarHostState.showSnackbar(
                message  = "❌ $msg",
                duration = SnackbarDuration.Long
            )
        }
    }

    Scaffold(
        snackbarHost    = { SnackbarHost(snackbarHostState) },
        containerColor  = Color.Transparent,
        contentColor    = GrooveTextPrimary
    ) { innerPadding ->
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .background(Brush.verticalGradient(listOf(GroovePlayerGradStart, GroovePlayerGradEnd)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ── Top bar ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown, "Back",
                        tint = GrooveTextPrimary, modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "Now Playing",
                    style       = MaterialTheme.typography.bodySmall,
                    color       = GrooveTextMuted,
                    letterSpacing = 0.1.sp
                )
                Spacer(Modifier.weight(1f))
                // Panel toggle icons (right side)
                IconButton(onClick = {
                    activeTab = PlayerTab.QUEUE
                }) {
                    Icon(
                        Icons.Outlined.QueueMusic, "Queue",
                        tint = if (activeTab == PlayerTab.QUEUE) GroovePurpleLight else GrooveTextMuted
                    )
                }
            }

            // ── Tab row ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlayerTab.values().forEach { tab ->
                    val label = when (tab) {
                        PlayerTab.QUEUE           -> "Queue"
                        PlayerTab.RECOMMENDATIONS -> "Suggestions"
                        PlayerTab.LYRICS          -> "Lyrics"
                    }
                    FilterChip(
                        selected = activeTab == tab,
                        onClick  = { activeTab = tab },
                        label    = { Text(label, style = MaterialTheme.typography.bodySmall) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor    = GroovePurple,
                            selectedLabelColor        = GrooveTextPrimary,
                            containerColor            = GrooveSurfaceHigh,
                            labelColor                = GrooveTextMuted
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled           = true,
                            selected          = activeTab == tab,
                            borderColor       = GrooveBorder,
                            selectedBorderColor = GroovePurple
                        )
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Panel area (weight = 1f → fills remaining space above controls) ─
            when (activeTab) {
                PlayerTab.QUEUE -> {
                    if (state.queue.isNotEmpty()) {
                        QueuePanel(
                            queue       = state.queue,
                            currentSong = state.currentSong,
                            onPlaySong  = { song -> playerViewModel.playSong(song, state.queue) },
                            onRemove    = { song -> playerViewModel.removeFromQueue(song) },
                            modifier    = Modifier.weight(1f)
                        )
                    } else {
                        // No queue yet — show album art
                        AlbumArtSection(
                            song     = state.currentSong,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(32.dp)
                        )
                    }
                }

                PlayerTab.RECOMMENDATIONS -> {
                    RecommendationsPanel(
                        state       = recommendationsState,
                        playingKey  = playingKey,
                        onRetry     = { recommendationsVm.retry(currentSong) },
                        onPlayTrack = { track ->
                            recommendationsVm.playTrack(
                                track         = track,
                                playerViewModel = playerViewModel
                            )
                        },
                        modifier    = Modifier.weight(1f)
                    )
                }

                PlayerTab.LYRICS -> {
                    LyricsPanel(
                        state    = lyricsState,
                        onRetry  = { lyricsVm.retry(currentSong) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── Track info ────────────────────────────────────────────────────
            state.currentSong?.let { song ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Text(
                        text     = song.title,
                        style    = MaterialTheme.typography.headlineMedium,
                        color    = GrooveTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text     = "${song.artist} · ${song.album}",
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = GrooveTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Seek bar ──────────────────────────────────────────────────────
            SeekBar(
                positionMs = state.currentPositionMs,
                durationMs = state.durationMs,
                onSeek     = { playerViewModel.seekTo(it) },
                modifier   = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(Modifier.height(16.dp))

            // ── Playback controls ─────────────────────────────────────────────
            PlaybackControls(
                isPlaying       = state.isPlaying,
                shuffle         = state.shuffle,
                repeat          = state.repeat,
                onPrevious      = { playerViewModel.previousSong() },
                onTogglePlay    = { playerViewModel.togglePlay() },
                onNext          = { playerViewModel.nextSong() },
                onToggleShuffle = { playerViewModel.toggleShuffle() },
                onToggleRepeat  = { playerViewModel.toggleRepeat() },
                modifier        = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(32.dp))
        }
    }
    } // end Scaffold
}

// ─────────────────────────────────────────────────────────────────────────────
// Recommendations Panel
// Mirrors RecommendationsPanel.jsx / useRecommendations.js
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecommendationsPanel(
    state:       RecommendationsState,
    playingKey:  String?,
    onRetry:     () -> Unit,
    onPlayTrack: (RecommendedTrack) -> Unit,
    modifier:    Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        when (state) {
            RecommendationsState.Idle -> {
                Text(
                    "Play a song to see suggestions",
                    color = GrooveTextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            RecommendationsState.Loading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = GroovePurple, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Finding similar tracks…", color = GrooveTextMuted,
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            RecommendationsState.Unavailable -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Outlined.MusicOff, null, tint = GrooveTextSubtle,
                        modifier = Modifier.size(40.dp))
                    Text(
                        "No suggestions available",
                        color = GrooveTextMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = onRetry,
                        border  = BorderStroke(1.dp, GroovePurple)
                    ) {
                        Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp),
                            tint = GroovePurpleLight)
                        Spacer(Modifier.width(6.dp))
                        Text("Retry", color = GroovePurpleLight)
                    }
                }
            }

            is RecommendationsState.Ready -> {
                LazyColumn(
                    modifier            = Modifier.fillMaxSize(),
                    contentPadding      = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    item {
                        Text(
                            "You May Also Like",
                            style    = MaterialTheme.typography.titleSmall,
                            color    = GrooveTextSubtle,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                        )
                    }
                    items(state.tracks) { track ->
                        val trackKey = "${track.title}|||${track.artist}"
                        RecommendationTrackRow(
                            track      = track,
                            isResolving = playingKey == trackKey,
                            onClick    = { onPlayTrack(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationTrackRow(
    track:       RecommendedTrack,
    isResolving: Boolean = false,
    onClick:     () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isResolving, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Cover art
        if (track.albumArt != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(track.albumArt)
                    .crossfade(true)
                    .build(),
                contentDescription = track.title,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
        } else {
            Surface(
                modifier = Modifier.size(44.dp),
                shape    = RoundedCornerShape(6.dp),
                color    = GrooveSurfaceHigh
            ) {
                Icon(Icons.Filled.MusicNote, null, tint = GrooveTextSubtle,
                    modifier = Modifier.padding(10.dp))
            }
        }

        // Title + artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                style      = MaterialTheme.typography.bodyMedium,
                color      = GrooveTextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Text(
                track.artist,
                style    = MaterialTheme.typography.bodySmall,
                color    = GrooveTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Duration or resolving spinner
        if (isResolving) {
            CircularProgressIndicator(
                color     = GroovePurple,
                modifier  = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
        } else if (track.durationMs > 0) {
            Text(
                track.durationMs.let {
                    val s = it / 1000; "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = GrooveTextSubtle
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Lyrics Panel
// Mirrors useLyrics.js / LyricsPanel.jsx
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LyricsPanel(
    state:    LyricsState,
    onRetry:  () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        when (state) {
            LyricsState.Idle -> {
                Text(
                    "Play a song to see lyrics",
                    color = GrooveTextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            LyricsState.Loading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = GroovePurple, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Loading lyrics…", color = GrooveTextMuted,
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            LyricsState.Unavailable -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Outlined.MusicNote, null, tint = GrooveTextSubtle,
                        modifier = Modifier.size(40.dp))
                    Text(
                        "Lyrics not available",
                        color = GrooveTextMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = onRetry,
                        border  = BorderStroke(1.dp, GroovePurple)
                    ) {
                        Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp),
                            tint = GroovePurpleLight)
                        Spacer(Modifier.width(6.dp))
                        Text("Retry", color = GroovePurpleLight)
                    }
                }
            }

            is LyricsState.Ready -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Source badge
                    if (!state.source.isNullOrBlank()) {
                        Text(
                            "Source: ${state.source}",
                            style    = MaterialTheme.typography.labelSmall,
                            color    = GrooveTextSubtle,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                        )
                    }
                    // Scrollable lyrics text
                    val scrollState = rememberScrollState()
                    Text(
                        text      = state.lyrics,
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = GrooveTextPrimary,
                        lineHeight = 24.sp,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Existing composables (unchanged)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AlbumArtSection(song: Song?, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (song?.coverUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(song.coverUri)
                    .crossfade(true)
                    .build(),
                contentDescription = song.title,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape    = RoundedCornerShape(16.dp),
                color    = GrooveSurfaceHigh
            ) {
                Icon(
                    Icons.Filled.MusicNote, null,
                    tint     = GrooveTextSubtle,
                    modifier = Modifier.fillMaxSize().padding(48.dp)
                )
            }
        }
    }
}

@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek:     (Long) -> Unit,
    modifier:   Modifier = Modifier
) {
    val progress = if (durationMs > 0)
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Column(modifier = modifier) {
        Slider(
            value         = progress,
            onValueChange = { pct -> onSeek((pct * durationMs).toLong()) },
            colors        = SliderDefaults.colors(
                thumbColor         = GroovePurple,
                activeTrackColor   = GroovePurple,
                inactiveTrackColor = GrooveBorder
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier               = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(positionMs.toTimeString(), style = MaterialTheme.typography.bodySmall,
                color = GrooveTextMuted)
            Text(durationMs.toTimeString(), style = MaterialTheme.typography.bodySmall,
                color = GrooveTextMuted)
        }
    }
}

private fun Long.toTimeString(): String {
    val totalSecs = this / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return "$mins:${secs.toString().padStart(2, '0')}"
}

@Composable
fun PlaybackControls(
    isPlaying:       Boolean,
    shuffle:         Boolean,
    repeat:          RepeatMode,
    onPrevious:      () -> Unit,
    onTogglePlay:    () -> Unit,
    onNext:          () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat:  () -> Unit,
    modifier:        Modifier = Modifier
) {
    Row(
        modifier              = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggleShuffle) {
            Icon(Icons.Filled.Shuffle, "Shuffle",
                tint = if (shuffle) GroovePurpleLight else GrooveTextSubtle)
        }
        IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.SkipPrevious, "Previous", tint = GrooveTextPrimary,
                modifier = Modifier.size(32.dp))
        }
        Surface(onClick = onTogglePlay, shape = CircleShape, color = GrooveTextPrimary,
            modifier = Modifier.size(64.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector        = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint               = GrooveBg,
                    modifier           = Modifier.size(36.dp)
                )
            }
        }
        IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.SkipNext, "Next", tint = GrooveTextPrimary,
                modifier = Modifier.size(32.dp))
        }
        IconButton(onClick = onToggleRepeat) {
            Icon(
                imageVector = when (repeat) {
                    RepeatMode.ONE -> Icons.Filled.RepeatOne
                    else           -> Icons.Filled.Repeat
                },
                contentDescription = "Repeat",
                tint = when (repeat) {
                    RepeatMode.NONE -> GrooveTextSubtle
                    else            -> GroovePurpleLight
                }
            )
        }
    }
}

@Composable
private fun QueuePanel(
    queue:       List<Song>,
    currentSong: Song?,
    onPlaySong:  (Song) -> Unit,
    onRemove:    (Song) -> Unit,
    modifier:    Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            "Queue (${queue.size})",
            style    = MaterialTheme.typography.titleMedium,
            color    = GrooveTextPrimary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(queue, key = { _, song -> song.id }) { index, song ->
                val isCurrent = song.id == currentSong?.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isCurrent) GroovePurpleDim else Color.Transparent)
                        .clickable { onPlaySong(song) }
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        (index + 1).toString(),
                        style    = MaterialTheme.typography.bodySmall,
                        color    = GrooveTextSubtle,
                        modifier = Modifier.width(20.dp)
                    )
                    CoverArt(song = song, size = 36.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            song.title,
                            style      = MaterialTheme.typography.bodyMedium,
                            color      = if (isCurrent) GroovePurpleLight else GrooveTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                        Text(
                            song.artist,
                            style    = MaterialTheme.typography.bodySmall,
                            color    = GrooveTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!isCurrent) {
                        IconButton(onClick = { onRemove(song) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, "Remove", tint = GrooveTextSubtle,
                                modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Persistent mini player bar — mirrors the fixed bottom AudioPlayer.jsx in the web app.
 */
@Composable
fun MiniPlayerBar(
    state:        PlayerState,
    onTogglePlay: () -> Unit,
    onClick:      () -> Unit
) {
    val song = state.currentSong ?: return

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color    = GrooveSurface,
        border   = BorderStroke(1.dp, GrooveBorder)
    ) {
        val progress = if (state.durationMs > 0)
            (state.currentPositionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f

        Column {
            LinearProgressIndicator(
                progress   = { progress },
                modifier   = Modifier.fillMaxWidth().height(2.dp),
                color      = GroovePurple,
                trackColor = GrooveBorder
            )
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CoverArt(song = song, size = 44.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song.title,
                        style      = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color      = GrooveTextPrimary,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                    Text(
                        song.artist,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = GrooveTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        imageVector        = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint               = GrooveTextPrimary
                    )
                }
            }
        }
    }
}
