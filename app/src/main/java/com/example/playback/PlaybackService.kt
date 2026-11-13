package com.example.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.MainActivity
import com.example.PlayLuxApplication
import com.example.data.datastore.PlayerSettings
import com.example.util.NotificationChannels
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class PlaybackService : MediaLibraryService() {

    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private val effectsManager = AudioEffectsManager()
    private val luxAudioProcessor = LuxAudioProcessor()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var currentSettings = PlayerSettings()
    private var audioManager: AudioManager? = null
    private var audioDeviceCallback: AudioDeviceCallback? = null

    companion object {
        const val ACTION_REWIND_CONFIGURED = "com.example.playlux.ACTION_REWIND_CONFIGURED"
        const val ACTION_FORWARD_CONFIGURED = "com.example.playlux.ACTION_FORWARD_CONFIGURED"
        const val ACTION_REWIND_15 = "com.example.playlux.ACTION_REWIND_15"
        const val ACTION_FORWARD_15 = "com.example.playlux.ACTION_FORWARD_15"
        const val ACTION_REWIND_30 = "com.example.playlux.ACTION_REWIND_30"
        const val ACTION_FORWARD_30 = "com.example.playlux.ACTION_FORWARD_30"

        const val ACTION_WIDGET_TOGGLE_PLAY = "com.example.playlux.ACTION_WIDGET_TOGGLE_PLAY"
        const val ACTION_WIDGET_REWIND = "com.example.playlux.ACTION_WIDGET_REWIND"
        const val ACTION_WIDGET_FORWARD = "com.example.playlux.ACTION_WIDGET_FORWARD"

        private const val NODE_ROOT = "root"
        private const val NODE_AUDIOBOOKS = "audiobooks"
        private const val NODE_RECENTS = "recents"
        private const val NODE_PLAYLISTS = "playlists"
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // Inject custom PCM AudioProcessor (Mono mixdown, L/R balance pan, real-time waveform bars)
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(luxAudioProcessor))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }

        player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        // Configure Media3 notification provider bound to playback_channel (silent, Palette colors, lockscreen)
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(NotificationChannels.CHANNEL_PLAYBACK)
            .build()
        setMediaNotificationProvider(notificationProvider)

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, MediaLibrarySessionCallback())
            .setSessionActivity(sessionActivityPendingIntent)
            .setCustomLayout(buildCustomButtons(currentSettings.jumpSeconds))
            .build()

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                effectsManager.attachOrUpdate(audioSessionId, currentSettings)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    AudioVisualStream.reset()
                }
                PlayLuxWidgetProvider.updateAllWidgets(this@PlaybackService)
                PlayLuxTileService.requestRefresh(this@PlaybackService)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (!currentSettings.autoNextChapter &&
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                ) {
                    player.pause()
                }
                PlayLuxWidgetProvider.updateAllWidgets(this@PlaybackService)
                PlayLuxTileService.requestRefresh(this@PlaybackService)
            }
        })

        observeSettings()
        registerBluetoothAudioCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val conn = (application as? PlayLuxApplication)?.container?.playbackConnection
        when (action) {
            ACTION_WIDGET_TOGGLE_PLAY -> {
                if (conn != null && conn.uiState.value.book != null) {
                    conn.togglePlayPause()
                } else if (player.mediaItemCount > 0) {
                    if (player.isPlaying) player.pause() else player.play()
                }
            }
            ACTION_WIDGET_REWIND -> {
                val delta = -(currentSettings.jumpSeconds * 1000L)
                if (conn != null && conn.uiState.value.book != null) {
                    conn.seekBy(delta)
                } else if (player.mediaItemCount > 0) {
                    player.seekTo((player.currentPosition + delta).coerceAtLeast(0L))
                }
            }
            ACTION_WIDGET_FORWARD -> {
                val delta = currentSettings.jumpSeconds * 1000L
                if (conn != null && conn.uiState.value.book != null) {
                    conn.seekBy(delta)
                } else if (player.mediaItemCount > 0) {
                    player.seekTo((player.currentPosition + delta).coerceAtMost(player.duration.coerceAtLeast(0L)))
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    @OptIn(UnstableApi::class)
    private fun observeSettings() {
        val settingsStore = (application as? PlayLuxApplication)?.container?.settingsStore ?: return
        serviceScope.launch {
            settingsStore.playerSettings.collectLatest { settings ->
                val jumpChanged = settings.jumpSeconds != currentSettings.jumpSeconds
                currentSettings = settings

                player.skipSilenceEnabled = settings.skipSilence
                luxAudioProcessor.monoAudio = settings.monoAudio
                luxAudioProcessor.balancePan = settings.balancePan
                effectsManager.attachOrUpdate(player.audioSessionId, settings)

                if (jumpChanged) {
                    mediaLibrarySession?.setCustomLayout(buildCustomButtons(settings.jumpSeconds))
                }
            }
        }
    }

    private fun buildCustomButtons(jumpSec: Int): List<CommandButton> {
        val rewindButton = CommandButton.Builder()
            .setDisplayName("Retroceder ${jumpSec}s")
            .setIconResId(android.R.drawable.ic_media_rew)
            .setSessionCommand(SessionCommand(ACTION_REWIND_CONFIGURED, Bundle.EMPTY))
            .build()

        val forwardButton = CommandButton.Builder()
            .setDisplayName("Adelantar ${jumpSec}s")
            .setIconResId(android.R.drawable.ic_media_ff)
            .setSessionCommand(SessionCommand(ACTION_FORWARD_CONFIGURED, Bundle.EMPTY))
            .build()

        return listOf(rewindButton, forwardButton)
    }

    private fun registerBluetoothAudioCallback() {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioManager = am
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                val hasBluetoothOutput = addedDevices.any { dev ->
                    dev.isSink && (
                        dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                        )
                }
                if (hasBluetoothOutput && currentSettings.resumeOnBluetooth &&
                    player.mediaItemCount > 0 && !player.isPlaying
                ) {
                    val rewindMs = currentSettings.autoRewindSeconds * 1000L
                    if (rewindMs > 0L) {
                        player.seekTo((player.currentPosition - rewindMs).coerceAtLeast(0L))
                    }
                    player.prepare()
                    player.play()
                }
            }
        }
        audioDeviceCallback = callback
        am.registerAudioDeviceCallback(callback, null)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        audioDeviceCallback?.let { cb ->
            runCatching { audioManager?.unregisterAudioDeviceCallback(cb) }
        }
        effectsManager.release()
        AudioVisualStream.reset()
        serviceScope.cancel()
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        super.onDestroy()
    }

    private inner class MediaLibrarySessionCallback : MediaLibrarySession.Callback {

        @OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(ACTION_REWIND_CONFIGURED, Bundle.EMPTY))
                .add(SessionCommand(ACTION_FORWARD_CONFIGURED, Bundle.EMPTY))
                .add(SessionCommand(ACTION_REWIND_15, Bundle.EMPTY))
                .add(SessionCommand(ACTION_FORWARD_15, Bundle.EMPTY))
                .add(SessionCommand(ACTION_REWIND_30, Bundle.EMPTY))
                .add(SessionCommand(ACTION_FORWARD_30, Bundle.EMPTY))
                .build()

            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootMetadata = MediaMetadata.Builder()
                .setTitle("Play LuX")
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
            val rootItem = MediaItem.Builder()
                .setMediaId(NODE_ROOT)
                .setMediaMetadata(rootMetadata)
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            val container = (application as? PlayLuxApplication)?.container
            if (container == null) {
                future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                return future
            }

            serviceScope.launch(Dispatchers.IO) {
                try {
                    val items = when (parentId) {
                        NODE_ROOT -> listOf(
                            browsableNode(NODE_AUDIOBOOKS, "Audiolibros", MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS),
                            browsableNode(NODE_RECENTS, "Recientes", MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS),
                            browsableNode(NODE_PLAYLISTS, "Listas", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                        )
                        NODE_AUDIOBOOKS -> {
                            container.bookRepository.getAllActiveBooksSync().map { book ->
                                MediaItem.Builder()
                                    .setMediaId("book:${book.id}")
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle(book.title)
                                            .setArtist(book.author)
                                            .setIsBrowsable(false)
                                            .setIsPlayable(true)
                                            .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
                                            .apply {
                                                if (book.coverPath != null) {
                                                    setArtworkUri(Uri.fromFile(File(book.coverPath)))
                                                }
                                            }
                                            .build()
                                    )
                                    .build()
                            }
                        }
                        NODE_RECENTS -> {
                            container.bookRepository.getAllActiveBooksSync()
                                .filter { it.lastPlayedAt > 0L }
                                .sortedByDescending { it.lastPlayedAt }
                                .take(10)
                                .map { book ->
                                    MediaItem.Builder()
                                        .setMediaId("book:${book.id}")
                                        .setMediaMetadata(
                                            MediaMetadata.Builder()
                                                .setTitle(book.title)
                                                .setArtist(book.author)
                                                .setIsBrowsable(false)
                                                .setIsPlayable(true)
                                                .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
                                                .apply {
                                                    if (book.coverPath != null) {
                                                        setArtworkUri(Uri.fromFile(File(book.coverPath)))
                                                    }
                                                }
                                                .build()
                                        )
                                        .build()
                                }
                        }
                        NODE_PLAYLISTS -> {
                            container.playlistRepository.allPlaylists.first().map { pl ->
                                browsableNode("playlist:${pl.id}", pl.name, MediaMetadata.MEDIA_TYPE_PLAYLIST)
                            }
                        }
                        else -> {
                            if (parentId.startsWith("playlist:")) {
                                val plId = parentId.removePrefix("playlist:").toLongOrNull()
                                if (plId != null) {
                                    val plItems = container.playlistRepository.getPlaylistItemsSync(plId)
                                    plItems.mapNotNull { item ->
                                        val b = container.bookRepository.getBookSync(item.bookId) ?: return@mapNotNull null
                                        MediaItem.Builder()
                                            .setMediaId("book:${b.id}")
                                            .setMediaMetadata(
                                                MediaMetadata.Builder()
                                                    .setTitle(b.title)
                                                    .setArtist(b.author)
                                                    .setIsBrowsable(false)
                                                    .setIsPlayable(true)
                                                    .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
                                                    .build()
                                            )
                                            .build()
                                    }
                                } else emptyList()
                            } else emptyList()
                        }
                    }
                    future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                } catch (_: Exception) {
                    future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                }
            }
            return future
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val container = (application as? PlayLuxApplication)?.container
                ?: return Futures.immediateFuture(mediaItems)

            val future = SettableFuture.create<MutableList<MediaItem>>()
            serviceScope.launch(Dispatchers.IO) {
                val resolved = mutableListOf<MediaItem>()
                for (item in mediaItems) {
                    if (item.localConfiguration != null) {
                        resolved.add(item)
                    } else if (item.mediaId.startsWith("book:")) {
                        val bookId = item.mediaId.removePrefix("book:")
                        val book = container.bookRepository.getBookSync(bookId)
                        val chapters = container.bookRepository.getChaptersSync(bookId)
                        if (book != null) {
                            chapters.forEach { ch ->
                                resolved.add(
                                    MediaItem.Builder()
                                        .setUri(Uri.fromFile(File(ch.filePath)))
                                        .setMediaId(ch.id.toString())
                                        .setMediaMetadata(
                                            MediaMetadata.Builder()
                                                .setTitle(ch.title)
                                                .setArtist(book.author)
                                                .setAlbumTitle(book.title)
                                                .setIsPlayable(true)
                                                .apply {
                                                    if (book.coverPath != null) {
                                                        setArtworkUri(Uri.fromFile(File(book.coverPath)))
                                                    }
                                                }
                                                .build()
                                        )
                                        .build()
                                )
                            }
                        }
                    }
                }
                future.set(resolved)
            }
            return future
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            val configuredDeltaMs = currentSettings.jumpSeconds * 1000L
            when (customCommand.customAction) {
                ACTION_REWIND_CONFIGURED -> {
                    player.seekTo((player.currentPosition - configuredDeltaMs).coerceAtLeast(0L))
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_FORWARD_CONFIGURED -> {
                    player.seekTo((player.currentPosition + configuredDeltaMs).coerceAtMost(player.duration.coerceAtLeast(0L)))
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_REWIND_15 -> {
                    player.seekTo((player.currentPosition - 15000L).coerceAtLeast(0L))
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_FORWARD_15 -> {
                    player.seekTo((player.currentPosition + 15000L).coerceAtMost(player.duration.coerceAtLeast(0L)))
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_REWIND_30 -> {
                    player.seekTo((player.currentPosition - 30000L).coerceAtLeast(0L))
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_FORWARD_30 -> {
                    player.seekTo((player.currentPosition + 30000L).coerceAtMost(player.duration.coerceAtLeast(0L)))
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        private fun browsableNode(id: String, title: String, mediaType: Int): MediaItem {
            return MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(mediaType)
                        .build()
                )
                .build()
        }
    }
}
