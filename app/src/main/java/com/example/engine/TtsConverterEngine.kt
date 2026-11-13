package com.example.engine

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.data.local.entity.Book
import com.example.data.local.entity.Chapter
import com.example.data.local.entity.TextItem
import com.example.data.local.repository.BookRepository
import com.example.data.local.repository.TextItemRepository
import com.example.util.CoverGenerator
import com.example.util.ExtractedChapter
import com.example.util.OpusAudioEncoder
import com.example.util.TextFileExtractor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class TtsEngineMode {
    LOCAL_DEVICE,
    LOQUENDO_SERVER
}

data class TtsVoiceOption(
    val id: String,
    val displayName: String,
    val languageTag: String,
    val localeDisplay: String,
    val requiresNetwork: Boolean
)

data class LoquendoVoicePreset(
    val id: String,
    val displayName: String,
    val languageTag: String
)

data class ConversionJobUi(
    val id: String,
    val textItem: TextItem,
    val languageTag: String,
    val voiceId: String?,
    val speed: Float,
    val pitch: Float,
    val paragraphPauseMs: Int,
    val engineMode: TtsEngineMode = TtsEngineMode.LOCAL_DEVICE,
    val loquendoUrl: String = "",
    val loquendoApiKey: String = "",
    val loquendoMethod: String = "POST_JSON",
    val progress: Float = 0f,
    val statusText: String = "En espera",
    val timeRemainingText: String = "Calculando…",
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val isCompleted: Boolean = false,
    val error: String? = null,
    val resultingBookId: String? = null
)

data class ConverterEngineState(
    val ttsInitialized: Boolean = false,
    val ttsError: String? = null,
    val availableVoices: List<TtsVoiceOption> = emptyList(),
    val queue: List<ConversionJobUi> = emptyList(),
    val isTestingServer: Boolean = false,
    val lastCompletedBookId: String? = null,
    val lastMessage: String? = null
)

class TtsConverterEngine(
    private val context: Context,
    private val bookRepository: BookRepository,
    private val textItemRepository: TextItemRepository,
    private val appScope: CoroutineScope
) {
    companion object {
        val LOQUENDO_PRESETS = listOf(
            LoquendoVoicePreset("Jorge", "Jorge (Español Castellano)", "es-ES"),
            LoquendoVoicePreset("Diego", "Diego (Español Latino)", "es-AR"),
            LoquendoVoicePreset("Carmen", "Carmen (Español Castellano)", "es-ES"),
            LoquendoVoicePreset("Esperanza", "Esperanza (Español México)", "es-MX"),
            LoquendoVoicePreset("Carlos", "Carlos (Español)", "es-ES"),
            LoquendoVoicePreset("Juan", "Juan (Español)", "es-ES"),
            LoquendoVoicePreset("Soledad", "Soledad (Español)", "es-ES"),
            LoquendoVoicePreset("Simon", "Simon (Inglés)", "en-US")
        )

        fun detectAudioExtension(bytes: ByteArray, contentType: String?): String {
            if (bytes.size >= 12 &&
                bytes[0] == 'R'.code.toByte() &&
                bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() &&
                bytes[3] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() &&
                bytes[9] == 'A'.code.toByte()
            ) {
                return "wav"
            }
            if (bytes.size >= 4 &&
                bytes[0] == 'O'.code.toByte() &&
                bytes[1] == 'g'.code.toByte() &&
                bytes[2] == 'g'.code.toByte() &&
                bytes[3] == 'S'.code.toByte()
            ) {
                return "ogg"
            }
            if (bytes.size >= 3 &&
                bytes[0] == 'I'.code.toByte() &&
                bytes[1] == 'D'.code.toByte() &&
                bytes[2] == '3'.code.toByte()
            ) {
                return "mp3"
            }
            if (bytes.size >= 2 &&
                (bytes[0].toInt() and 0xFF) == 0xFF &&
                (bytes[1].toInt() and 0xE0) == 0xE0
            ) {
                return "mp3"
            }
            val ct = contentType?.lowercase() ?: ""
            return when {
                ct.contains("mpeg") || ct.contains("mp3") -> "mp3"
                ct.contains("ogg") || ct.contains("opus") -> "ogg"
                ct.contains("wav") || ct.contains("wave") -> "wav"
                else -> "mp3"
            }
        }

        fun buildLoquendoGetUrl(
            baseUrl: String,
            text: String,
            voice: String,
            languageTag: String,
            speed: Float,
            pitch: Float
        ): String {
            val encText = URLEncoder.encode(text, "UTF-8")
            val encVoice = URLEncoder.encode(voice, "UTF-8")
            val encLang = URLEncoder.encode(languageTag, "UTF-8")
            val speedStr = String.format(Locale.US, "%.2f", speed)
            val pitchStr = String.format(Locale.US, "%.2f", pitch)

            if (baseUrl.contains("{text}") || baseUrl.contains("{voice}") || baseUrl.contains("{lang}")) {
                return baseUrl
                    .replace("{text}", encText)
                    .replace("{voice}", encVoice)
                    .replace("{lang}", encLang)
                    .replace("{speed}", speedStr)
                    .replace("{pitch}", pitchStr)
            }
            val separator = if (baseUrl.contains("?")) "&" else "?"
            return "${baseUrl}${separator}text=$encText&voice=$encVoice&lang=$encLang&speed=$speedStr&pitch=$pitchStr"
        }

        /**
         * Returns the first incomplete chapter index (0-based) for resumable multi-chapter jobs,
         * or [totalChapters] if all chapters have already been converted.
         */
        fun findResumeStartChapter(completedIndices: Set<Int>, totalChapters: Int): Int {
            for (idx in 0 until totalChapters) {
                if (!completedIndices.contains(idx)) return idx
            }
            return totalChapters
        }
    }

    private data class ChapterMetadataEntry(
        val index: Int,
        val title: String,
        val fileName: String,
        val format: String,
        val durationMs: Long,
        val appliedGainDb: Float,
        val measuredLufs: Float,
        val peakDbfs: Float
    )

    private class ServiceTimeoutException : Exception("Límite de ejecución en segundo plano alcanzado (6h)")

    private val _state = MutableStateFlow(ConverterEngineState())
    val state: StateFlow<ConverterEngineState> = _state.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private var tts: TextToSpeech? = null
    private var previewPlayer: MediaPlayer? = null
    private val utteranceCallbacks = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private var workerJob: Job? = null
    private var pauseFlag = false
    private var cancelCurrentFlag = false
    private var timeoutPauseFlag = false

    private val localEngine: TtsEngine = LocalAndroidTtsEngine(
        ttsProvider = { tts },
        utteranceCallbacks = utteranceCallbacks,
        appScope = appScope
    )
    private val loquendoEngine = LoquendoServerTtsEngine(httpClient)

    private fun selectEngine(mode: TtsEngineMode): TtsEngine {
        return when (mode) {
            TtsEngineMode.LOCAL_DEVICE -> localEngine
            TtsEngineMode.LOQUENDO_SERVER -> loquendoEngine
        }
    }

    init {
        initTts()
    }

    fun initTts() {
        appScope.launch(Dispatchers.Main) {
            try {
                tts?.shutdown()
                tts = TextToSpeech(context.applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        val engine = tts
                        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}
                            override fun onDone(utteranceId: String?) {
                                utteranceId?.let { utteranceCallbacks.remove(it)?.complete(true) }
                            }
                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                utteranceId?.let { utteranceCallbacks.remove(it)?.complete(false) }
                            }
                            override fun onError(utteranceId: String?, errorCode: Int) {
                                utteranceId?.let { utteranceCallbacks.remove(it)?.complete(false) }
                            }
                        })
                        val voices = loadVoices(engine)
                        _state.update {
                            it.copy(
                                ttsInitialized = true,
                                ttsError = null,
                                availableVoices = voices
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                ttsInitialized = false,
                                ttsError = "No se encontró motor TTS del sistema. Puedes usar Loquendo vía servidor o instalar un motor de voz."
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        ttsInitialized = false,
                        ttsError = "Error iniciando TTS: ${e.message}"
                    )
                }
            }
        }
    }

    private fun loadVoices(engine: TextToSpeech?): List<TtsVoiceOption> {
        val rawVoices: Set<Voice> = try {
            engine?.voices ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
        return rawVoices
            .filter { !it.isNetworkConnectionRequired || it.locale.language in listOf("es", "en", "fr", "de", "it", "pt") }
            .map { v ->
                val langTag = v.locale.toLanguageTag()
                val friendlyName = v.name
                    .replace("-language", "")
                    .replace("es-es-x-", "Español ")
                    .replace("es-us-x-", "Español US ")
                    .replace("en-us-x-", "Inglés US ")
                    .replace("-local", " (Offline)")
                    .replace("-network", " (Red)")
                TtsVoiceOption(
                    id = v.name,
                    displayName = friendlyName,
                    languageTag = langTag,
                    localeDisplay = v.locale.displayName,
                    requiresNetwork = v.isNetworkConnectionRequired
                )
            }
            .sortedWith(compareBy<TtsVoiceOption> { it.requiresNetwork }.thenBy { it.languageTag }.thenBy { it.displayName })
    }

    fun previewVoice(voiceId: String?, languageTag: String, speed: Float, pitch: Float) {
        val engine = tts ?: return
        appScope.launch(Dispatchers.Main) {
            try {
                val locale = Locale.forLanguageTag(languageTag)
                engine.language = locale
                if (voiceId != null) {
                    engine.voices?.firstOrNull { it.name == voiceId }?.let { engine.voice = it }
                }
                engine.setSpeechRate(speed.coerceIn(0.5f, 2.5f))
                engine.setPitch(pitch.coerceIn(0.5f, 2.0f))

                val sample = when (locale.language) {
                    "en" -> "Welcome to Play LuX. Your audiobook will sound like this."
                    "fr" -> "Bienvenue sur Play LuX. Votre livre audio aura cette voix."
                    "pt" -> "Bem-vindo ao Play LuX. Seu audiolivro soará assim."
                    "de" -> "Willkommen bei Play LuX. So wird Ihr Hörbuch klingen."
                    "it" -> "Benvenuto in Play LuX. Il tuo audiolibro suonerà così."
                    else -> "Bienvenido a Play LuX. Así sonará tu audiolibro convertido en tu dispositivo."
                }
                engine.speak(sample, TextToSpeech.QUEUE_FLUSH, null, "preview_${System.currentTimeMillis()}")
            } catch (e: Exception) {
                _state.update { it.copy(lastMessage = "No se pudo reproducir la muestra: ${e.message}") }
            }
        }
    }

    fun previewLoquendoServer(
        serverUrl: String,
        apiKey: String,
        method: String,
        voiceName: String,
        languageTag: String,
        speed: Float,
        pitch: Float
    ) {
        val cleanUrl = serverUrl.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            _state.update {
                it.copy(lastMessage = "Ingresa una URL válida de servidor Loquendo (http:// o https://).")
            }
            return
        }

        appScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isTestingServer = true) }
            try {
                val sample = "Hola, esta es una prueba de voz Loquendo con $voiceName en Play LuX."
                val (bytes, ext) = loquendoEngine.fetchServerAudioBytes(
                    text = sample,
                    voiceName = voiceName,
                    languageTag = languageTag,
                    speed = speed,
                    pitch = pitch,
                    serverUrl = cleanUrl,
                    apiKey = apiKey.trim(),
                    method = method
                )
                val previewFile = File(context.cacheDir, "loquendo_preview.$ext")
                previewFile.writeBytes(bytes)

                withContext(Dispatchers.Main) {
                    previewPlayer?.release()
                    previewPlayer = MediaPlayer().apply {
                        setDataSource(previewFile.absolutePath)
                        setOnCompletionListener { mp -> mp.release() }
                        prepare()
                        start()
                    }
                }
                _state.update {
                    it.copy(
                        isTestingServer = false,
                        lastMessage = "Conexión exitosa con Loquendo (${bytes.size / 1024} KB • ${ext.uppercase()})"
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isTestingServer = false,
                        lastMessage = "Error conectando al servidor Loquendo: ${e.message}"
                    )
                }
            }
        }
    }

    fun openInstallTtsVoices(ctx: Context) {
        try {
            val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (_: Exception) {
            _state.update { it.copy(lastMessage = "Abre Ajustes del sistema → Idioma → Síntesis de voz para descargar voces.") }
        }
    }

    fun enqueueConversion(
        textItem: TextItem,
        languageTag: String,
        voiceId: String?,
        speed: Float,
        pitch: Float,
        paragraphPauseMs: Int,
        engineMode: TtsEngineMode = TtsEngineMode.LOCAL_DEVICE,
        loquendoUrl: String = "",
        loquendoApiKey: String = "",
        loquendoMethod: String = "POST_JSON"
    ) {
        if (engineMode == TtsEngineMode.LOQUENDO_SERVER) {
            val cleanUrl = loquendoUrl.trim()
            if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                _state.update {
                    it.copy(lastMessage = "Configura primero la URL de tu servidor Loquendo (http:// o https://).")
                }
                return
            }
        }

        val jobId = "conv_" + UUID.randomUUID().toString().take(8)
        val job = ConversionJobUi(
            id = jobId,
            textItem = textItem,
            languageTag = languageTag,
            voiceId = voiceId,
            speed = speed,
            pitch = pitch,
            paragraphPauseMs = paragraphPauseMs,
            engineMode = engineMode,
            loquendoUrl = loquendoUrl.trim(),
            loquendoApiKey = loquendoApiKey.trim(),
            loquendoMethod = loquendoMethod,
            statusText = if (engineMode == TtsEngineMode.LOQUENDO_SERVER) "En espera (Loquendo)" else "En espera"
        )
        _state.update { it.copy(queue = it.queue + job, lastMessage = "\"${textItem.title}\" añadido a la cola de conversión") }
        ConversionService.ensureRunning(context)
        ensureWorkerRunning()
    }

    fun togglePauseActive() {
        pauseFlag = !pauseFlag
        _state.update { st ->
            val updated = st.queue.map { job ->
                if (job.isActive) job.copy(
                    isPaused = pauseFlag,
                    statusText = if (pauseFlag) "Pausado" else "Sintetizando audio…"
                ) else job
            }
            st.copy(queue = updated)
        }
    }

    /**
     * Triggered by ConversionService.onTimeout() (Android 15 6h dataSync foreground limit)
     * so already-encoded chapters & metadata.json stay preserved on disk for seamless resume.
     */
    fun pauseForServiceTimeout() {
        timeoutPauseFlag = true
        pauseFlag = false
        _state.update { st ->
            val updated = st.queue.map { job ->
                if (job.isActive) {
                    job.copy(
                        isActive = false,
                        isPaused = true,
                        statusText = "Pausado (límite 6h). Toca Reanudar para continuar"
                    )
                } else job
            }
            st.copy(
                queue = updated,
                lastMessage = "Conversión pausada tras 6h en segundo plano. Capítulos listos guardados para reanudar."
            )
        }
    }

    fun resumeJob(jobId: String) {
        _state.update { st ->
            val updated = st.queue.map { job ->
                if (job.id == jobId) {
                    job.copy(
                        isPaused = false,
                        error = null,
                        statusText = "Reanudando desde el último capítulo…"
                    )
                } else job
            }
            st.copy(queue = updated)
        }
        ConversionService.ensureRunning(context)
        ensureWorkerRunning()
    }

    fun cancelJob(jobId: String) {
        val active = _state.value.queue.firstOrNull { it.id == jobId && it.isActive }
        if (active != null) {
            cancelCurrentFlag = true
            pauseFlag = false
            timeoutPauseFlag = false
        } else {
            val target = _state.value.queue.firstOrNull { it.id == jobId }
            if (target != null) {
                stageDirForText(target.textItem.id).deleteRecursively()
            }
            _state.update { st -> st.copy(queue = st.queue.filterNot { it.id == jobId }) }
        }
    }

    fun moveQueueItem(jobId: String, direction: Int) {
        _state.update { st ->
            val list = st.queue.toMutableList()
            val idx = list.indexOfFirst { it.id == jobId }
            if (idx <= 0) return@update st
            val targetIdx = (idx + direction).coerceIn(1, list.lastIndex)
            if (targetIdx != idx && !list[targetIdx].isActive) {
                val item = list.removeAt(idx)
                list.add(targetIdx, item)
            }
            st.copy(queue = list)
        }
    }

    fun clearMessage() {
        _state.update { it.copy(lastMessage = null) }
    }

    private fun stageDirForText(textItemId: String): File {
        val safeId = textItemId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(context.filesDir, "conversion_staging/$safeId")
    }

    private fun ensureWorkerRunning() {
        if (workerJob?.isActive == true) return
        workerJob = appScope.launch(Dispatchers.IO) {
            while (isActive) {
                val nextJob = _state.value.queue.firstOrNull {
                    !it.isCompleted && !it.isPaused && it.error == null
                } ?: break
                pauseFlag = false
                cancelCurrentFlag = false
                timeoutPauseFlag = false
                processJob(nextJob)
            }
        }
    }

    private suspend fun processJob(job: ConversionJobUi) {
        val jobId = job.id
        updateJob(jobId) {
            it.copy(isActive = true, isPaused = false, error = null, statusText = "Preparando capítulos…", progress = 0.02f)
        }

        val bookId = "conv_book_" + UUID.randomUUID().toString().take(8)
        val stageDir = stageDirForText(job.textItem.id).apply { mkdirs() }
        val tempDir = File(context.cacheDir, "tts_tmp_$jobId").apply { mkdirs() }

        try {
            val activeEngine = selectEngine(job.engineMode)
            val chapters = TextFileExtractor.loadChaptersForTextItem(context, job.textItem)
                .ifEmpty {
                    listOf(ExtractedChapter("Capítulo 1", job.textItem.title))
                }

            if (job.engineMode == TtsEngineMode.LOCAL_DEVICE) {
                withContext(Dispatchers.Main) {
                    val engine = tts ?: throw IllegalStateException("Motor TTS local no disponible")
                    engine.language = Locale.forLanguageTag(job.languageTag)
                    if (job.voiceId != null) {
                        engine.voices?.firstOrNull { it.name == job.voiceId }?.let { engine.voice = it }
                    }
                    engine.setSpeechRate(job.speed.coerceIn(0.5f, 2.5f))
                    engine.setPitch(job.pitch.coerceIn(0.5f, 2.0f))
                }
            }

            // Load chapter-level checkpoint if a previous long run paused or timed out
            val stageMetadataFile = File(stageDir, "metadata.json")
            val completedChaptersMap = loadValidChapterCheckpoint(
                metadataFile = stageMetadataFile,
                stageDir = stageDir,
                textItemId = job.textItem.id,
                languageTag = job.languageTag,
                engineMode = job.engineMode,
                expectedTotalChapters = chapters.size
            ).toMutableMap()

            if (completedChaptersMap.isEmpty() && stageDir.exists()) {
                // Clean mismatched/corrupted leftovers before starting fresh
                stageDir.listFiles()?.forEach { it.deleteRecursively() }
            }

            // Split strictly under engine.maxSegmentChars() (< TextToSpeech.getMaxSpeechInputLength())
            val maxSegmentChars = activeEngine.maxSegmentChars()
            val allSegments = chapters.mapIndexed { chIdx, ch ->
                chIdx to TtsEngine.splitTextIntoSegments(ch.content, maxChars = maxSegmentChars)
            }
            val totalSegments = allSegments.sumOf { it.second.size }.coerceAtLeast(1)
            var processedSegments = allSegments
                .filter { completedChaptersMap.containsKey(it.first) }
                .sumOf { it.second.size }

            val startTimeMs = System.currentTimeMillis()
            val newlyProcessedStartSegments = processedSegments

            if (completedChaptersMap.isNotEmpty()) {
                val resumeIdx = findResumeStartChapter(completedChaptersMap.keys, chapters.size)
                if (resumeIdx < chapters.size) {
                    updateJob(jobId) {
                        it.copy(
                            progress = (processedSegments.toFloat() / totalSegments.toFloat()).coerceIn(0.02f, 0.94f),
                            statusText = "Reanudando desde cap. ${resumeIdx + 1}/${chapters.size}…"
                        )
                    }
                }
            }

            val synthesisRequest = TtsSynthesisRequest(
                languageTag = job.languageTag,
                voiceId = if (job.engineMode == TtsEngineMode.LOQUENDO_SERVER) (job.voiceId ?: "Jorge") else job.voiceId,
                speed = job.speed,
                pitch = job.pitch,
                serverUrl = job.loquendoUrl,
                apiKey = job.loquendoApiKey,
                method = job.loquendoMethod
            )

            for ((chIdx, segments) in allSegments) {
                if (cancelCurrentFlag) throw InterruptedException("Conversión cancelada")
                if (timeoutPauseFlag) throw ServiceTimeoutException()

                // Resume skip: already encoded and verified chapter file
                if (completedChaptersMap.containsKey(chIdx)) {
                    continue
                }

                val chapterTitle = chapters[chIdx].title
                val segmentFiles = mutableListOf<TtsSegmentResult>()

                for ((segIdx, segText) in segments.withIndex()) {
                    while (pauseFlag && !cancelCurrentFlag && !timeoutPauseFlag) {
                        delay(250L)
                    }
                    if (cancelCurrentFlag) throw InterruptedException("Conversión cancelada")
                    if (timeoutPauseFlag) throw ServiceTimeoutException()

                    val pct = (processedSegments.toFloat() / totalSegments.toFloat()).coerceIn(0.02f, 0.94f)
                    val doneThisSession = (processedSegments - newlyProcessedStartSegments).coerceAtLeast(0)
                    val elapsedSec = ((System.currentTimeMillis() - startTimeMs) / 1000L).coerceAtLeast(1L)
                    val estRemainingSec = if (doneThisSession > 0) {
                        ((elapsedSec.toFloat() / doneThisSession) * (totalSegments - processedSegments)).toInt()
                    } else {
                        (totalSegments - processedSegments) * 4
                    }
                    val remainingStr = if (estRemainingSec >= 60) {
                        "~${estRemainingSec / 60}m ${estRemainingSec % 60}s"
                    } else {
                        "~${estRemainingSec}s"
                    }

                    val modeTag = if (job.engineMode == TtsEngineMode.LOQUENDO_SERVER) "[Loquendo] " else ""
                    updateJob(jobId) {
                        it.copy(
                            progress = pct,
                            statusText = "${modeTag}Cap. ${chIdx + 1}/${chapters.size}: ${chapterTitle.take(26)}",
                            timeRemainingText = remainingStr
                        )
                    }

                    val res = activeEngine.synthesizeSegment(
                        text = segText,
                        request = synthesisRequest,
                        outputDir = tempDir,
                        fileBaseName = "ch_${chIdx}_part_${segIdx}"
                    )
                    if (res.success) {
                        segmentFiles.add(res)
                    }
                    processedSegments++
                }

                updateJob(jobId) {
                    it.copy(statusText = "Normalizando y codificando Cap. ${chIdx + 1}…")
                }

                val dominantExt = segmentFiles.firstOrNull()?.extension ?: "wav"
                val chapterEntry = if (dominantExt == "wav") {
                    val encoded = OpusAudioEncoder.encodeWavChunksToOpusOrFallback(
                        wavChunks = segmentFiles.map { it.file },
                        tempDir = tempDir,
                        outputDir = stageDir,
                        baseFileName = "chapter_${chIdx + 1}",
                        paragraphPauseMs = job.paragraphPauseMs,
                        normalizeChapter = true
                    )
                    ChapterMetadataEntry(
                        index = chIdx,
                        title = chapterTitle,
                        fileName = encoded.file.name,
                        format = encoded.formatTag,
                        durationMs = encoded.durationMs,
                        appliedGainDb = encoded.appliedGainDb,
                        measuredLufs = encoded.measuredLufs,
                        peakDbfs = encoded.peakDbfs
                    )
                } else {
                    val outFile = File(stageDir, "chapter_${chIdx + 1}.$dominantExt")
                    val dur = mergeStreamAudioFiles(segmentFiles.map { it.file }, outFile, dominantExt)
                    segmentFiles.forEach { runCatching { it.file.delete() } }
                    ChapterMetadataEntry(
                        index = chIdx,
                        title = chapterTitle,
                        fileName = outFile.name,
                        format = dominantExt,
                        durationMs = dur,
                        appliedGainDb = 0f,
                        measuredLufs = OpusAudioEncoder.TARGET_LUFS,
                        peakDbfs = OpusAudioEncoder.MAX_PEAK_DBFS
                    )
                }

                completedChaptersMap[chIdx] = chapterEntry

                // Persist incremental checkpoint metadata.json right after each chapter finishes
                writeMetadataJson(
                    outputFile = stageMetadataFile,
                    bookId = bookId,
                    textItem = job.textItem,
                    languageTag = job.languageTag,
                    engineMode = job.engineMode,
                    totalChapters = chapters.size,
                    completed = false,
                    entries = completedChaptersMap.values.sortedBy { it.index }
                )
            }

            // Finalize: move completed chapters from staging to filesDir/books/<bookId>/
            val bookDir = File(context.filesDir, "books/$bookId").apply { mkdirs() }
            val orderedEntries = completedChaptersMap.values.sortedBy { it.index }
            val generatedChapters = mutableListOf<Chapter>()
            var totalBookDurationMs = 0L

            orderedEntries.forEach { entry ->
                val stagedFile = File(stageDir, entry.fileName)
                val finalFile = File(bookDir, entry.fileName)
                if (stagedFile.exists()) {
                    if (!stagedFile.renameTo(finalFile)) {
                        stagedFile.copyTo(finalFile, overwrite = true)
                        stagedFile.delete()
                    }
                }
                totalBookDurationMs += entry.durationMs
                generatedChapters.add(
                    Chapter(
                        bookId = bookId,
                        partId = null,
                        index = entry.index,
                        title = entry.title,
                        filePath = finalFile.absolutePath,
                        durationMs = entry.durationMs
                    )
                )
            }

            // Write final book metadata.json including exact normalization gains applied per chapter
            writeMetadataJson(
                outputFile = File(bookDir, "metadata.json"),
                bookId = bookId,
                textItem = job.textItem,
                languageTag = job.languageTag,
                engineMode = job.engineMode,
                totalChapters = chapters.size,
                completed = true,
                entries = orderedEntries
            )
            // Persist synchronized chapter texts inside books/<bookId>/chapters_text.json for Lyrics/Follow-Text mode
            TextFileExtractor.saveExtractedChapters(bookDir, chapters)
            val savedChaptersJson = File(bookDir, "chapters.json")
            val textTargetJson = File(bookDir, "chapters_text.json")
            if (savedChaptersJson.exists()) {
                savedChaptersJson.renameTo(textTargetJson)
            }
            stageDir.deleteRecursively()

            val coverPath = CoverGenerator.generateDefaultCoverFile(
                context = context,
                bookId = bookId,
                title = job.textItem.title,
                author = job.textItem.author
            )
            val newBook = Book(
                id = bookId,
                title = job.textItem.title,
                author = job.textItem.author,
                lang = job.languageTag.substringBefore("-"),
                coverPath = coverPath,
                durationMs = totalBookDurationMs.coerceAtLeast(1000L),
                folderId = job.textItem.folderId,
                createdAt = System.currentTimeMillis(),
                sourceTextId = job.textItem.id
            )
            bookRepository.insertBookWithChapters(
                book = newBook,
                parts = emptyList(),
                chapters = generatedChapters
            )
            textItemRepository.updateStatus(job.textItem.id, "CONVERTED")

            _state.update { st ->
                st.copy(
                    queue = st.queue.filterNot { it.id == jobId },
                    lastCompletedBookId = bookId,
                    lastMessage = "¡Audiolibro \"${job.textItem.title}\" normalizado (-16 LUFS), codificado a Opus y guardado!"
                )
            }
        } catch (_: ServiceTimeoutException) {
            // Keep stageDir + metadata.json intact so resuming continues at next incomplete chapter
            updateJob(jobId) {
                it.copy(
                    isActive = false,
                    isPaused = true,
                    statusText = "Pausado (límite 6h). Pulsa Reanudar para continuar"
                )
            }
        } catch (e: InterruptedException) {
            stageDir.deleteRecursively()
            _state.update { st ->
                st.copy(
                    queue = st.queue.filterNot { it.id == jobId },
                    lastMessage = "Conversión cancelada"
                )
            }
        } catch (e: Exception) {
            // Preserve stageDir on transient failures so retry can resume from last completed chapter
            updateJob(jobId) {
                it.copy(
                    isActive = false,
                    error = e.message ?: "Error en conversión",
                    statusText = "Error (capítulos previos guardados): ${e.message}"
                )
            }
        } finally {
            // Guarantees no leftover temporary WAV files remain anywhere
            tempDir.deleteRecursively()
        }
    }

    private fun loadValidChapterCheckpoint(
        metadataFile: File,
        stageDir: File,
        textItemId: String,
        languageTag: String,
        engineMode: TtsEngineMode,
        expectedTotalChapters: Int
    ): Map<Int, ChapterMetadataEntry> {
        if (!metadataFile.exists()) return emptyMap()
        return runCatching {
            val root = JSONObject(metadataFile.readText())
            if (root.optString("textItemId") != textItemId) return emptyMap()
            if (root.optInt("totalChapters") != expectedTotalChapters) return emptyMap()
            if (root.optString("languageTag") != languageTag) return emptyMap()
            if (root.optString("engineMode") != engineMode.name) return emptyMap()

            val arr = root.optJSONArray("chapters") ?: return emptyMap()
            val map = linkedMapOf<Int, ChapterMetadataEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val idx = obj.optInt("index", -1)
                val fileName = obj.optString("fileName", "")
                val f = if (fileName.isNotBlank()) File(stageDir, fileName) else null
                if (idx in 0 until expectedTotalChapters && f != null && f.exists() && f.length() > 128L) {
                    map[idx] = ChapterMetadataEntry(
                        index = idx,
                        title = obj.optString("title", "Capítulo ${idx + 1}"),
                        fileName = fileName,
                        format = obj.optString("format", "opus"),
                        durationMs = obj.optLong("durationMs", 1000L),
                        appliedGainDb = obj.optDouble("appliedGainDb", 0.0).toFloat(),
                        measuredLufs = obj.optDouble("measuredLufs", OpusAudioEncoder.TARGET_LUFS.toDouble()).toFloat(),
                        peakDbfs = obj.optDouble("peakDbfs", OpusAudioEncoder.MAX_PEAK_DBFS.toDouble()).toFloat()
                    )
                }
            }
            map
        }.getOrDefault(emptyMap())
    }

    private fun writeMetadataJson(
        outputFile: File,
        bookId: String,
        textItem: TextItem,
        languageTag: String,
        engineMode: TtsEngineMode,
        totalChapters: Int,
        completed: Boolean,
        entries: List<ChapterMetadataEntry>
    ) {
        val chaptersArray = JSONArray()
        entries.forEach { ch ->
            chaptersArray.put(
                JSONObject().apply {
                    put("index", ch.index)
                    put("title", ch.title)
                    put("fileName", ch.fileName)
                    put("format", ch.format)
                    put("durationMs", ch.durationMs)
                    put("appliedGainDb", String.format(Locale.US, "%.2f", ch.appliedGainDb).toDouble())
                    put("measuredLufs", String.format(Locale.US, "%.2f", ch.measuredLufs).toDouble())
                    put("peakDbfs", String.format(Locale.US, "%.2f", ch.peakDbfs).toDouble())
                }
            )
        }
        val root = JSONObject().apply {
            put("bookId", bookId)
            put("textItemId", textItem.id)
            put("title", textItem.title)
            put("author", textItem.author)
            put("languageTag", languageTag)
            put("engineMode", engineMode.name)
            put("normalizationTargetLufs", OpusAudioEncoder.TARGET_LUFS.toDouble())
            put("normalizationMaxPeakDbfs", OpusAudioEncoder.MAX_PEAK_DBFS.toDouble())
            put("totalChapters", totalChapters)
            put("completedChapters", entries.size)
            put("completed", completed)
            put("updatedAt", System.currentTimeMillis())
            put("chapters", chaptersArray)
        }
        outputFile.writeText(root.toString(2))
    }

    private fun mergeStreamAudioFiles(inputs: List<File>, output: File, ext: String): Long {
        FileOutputStream(output).use { fos ->
            inputs.forEachIndexed { idx, file ->
                var bytes = file.readBytes()
                if (idx > 0 && ext == "mp3" && bytes.size > 10 &&
                    bytes[0] == 'I'.code.toByte() &&
                    bytes[1] == 'D'.code.toByte() &&
                    bytes[2] == '3'.code.toByte()
                ) {
                    val tagSize = ((bytes[6].toInt() and 0x7F) shl 21) or
                        ((bytes[7].toInt() and 0x7F) shl 14) or
                        ((bytes[8].toInt() and 0x7F) shl 7) or
                        (bytes[9].toInt() and 0x7F)
                    val offset = (10 + tagSize).coerceAtMost(bytes.size)
                    bytes = bytes.copyOfRange(offset, bytes.size)
                }
                fos.write(bytes)
            }
        }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(output.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?.takeIf { it > 0 } ?: 5_000L
        } catch (_: Exception) {
            5_000L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun updateJob(jobId: String, transform: (ConversionJobUi) -> ConversionJobUi) {
        _state.update { st ->
            st.copy(queue = st.queue.map { if (it.id == jobId) transform(it) else it })
        }
    }
}
