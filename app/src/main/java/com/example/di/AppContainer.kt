package com.example.di

import android.content.Context
import com.example.data.datastore.SettingsStore
import com.example.data.local.AppDatabase
import com.example.data.local.repository.BookRepository
import com.example.data.local.repository.FolderRepository
import com.example.data.local.repository.PlaylistRepository
import com.example.data.local.repository.RecycleRepository
import com.example.data.local.repository.TextItemRepository
import com.example.engine.LocalTranslatorEngine
import com.example.engine.PublicBookSearchEngine
import com.example.engine.TtsConverterEngine
import com.example.playback.PlaybackConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(private val context: Context) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(context)
    }

    val bookRepository: BookRepository by lazy {
        BookRepository(
            bookDao = database.bookDao(),
            chapterDao = database.chapterDao(),
            partDao = database.partDao(),
            bookmarkDao = database.bookmarkDao(),
            listeningStatsDao = database.listeningStatsDao()
        )
    }

    val recycleRepository: RecycleRepository by lazy {
        RecycleRepository(
            recycleDao = database.recycleDao(),
            bookDao = database.bookDao(),
            chapterDao = database.chapterDao(),
            partDao = database.partDao(),
            bookmarkDao = database.bookmarkDao()
        )
    }

    val folderRepository: FolderRepository by lazy {
        FolderRepository(
            folderDao = database.folderDao(),
            bookDao = database.bookDao()
        )
    }

    val textItemRepository: TextItemRepository by lazy {
        TextItemRepository(database.textItemDao())
    }

    val playlistRepository: PlaylistRepository by lazy {
        PlaylistRepository(database.playlistDao())
    }

    val settingsStore: SettingsStore by lazy {
        SettingsStore(context)
    }

    val playbackConnection: PlaybackConnection by lazy {
        PlaybackConnection(context, bookRepository, settingsStore, applicationScope)
    }

    val ttsConverterEngine: TtsConverterEngine by lazy {
        TtsConverterEngine(context, bookRepository, textItemRepository, applicationScope)
    }

    val localTranslatorEngine: LocalTranslatorEngine by lazy {
        LocalTranslatorEngine(context, textItemRepository, applicationScope)
    }

    val publicBookSearchEngine: PublicBookSearchEngine by lazy {
        PublicBookSearchEngine(context, textItemRepository, applicationScope)
    }
}
