package com.example.playback

import android.content.ComponentName
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.datastore.PlayerSettings
import com.example.data.datastore.SettingsStore
import com.example.data.local.entity.Book
import com.example.data.local.entity.Chapter
import com.example.data.local.repository.BookRepository
import com.example.util.CoverColors
import com.example.util.CoverGenerator
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class SleepTimerMode {
    OFF,
    MINUTES_15,
    MINUTES_30,
    MINUTES_45,
    MINUTES_60,
    END_OF_CHAPTER
}

data class QueueEntry(
    val uid: String = UUID.randomUUID().toString().take(8),
    val book: Book,
    val chapter: Chapter
)

data class AudioOutputDeviceUi(
    val id: Int,
    val name: String,
    val typeLabel: String,
    val isBluetooth: Boolean,
    val isWired: Boolean,
    val isSpeaker: Boolean,
    val isActiveRoute: Boolean = false
)

data class PlaybackUiState(
    val book: Book? = null,
    val chapters: List<Chapter> = emptyList(),
    val queueEntries: List<QueueEntry> = emptyList(),
    val currentChapterIndex: Int = 0,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val chapterDurationMs: Long = 0L,
    val speed: Float = 1.0f,
    val jumpSeconds: Int = 15,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val abLoopStartMs: Long? = null,
    val abLoopEndMs: Long? = null,
    val sleepTimerMode: SleepTimerMode = SleepTimerMode.OFF,
    val sleepTimerRemainingSeconds: Int? = null,
    val coverColors: CoverColors = CoverColors.default(),
    val errorMessage: String? = null
)

class PlaybackConnection(
    private val context: Context,
    private val bookRepository: BookRepository,
    private val settingsStore: SettingsStore,
    private val appScope: CoroutineScope
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    val waveformBars: StateFlow<List<Float>> = AudioVisualStream.waveformBars

    private var currentSettings = PlayerSettings()
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var fadeJob: Job? = null
    private var accumulatedListenMs: Long = 0L

    init {
        initController()
        observePlayerSettings()
    }

    private fun observePlayerSettings() {
        appScope.launch {
            settingsStore.playerSettings.collectLatest { settings ->
                currentSettings = settings
                _uiState.update { it.copy(jumpSeconds = settings.jumpSeconds) }
            }
        }
    }

    private fun initController() {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get()
                mediaController = controller
                setupPlayerListener(controller)
            } catch (_: Exception) {
                // Service may not be ready yet
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener(controller: MediaController?) {
        controller?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                    flushAccumulatedStats()
                    saveCurrentProgress()
                }
                PlayLuxWidgetProvider.updateAllWidgets(context)
                PlayLuxTileService.requestRefresh(context)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _uiState.update { it.copy(shuffleEnabled = shuffleModeEnabled) }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _uiState.update { it.copy(repeatMode = repeatMode) }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val newIndex = controller.currentMediaItemIndex
                val queue = _uiState.value.queueEntries
                val entry = queue.getOrNull(newIndex)
                val chapter = entry?.chapter ?: _uiState.value.chapters.getOrNull(newIndex)
                val duration = chapter?.durationMs ?: (controller.duration.takeIf { it != C.TIME_UNSET } ?: 0L)
                val activeBook = entry?.book ?: _uiState.value.book

                _uiState.update {
                    it.copy(
                        book = activeBook,
                        currentChapterIndex = newIndex,
                        chapterDurationMs = duration,
                        currentPositionMs = controller.currentPosition,
                        abLoopStartMs = null,
                        abLoopEndMs = null
                    )
                }

                // Soft crossfade entry ramp when moving to next chapter automatically
                if (currentSettings.crossfadeMs > 0 &&
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                ) {
                    runVolumeRamp(0.25f, 1.0f, (currentSettings.crossfadeMs / 2).toLong().coerceIn(150L, 1200L))
                }

                // If sleep timer is set to END_OF_CHAPTER and this was an automatic transition
                if (_uiState.value.sleepTimerMode == SleepTimerMode.END_OF_CHAPTER &&
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                ) {
                    controller.pause()
                    cancelSleepTimer()
                }

                saveCurrentProgress()
                PlayLuxWidgetProvider.updateAllWidgets(context)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    _uiState.update { it.copy(isPlaying = false) }
                    flushAccumulatedStats()
                    val currentBook = _uiState.value.book
                    if (currentBook != null) {
                        appScope.launch {
                            bookRepository.updateProgress(
                                bookId = currentBook.id,
                                chapterIndex = _uiState.value.currentChapterIndex,
                                posMs = _uiState.value.currentPositionMs,
                                finished = true
                            )
                        }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val currentBook = _uiState.value.book
                if (currentBook != null) {
                    appScope.launch {
                        bookRepository.setCorrupted(currentBook.id, true)
                    }
                }
                _uiState.update {
                    it.copy(
                        isPlaying = false,
                        errorMessage = "El archivo de audio está dañado o inaccesible. Se omitirá el capítulo."
                    )
                }

                if (controller.hasNextMediaItem()) {
                    controller.seekToNextMediaItem()
                    controller.prepare()
                    controller.play()
                }
            }
        })
    }

    @OptIn(UnstableApi::class)
    fun playBook(book: Book, startChapterIndex: Int = book.lastChapter, startPositionMs: Long = book.lastPosMs) {
        appScope.launch(Dispatchers.Main) {
            val chapters = bookRepository.getChaptersSync(book.id)
            if (chapters.isEmpty()) return@launch

            val controller = mediaController ?: return@launch
            val safeChapterIndex = startChapterIndex.coerceIn(0, chapters.size - 1)
            val currentChapter = chapters[safeChapterIndex]
            val colors = CoverGenerator.extractColorsFromCover(book.coverPath)

            val effectiveSpeed = if (book.speed == 1.0f && currentSettings.defaultSpeed != 1.0f) {
                currentSettings.defaultSpeed
            } else {
                book.speed
            }

            val queue = chapters.map { QueueEntry(book = book, chapter = it) }

            _uiState.update {
                it.copy(
                    book = book,
                    chapters = chapters,
                    queueEntries = queue,
                    currentChapterIndex = safeChapterIndex,
                    currentPositionMs = startPositionMs,
                    chapterDurationMs = currentChapter.durationMs,
                    speed = effectiveSpeed,
                    jumpSeconds = currentSettings.jumpSeconds,
                    abLoopStartMs = null,
                    abLoopEndMs = null,
                    coverColors = colors,
                    errorMessage = null
                )
            }

            val mediaItems = queue.map { buildMediaItem(it) }

            controller.stop()
            controller.clearMediaItems()
            controller.setMediaItems(mediaItems, safeChapterIndex, startPositionMs)
            controller.setPlaybackParameters(PlaybackParameters(effectiveSpeed, 1.0f))
            controller.volume = 1.0f
            controller.prepare()
            controller.play()

            bookRepository.markBookPlayed(book.id)
            bookRepository.recordListeningSeconds(0L, isNewSession = true)
            PlayLuxWidgetProvider.updateAllWidgets(context)
            PlayLuxTileService.requestRefresh(context)
        }
    }

    @OptIn(UnstableApi::class)
    fun playPlaylist(
        playlistName: String,
        entries: List<Pair<Book, Chapter?>>,
        shuffle: Boolean,
        repeatAll: Boolean
    ) {
        appScope.launch(Dispatchers.Main) {
            if (entries.isEmpty()) return@launch
            val controller = mediaController ?: return@launch

            val expandedChapters = mutableListOf<QueueEntry>()
            for ((book, specificChapter) in entries) {
                if (specificChapter != null) {
                    expandedChapters.add(QueueEntry(book = book, chapter = specificChapter))
                } else {
                    val bookChapters = bookRepository.getChaptersSync(book.id)
                    bookChapters.forEach { ch -> expandedChapters.add(QueueEntry(book = book, chapter = ch)) }
                }
            }
            if (expandedChapters.isEmpty()) return@launch

            val leadBook = expandedChapters.first().book
            val virtualBook = leadBook.copy(
                title = "$playlistName (${expandedChapters.size} pistas)",
                durationMs = expandedChapters.sumOf { it.chapter.durationMs }
            )
            val allChapters = expandedChapters.map { it.chapter }
            val colors = CoverGenerator.extractColorsFromCover(leadBook.coverPath)

            _uiState.update {
                it.copy(
                    book = virtualBook,
                    chapters = allChapters,
                    queueEntries = expandedChapters,
                    currentChapterIndex = 0,
                    currentPositionMs = 0L,
                    chapterDurationMs = allChapters.first().durationMs,
                    speed = leadBook.speed,
                    shuffleEnabled = shuffle,
                    repeatMode = if (repeatAll) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF,
                    abLoopStartMs = null,
                    abLoopEndMs = null,
                    coverColors = colors,
                    errorMessage = null
                )
            }

            val mediaItems = expandedChapters.map { entry ->
                buildMediaItem(entry, albumTitleOverride = playlistName)
            }

            controller.stop()
            controller.clearMediaItems()
            controller.shuffleModeEnabled = shuffle
            controller.repeatMode = if (repeatAll) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            controller.setMediaItems(mediaItems, 0, 0L)
            controller.setPlaybackParameters(PlaybackParameters(leadBook.speed, 1.0f))
            controller.volume = 1.0f
            controller.prepare()
            controller.play()

            bookRepository.markBookPlayed(leadBook.id)
            bookRepository.recordListeningSeconds(0L, isNewSession = true)
            PlayLuxWidgetProvider.updateAllWidgets(context)
            PlayLuxTileService.requestRefresh(context)
        }
    }

    /**
     * Enqueues a book (or specific chapter) right after the current playing item ("Reproducir a continuación").
     */
    fun enqueueNext(book: Book, specificChapter: Chapter? = null): Boolean {
        if (_uiState.value.book == null || mediaController == null || _uiState.value.queueEntries.isEmpty()) {
            playBook(book)
            return true
        }
        appScope.launch(Dispatchers.Main) {
            val controller = mediaController ?: return@launch
            val toAdd = resolveQueueEntries(book, specificChapter)
            if (toAdd.isEmpty()) return@launch
            val insertIdx = (_uiState.value.currentChapterIndex + 1).coerceIn(0, _uiState.value.queueEntries.size)

            val updatedQueue = _uiState.value.queueEntries.toMutableList().apply { addAll(insertIdx, toAdd) }
            controller.addMediaItems(insertIdx, toAdd.map { buildMediaItem(it) })
            _uiState.update {
                it.copy(
                    queueEntries = updatedQueue,
                    chapters = updatedQueue.map { q -> q.chapter }
                )
            }
        }
        return true
    }

    /**
     * Appends a book (or specific chapter) at the end of the active queue ("Añadir a la cola").
     */
    fun appendToQueue(book: Book, specificChapter: Chapter? = null): Boolean {
        if (_uiState.value.book == null || mediaController == null || _uiState.value.queueEntries.isEmpty()) {
            playBook(book)
            return true
        }
        appScope.launch(Dispatchers.Main) {
            val controller = mediaController ?: return@launch
            val toAdd = resolveQueueEntries(book, specificChapter)
            if (toAdd.isEmpty()) return@launch

            val updatedQueue = _uiState.value.queueEntries + toAdd
            controller.addMediaItems(toAdd.map { buildMediaItem(it) })
            _uiState.update {
                it.copy(
                    queueEntries = updatedQueue,
                    chapters = updatedQueue.map { q -> q.chapter }
                )
            }
        }
        return true
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val controller = mediaController ?: return
        val currentQueue = _uiState.value.queueEntries.toMutableList()
        if (fromIndex !in currentQueue.indices || toIndex !in currentQueue.indices || fromIndex == toIndex) return

        val item = currentQueue.removeAt(fromIndex)
        currentQueue.add(toIndex, item)
        controller.moveMediaItem(fromIndex, toIndex)

        val currentIdx = controller.currentMediaItemIndex.coerceIn(0, (currentQueue.size - 1).coerceAtLeast(0))
        _uiState.update {
            it.copy(
                queueEntries = currentQueue,
                chapters = currentQueue.map { q -> q.chapter },
                currentChapterIndex = currentIdx
            )
        }
    }

    fun removeQueueItem(index: Int) {
        val controller = mediaController ?: return
        val currentQueue = _uiState.value.queueEntries.toMutableList()
        if (index !in currentQueue.indices) return
        if (currentQueue.size <= 1) {
            stopPlayback()
            return
        }

        currentQueue.removeAt(index)
        controller.removeMediaItem(index)
        val newCurrentIdx = controller.currentMediaItemIndex.coerceIn(0, currentQueue.lastIndex)
        _uiState.update {
            it.copy(
                queueEntries = currentQueue,
                chapters = currentQueue.map { q -> q.chapter },
                currentChapterIndex = newCurrentIdx
            )
        }
    }

    fun stopPlayback() {
        fadeJob?.cancel()
        stopProgressTracker()
        flushAccumulatedStats()
        saveCurrentProgress()
        cancelSleepTimer()
        mediaController?.apply {
            stop()
            clearMediaItems()
            volume = 1.0f
        }
        AudioVisualStream.reset()
        _uiState.value = PlaybackUiState(jumpSeconds = currentSettings.jumpSeconds)
        PlayLuxWidgetProvider.updateAllWidgets(context)
        PlayLuxTileService.requestRefresh(context)
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        val next = !controller.shuffleModeEnabled
        controller.shuffleModeEnabled = next
        _uiState.update { it.copy(shuffleEnabled = next) }
    }

    fun cycleRepeatMode() {
        val controller = mediaController ?: return
        val next = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = next
        _uiState.update { it.copy(repeatMode = next) }
    }

    /**
     * Cycles A-B loop state inside the current chapter:
     * Idle -> sets A at current pos -> sets B at current pos -> clears A-B loop.
     */
    fun toggleAbLoopPoint() {
        val pos = mediaController?.currentPosition ?: _uiState.value.currentPositionMs
        val a = _uiState.value.abLoopStartMs
        val b = _uiState.value.abLoopEndMs
        when {
            a == null -> {
                _uiState.update { it.copy(abLoopStartMs = pos, abLoopEndMs = null) }
            }
            b == null -> {
                val validStart = a.coerceAtMost(pos)
                val validEnd = a.coerceAtLeast(pos)
                if (validEnd - validStart >= 500L) {
                    _uiState.update { it.copy(abLoopStartMs = validStart, abLoopEndMs = validEnd) }
                } else {
                    _uiState.update { it.copy(abLoopStartMs = pos, abLoopEndMs = null) }
                }
            }
            else -> {
                clearAbLoop()
            }
        }
    }

    fun clearAbLoop() {
        _uiState.update { it.copy(abLoopStartMs = null, abLoopEndMs = null) }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        fadeJob?.cancel()

        if (controller.isPlaying) {
            if (currentSettings.fadeOnPauseResume) {
                fadeJob = appScope.launch(Dispatchers.Main) {
                    val steps = 8
                    for (i in steps downTo 1) {
                        controller.volume = (i.toFloat() / steps).coerceIn(0.05f, 1.0f)
                        delay(26L)
                    }
                    controller.pause()
                    controller.volume = 1.0f
                }
            } else {
                controller.pause()
            }
        } else {
            val rewindMs = currentSettings.autoRewindSeconds * 1000L
            if (rewindMs > 0L && controller.currentPosition > 1000L) {
                val targetPos = (controller.currentPosition - rewindMs).coerceAtLeast(0L)
                controller.seekTo(targetPos)
                _uiState.update { it.copy(currentPositionMs = targetPos) }
            }
            if (currentSettings.fadeOnPauseResume) {
                controller.volume = 0.08f
                controller.play()
                runVolumeRamp(0.08f, 1.0f, 260L)
            } else {
                controller.volume = 1.0f
                controller.play()
            }
        }
    }

    private fun runVolumeRamp(fromVol: Float, toVol: Float, durationMs: Long) {
        val controller = mediaController ?: return
        fadeJob?.cancel()
        fadeJob = appScope.launch(Dispatchers.Main) {
            val steps = 10
            val stepDelay = (durationMs / steps).coerceAtLeast(15L)
            for (i in 1..steps) {
                if (_uiState.value.sleepTimerRemainingSeconds in 1..30) break
                val t = i.toFloat() / steps
                controller.volume = (fromVol + (toVol - fromVol) * t).coerceIn(0.05f, 1.0f)
                delay(stepDelay)
            }
            if (_uiState.value.sleepTimerRemainingSeconds !in 1..30) {
                controller.volume = toVol
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        controller.seekTo(positionMs)
        _uiState.update { it.copy(currentPositionMs = positionMs) }
        saveCurrentProgress()
    }

    fun seekBy(deltaMs: Long) {
        val controller = mediaController ?: return
        val newPos = (controller.currentPosition + deltaMs)
            .coerceIn(0L, controller.duration.coerceAtLeast(0L))
        controller.seekTo(newPos)
        _uiState.update { it.copy(currentPositionMs = newPos) }
        saveCurrentProgress()
    }

    fun previousChapter() {
        val controller = mediaController ?: return
        if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
        } else {
            controller.seekTo(0L)
        }
    }

    fun nextChapter() {
        val controller = mediaController ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
        }
    }

    fun jumpToChapter(index: Int, positionMs: Long = 0L) {
        val controller = mediaController ?: return
        val chapters = _uiState.value.chapters
        if (index in chapters.indices) {
            controller.seekTo(index, positionMs)
            controller.play()
        }
    }

    fun setSpeed(speed: Float) {
        val controller = mediaController ?: return
        val clampedSpeed = speed.coerceIn(0.5f, 3.0f)
        controller.setPlaybackParameters(PlaybackParameters(clampedSpeed, 1.0f))
        _uiState.update { it.copy(speed = clampedSpeed) }
        val currentBook = _uiState.value.book
        if (currentBook != null) {
            appScope.launch {
                bookRepository.updateSpeed(currentBook.id, clampedSpeed)
            }
        }
    }

    fun setSleepTimer(mode: SleepTimerMode) {
        cancelSleepTimer()
        val durationMinutes = when (mode) {
            SleepTimerMode.MINUTES_15 -> 15
            SleepTimerMode.MINUTES_30 -> 30
            SleepTimerMode.MINUTES_45 -> 45
            SleepTimerMode.MINUTES_60 -> 60
            SleepTimerMode.END_OF_CHAPTER -> {
                _uiState.update { it.copy(sleepTimerMode = mode, sleepTimerRemainingSeconds = null) }
                return
            }
            SleepTimerMode.OFF -> {
                cancelSleepTimer()
                return
            }
        }

        val totalSeconds = durationMinutes * 60
        _uiState.update { it.copy(sleepTimerMode = mode, sleepTimerRemainingSeconds = totalSeconds) }

        sleepTimerJob = appScope.launch(Dispatchers.Main) {
            var remaining = totalSeconds
            while (isActive && remaining > 0) {
                delay(1000L)
                remaining--
                _uiState.update { it.copy(sleepTimerRemainingSeconds = remaining) }

                val controller = mediaController
                if (controller != null && remaining in 1..30) {
                    val fadeVolume = (remaining.toFloat() / 30f).coerceIn(0.05f, 1.0f)
                    controller.volume = fadeVolume
                }
            }

            val controller = mediaController
            controller?.pause()
            controller?.volume = 1.0f
            cancelSleepTimer()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        mediaController?.volume = 1.0f
        _uiState.update { it.copy(sleepTimerMode = SleepTimerMode.OFF, sleepTimerRemainingSeconds = null) }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun getAvailableAudioOutputs(): List<AudioOutputDeviceUi> {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return emptyList()
        val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val hasExternal = devices.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        return devices.mapNotNull { dev ->
            val isBt = dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            val isWired = dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                dev.type == AudioDeviceInfo.TYPE_USB_HEADSET
            val isSpeaker = dev.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            if (!isBt && !isWired && !isSpeaker) return@mapNotNull null

            val typeLabel = when {
                isBt -> "Bluetooth"
                isWired -> "Auriculares con cable"
                else -> "Altavoz del teléfono"
            }
            val friendlyName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !dev.productName.isNullOrBlank()) {
                dev.productName.toString()
            } else {
                typeLabel
            }
            val isActive = if (hasExternal) (isBt || isWired) else isSpeaker
            AudioOutputDeviceUi(
                id = dev.id,
                name = friendlyName,
                typeLabel = typeLabel,
                isBluetooth = isBt,
                isWired = isWired,
                isSpeaker = isSpeaker,
                isActiveRoute = isActive
            )
        }.distinctBy { "${it.name}_${it.typeLabel}" }
    }

    private suspend fun resolveQueueEntries(book: Book, specificChapter: Chapter?): List<QueueEntry> {
        return if (specificChapter != null) {
            listOf(QueueEntry(book = book, chapter = specificChapter))
        } else {
            bookRepository.getChaptersSync(book.id).map { QueueEntry(book = book, chapter = it) }
        }
    }

    private fun buildMediaItem(entry: QueueEntry, albumTitleOverride: String? = null): MediaItem {
        val ch = entry.chapter
        val book = entry.book
        val uri = Uri.fromFile(File(ch.filePath))
        val metadata = MediaMetadata.Builder()
            .setTitle(ch.title)
            .setArtist(book.author)
            .setAlbumTitle(albumTitleOverride ?: book.title)
            .apply {
                if (book.coverPath != null) {
                    setArtworkUri(Uri.fromFile(File(book.coverPath)))
                }
            }
            .build()
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId("${book.id}_${ch.id}_${entry.uid}")
            .setMediaMetadata(metadata)
            .build()
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = appScope.launch(Dispatchers.Main) {
            var tickCount = 0
            while (isActive) {
                // High responsiveness (250ms) when A-B Loop or Crossfade is active, otherwise 1000ms
                val hasFastLoop = _uiState.value.abLoopEndMs != null || currentSettings.crossfadeMs > 0
                val delayMs = if (hasFastLoop) 250L else 1000L
                delay(delayMs)

                val controller = mediaController ?: continue
                val pos = controller.currentPosition
                val dur = controller.duration.takeIf { it != C.TIME_UNSET } ?: _uiState.value.chapterDurationMs

                // Enforce A-B Loop automatically
                val loopStart = _uiState.value.abLoopStartMs
                val loopEnd = _uiState.value.abLoopEndMs
                if (loopStart != null && loopEnd != null && pos >= loopEnd) {
                    controller.seekTo(loopStart)
                }

                // Soft chapter-end crossfade taper when enabled and near end of chapter
                val crossfade = currentSettings.crossfadeMs
                if (crossfade > 0 && dur > crossfade * 2 && _uiState.value.sleepTimerRemainingSeconds !in 1..30) {
                    val remainingMs = (dur - pos).coerceAtLeast(0L)
                    if (remainingMs in 1L..crossfade.toLong()) {
                        controller.volume = (remainingMs.toFloat() / crossfade.toFloat()).coerceIn(0.2f, 1.0f)
                    } else if (pos < (crossfade / 2)) {
                        controller.volume = (pos.toFloat() / (crossfade / 2f)).coerceIn(0.25f, 1.0f)
                    } else if (controller.volume < 0.99f && fadeJob?.isActive != true) {
                        controller.volume = 1.0f
                    }
                }

                _uiState.update {
                    it.copy(
                        currentPositionMs = pos,
                        chapterDurationMs = dur.coerceAtLeast(0L)
                    )
                }

                accumulatedListenMs += delayMs
                tickCount++
                val saveEveryTicks = if (hasFastLoop) 20 else 5
                if (tickCount % saveEveryTicks == 0) {
                    saveCurrentProgress()
                    flushAccumulatedStats()
                }
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun flushAccumulatedStats() {
        val secs = accumulatedListenMs / 1000L
        if (secs > 0L) {
            accumulatedListenMs %= 1000L
            appScope.launch {
                bookRepository.recordListeningSeconds(secs, isNewSession = false)
            }
        }
    }

    private fun saveCurrentProgress() {
        val currentBook = _uiState.value.book ?: return
        val chapterIdx = _uiState.value.currentChapterIndex
        val pos = _uiState.value.currentPositionMs

        appScope.launch {
            bookRepository.updateProgress(
                bookId = currentBook.id,
                chapterIndex = chapterIdx,
                posMs = pos,
                finished = false
            )
        }
    }

    companion object {
        /**
         * Pure helper for computing next A-B loop transition, verified in unit tests.
         */
        fun computeAbLoopJumpTarget(
            currentPosMs: Long,
            abStartMs: Long?,
            abEndMs: Long?
        ): Long? {
            if (abStartMs == null || abEndMs == null) return null
            return if (currentPosMs >= abEndMs) abStartMs else null
        }
    }
}
