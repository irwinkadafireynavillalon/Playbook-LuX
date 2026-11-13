package com.example.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Publishes real-time audio spectrum/waveform bar levels (24 bars in 0f..1f)
 * directly from ExoPlayer's decoded PCM stream with zero RECORD_AUDIO permission.
 */
object AudioVisualStream {
    private val _waveformBars = MutableStateFlow(List(24) { 0.08f })
    val waveformBars: StateFlow<List<Float>> = _waveformBars.asStateFlow()

    fun publish(bars: List<Float>) {
        _waveformBars.value = bars
    }

    fun reset() {
        _waveformBars.value = List(24) { 0.08f }
    }
}

/**
 * ExoPlayer PCM-16 AudioProcessor handling:
 * 1) Real-time Mono downmix (L+R -> average)
 * 2) Stereo Left/Right balance pan (-1f..+1f)
 * 3) Zero-permission real-time 24-bar PCM amplitude envelope analysis
 */
@OptIn(UnstableApi::class)
class LuxAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var monoAudio: Boolean = false

    @Volatile
    var balancePan: Float = 0f

    @Volatile
    var waveformEnabled: Boolean = true

    private var lastVisualEmitMs: Long = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val mono = monoAudio
        val pan = balancePan.coerceIn(-1f, 1f)

        val shortBuf = inputBuffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
        val sampleCount = remaining / 2
        val samples = ShortArray(sampleCount)
        for (i in 0 until sampleCount) {
            samples[i] = shortBuf.getShort()
        }

        // Advance input buffer position cleanly
        inputBuffer.position(inputBuffer.limit())

        processPcm16InPlace(samples, channels, mono, pan)

        val outOrder = outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) {
            outOrder.putShort(s)
        }
        outputBuffer.flip()

        if (waveformEnabled && sampleCount >= channels) {
            val now = System.currentTimeMillis()
            if (now - lastVisualEmitMs >= 33L) {
                lastVisualEmitMs = now
                AudioVisualStream.publish(computeWaveformBars(samples, 24))
            }
        }
    }

    override fun onFlush() {
        AudioVisualStream.reset()
    }

    override fun onReset() {
        AudioVisualStream.reset()
    }

    companion object {
        fun processPcm16InPlace(
            samples: ShortArray,
            channels: Int,
            mono: Boolean,
            balancePan: Float
        ) {
            val pan = balancePan.coerceIn(-1f, 1f)
            val needsMono = mono && channels >= 2
            val needsPan = abs(pan) > 0.01f && channels >= 2
            if (!needsMono && !needsPan) return

            // Smooth linear balance attenuation (never > 1.0f to prevent clipping)
            val leftGain = if (pan > 0f) (1f - pan).coerceIn(0f, 1f) else 1f
            val rightGain = if (pan < 0f) (1f + pan).coerceIn(0f, 1f) else 1f

            var idx = 0
            while (idx + 1 < samples.size) {
                var l = samples[idx].toInt()
                var r = samples[idx + 1].toInt()

                if (needsMono) {
                    val avg = (l + r) / 2
                    l = avg
                    r = avg
                }
                if (needsPan) {
                    l = (l * leftGain).toInt().coerceIn(-32768, 32767)
                    r = (r * rightGain).toInt().coerceIn(-32768, 32767)
                }

                samples[idx] = l.toShort()
                samples[idx + 1] = r.toShort()
                idx += channels
            }
        }

        fun computeWaveformBars(samples: ShortArray, barCount: Int = 24): List<Float> {
            if (samples.isEmpty() || barCount <= 0) return List(barCount.coerceAtLeast(1)) { 0.08f }
            val step = (samples.size / barCount).coerceAtLeast(1)
            val bars = ArrayList<Float>(barCount)
            for (b in 0 until barCount) {
                val start = (b * step).coerceAtMost(samples.lastIndex)
                val end = ((b + 1) * step).coerceAtMost(samples.size)
                var sumSq = 0.0
                var count = 0
                for (i in start until end) {
                    val norm = samples[i] / 32768.0
                    sumSq += norm * norm
                    count++
                }
                val rms = if (count > 0) sqrt(sumSq / count).toFloat() else 0f
                // Speech-tuned visual curve (boosts subtle voice levels smoothly into 0.08f..1.0f)
                val scaled = (rms * 3.8f).coerceIn(0.08f, 1.0f)
                bars.add(scaled)
            }
            return bars
        }
    }
}
