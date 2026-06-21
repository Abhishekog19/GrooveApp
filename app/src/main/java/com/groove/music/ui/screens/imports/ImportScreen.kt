package com.groove.music.ui.screens.imports

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.groove.music.core.network.ServiceStatus
import com.groove.music.core.network.ServiceStatusChecker
import com.groove.music.domain.model.Song
import com.groove.music.ui.components.MaintenanceBanner
import com.groove.music.ui.theme.*
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onPlaySong: (Song) -> Unit = {},
    viewModel: ImportViewModel = hiltViewModel(),
    serviceStatusChecker: ServiceStatusChecker
) {
    var urlInput by remember { mutableStateOf("") }
    val screenState by viewModel.state.collectAsStateWithLifecycle()
    val zipState    by viewModel.zipState.collectAsStateWithLifecycle()

    // ── Service status check ─────────────────────────────────────────────────
    val serviceStatus by serviceStatusChecker.status.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { serviceStatusChecker.startChecking() }

    // ── If service is down, show maintenance banner ────────────────────────
    if (serviceStatus == ServiceStatus.DOWN) {
        MaintenanceBanner(
            title = "Import Unavailable",
            subtitle = "The TIDAL streaming service is temporarily\nunder maintenance. Playlist import will\nbe available again shortly.",
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

    val snackbarHostState = remember { SnackbarHostState() }

    // ── Listen for download completion events → show Snackbar ─────────────────
    LaunchedEffect(Unit) {
        viewModel.downloadEvents.collectLatest { event ->
            if (event.success) {
                snackbarHostState.showSnackbar(
                    message  = "✅ \"${event.trackTitle}\" saved to Music/${event.artist}",
                    duration = SnackbarDuration.Short
                )
            } else {
                snackbarHostState.showSnackbar(
                    message  = "❌ \"${event.trackTitle}\" failed: ${event.errorMessage ?: "unknown error"}",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    // ZIP result dialog
    when (val z = zipState) {
        is ZipDownloadState.Done -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissZipState() },
                containerColor = GrooveSurface,
                icon = { Icon(Icons.Filled.CheckCircle, null, tint = GrooveGreen, modifier = Modifier.size(36.dp)) },
                title = { Text("ZIP Downloaded!", color = GrooveTextPrimary) },
                text  = {
                    Text(
                        "Saved to internal storage:\n${z.filePath.substringAfterLast("/")}",
                        color = GrooveTextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissZipState() },
                        colors = ButtonDefaults.buttonColors(containerColor = GroovePurple)
                    ) { Text("OK") }
                }
            )
        }
        is ZipDownloadState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissZipState() },
                containerColor = GrooveSurface,
                icon = { Icon(Icons.Filled.ErrorOutline, null, tint = GrooveRed, modifier = Modifier.size(36.dp)) },
                title = { Text("ZIP Failed", color = GrooveTextPrimary) },
                text  = { Text(z.message, color = GrooveTextMuted, style = MaterialTheme.typography.bodySmall) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissZipState() },
                        colors = ButtonDefaults.buttonColors(containerColor = GrooveRed)
                    ) { Text("Close") }
                }
            )
        }
        else -> {}
    }

    Scaffold(
        containerColor = GrooveBg,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData    = data,
                    containerColor  = GrooveSurface,
                    contentColor    = GrooveTextPrimary,
                    shape           = RoundedCornerShape(12.dp)
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Import Spotify Playlist",
                        color = GrooveTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GrooveBg)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // ── URL input ──────────────────────────────────────────────────────
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label         = { Text("Spotify Playlist URL") },
                placeholder   = { Text("https://open.spotify.com/playlist/...") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = GroovePurple,
                    unfocusedBorderColor = GrooveBorder,
                    focusedTextColor     = GrooveTextPrimary,
                    unfocusedTextColor   = GrooveTextPrimary,
                    cursorColor          = GroovePurple
                )
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick  = { viewModel.importPlaylist(urlInput.trim()) },
                modifier = Modifier.fillMaxWidth(),
                enabled  = urlInput.isNotBlank() && screenState !is ImportScreenState.Fetching,
                colors   = ButtonDefaults.buttonColors(containerColor = GroovePurple),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Search, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Fetch Tracks", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(16.dp))

            // ── State content ──────────────────────────────────────────────────
            when (val state = screenState) {

                is ImportScreenState.Idle -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.CloudDownload, null,
                                modifier = Modifier.size(72.dp),
                                tint = GrooveTextSubtle
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Paste a Spotify playlist URL above",
                                style = MaterialTheme.typography.bodyLarge,
                                color = GrooveTextMuted
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Tracks will be fetched and matched\nagainst your local library",
                                style = MaterialTheme.typography.bodySmall,
                                color = GrooveTextSubtle,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                is ImportScreenState.Fetching -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = GroovePurple)
                            Spacer(Modifier.height(16.dp))
                            Text(state.message, color = GrooveTextMuted)
                        }
                    }
                }

                is ImportScreenState.Error -> {
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
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                is ImportScreenState.Loaded -> {
                    val inLibraryCount  = state.tracks.count { it.isInLibrary }
                    val downloadedCount = state.tracks.count { it.downloadStatus == "done" }
                    val missingCount    = state.tracks.size - inLibraryCount - downloadedCount
                    val isZipping       = zipState is ZipDownloadState.Downloading

                    // ── Stats + action bar ─────────────────────────────────────
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp),
                        color    = GrooveSurface,
                        tonalElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "\"${state.playlistName}\"",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = GrooveTextPrimary,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "✅ $inLibraryCount · ⬇ $downloadedCount · ⏳ $missingCount missing  of ${state.tracks.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GrooveTextMuted
                                    )
                                }
                            }

                            // ── Download location hint ────────────────────────
                            Text(
                                "📂 Saved to: internal storage/Music/",
                                style = MaterialTheme.typography.labelSmall,
                                color = GrooveTextSubtle,
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            if (missingCount > 0) {
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Download All button
                                    OutlinedButton(
                                        onClick = { viewModel.downloadAllMissing() },
                                        modifier = Modifier.weight(1f),
                                        border    = androidx.compose.foundation.BorderStroke(1.dp, GroovePurple),
                                        shape     = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Filled.Download, null, modifier = Modifier.size(14.dp), tint = GroovePurple)
                                        Spacer(Modifier.width(4.dp))
                                        Text("All ($missingCount)", fontSize = 12.sp, color = GroovePurple)
                                    }

                                    // Download as ZIP button
                                    Button(
                                        onClick  = { viewModel.downloadAllAsZip() },
                                        modifier = Modifier.weight(1f),
                                        enabled  = !isZipping,
                                        colors   = ButtonDefaults.buttonColors(containerColor = GrooveBlue),
                                        shape    = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(vertical = 8.dp)
                                    ) {
                                        if (isZipping) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                                color = GrooveTextPrimary
                                            )
                                        } else {
                                            Icon(Icons.Filled.FolderZip, null,
                                                modifier = Modifier.size(14.dp))
                                        }
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            if (isZipping) "Zipping…" else "ZIP",
                                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // ZIP progress message
                                AnimatedVisibility(isZipping) {
                                    val msg = (zipState as? ZipDownloadState.Downloading)?.message ?: ""
                                    Text(
                                        msg,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GrooveBlue,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // ── Track list ─────────────────────────────────────────────
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(state.tracks, key = { it.track.spotifyId }) { trackUiState ->
                            TrackImportRow(
                                trackUiState = trackUiState,
                                onPlay       = { onPlaySong(trackUiState.localSong!!) },
                                onDownload   = { viewModel.downloadTrack(trackUiState.track) },
                                viewModel    = viewModel
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrackImportRow(
    trackUiState: TrackUiState,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    viewModel: ImportViewModel
) {
    val track = trackUiState.track

    // Observe live download status from DAO (Room Flow).
    // IMPORTANT: getDownloadStatusFlow now returns a CACHED StateFlow instance,
    // so this no longer creates a new flow on every recomposition.
    val liveStatus   by viewModel.getDownloadStatusFlow(track.spotifyId).collectAsStateWithLifecycle()
    val liveProgress by viewModel.getDownloadProgressFlow(track.spotifyId).collectAsStateWithLifecycle()

    // Prefer DB live status over in-memory state, but only when it's meaningful
    val effectiveStatus = when {
        liveStatus != null -> liveStatus
        else -> trackUiState.downloadStatus
    }
    val effectiveProgress = if (liveProgress > 0) liveProgress else trackUiState.downloadProgress

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = GrooveSurface,
        shape    = RoundedCornerShape(8.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album art
                AsyncImage(
                    model             = track.albumArt,
                    contentDescription = null,
                    modifier          = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(Modifier.width(12.dp))

                // Track info + status label
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        track.title,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color      = GrooveTextPrimary
                    )
                    Text(
                        track.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = GrooveTextMuted
                    )
                    Spacer(Modifier.height(2.dp))
                    // Status chip
                    when (effectiveStatus) {
                        "queued"      -> StatusChip("⏳ Queued",             Color(0xFFFFB300))
                        "resolving"   -> StatusChip("🔍 Finding stream…",   Color(0xFF64B5F6))
                        "downloading" -> StatusChip("⬇ $effectiveProgress%", Color(0xFF4DD0E1))
                        "done"        -> StatusChip("✅ Saved to Music/${track.artist.take(20)}",  GrooveGreen)
                        "failed"      -> StatusChip("❌ Failed — tap ⬇ to retry",  GrooveRed)
                        else          -> if (trackUiState.isInLibrary) {
                            StatusChip("✅ In library", GrooveGreen)
                        }
                    }
                }

                Spacer(Modifier.width(4.dp))

                // Action button
                when {
                    trackUiState.isInLibrary -> {
                        IconButton(onClick = onPlay) {
                            Icon(Icons.Filled.PlayCircle, "Play",
                                tint = GroovePurple, modifier = Modifier.size(32.dp))
                        }
                    }
                    effectiveStatus == "done" -> {
                        Icon(Icons.Filled.CheckCircle, null,
                            tint = GrooveGreen, modifier = Modifier.size(28.dp))
                    }
                    effectiveStatus == "queued" || effectiveStatus == "resolving" ||
                    effectiveStatus == "downloading" -> {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(28.dp).padding(4.dp),
                            strokeWidth = 2.dp,
                            color       = GroovePurple
                        )
                    }
                    effectiveStatus == "failed" -> {
                        // Show retry button for failed tracks
                        IconButton(onClick = onDownload) {
                            Icon(Icons.Filled.Refresh, "Retry",
                                tint = GrooveRed, modifier = Modifier.size(24.dp))
                        }
                    }
                    else -> {
                        IconButton(onClick = onDownload) {
                            Icon(Icons.Filled.Download, "Download",
                                tint = GrooveTextMuted, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }

            // Download progress bar (only when actually downloading)
            if (effectiveStatus == "downloading" && effectiveProgress > 0) {
                LinearProgressIndicator(
                    progress     = { effectiveProgress / 100f },
                    modifier     = Modifier.fillMaxWidth().height(2.dp),
                    color        = GroovePurple,
                    trackColor   = GrooveBorder
                )
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, color: Color) {
    Text(
        label,
        fontSize = 10.sp,
        color    = color,
        fontWeight = FontWeight.Medium
    )
}
