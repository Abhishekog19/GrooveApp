@file:OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
package com.groove.music.ui.screens.library
import coil3.request.crossfade
import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.compose.animation.core.animateFloat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.groove.music.domain.model.Song
import com.groove.music.ui.screens.player.PlayerViewModel
import com.groove.music.ui.theme.*

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    playerViewModel: PlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState      by viewModel.uiState.collectAsStateWithLifecycle()
    val songs        by viewModel.filteredSongs.collectAsStateWithLifecycle(emptyList())
    val playerState  by playerViewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current

    // ── Permissions ───────────────────────────────────────────────────────────
    val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        rememberPermissionState(Manifest.permission.READ_MEDIA_AUDIO)
    else
        rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)

    // SAF folder picker — mirrors "Scan Folder" in the web setup screen
    val safLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            // Persist across reboots
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.scanSafFolder(uri)
        }
    }

    // Auto-scan MediaStore when permission is granted
    LaunchedEffect(mediaPermission.status) {
        if (mediaPermission.status.isGranted()) {
            viewModel.scanMediaStore()
        }
    }

    // Request permission on first launch if not granted
    LaunchedEffect(Unit) {
        if (!mediaPermission.status.isGranted()) {
            mediaPermission.launchPermissionRequest()
        }
    }

    // Song to delete — mirrors songToDelete state in Library.jsx
    var songToDelete by remember { mutableStateOf<Song?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(GrooveBg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // ── Header ──────────────────────────────────────────────────────
            item {
                LibraryHeader(
                    songCount   = songs.size,
                    isScanning  = uiState.isScanning,
                    scanProgress = uiState.scanProgress,
                    onRescan    = { viewModel.scanMediaStore() },
                    onAddFolder = { safLauncher.launch(null) }
                )
            }

            // ── Search bar ──────────────────────────────────────────────────
            item {
                SearchBar(
                    query     = uiState.searchQuery,
                    onChange  = { viewModel.setSearchQuery(it) },
                    modifier  = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // ── Genre filter chips (mirrors genre pills in Library.jsx) ─────
            if (uiState.genres.isNotEmpty()) {
                item {
                    GenreChips(
                        genres   = listOf("all") + uiState.genres,
                        selected = uiState.selectedGenre,
                        onSelect = { viewModel.setSelectedGenre(it) }
                    )
                }
            }

            // ── Track count ─────────────────────────────────────────────────
            item {
                Text(
                    text  = "${songs.size} ${if (songs.size == 1) "track" else "tracks"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = GrooveTextSubtle,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // ── Empty state ─────────────────────────────────────────────────
            if (songs.isEmpty() && !uiState.isScanning) {
                item { EmptyLibraryState() }
            }

            // ── Song list (mirrors track list in Library.jsx) ───────────────
            items(songs, key = { it.id }) { song ->
                SongRow(
                    song            = song,
                    isPlaying       = playerState.currentSong?.id == song.id && playerState.isPlaying,
                    isCurrentSong   = playerState.currentSong?.id == song.id,
                    index           = songs.indexOf(song),
                    onPlay          = {
                        playerViewModel.playSong(song, songs)
                        onNavigateToPlayer()
                    },
                    onAddToQueue    = { playerViewModel.addToQueue(song) },
                    onEnqueueNext   = { playerViewModel.enqueueNext(song) },
                    onToggleFavorite = { playerViewModel.toggleFavorite(song) },
                    onDelete        = { songToDelete = song }
                )
            }
        }

        // ── Scanning progress overlay ────────────────────────────────────────
        if (uiState.isScanning) {
            ScanningOverlay(
                message  = uiState.scanMessage,
                progress = uiState.scanProgress
            )
        }

        // ── Toast notification (mirrors syncToast in Library.jsx) ─────────────
        uiState.toast?.let { toast ->
            ToastBanner(
                message = toast.message,
                type    = toast.type,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
            )
        }
    }

    // ── Delete confirm dialog (mirrors DeleteModal in Library.jsx) ─────────────
    songToDelete?.let { song ->
        DeleteConfirmDialog(
            song      = song,
            onConfirm = {
                viewModel.deleteSong(song)
                songToDelete = null
            },
            onDismiss = { songToDelete = null }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LibraryHeader(
    songCount: Int,
    isScanning: Boolean,
    scanProgress: Int,
    onRescan: () -> Unit,
    onAddFolder: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.Top
    ) {
        Column {
            Text(
                text  = "Your Library",
                style = MaterialTheme.typography.headlineLarge,
                color = GrooveTextPrimary
            )
            Text(
                text  = "$songCount tracks",
                style = MaterialTheme.typography.bodySmall,
                color = GrooveTextMuted
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Re-scan button (mirrors Re-scan button in Library.jsx)
            OutlinedButton(
                onClick  = onRescan,
                enabled  = !isScanning,
                modifier = Modifier.height(36.dp),
                border   = BorderStroke(1.dp, GrooveBorder),
                shape    = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = GrooveTextMuted
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (scanProgress > 0) "Scanning ($scanProgress)…" else "Scanning…",
                        style = MaterialTheme.typography.bodySmall,
                        color = GrooveTextMuted
                    )
                } else {
                    Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Re-scan", style = MaterialTheme.typography.bodySmall)
                }
            }

            // Add Folder button (SAF picker)
            Button(
                onClick = onAddFolder,
                modifier = Modifier.height(36.dp),
                colors  = ButtonDefaults.buttonColors(containerColor = GroovePurple),
                shape   = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Folder", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value         = query,
        onValueChange = onChange,
        modifier      = modifier.fillMaxWidth(),
        placeholder   = { Text("Search title, artist or album…", color = GrooveTextSubtle) },
        leadingIcon   = { Icon(Icons.Outlined.Search, null, tint = GrooveTextSubtle) },
        trailingIcon  = if (query.isNotEmpty()) {{
            IconButton(onClick = { onChange("") }) {
                Icon(Icons.Filled.Clear, "Clear", tint = GrooveTextSubtle)
            }
        }} else null,
        singleLine    = true,
        shape         = RoundedCornerShape(12.dp),
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = GroovePurple,
            unfocusedBorderColor = GrooveBorder,
            focusedTextColor     = GrooveTextPrimary,
            unfocusedTextColor   = GrooveTextPrimary,
            cursorColor          = GroovePurple
        )
    )
}

@Composable
private fun GenreChips(genres: List<String>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(genres) { genre ->
            val isSelected = genre == selected
            Surface(
                shape  = RoundedCornerShape(20.dp),
                color  = if (isSelected) GroovePurpleDim else GrooveSurface,
                border = BorderStroke(
                    1.dp,
                    if (isSelected) GroovePurple else GrooveBorder
                ),
                modifier = Modifier.clickable { onSelect(genre) }
            ) {
                Text(
                    text  = if (genre == "all") "All" else genre,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) GroovePurpleLight else GrooveTextMuted,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun SongRow(
    song: Song,
    isPlaying: Boolean,
    isCurrentSong: Boolean,
    index: Int,
    onPlay: () -> Unit,
    onAddToQueue: () -> Unit,
    onEnqueueNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onPlay),
        color  = if (isCurrentSong) GroovePurpleDim else GrooveSurface.copy(alpha = 0f),
        shape  = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Track index / equalizer indicator (mirrors eq-bar in Library.jsx)
            Box(modifier = Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                if (isPlaying) {
                    EqualizerBars()
                } else {
                    Text(
                        text  = (index + 1).toString().padStart(2, '0'),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCurrentSong) GroovePurpleLight else GrooveTextSubtle
                    )
                }
            }

            // Album art
            CoverArt(song = song, size = 44.dp)

            // Title + artist
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = song.title,
                    style    = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color    = if (isCurrentSong) GroovePurpleLight else GrooveTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text     = "${song.artist}${if (song.album.isNotBlank()) " · ${song.album}" else ""}",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = GrooveTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Duration
            Text(
                text  = song.durationFormatted,
                style = MaterialTheme.typography.bodySmall,
                color = GrooveTextSubtle
            )

            // Favorite + context menu
            IconButton(
                onClick  = onToggleFavorite,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Filled.Favorite
                                  else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (song.isFavorite) GrooveRed else GrooveTextSubtle,
                    modifier = Modifier.size(16.dp)
                )
            }

            Box {
                IconButton(
                    onClick  = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Filled.MoreVert, "More", tint = GrooveTextSubtle,
                        modifier = Modifier.size(16.dp))
                }
                DropdownMenu(
                    expanded        = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor  = GrooveSurfaceHigh
                ) {
                    DropdownMenuItem(
                        text    = { Text("Play now") },
                        leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                        onClick = { showMenu = false; onPlay() }
                    )
                    DropdownMenuItem(
                        text    = { Text("Play next") },
                        leadingIcon = { Icon(Icons.Outlined.SkipNext, null) },
                        onClick = { showMenu = false; onEnqueueNext() }
                    )
                    DropdownMenuItem(
                        text    = { Text("Add to queue") },
                        leadingIcon = { Icon(Icons.Outlined.AddToQueue, null) },
                        onClick = { showMenu = false; onAddToQueue() }
                    )
                    HorizontalDivider(color = GrooveBorder)
                    DropdownMenuItem(
                        text    = { Text("Remove from library", color = GrooveRed) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = GrooveRed) },
                        onClick = { showMenu = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
fun CoverArt(song: Song, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(GrooveSurfaceHigh),
        contentAlignment = Alignment.Center
    ) {
        if (song.coverUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(song.coverUri)
                    .crossfade(true)
                    .build(),
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = GrooveTextSubtle,
                modifier = Modifier.size(size * 0.4f)
            )
        }
    }
}

@Composable
private fun EqualizerBars() {
    // Simple animated equalizer indicator (mirrors eq-bar CSS in Library.jsx)
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.height(14.dp)
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "eq")
        listOf(0.5f, 1f, 0.7f).forEachIndexed { i, base ->
            val anim by infiniteTransition.animateFloat(
                initialValue = base * 0.4f,
                targetValue  = base,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(
                        durationMillis = 400 + i * 80,
                        easing = androidx.compose.animation.core.LinearEasing
                    ),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "bar$i"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(anim)
                    .clip(RoundedCornerShape(2.dp))
                    .background(GroovePurpleLight)
            )
            if (i < 2) Spacer(Modifier.width(2.dp))
        }
    }
}

@Composable
private fun EmptyLibraryState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.LibraryMusic,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = GrooveTextSubtle
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Your library is empty",
            style = MaterialTheme.typography.titleMedium,
            color = GrooveTextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Tap Re-scan or Add Folder to get started",
            style = MaterialTheme.typography.bodySmall,
            color = GrooveTextMuted
        )
    }
}

@Composable
private fun ScanningOverlay(message: String, progress: Int) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        color  = GrooveSurface,
        shape  = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, GrooveBorder)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = GroovePurple
            )
            Column {
                Text(message, style = MaterialTheme.typography.bodySmall, color = GrooveTextPrimary)
                if (progress > 0) {
                    Text(
                        "$progress files scanned",
                        style = MaterialTheme.typography.bodySmall,
                        color = GrooveTextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun ToastBanner(message: String, type: ToastType, modifier: Modifier = Modifier) {
    val color = when (type) {
        ToastType.SUCCESS -> GrooveGreen
        ToastType.ERROR   -> GrooveRed
        ToastType.INFO    -> GrooveAmber
    }
    Surface(
        modifier = modifier.padding(horizontal = 16.dp),
        shape    = RoundedCornerShape(10.dp),
        color    = color.copy(alpha = 0.15f),
        border   = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            text     = message,
            style    = MaterialTheme.typography.bodySmall,
            color    = GrooveTextPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

@Composable
fun DeleteConfirmDialog(song: Song, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = GrooveSurface,
        shape            = RoundedCornerShape(18.dp),
        icon = {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = GrooveRed.copy(alpha = 0.15f)
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    null,
                    tint = GrooveRed,
                    modifier = Modifier.padding(10.dp)
                )
            }
        },
        title = { Text("Remove from library?", style = MaterialTheme.typography.titleMedium) },
        text  = {
            Column {
                Text(
                    song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GrooveTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${song.artist} · ${song.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = GrooveTextMuted
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (song.sourceType == "saf")
                        "The entry will be removed from your library. The original file on disk is NOT deleted."
                    else
                        "This cannot be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GrooveTextMuted
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors  = ButtonDefaults.buttonColors(containerColor = GrooveRed)
            ) { Text("Remove") }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border  = BorderStroke(1.dp, GrooveBorder)
            ) { Text("Keep it") }
        }
    )
}

// Accompanist permission extension
fun com.google.accompanist.permissions.PermissionStatus.isGranted(): Boolean =
    this is com.google.accompanist.permissions.PermissionStatus.Granted
