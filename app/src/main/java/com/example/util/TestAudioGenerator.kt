package com.example.util

import android.content.Context
import com.example.data.local.entity.Book
import com.example.data.local.entity.Chapter
import com.example.data.local.entity.Part
import com.example.data.local.repository.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.sin

object TestAudioGenerator {

    /**
     * B9: ONLY in build debug: "Crear libro de prueba"
     * Generates 3 synthesized audio chapters (WAV format, 60s, 75s, 90s)
     * with harmonic chords and gentle chimes in filesDir/books/<id>/,
     * generates default cover, and registers them in Room database.
     */
    suspend fun createTestBook(
        context: Context,
        bookRepository: BookRepository
    ): String = withContext(Dispatchers.IO) {
        val testBooksCount = File(context.filesDir, "books").listFiles()?.count { it.name.startsWith("test_book_") } ?: 0
        val bookNumber = testBooksCount + 1

        val bookId = "test_book_${bookNumber}_" + UUID.randomUUID().toString().take(6)
        val bookDir = File(context.filesDir, "books/$bookId")
        if (!bookDir.exists()) {
            bookDir.mkdirs()
        }

        val testPresets = listOf(
            Triple("Libro de prueba #$bookNumber: El Faro de Alejandría", "Marcus Valerius", listOf(
                "Capítulo 1: La torre sobre el mar",
                "Capítulo 2: El fuego inextinguible",
                "Capítulo 3: Ecos de la biblioteca"
            )),
            Triple("Libro de prueba #$bookNumber: La Ciudad Esmeralda", "L. Frank Baum", listOf(
                "Capítulo 1: El camino de baldosas amarillas",
                "Capítulo 2: Los guardianes de las puertas",
                "Capítulo 3: El gran mago de Oz"
            )),
            Triple("Libro de prueba #$bookNumber: Las Sombras del Tiempo", "H. G. Wells", listOf(
                "Capítulo 1: La invención de la máquina",
                "Capítulo 2: El año 802.701",
                "Capítulo 3: Los Eloi y los Morlocks"
            )),
            Triple("Libro de prueba #$bookNumber: El Misterio del Valle", "Arthur Conan Doyle", listOf(
                "Capítulo 1: El mensaje cifrado",
                "Capítulo 2: La tragedia de Birlstone",
                "Capítulo 3: La revelación final"
            ))
        )

        val preset = testPresets[(bookNumber - 1) % testPresets.size]
        val title = preset.first
        val author = preset.second
        val chapterTitles = preset.third
        val coverPath = CoverGenerator.generateDefaultCoverFile(context, bookId, title, author)

        // Generate 3 chapters: 60s, 75s, 90s
        val chapterDurationsSec = listOf(60, 75, 90)
        val baseFrequencies = listOf(261.63, 329.63, 392.00) // C4, E4, G4 pleasant warm chord

        val chapters = mutableListOf<Chapter>()
        var totalDurationMs = 0L

        chapterDurationsSec.forEachIndexed { index, durationSec ->
            val tmpWav = File(context.cacheDir, "test_ch_${bookId}_${index + 1}.wav")
            generateWavTone(
                file = tmpWav,
                durationSeconds = durationSec,
                frequencyHz = baseFrequencies[index]
            )

            val encoded = OpusAudioEncoder.encodeWavChunksToOpusOrFallback(
                wavChunks = listOf(tmpWav),
                tempDir = context.cacheDir,
                outputDir = bookDir,
                baseFileName = "chapter_${index + 1}",
                paragraphPauseMs = 0
            )
            tmpWav.delete()

            val durationMs = encoded.durationMs.takeIf { it > 0 } ?: (durationSec * 1000L)
            totalDurationMs += durationMs

            chapters.add(
                Chapter(
                    bookId = bookId,
                    partId = null,
                    index = index,
                    title = chapterTitles[index],
                    filePath = encoded.file.absolutePath,
                    durationMs = durationMs
                )
            )
        }

        val part = Part(
            bookId = bookId,
            index = 0,
            title = "Parte I: Alejandría"
        )

        val book = Book(
            id = bookId,
            title = title,
            author = author,
            lang = "es",
            coverPath = coverPath,
            durationMs = totalDurationMs,
            folderId = null,
            favorite = true,
            finished = false,
            lastChapter = 0,
            lastPosMs = 0L,
            speed = 1.0f,
            isCorrupted = false,
            createdAt = System.currentTimeMillis()
        )

        bookRepository.insertBookWithChapters(
            book = book,
            parts = listOf(part),
            chapters = chapters
        )

        bookId
    }

    /**
     * Synthesizes 16-bit PCM 22050Hz mono WAV file with smooth attack, harmonic overtones,
     * and rhythmic gentle chimes that make listening pleasant during testing.
     */
    private fun generateWavTone(
        file: File,
        durationSeconds: Int,
        frequencyHz: Double
    ) {
        val sampleRate = 22050
        val numSamples = durationSeconds * sampleRate
        val dataSize = numSamples * 2 // 16-bit = 2 bytes per sample

        FileOutputStream(file).use { fos ->
            // Write placeholder 44-byte WAV header
            val header = ByteArray(44)
            fos.write(header)

            val buffer = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
            val twoPi = 2.0 * Math.PI

            for (i in 0 until numSamples) {
                val time = i.toDouble() / sampleRate
                // Periodic harmonic wave with gentle tremolo and envelope
                val base = sin(twoPi * frequencyHz * time)
                val overtone = 0.3 * sin(twoPi * (frequencyHz * 1.5) * time)
                val ambientModulation = 0.5 + 0.5 * sin(twoPi * 0.25 * time)

                // Envelope attack and release
                val envelope = when {
                    time < 0.2 -> (time / 0.2)
                    time > durationSeconds - 0.5 -> ((durationSeconds - time) / 0.5)
                    else -> 1.0
                }

                val sample = ((base + overtone) * ambientModulation * envelope * 0.35 * Short.MAX_VALUE).toInt()
                val clamped = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

                buffer.putShort(clamped)
                if (!buffer.hasRemaining()) {
                    fos.write(buffer.array())
                    buffer.clear()
                }
            }

            if (buffer.position() > 0) {
                fos.write(buffer.array(), 0, buffer.position())
            }
        }

        // Fill in real WAV header sizes
        RandomAccessFile(file, "rw").use { raf ->
            val totalAudioLen = dataSize.toLong()
            val totalDataLen = totalAudioLen + 36
            val byteRate = (sampleRate * 2).toLong()

            val header = ByteArray(44)
            header[0] = 'R'.code.toByte()
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()
            header[4] = (totalDataLen and 0xff).toByte()
            header[5] = ((totalDataLen shr 8) and 0xff).toByte()
            header[6] = ((totalDataLen shr 16) and 0xff).toByte()
            header[7] = ((totalDataLen shr 24) and 0xff).toByte()
            header[8] = 'W'.code.toByte()
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()
            header[12] = 'f'.code.toByte()
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()
            header[16] = 16 // 16 for PCM
            header[17] = 0
            header[18] = 0
            header[19] = 0
            header[20] = 1 // PCM format
            header[21] = 0
            header[22] = 1 // Mono channel
            header[23] = 0
            header[24] = (sampleRate and 0xff).toByte()
            header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte()
            header[27] = ((sampleRate shr 24) and 0xff).toByte()
            header[28] = (byteRate and 0xff).toByte()
            header[29] = ((byteRate shr 8) and 0xff).toByte()
            header[30] = ((byteRate shr 16) and 0xff).toByte()
            header[31] = ((byteRate shr 24) and 0xff).toByte()
            header[32] = 2 // Block align (1 channel * 2 bytes)
            header[33] = 0
            header[34] = 16 // Bits per sample
            header[35] = 0
            header[36] = 'd'.code.toByte()
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()
            header[40] = (totalAudioLen and 0xff).toByte()
            header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
            header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
            header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

            raf.seek(0)
            raf.write(header)
        }
    }
}
