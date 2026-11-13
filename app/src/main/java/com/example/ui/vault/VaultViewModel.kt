package com.example.ui.vault

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.datastore.SettingsStore
import com.example.data.local.entity.Book
import com.example.data.local.entity.Folder
import com.example.data.local.entity.ListeningStat
import com.example.data.local.entity.TextItem
import com.example.data.local.repository.BookRepository
import com.example.data.local.repository.FolderRepository
import com.example.data.local.repository.RecycleRepository
import com.example.data.local.repository.TextItemRepository
import com.example.playback.PlaybackConnection
import com.example.util.LuxPackageManager
import com.example.util.TestAudioGenerator
import com.example.util.TextFileExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class VaultFilter {
    ALL,
    IN_PROGRESS,
    COMPLETED,
    FAVORITES
}

enum class VaultSort {
    RECENT,
    TITLE,
    AUTHOR,
    DURATION
}

data class VaultFilterState(
    val query: String = "",
    val filter: VaultFilter = VaultFilter.ALL,
    val sort: VaultSort = VaultSort.RECENT,
    val folderId: Long? = null,
    val currentTab: Int = 0 // 0 = Audiolibros, 1 = Textos
)

data class VaultSelectionState(
    val selectedBooks: Set<String> = emptySet(),
    val selectedTexts: Set<String> = emptySet(),
    val isSearchExpanded: Boolean = false,
    val isFilterSheetVisible: Boolean = false
)

data class VaultSourceData(
    val books: List<Book> = emptyList(),
    val texts: List<TextItem> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val isGrid: Boolean = true
)

data class VaultUiState(
    val books: List<Book> = emptyList(),
    val texts: List<TextItem> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val selectedFolderId: Long? = null,
    val searchQuery: String = "",
    val currentFilter: VaultFilter = VaultFilter.ALL,
    val currentSort: VaultSort = VaultSort.RECENT,
    val isGridView: Boolean = true,
    val currentTab: Int = 0, // 0 = Audiolibros, 1 = Textos
    val selectedBookIds: Set<String> = emptySet(),
    val selectedTextIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val isSearchExpanded: Boolean = false,
    val isFilterSheetVisible: Boolean = false,
    val isCreatingTestBook: Boolean = false,
    val message: String? = null
)

class VaultViewModel(
    private val bookRepository: BookRepository,
    private val folderRepository: FolderRepository,
    private val recycleRepository: RecycleRepository,
    private val textItemRepository: TextItemRepository,
    private val settingsStore: SettingsStore,
    private val playbackConnection: PlaybackConnection
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _filter = MutableStateFlow(VaultFilter.ALL)
    private val _sort = MutableStateFlow(VaultSort.RECENT)
    private val _selectedFolderId = MutableStateFlow<Long?>(null)
    private val _currentTab = MutableStateFlow(0)
    private val _selectedBookIds = MutableStateFlow<Set<String>>(emptySet())
    private val _selectedTextIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isSearchExpanded = MutableStateFlow(false)
    private val _isFilterSheetVisible = MutableStateFlow(false)
    private val _isCreatingTestBook = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)

    val recentPlayedBooks: StateFlow<List<Book>> = bookRepository.recentlyPlayedBooks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val totalListenedSeconds: StateFlow<Long> = (
        bookRepository.totalListenedSeconds?.map { it ?: 0L } ?: flowOf(0L)
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0L
    )

    val recentListeningStats: StateFlow<List<ListeningStat>> = (
        bookRepository.getRecentListeningStats(14) ?: flowOf(emptyList())
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val sourceData = combine(
        bookRepository.activeBooks,
        textItemRepository.allTextItems,
        folderRepository.allFolders,
        settingsStore.isGridView
    ) { activeBooks, allTexts, folders, isGrid ->
        VaultSourceData(
            books = activeBooks,
            texts = allTexts,
            folders = folders,
            isGrid = isGrid
        )
    }

    private val filterState = combine(
        _searchQuery,
        _filter,
        _sort,
        _selectedFolderId,
        _currentTab
    ) { query, filter, sort, folderId, tab ->
        VaultFilterState(
            query = query,
            filter = filter,
            sort = sort,
            folderId = folderId,
            currentTab = tab
        )
    }

    private val selectionState = combine(
        _selectedBookIds,
        _selectedTextIds,
        _isSearchExpanded,
        _isFilterSheetVisible
    ) { selectedBooks, selectedTexts, isSearch, isFilterSheet ->
        VaultSelectionState(
            selectedBooks = selectedBooks,
            selectedTexts = selectedTexts,
            isSearchExpanded = isSearch,
            isFilterSheetVisible = isFilterSheet
        )
    }

    val uiState: StateFlow<VaultUiState> = combine(
        sourceData,
        filterState,
        selectionState
    ) { source, filter, sel ->
        val selectedBooks = sel.selectedBooks
        val selectedTexts = sel.selectedTexts
        val isSelectionMode = selectedBooks.isNotEmpty() || selectedTexts.isNotEmpty()

        // Filter books
        var filteredBooks = source.books
        if (filter.folderId != null) {
            filteredBooks = if (filter.folderId == -1L) {
                filteredBooks.filter { it.folderId == null }
            } else {
                filteredBooks.filter { it.folderId == filter.folderId }
            }
        }
        if (filter.query.isNotBlank()) {
            val q = filter.query.trim().lowercase()
            filteredBooks = filteredBooks.filter {
                it.title.lowercase().contains(q) || it.author.lowercase().contains(q)
            }
        }
        filteredBooks = when (filter.filter) {
            VaultFilter.ALL -> filteredBooks
            VaultFilter.IN_PROGRESS -> filteredBooks.filter { !it.finished && it.lastPosMs > 0L }
            VaultFilter.COMPLETED -> filteredBooks.filter { it.finished }
            VaultFilter.FAVORITES -> filteredBooks.filter { it.favorite }
        }
        filteredBooks = when (filter.sort) {
            VaultSort.RECENT -> filteredBooks.sortedByDescending { it.createdAt }
            VaultSort.TITLE -> filteredBooks.sortedBy { it.title.lowercase() }
            VaultSort.AUTHOR -> filteredBooks.sortedBy { it.author.lowercase() }
            VaultSort.DURATION -> filteredBooks.sortedByDescending { it.durationMs }
        }

        // Filter texts
        var filteredTexts = source.texts
        if (filter.folderId != null) {
            filteredTexts = if (filter.folderId == -1L) {
                filteredTexts.filter { it.folderId == null }
            } else {
                filteredTexts.filter { it.folderId == filter.folderId }
            }
        }
        if (filter.query.isNotBlank()) {
            val q = filter.query.trim().lowercase()
            filteredTexts = filteredTexts.filter {
                it.title.lowercase().contains(q) || it.author.lowercase().contains(q)
            }
        }
        filteredTexts = when (filter.sort) {
            VaultSort.RECENT -> filteredTexts.sortedByDescending { it.createdAt }
            VaultSort.TITLE -> filteredTexts.sortedBy { it.title.lowercase() }
            VaultSort.AUTHOR -> filteredTexts.sortedBy { it.author.lowercase() }
            VaultSort.DURATION -> filteredTexts.sortedByDescending { it.wordCount }
        }

        VaultUiState(
            books = filteredBooks,
            texts = filteredTexts,
            folders = source.folders,
            selectedFolderId = filter.folderId,
            searchQuery = filter.query,
            currentFilter = filter.filter,
            currentSort = filter.sort,
            isGridView = source.isGrid,
            currentTab = filter.currentTab,
            selectedBookIds = selectedBooks,
            selectedTextIds = selectedTexts,
            isSelectionMode = isSelectionMode,
            isSearchExpanded = sel.isSearchExpanded,
            isFilterSheetVisible = sel.isFilterSheetVisible,
            isCreatingTestBook = _isCreatingTestBook.value,
            message = _message.value
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VaultUiState()
    )

    fun setCurrentTab(tab: Int) {
        _currentTab.value = tab
        clearSelection()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleSearchExpanded() {
        val next = !_isSearchExpanded.value
        _isSearchExpanded.value = next
        if (!next) {
            _searchQuery.value = ""
        }
    }

    fun setFilter(filter: VaultFilter) {
        _filter.value = filter
    }

    fun setSort(sort: VaultSort) {
        _sort.value = sort
    }

    fun showFilterSheet(show: Boolean) {
        _isFilterSheetVisible.value = show
    }

    fun selectFolder(folderId: Long?) {
        _selectedFolderId.value = folderId
    }

    fun toggleGridView() {
        val current = uiState.value.isGridView
        viewModelScope.launch {
            settingsStore.setGridView(!current)
        }
    }

    fun toggleFavorite(book: Book) {
        viewModelScope.launch {
            bookRepository.toggleFavorite(book.id, book.favorite)
        }
    }

    fun moveToRecycle(bookId: String) {
        viewModelScope.launch {
            recycleRepository.moveToRecycle(bookId)
        }
    }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            folderRepository.createFolder(name)
        }
    }

    fun renameFolder(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            folderRepository.renameFolder(id, name)
        }
    }

    fun deleteFolder(id: Long) {
        viewModelScope.launch {
            folderRepository.deleteFolder(id)
            if (_selectedFolderId.value == id) {
                _selectedFolderId.value = null
            }
        }
    }

    fun moveBookToFolder(bookId: String, folderId: Long?) {
        viewModelScope.launch {
            bookRepository.setFolder(bookId, folderId)
        }
    }

    fun moveTextToFolder(textId: String, folderId: Long?) {
        viewModelScope.launch {
            textItemRepository.updateFolder(textId, folderId)
        }
    }

    fun deleteText(textId: String) {
        viewModelScope.launch {
            textItemRepository.deleteTextItem(textId)
            _message.value = "Texto eliminado"
        }
    }

    // Multi-selection operations
    fun toggleSelectBook(bookId: String) {
        val current = _selectedBookIds.value.toMutableSet()
        if (current.contains(bookId)) {
            current.remove(bookId)
        } else {
            current.add(bookId)
        }
        _selectedBookIds.value = current
    }

    fun toggleSelectText(textId: String) {
        val current = _selectedTextIds.value.toMutableSet()
        if (current.contains(textId)) {
            current.remove(textId)
        } else {
            current.add(textId)
        }
        _selectedTextIds.value = current
    }

    fun clearSelection() {
        _selectedBookIds.value = emptySet()
        _selectedTextIds.value = emptySet()
    }

    fun moveSelectedToFolder(folderId: Long?) {
        viewModelScope.launch {
            val bookIds = _selectedBookIds.value
            val textIds = _selectedTextIds.value
            bookIds.forEach { bookRepository.setFolder(it, folderId) }
            textIds.forEach { textItemRepository.updateFolder(it, folderId) }
            clearSelection()
            _message.value = "Elementos movidos a carpeta"
        }
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val bookIds = _selectedBookIds.value
            val textIds = _selectedTextIds.value
            bookIds.forEach { recycleRepository.moveToRecycle(it) }
            textIds.forEach { textItemRepository.deleteTextItem(it) }
            clearSelection()
            _message.value = "Elementos eliminados"
        }
    }

    fun createTestBook(context: Context) {
        viewModelScope.launch {
            _isCreatingTestBook.value = true
            try {
                TestAudioGenerator.createTestBook(context, bookRepository)
                _message.value = "Audiolibro de prueba creado correctamente"
            } catch (e: Exception) {
                _message.value = "Error al crear audiolibro: ${e.message}"
            } finally {
                _isCreatingTestBook.value = false
            }
        }
    }

    fun updateBookInfo(book: Book, newTitle: String, newAuthor: String) {
        viewModelScope.launch {
            bookRepository.updateBookInfo(
                bookId = book.id,
                title = newTitle.trim(),
                author = newAuthor.trim().ifBlank { book.author },
                coverPath = book.coverPath
            )
            _message.value = "Audiolibro actualizado"
        }
    }

    fun importFileFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val fileName = resolveUriDisplayName(context, uri)
            if (fileName.endsWith(".lux", ignoreCase = true)) {
                try {
                    val importedBook = LuxPackageManager.importLuxPackageFromUri(
                        context = context,
                        uri = uri,
                        folderId = _selectedFolderId.value,
                        bookRepository = bookRepository
                    )
                    _currentTab.value = 0
                    _message.value = "Audiolibro \"${importedBook.title}\" importado desde paquete .lux"
                } catch (e: Exception) {
                    _message.value = e.message ?: "Error al importar paquete .lux"
                }
                return@launch
            }
            try {
                val textItem = TextFileExtractor.importDocumentFromUri(context, uri, _selectedFolderId.value)
                textItemRepository.insertTextItem(textItem)
                _currentTab.value = 1
                _message.value = "Texto \"${textItem.title}\" importado (${textItem.wordCount} palabras)"
            } catch (e: Exception) {
                _message.value = e.message ?: "Error al importar: formato no compatible"
            }
        }
    }

    private fun resolveUriDisplayName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        return uri.lastPathSegment ?: ""
    }

    fun enqueueBookNext(book: Book) {
        playbackConnection.enqueueNext(book)
        _message.value = "\"${book.title}\" se reproducirá a continuación"
    }

    fun appendBookToQueue(book: Book) {
        playbackConnection.appendToQueue(book)
        _message.value = "\"${book.title}\" añadido al final de la cola"
    }

    fun shareBookAsLux(context: Context, book: Book, onReadyIntent: (Intent) -> Unit) {
        viewModelScope.launch {
            try {
                val chapters = bookRepository.getChaptersSync(book.id)
                if (chapters.isEmpty()) {
                    _message.value = "El audiolibro no tiene capítulos para compartir"
                    return@launch
                }
                val shareIntent = LuxPackageManager.createShareIntent(context, book, chapters)
                onReadyIntent(shareIntent)
            } catch (e: Exception) {
                _message.value = "Error al empaquetar .lux: ${e.message}"
            }
        }
    }

    fun exportBookAsLuxToUri(context: Context, book: Book, destUri: Uri) {
        viewModelScope.launch {
            try {
                val chapters = bookRepository.getChaptersSync(book.id)
                if (chapters.isEmpty()) {
                    _message.value = "El audiolibro no tiene capítulos para exportar"
                    return@launch
                }
                LuxPackageManager.exportBookToUri(context, book, chapters, destUri)
                _message.value = "Paquete .lux guardado correctamente"
            } catch (e: Exception) {
                _message.value = "Error al exportar .lux: ${e.message}"
            }
        }
    }

    fun createPastedText(context: Context, title: String, author: String, content: String) {
        if (title.isBlank() || content.isBlank()) return
        viewModelScope.launch {
            try {
                val id = "text_" + UUID.randomUUID().toString().take(8)
                val itemDir = File(TextFileExtractor.getTextsRootDir(context), id).apply { mkdirs() }
                val rawFile = File(itemDir, "source.txt")
                rawFile.writeText(content.trim())
                val chapters = TextFileExtractor.splitTextIntoChapters(content.trim())
                TextFileExtractor.saveExtractedChapters(itemDir, chapters)

                val item = TextItem(
                    id = id,
                    title = title.trim(),
                    author = author.trim().ifBlank { "Nota personal" },
                    format = "TXT",
                    filePath = rawFile.absolutePath,
                    sizeBytes = rawFile.length(),
                    status = "PREPARED",
                    detectedLang = TextFileExtractor.guessLanguageSimple(content),
                    wordCount = TextFileExtractor.countWords(chapters),
                    folderId = _selectedFolderId.value
                )
                textItemRepository.insertTextItem(item)
                _currentTab.value = 1
                _message.value = "Texto \"${item.title}\" guardado en Textos"
            } catch (e: Exception) {
                _message.value = "Error al guardar texto: ${e.message}"
            }
        }
    }

    fun addSampleText(format: String, title: String, author: String) {
        viewModelScope.launch {
            val id = "text_" + UUID.randomUUID().toString().take(8)
            val sampleItem = TextItem(
                id = id,
                title = title,
                author = author,
                format = format,
                filePath = "sample/$id.$format",
                sizeBytes = 1024L * 150,
                status = "PREPARED",
                detectedLang = "es",
                wordCount = 18500,
                folderId = _selectedFolderId.value
            )
            textItemRepository.insertTextItem(sampleItem)
            _message.value = "Texto \"$title\" añadido a la Bóveda"
        }
    }

    fun playBook(book: Book) {
        playbackConnection.playBook(book)
    }

    fun clearMessage() {
        _message.value = null
    }

    companion object {
        fun provideFactory(
            bookRepository: BookRepository,
            folderRepository: FolderRepository,
            recycleRepository: RecycleRepository,
            textItemRepository: TextItemRepository,
            settingsStore: SettingsStore,
            playbackConnection: PlaybackConnection
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return VaultViewModel(
                    bookRepository,
                    folderRepository,
                    recycleRepository,
                    textItemRepository,
                    settingsStore,
                    playbackConnection
                ) as T
            }
        }
    }
}
