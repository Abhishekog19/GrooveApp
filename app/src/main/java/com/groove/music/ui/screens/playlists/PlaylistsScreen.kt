package com.groove.music.ui.screens.playlists

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.groove.music.domain.model.Playlist
import com.groove.music.domain.model.Song
import com.groove.music.ui.screens.library.CoverArt
import com.groove.music.ui.screens.library.SongRow
import com.groove.music.ui.screens.player.PlayerViewModel
import com.groove.music.ui.theme.*

@Composable
fun PlaylistsScreen(
    playerViewModel: PlayerViewModel,
    viewModel: PlaylistViewModel = hiltViewModel()
) {
    val uiState     by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()

    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(GrooveBg)) {
        if (selectedPlaylist != null) {
            // ── Playlist detail view ────────────────────────────────────────
            PlaylistDetailScreen(
                playlist        = selectedPlaylist!!,
                playerViewModel = playerViewModel,
                playerState     = playerState,
                onBack          = { selectedPlaylist = null },
                onRename        = { id, name -> viewModel.renamePlaylist(id, name) },
                onDelete        = { id ->
                    viewModel.deletePlaylist(id)
                    selectedPlaylist = null
                },
                onRemoveSong    = { playlistId, songId ->
                    viewModel.removeSong(playlistId, songId)
                }
            )
        } else {
            // ── Playlist grid ───────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Playlists",
                                style = MaterialTheme.typography.headlineLarge,
                                color = GrooveTextPrimary
                            )
                            Text(
                                "${uiState.playlists.size} playlists",
                                style = MaterialTheme.typography.bodySmall,
                                color = GrooveTextMuted
                            )
                        }
                        Button(
                            onClick = { showCreateDialog = true },
                            colors  = ButtonDefaults.buttonColors(containerColor = GroovePurple),
                            shape   = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New Playlist")
                        }
                    }
                }

                items(uiState.playlists, key = { it.id }) { playlist ->
                    PlaylistCard(
                        playlist  = playlist,
                        onClick   = { selectedPlaylist = playlist }
                    )
                }

                if (uiState.playlists.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 80.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Outlined.QueueMusic, null,
                                modifier = Modifier.size(64.dp), tint = GrooveTextSubtle)
                            Spacer(Modifier.height(16.dp))
                            Text("No playlists yet", style = MaterialTheme.typography.titleMedium,
                                color = GrooveTextPrimary, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text("Create a playlist to organize your music",
                                style = MaterialTheme.typography.bodySmall, color = GrooveTextMuted)
                        }
                    }
                }
            }
        }
    }

    // Create playlist dialog
    if (showCreateDialog) {
        CreatePlaylistDialog(
            onConfirm = { name, emoji ->
                viewModel.createPlaylist(name, emoji)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }
}

@Composable
private fun PlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color  = GrooveSurface,
        shape  = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Emoji / art representation
            Surface(
                modifier = Modifier.size(52.dp),
                shape    = RoundedCornerShape(10.dp),
                color    = GrooveSurfaceHigh,
                border   = BorderStroke(1.dp, GrooveBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val emoji = playlist.emoji ?: if (playlist.isAutoGenerated) "📁" else "🎵"
                    Text(emoji, style = MaterialTheme.typography.headlineMedium)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    style    = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color    = GrooveTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${playlist.songs.size} songs" +
                        if (playlist.isAutoGenerated) " · Auto-generated" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = GrooveTextMuted
                )
            }

            Icon(Icons.Filled.ChevronRight, null, tint = GrooveTextSubtle)
        }
    }
}

@Composable
private fun PlaylistDetailScreen(
    playlist: Playlist,
    playerViewModel: PlayerViewModel,
    playerState: com.groove.music.domain.model.PlayerState,
    onBack: () -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onRemoveSong: (Long, Long) -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = GrooveTextPrimary)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = GrooveTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))

                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, "More", tint = GrooveTextPrimary)
                    }
                    DropdownMenu(
                        expanded         = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor   = GrooveSurfaceHigh
                    ) {
                        if (!playlist.isAutoGenerated) {
                            DropdownMenuItem(
                                text    = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                onClick = { showMenu = false; showRenameDialog = true }
                            )
                        }
                        DropdownMenuItem(
                            text    = { Text("Delete", color = GrooveRed) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = GrooveRed) },
                            onClick = { showMenu = false; showDeleteDialog = true }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Play all button
            if (playlist.songs.isNotEmpty()) {
                Button(
                    onClick = { playerViewModel.playSong(playlist.songs.first(), playlist.songs) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GroovePurple),
                    shape  = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Play All (${playlist.songs.size} songs)")
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        items(playlist.songs, key = { it.id }) { song ->
            SongRow(
                song             = song,
                isPlaying        = playerState.currentSong?.id == song.id && playerState.isPlaying,
                isCurrentSong    = playerState.currentSong?.id == song.id,
                index            = playlist.songs.indexOf(song),
                onPlay           = { playerViewModel.playSong(song, playlist.songs) },
                onAddToQueue     = { playerViewModel.addToQueue(song) },
                onEnqueueNext    = { playerViewModel.enqueueNext(song) },
                onToggleFavorite = { playerViewModel.toggleFavorite(song) },
                onDelete         = { onRemoveSong(playlist.id, song.id) }
            )
        }

        if (playlist.songs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No songs in this playlist", color = GrooveTextMuted)
                }
            }
        }
    }

    if (showRenameDialog) {
        RenamePlaylistDialog(
            currentName = playlist.name,
            onConfirm   = { newName -> onRename(playlist.id, newName); showRenameDialog = false },
            onDismiss   = { showRenameDialog = false }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor   = GrooveSurface,
            title = { Text("Delete playlist?") },
            text  = { Text("\"${playlist.name}\" will be permanently deleted.", color = GrooveTextMuted) },
            confirmButton = {
                Button(
                    onClick = { onDelete(playlist.id) },
                    colors  = ButtonDefaults.buttonColors(containerColor = GrooveRed)
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteDialog = false },
                    border  = BorderStroke(1.dp, GrooveBorder)
                ) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun CreatePlaylistDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name  by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🎵") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = GrooveSurface,
        shape            = RoundedCornerShape(18.dp),
        title = { Text("New Playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("Playlist name") },
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
                OutlinedTextField(
                    value         = emoji,
                    onValueChange = { emoji = it.takeLast(2) },
                    label         = { Text("Emoji") },
                    singleLine    = true,
                    modifier      = Modifier.width(80.dp),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = GroovePurple,
                        unfocusedBorderColor = GrooveBorder,
                        focusedTextColor     = GrooveTextPrimary,
                        unfocusedTextColor   = GrooveTextPrimary,
                        cursorColor          = GroovePurple
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick  = { if (name.isNotBlank()) onConfirm(name.trim(), emoji) },
                enabled  = name.isNotBlank(),
                colors   = ButtonDefaults.buttonColors(containerColor = GroovePurple)
            ) { Text("Create") }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border  = BorderStroke(1.dp, GrooveBorder)
            ) { Text("Cancel") }
        }
    )
}

@Composable
private fun RenamePlaylistDialog(
    currentName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = GrooveSurface,
        title = { Text("Rename Playlist") },
        text  = {
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
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
        },
        confirmButton = {
            Button(
                onClick  = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled  = name.isNotBlank(),
                colors   = ButtonDefaults.buttonColors(containerColor = GroovePurple)
            ) { Text("Save") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, border = BorderStroke(1.dp, GrooveBorder)) {
                Text("Cancel")
            }
        }
    )
}
