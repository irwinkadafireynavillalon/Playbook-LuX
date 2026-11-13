package com.example.playback

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import com.example.data.datastore.PlayerSettings
import kotlin.math.roundToInt

/**
 * Manages hardware/OS 5-band Equalizer, LoudnessEnhancer, BassBoost, and Virtualizer
 * bound to ExoPlayer's audioSessionId.
 */
class AudioEffectsManager {
    companion object {
        fun isLoudnessEnhancerSupported(): Boolean {
            return runCatching {
                val descriptors = AudioEffect.queryEffects()
                descriptors?.any { it.type == AudioEffect.EFFECT_TYPE_LOUDNESS_ENHANCER } == true
            }.getOrDefault(false)
        }

        fun isBassBoostSupported(): Boolean {
            return runCatching {
                val descriptors = AudioEffect.queryEffects()
                descriptors?.any { it.type == AudioEffect.EFFECT_TYPE_BASS_BOOST } == true
            }.getOrDefault(false)
        }

        fun isVirtualizerSupported(): Boolean {
            return runCatching {
                val descriptors = AudioEffect.queryEffects()
                descriptors?.any { it.type == AudioEffect.EFFECT_TYPE_VIRTUALIZER } == true
            }.getOrDefault(false)
        }

        fun gainDbToMillibels(gainDb: Float): Int {
            return (gainDb.coerceIn(0f, 6f) * 100f).roundToInt().coerceIn(0, 600)
        }
    }

    private var currentSessionId: Int = 0
    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    fun attachOrUpdate(audioSessionId: Int, settings: PlayerSettings) {
        if (audioSessionId <= 0) return

        if (audioSessionId != currentSessionId) {
            release()
            currentSessionId = audioSessionId
            runCatching {
                equalizer = Equalizer(0, audioSessionId)
            }
            if (isLoudnessEnhancerSupported()) {
                runCatching {
                    loudnessEnhancer = LoudnessEnhancer(audioSessionId)
                }.onFailure {
                    loudnessEnhancer = null
                }
            }
            if (isBassBoostSupported()) {
                runCatching {
                    bassBoost = BassBoost(0, audioSessionId)
                }.onFailure {
                    bassBoost = null
                }
            }
            if (isVirtualizerSupported()) {
                runCatching {
                    virtualizer = Virtualizer(0, audioSessionId)
                }.onFailure {
                    virtualizer = null
                }
            }
        }

        applyEqualizer(settings)
        applyNormalization(settings.normalizeVolume, settings.normalizeGainDb)
        applyBassBoost(settings.bassBoostStrength)
        applyVirtualizer(settings.virtualizerStrength)
    }

    private fun applyEqualizer(settings: PlayerSettings) {
        val eq = equalizer ?: return
        runCatching {
            val isActive = settings.eqPreset != 0 || settings.eqBands.any { it != 0 }
            eq.enabled = isActive
            if (!isActive) return@runCatching

            val numBands = eq.numberOfBands.toInt().coerceAtLeast(1)
            val range = eq.bandLevelRange
            val minLevel = range.getOrNull(0)?.toInt() ?: -1500
            val maxLevel = range.getOrNull(1)?.toInt() ?: 1500

            for (bandIdx in 0 until numBands) {
                val mappedIdx = ((bandIdx.toFloat() / numBands) * settings.eqBands.size)
                    .toInt().coerceIn(0, settings.eqBands.lastIndex)
                val targetLevel = settings.eqBands[mappedIdx].coerceIn(minLevel, maxLevel).toShort()
                eq.setBandLevel(bandIdx.toShort(), targetLevel)
            }
        }
    }

    private fun applyNormalization(enabled: Boolean, gainDb: Float) {
        val enhancer = loudnessEnhancer ?: return
        runCatching {
            if (enabled) {
                enhancer.setTargetGain(gainDbToMillibels(gainDb))
            }
            enhancer.enabled = enabled
        }
    }

    private fun applyBassBoost(strength: Int) {
        val bb = bassBoost ?: return
        runCatching {
            val clamped = strength.coerceIn(0, 1000)
            if (clamped > 0 && bb.strengthSupported) {
                bb.setStrength(clamped.toShort())
                bb.enabled = true
            } else {
                bb.enabled = false
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun applyVirtualizer(strength: Int) {
        val virt = virtualizer ?: return
        runCatching {
            val clamped = strength.coerceIn(0, 1000)
            if (clamped > 0 && virt.strengthSupported) {
                virt.setStrength(clamped.toShort())
                virt.enabled = true
            } else {
                virt.enabled = false
            }
        }
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
        }
        runCatching {
            bassBoost?.enabled = false
            bassBoost?.release()
        }
        runCatching {
            virtualizer?.enabled = false
            virtualizer?.release()
        }
        equalizer = null
        loudnessEnhancer = null
        bassBoost = null
        virtualizer = null
        currentSessionId = 0
    }
}
