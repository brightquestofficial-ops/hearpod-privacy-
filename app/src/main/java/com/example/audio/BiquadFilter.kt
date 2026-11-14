package com.example.audio

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * High-performance 2nd-order IIR Biquad Filter implemented in Kotlin.
 * Based on the Audio EQ Cookbook by Robert Bristow-Johnson.
 *
 * Uses volatile coefficient encapsulation for thread-safe real-time DSP parameter updates.
 */
class BiquadFilter(
    val centerFrequency: Float,
    val sampleRate: Float = 44100f,
    val q: Float = 1.2f
) {
    private class Coeffs(
        val b0: Float,
        val b1: Float,
        val b2: Float,
        val a1: Float,
        val a2: Float
    )

    @Volatile
    private var coeffs = Coeffs(1.0f, 0.0f, 0.0f, 0.0f, 0.0f)

    // Direct Form II Transposed states
    private var s1 = 0.0f
    private var s2 = 0.0f

    var currentGainDb: Float = 0f
        private set

    init {
        setGain(0f)
    }

    /**
     * Updates the gain of this peaking EQ band in decibels (-12 dB to +18 dB).
     * Atomically swaps the coefficient struct so the audio processing thread never
     * observes torn or partially written filter parameters.
     */
    fun setGain(gainDb: Float) {
        currentGainDb = gainDb
        val a = 10.0.pow(gainDb / 40.0).toFloat()
        val omega = (2.0 * Math.PI * centerFrequency / sampleRate).toFloat()
        val sn = sin(omega)
        val cs = cos(omega)
        val alpha = sn / (2.0f * q)

        val b0Raw = 1.0f + alpha * a
        val b1Raw = -2.0f * cs
        val b2Raw = 1.0f - alpha * a
        val a0Raw = 1.0f + alpha / a
        val a1Raw = -2.0f * cs
        val a2Raw = 1.0f - alpha / a

        val newCoeffs = Coeffs(
            b0 = b0Raw / a0Raw,
            b1 = b1Raw / a0Raw,
            b2 = b2Raw / a0Raw,
            a1 = a1Raw / a0Raw,
            a2 = a2Raw / a0Raw
        )
        coeffs = newCoeffs
    }

    /**
     * Processes a single float sample [-1.0f, 1.0f] using Direct Form II Transposed
     */
    fun processSample(input: Float): Float {
        val c = coeffs
        val output = c.b0 * input + s1
        s1 = c.b1 * input - c.a1 * output + s2
        s2 = c.b2 * input - c.a2 * output
        if (output.isNaN() || output.isInfinite()) {
            reset()
            return input
        }
        return output
    }

    fun reset() {
        s1 = 0.0f
        s2 = 0.0f
    }
}

