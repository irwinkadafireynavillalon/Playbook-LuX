package com.example.ui.vault

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.entity.Book
import com.example.data.local.entity.TextItem
import com.example.ui.components.BookCoverImage
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ListeningStatsDialog
import com.example.ui.theme.LuxGoldAccent
import com.example.ui.theme.LuxGoldBright
import com.example.ui.theme.LuxSilverText
import com.example.util.LuxPackageManager
import com.example.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    onNavigateToSettings: () -> Unit,
    onOpenBookDetail: (String) -> Unit,
    onNavigateToTranslator: (String?) -> Unit = {},
    onNavigateToConverter: (String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recentPlayedBooks by viewModel.recentPlayedBooks.collectAsStateWithLifecycle()
    val totalListenedSeconds by viewModel.totalListenedSeconds.collectAsStateWithLifecycle()
    val recentListeningStats by viewModel.recentListeningStats.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showPasteTextDialog by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var folderToDelete by remember { mutableStateOf<Long?>(null) }
    var bookToMove by remember { mutableStateOf<Book?>(null) }
    var bookToDelete by remember { mutableStateOf<Book?>(null) }
    var bookToEdit by remember { mutableStateOf<Book?>(null) }
    var bookToShare by remember { mutableStateOf<Book?>(null) }
    var pendingExportBook by remember { mutableStateOf<Book?>(null) }
    var showMoveSelectionDialog by remember { mutableStateOf(false) }
    var showDeleteSelectionDialog by remember { mutableStateOf(false) }

    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importFileFromUri(context, uri)
        }
    }

    val createLuxFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { destUri ->
        val book = pendingExportBook
        pendingExportBook = null
        if (destUri != null && book != null) {
            viewModel.exportBookAsLuxToUri(context, book, destUri)
        }
    }

    val openSupportedFilePicker = {
        openFileLauncher.launch(
            arrayOf(
                "text/*",
                "text/html",
                "text/markdown",
                "application/epub+zip",
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/octet-stream"
            )
        )
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Selection Mode Bar OR Standard Controls Row
            if (uiState.isSelectionMode) {
                SelectionTopBar(
                    selectedCount = uiState.selectedBookIds.size + uiState.selectedTextIds.size,
                    onClearSelection = { viewModel.clearSelection() },
                    onMoveToFolder = { showMoveSelectionDialog = true },
                    onDeleteSelection = { showDeleteSelectionDialog = true }
                )
            } else {
                // Compact Action Controls: Search, Import file, Paste text, Filter/Sort, Grid/List
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { viewModel.toggleSearchExpanded() },
                            modifier = Modifier.size(40.dp).testTag("vault_search_toggle_btn")
                        ) {
                            Icon(
                                imageVector = if (uiState.isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Buscar",
                                tint = if (uiState.searchQuery.isNotEmpty()) LuxGoldBright else LuxGoldAccent
                            )
                        }

                        OutlinedButton(
                            onClick = openSupportedFilePicker,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp).testTag("vault_import_file_btn")
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp), tint = LuxGoldBright)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Importar", fontSize = 12.sp, color = LuxGoldBright, fontWeight = FontWeight.SemiBold)
                        }

                        IconButton(
                            onClick = { showPasteTextDialog = true },
                            modifier = Modifier.size(40.dp).testTag("vault_paste_text_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.NoteAdd,
                                contentDescription = "Escribir o pegar texto",
                                tint = LuxGoldAccent
                            )
                        }
                    }

                    // Right: Stats, Filter/Sort BottomSheet & Grid/List view toggle
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showStatsDialog = true },
                            modifier = Modifier.size(40.dp).testTag("vault_stats_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = stringResource(R.string.stats_dialog_title),
                                tint = LuxGoldAccent
                            )
                        }

                        IconButton(
                            onClick = { viewModel.showFilterSheet(true) },
                            modifier = Modifier.size(40.dp).testTag("vault_filter_sheet_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = stringResource(R.string.filter_sort_title),
                                tint = if (uiState.currentFilter != VaultFilter.ALL || uiState.currentSort != VaultSort.RECENT) LuxGoldBright else LuxGoldAccent
                            )
                        }

                        IconButton(
                            onClick = { viewModel.toggleGridView() },
                            modifier = Modifier.size(40.dp).testTag("vault_view_toggle_btn")
                        ) {
                            Icon(
                                imageVector = if (uiState.isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                contentDescription = if (uiState.isGridView) stringResource(R.string.view_mode_list) else stringResource(R.string.view_mode_grid),
                                tint = LuxGoldAccent
                            )
                        }
                    }
                }

                // Collapsible Search Input
                AnimatedVisibility(
                    visible = uiState.isSearchExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = LuxGoldBright, modifier = Modifier.size(20.dp))
                            },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Limpiar búsqueda", tint = LuxSilverText)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = LuxGoldBright,
                                unfocusedBorderColor = LuxSilverText.copy(alpha = 0.45f),
                                cursorColor = LuxGoldBright
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("vault_search_input")
                        )
                    }
                }
            }

            // Two Tabs: "Audiolibros" and "Textos"
            TabRow(
                selectedTabIndex = uiState.currentTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = LuxGoldAccent,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[uiState.currentTab]),
                        color = LuxGoldAccent,
                        height = 3.dp
                    )
                },
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Tab(
                    selected = uiState.currentTab == 0,
                    onClick = { viewModel.setCurrentTab(0) },
                    text = {
                        Text(
                            text = "${stringResource(R.string.tab_audiobooks)} (${uiState.books.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (uiState.currentTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (uiState.currentTab == 0) LuxGoldBright else LuxSilverText
                        )
                    },
                    modifier = Modifier.testTag("tab_audiobooks")
                )
                Tab(
                    selected = uiState.currentTab == 1,
                    onClick = { viewModel.setCurrentTab(1) },
                    text = {
                        Text(
                            text = "${stringResource(R.string.tab_texts)} (${uiState.texts.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (uiState.currentTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (uiState.currentTab == 1) LuxGoldBright else LuxSilverText
                        )
                    },
                    modifier = Modifier.testTag("tab_texts")
                )
            }

            // Folder Bar (Common to both Audiolibros and Textos)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = uiState.selectedFolderId == null,
                        onClick = { viewModel.selectFolder(null) },
                        label = { Text(stringResource(R.string.folder_all)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LuxGoldAccent.copy(alpha = 0.2f),
                            selectedLabelColor = LuxGoldBright
                        ),
                        modifier = Modifier.testTag("folder_chip_all")
                    )
                }

                items(uiState.folders, key = { it.id }) { folder ->
                    var showFolderMenu by remember { mutableStateOf(false) }
                    Box {
                        FilterChip(
                            selected = uiState.selectedFolderId == folder.id,
                            onClick = { viewModel.selectFolder(folder.id) },
                            label = { Text(folder.name) },
                            leadingIcon = {
                                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { showFolderMenu = true },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Opciones carpeta", modifier = Modifier.size(14.dp))
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LuxGoldAccent.copy(alpha = 0.2f),
                                selectedLabelColor = LuxGoldBright
                            ),
                            modifier = Modifier.testTag("folder_chip_${folder.id}")
                        )

                        DropdownMenu(
                            expanded = showFolderMenu,
                            onDismissRequest = { showFolderMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.folder_rename)) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    showFolderMenu = false
                                    folderToRename = folder.id to folder.name
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.folder_delete), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showFolderMenu = false
                                    folderToDelete = folder.id
                                }
                            )
                        }
                    }
                }

                item {
                    IconButton(
                        onClick = { showCreateFolderDialog = true },
                        modifier = Modifier.size(36.dp).testTag("folder_add_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.folder_new), tint = LuxGoldAccent)
                    }
                }
            }

            // Main Content: Audiolibros Tab vs Textos Tab
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (uiState.currentTab == 0) {
                    // Audiolibros List/Grid
                    if (uiState.books.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            EmptyStateView(
                                icon = Icons.AutoMirrored.Filled.MenuBook,
                                title = stringResource(R.string.vault_empty_title),
                                description = stringResource(R.string.vault_empty_desc)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = openSupportedFilePicker,
                                colors = ButtonDefaults.buttonColors(containerColor = LuxGoldAccent)
                            ) {
                                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.surface)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Importar libro o texto", color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (uiState.isGridView) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 145.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize().testTag("vault_books_grid")
                        ) {
                            items(uiState.books, key = { it.id }) { book ->
                                val isSelected = uiState.selectedBookIds.contains(book.id)
                                BookGridCard(
                                    book = book,
                                    isSelected = isSelected,
                                    isSelectionMode = uiState.isSelectionMode,
                                    onClick = {
                                        if (uiState.isSelectionMode) {
                                            viewModel.toggleSelectBook(book.id)
                                        } else {
                                            onOpenBookDetail(book.id)
                                        }
                                    },
                                    onLongClick = {
                                        viewModel.toggleSelectBook(book.id)
                                    },
                                    onPlay = { viewModel.playBook(book) },
                                    onPlayNext = { viewModel.enqueueBookNext(book) },
                                    onAddToQueue = { viewModel.appendBookToQueue(book) },
                                    onToggleFavorite = { viewModel.toggleFavorite(book) },
                                    onEdit = { bookToEdit = book },
                                    onMoveToFolder = { bookToMove = book },
                                    onDelete = { bookToDelete = book },
                                    onShare = { bookToShare = book }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize().testTag("vault_books_list")
                        ) {
                            items(uiState.books, key = { it.id }) { book ->
                                val isSelected = uiState.selectedBookIds.contains(book.id)
                                BookListItem(
                                    book = book,
                                    isSelected = isSelected,
                                    isSelectionMode = uiState.isSelectionMode,
                                    onClick = {
                                        if (uiState.isSelectionMode) {
                                            viewModel.toggleSelectBook(book.id)
                                        } else {
                                            onOpenBookDetail(book.id)
                                        }
                                    },
                                    onLongClick = {
                                        viewModel.toggleSelectBook(book.id)
                                    },
                                    onPlay = { viewModel.playBook(book) },
                                    onPlayNext = { viewModel.enqueueBookNext(book) },
                                    onAddToQueue = { viewModel.appendBookToQueue(book) },
                                    onToggleFavorite = { viewModel.toggleFavorite(book) },
                                    onEdit = { bookToEdit = book },
                                    onMoveToFolder = { bookToMove = book },
                                    onDelete = { bookToDelete = book },
                                    onShare = { bookToShare = book }
                                )
                            }
                        }
                    }
                } else {
                    // Textos Tab
                    if (uiState.texts.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            EmptyStateView(
                                icon = Icons.AutoMirrored.Filled.MenuBook,
                                title = stringResource(R.string.vault_texts_empty_title),
                                description = stringResource(R.string.vault_texts_empty_desc)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = openSupportedFilePicker,
                                    colors = ButtonDefaults.buttonColors(containerColor = LuxGoldAccent)
                                ) {
                                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.surface)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Importar archivo", color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { showPasteTextDialog = true },
                                    modifier = Modifier.testTag("paste_text_empty_btn")
                                ) {
                                    Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp), tint = LuxGoldAccent)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pegar texto", color = LuxGoldAccent)
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize().testTag("vault_texts_list")
                        ) {
                            items(uiState.texts, key = { it.id }) { textItem ->
                                val isSelected = uiState.selectedTextIds.contains(textItem.id)
                                TextItemCard(
                                    item = textItem,
                                    isSelected = isSelected,
                                    isSelectionMode = uiState.isSelectionMode,
                                    onClick = {
                                        if (uiState.isSelectionMode) {
                                            viewModel.toggleSelectText(textItem.id)
                                        }
                                    },
                                    onLongClick = {
                                        viewModel.toggleSelectText(textItem.id)
                                    },
                                    onTranslate = { onNavigateToTranslator(textItem.id) },
                                    onConvert = { onNavigateToConverter(textItem.id) },
                                    onDelete = { viewModel.deleteText(textItem.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp)
        )
    }

    // Modal Bottom Sheet: Filters & Sorting (B3)
    if (uiState.isFilterSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.showFilterSheet(false) },
            sheetState = bottomSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.filter_sort_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LuxGoldBright
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.filter_by_status),
                    style = MaterialTheme.typography.labelMedium,
                    color = LuxSilverText
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VaultFilter.entries.forEach { filterOption ->
                        val selected = uiState.currentFilter == filterOption
                        val label = when (filterOption) {
                            VaultFilter.ALL -> stringResource(R.string.filter_all)
                            VaultFilter.IN_PROGRESS -> stringResource(R.string.filter_in_progress)
                            VaultFilter.COMPLETED -> stringResource(R.string.filter_completed)
                            VaultFilter.FAVORITES -> stringResource(R.string.filter_favorites)
                        }
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.setFilter(filterOption) },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LuxGoldAccent.copy(alpha = 0.25f),
                                selectedLabelColor = LuxGoldBright
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.sort_by),
                    style = MaterialTheme.typography.labelMedium,
                    color = LuxSilverText
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VaultSort.entries.forEach { sortOption ->
                        val selected = uiState.currentSort == sortOption
                        val label = when (sortOption) {
                            VaultSort.RECENT -> stringResource(R.string.sort_recent)
                            VaultSort.TITLE -> stringResource(R.string.sort_title)
                            VaultSort.AUTHOR -> stringResource(R.string.sort_author)
                            VaultSort.DURATION -> stringResource(R.string.sort_duration)
                        }
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.setSort(sortOption) },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LuxGoldAccent.copy(alpha = 0.25f),
                                selectedLabelColor = LuxGoldBright
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.showFilterSheet(false) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = LuxGoldAccent)
                ) {
                    Text(stringResource(R.string.ok), color = MaterialTheme.colorScheme.surface)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Dialog: Create Folder (B5)
    if (showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text(stringResource(R.string.folder_new)) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    placeholder = { Text(stringResource(R.string.folder_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("folder_create_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderName.isNotBlank()) {
                            viewModel.createFolder(folderName)
                            showCreateFolderDialog = false
                        }
                    },
                    modifier = Modifier.testTag("folder_create_confirm_btn")
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Dialog: Rename Folder (B5)
    folderToRename?.let { (id, currentName) ->
        var newName by remember { mutableStateOf(currentName) }
        AlertDialog(
            onDismissRequest = { folderToRename = null },
            title = { Text(stringResource(R.string.folder_rename)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.renameFolder(id, newName)
                            folderToRename = null
                        }
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToRename = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Dialog: Delete Folder (B5)
    folderToDelete?.let { folderId ->
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text(stringResource(R.string.folder_delete)) },
            text = { Text(stringResource(R.string.folder_delete_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFolder(folderId)
                        folderToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Dialog: Move Single Book to Folder (B5)
    bookToMove?.let { book ->
        AlertDialog(
            onDismissRequest = { bookToMove = null },
            title = { Text(stringResource(R.string.move_to_folder)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.no_folder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.moveBookToFolder(book.id, null)
                                bookToMove = null
                            }
                            .padding(vertical = 12.dp)
                    )
                    uiState.folders.forEach { folder ->
                        Text(
                            text = folder.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.moveBookToFolder(book.id, folder.id)
                                    bookToMove = null
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { bookToMove = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Dialog: Move Selection to Folder
    if (showMoveSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showMoveSelectionDialog = false },
            title = { Text(stringResource(R.string.move_to_folder)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.no_folder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.moveSelectedToFolder(null)
                                showMoveSelectionDialog = false
                            }
                            .padding(vertical = 12.dp)
                    )
                    uiState.folders.forEach { folder ->
                        Text(
                            text = folder.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.moveSelectedToFolder(folder.id)
                                    showMoveSelectionDialog = false
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveSelectionDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Dialog: Delete Selection Confirmation
    if (showDeleteSelectionDialog) {
        val totalCount = uiState.selectedBookIds.size + uiState.selectedTextIds.size
        AlertDialog(
            onDismissRequest = { showDeleteSelectionDialog = false },
            title = { Text("¿Eliminar elementos seleccionados?") },
            text = { Text("Se eliminarán $totalCount elementos seleccionados. Los audiolibros se moverán a la papelera de reciclaje.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSelected()
                        showDeleteSelectionDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectionDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Dialog: Delete Single Book Confirmation (B7)
    bookToDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { bookToDelete = null },
            title = { Text(stringResource(R.string.delete_book_confirm_title)) },
            text = { Text(stringResource(R.string.delete_book_confirm_desc, book.title)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.moveToRecycle(book.id)
                        bookToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("delete_book_confirm_btn")
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { bookToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Dialog: Edit Book (B6)
    bookToEdit?.let { book ->
        EditBookDialog(
            book = book,
            onDismiss = { bookToEdit = null },
            onSave = { newTitle, newAuthor ->
                viewModel.updateBookInfo(book, newTitle, newAuthor)
                bookToEdit = null
            }
        )
    }

    // Dialog: Paste / Write custom text directly
    if (showPasteTextDialog) {
        var textTitle by remember { mutableStateOf("") }
        var textAuthor by remember { mutableStateOf("") }
        var textBody by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPasteTextDialog = false },
            title = { Text("Nuevo texto para convertir o traducir") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = textTitle,
                        onValueChange = { textTitle = it },
                        label = { Text("Título") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = textAuthor,
                        onValueChange = { textAuthor = it },
                        label = { Text("Autor (opcional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = textBody,
                        onValueChange = { textBody = it },
                        label = { Text("Pega o escribe el texto aquí") },
                        minLines = 5,
                        maxLines = 9,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = textTitle.isNotBlank() && textBody.isNotBlank(),
                    onClick = {
                        viewModel.createPastedText(context, textTitle, textAuthor, textBody)
                        showPasteTextDialog = false
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteTextDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Dialog: Export or Share .lux package (B8)
    bookToShare?.let { book ->
        AlertDialog(
            onDismissRequest = { bookToShare = null },
            title = { Text(stringResource(R.string.share_lux_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.share_lux_dialog_desc, book.title),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            val selected = book
                            bookToShare = null
                            viewModel.shareBookAsLux(context, selected) { shareIntent ->
                                context.startActivity(
                                    Intent.createChooser(shareIntent, context.getString(R.string.share_book_btn))
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LuxGoldAccent),
                        modifier = Modifier.fillMaxWidth().testTag("share_lux_intent_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.surface)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.share_lux_via_apps), color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            pendingExportBook = book
                            bookToShare = null
                            createLuxFileLauncher.launch(LuxPackageManager.buildSafeFileName(book.title))
                        },
                        modifier = Modifier.fillMaxWidth().testTag("export_lux_file_btn")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp), tint = LuxGoldBright)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.export_lux_to_device), color = LuxGoldBright)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { bookToShare = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showStatsDialog) {
        ListeningStatsDialog(
            totalListenedSeconds = totalListenedSeconds,
            recentStats = recentListeningStats,
            recentBooks = recentPlayedBooks,
            onDismiss = { showStatsDialog = false },
            onPlayBook = { book -> viewModel.playBook(book) },
            onOpenBookDetail = { book -> onOpenBookDetail(book.id) }
        )
    }
}

@Composable
fun SelectionTopBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onMoveToFolder: () -> Unit,
    onDeleteSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 6.dp,
        modifier = modifier.fillMaxWidth().height(48.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClearSelection, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_clear_selection))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.selected_items_count, selectedCount),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = LuxGoldBright
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMoveToFolder, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = stringResource(R.string.move_to_folder), tint = LuxGoldAccent)
                }
                IconButton(onClick = onDeleteSelection, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookGridCard(
    book: Book,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onMoveToFolder: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val progress = if (book.durationMs > 0) {
        (book.lastPosMs.toFloat() / book.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) LuxGoldAccent else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("book_grid_item_${book.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                BookCoverImage(
                    coverPath = book.coverPath,
                    title = book.title,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
                    enable25DTilt = true,
                    interactiveTouchTilt = false
                )

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(LuxGoldAccent.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = LuxGoldBright,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                if (!isSelectionMode) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(36.dp)
                            .testTag("fav_btn_${book.id}")
                    ) {
                        Icon(
                            imageVector = if (book.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorito",
                            tint = if (book.favorite) LuxGoldAccent else LuxSilverText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (book.finished) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Terminado",
                            tint = LuxGoldBright,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = LuxGoldAccent,
                trackColor = MaterialTheme.colorScheme.outlineVariant
            )

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.labelSmall,
                    color = LuxSilverText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = TimeUtils.formatDurationHuman(book.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = LuxGoldAccent
                    )

                    if (!isSelectionMode) {
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Opciones", modifier = Modifier.size(16.dp))
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.queue_play_next)) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onPlayNext()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.queue_add_end)) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onAddToQueue()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.edit_book)) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onEdit()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.move_to_folder)) },
                                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onMoveToFolder()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.share_book_btn)) },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onShare()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        onDelete()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookListItem(
    book: Book,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onMoveToFolder: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val progress = if (book.durationMs > 0) {
        (book.lastPosMs.toFloat() / book.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) LuxGoldAccent else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("book_list_item_${book.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookCoverImage(
                coverPath = book.coverPath,
                title = book.title,
                modifier = Modifier.size(60.dp),
                shape = RoundedCornerShape(6.dp),
                enable25DTilt = true,
                interactiveTouchTilt = false
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.labelSmall,
                    color = LuxSilverText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(3.dp),
                    color = LuxGoldAccent,
                    trackColor = MaterialTheme.colorScheme.outlineVariant
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = TimeUtils.formatDurationHuman(book.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = LuxGoldAccent
                    )
                    if (book.finished) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• ${stringResource(R.string.finished_badge)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = LuxGoldBright,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (!isSelectionMode) {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (book.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorito",
                        tint = if (book.favorite) LuxGoldAccent else LuxSilverText,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opciones", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.queue_play_next)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onPlayNext()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.queue_add_end)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onAddToQueue()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit_book)) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.move_to_folder)) },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onMoveToFolder()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_book_btn)) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onShare()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TextItemCard(
    item: TextItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTranslate: () -> Unit,
    onConvert: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) LuxGoldAccent else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("text_card_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Format badge
                    Surface(
                        color = LuxGoldAccent.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = item.format.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = LuxGoldBright,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    val statusLabel = when (item.status) {
                        "PREPARED" -> stringResource(R.string.status_prepared)
                        "CONVERTED" -> stringResource(R.string.status_converted)
                        else -> stringResource(R.string.status_unprepared)
                    }
                    val statusColor = when (item.status) {
                        "PREPARED" -> LuxGoldAccent
                        "CONVERTED" -> LuxGoldBright
                        else -> LuxSilverText
                    }

                    Text(
                        text = "• $statusLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }

                if (!isSelectionMode) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = LuxSilverText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${item.author} • ${item.wordCount} palabras",
                style = MaterialTheme.typography.labelMedium,
                color = LuxSilverText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons: Traducir and Convertir
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onTranslate,
                    modifier = Modifier.weight(1f).height(38.dp).testTag("translate_text_${item.id}"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp), tint = LuxGoldAccent)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_translate), fontSize = 12.sp, color = LuxGoldAccent)
                }

                Button(
                    onClick = onConvert,
                    modifier = Modifier.weight(1f).height(38.dp).testTag("convert_text_${item.id}"),
                    colors = ButtonDefaults.buttonColors(containerColor = LuxGoldAccent),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.surface)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_convert), fontSize = 12.sp, color = MaterialTheme.colorScheme.surface)
                }
            }
        }
    }
}

@Composable
fun EditBookDialog(
    book: Book,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf(book.author) }
    var titleError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_book)) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        titleError = it.isBlank()
                    },
                    label = { Text(stringResource(R.string.book_title_label)) },
                    isError = titleError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_book_title_input")
                )
                if (titleError) {
                    Text(
                        text = stringResource(R.string.book_title_empty_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text(stringResource(R.string.book_author_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_book_author_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(title.trim(), author.trim())
                    } else {
                        titleError = true
                    }
                },
                modifier = Modifier.testTag("edit_book_save_btn")
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
