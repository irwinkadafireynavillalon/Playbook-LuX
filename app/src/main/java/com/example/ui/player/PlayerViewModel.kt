package com.example.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.Bookmark
import com.example.data.local.repository.BookRepository
import com.example.data.local.repository.TextItemRepository
import com.example.playback.AudioOutputDeviceUi
import com.example.playback.PlaybackConnection
import com.example.playback.PlaybackUiState
import com.example.playback.SleepTimerMode
import com.example.util.TextFileExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel(
    private val appContext: Context,
    private val playbackConnection: PlaybackConnection,
    private val bookRepository: BookRepository,
    private val textItemRepository: TextItemRepository? = null
) : ViewModel() {

    val uiState: StateFlow<PlaybackUiState> = playbackConnection.uiState

    val waveformBars: StateFlow<List<Float>> = playbackConnection.waveformBars

    val bookmarks: StateFlow<List<Bookmark>> = playbackConnection.uiState
        .flatMapLatest { state ->
            val book = state.book
            if (book != null) {
                bookRepository.getBookmarks(book.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Loads readable paragraphs for the currently playing chapter from:
     * 1) filesDir/books/<bookId>/chapters_text.json
     * 2) linked TextItem via book.sourceTextId
     * 3) fallback matching by book title in TextItemRepository
     */
    val chapterParagraphs: StateFlow<List<String>> = playbackConnection.uiState
        .map { state ->
            val queueBook = state.queueEntries.getOrNull(state.currentChapterIndex)?.book ?: state.book
            val chapterIdx = state.queueEntries.getOrNull(state.currentChapterIndex)?.chapter?.index
                ?: state.currentChapterIndex
            Triple(queueBook?.id, queueBook?.sourceTextId, queueBook?.title to chapterIdx)
        }
        .distinctUntilChanged()
        .flatMapLatest { (bookId, sourceTextId, titleAndIdx) ->
            flow {
                if (bookId == null) {
                    emit(emptyList())
                    return@flow
                }
                val chIdx = titleAndIdx.second
                val bookTextFile = File(appContext.filesDir, "books/$bookId/chapters_text.json")
                if (bookTextFile.exists()) {
                    val parsed = readParagraphsFromChaptersJson(bookTextFile, chIdx)
                    if (parsed.isNotEmpty()) {
                        emit(parsed)
                        return@flow
                    }
                }
                val repo = textItemRepository
                if (repo != null) {
                    val item = if (!sourceTextId.isNullOrBlank()) {
                        repo.getTextItemSync(sourceTextId)
                    } else null
                    if (item != null) {
                        val chapters = TextFileExtractor.loadChaptersForTextItem(appContext, item)
                        val content = chapters.getOrNull(chIdx)?.content.orEmpty()
                        if (content.isNotBlank()) {
                            emit(splitParagraphs(content))
                            return@flow
                        }
                    }
                }
                emit(emptyList())
            }.flowOn(Dispatchers.IO)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun togglePlay() {
        playbackConnection.togglePlayPause()
    }

    fun seekTo(posMs: Long) {
        playbackConnection.seekTo(posMs)
    }

    fun seekBy(deltaMs: Long) {
        playbackConnection.seekBy(deltaMs)
    }

    fun previousChapter() {
        playbackConnection.previousChapter()
    }

    fun nextChapter() {
        playbackConnection.nextChapter()
    }

    fun jumpToChapter(index: Int) {
        playbackConnection.jumpToChapter(index, 0L)
    }

    fun setSpeed(speed: Float) {
        playbackConnection.setSpeed(speed)
    }

    fun toggleShuffle() {
        playbackConnection.toggleShuffle()
    }

    fun cycleRepeatMode() {
        playbackConnection.cycleRepeatMode()
    }

    fun toggleAbLoopPoint() {
        playbackConnection.toggleAbLoopPoint()
    }

    fun clearAbLoop() {
        playbackConnection.clearAbLoop()
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        playbackConnection.moveQueueItem(fromIndex, toIndex)
    }

    fun removeQueueItem(index: Int) {
        playbackConnection.removeQueueItem(index)
    }

    fun getAvailableAudioOutputs(): List<AudioOutputDeviceUi> {
        return playbackConnection.getAvailableAudioOutputs()
    }

    fun setSleepTimer(mode: SleepTimerMode) {
        playbackConnection.setSleepTimer(mode)
    }

    fun addBookmark(note: String) {
        val state = uiState.value
        val book = state.book ?: return
        val currentChapter = state.chapters.getOrNull(state.currentChapterIndex) ?: return
        viewModelScope.launch {
            bookRepository.addBookmark(
                bookId = book.id,
                chapterId = currentChapter.id,
                posMs = state.currentPositionMs,
                note = note
            )
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            bookRepository.deleteBookmark(id)
        }
    }

    fun jumpToBookmark(bookmark: Bookmark) {
        val state = uiState.value
        val chIdx = state.chapters.indexOfFirst { it.id == bookmark.chapterId }
        if (chIdx >= 0) {
            playbackConnection.jumpToChapter(chIdx, bookmark.posMs)
        } else {
            playbackConnection.seekTo(bookmark.posMs)
        }
    }

    fun toggleFavorite() {
        val book = uiState.value.book ?: return
        viewModelScope.launch {
            bookRepository.toggleFavorite(book.id, book.favorite)
        }
    }

    fun dismissError() {
        playbackConnection.clearErrorMessage()
    }

    private fun readParagraphsFromChaptersJson(file: File, chapterIdx: Int): List<String> {
        return runCatching {
            val arr = JSONArray(file.readText())
            val obj = arr.optJSONObject(chapterIdx) ?: return emptyList()
            val content = obj.optString("content", "")
            if (content.isBlank()) emptyList() else splitParagraphs(content)
        }.getOrDefault(emptyList())
    }

    private fun splitParagraphs(content: String): List<String> {
        return content.split(Regex("\\n{1,2}"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    companion object {
        fun provideFactory(
            context: Context,
            playbackConnection: PlaybackConnection,
            bookRepository: BookRepository,
            textItemRepository: TextItemRepository? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PlayerViewModel(
                    context.applicationContext,
                    playbackConnection,
                    bookRepository,
                    textItemRepository
                ) as T
            }
        }
    }
}
