package com.example

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.datastore.SettingsStore
import com.example.data.local.AppDatabase
import com.example.data.local.entity.Book
import com.example.data.local.entity.Chapter
import com.example.data.local.repository.BookRepository
import com.example.engine.TtsConverterEngine
import com.example.engine.TtsEngine
import com.example.playback.AudioEffectsManager
import com.example.playback.LuxAudioProcessor
import com.example.playback.PlaybackConnection
import com.example.util.LuxPackageManager
import com.example.util.NotificationChannels
import com.example.util.OpusAudioEncoder
import com.example.util.TextFileExtractor
import com.example.util.TimeUtils
import com.example.util.WavPcmData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Play LuX", appName)
    }

    @Test
    fun `test time formatting utility`() {
        assertEquals("00:00", TimeUtils.formatMs(0L))
        assertEquals("01:30", TimeUtils.formatMs(90000L))
        assertEquals("01:00:00", TimeUtils.formatMs(3600000L))
        assertEquals("1 h", TimeUtils.formatDurationHuman(3600000L))
        assertEquals("1 h 30 min", TimeUtils.formatDurationHuman(5400000L))
    }

    @Test
    fun `verify 5 module titles exist in resources`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Bóveda", context.getString(R.string.nav_vault))
        assertEquals("Listas", context.getString(R.string.nav_playlists))
        assertEquals("Buscar", context.getString(R.string.nav_search))
        assertEquals("Traductor", context.getString(R.string.nav_translator))
        assertEquals("Convertidor", context.getString(R.string.nav_converter))
    }

    @Test
    fun `verify text extractor format detection audio rejection and html cleaning`() {
        assertEquals("EPUB", TextFileExtractor.detectFormat("mi_libro.epub", null))
        assertEquals("PDF", TextFileExtractor.detectFormat("doc.pdf", null))
        assertEquals("DOCX", TextFileExtractor.detectFormat("texto.docx", null))
        assertEquals("MD", TextFileExtractor.detectFormat("notas.md", null))

        // Audio files must be rejected cleanly by Play LuX text-only importer
        assertTrue(TextFileExtractor.isAudioFile("audiolibro.mp3", "audio/mpeg"))
        assertTrue(TextFileExtractor.isAudioFile("pista.ogg", null))
        assertFalse(TextFileExtractor.isAudioFile("libro.epub", null))

        val cleaned = TextFileExtractor.cleanHtmlText("<html><body><h1>Capítulo 1</h1><p>Hola &amp; bienvenidos.</p></body></html>")
        assertTrue(cleaned.contains("Capítulo 1"))
        assertTrue(cleaned.contains("Hola & bienvenidos."))
    }

    @Test
    fun `verify chapter splitting detects headings`() {
        val sample = """
            Capítulo 1: El comienzo
            Había una vez un viajero en un largo camino.
            
            Capítulo 2: El regreso
            Al final volvió a casa con grandes historias.
        """.trimIndent()

        val chapters = TextFileExtractor.splitTextIntoChapters(sample)
        assertEquals(2, chapters.size)
        assertEquals("Capítulo 1: El comienzo", chapters[0].title)
        assertEquals("Capítulo 2: El regreso", chapters[1].title)
    }

    @Test
    fun `verify loquendo server audio format detection and url builder`() {
        val wavHeader = "RIFF\u0000\u0000\u0000\u0000WAVE".toByteArray(Charsets.US_ASCII)
        val mp3Header = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 0x04)
        val oggHeader = "OggS\u0000".toByteArray(Charsets.US_ASCII)

        assertEquals("wav", TtsConverterEngine.detectAudioExtension(wavHeader, null))
        assertEquals("mp3", TtsConverterEngine.detectAudioExtension(mp3Header, null))
        assertEquals("ogg", TtsConverterEngine.detectAudioExtension(oggHeader, null))

        val getUrl = TtsConverterEngine.buildLoquendoGetUrl(
            baseUrl = "http://192.168.1.50:5000/tts",
            text = "Hola mundo",
            voice = "Jorge",
            languageTag = "es-ES",
            speed = 1.0f,
            pitch = 1.0f
        )
        assertTrue(getUrl.contains("text=Hola+mundo"))
        assertTrue(getUrl.contains("voice=Jorge"))
        assertTrue(getUrl.contains("lang=es-ES"))
    }

    @Test
    fun `verify tts segmentation splits below limit and pcm resampling scales cleanly`() {
        val longParagraph = List(80) { "Oración número $it con palabras suficientes para probar fragmentación." }.joinToString(" ")
        val maxLen = 600
        val chunks = TtsEngine.splitTextIntoSegments(longParagraph, maxLen)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= maxLen && it.isNotBlank() })

        // Resample 24000 Hz 1ch -> 48000 Hz 1ch (approx 2x length)
        val srcSamples = 240
        val srcBytes = ByteArray(srcSamples * 2)
        val resampled = OpusAudioEncoder.resamplePcm16(srcBytes, 24000, 48000, 1)
        assertEquals(srcSamples * 2 * 2, resampled.size)
    }

    @Test
    fun `verify 4 silent notification channels and eq presets exist`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NotificationChannels.createAll(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        assertNotNull(manager.getNotificationChannel(NotificationChannels.CHANNEL_PLAYBACK))
        assertNotNull(manager.getNotificationChannel(NotificationChannels.CHANNEL_DOWNLOADS))
        assertNotNull(manager.getNotificationChannel(NotificationChannels.CHANNEL_TRANSLATION))
        assertNotNull(manager.getNotificationChannel(NotificationChannels.CHANNEL_CONVERSION))

        assertTrue(SettingsStore.EQ_PRESETS.size >= 5)
        assertTrue(SettingsStore.EQ_PRESETS.all { it.bands.size == 5 })
    }

    @Test
    fun `verify chapter pcm normalization targets -16 lufs without clipping above -1 dbfs`() {
        val sampleRate = 22050
        val frames = sampleRate // 1 second test tone
        val quietBuf = ByteBuffer.allocate(frames * 2).order(ByteOrder.LITTLE_ENDIAN)
        val loudBuf = ByteBuffer.allocate(frames * 2).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until frames) {
            val wave = sin(2.0 * Math.PI * 440.0 * i / sampleRate)
            // Very quiet speech-like amplitude (-32 dBFS)
            quietBuf.putShort((wave * 0.025 * 32767.0).toInt().toShort())
            // Very hot amplitude near 0 dBFS
            loudBuf.putShort((wave * 0.99 * 32767.0).toInt().toShort())
        }

        val quietNorm = OpusAudioEncoder.normalizePcm16(WavPcmData(sampleRate, 1, 16, quietBuf.array()))
        assertTrue("Quiet chapter should receive positive gain", quietNorm.stats.appliedGainDb > 5f)
        assertTrue("Final peak must never exceed -1 dBFS", quietNorm.stats.finalPeakDbfs <= -0.95f)

        val loudNorm = OpusAudioEncoder.normalizePcm16(WavPcmData(sampleRate, 1, 16, loudBuf.array()))
        assertTrue("Hot chapter should be attenuated cleanly", loudNorm.stats.appliedGainDb < 0f)
        assertTrue("Hot chapter peak must stay <= -1 dBFS", loudNorm.stats.finalPeakDbfs <= -0.95f)

        val outBuf = ByteBuffer.wrap(loudNorm.pcm.pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        var maxSample = 0
        for (i in 0 until frames) {
            val s = abs(outBuf.getShort(i * 2).toInt())
            if (s > maxSample) maxSample = s
        }
        assertTrue("No sample should exceed -1 dBFS ceiling (~29204)", maxSample <= 29300)
    }

    @Test
    fun `verify loudness enhancer gain mapping and chapter resume start calculation`() {
        assertEquals(0, AudioEffectsManager.gainDbToMillibels(-2f))
        assertEquals(300, AudioEffectsManager.gainDbToMillibels(3.0f))
        assertEquals(600, AudioEffectsManager.gainDbToMillibels(8.5f))

        // Should never throw on any device or Robolectric runtime
        AudioEffectsManager.isLoudnessEnhancerSupported()

        // Resumption skips completed chapters and starts at first missing one
        assertEquals(0, TtsConverterEngine.findResumeStartChapter(emptySet(), 5))
        assertEquals(3, TtsConverterEngine.findResumeStartChapter(setOf(0, 1, 2), 5))
        assertEquals(5, TtsConverterEngine.findResumeStartChapter(setOf(0, 1, 2, 3, 4), 5))
    }

    @Test
    fun `verify lux audio processor mono mixdown stereo pan balance and 24-bar waveform`() {
        // Mono downmix averages L + R equally
        val stereoMono = shortArrayOf(10000, 2000, -4000, 4000)
        LuxAudioProcessor.processPcm16InPlace(stereoMono, channels = 2, mono = true, balancePan = 0f)
        assertEquals(6000.toShort(), stereoMono[0])
        assertEquals(6000.toShort(), stereoMono[1])
        assertEquals(0.toShort(), stereoMono[2])
        assertEquals(0.toShort(), stereoMono[3])

        // Full right pan (+1f) silences Left channel while preserving Right
        val panRight = shortArrayOf(12000, 8000)
        LuxAudioProcessor.processPcm16InPlace(panRight, channels = 2, mono = false, balancePan = 1f)
        assertEquals(0.toShort(), panRight[0])
        assertEquals(8000.toShort(), panRight[1])

        // Full left pan (-1f) silences Right channel while preserving Left
        val panLeft = shortArrayOf(12000, 8000)
        LuxAudioProcessor.processPcm16InPlace(panLeft, channels = 2, mono = false, balancePan = -1f)
        assertEquals(12000.toShort(), panLeft[0])
        assertEquals(0.toShort(), panLeft[1])

        // Waveform computes exactly 24 bars bounded cleanly in [0.08f, 1.0f]
        val samples = ShortArray(480) { i -> (sin(i * 0.25) * 16000).toInt().toShort() }
        val bars = LuxAudioProcessor.computeWaveformBars(samples, 24)
        assertEquals(24, bars.size)
        assertTrue(bars.all { it in 0.08f..1.0f })
    }

    @Test
    fun `verify a-b loop jump target calculation`() {
        assertNull(PlaybackConnection.computeAbLoopJumpTarget(5000L, null, null))
        assertNull(PlaybackConnection.computeAbLoopJumpTarget(5000L, 2000L, null))
        assertNull(PlaybackConnection.computeAbLoopJumpTarget(3500L, 2000L, 6000L))
        assertEquals(2000L, PlaybackConnection.computeAbLoopJumpTarget(6000L, 2000L, 6000L))
        assertEquals(2000L, PlaybackConnection.computeAbLoopJumpTarget(6450L, 2000L, 6000L))
    }

    @Test
    fun `verify lux package export import round-trip and listening stats persistence`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = BookRepository(
                bookDao = db.bookDao(),
                chapterDao = db.chapterDao(),
                partDao = db.partDao(),
                bookmarkDao = db.bookmarkDao(),
                listeningStatsDao = db.listeningStatsDao()
            )

            val srcBookId = "src_book_1"
            val srcBookDir = File(context.filesDir, "books/$srcBookId").apply { mkdirs() }
            val chFile = File(srcBookDir, "ch_0.ogg").apply { writeBytes(byteArrayOf(0x4F, 0x67, 0x67, 0x53)) }
            File(srcBookDir, "chapters_text.json").writeText("[\"Párrafo uno de prueba\"]")

            val srcBook = Book(
                id = srcBookId,
                title = "El Principito Prueba",
                author = "Autor Demo",
                lang = "es",
                coverPath = null,
                durationMs = 15000L,
                sourceTextId = "txt_1"
            )
            val srcChapter = Chapter(
                id = 1L,
                bookId = srcBookId,
                partId = null,
                index = 0,
                title = "Capítulo 1",
                filePath = chFile.absolutePath,
                durationMs = 15000L
            )

            val luxOut = File(context.cacheDir, "roundtrip_test.lux")
            LuxPackageManager.exportBookToFile(context, srcBook, listOf(srcChapter), luxOut)
            assertTrue("Exported .lux archive must exist and be non-empty", luxOut.exists() && luxOut.length() > 0L)

            val imported = LuxPackageManager.importLuxPackageFile(context, luxOut, folderId = null, bookRepository = repo)
            assertEquals("El Principito Prueba", imported.title)
            assertEquals("Autor Demo", imported.author)

            val importedChapters = repo.getChaptersSync(imported.id)
            assertEquals(1, importedChapters.size)
            assertTrue("Extracted audio file must exist", File(importedChapters.first().filePath).exists())
            assertTrue("Synchronized chapter text json should be preserved", File(context.filesDir, "books/${imported.id}/chapters_text.json").exists())

            // Listening stats recording & book play recency
            repo.markBookPlayed(imported.id)
            repo.recordListeningSeconds(125L, isNewSession = true)
            repo.recordListeningSeconds(45L, isNewSession = false)

            val totalSecs = repo.totalListenedSeconds?.first() ?: 0L
            assertEquals(170L, totalSecs)
            val recentBooks = repo.recentlyPlayedBooks.first()
            assertTrue(recentBooks.any { it.id == imported.id && it.lastPlayedAt > 0L })
        } finally {
            db.close()
        }
    }
}

