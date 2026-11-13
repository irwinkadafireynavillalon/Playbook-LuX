package com.example.engine

import android.content.Context
import com.example.data.local.entity.TextItem
import com.example.data.local.repository.TextItemRepository
import com.example.util.ExtractedChapter
import com.example.util.NotificationChannels
import com.example.util.TextFileExtractor
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class TranslatorEngineState(
    val selectedTextItem: TextItem? = null,
    val detectedSourceLang: String = "en",
    val targetLang: String = "es",
    val currentStep: Int = -1, // -1 idle, 0..4 steps, 5 completed
    val progress: Float = 0f,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val statusText: String = "Listo para traducir en el dispositivo",
    val previewOriginal: String = "",
    val previewTranslated: String = "",
    val errorMessage: String? = null,
    val completedTextItemId: String? = null
)

class LocalTranslatorEngine(
    private val context: Context,
    private val textItemRepository: TextItemRepository,
    private val appScope: CoroutineScope
) {
    private val _state = MutableStateFlow(TranslatorEngineState())
    val state: StateFlow<TranslatorEngineState> = _state.asStateFlow()

    private var translationJob: Job? = null
    private var pauseFlag = false

    fun selectTextItem(item: TextItem) {
        _state.update {
            it.copy(
                selectedTextItem = item,
                detectedSourceLang = item.detectedLang.ifBlank { "en" },
                errorMessage = null,
                completedTextItemId = null
            )
        }
        detectLanguageForItem(item)
    }

    fun setSourceLang(lang: String) {
        _state.update { it.copy(detectedSourceLang = lang) }
    }

    fun setTargetLang(lang: String) {
        _state.update { it.copy(targetLang = lang) }
    }

    fun detectLanguageForItem(item: TextItem) {
        appScope.launch(Dispatchers.IO) {
            try {
                val chapters = TextFileExtractor.loadChaptersForTextItem(context, item)
                val sample = chapters.firstOrNull()?.content?.take(600) ?: item.title
                _state.update { it.copy(previewOriginal = sample.take(350)) }

                val identifier = LanguageIdentification.getClient()
                val langCode = identifier.identifyLanguage(sample).awaitTask()
                identifier.close()

                val cleanCode = if (langCode != "und" && langCode.length >= 2) {
                    langCode.substring(0, 2).lowercase()
                } else {
                    TextFileExtractor.guessLanguageSimple(sample)
                }

                _state.update { st ->
                    val autoTarget = if (cleanCode == st.targetLang) {
                        if (cleanCode == "es") "en" else "es"
                    } else st.targetLang
                    st.copy(detectedSourceLang = cleanCode, targetLang = autoTarget)
                }
            } catch (_: Exception) {
            }
        }
    }

    fun startOrTogglePause() {
        val current = _state.value
        if (current.isRunning) {
            pauseFlag = !pauseFlag
            _state.update {
                it.copy(
                    isPaused = pauseFlag,
                    statusText = if (pauseFlag) "Traducción en pausa" else "Traduciendo capítulos…"
                )
            }
            return
        }

        val item = current.selectedTextItem ?: return
        if (current.detectedSourceLang == current.targetLang) {
            _state.update { it.copy(errorMessage = "El idioma de origen y destino no pueden ser iguales.") }
            return
        }

        pauseFlag = false
        translationJob = appScope.launch(Dispatchers.IO) {
            runTranslation(item, current.detectedSourceLang, current.targetLang)
        }
    }

    fun cancelTranslation() {
        translationJob?.cancel()
        translationJob = null
        pauseFlag = false
        _state.update {
            it.copy(
                isRunning = false,
                isPaused = false,
                currentStep = -1,
                progress = 0f,
                statusText = "Traducción cancelada"
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private suspend fun runTranslation(item: TextItem, sourceCode: String, targetCode: String) {
        try {
            _state.update {
                it.copy(
                    isRunning = true,
                    isPaused = false,
                    currentStep = 0,
                    progress = 0.05f,
                    statusText = "Paso 1/5: Extrayendo capítulos…",
                    errorMessage = null,
                    completedTextItemId = null
                )
            }

            val chapters = TextFileExtractor.loadChaptersForTextItem(context, item)
            if (chapters.isEmpty()) {
                throw IllegalStateException("El texto no contiene párrafos legibles.")
            }

            _state.update {
                it.copy(
                    currentStep = 1,
                    progress = 0.15f,
                    statusText = "Paso 2/5: Confirmando idioma ($sourceCode → $targetCode)…",
                    previewOriginal = chapters.first().content.take(350)
                )
            }

            val mlSource = mapToMlKitLanguage(sourceCode)
            val mlTarget = mapToMlKitLanguage(targetCode)
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(mlSource)
                .setTargetLanguage(mlTarget)
                .build()
            val translator = Translation.getClient(options)

            try {
                _state.update {
                    it.copy(
                        currentStep = 2,
                        progress = 0.22f,
                        statusText = "Paso 3/5: Verificando modelo offline ML Kit…"
                    )
                }

                val conditions = DownloadConditions.Builder().build()
                translator.downloadModelIfNeeded(conditions).awaitTask()

                // Translate paragraph by paragraph
                val translatedChapters = mutableListOf<ExtractedChapter>()
                val totalParagraphs = chapters.sumOf { ch ->
                    ch.content.split(Regex("\\n+")).count { it.isNotBlank() }.coerceAtLeast(1)
                }.coerceAtLeast(1)
                var doneParagraphs = 0

                for ((chIdx, chapter) in chapters.withIndex()) {
                    val translatedTitle = translator.translate(chapter.title).awaitTask()
                    val paragraphs = chapter.content.split(Regex("\\n+")).filter { it.isNotBlank() }
                    val sb = StringBuilder()

                    for (p in paragraphs) {
                        while (pauseFlag) {
                            delay(250L)
                        }
                        val translatedP = translator.translate(p).awaitTask()
                        if (sb.isNotEmpty()) sb.append("\n\n")
                        sb.append(translatedP)

                        doneParagraphs++
                        val pct = 0.25f + (0.60f * (doneParagraphs.toFloat() / totalParagraphs.toFloat()))
                        _state.update { st ->
                            val preview = if (chIdx == 0) sb.toString().take(400) else st.previewTranslated
                            st.copy(
                                progress = pct.coerceIn(0.25f, 0.88f),
                                statusText = "Paso 3/5: Traduciendo cap. ${chIdx + 1}/${chapters.size}…",
                                previewTranslated = preview
                            )
                        }
                        if (doneParagraphs % 3 == 0 || doneParagraphs == totalParagraphs) {
                            NotificationChannels.postProgress(
                                context = context,
                                channelId = NotificationChannels.CHANNEL_TRANSLATION,
                                notificationId = NotificationChannels.NOTIF_ID_TRANSLATION,
                                title = "Traduciendo ${item.title}",
                                contentText = "Cap. ${chIdx + 1}/${chapters.size} • ${(pct * 100).toInt()}%",
                                progressPercent = (pct * 100).toInt(),
                                ongoing = true
                            )
                        }
                    }

                    translatedChapters.add(
                        ExtractedChapter(
                            title = translatedTitle.ifBlank { chapter.title },
                            content = TextFileExtractor.cleanPlainText(sb.toString())
                        )
                    )
                }

                _state.update {
                    it.copy(
                        currentStep = 3,
                        progress = 0.92f,
                        statusText = "Paso 4/5: Limpieza sintáctica y formato…"
                    )
                }

                val savedItem = TextFileExtractor.saveTranslatedChapters(
                    context = context,
                    sourceItem = item,
                    targetLang = targetCode,
                    translatedChapters = translatedChapters
                )
                textItemRepository.insertTextItem(savedItem)

                NotificationChannels.postProgress(
                    context = context,
                    channelId = NotificationChannels.CHANNEL_TRANSLATION,
                    notificationId = NotificationChannels.NOTIF_ID_TRANSLATION,
                    title = "Traducción completada",
                    contentText = "\"${savedItem.title}\" guardado en Bóveda → Textos",
                    progressPercent = null,
                    ongoing = false
                )

                _state.update {
                    it.copy(
                        currentStep = 4,
                        progress = 1.0f,
                        isRunning = false,
                        isPaused = false,
                        statusText = "¡Traducción completada y guardada en Bóveda → Textos!",
                        previewTranslated = translatedChapters.firstOrNull()?.content?.take(400) ?: "",
                        completedTextItemId = savedItem.id
                    )
                }
            } finally {
                translator.close()
            }
        } catch (e: Exception) {
            NotificationChannels.cancel(context, NotificationChannels.NOTIF_ID_TRANSLATION)
            _state.update {
                it.copy(
                    isRunning = false,
                    isPaused = false,
                    statusText = "Error en traducción",
                    errorMessage = "No se pudo traducir: ${e.message ?: "Descarga el modelo offline con internet la primera vez."}"
                )
            }
        }
    }

    private fun mapToMlKitLanguage(code: String): String {
        return when (code.lowercase()) {
            "es" -> TranslateLanguage.SPANISH
            "en" -> TranslateLanguage.ENGLISH
            "fr" -> TranslateLanguage.FRENCH
            "de" -> TranslateLanguage.GERMAN
            "it" -> TranslateLanguage.ITALIAN
            "pt" -> TranslateLanguage.PORTUGUESE
            else -> TranslateLanguage.ENGLISH
        }
    }

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { exc ->
            if (cont.isActive) cont.resumeWithException(exc)
        }
    }
}
