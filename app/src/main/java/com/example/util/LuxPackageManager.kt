package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.entity.Book
import com.example.data.local.entity.Chapter
import com.example.data.local.repository.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Handles exporting and importing Play LuX `.lux` audiobook packages (ZIP archives containing
 * manifest.json, chapter audio files, cover image, and synchronized chapters_text.json).
 */
object LuxPackageManager {

    const val PACKAGE_VERSION = 1

    fun buildSafeFileName(title: String): String {
        val clean = title.trim()
            .replace(Regex("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ _-]"), "")
            .replace(Regex("\\s+"), "_")
            .take(48)
            .ifBlank { "Audiolibro" }
        return "$clean.lux"
    }

    suspend fun exportBookToFile(
        context: Context,
        book: Book,
        chapters: List<Chapter>,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { zos ->
            val manifest = JSONObject().apply {
                put("packageType", "PLAYLUX_BOOK")
                put("version", PACKAGE_VERSION)
                put("title", book.title)
                put("author", book.author)
                put("lang", book.lang)
                put("durationMs", book.durationMs)
                put("speed", book.speed.toDouble())
                put("sourceTextId", book.sourceTextId ?: "")
                val chaptersArr = JSONArray()
                chapters.sortedBy { it.index }.forEach { ch ->
                    val audioFile = File(ch.filePath)
                    val entryName = "audio/${ch.index}_${audioFile.name}"
                    if (audioFile.exists()) {
                        writeFileToZip(zos, audioFile, entryName)
                    }
                    chaptersArr.put(
                        JSONObject().apply {
                            put("index", ch.index)
                            put("title", ch.title)
                            put("entryName", entryName)
                            put("durationMs", ch.durationMs)
                        }
                    )
                }
                put("chapters", chaptersArr)
            }

            // Cover image if present
            val coverFile = book.coverPath?.let { File(it) }
            if (coverFile != null && coverFile.exists() && coverFile.length() > 0) {
                writeFileToZip(zos, coverFile, "cover.png")
                manifest.put("hasCover", true)
            } else {
                manifest.put("hasCover", false)
            }

            // Optional synchronized chapter texts
            val bookDir = File(context.filesDir, "books/${book.id}")
            val textJson = File(bookDir, "chapters_text.json")
            if (textJson.exists()) {
                writeFileToZip(zos, textJson, "chapters_text.json")
            }

            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        outputFile
    }

    suspend fun exportBookToUri(
        context: Context,
        book: Book,
        chapters: List<Chapter>,
        destUri: Uri
    ) = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "export_${UUID.randomUUID()}.lux")
        try {
            exportBookToFile(context, book, chapters, tempFile)
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                FileInputStream(tempFile).use { input ->
                    input.copyTo(out)
                }
            } ?: throw IllegalStateException("No se pudo escribir el paquete .lux en el destino.")
        } finally {
            tempFile.delete()
        }
    }

    suspend fun createShareIntent(
        context: Context,
        book: Book,
        chapters: List<Chapter>
    ): Intent = withContext(Dispatchers.IO) {
        val shareDir = File(context.cacheDir, "share").apply { mkdirs() }
        val targetFile = File(shareDir, buildSafeFileName(book.title))
        exportBookToFile(context, book, chapters, targetFile)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            targetFile
        )
        Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${book.title} (.lux)")
            putExtra(Intent.EXTRA_TEXT, "Audiolibro \"${book.title}\" de ${book.author} en formato Play LuX (.lux).")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun importLuxPackageFromUri(
        context: Context,
        uri: Uri,
        folderId: Long?,
        bookRepository: BookRepository
    ): Book = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "import_${UUID.randomUUID()}.lux")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { out -> input.copyTo(out) }
            } ?: throw IllegalStateException("No se pudo abrir el archivo .lux.")
            importLuxPackageFile(context, tempFile, folderId, bookRepository)
        } finally {
            tempFile.delete()
        }
    }

    suspend fun importLuxPackageFile(
        context: Context,
        luxFile: File,
        folderId: Long?,
        bookRepository: BookRepository
    ): Book = withContext(Dispatchers.IO) {
        val bookId = "lux_" + UUID.randomUUID().toString().take(8)
        val bookDir = File(context.filesDir, "books/$bookId").apply { mkdirs() }
        val canonicalDir = bookDir.canonicalPath

        var manifestJson: JSONObject? = null

        ZipInputStream(BufferedInputStream(FileInputStream(luxFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name
                if (!entry.isDirectory) {
                    val outFile = File(bookDir, entryName)
                    if (!outFile.canonicalPath.startsWith(canonicalDir + File.separator)) {
                        throw SecurityException("Entrada inválida en paquete .lux")
                    }
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                    if (entryName == "manifest.json") {
                        manifestJson = JSONObject(outFile.readText(Charsets.UTF_8))
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val manifest = manifestJson
        if (manifest == null || manifest.optString("packageType") != "PLAYLUX_BOOK") {
            bookDir.deleteRecursively()
            throw IllegalArgumentException("El archivo no es un paquete Play LuX (.lux) válido.")
        }

        val title = manifest.optString("title", "Audiolibro importado").ifBlank { "Audiolibro importado" }
        val author = manifest.optString("author", "Desconocido").ifBlank { "Desconocido" }
        val lang = manifest.optString("lang", "es")
        val speed = manifest.optDouble("speed", 1.0).toFloat().coerceIn(0.5f, 3.0f)
        val sourceTextId = manifest.optString("sourceTextId", "").ifBlank { null }

        val coverFile = File(bookDir, "cover.png")
        val coverPath = if (coverFile.exists() && coverFile.length() > 0) {
            coverFile.absolutePath
        } else {
            CoverGenerator.generateDefaultCoverFile(context, bookId, title, author)
        }

        val chArr = manifest.optJSONArray("chapters") ?: JSONArray()
        val chapters = mutableListOf<Chapter>()
        var totalDuration = 0L

        for (i in 0 until chArr.length()) {
            val chObj = chArr.optJSONObject(i) ?: continue
            val entryName = chObj.optString("entryName", "")
            val chFile = File(bookDir, entryName)
            if (chFile.exists()) {
                val dur = chObj.optLong("durationMs", 1000L).coerceAtLeast(1000L)
                totalDuration += dur
                chapters.add(
                    Chapter(
                        bookId = bookId,
                        partId = null,
                        index = chObj.optInt("index", i),
                        title = chObj.optString("title", "Capítulo ${i + 1}"),
                        filePath = chFile.absolutePath,
                        durationMs = dur
                    )
                )
            }
        }

        if (chapters.isEmpty()) {
            bookDir.deleteRecursively()
            throw IllegalArgumentException("El paquete .lux no contiene capítulos de audio válidos.")
        }

        val book = Book(
            id = bookId,
            title = title,
            author = author,
            lang = lang,
            coverPath = coverPath,
            durationMs = manifest.optLong("durationMs", totalDuration).coerceAtLeast(totalDuration),
            folderId = folderId,
            speed = speed,
            sourceTextId = sourceTextId,
            createdAt = System.currentTimeMillis()
        )
        bookRepository.insertBookWithChapters(book, emptyList(), chapters.sortedBy { it.index })
        book
    }

    private fun writeFileToZip(zos: ZipOutputStream, file: File, entryName: String) {
        zos.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { fis -> fis.copyTo(zos) }
        zos.closeEntry()
    }
}
