package com.example.ui.playlists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.entity.Book
import com.example.data.local.entity.Chapter
import com.example.data.local.entity.Playlist
import com.example.data.local.entity.PlaylistItem
import com.example.data.local.repository.BookRepository
import com.example.data.local.repository.PlaylistRepository
import com.example.playback.PlaybackConnection
import com.example.ui.components.EmptyStateView
import com.example.ui.theme.LuxGoldAccent
import com.example.ui.theme.LuxGoldBright
import com.example.ui.theme.LuxSilverText
import kotlinx.coroutines.launch

@Composable
fun PlaylistsScreen(
    playlistRepository: PlaylistRepository,
    bookRepository: BookRepository,
    playbackConnection: PlaybackConnection,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val playlists by playlistRepository.allPlaylists.collectAsStateWithLifecycle(initialValue = emptyList())
    val allItems by playlistRepository.allPlaylistItems.collectAsStateWithLifecycle(initialValue = emptyList())
    val books by bookRepository.activeBooks.collectAsStateWithLifecycle(initialValue = emptyList())

    var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }
    val selectedPlaylist = playlists.firstOrNull { it.id == selectedPlaylistId }

    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var showAddBookDialog by remember { mutableStateOf(false) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    var repeatEnabled by remember { mutableStateOf(false) }

    val chaptersCache = remember { mutableStateMapOf<String, List<Chapter>>() }
    LaunchedEffect(books) {
        books.forEach { b ->
            if (!chaptersCache.containsKey(b.id)) {
                chaptersCache[b.id] = bookRepository.getChaptersSync(b.id)
            }
        }
    }

    if (selectedPlaylist != null) {
        BackHandler { selectedPlaylistId = null }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (selectedPlaylist == null) {
                // Module Header: Listas
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Listas de reproducción",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = LuxGoldBright
                        )
                        Text(
                            text = if (playlists.isEmpty()) "Combina libros o capítulos" else "${playlists.size} listas guardadas",
                            style = MaterialTheme.typography.labelSmall,
                            color = LuxSilverText
                        )
                    }
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = LuxGoldAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("playlists_new_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.surface)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.playlist_new), color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                if (playlists.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                        title = stringResource(R.string.playlist_empty_title),
                        description = stringResource(R.string.playlist_empty_desc),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize().testTag("playlists_list")
                    ) {
                        items(playlists, key = { it.id }) { pl ->
                            val count = allItems.count { it.playlistId == pl.id }
                            Card(
                                onClick = { selectedPlaylistId = pl.id },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("playlist_card_${pl.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = LuxGoldAccent.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.size(46.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.PlaylistPlay,
                                                contentDescription = null,
                                                tint = LuxGoldAccent,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pl.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (pl.description.isNotBlank()) {
                                            Text(
                                                text = pl.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = LuxSilverText,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = stringResource(R.string.playlist_items_count, count),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = LuxGoldAccent
                                        )
                                    }
                                    IconButton(onClick = { playlistToRename = pl }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Editar lista", tint = LuxSilverText, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = {
                                        scope.launch {
                                            playlistRepository.deletePlaylist(pl.id)
                                            snackbarHostState.showSnackbar("Lista eliminada")
                                        }
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Eliminar lista", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Playlist Detail View
                val playlistItems = allItems
                    .filter { it.playlistId == selectedPlaylist.id }
                    .sortedBy { it.position }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedPlaylistId = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver a listas", tint = LuxGoldAccent)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedPlaylist.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = LuxGoldBright,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${playlistItems.size} elementos",
                            style = MaterialTheme.typography.labelSmall,
                            color = LuxSilverText
                        )
                    }
                    OutlinedButton(
                        onClick = { showAddBookDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = LuxGoldAccent)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Añadir", fontSize = 12.sp, color = LuxGoldAccent)
                    }
                }

                // Controls: Play, Shuffle, Repeat
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        enabled = playlistItems.isNotEmpty(),
                        onClick = {
                            val resolvedEntries: List<Pair<Book, Chapter?>> = playlistItems.mapNotNull { item ->
                                val book = books.firstOrNull { it.id == item.bookId } ?: return@mapNotNull null
                                val chapter = item.chapterId?.let { chId ->
                                    chaptersCache[book.id]?.firstOrNull { it.id == chId }
                                }
                                book to chapter
                            }
                            if (resolvedEntries.isNotEmpty()) {
                                playbackConnection.playPlaylist(
                                    playlistName = selectedPlaylist.name,
                                    entries = resolvedEntries,
                                    shuffle = shuffleEnabled,
                                    repeatAll = repeatEnabled
                                )
                                onOpenPlayer()
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Los libros de esta lista ya no están en la Bóveda.")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LuxGoldAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("playlist_play_btn")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.surface)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reproducir", color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold)
                    }

                    FilterChip(
                        selected = shuffleEnabled,
                        onClick = { shuffleEnabled = !shuffleEnabled },
                        label = { Text(stringResource(R.string.playlist_shuffle), fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(15.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LuxGoldAccent.copy(alpha = 0.25f),
                            selectedLabelColor = LuxGoldBright
                        )
                    )

                    FilterChip(
                        selected = repeatEnabled,
                        onClick = { repeatEnabled = !repeatEnabled },
                        label = { Text(stringResource(R.string.playlist_repeat), fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(15.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LuxGoldAccent.copy(alpha = 0.25f),
                            selectedLabelColor = LuxGoldBright
                        )
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(top = 4.dp)
                )

                if (playlistItems.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Default.LibraryMusic,
                        title = "Lista vacía",
                        description = "Pulsa \"+ Añadir\" para incluir audiolibros completos o capítulos sueltos.",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(playlistItems, key = { _, item -> item.id }) { index, item ->
                            val book = books.firstOrNull { it.id == item.bookId }
                            val chapter = item.chapterId?.let { chId ->
                                chaptersCache[item.bookId]?.firstOrNull { it.id == chId }
                            }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}.",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = LuxGoldAccent,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(26.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = chapter?.title ?: book?.title ?: "Audiolibro no disponible",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (chapter != null) "${book?.title ?: ""} • Capítulo" else "${book?.author ?: ""} • Libro completo",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = LuxSilverText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        enabled = index > 0,
                                        onClick = {
                                            scope.launch {
                                                val mutable = playlistItems.toMutableList()
                                                val moved = mutable.removeAt(index)
                                                mutable.add(index - 1, moved)
                                                playlistRepository.updateItemsOrder(mutable)
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Subir", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        enabled = index < playlistItems.lastIndex,
                                        onClick = {
                                            scope.launch {
                                                val mutable = playlistItems.toMutableList()
                                                val moved = mutable.removeAt(index)
                                                mutable.add(index + 1, moved)
                                                playlistRepository.updateItemsOrder(mutable)
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Bajar", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch { playlistRepository.removeItem(item.id) }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Quitar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
        )
    }

    // Create Playlist Dialog
    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.playlist_create_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.playlist_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text(stringResource(R.string.playlist_desc_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = name.isNotBlank(),
                    onClick = {
                        scope.launch {
                            val newId = playlistRepository.createPlaylist(name, desc)
                            selectedPlaylistId = newId
                            showCreateDialog = false
                        }
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Rename Playlist Dialog
    playlistToRename?.let { pl ->
        var name by remember { mutableStateOf(pl.name) }
        var desc by remember { mutableStateOf(pl.description) }
        AlertDialog(
            onDismissRequest = { playlistToRename = null },
            title = { Text("Editar lista") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Descripción") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    enabled = name.isNotBlank(),
                    onClick = {
                        scope.launch {
                            playlistRepository.updatePlaylist(pl.copy(name = name.trim(), description = desc.trim()))
                            playlistToRename = null
                        }
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { playlistToRename = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Add Book / Chapter to Playlist Dialog
    if (showAddBookDialog && selectedPlaylist != null) {
        val currentCount = allItems.count { it.playlistId == selectedPlaylist.id }
        var expandedBookId by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddBookDialog = false },
            title = { Text("Añadir desde la Bóveda") },
            text = {
                if (books.isEmpty()) {
                    Text("No tienes audiolibros en la Bóveda todavía. Convierte un texto o importa un archivo de audio primero.")
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.height(320.dp)
                    ) {
                        items(books, key = { it.id }) { book ->
                            val chapters = chaptersCache[book.id].orEmpty()
                            val isExpanded = expandedBookId == book.id
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch {
                                                playlistRepository.addItem(
                                                    playlistId = selectedPlaylist.id,
                                                    bookId = book.id,
                                                    chapterId = null,
                                                    position = currentCount
                                                )
                                                showAddBookDialog = false
                                                snackbarHostState.showSnackbar("\"${book.title}\" añadido a la lista")
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(book.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${book.author} • ${chapters.size} cap.", style = MaterialTheme.typography.labelSmall, color = LuxSilverText)
                                    }
                                    if (chapters.size > 1) {
                                        IconButton(onClick = {
                                            expandedBookId = if (isExpanded) null else book.id
                                        }) {
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = "Ver capítulos",
                                                tint = LuxGoldAccent
                                            )
                                        }
                                    }
                                }
                                if (isExpanded) {
                                    chapters.forEach { ch ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    scope.launch {
                                                        playlistRepository.addItem(
                                                            playlistId = selectedPlaylist.id,
                                                            bookId = book.id,
                                                            chapterId = ch.id,
                                                            position = currentCount
                                                        )
                                                        showAddBookDialog = false
                                                        snackbarHostState.showSnackbar("\"${ch.title}\" añadido")
                                                    }
                                                }
                                                .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = LuxGoldAccent)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(ch.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddBookDialog = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }
}
