package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.example.data.local.entity.TextItem
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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

    const val MAX_TXT_FILE_BYTES: Long = 5L * 1024L * 1024L // 5 MB para TXT / MD / HTML
    const val MAX_DOCUMENT_CONTAINER_BYTES: Long = 50L * 1024L * 1024L // 50 MB para EPUB / PDF / DOCX
    const val MAX_PASTED_CHARS: Int = 600_000
    private const val MAX_OCR_PAGES = 35

    private val pdfBoxInitialized = AtomicBoolean(false)

    private fun ensurePdfBoxInitialized(context: Context?) {
        if (context != null && pdfBoxInitialized.compareAndSet(false, true)) {
            runCatching { PDFBoxResourceLoader.init(context.applicationContext) }
        }
    }

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

    fun queryUriSizeBytes(context: Context, uri: Uri): Long {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) {
                    return cursor.getLong(idx)
                }
            }
        }
        return -1L
    }

    fun validateFileSize(format: String, sizeBytes: Long) {
        if (sizeBytes <= 0L) return
        val upper = format.uppercase()
        if (upper in listOf("TXT", "MD", "HTML", "HTM")) {
            if (sizeBytes > MAX_TXT_FILE_BYTES) {
                val mb = String.format("%.1f", sizeBytes / (1024.0 * 1024.0))
                throw IllegalArgumentException(
                    "El archivo $upper pesa $mb MB y supera el límite máximo de 5.0 MB. Divide el texto o usa EPUB/PDF."
                )
            }
        } else if (sizeBytes > MAX_DOCUMENT_CONTAINER_BYTES) {
            val mb = String.format("%.1f", sizeBytes / (1024.0 * 1024.0))
            throw IllegalArgumentException(
                "El archivo $upper pesa $mb MB y supera el límite máximo de 50 MB."
            )
        }
    }

    fun validatePastedTextLength(content: String) {
        if (content.length > MAX_PASTED_CHARS) {
            throw IllegalArgumentException(
                "El texto supera el límite de ${MAX_PASTED_CHARS / 1000}K caracteres por nota (${content.length} caracteres)."
            )
        }
        val byteLen = content.toByteArray(Charsets.UTF_8).size.toLong()
        validateFileSize("TXT", byteLen)
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
        ensurePdfBoxInitialized(context)
        val fileName = getDisplayName(context, uri)
        val mime = context.contentResolver.getType(uri)
        if (isAudioFile(fileName, mime)) {
            throw IllegalArgumentException(
                "Solo se importan libros o textos (EPUB, TXT, PDF, DOCX, HTML, MD). Play LuX solo reproduce sus propios audiolibros convertidos."
            )
        }
        val format = detectFormat(fileName, mime)
        val preReportedSize = queryUriSizeBytes(context, uri)
        validateFileSize(format, preReportedSize)

        val id = "text_" + UUID.randomUUID().toString().take(8)
        val itemDir = File(getTextsRootDir(context), id).apply { mkdirs() }
        val rawFile = File(itemDir, "source.${format.lowercase()}")

        val maxBytes = if (format in listOf("TXT", "MD", "HTML")) MAX_TXT_FILE_BYTES else MAX_DOCUMENT_CONTAINER_BYTES
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(rawFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        copied += read
                        if (copied > maxBytes) {
                            validateFileSize(format, copied)
                        }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: throw IllegalStateException("No se pudo abrir el archivo seleccionado")

            validateFileSize(format, rawFile.length())

            val extracted = extractFromFile(rawFile, format, fileName.substringBeforeLast('.'), context)
            val review = TextSyntaxReviewer.reviewChapters(
                extracted.chapters,
                guessLanguageSimple(extracted.chapters.firstOrNull()?.content ?: "")
            )
            saveExtractedChapters(itemDir, review.reviewedChapters)

            TextItem(
                id = id,
                title = extracted.title.ifBlank { fileName.substringBeforeLast('.') },
                author = extracted.author.ifBlank { "Autor desconocido" },
                format = format,
                filePath = rawFile.absolutePath,
                sizeBytes = rawFile.length(),
                status = if (review.reviewedChapters.isNotEmpty()) "PREPARED" else "UNPREPARED",
                detectedLang = guessLanguageSimple(review.reviewedChapters.firstOrNull()?.content ?: ""),
                wordCount = countWords(review.reviewedChapters),
                folderId = folderId,
                createdAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            itemDir.deleteRecursively()
            throw e
        }
    }

    fun registerSharedPlainText(
        context: Context,
        title: String,
        author: String,
        content: String,
        folderId: Long? = null
    ): TextItem {
        val trimmed = content.trim()
        if (trimmed.isBlank()) throw IllegalArgumentException("El texto compartido está vacío.")
        validatePastedTextLength(trimmed)

        val id = "text_" + UUID.randomUUID().toString().take(8)
        val itemDir = File(getTextsRootDir(context), id).apply { mkdirs() }
        val rawFile = File(itemDir, "source.txt")
        val lang = guessLanguageSimple(trimmed)
        val rawChapters = splitTextIntoChapters(trimmed)
        val reviewed = TextSyntaxReviewer.reviewChapters(rawChapters, lang).reviewedChapters
        rawFile.writeText(reviewed.joinToString("\n\n") { "${it.title}\n\n${it.content}" })
        saveExtractedChapters(itemDir, reviewed)

        val inferredTitle = title.trim().ifBlank {
            trimmed.lineSequence().firstOrNull { it.isNotBlank() }?.take(48) ?: "Texto compartido"
        }

        return TextItem(
            id = id,
            title = inferredTitle,
            author = author.trim().ifBlank { "Compartido" },
            format = "TXT",
            filePath = rawFile.absolutePath,
            sizeBytes = rawFile.length(),
            status = "PREPARED",
            detectedLang = lang,
            wordCount = countWords(reviewed),
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
        ensurePdfBoxInitialized(context)
        validateFileSize(format, downloadedFile.length())
        val itemDir = downloadedFile.parentFile ?: File(getTextsRootDir(context), id).apply { mkdirs() }
        val extracted = extractFromFile(downloadedFile, format, fallbackTitle, context)
        val effectiveLang = lang.ifBlank { guessLanguageSimple(extracted.chapters.firstOrNull()?.content ?: "") }
        val cleanedChapters = TextSyntaxReviewer.reviewChapters(extracted.chapters, effectiveLang).reviewedChapters
        saveExtractedChapters(itemDir, cleanedChapters)

        return TextItem(
            id = id,
            title = if (fallbackTitle.isNotBlank()) fallbackTitle else extracted.title,
            author = if (fallbackAuthor.isNotBlank()) fallbackAuthor else extracted.author,
            format = format.uppercase(),
            filePath = downloadedFile.absolutePath,
            sizeBytes = downloadedFile.length(),
            status = if (cleanedChapters.isNotEmpty() && countWords(cleanedChapters) > 0) "PREPARED" else "UNPREPARED",
            detectedLang = effectiveLang,
            wordCount = countWords(cleanedChapters),
            folderId = folderId,
            createdAt = System.currentTimeMillis()
        )
    }

    fun saveEditedTextItem(
        context: Context,
        item: TextItem,
        newTitle: String,
        newAuthor: String,
        newLang: String,
        editedChapters: List<ExtractedChapter>
    ): TextItem {
        val rawFile = File(item.filePath)
        val itemDir = rawFile.parentFile ?: File(getTextsRootDir(context), item.id).apply { mkdirs() }
        saveExtractedChapters(itemDir, editedChapters)

        // Update plain companion file so future reloads stay consistent
        val fullText = editedChapters.joinToString("\n\n") { "${it.title}\n\n${it.content}" }
        val textFile = if (rawFile.extension.lowercase() in listOf("txt", "md")) rawFile else File(itemDir, "edited.txt")
        textFile.writeText(fullText)

        val words = countWords(editedChapters)
        return item.copy(
            title = newTitle.trim().ifBlank { item.title },
            author = newAuthor.trim().ifBlank { "Autor desconocido" },
            detectedLang = newLang.ifBlank { item.detectedLang },
            sizeBytes = textFile.length().coerceAtLeast(item.sizeBytes),
            wordCount = words,
            status = if (words > 0) "PREPARED" else "UNPREPARED"
        )
    }

    fun extractFromFile(
        file: File,
        format: String,
        defaultTitle: String,
        context: Context? = null
    ): ExtractedDocument {
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
            "PDF" -> extractPdfFull(file, defaultTitle, context)
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

                val contentEntries = entries
                    .filter {
                        !it.isDirectory && (
                            it.name.endsWith(".xhtml", ignoreCase = true) ||
                                it.name.endsWith(".html", ignoreCase = true) ||
                                it.name.endsWith(".htm", ignoreCase = true)
                            ) && !it.name.contains("toc", ignoreCase = true) &&
                            !it.name.contains("nav", ignoreCase = true) &&
                            !it.name.contains("cover", ignoreCase = true)
                    }
                    .sortedBy { it.name }

                contentEntries.forEach { entry ->
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
        } catch (_: Exception) {
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

    /**
     * Real compressed PDF text extractor powered by PDFBox-Android,
     * with automatic on-device OCR fallback (PdfRenderer + ML Kit TextRecognition) for scanned PDFs.
     */
    private fun extractPdfFull(
        file: File,
        defaultTitle: String,
        context: Context?
    ): ExtractedDocument {
        ensurePdfBoxInitialized(context)
        var docTitle = defaultTitle
        var docAuthor = ""
        var pdfBoxText = ""

        // 1. Primary path: PDFBox decodes compressed FlateDecode/CMap streams accurately
        runCatching {
            PDDocument.load(file).use { document ->
                document.documentInformation?.title?.takeIf { it.isNotBlank() }?.let {
                    docTitle = it.trim().take(120)
                }
                document.documentInformation?.author?.takeIf { it.isNotBlank() }?.let {
                    docAuthor = it.trim().take(100)
                }
                val stripper = PDFTextStripper().apply {
                    sortByPosition = true
                    paragraphStart = "\n\n"
                }
                pdfBoxText = cleanPlainText(stripper.getText(document))
            }
        }

        if (countWords(pdfBoxText) >= 15) {
            val chapters = splitTextIntoChapters(pdfBoxText)
            return ExtractedDocument(docTitle, docAuthor, "PDF", chapters, countWords(chapters))
        }

        // 2. Fallback OCR path for scanned/image-only PDFs using Android PdfRenderer + ML Kit OCR
        val ocrText = runCatching { extractPdfWithOcr(file) }.getOrNull().orEmpty()
        if (countWords(ocrText) >= 8) {
            val chapters = splitTextIntoChapters(ocrText)
            return ExtractedDocument(docTitle, docAuthor, "PDF", chapters, countWords(chapters))
        }

        // 3. Last-resort uncompressed literal operator scan
        val basicText = extractPdfRawLiteralsFallback(file)
        val finalContent = when {
            countWords(basicText) >= 10 -> basicText
            pdfBoxText.isNotBlank() -> pdfBoxText
            else -> "No se detectó texto seleccionable ni OCR legible en '$defaultTitle'. Usa Editar/Revisar texto para pegar contenido manual."
        }
        val chapters = splitTextIntoChapters(finalContent)
        return ExtractedDocument(docTitle, docAuthor, "PDF", chapters, countWords(chapters))
    }

    private fun extractPdfWithOcr(file: File): String {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) ?: return ""
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val resultBuilder = StringBuilder()
        try {
            PdfRenderer(pfd).use { renderer ->
                val pageLimit = minOf(renderer.pageCount, MAX_OCR_PAGES)
                for (i in 0 until pageLimit) {
                    renderer.openPage(i).use { page ->
                        val width = (page.width * 1.5f).toInt().coerceIn(720, 1650)
                        val height = (page.height * 1.5f).toInt().coerceIn(960, 2300)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        try {
                            Canvas(bitmap).drawColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val inputImage = InputImage.fromBitmap(bitmap, 0)
                            val visionText = Tasks.await(recognizer.process(inputImage), 15, TimeUnit.SECONDS)
                            val pageText = visionText.text.trim()
                            if (pageText.isNotBlank()) {
                                if (resultBuilder.isNotEmpty()) resultBuilder.append("\n\n")
                                resultBuilder.append(pageText)
                            }
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        } finally {
            runCatching { recognizer.close() }
            runCatching { pfd.close() }
        }
        return cleanPlainText(resultBuilder.toString())
    }

    private fun extractPdfRawLiteralsFallback(file: File): String {
        return runCatching {
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
            cleanPlainText(sb.toString())
        }.getOrDefault("")
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
            val extracted = extractFromFile(rawFile, item.format, item.title, context)
            if (extracted.chapters.isNotEmpty()) {
                saveExtractedChapters(itemDir, extracted.chapters)
                return extracted.chapters
            }
        }
        return listOf(
            ExtractedChapter(
                title = "Capítulo 1: ${item.title}",
                content = "Contenido local de ${item.title} listo para editar, traducir o convertir a audio en Play LuX."
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
            .replace(Regex("-\n([a-záéíóúñ])"), "$1")
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
