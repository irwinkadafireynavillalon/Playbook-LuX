package com.example.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.data.datastore.NotificationTone
import com.example.data.datastore.SettingsStore
import com.example.data.local.repository.TextItemRepository
import com.example.util.NotificationChannels
import com.example.util.TextFileExtractor
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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

data class PublicBookResult(
    val remoteId: String,
    val title: String,
    val author: String,
    val source: String, // "Gutenberg", "Internet Archive", "Wikisource"
    val format: String, // "EPUB", "TXT", "HTML", "PDF"
    val language: String,
    val downloadUrl: String,
    val infoText: String = ""
)

data class DownloadTaskUi(
    val id: String,
    val result: PublicBookResult,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val isPaused: Boolean = false,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null,
    val savedTextItemId: String? = null
)

data class SearchDownloadState(
    val query: String = "",
    val selectedFormats: Set<String> = setOf("EPUB", "TXT", "HTML", "PDF"),
    val selectedSources: Set<String> = setOf("Gutenberg", "Internet Archive", "Wikisource"),
    val selectedLanguage: String = "es", // "es", "en", "all"
    val wifiOnly: Boolean = false,
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val results: List<PublicBookResult> = emptyList(),
    val downloads: List<DownloadTaskUi> = emptyList(),
    val lastMessage: String? = null
)

class PublicBookSearchEngine(
    private val context: Context,
    private val textItemRepository: TextItemRepository,
    private val settingsStore: SettingsStore,
    private val appScope: CoroutineScope
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _state = MutableStateFlow(SearchDownloadState())
    val state: StateFlow<SearchDownloadState> = _state.asStateFlow()

    private val activeJobs = mutableMapOf<String, Job>()
    private val pausedIds = mutableSetOf<String>()

    init {
        search("cervantes")
    }

    fun setQuery(q: String) {
        _state.update { it.copy(query = q) }
    }

    fun toggleFormat(format: String) {
        _state.update { st ->
            val next = st.selectedFormats.toMutableSet()
            if (next.contains(format) && next.size > 1) next.remove(format) else next.add(format)
            st.copy(selectedFormats = next)
        }
    }

    fun toggleSource(source: String) {
        _state.update { st ->
            val next = st.selectedSources.toMutableSet()
            if (next.contains(source) && next.size > 1) next.remove(source) else next.add(source)
            st.copy(selectedSources = next)
        }
    }

    fun setLanguage(lang: String) {
        _state.update { it.copy(selectedLanguage = lang) }
    }

    fun setWifiOnly(enabled: Boolean) {
        _state.update { it.copy(wifiOnly = enabled) }
    }

    fun clearMessage() {
        _state.update { it.copy(lastMessage = null) }
    }

    fun search(explicitQuery: String? = null) {
        val query = (explicitQuery ?: _state.value.query).trim()
        if (explicitQuery != null) {
            _state.update { it.copy(query = query) }
        }

        val currentState = _state.value
        val effectiveQuery = query.ifBlank { "novela" }

        appScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isSearching = true, searchError = null) }
            val combined = mutableListOf<PublicBookResult>()
            val errors = mutableListOf<String>()

            if ("Gutenberg" in currentState.selectedSources) {
                try {
                    combined += queryGutendex(effectiveQuery, currentState.selectedLanguage, currentState.selectedFormats)
                } catch (e: Exception) {
                    errors += "Gutenberg: ${e.message}"
                }
            }

            if ("Internet Archive" in currentState.selectedSources) {
                try {
                    combined += queryInternetArchive(effectiveQuery, currentState.selectedLanguage, currentState.selectedFormats)
                } catch (e: Exception) {
                    errors += "Archive: ${e.message}"
                }
            }

            if ("Wikisource" in currentState.selectedSources) {
                try {
                    combined += queryWikisource(effectiveQuery, currentState.selectedLanguage)
                } catch (e: Exception) {
                    errors += "Wikisource: ${e.message}"
                }
            }

            val filtered = combined.filter { it.format in currentState.selectedFormats || currentState.selectedFormats.isEmpty() }

            _state.update {
                it.copy(
                    isSearching = false,
                    results = filtered.distinctBy { r -> r.downloadUrl },
                    searchError = if (filtered.isEmpty() && errors.isNotEmpty()) {
                        "No se pudo conectar al catálogo (${errors.first()}). Revisa tu conexión a internet."
                    } else null
                )
            }
        }
    }

    private fun queryGutendex(query: String, lang: String, preferredFormats: Set<String>): List<PublicBookResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val langParam = if (lang == "all") "" else "&languages=$lang"
        val url = "https://gutendex.com/books?search=$encoded$langParam"
        val req = Request.Builder().url(url).header("User-Agent", "PlayLuX-Android/1.0").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val body = resp.body?.string() ?: "{}"
            val root = JSONObject(body)
            val resultsArr = root.optJSONArray("results") ?: JSONArray()
            val list = mutableListOf<PublicBookResult>()
            for (i in 0 until minOf(resultsArr.length(), 15)) {
                val obj = resultsArr.getJSONObject(i)
                val id = obj.optInt("id").toString()
                val title = obj.optString("title", "Sin título").take(120)
                val authorsArr = obj.optJSONArray("authors")
                val author = if (authorsArr != null && authorsArr.length() > 0) {
                    authorsArr.getJSONObject(0).optString("name", "Autor desconocido")
                } else "Autor desconocido"
                val langsArr = obj.optJSONArray("languages")
                val bookLang = if (langsArr != null && langsArr.length() > 0) langsArr.optString(0, "es") else "es"

                val formatsObj = obj.optJSONObject("formats") ?: continue
                val epubUrl = formatsObj.optString("application/epub+zip").takeIf { it.startsWith("http") }
                val pdfUrl = formatsObj.optString("application/pdf").takeIf { it.startsWith("http") }
                val txtUrl = formatsObj.optString("text/plain; charset=utf-8")
                    .ifBlank { formatsObj.optString("text/plain; charset=us-ascii") }
                    .ifBlank { formatsObj.optString("text/plain") }
                    .takeIf { it.startsWith("http") }
                val htmlUrl = formatsObj.optString("text/html")
                    .ifBlank { formatsObj.optString("text/html; charset=utf-8") }
                    .takeIf { it.startsWith("http") }

                val (chosenFormat, chosenUrl) = when {
                    "PDF" in preferredFormats && pdfUrl != null -> "PDF" to pdfUrl
                    "EPUB" in preferredFormats && epubUrl != null -> "EPUB" to epubUrl
                    "TXT" in preferredFormats && txtUrl != null -> "TXT" to txtUrl
                    "HTML" in preferredFormats && htmlUrl != null -> "HTML" to htmlUrl
                    epubUrl != null -> "EPUB" to epubUrl
                    pdfUrl != null -> "PDF" to pdfUrl
                    txtUrl != null -> "TXT" to txtUrl
                    htmlUrl != null -> "HTML" to htmlUrl
                    else -> continue
                }

                list.add(
                    PublicBookResult(
                        remoteId = "gut_$id",
                        title = title,
                        author = author,
                        source = "Gutenberg",
                        format = chosenFormat,
                        language = bookLang,
                        downloadUrl = chosenUrl,
                        infoText = "Project Gutenberg #$id"
                    )
                )
            }
            return list
        }
    }

    private fun queryInternetArchive(
        query: String,
        lang: String,
        preferredFormats: Set<String>
    ): List<PublicBookResult> {
        val pdfFilter = if (preferredFormats == setOf("PDF")) " AND format:(Text PDF OR Additional Text PDF OR PDF)" else ""
        val encoded = URLEncoder.encode("$query AND mediatype:texts$pdfFilter", "UTF-8")
        val url = "https://archive.org/advancedsearch.php?q=$encoded&fl[]=identifier,title,creator,language,format&rows=10&output=json"
        val req = Request.Builder().url(url).header("User-Agent", "PlayLuX-Android/1.0").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val body = resp.body?.string() ?: "{}"
            val docs = JSONObject(body).optJSONObject("response")?.optJSONArray("docs") ?: JSONArray()
            val list = mutableListOf<PublicBookResult>()
            for (i in 0 until docs.length()) {
                val obj = docs.getJSONObject(i)
                val identifier = obj.optString("identifier").takeIf { it.isNotBlank() } ?: continue
                val title = obj.optString("title", identifier).take(120)
                val creator = obj.optString("creator", "Internet Archive")
                val bookLang = obj.optString("language", lang.takeIf { it != "all" } ?: "es").take(2).lowercase()
                val formats = parseArchiveFormats(obj.opt("format"))

                val candidates = selectArchiveFormatsAndUrls(identifier, formats, preferredFormats)
                for ((fmt, dlUrl) in candidates) {
                    list.add(
                        PublicBookResult(
                            remoteId = "ia_${identifier}_$fmt",
                            title = title,
                            author = creator,
                            source = "Internet Archive",
                            format = fmt,
                            language = bookLang,
                            downloadUrl = dlUrl,
                            infoText = "Internet Archive • $fmt"
                        )
                    )
                }
            }
            return list
        }
    }

    private fun queryWikisource(query: String, lang: String): List<PublicBookResult> {
        val wikiLang = if (lang == "en") "en" else "es"
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://$wikiLang.wikisource.org/w/api.php?action=opensearch&search=$encoded&limit=8&namespace=0&format=json"
        val req = Request.Builder().url(url).header("User-Agent", "PlayLuX-Android/1.0").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val body = resp.body?.string() ?: "[]"
            val rootArr = JSONArray(body)
            val titles = rootArr.optJSONArray(1) ?: JSONArray()
            val urls = rootArr.optJSONArray(3) ?: JSONArray()
            val list = mutableListOf<PublicBookResult>()
            for (i in 0 until titles.length()) {
                val title = titles.optString(i)
                val pageUrl = urls.optString(i)
                if (title.isNotBlank() && pageUrl.startsWith("http")) {
                    list.add(
                        PublicBookResult(
                            remoteId = "ws_${wikiLang}_${title.hashCode()}",
                            title = title,
                            author = "Wikisource ($wikiLang)",
                            source = "Wikisource",
                            format = "HTML",
                            language = wikiLang,
                            downloadUrl = pageUrl,
                            infoText = "Texto completo HTML en Wikisource"
                        )
                    )
                }
            }
            return list
        }
    }

    fun startDownload(result: PublicBookResult) {
        val currentState = _state.value
        if (currentState.wifiOnly && !isOnWifi()) {
            _state.update {
                it.copy(lastMessage = "Modo Solo Wi-Fi activado: conéctate a una red Wi-Fi para descargar.")
            }
            return
        }

        if (currentState.downloads.any { it.result.remoteId == result.remoteId && !it.isCompleted && it.errorMessage == null }) {
            _state.update { it.copy(lastMessage = "Este libro ya se está descargando.") }
            return
        }

        val taskId = "dl_" + UUID.randomUUID().toString().take(8)
        val task = DownloadTaskUi(id = taskId, result = result)
        _state.update {
            it.copy(
                downloads = listOf(task) + it.downloads,
                lastMessage = "Descargando \"${result.title}\" (${result.format})…"
            )
        }

        val job = appScope.launch(Dispatchers.IO) {
            performDownload(taskId, result)
        }
        activeJobs[taskId] = job
    }

    fun togglePauseDownload(taskId: String) {
        if (pausedIds.contains(taskId)) {
            pausedIds.remove(taskId)
        } else {
            pausedIds.add(taskId)
        }
        val nowPaused = pausedIds.contains(taskId)
        _state.update { st ->
            st.copy(downloads = st.downloads.map { if (it.id == taskId) it.copy(isPaused = nowPaused) else it })
        }
    }

    fun cancelDownload(taskId: String) {
        activeJobs.remove(taskId)?.cancel()
        pausedIds.remove(taskId)
        _state.update { st ->
            st.copy(downloads = st.downloads.filterNot { it.id == taskId })
        }
    }

    private suspend fun performDownload(taskId: String, result: PublicBookResult) {
        val textId = "text_" + UUID.randomUUID().toString().take(8)
        val itemDir = File(TextFileExtractor.getTextsRootDir(context), textId).apply { mkdirs() }
        val ext = result.format.lowercase()
        val targetFile = File(itemDir, "source.$ext")
        val maxBytes = if (result.format in listOf("TXT", "MD", "HTML")) {
            TextFileExtractor.MAX_TXT_FILE_BYTES
        } else {
            TextFileExtractor.MAX_DOCUMENT_CONTAINER_BYTES
        }

        val notifId = NotificationChannels.NOTIF_ID_DOWNLOAD_BASE + (taskId.hashCode() and 0xFF)
        NotificationChannels.postProgress(
            context = context,
            channelId = NotificationChannels.CHANNEL_DOWNLOADS,
            notificationId = notifId,
            title = "Descargando libro (${result.format})",
            contentText = result.title,
            progressPercent = 0,
            ongoing = true
        )
        try {
            val req = Request.Builder()
                .url(result.downloadUrl)
                .header("User-Agent", "PlayLuX-Android/1.0")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Error HTTP ${resp.code}")
                }
                val body = resp.body ?: throw IllegalStateException("Respuesta vacía del servidor")
                val total = body.contentLength()
                if (total > 0L) {
                    TextFileExtractor.validateFileSize(result.format, total)
                }
                var downloaded = 0L
                val buffer = ByteArray(16 * 1024)
                var lastNotifTime = 0L

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        while (appScope.isActive) {
                            while (pausedIds.contains(taskId)) {
                                delay(250L)
                            }
                            val read = input.read(buffer)
                            if (read == -1) break
                            downloaded += read
                            if (downloaded > maxBytes) {
                                TextFileExtractor.validateFileSize(result.format, downloaded)
                            }
                            output.write(buffer, 0, read)

                            val pct = if (total > 0) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 0.99f) else 0.5f
                            _state.update { st ->
                                st.copy(downloads = st.downloads.map {
                                    if (it.id == taskId) it.copy(
                                        progress = pct,
                                        downloadedBytes = downloaded,
                                        totalBytes = total
                                    ) else it
                                })
                            }
                            val now = System.currentTimeMillis()
                            if (now - lastNotifTime > 900L) {
                                lastNotifTime = now
                                NotificationChannels.postProgress(
                                    context = context,
                                    channelId = NotificationChannels.CHANNEL_DOWNLOADS,
                                    notificationId = notifId,
                                    title = "Descargando ${result.title}",
                                    contentText = "${downloaded / 1024} KB • ${(pct * 100).toInt()}%",
                                    progressPercent = (pct * 100).toInt(),
                                    ongoing = true
                                )
                            }
                        }
                    }
                }
            }

            val textItem = TextFileExtractor.registerDownloadedFile(
                context = context,
                id = textId,
                downloadedFile = targetFile,
                fallbackTitle = result.title,
                fallbackAuthor = result.author,
                format = result.format,
                lang = result.language
            )
            textItemRepository.insertTextItem(textItem)

            NotificationChannels.cancel(context, notifId)
            val tone = runCatching { settingsStore.getNotificationToneSync() }.getOrDefault(NotificationTone.LUX_CHIME)
            NotificationChannels.postCompletionAlert(
                context = context,
                tone = tone,
                notificationId = notifId + 1000,
                title = "Descarga completada (${result.format})",
                contentText = "\"${result.title}\" guardado en Bóveda → Textos"
            )

            _state.update { st ->
                st.copy(
                    downloads = st.downloads.map {
                        if (it.id == taskId) it.copy(
                            progress = 1f,
                            isCompleted = true,
                            savedTextItemId = textItem.id
                        ) else it
                    },
                    lastMessage = "\"${result.title}\" (${result.format}) guardado en Bóveda → Textos"
                )
            }
        } catch (e: Exception) {
            itemDir.deleteRecursively()
            NotificationChannels.cancel(context, notifId)
            _state.update { st ->
                st.copy(downloads = st.downloads.map {
                    if (it.id == taskId) it.copy(
                        errorMessage = e.message ?: "Error de descarga"
                    ) else it
                })
            }
        } finally {
            activeJobs.remove(taskId)
            pausedIds.remove(taskId)
        }
    }

    private fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    companion object {
        fun parseArchiveFormats(rawFormatField: Any?): Set<String> {
            val out = mutableSetOf<String>()
            when (rawFormatField) {
                is JSONArray -> {
                    for (i in 0 until rawFormatField.length()) {
                        out += rawFormatField.optString(i).lowercase()
                    }
                }
                is String -> out += rawFormatField.lowercase()
            }
            return out
        }

        fun selectArchiveFormatsAndUrls(
            identifier: String,
            archiveFormats: Set<String>,
            preferredFormats: Set<String>
        ): List<Pair<String, String>> {
            val out = mutableListOf<Pair<String, String>>()
            val hasPdf = archiveFormats.isEmpty() || archiveFormats.any { it.contains("pdf") }
            val hasEpub = archiveFormats.any { it.contains("epub") }
            val hasTxt = archiveFormats.isEmpty() || archiveFormats.any { it.contains("djvu") || it.contains("text") }

            if ("PDF" in preferredFormats && hasPdf) {
                out += "PDF" to "https://archive.org/download/$identifier/$identifier.pdf"
            }
            if ("EPUB" in preferredFormats && hasEpub) {
                out += "EPUB" to "https://archive.org/download/$identifier/$identifier.epub"
            }
            if ("TXT" in preferredFormats && hasTxt) {
                out += "TXT" to "https://archive.org/download/$identifier/${identifier}_djvu.txt"
            }
            if (out.isEmpty()) {
                out += "TXT" to "https://archive.org/download/$identifier/${identifier}_djvu.txt"
            }
            return out
        }
    }
}
