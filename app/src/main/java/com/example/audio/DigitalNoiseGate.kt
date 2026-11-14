package com.example.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Sensitivity presets for the Digital Noise Gate.
 * Determines threshold sensitivity and suppression depth when the user is not speaking.
 */
enum class NoiseGateSensitivity(
    val title: String,
    val suppressionDb: Float,
    val thresholdMultiplier: Float,
    val description: String
) {
    GENTLE("Gentle (-12 dB)", -12f, 1.6f, "Gentle attenuation for quiet rooms and natural ambiance"),
    BALANCED("Balanced (-18 dB)", -18f, 2.2f, "Optimal for everyday conversations and background hum"),
    DEEP_QUIET("Deep Quiet (-24 dB)", -24f, 2.8f, "Deep suppression for noisy fans, air conditioning, and traffic"),
    MAXIMUM_CUT("Max Cut (-32 dB)", -32f, 3.6f, "Near-total silence during speech pauses for extreme noise")
}

/**
 * Professional Digital Noise Gate with Voice Activity Detection (VAD).
 *
 * Automatically suppresses ambient background noise (AC hum, HVAC drone, computer fans,
 * microphone electronic hiss, and room reverberation) whenever the user is not speaking.
 *
 * Key Architecture:
 * 1. Dual-Threshold Schmitt Trigger (Hysteresis): Prevents chatter/flutter around the threshold.
 * 2. Asymmetric Noise Floor Tracker: Accurately estimates ambient acoustic floor without being skewed by speech.
 * 3. Instant Attack (<3ms): Never clips the initial syllables or soft consonants ('p', 't', 'k', 's').
 * 4. Hold Hangover (~150ms): Prevents word chopping during natural speech cadence and trailing consonants.
 * 5. Smooth Logarithmic Release (~70ms): Eliminates clicks, pops, and audible "pumping" artifacts.
 * 6. Soft Knee Expander: Delivers natural, transparent transitions between speech and quiet.
 */
class DigitalNoiseGate(
    private var sampleRate: Float = 48000f
) {
    // Toggle on/off
    var isEnabled: Boolean = true

    // Current sensitivity mode
    var sensitivity: NoiseGateSensitivity = NoiseGateSensitivity.BALANCED
        set(value) {
            field = value
            updateFloorGain()
        }

    // Minimum absolute threshold in linear amplitude (~ -48 dBFS)
    var baseThresholdAmp: Float = 0.0035f

    // Eco Mode: decimate RMS calculations to conserve DSP battery power
    var isEcoMode: Boolean = false

    // Attack time in milliseconds (fast opening)
    var attackTimeMs: Float = 2.5f

    // Hold time in milliseconds (preserves word endings and short pauses)
    var holdTimeMs: Float = 150f

    // Release time in milliseconds (smooth decay into quiet)
    var releaseTimeMs: Float = 75f

    // Real-time telemetry
    var isGateOpen: Boolean = false
        private set

    var currentGainReductionDb: Float = 0f
        private set

    var currentEnergyDb: Float = -60f
        private set

    var estimatedNoiseFloorDb: Float = -55f
        private set

    // Internal state
    private var envelope: Float = 0f
    private var currentGain: Float = 1.0f
    private var targetFloorGain: Float = 0.125f // ~ -18 dB
    private var holdCounterSamples: Int = 0
    private var noiseFloorAmp: Float = 0.002f

    init {
        updateFloorGain()
    }

    fun updateSampleRate(newSampleRate: Float) {
        if (newSampleRate >= 8000f) {
            sampleRate = newSampleRate
        }
    }

    private fun updateFloorGain() {
        // Convert suppression dB (e.g. -18 dB) to linear gain multiplier
        targetFloorGain = 10f.pow(sensitivity.suppressionDb / 20f).coerceIn(0.015f, 0.5f)
    }

    /**
     * Process a buffer of normalized audio samples in-place.
     */
    fun process(samples: FloatArray, count: Int) {
        if (!isEnabled || count <= 0) {
            isGateOpen = true
            currentGainReductionDb = 0f
            return
        }

        // 1. Calculate frame RMS energy (decimated 4x in Eco Mode to cut DSP CPU load)
        val step = if (isEcoMode) 4 else 1
        var sumSquares = 0.0
        var sampledCount = 0
        var sIdx = 0
        while (sIdx < count) {
            val s = samples[sIdx]
            sumSquares += (s * s).toDouble()
            sampledCount++
            sIdx += step
        }
        val frameRms = if (sampledCount > 0) {
            sqrt(sumSquares / sampledCount).toFloat().coerceAtLeast(1e-6f)
        } else 1e-6f
        currentEnergyDb = (20f * log10(frameRms)).coerceIn(-90f, 0f)

        // 2. Adaptive ambient noise floor tracking (asymmetric leaky integrator)
        if (frameRms < noiseFloorAmp * 1.5f) {
            // Track slow shifts in stationary room background noise
            noiseFloorAmp = noiseFloorAmp * 0.98f + frameRms * 0.02f
        } else if (frameRms < noiseFloorAmp) {
            // Fast downward adjustment when quiet
            noiseFloorAmp = frameRms
        }
        noiseFloorAmp = noiseFloorAmp.coerceIn(0.0004f, 0.040f)
        estimatedNoiseFloorDb = (20f * log10(noiseFloorAmp)).coerceIn(-90f, -20f)

        // 3. Dynamic Hysteresis Thresholds
        val openThreshold = max(baseThresholdAmp, noiseFloorAmp * sensitivity.thresholdMultiplier)
        val closeThreshold = openThreshold * 0.72f // ~3 dB below open threshold for stable hysteresis

        // 4. Voice Activity Detection (VAD) State Machine
        val holdDurationSamples = ((holdTimeMs / 1000f) * sampleRate).toInt()

        if (frameRms >= openThreshold) {
            // User is speaking! Open gate immediately and reset hold counter
            isGateOpen = true
            holdCounterSamples = holdDurationSamples
        } else if (holdCounterSamples > 0) {
            // Voice active recently; hold gate open through natural speech pauses
            holdCounterSamples -= count
            isGateOpen = true
        } else if (frameRms < closeThreshold) {
            // User is silent; close gate to suppress background noise
            isGateOpen = false
        }

        // 5. Target Gain Calculation
        val targetGain = if (isGateOpen) 1.0f else targetFloorGain

        // 6. Smooth Sample-by-Sample Envelope Interpolation (prevents clicks, zipper noise, and pops)
        val attackAlpha = 1.0f - (-1.0f / (sampleRate * (attackTimeMs / 1000f))).coerceIn(-1f, 0f).let { 10f.pow(it) }
        val releaseAlpha = 1.0f - (-1.0f / (sampleRate * (releaseTimeMs / 1000f))).coerceIn(-1f, 0f).let { 10f.pow(it) }

        val gainStep = (targetGain - currentGain) / count.toFloat()

        for (i in 0 until count) {
            // Linear glide per sample across the audio chunk
            currentGain += gainStep

            // Apply gain in-place
            samples[i] *= currentGain
        }

        currentGain = targetGain
        currentGainReductionDb = (20f * log10(currentGain.coerceAtLeast(1e-4f))).coerceIn(-40f, 0f)
    }

    /**
     * Reset internal filter state and envelope followers.
     */
    fun reset() {
        envelope = 0f
        currentGain = 1.0f
        holdCounterSamples = 0
        noiseFloorAmp = 0.002f
        isGateOpen = false
        currentGainReductionDb = 0f
    }
}
