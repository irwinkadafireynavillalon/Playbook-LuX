package com.example.engine

import android.os.Bundle
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

data class TtsSynthesisRequest(
    val languageTag: String,
    val voiceId: String?,
    val speed: Float,
    val pitch: Float,
    val serverUrl: String = "",
    val apiKey: String = "",
    val method: String = "POST_JSON"
)

data class TtsSegmentResult(
    val file: File,
    val extension: String, // "wav", "mp3", "ogg"
    val success: Boolean
)

/**
 * Unified interface behind native Android TTS, Loquendo Server, and future engines (e.g. Piper TTS).
 */
interface TtsEngine {
    val mode: TtsEngineMode
    fun maxSegmentChars(): Int
    suspend fun synthesizeSegment(
        text: String,
        request: TtsSynthesisRequest,
        outputDir: File,
        fileBaseName: String
    ): TtsSegmentResult

    companion object {
        /**
         * Splits chapter text strictly into chunks under [maxChars] (always < TextToSpeech.getMaxSpeechInputLength()).
         */
        fun splitTextIntoSegments(text: String, maxChars: Int): List<String> {
            val safeMax = maxChars.coerceIn(100, 3800)
            val paragraphs = text.split(Regex("\\n+")).map { it.trim() }.filter { it.isNotEmpty() }
            val result = mutableListOf<String>()
            val sb = StringBuilder()

            for (p in paragraphs) {
                if (p.length > safeMax) {
                    if (sb.isNotEmpty()) {
                        result.add(sb.toString().trim())
                        sb.clear()
                    }
                    val sentences = p.split(Regex("(?<=[.!?])\\s+"))
                    for (s in sentences) {
                        if (s.length > safeMax) {
                            if (sb.isNotEmpty()) {
                                result.add(sb.toString().trim())
                                sb.clear()
                            }
                            var start = 0
                            while (start < s.length) {
                                val end = (start + safeMax).coerceAtMost(s.length)
                                result.add(s.substring(start, end).trim())
                                start = end
                            }
                        } else {
                            if (sb.length + s.length + 1 > safeMax && sb.isNotEmpty()) {
                                result.add(sb.toString().trim())
                                sb.clear()
                            }
                            if (sb.isNotEmpty()) sb.append(" ")
                            sb.append(s)
                        }
                    }
                } else {
                    if (sb.length + p.length + 2 > safeMax && sb.isNotEmpty()) {
                        result.add(sb.toString().trim())
                        sb.clear()
                    }
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append(p)
                }
            }
            if (sb.isNotEmpty()) {
                result.add(sb.toString().trim())
            }
            return result.ifEmpty { listOf(" ") }
        }
    }
}

class LocalAndroidTtsEngine(
    private val ttsProvider: () -> TextToSpeech?,
    private val utteranceCallbacks: ConcurrentHashMap<String, CompletableDeferred<Boolean>>,
    private val appScope: CoroutineScope
) : TtsEngine {
    override val mode = TtsEngineMode.LOCAL_DEVICE

    override fun maxSegmentChars(): Int {
        val systemLimit = runCatching { TextToSpeech.getMaxSpeechInputLength() }.getOrDefault(4000)
        // Strictly keep each chunk comfortably below TextToSpeech.getMaxSpeechInputLength()
        return (systemLimit - 250).coerceIn(500, 3500)
    }

    override suspend fun synthesizeSegment(
        text: String,
        request: TtsSynthesisRequest,
        outputDir: File,
        fileBaseName: String
    ): TtsSegmentResult {
        val engine = ttsProvider() ?: return TtsSegmentResult(File(outputDir, "$fileBaseName.wav"), "wav", false)
        val outFile = File(outputDir, "$fileBaseName.wav")
        val utteranceId = "utt_" + UUID.randomUUID().toString()
        val deferred = CompletableDeferred<Boolean>()
        utteranceCallbacks[utteranceId] = deferred

        withContext(Dispatchers.Main) {
            val params = Bundle()
            val res = engine.synthesizeToFile(text, params, outFile, utteranceId)
            if (res != TextToSpeech.SUCCESS) {
                utteranceCallbacks.remove(utteranceId)?.complete(false)
            }
        }

        val ok = suspendCancellableCoroutine { cont ->
            appScope.launch(Dispatchers.IO) {
                val res = try {
                    deferred.await()
                } catch (_: Exception) {
                    false
                }
                if (cont.isActive) cont.resume(res)
            }
            cont.invokeOnCancellation {
                utteranceCallbacks.remove(utteranceId)
                deferred.cancel()
            }
        }
        return TtsSegmentResult(
            file = outFile,
            extension = "wav",
            success = ok && outFile.exists() && outFile.length() > 44
        )
    }
}

class LoquendoServerTtsEngine(
    private val httpClient: OkHttpClient
) : TtsEngine {
    override val mode = TtsEngineMode.LOQUENDO_SERVER

    override fun maxSegmentChars(): Int = 1000

    override suspend fun synthesizeSegment(
        text: String,
        request: TtsSynthesisRequest,
        outputDir: File,
        fileBaseName: String
    ): TtsSegmentResult = withContext(Dispatchers.IO) {
        val resp = fetchServerAudioBytes(
            text = text,
            voiceName = request.voiceId ?: "Jorge",
            languageTag = request.languageTag,
            speed = request.speed,
            pitch = request.pitch,
            serverUrl = request.serverUrl,
            apiKey = request.apiKey,
            method = request.method
        )
        val outFile = File(outputDir, "$fileBaseName.${resp.second}")
        outFile.writeBytes(resp.first)
        TtsSegmentResult(
            file = outFile,
            extension = resp.second,
            success = outFile.exists() && outFile.length() > 32
        )
    }

    fun fetchServerAudioBytes(
        text: String,
        voiceName: String,
        languageTag: String,
        speed: Float,
        pitch: Float,
        serverUrl: String,
        apiKey: String,
        method: String
    ): Pair<ByteArray, String> {
        val requestBuilder = Request.Builder()
            .header("User-Agent", "PlayLuX-Android/1.0")
            .header("Accept", "audio/*, application/octet-stream")

        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
            requestBuilder.header("X-API-Key", apiKey)
        }

        if (method == "GET_QUERY") {
            val fullUrl = TtsConverterEngine.buildLoquendoGetUrl(serverUrl, text, voiceName, languageTag, speed, pitch)
            requestBuilder.url(fullUrl).get()
        } else {
            val json = JSONObject().apply {
                put("text", text)
                put("input", text)
                put("voice", voiceName)
                put("language", languageTag)
                put("lang", languageTag.substringBefore("-"))
                put("speed", speed.toDouble())
                put("rate", speed.toDouble())
                put("pitch", pitch.toDouble())
                put("format", "wav")
            }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            requestBuilder.url(serverUrl).post(body)
        }

        httpClient.newCall(requestBuilder.build()).execute().use { resp ->
            val rawBytes = resp.body?.bytes() ?: ByteArray(0)
            val contentType = resp.header("Content-Type")

            if (!resp.isSuccessful) {
                val snippet = runCatching { String(rawBytes, Charsets.UTF_8).take(140) }.getOrDefault("")
                throw IllegalStateException("Servidor HTTP ${resp.code}${if (snippet.isNotBlank()) ": $snippet" else ""}")
            }
            if (rawBytes.size < 32) {
                throw IllegalStateException("El servidor devolvió un audio vacío.")
            }
            val headText = String(rawBytes, 0, minOf(rawBytes.size, 64), Charsets.UTF_8).trimStart()
            if (headText.startsWith("{") || headText.startsWith("<html", ignoreCase = true) || headText.startsWith("<!DOCTYPE", ignoreCase = true)) {
                throw IllegalStateException("El servidor respondió texto/error en lugar de audio: ${headText.take(100)}")
            }

            val ext = TtsConverterEngine.detectAudioExtension(rawBytes, contentType)
            return rawBytes to ext
        }
    }
}
