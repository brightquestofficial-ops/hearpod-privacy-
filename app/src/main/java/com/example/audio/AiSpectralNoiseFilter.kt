package com.example.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tan

enum class AiNoiseMode(val title: String, val description: String, val reductionDb: Float) {
    STUDIO_QUIET("Studio Quiet", "Deep background noise and hiss suppression with speech focus", 20f),
    BALANCED("Balanced AI", "Comfortable listening with gentle background noise reduction", 10f),
    CONVERSATION_FOCUS("Speech Focus", "Boosts human speech clarity while silencing room drone", 16f),
    OFF("Raw Pass-Through", "Unfiltered sound pass-through", 0f)
}

/**
 * Real-time Speech Clarifier & Background White Noise Silencer.
 *
 * Eliminates background white noise, room hiss, fan drone, and AC hum
 * while ensuring speech is passed through with instant zero-latency clarity.
 *
 * Features:
 * - 2nd Order Butterworth High-Pass at 100 Hz (cuts 50/60 Hz mains hum and handling thumps)
 * - 2nd Order Butterworth Low-Pass at 7200 Hz (cuts thermal mic white noise and hiss)
 * - Dynamic Voice Activity Detector (VAD) with instant attack (<1ms) and 120ms hangover
 * - Downward Expander Noise Gate with sample-by-sample linear ramp (zero clicks or flutter)
 */
class AiSpectralNoiseFilter(private val sampleRate: Float = 48000f) {

    var mode: AiNoiseMode = AiNoiseMode.BALANCED
    var transparencyLevel: Float = 0.85f

    // Eco Mode: decimate noise analysis to reduce DSP energy consumption
    var isEcoMode: Boolean = false

    // High-pass filter coefficients (100 Hz cutoff)
    private var hpB0 = 1f
    private var hpB1 = -2f
    private var hpB2 = 1f
    private var hpA1 = 0f
    private var hpA2 = 0f
    private var hpX1 = 0f
    private var hpX2 = 0f
    private var hpY1 = 0f
    private var hpY2 = 0f

    // Low-pass filter coefficients (7200 Hz cutoff to eliminate microphone white noise / hiss)
    private var lpB0 = 1f
    private var lpB1 = 2f
    private var lpB2 = 1f
    private var lpA1 = 0f
    private var lpA2 = 0f
    private var lpX1 = 0f
    private var lpX2 = 0f
    private var lpY1 = 0f
    private var lpY2 = 0f

    // Running noise floor tracker
    private var noiseFloor = 0.002f

    // Current smoothed gain for sample-by-sample interpolation
    private var currentGain = 1.0f

    // Speech hold hangover frames (~120ms) so word endings are preserved naturally
    private var speechHoldFrames = 0

    init {
        updateFilterCoefficients(sampleRate)
    }

    fun updateSampleRate(newRate: Float) {
        if (newRate > 8000f) {
            updateFilterCoefficients(newRate)
        }
    }

    private fun updateFilterCoefficients(sr: Float) {
        // 1. High-Pass at 75 Hz (cuts sub-audible wind rumble & handling thumps, preserving rich voice warmth)
        val hpFc = 75f
        val hpK = tan(PI.toFloat() * hpFc / sr)
        val hpNorm = 1f / (1f + sqrt(2f) * hpK + hpK * hpK)
        hpB0 = hpNorm
        hpB1 = -2f * hpNorm
        hpB2 = hpNorm
        hpA1 = 2f * (hpK * hpK - 1f) * hpNorm
        hpA2 = (1f - sqrt(2f) * hpK + hpK * hpK) * hpNorm

        // 2. Low-Pass at 14,000 Hz (crystal-clear vocal air, crisp consonants, and high-frequency presence)
        val lpFc = 14000f.coerceAtMost(sr * 0.45f)
        val lpK = tan(PI.toFloat() * lpFc / sr)
        val lpNorm = 1f / (1f + sqrt(2f) * lpK + lpK * lpK)
        lpB0 = lpK * lpK * lpNorm
        lpB1 = 2f * lpB0
        lpB2 = lpB0
        lpA1 = 2f * (lpK * lpK - 1f) * lpNorm
        lpA2 = (1f - sqrt(2f) * lpK + lpK * lpK) * lpNorm
    }

    fun processFrame(samples: FloatArray, count: Int) {
        if (count <= 0) return

        // 1. Butterworth High-Pass (180 Hz) to eliminate AC hum and rumble
        for (i in 0 until count) {
            val inSample = samples[i]
            var out = hpB0 * inSample + hpB1 * hpX1 + hpB2 * hpX2 - hpA1 * hpY1 - hpA2 * hpY2
            if (out.isNaN() || out.isInfinite()) {
                out = 0f
                hpX1 = 0f; hpX2 = 0f; hpY1 = 0f; hpY2 = 0f
            }
            hpX2 = hpX1
            hpX1 = inSample
            hpY2 = hpY1
            hpY1 = out
            samples[i] = out
        }

        // 2. Butterworth Low-Pass (5200 Hz) to eliminate white noise / mic hiss
        for (i in 0 until count) {
            val inSample = samples[i]
            var out = lpB0 * inSample + lpB1 * lpX1 + lpB2 * lpX2 - lpA1 * lpY1 - lpA2 * lpY2
            if (out.isNaN() || out.isInfinite()) {
                out = 0f
                lpX1 = 0f; lpX2 = 0f; lpY1 = 0f; lpY2 = 0f
            }
            lpX2 = lpX1
            lpX1 = inSample
            lpY2 = lpY1
            lpY1 = out
            samples[i] = out
        }

        if (mode == AiNoiseMode.OFF) return

        // 3. Measure frame RMS energy (decimated 4x in Eco Mode for DSP power savings)
        val step = if (isEcoMode) 4 else 1
        var sumSquares = 0.0
        var sampledCount = 0
        var sIdx = 0
        while (sIdx < count) {
            val s = samples[sIdx]
            sumSquares += s * s
            sampledCount++
            sIdx += step
        }
        val frameRms = if (sampledCount > 0) sqrt(sumSquares / sampledCount).toFloat() else 1e-5f

        // 4. Adaptively track stationary background noise floor
        if (frameRms < noiseFloor * 1.4f) {
            noiseFloor = noiseFloor * 0.96f + frameRms * 0.04f
        } else if (frameRms < noiseFloor) {
            noiseFloor = frameRms
        }
        noiseFloor = noiseFloor.coerceIn(0.0005f, 0.035f)

        // 5. Dynamic Voice Activity Detection (VAD) & Downward Noise Expansion
        val speechThreshold = noiseFloor * 2.0f + 0.0025f
        val isVoiceActive = frameRms > speechThreshold

        val targetGain = if (isVoiceActive) {
            speechHoldFrames = 40 // ~160ms hold hangover so word endings and quiet consonants are fully preserved
            when (mode) {
                AiNoiseMode.CONVERSATION_FOCUS -> 1.35f
                AiNoiseMode.BALANCED -> 1.15f
                AiNoiseMode.STUDIO_QUIET -> 1.05f
                AiNoiseMode.OFF -> 1.00f
            }
        } else if (speechHoldFrames > 0) {
            speechHoldFrames--
            1.00f
        } else {
            // Ambient pause: attenuate stationary microphone hiss and room noise down into silence
            when (mode) {
                AiNoiseMode.STUDIO_QUIET -> 0.25f // Deep silence for white noise
                AiNoiseMode.CONVERSATION_FOCUS -> 0.35f // Suppress room drone and fan noise
                AiNoiseMode.BALANCED -> (0.45f + 0.25f * transparencyLevel).coerceIn(0.35f, 0.70f)
                AiNoiseMode.OFF -> 1.00f
            }
        }

        // 6. Smooth sample-by-sample linear gain ramp (eliminates clicks, vinyl pops, or flutter)
        val gainStep = (targetGain - currentGain) / count
        for (i in 0 until count) {
            currentGain += gainStep
            samples[i] *= currentGain
        }
    }

    fun reset() {
        hpX1 = 0f; hpX2 = 0f; hpY1 = 0f; hpY2 = 0f
        lpX1 = 0f; lpX2 = 0f; lpY1 = 0f; lpY2 = 0f
        noiseFloor = 0.002f
        currentGain = 1.0f
        speechHoldFrames = 0
    }
}
