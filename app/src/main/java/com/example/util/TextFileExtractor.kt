package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.entity.TextItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.UUID
import java.util.zip.ZipFile

data class ExtractedChapter(
    val title: String,
    val content: String
)

data class ExtractedDocument(
    val title: String,
    val author: String,
    val format: String,
    val chapters: List<ExtractedChapter>,
    val wordCount: Int
)

object TextFileExtractor {

    fun getTextsRootDir(context: Context): File {
        return File(context.filesDir, "texts").apply { if (!exists()) mkdirs() }
    }

    fun getDisplayName(context: Context, uri: Uri): String {
        var name = "documento_${System.currentTimeMillis()}"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(idx) ?: name
            }
        }
        return name
    }

    fun isAudioFile(fileName: String, mimeType: String?): Boolean {
        val ext = fileName.substringAfterLast('.', "").uppercase()
        return ext in listOf("MP3", "M4A", "M4B", "WAV", "OGG", "OPUS", "AAC", "FLAC", "MP4", "WMA") ||
            mimeType?.startsWith("audio/") == true ||
            mimeType?.startsWith("video/") == true
    }

    fun detectFormat(fileName: String, mimeType: String?): String {
        val ext = fileName.substringAfterLast('.', "").uppercase()
        return when {
            ext in listOf("EPUB", "TXT", "PDF", "DOCX", "HTML", "HTM", "MD") -> if (ext == "HTM") "HTML" else ext
            mimeType?.contains("epub") == true -> "EPUB"
            mimeType?.contains("pdf") == true -> "PDF"
            mimeType?.contains("wordprocessingml") == true -> "DOCX"
            mimeType?.contains("html") == true -> "HTML"
            mimeType?.contains("markdown") == true -> "MD"
            else -> "TXT"
        }
    }

    suspend fun importDocumentFromUri(
        context: Context,
        uri: Uri,
        folderId: Long? = null
    ): TextItem = withContext(Dispatchers.IO) {
        val fileName = getDisplayName(context, uri)
        val mime = context.contentResolver.getType(uri)
        if (isAudioFile(fileName, mime)) {
            throw IllegalArgumentException(
                "Solo se importan libros o textos (EPUB, TXT, PDF, DOCX, HTML, MD). Play LuX solo reproduce sus propios audiolibros convertidos."
            )
        }
        val format = detectFormat(fileName, mime)
        val id = "text_" + UUID.randomUUID().toString().take(8)
        val itemDir = File(getTextsRootDir(context), id).apply { mkdirs() }
        val rawFile = File(itemDir, "source.${format.lowercase()}")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(rawFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("No se pudo abrir el archivo seleccionado")

        val extracted = extractFromFile(rawFile, format, fileName.substringBeforeLast('.'))
        saveExtractedChapters(itemDir, extracted.chapters)

        TextItem(
            id = id,
            title = extracted.title.ifBlank { fileName.substringBeforeLast('.') },
            author = extracted.author.ifBlank { "Autor desconocido" },
            format = format,
            filePath = rawFile.absolutePath,
            sizeBytes = rawFile.length(),
            status = if (extracted.chapters.isNotEmpty()) "PREPARED" else "UNPREPARED",
            detectedLang = guessLanguageSimple(extracted.chapters.firstOrNull()?.content ?: ""),
            wordCount = extracted.wordCount,
            folderId = folderId,
            createdAt = System.currentTimeMillis()
        )
    }

    fun registerDownloadedFile(
        context: Context,
        id: String,
        downloadedFile: File,
        fallbackTitle: String,
        fallbackAuthor: String,
        format: String,
        lang: String,
        folderId: Long? = null
    ): TextItem {
        val itemDir = downloadedFile.parentFile ?: File(getTextsRootDir(context), id).apply { mkdirs() }
        val extracted = extractFromFile(downloadedFile, format, fallbackTitle)
        saveExtractedChapters(itemDir, extracted.chapters)

        return TextItem(
            id = id,
            title = if (fallbackTitle.isNotBlank()) fallbackTitle else extracted.title,
            author = if (fallbackAuthor.isNotBlank()) fallbackAuthor else extracted.author,
            format = format.uppercase(),
            filePath = downloadedFile.absolutePath,
            sizeBytes = downloadedFile.length(),
            status = if (extracted.wordCount > 0) "PREPARED" else "UNPREPARED",
            detectedLang = lang.ifBlank { guessLanguageSimple(extracted.chapters.firstOrNull()?.content ?: "") },
            wordCount = extracted.wordCount,
            folderId = folderId,
            createdAt = System.currentTimeMillis()
        )
    }

    fun extractFromFile(file: File, format: String, defaultTitle: String): ExtractedDocument {
        if (!file.exists()) {
            return ExtractedDocument(defaultTitle, "", format, emptyList(), 0)
        }
        return when (format.uppercase()) {
            "EPUB" -> extractEpub(file, defaultTitle)
            "HTML", "HTM" -> {
                val raw = readTextSmart(file)
                val title = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .find(raw)?.groupValues?.getOrNull(1)?.let { cleanHtmlText(it) }?.ifBlank { defaultTitle } ?: defaultTitle
                val body = cleanHtmlText(raw)
                val chapters = splitTextIntoChapters(body)
                ExtractedDocument(title, "", "HTML", chapters, countWords(chapters))
            }
            "DOCX" -> extractDocx(file, defaultTitle)
            "PDF" -> extractPdfBasic(file, defaultTitle)
            else -> {
                val raw = readTextSmart(file)
                val clean = cleanPlainText(raw)
                val chapters = splitTextIntoChapters(clean)
                ExtractedDocument(defaultTitle, "", format.uppercase(), chapters, countWords(chapters))
            }
        }
    }

    private fun extractEpub(file: File, defaultTitle: String): ExtractedDocument {
        return try {
            var title = defaultTitle
            var author = ""
            val htmlChapters = mutableListOf<Pair<String, String>>()

            ZipFile(file).use { zip ->
                val entries = zip.entries().toList()
                // Find OPF metadata if present
                val opfEntry = entries.firstOrNull { it.name.endsWith(".opf", ignoreCase = true) }
                if (opfEntry != null) {
                    val opfContent = zip.getInputStream(opfEntry).bufferedReader().use { it.readText() }
                    Regex("<dc:title[^>]*>(.*?)</dc:title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                        .find(opfContent)?.groupValues?.getOrNull(1)?.let {
                            val cleaned = cleanHtmlText(it)
                            if (cleaned.isNotBlank()) title = cleaned
                        }
                    Regex("<dc:creator[^>]*>(.*?)</dc:creator>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                        .find(opfContent)?.groupValues?.getOrNull(1)?.let {
                            val cleaned = cleanHtmlText(it)
                            if (cleaned.isNotBlank()) author = cleaned
                        }
                }

                // Read XHTML / HTML content files sorted cleanly
                val contentEntries = entries
                    .filter {
                        !it.isDirectory && (
                            it.name.endsWith(".xhtml", ignoreCase = true) ||
                            it.name.endsWith(".html", ignoreCase = true) ||
                            it.name.endsWith(".htm", ignoreCase = true)
                        ) && !it.name.contains("toc", ignoreCase = true)
                          && !it.name.contains("nav", ignoreCase = true)
                          && !it.name.contains("cover", ignoreCase = true)
                    }
                    .sortedBy { it.name }

                contentEntries.forEachIndexed { idx, entry ->
                    val raw = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    val heading = Regex("<h[1-3][^>]*>(.*?)</h[1-3]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                        .find(raw)?.groupValues?.getOrNull(1)?.let { cleanHtmlText(it) }
                        ?.take(80)
                        ?.ifBlank { null }
                    val body = cleanHtmlText(raw)
                    if (countWords(body) >= 15) {
                        htmlChapters.add((heading ?: "Capítulo ${htmlChapters.size + 1}") to body)
                    }
                }
            }

            val chapters = if (htmlChapters.isNotEmpty()) {
                htmlChapters.map { ExtractedChapter(it.first, it.second) }
            } else {
                listOf(ExtractedChapter("Capítulo 1", "No se encontró texto legible en este EPUB."))
            }
            ExtractedDocument(title, author, "EPUB", chapters, countWords(chapters))
        } catch (e: Exception) {
            ExtractedDocument(defaultTitle, "", "EPUB", listOf(ExtractedChapter("Capítulo 1", "Archivo EPUB importado ($defaultTitle).")), 10)
        }
    }

    private fun extractDocx(file: File, defaultTitle: String): ExtractedDocument {
        return try {
            val textBuilder = StringBuilder()
            ZipFile(file).use { zip ->
                val docEntry = zip.getEntry("word/document.xml")
                if (docEntry != null) {
                    val xml = zip.getInputStream(docEntry).bufferedReader().use { it.readText() }
                    val cleaned = xml
                        .replace(Regex("</w:p>"), "\n\n")
                        .replace(Regex("<[^>]+>"), "")
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                    textBuilder.append(cleaned)
                }
            }
            val clean = cleanPlainText(textBuilder.toString())
            val chapters = splitTextIntoChapters(clean)
            ExtractedDocument(defaultTitle, "", "DOCX", chapters, countWords(chapters))
        } catch (_: Exception) {
            ExtractedDocument(defaultTitle, "", "DOCX", emptyList(), 0)
        }
    }

    private fun extractPdfBasic(file: File, defaultTitle: String): ExtractedDocument {
        // Basic text-stream extraction for uncompressed PDF text operators; fallback safe message if binary compressed
        return try {
            val bytes = file.readBytes()
            val latin = String(bytes, Charset.forName("ISO-8859-1"))
            val matches = Regex("\\(([^()\\\\]{3,200})\\)\\s*Tj").findAll(latin)
            val sb = StringBuilder()
            for (m in matches) {
                val part = m.groupValues[1].trim()
                if (part.any { it.isLetter() }) {
                    sb.append(part).append(" ")
                }
            }
            val clean = cleanPlainText(sb.toString())
            val finalContent = if (clean.length > 40) clean else {
                "Documento PDF '$defaultTitle' importado correctamente. Puedes editar o pegar su texto si el PDF está comprimido o escaneado."
            }
            val chapters = splitTextIntoChapters(finalContent)
            ExtractedDocument(defaultTitle, "", "PDF", chapters, countWords(chapters))
        } catch (_: Exception) {
            val fallback = listOf(ExtractedChapter("Capítulo 1", "Documento PDF '$defaultTitle'."))
            ExtractedDocument(defaultTitle, "", "PDF", fallback, 4)
        }
    }

    fun saveExtractedChapters(itemDir: File, chapters: List<ExtractedChapter>) {
        if (!itemDir.exists()) itemDir.mkdirs()
        val arr = JSONArray()
        chapters.forEach { ch ->
            val obj = JSONObject()
            obj.put("title", ch.title)
            obj.put("content", ch.content)
            arr.put(obj)
        }
        File(itemDir, "chapters.json").writeText(arr.toString())
    }

    fun loadChaptersForTextItem(context: Context, item: TextItem): List<ExtractedChapter> {
        val rawFile = File(item.filePath)
        val itemDir = rawFile.parentFile ?: File(getTextsRootDir(context), item.id)
        val chaptersJsonFile = File(itemDir, "chapters.json")
        if (chaptersJsonFile.exists()) {
            try {
                val arr = JSONArray(chaptersJsonFile.readText())
                val list = mutableListOf<ExtractedChapter>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val title = obj.optString("title", "Capítulo ${i + 1}")
                    val content = obj.optString("content", "")
                    if (content.isNotBlank()) {
                        list.add(ExtractedChapter(title, content))
                    }
                }
                if (list.isNotEmpty()) return list
            } catch (_: Exception) {
            }
        }
        if (rawFile.exists()) {
            val extracted = extractFromFile(rawFile, item.format, item.title)
            if (extracted.chapters.isNotEmpty()) {
                saveExtractedChapters(itemDir, extracted.chapters)
                return extracted.chapters
            }
        }
        // Fallback sample content if it was created as a sample entry without raw file
        return listOf(
            ExtractedChapter(
                title = "Capítulo 1: ${item.title}",
                content = "En un lugar de la Mancha, de cuyo nombre no quiero acordarme, no ha mucho tiempo que vivía un hidalgo de los de lanza en astillero, adarga antigua, rocín flaco y galgo corredor. Esta obra pertenece a tu biblioteca local de Play LuX y está lista para ser escuchada o traducida directamente en tu teléfono."
            ),
            ExtractedChapter(
                title = "Capítulo 2: La aventura continúa",
                content = "Hechas, pues, estas prevenciones, no quiso aguardar más tiempo a poner en efecto su pensamiento, apretándole a ello la falta que él pensaba que hacía en el mundo su tardanza, según eran los agravios que pensaba deshacer, tuertos que enderezar, sinrazones que emendar y abusos que mejorar."
            )
        )
    }

    fun saveTranslatedChapters(
        context: Context,
        sourceItem: TextItem,
        targetLang: String,
        translatedChapters: List<ExtractedChapter>
    ): TextItem {
        val newId = "text_" + UUID.randomUUID().toString().take(8)
        val itemDir = File(getTextsRootDir(context), newId).apply { mkdirs() }
        val txtFile = File(itemDir, "translated_${targetLang}.txt")
        val fullText = translatedChapters.joinToString("\n\n") { "${it.title}\n\n${it.content}" }
        txtFile.writeText(fullText)
        saveExtractedChapters(itemDir, translatedChapters)

        val langLabel = targetLang.uppercase()
        return TextItem(
            id = newId,
            title = "${sourceItem.title} ($langLabel)",
            author = sourceItem.author,
            format = "TXT",
            filePath = txtFile.absolutePath,
            sizeBytes = txtFile.length(),
            status = "PREPARED",
            detectedLang = targetLang,
            wordCount = countWords(translatedChapters),
            folderId = sourceItem.folderId,
            createdAt = System.currentTimeMillis()
        )
    }

    fun splitTextIntoChapters(text: String, maxWordsPerChapter: Int = 1500): List<ExtractedChapter> {
        val cleaned = cleanPlainText(text)
        if (cleaned.isBlank()) return emptyList()

        // Try splitting by explicit chapter headings first
        val headingRegex = Regex("(?m)^(#{1,3}\\s+.+|(?:Capítulo|CAPÍTULO|Chapter|CHAPTER|Parte|PARTE)\\s+[\\dIVXLCDM]+.*)$")
        val matches = headingRegex.findAll(cleaned).toList()

        if (matches.size >= 2) {
            val chapters = mutableListOf<ExtractedChapter>()
            val preamble = cleaned.substring(0, matches.first().range.first).trim()
            if (countWords(preamble) > 30) {
                chapters.add(ExtractedChapter("Introducción", preamble))
            }
            for (i in matches.indices) {
                val start = matches[i].range.last + 1
                val end = if (i + 1 < matches.size) matches[i + 1].range.first else cleaned.length
                val title = matches[i].value.replace("#", "").trim().take(80)
                val body = cleaned.substring(start, end).trim()
                if (body.isNotBlank()) {
                    chapters.add(ExtractedChapter(title, body))
                }
            }
            if (chapters.isNotEmpty()) return chapters
        }

        // Split by paragraphs up to maxWordsPerChapter
        val paragraphs = cleaned.split(Regex("\\n{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
        val chapters = mutableListOf<ExtractedChapter>()
        val currentSb = StringBuilder()
        var currentWords = 0

        for (p in paragraphs) {
            val pWords = countWords(p)
            if (currentWords > 0 && currentWords + pWords > maxWordsPerChapter) {
                chapters.add(ExtractedChapter("Capítulo ${chapters.size + 1}", currentSb.toString().trim()))
                currentSb.clear()
                currentWords = 0
            }
            if (currentSb.isNotEmpty()) currentSb.append("\n\n")
            currentSb.append(p)
            currentWords += pWords
        }
        if (currentSb.isNotEmpty()) {
            chapters.add(ExtractedChapter("Capítulo ${chapters.size + 1}", currentSb.toString().trim()))
        }
        return chapters
    }

    fun cleanHtmlText(html: String): String {
        return html
            .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("</?(p|div|br|h[1-6]|li|tr)[^>]*>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .let { cleanPlainText(it) }
    }

    fun cleanPlainText(raw: String): String {
        return raw
            .replace("\r\n", "\n")
            .replace(Regex("-\n([a-záéíóúñ])"), "$1") // join hyphenated line breaks
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun readTextSmart(file: File): String {
        val bytes = file.readBytes()
        return String(bytes, Charsets.UTF_8)
    }

    fun countWords(text: String): Int {
        if (text.isBlank()) return 0
        return text.trim().split(Regex("\\s+")).size
    }

    fun countWords(chapters: List<ExtractedChapter>): Int {
        return chapters.sumOf { countWords(it.content) }
    }

    fun guessLanguageSimple(sample: String): String {
        val lower = sample.take(1000).lowercase()
        val spanishHits = listOf(" el ", " la ", " de ", " que ", " y ", " los ", " las ").count { lower.contains(it) }
        val englishHits = listOf(" the ", " and ", " of ", " to ", " in ", " that ", " is ").count { lower.contains(it) }
        return if (englishHits > spanishHits) "en" else "es"
    }
}
