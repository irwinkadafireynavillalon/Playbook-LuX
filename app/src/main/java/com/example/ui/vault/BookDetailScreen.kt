package com.example.ui.vault

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.entity.Book
import com.example.data.local.entity.Bookmark
import com.example.data.local.entity.Chapter
import com.example.data.local.repository.BookRepository
import com.example.playback.PlaybackConnection
import com.example.ui.components.BookCoverImage
import com.example.ui.effects.LuxGoldButton
import com.example.ui.theme.LuxGoldAccent
import com.example.ui.theme.LuxGoldBright
import com.example.ui.theme.LuxSilverBright
import com.example.ui.theme.LuxSilverText
import com.example.util.LuxPackageManager
import com.example.util.TimeUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId: String,
    bookRepository: BookRepository,
    playbackConnection: PlaybackConnection,
    onNavigateBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val book by bookRepository.getBook(bookId).collectAsStateWithLifecycle(initialValue = null)
    val chapters by bookRepository.getChapters(bookId).collectAsStateWithLifecycle(initialValue = emptyList())
    val parts by bookRepository.getParts(bookId).collectAsStateWithLifecycle(initialValue = emptyList())
    val bookmarks by bookRepository.getBookmarks(bookId).collectAsStateWithLifecycle(initialValue = emptyList())

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Capítulos, 1 = Marcadores
    var showEditDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val currentBook = book

    val createLuxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { destUri ->
        val b = currentBook
        if (destUri != null && b != null) {
            scope.launch {
                runCatching {
                    LuxPackageManager.exportBookToUri(context, b, chapters, destUri)
                }.onSuccess {
                    snackbarHostState.showSnackbar("Paquete .lux guardado correctamente")
                }.onFailure { err ->
                    snackbarHostState.showSnackbar("Error al exportar .lux: ${err.message}")
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentBook?.title ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("detail_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    if (currentBook != null) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    bookRepository.toggleFavorite(currentBook.id, currentBook.favorite)
                                }
                            },
                            modifier = Modifier.testTag("detail_fav_btn")
                        ) {
                            Icon(
                                imageVector = if (currentBook.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorito",
                                tint = if (currentBook.favorite) LuxGoldAccent else LuxSilverText
                            )
                        }

                        IconButton(onClick = { showEditDialog = true }, modifier = Modifier.testTag("detail_edit_btn")) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_book))
                        }

                        Box {
                            IconButton(
                                onClick = { showMoreMenu = true },
                                modifier = Modifier.testTag("detail_more_btn")
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.queue_play_next)) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        playbackConnection.enqueueNext(currentBook)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("\"${currentBook.title}\" se reproducirá a continuación")
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.queue_add_end)) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        playbackConnection.appendToQueue(currentBook)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("\"${currentBook.title}\" añadido a la cola")
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.share_lux_via_apps)) },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        scope.launch {
                                            runCatching {
                                                LuxPackageManager.createShareIntent(context, currentBook, chapters)
                                            }.onSuccess { intent ->
                                                context.startActivity(
                                                    Intent.createChooser(intent, context.getString(R.string.share_book_btn))
                                                )
                                            }.onFailure { err ->
                                                snackbarHostState.showSnackbar("Error al compartir .lux: ${err.message}")
                                            }
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.export_lux_to_device)) },
                                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        createLuxLauncher.launch(LuxPackageManager.buildSafeFileName(currentBook.title))
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { padding ->
        if (currentBook == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Cargando audiolibro…", color = LuxSilverText)
            }
            return@Scaffold
        }

        val progress = if (currentBook.durationMs > 0) {
            (currentBook.lastPosMs.toFloat() / currentBook.durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Compact Header: 110 dp cover on left + info on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BookCoverImage(
                    coverPath = currentBook.coverPath,
                    title = currentBook.title,
                    modifier = Modifier.size(110.dp),
                    shape = RoundedCornerShape(10.dp),
                    enable25DTilt = true,
                    interactiveTouchTilt = true
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = currentBook.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = LuxSilverBright,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = currentBook.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LuxGoldAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${TimeUtils.formatDurationHuman(currentBook.durationMs)} • ${chapters.size} cap.",
                        style = MaterialTheme.typography.labelSmall,
                        color = LuxSilverText
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Single progress bar and percentage
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = LuxGoldAccent,
                        trackColor = MaterialTheme.colorScheme.outlineVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = LuxSilverText
                        )
                        if (currentBook.finished) {
                            Text(
                                text = stringResource(R.string.finished_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = LuxGoldBright,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // UN Solo botón Continuar / Reproducir
            LuxGoldButton(
                onClick = {
                    playbackConnection.playBook(currentBook)
                    onOpenPlayer()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .height(46.dp)
                    .testTag("detail_play_btn"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.surface)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (currentBook.lastPosMs > 0L && !currentBook.finished)
                        stringResource(R.string.continue_reading)
                    else
                        stringResource(R.string.start_reading),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.surface
                )
            }

            // Tabs for Chapters and Bookmarks occupying the rest of the height
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = LuxGoldAccent
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Capítulos (${chapters.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Marcadores (${bookmarks.size})") }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (selectedTab == 0) {
                    if (parts.size > 1) {
                        parts.forEach { part ->
                            item(key = "part_${part.id}") {
                                Text(
                                    text = part.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = LuxGoldAccent,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }

                            val partChapters = chapters.filter { it.partId == part.id }
                            itemsIndexed(partChapters, key = { _, ch -> ch.id }) { _, chapter ->
                                ChapterListItem(
                                    chapter = chapter,
                                    isCurrent = currentBook.lastChapter == chapter.index,
                                    onClick = {
                                        playbackConnection.playBook(currentBook, startChapterIndex = chapter.index, startPositionMs = 0L)
                                        onOpenPlayer()
                                    }
                                )
                            }
                        }
                    } else {
                        itemsIndexed(chapters, key = { _, ch -> ch.id }) { _, chapter ->
                            ChapterListItem(
                                chapter = chapter,
                                isCurrent = currentBook.lastChapter == chapter.index,
                                onClick = {
                                    playbackConnection.playBook(currentBook, startChapterIndex = chapter.index, startPositionMs = 0L)
                                    onOpenPlayer()
                                }
                            )
                        }
                    }
                } else {
                    if (bookmarks.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(stringResource(R.string.player_bookmarks_empty), color = LuxSilverText)
                            }
                        }
                    } else {
                        items(bookmarks, key = { it.id }) { bookmark ->
                            val chapter = chapters.find { it.id == bookmark.chapterId }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clickable {
                                        val chIdx = chapter?.index ?: 0
                                        playbackConnection.playBook(currentBook, startChapterIndex = chIdx, startPositionMs = bookmark.posMs)
                                        onOpenPlayer()
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = LuxGoldAccent)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = TimeUtils.formatMs(bookmark.posMs) + (chapter?.let { " • ${it.title}" } ?: ""),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = LuxGoldBright
                                        )
                                        if (bookmark.note.isNotBlank()) {
                                            Text(
                                                text = bookmark.note,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = LuxSilverText
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                bookRepository.deleteBookmark(bookmark.id)
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Eliminar marcador", tint = LuxSilverText)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog && currentBook != null) {
        EditBookDialog(
            book = currentBook,
            chapters = chapters,
            onDismiss = { showEditDialog = false },
            onSave = { newTitle, newAuthor, newCoverPath, updatedChapters ->
                scope.launch {
                    bookRepository.updateBookInfo(currentBook.id, newTitle, newAuthor, newCoverPath)
                    updatedChapters.forEach { (chId, chTitle) ->
                        bookRepository.updateChapterTitle(chId, chTitle)
                    }
                    showEditDialog = false
                }
            }
        )
    }
}

@Composable
fun ChapterListItem(
    chapter: Chapter,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
            .testTag("chapter_item_${chapter.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isCurrent) Icons.Default.Headphones else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = if (isCurrent) LuxGoldAccent else LuxSilverText,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) LuxGoldBright else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = TimeUtils.formatMs(chapter.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = LuxSilverText
            )
        }
    }
}

