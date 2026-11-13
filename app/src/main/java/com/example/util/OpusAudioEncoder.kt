package com.example.util

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan

data class EncodedAudioResult(
    val file: File,
    val durationMs: Long,
    val formatTag: String, // "opus", "aac", or "wav"
    val appliedGainDb: Float = 0f,
    val measuredLufs: Float = -16f,
    val peakDbfs: Float = -1f
)

data class NormalizationStats(
    val measuredLufs: Float,
    val peakDbfs: Float,
    val appliedGainDb: Float,
    val finalPeakDbfs: Float
)

data class NormalizedPcmResult(
    val pcm: WavPcmData,
    val stats: NormalizationStats
)

data class WavPcmData(
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val pcmBytes: ByteArray
)

object OpusAudioEncoder {

    const val TARGET_LUFS = -16.0f
    const val MAX_PEAK_DBFS = -1.0f
    private const val ABSOLUTE_GATE_LUFS = -70.0
    private const val MAX_BOOST_DB = 18.0
    private const val MAX_CUT_DB = -24.0

    private const val OPUS_SAMPLE_RATE = 48000
    private const val OPUS_BITRATE = 32_000 // ~14 MB/hour high clarity voice
    private const val AAC_BITRATE = 48_000
    private const val CODEC_TIMEOUT_US = 10_000L

    /**
     * Merges PCM WAV chunk files with optional pause, normalizes loudness (~ -16 LUFS,
     * max peak -1 dBFS without clipping), encodes directly into Opus/OGG (.ogg, API 29+),
     * falling back to AAC/M4A (.m4a) if Opus/OGG is unavailable on hardware.
     * Deletes ALL temporary WAV files on success.
     */
    fun encodeWavChunksToOpusOrFallback(
        wavChunks: List<File>,
        tempDir: File,
        outputDir: File,
        baseFileName: String,
        paragraphPauseMs: Int,
        normalizeChapter: Boolean = true
    ): EncodedAudioResult {
        if (!outputDir.exists()) outputDir.mkdirs()

        val mergedPcm = mergeWavChunksToPcm(wavChunks, paragraphPauseMs)
        if (mergedPcm == null || mergedPcm.pcmBytes.isEmpty()) {
            // Emergency 1s silence if TTS produced no chunks
            val fallbackWav = File(tempDir, "${baseFileName}_silence.wav")
            writeSilentWav(fallbackWav, 1000L)
            val oggFile = File(outputDir, "$baseFileName.ogg")
            val m4aFile = File(outputDir, "$baseFileName.m4a")
            val pcm = extractWavPcm(fallbackWav)!!
            fallbackWav.delete()
            return tryEncodeAll(pcm, oggFile, m4aFile, File(outputDir, "$baseFileName.wav"), NormalizationStats(-70f, -96f, 0f, -96f))
        }

        val normalized = if (normalizeChapter) {
            normalizePcm16(mergedPcm)
        } else {
            NormalizedPcmResult(
                pcm = mergedPcm,
                stats = NormalizationStats(TARGET_LUFS, MAX_PEAK_DBFS, 0f, MAX_PEAK_DBFS)
            )
        }

        val oggFile = File(outputDir, "$baseFileName.ogg")
        val m4aFile = File(outputDir, "$baseFileName.m4a")
        val emergencyWav = File(outputDir, "$baseFileName.wav")

        val result = tryEncodeAll(normalized.pcm, oggFile, m4aFile, emergencyWav, normalized.stats)

        // Ensure no temporary WAV chunk files remain
        wavChunks.forEach { runCatching { it.delete() } }
        return result
    }

    /**
     * Normalizes 16-bit PCM to target loudness (~ -16 LUFS via gated BS.1770 K-weighted blocks)
     * while enforcing a strict -1 dBFS ceiling so speech peaks never clip.
     */
    fun normalizePcm16(
        pcm: WavPcmData,
        targetLufs: Float = TARGET_LUFS,
        maxPeakDbfs: Float = MAX_PEAK_DBFS
    ): NormalizedPcmResult {
        val channels = pcm.channels.coerceIn(1, 2)
        val totalFrames = pcm.pcmBytes.size / (2 * channels)
        if (totalFrames <= 0) {
            return NormalizedPcmResult(pcm, NormalizationStats(-70f, -96f, 0f, -96f))
        }

        val fs = pcm.sampleRate.coerceAtLeast(8000).toDouble()
        val inBuf = ByteBuffer.wrap(pcm.pcmBytes).order(ByteOrder.LITTLE_ENDIAN)

        // Stage 1 K-weighting high-shelf coefficients (+4 dB above ~1682 Hz)
        val f0Shelf = 1681.974450955533
        val gShelf = 3.999843853973347
        val qShelf = 0.7071752369554196
        val k1 = tan(Math.PI * f0Shelf / fs)
        val vh = 10.0.pow(gShelf / 20.0)
        val vb = vh.pow(0.4996667741545416)
        val a0S = 1.0 + k1 / qShelf + k1 * k1
        val s1b0 = (vh + vb * k1 / qShelf + k1 * k1) / a0S
        val s1b1 = 2.0 * (k1 * k1 - vh) / a0S
        val s1b2 = (vh - vb * k1 / qShelf + k1 * k1) / a0S
        val s1a1 = 2.0 * (k1 * k1 - 1.0) / a0S
        val s1a2 = (1.0 - k1 / qShelf + k1 * k1) / a0S

        // Stage 2 RLB high-pass (~38 Hz)
        val f0Hp = 38.13547087602444
        val qHp = 0.5003270373238773
        val k2 = tan(Math.PI * f0Hp / fs)
        val a0Hp = 1.0 + k2 / qHp + k2 * k2
        val s2b0 = 1.0 / a0Hp
        val s2b1 = -2.0 / a0Hp
        val s2b2 = 1.0 / a0Hp
        val s2a1 = 2.0 * (k2 * k2 - 1.0) / a0Hp
        val s2a2 = (1.0 - k2 / qHp + k2 * k2) / a0Hp

        val z1A = DoubleArray(channels)
        val z1B = DoubleArray(channels)
        val z2A = DoubleArray(channels)
        val z2B = DoubleArray(channels)

        val blockFrames = (fs * 0.1).roundToInt().coerceAtLeast(1)
        val blockEnergies = ArrayList<Double>((totalFrames / blockFrames) + 1)
        var blockSumSq = 0.0
        var blockCount = 0
        var rawPeakLinear = 0.0

        for (frame in 0 until totalFrames) {
            var frameSumSq = 0.0
            for (ch in 0 until channels) {
                val sample = inBuf.getShort((frame * channels + ch) * 2).toDouble() / 32768.0
                val absSample = abs(sample)
                if (absSample > rawPeakLinear) rawPeakLinear = absSample

                // Direct Form II Transposed biquad stage 1
                val y1 = s1b0 * sample + z1A[ch]
                z1A[ch] = s1b1 * sample - s1a1 * y1 + z1B[ch]
                z1B[ch] = s1b2 * sample - s1a2 * y1

                // Direct Form II Transposed biquad stage 2
                val y2 = s2b0 * y1 + z2A[ch]
                z2A[ch] = s2b1 * y1 - s2a1 * y2 + z2B[ch]
                z2B[ch] = s2b2 * y1 - s2a2 * y2

                frameSumSq += y2 * y2
            }
            blockSumSq += (frameSumSq / channels)
            blockCount++
            if (blockCount >= blockFrames) {
                blockEnergies.add(blockSumSq / blockCount)
                blockSumSq = 0.0
                blockCount = 0
            }
        }
        if (blockCount > 0) {
            blockEnergies.add(blockSumSq / blockCount)
        }

        if (rawPeakLinear <= 1e-6) {
            return NormalizedPcmResult(pcm, NormalizationStats(-70f, -96f, 0f, -96f))
        }

        // Absolute gate (-70 LUFS) + Relative gate (-10 LU) so TTS pauses don't bias loudness
        val absGated = blockEnergies.filter { e ->
            e > 0.0 && (-0.691 + 10.0 * log10(e)) > ABSOLUTE_GATE_LUFS
        }
        if (absGated.isEmpty()) {
            val peakDb = (20.0 * log10(rawPeakLinear)).toFloat()
            return NormalizedPcmResult(pcm, NormalizationStats(-70f, peakDb, 0f, peakDb))
        }
        val ungatedMean = absGated.average()
        val ungatedLufs = -0.691 + 10.0 * log10(ungatedMean)
        val relGated = absGated.filter { e ->
            (-0.691 + 10.0 * log10(e)) >= (ungatedLufs - 10.0)
        }
        val gatedMean = if (relGated.isNotEmpty()) relGated.average() else ungatedMean
        val measuredLufs = (-0.691 + 10.0 * log10(gatedMean)).toFloat()
        val rawPeakDbfs = (20.0 * log10(rawPeakLinear)).toFloat()

        val desiredGainDb = (targetLufs - measuredLufs).toDouble().coerceIn(MAX_CUT_DB, MAX_BOOST_DB)
        val maxAllowedGainDb = maxPeakDbfs.toDouble() - rawPeakDbfs.toDouble()
        val appliedGainDb = minOf(desiredGainDb, maxAllowedGainDb).coerceIn(MAX_CUT_DB, MAX_BOOST_DB)

        val gainLinear = 10.0.pow(appliedGainDb / 20.0)
        val ceilingLinear = 10.0.pow(maxPeakDbfs.toDouble() / 20.0)

        val outBytes = ByteArray(pcm.pcmBytes.size)
        val outBuf = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN)
        var finalPeakLinear = 0.0

        val totalSamples = totalFrames * channels
        for (i in 0 until totalSamples) {
            val s = inBuf.getShort(i * 2).toDouble() / 32768.0
            val amplified = (s * gainLinear).coerceIn(-ceilingLinear, ceilingLinear)
            val absAmp = abs(amplified)
            if (absAmp > finalPeakLinear) finalPeakLinear = absAmp
            val outShort = (amplified * 32767.0).roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            outBuf.putShort(outShort)
        }

        val finalPeakDbfs = if (finalPeakLinear > 1e-9) (20.0 * log10(finalPeakLinear)).toFloat() else -96f
        return NormalizedPcmResult(
            pcm = pcm.copy(pcmBytes = outBytes),
            stats = NormalizationStats(
                measuredLufs = measuredLufs,
                peakDbfs = rawPeakDbfs,
                appliedGainDb = appliedGainDb.toFloat(),
                finalPeakDbfs = finalPeakDbfs
            )
        )
    }

    private fun tryEncodeAll(
        pcm: WavPcmData,
        oggFile: File,
        m4aFile: File,
        emergencyWav: File,
        stats: NormalizationStats
    ): EncodedAudioResult {
        val durationMs = computeDurationMs(pcm)

        // 1. Primary: Opus in OGG container (API 29+, minSdk = 29)
        val opusSuccess = runCatching {
            encodeWithMediaCodec(
                pcm = pcm,
                outputFile = oggFile,
                mimeType = MediaFormat.MIMETYPE_AUDIO_OPUS,
                muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG,
                targetSampleRate = OPUS_SAMPLE_RATE,
                bitrate = OPUS_BITRATE
            )
        }.getOrDefault(false)

        if (opusSuccess && oggFile.exists() && oggFile.length() > 128) {
            runCatching { m4aFile.delete() }
            runCatching { emergencyWav.delete() }
            val realDur = probeDurationMs(oggFile).takeIf { it > 0 } ?: durationMs
            return EncodedAudioResult(
                file = oggFile,
                durationMs = realDur,
                formatTag = "opus",
                appliedGainDb = stats.appliedGainDb,
                measuredLufs = stats.measuredLufs,
                peakDbfs = stats.finalPeakDbfs
            )
        } else {
            runCatching { oggFile.delete() }
        }

        // 2. Fallback: AAC in MPEG-4 (.m4a) container
        val aacSuccess = runCatching {
            encodeWithMediaCodec(
                pcm = pcm,
                outputFile = m4aFile,
                mimeType = MediaFormat.MIMETYPE_AUDIO_AAC,
                muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
                targetSampleRate = 44100,
                bitrate = AAC_BITRATE
            )
        }.getOrDefault(false)

        if (aacSuccess && m4aFile.exists() && m4aFile.length() > 128) {
            runCatching { emergencyWav.delete() }
            val realDur = probeDurationMs(m4aFile).takeIf { it > 0 } ?: durationMs
            return EncodedAudioResult(
                file = m4aFile,
                durationMs = realDur,
                formatTag = "aac",
                appliedGainDb = stats.appliedGainDb,
                measuredLufs = stats.measuredLufs,
                peakDbfs = stats.finalPeakDbfs
            )
        } else {
            runCatching { m4aFile.delete() }
        }

        // 3. Last-resort fallback (e.g. unit test sandbox without MediaCodec)
        writePcmAsWav(pcm, emergencyWav)
        return EncodedAudioResult(
            file = emergencyWav,
            durationMs = durationMs,
            formatTag = "wav",
            appliedGainDb = stats.appliedGainDb,
            measuredLufs = stats.measuredLufs,
            peakDbfs = stats.finalPeakDbfs
        )
    }

    private fun encodeWithMediaCodec(
        pcm: WavPcmData,
        outputFile: File,
        mimeType: String,
        muxerFormat: Int,
        targetSampleRate: Int,
        bitrate: Int
    ): Boolean {
        val channels = pcm.channels.coerceIn(1, 2)
        val resampledPcm = if (pcm.sampleRate != targetSampleRate) {
            resamplePcm16(pcm.pcmBytes, pcm.sampleRate, targetSampleRate, channels)
        } else {
            pcm.pcmBytes
        }

        val format = MediaFormat.createAudioFormat(mimeType, targetSampleRate, channels).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            if (mimeType == MediaFormat.MIMETYPE_AUDIO_AAC) {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            }
        }

        val codec = MediaCodec.createEncoderByType(mimeType)
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var trackIndex = -1

        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            muxer = MediaMuxer(outputFile.absolutePath, muxerFormat)

            val bufferInfo = MediaCodec.BufferInfo()
            val bytesPerSecond = targetSampleRate.toLong() * channels * 2L
            var inputOffset = 0
            var totalBytesFed = 0L
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inIdx >= 0) {
                        val inBuffer = codec.getInputBuffer(inIdx)
                        if (inBuffer != null) {
                            inBuffer.clear()
                            val remaining = resampledPcm.size - inputOffset
                            if (remaining <= 0) {
                                val ptsUs = (totalBytesFed * 1_000_000L) / bytesPerSecond
                                codec.queueInputBuffer(
                                    inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                val chunkSize = minOf(inBuffer.capacity(), remaining)
                                inBuffer.put(resampledPcm, inputOffset, chunkSize)
                                val ptsUs = (totalBytesFed * 1_000_000L) / bytesPerSecond
                                codec.queueInputBuffer(inIdx, 0, chunkSize, ptsUs, 0)
                                inputOffset += chunkSize
                                totalBytesFed += chunkSize
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            trackIndex = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    outIdx >= 0 -> {
                        val outBuffer = codec.getOutputBuffer(outIdx)
                        if (outBuffer != null && bufferInfo.size > 0 &&
                            (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                        ) {
                            if (muxerStarted && trackIndex >= 0) {
                                outBuffer.position(bufferInfo.offset)
                                outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(trackIndex, outBuffer, bufferInfo)
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }
            return outputFile.exists() && outputFile.length() > 0
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            if (muxer != null) {
                if (muxerStarted) runCatching { muxer.stop() }
                runCatching { muxer.release() }
            }
        }
    }

    fun mergeWavChunksToPcm(inputs: List<File>, pauseMs: Int): WavPcmData? {
        if (inputs.isEmpty()) return null
        var sampleRate = 22050
        var channels = 1
        var bitsPerSample = 16
        val pcmOut = ByteArrayOutputStream()

        inputs.forEachIndexed { idx, file ->
            val wavData = extractWavPcm(file)
            if (wavData != null && wavData.pcmBytes.isNotEmpty()) {
                if (idx == 0) {
                    sampleRate = wavData.sampleRate
                    channels = wavData.channels
                    bitsPerSample = wavData.bitsPerSample
                }
                pcmOut.write(wavData.pcmBytes)
                if (idx < inputs.lastIndex && pauseMs > 0) {
                    val bytesPerSec = sampleRate * channels * (bitsPerSample / 8)
                    var pauseBytes = ((bytesPerSec.toLong() * pauseMs) / 1000L).toInt()
                    if (pauseBytes % 2 != 0) pauseBytes++
                    if (pauseBytes > 0) {
                        pcmOut.write(ByteArray(pauseBytes))
                    }
                }
            }
        }

        val bytes = pcmOut.toByteArray()
        return if (bytes.isNotEmpty()) WavPcmData(sampleRate, channels, bitsPerSample, bytes) else null
    }

    fun extractWavPcm(file: File): WavPcmData? {
        if (!file.exists() || file.length() < 44) return null
        val bytes = file.readBytes()
        if (bytes.size < 44) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        if (bytes[0] != 'R'.code.toByte() || bytes[8] != 'W'.code.toByte()) return null

        var pos = 12
        var sampleRate = 22050
        var channels = 1
        var bitsPerSample = 16
        val pcmOut = ByteArrayOutputStream()

        while (pos + 8 <= bytes.size) {
            val chunkId = String(bytes, pos, 4, Charsets.US_ASCII)
            val chunkSize = buf.getInt(pos + 4)
            pos += 8
            if (chunkSize < 0 || pos + chunkSize > bytes.size) break

            if (chunkId == "fmt " && chunkSize >= 16) {
                channels = buf.getShort(pos + 2).toInt().coerceIn(1, 2)
                sampleRate = buf.getInt(pos + 4).coerceAtLeast(8000)
                bitsPerSample = buf.getShort(pos + 14).toInt().coerceAtLeast(8)
            } else if (chunkId == "data") {
                pcmOut.write(bytes, pos, chunkSize)
            }
            pos += chunkSize + (chunkSize % 2)
        }

        val pcm = pcmOut.toByteArray()
        return if (pcm.isNotEmpty()) WavPcmData(sampleRate, channels, bitsPerSample, pcm) else null
    }

    fun resamplePcm16(
        pcmBytes: ByteArray,
        fromRate: Int,
        toRate: Int,
        channels: Int
    ): ByteArray {
        if (fromRate == toRate || pcmBytes.size < 2 * channels) return pcmBytes
        val bytesPerFrame = 2 * channels
        val inFrames = pcmBytes.size / bytesPerFrame
        if (inFrames < 2) return pcmBytes

        val ratio = fromRate.toDouble() / toRate.toDouble()
        val outFrames = ((inFrames.toDouble() * toRate.toDouble()) / fromRate.toDouble()).roundToInt().coerceAtLeast(1)
        val inBuf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        val outBytes = ByteArray(outFrames * bytesPerFrame)
        val outBuf = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN)

        for (outIdx in 0 until outFrames) {
            val srcPos = outIdx * ratio
            val idx0 = srcPos.toInt().coerceIn(0, inFrames - 1)
            val idx1 = (idx0 + 1).coerceIn(0, inFrames - 1)
            val frac = srcPos - idx0

            for (ch in 0 until channels) {
                val s0 = inBuf.getShort((idx0 * channels + ch) * 2).toInt()
                val s1 = inBuf.getShort((idx1 * channels + ch) * 2).toInt()
                val interp = (s0 + (s1 - s0) * frac).roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                outBuf.putShort(interp)
            }
        }
        return outBytes
    }

    fun computeDurationMs(pcm: WavPcmData): Long {
        val byteRate = pcm.sampleRate.toLong() * pcm.channels * (pcm.bitsPerSample / 8)
        return if (byteRate > 0) (pcm.pcmBytes.size.toLong() * 1000L) / byteRate else 1000L
    }

    private fun probeDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun writeSilentWav(output: File, durationMs: Long) {
        val sampleRate = 22050
        val channels = 1
        val bitsPerSample = 16
        val pcmLen = ((sampleRate * channels * 2L * durationMs) / 1000L).toInt()
        writePcmAsWav(WavPcmData(sampleRate, channels, bitsPerSample, ByteArray(pcmLen)), output)
    }

    fun writePcmAsWav(pcm: WavPcmData, output: File) {
        val totalAudioLen = pcm.pcmBytes.size.toLong()
        val totalDataLen = totalAudioLen + 36
        val byteRate = pcm.sampleRate.toLong() * pcm.channels * (pcm.bitsPerSample / 8)
        val blockAlign = (pcm.channels * (pcm.bitsPerSample / 8)).toShort()

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen.toInt())
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(pcm.channels.toShort())
            putInt(pcm.sampleRate)
            putInt(byteRate.toInt())
            putShort(blockAlign)
            putShort(pcm.bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(totalAudioLen.toInt())
        }
        output.outputStream().use { fos ->
            fos.write(header.array())
            fos.write(pcm.pcmBytes)
        }
    }
}
