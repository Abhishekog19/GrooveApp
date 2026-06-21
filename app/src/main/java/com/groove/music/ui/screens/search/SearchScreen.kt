package com.groove.music.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.groove.music.core.network.ServiceStatus
import com.groove.music.core.network.ServiceStatusChecker
import com.groove.music.core.network.dto.TidalSearchTrack
import com.groove.music.domain.model.Song
import com.groove.music.ui.components.MaintenanceBanner
import com.groove.music.ui.screens.player.PlayerViewModel
import kotlinx.coroutines.flow.collectLatest
import com.groove.music.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    playerViewModel: PlayerViewModel,
    onNavigateToPlayer: () -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
    serviceStatusChecker: ServiceStatusChecker
) {
    val query       by viewModel.query.collectAsStateWithLifecycle()
    val searchState by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val streamingTrackId = viewModel.streamingTrackId.collectAsStateWithLifecycle()

    // ── Service status check ─────────────────────────────────────────────────
    val serviceStatus by serviceStatusChecker.status.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { serviceStatusChecker.startChecking() }

    // ── Collect stream errors → show Snackbar ────────────────────────────────
    LaunchedEffect(viewModel) {
        viewModel.streamError.collectLatest { errorMsg ->
            snackbarHostState.showSnackbar(
                message  = "❌ $errorMsg",
                duration = androidx.compose.material3.SnackbarDuration.Long
            )
        }
    }

    // ── If service is down, show maintenance banner ────────────────────────
    if (serviceStatus == ServiceStatus.DOWN) {
        MaintenanceBanner(
            title = "Search Unavailable",
            subtitle = "The TIDAL streaming service is temporarily\nunder maintenance. Please try again shortly.",
            onRetry = { serviceStatusChecker.retry() }
        )
        return
    }

    // ── If still checking, show a brief loading state ─────────────────────
    if (serviceStatus == ServiceStatus.CHECKING) {
        Box(
            modifier = Modifier.fillMaxSize().background(GrooveBg),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = GroovePurple)
                Spacer(Modifier.height(12.dp))
                Text("Checking service…", color = GrooveTextMuted)
            }
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = GrooveBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Header ───────────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(GroovePurple.copy(alpha = 0.15f), GrooveBg)
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Column {
                    Text(
                        "Search",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = GrooveTextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Find songs on TIDAL — play or download instantly",
                        style = MaterialTheme.typography.bodySmall,
                        color = GrooveTextMuted
                    )
                    Spacer(Modifier.height(12.dp))

                    // ── Search bar ───────────────────────────────────────────────
                    OutlinedTextField(
                        value = query,
                        onValueChange = { viewModel.updateQuery(it) },
                        placeholder = { Text("Song, artist, or album…", color = GrooveTextSubtle) },
                        leadingIcon = {
                            Icon(Icons.Filled.Search, null, tint = GroovePurple)
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateQuery("") }) {
                                    Icon(Icons.Filled.Close, "Clear", tint = GrooveTextMuted)
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = GroovePurple,
                            unfocusedBorderColor = GrooveBorder,
                            focusedContainerColor = GrooveSurface,
                            unfocusedContainerColor = GrooveSurface,
                            focusedTextColor     = GrooveTextPrimary,
                            unfocusedTextColor   = GrooveTextPrimary,
                            cursorColor          = GroovePurple
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                focusManager.clearFocus()
                                viewModel.searchNow()
                            }
                        )
                    )
                }
            }

            // ── Content ──────────────────────────────────────────────────────────
            when (val state = searchState) {

                is SearchUiState.Idle -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.MusicNote, null,
                                modifier = Modifier.size(72.dp),
                                tint = GrooveTextSubtle
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Search for any song",
                                style = MaterialTheme.typography.bodyLarge,
                                color = GrooveTextMuted
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "▶ Play instantly from TIDAL\n⬇ Download to your library",
                                style = MaterialTheme.typography.bodySmall,
                                color = GrooveTextSubtle,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                is SearchUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = GroovePurple)
                            Spacer(Modifier.height(12.dp))
                            Text("Searching…", color = GrooveTextMuted)
                        }
                    }
                }

                is SearchUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(Icons.Filled.ErrorOutline, null,
                                modifier = Modifier.size(48.dp), tint = GrooveRed)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                state.message,
                                color = GrooveRed,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = { viewModel.searchNow() },
                                border = androidx.compose.foundation.BorderStroke(1.dp, GroovePurple)
                            ) {
                                Text("Retry", color = GroovePurple)
                            }
                        }
                    }
                }

                is SearchUiState.Results -> {
                    if (state.tracks.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.SearchOff, null,
                                    modifier = Modifier.size(56.dp), tint = GrooveTextSubtle)
                                Spacer(Modifier.height(12.dp))
                                Text("No results found", color = GrooveTextMuted)
                            }
                        }
                    } else {
                        Text(
                            "${state.tracks.size} results",
                            style = MaterialTheme.typography.labelSmall,
                            color = GrooveTextSubtle,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(state.tracks, key = { it.track.id }) { trackUi ->
                                SearchTrackRow(
                                    trackUi         = trackUi,
                                    viewModel       = viewModel,
                                    isStreaming      = streamingTrackId.value == trackUi.track.id,
                                    onPlayLocal     = {
                                        trackUi.localSong?.let { song ->
                                            playerViewModel.playSong(song)
                                            onNavigateToPlayer()
                                        }
                                    },
                                    onPlayStream    = {
                                        viewModel.playTrackFromSearch(trackUi.track, playerViewModel) {
                                            onNavigateToPlayer()
                                        }
                                    },
                                    onDownload      = { viewModel.downloadTrack(trackUi.track) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTrackRow(
    trackUi: SearchTrackUi,
    viewModel: SearchViewModel,
    isStreaming: Boolean,
    onPlayLocal: () -> Unit,
    onPlayStream: () -> Unit,
    onDownload: () -> Unit
) {
    val track = trackUi.track

    // Observe live download status from Room
    val liveStatus by viewModel.getDownloadStatusFlow(track.id.toString())
        .collectAsStateWithLifecycle()

    val effectiveStatus = liveStatus ?: trackUi.downloadStatus

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = GrooveSurface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album art
            if (track.albumArt != null) {
                AsyncImage(
                    model = track.albumArt,
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(GrooveBorder),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.MusicNote, null, tint = GrooveTextSubtle)
                }
            }

            Spacer(Modifier.width(12.dp))

            // Track info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = GrooveTextPrimary
                )
                Text(
                    track.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = GrooveTextMuted
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!track.album.isNullOrBlank()) {
                        Text(
                            track.album,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = GrooveTextSubtle,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(" · ", color = GrooveTextSubtle, fontSize = 10.sp)
                    }
                    Text(
                        trackUi.durationFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = GrooveTextSubtle
                    )
                }

                // Status chip
                Spacer(Modifier.height(2.dp))
                when (effectiveStatus) {
                    "queued"      -> StatusLabel("⏳ Queued",      Color(0xFFFFB300))
                    "resolving"   -> StatusLabel("🔍 Resolving…",  Color(0xFF64B5F6))
                    "downloading" -> StatusLabel("⬇ Downloading",  Color(0xFF4DD0E1))
                    "done"        -> StatusLabel("✅ Downloaded",   GrooveGreen)
                    "failed"      -> StatusLabel("❌ Failed",       GrooveRed)
                    else          -> if (trackUi.isInLibrary) {
                        StatusLabel("✅ In library", GrooveGreen)
                    }
                }
            }

            Spacer(Modifier.width(4.dp))

            // ── Play button (always visible) ─────────────────────────────────
            if (isStreaming) {
                // Resolving stream URL…
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp).padding(4.dp),
                    strokeWidth = 2.dp,
                    color = GrooveGreen
                )
            } else {
                IconButton(
                    onClick = {
                        if (trackUi.isInLibrary) onPlayLocal() else onPlayStream()
                    }
                ) {
                    Icon(
                        Icons.Filled.PlayCircle, "Play",
                        tint = if (trackUi.isInLibrary) GrooveGreen else GroovePurple,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            // ── Download button ──────────────────────────────────────────────
            when (effectiveStatus) {
                "done" -> {
                    Icon(Icons.Filled.CheckCircle, null,
                        tint = GrooveGreen, modifier = Modifier.size(24.dp))
                }
                "queued", "resolving", "downloading" -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).padding(2.dp),
                        strokeWidth = 2.dp,
                        color = GroovePurple
                    )
                }
                "failed" -> {
                    IconButton(onClick = onDownload, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Refresh, "Retry",
                            tint = GrooveRed, modifier = Modifier.size(22.dp))
                    }
                }
                else -> {
                    if (!trackUi.isInLibrary) {
                        IconButton(onClick = onDownload, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Download, "Download",
                                tint = GrooveTextMuted, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLabel(label: String, color: Color) {
    Text(
        label,
        fontSize = 10.sp,
        color = color,
        fontWeight = FontWeight.Medium
    )
}
