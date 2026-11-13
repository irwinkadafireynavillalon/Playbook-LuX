package com.example.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.datastore.PlayerSettings
import com.example.data.datastore.SettingsStore
import com.example.data.local.entity.Book
import com.example.data.local.entity.ListeningStat
import com.example.data.local.repository.BookRepository
import com.example.data.local.repository.RecycleRepository
import com.example.ui.theme.AppThemeMode
import com.example.util.TestAudioGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val recycleRepository: RecycleRepository,
    private val bookRepository: BookRepository? = null
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _isCreatingTestBook = MutableStateFlow(false)
    val isCreatingTestBook: StateFlow<Boolean> = _isCreatingTestBook.asStateFlow()

    val themeMode: StateFlow<AppThemeMode> = settingsStore.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppThemeMode.DARK
    )

    val reduceEffects: StateFlow<Boolean> = settingsStore.reduceEffects.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val playerSettings: StateFlow<PlayerSettings> = settingsStore.playerSettings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlayerSettings()
    )

    val recycledBooks: StateFlow<List<Book>> = recycleRepository.recycledBooks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val recentlyPlayedBooks: StateFlow<List<Book>> = (
        bookRepository?.recentlyPlayedBooks ?: flowOf(emptyList())
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val totalListenedSeconds: StateFlow<Long> = (
        bookRepository?.totalListenedSeconds?.map { it ?: 0L } ?: flowOf(0L)
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0L
    )

    val recentListeningStats: StateFlow<List<ListeningStat>> = (
        bookRepository?.getRecentListeningStats(14) ?: flowOf(emptyList())
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            settingsStore.setThemeMode(mode)
        }
    }

    fun setReduceEffects(reduce: Boolean) {
        viewModelScope.launch {
            settingsStore.setReduceEffects(reduce)
        }
    }

    fun updatePlayerSettings(transform: (PlayerSettings) -> PlayerSettings) {
        viewModelScope.launch {
            settingsStore.updatePlayerSettings(transform)
        }
    }

    fun applyEqPreset(presetId: Int) {
        val spec = SettingsStore.EQ_PRESETS.firstOrNull { it.id == presetId } ?: return
        viewModelScope.launch {
            settingsStore.updatePlayerSettings { current ->
                current.copy(eqPreset = spec.id, eqBands = spec.bands)
            }
        }
    }

    fun updateEqBand(bandIndex: Int, levelMb: Int) {
        if (bandIndex !in 0..4) return
        viewModelScope.launch {
            settingsStore.updatePlayerSettings { current ->
                val updated = current.eqBands.toMutableList()
                while (updated.size < 5) updated.add(0)
                updated[bandIndex] = levelMb.coerceIn(-1200, 1200)
                current.copy(eqPreset = 5, eqBands = updated)
            }
        }
    }

    fun restoreBook(bookId: String) {
        viewModelScope.launch {
            recycleRepository.restoreFromRecycle(bookId)
            _message.value = "Audiolibro restaurado"
        }
    }

    fun deletePermanently(context: Context, bookId: String) {
        viewModelScope.launch {
            recycleRepository.deletePermanently(context, bookId)
            _message.value = "Audiolibro eliminado definitivamente"
        }
    }

    fun emptyRecycle(context: Context) {
        viewModelScope.launch {
            recycleRepository.emptyRecycle(context)
            _message.value = "Papelera vaciada correctamente"
        }
    }

    fun clearCache(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = context.cacheDir
                cacheDir.deleteRecursively()
                cacheDir.mkdirs()
                withContext(Dispatchers.Main) {
                    _message.value = "Caché temporal limpiada correctamente"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _message.value = "Error al limpiar caché: ${e.message}"
                }
            }
        }
    }

    fun createTestBook(context: Context) {
        if (bookRepository == null) return
        viewModelScope.launch {
            _isCreatingTestBook.value = true
            try {
                TestAudioGenerator.createTestBook(context, bookRepository)
                _message.value = "Audiolibro de prueba creado correctamente en la Bóveda"
            } catch (e: Exception) {
                _message.value = "Error al crear audiolibro: ${e.message}"
            } finally {
                _isCreatingTestBook.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    companion object {
        fun provideFactory(
            settingsStore: SettingsStore,
            recycleRepository: RecycleRepository,
            bookRepository: BookRepository? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(settingsStore, recycleRepository, bookRepository) as T
            }
        }
    }
}
