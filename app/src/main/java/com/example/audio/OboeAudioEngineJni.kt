package com.example.audio

import android.util.Log
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Diagnostic metrics and hardware parameters reported by the native Oboe stream.
 */
data class OboeStreamDiagnostics(
    val isRunning: Boolean = false,
    val sampleRate: Int = 48000,
    val bufferSizeFrames: Int = 0,
    val isAAudio: Boolean = true,
    val apiName: String = if (isAAudio) "AAudio (Kernel MMAP Direct)" else "OpenSL ES (Legacy Direct)",
    val latencyMs: Float = 3.8f,
    val currentDb: Float = 35.0f,
    val peakDb: Float = 35.0f,
    val masterGain: Float = 1.15f
)

/**
 * High-level stream controller contract for real-time mouth-to-ear audio pipelines.
 */
interface RealtimeStreamController {
    fun start(): Boolean
    fun stop()
    val isRunning: Boolean
    fun setMasterGain(gain: Float)
    fun setBandGain(frequencyHz: Int, gainDb: Float)
    fun getBandGain(frequencyHz: Int): Float
    fun setAiNoiseMode(mode: AiNoiseMode)
    fun setFeedbackPrevention(enabled: Boolean)
    fun setEarBalance(balance: Float)
    fun setLeftRightGains(left: Float, right: Float)
    fun setMicSensitivity(sensitivity: Float)
    fun setTransparencyLevel(level: Float)
    val currentDb: Float
    val peakDb: Float
    val latencyMs: Float
    fun getWaveformSamples(outArray: FloatArray)
    fun getDiagnostics(): OboeStreamDiagnostics
}

/**
 * JNI Interface Contract specifying all native function signatures required
 * by the native C++ Oboe audio engine (`libhearpod_audio.so`).
 */
interface IOboeAudioEngineJni {
    fun createEngine(): Long
    fun destroyEngine(engineHandle: Long)
    fun startEngine(engineHandle: Long): Boolean
    fun stopEngine(engineHandle: Long)
    fun isRunning(engineHandle: Long): Boolean
    fun setMasterGain(engineHandle: Long, gain: Float)
    fun setBandGain(engineHandle: Long, freq: Int, gainDb: Float)
    fun getBandGain(engineHandle: Long, freq: Int): Float
    fun setAiNoiseMode(engineHandle: Long, mode: Int)
    fun setFeedbackPrevention(engineHandle: Long, enabled: Boolean)
    fun getCurrentDb(engineHandle: Long): Float
    fun getPeakDb(engineHandle: Long): Float
    fun getLatencyMs(engineHandle: Long): Float
    fun getWaveform(engineHandle: Long, outArray: FloatArray)
    fun getSampleRate(engineHandle: Long): Int
    fun getBufferSizeFrames(engineHandle: Long): Int
    fun isAAudio(engineHandle: Long): Boolean
    fun setEarBalance(engineHandle: Long, balance: Float)
    fun setLeftRightGains(engineHandle: Long, left: Float, right: Float)
    fun getEarBalance(engineHandle: Long): Float
    fun setMicSensitivity(engineHandle: Long, sensitivity: Float)
    fun getMicSensitivity(engineHandle: Long): Float
    fun setTransparencyLevel(engineHandle: Long, level: Float)
}

/**
 * Dedicated JNI Interface Layer for the Oboe C++ Real-Time Audio Engine.
 *
 * Implements [IOboeAudioEngineJni] using direct native JNI method bindings to
 * `libhearpod_audio.so`.
 */
open class OboeAudioEngineJni : IOboeAudioEngineJni {

    external override fun createEngine(): Long
    external override fun destroyEngine(engineHandle: Long)
    external override fun startEngine(engineHandle: Long): Boolean
    external override fun stopEngine(engineHandle: Long)
    external override fun isRunning(engineHandle: Long): Boolean
    external override fun setMasterGain(engineHandle: Long, gain: Float)
    external override fun setBandGain(engineHandle: Long, freq: Int, gainDb: Float)
    external override fun getBandGain(engineHandle: Long, freq: Int): Float
    external override fun setAiNoiseMode(engineHandle: Long, mode: Int)
    external override fun setFeedbackPrevention(engineHandle: Long, enabled: Boolean)
    external override fun getCurrentDb(engineHandle: Long): Float
    external override fun getPeakDb(engineHandle: Long): Float
    external override fun getLatencyMs(engineHandle: Long): Float
    external override fun getWaveform(engineHandle: Long, outArray: FloatArray)
    external override fun getSampleRate(engineHandle: Long): Int
    external override fun getBufferSizeFrames(engineHandle: Long): Int
    external override fun isAAudio(engineHandle: Long): Boolean
    external override fun setEarBalance(engineHandle: Long, balance: Float)
    external override fun setLeftRightGains(engineHandle: Long, left: Float, right: Float)
    external override fun getEarBalance(engineHandle: Long): Float
    external override fun setMicSensitivity(engineHandle: Long, sensitivity: Float)
    external override fun getMicSensitivity(engineHandle: Long): Float
    external override fun setTransparencyLevel(engineHandle: Long, level: Float)

    companion object : OboeAudioEngineJni() {
        private const val TAG = "OboeAudioEngineJni"

        var isLibraryLoaded: Boolean = false
            private set

        init {
            try {
                System.loadLibrary("hearpod_audio")
                isLibraryLoaded = true
                Log.i(TAG, "Native library 'hearpod_audio' loaded successfully into JNI interface layer")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library 'hearpod_audio' in JNI layer", e)
                isLibraryLoaded = false
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException while loading native library 'hearpod_audio'", e)
                isLibraryLoaded = false
            }
        }
    }
}

/**
 * Thread-safe, lifecycle-managed native stream controller wrapper.
 *
 * Encapsulates the 64-bit native C++ engine pointer, guarding against double-frees,
 * use-after-free, and dangling pointers. Implements [RealtimeStreamController] and [AutoCloseable].
 */
class OboeStreamHandle(
    private val jni: IOboeAudioEngineJni = OboeAudioEngineJni
) : RealtimeStreamController, Closeable {

    private val handle = AtomicLong(0L)
    private val isClosed = AtomicBoolean(false)

    init {
        if (OboeAudioEngineJni.isLibraryLoaded) {
            try {
                val ptr = jni.createEngine()
                handle.set(ptr)
                Log.i(TAG, "Native Oboe audio engine instance allocated at handle: $ptr")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed creating native Oboe audio engine", e)
                handle.set(0L)
            }
        } else {
            Log.w(TAG, "Oboe native library not loaded; handle initialized to 0")
        }
    }

    val isValid: Boolean
        get() = handle.get() != 0L && !isClosed.get()

    override val isRunning: Boolean
        get() {
            val ptr = handle.get()
            return if (ptr != 0L && !isClosed.get()) jni.isRunning(ptr) else false
        }

    override fun start(): Boolean {
        val ptr = handle.get()
        if (ptr == 0L || isClosed.get()) {
            Log.e(TAG, "Cannot start: native stream handle is invalid or closed")
            return false
        }
        return try {
            val started = jni.startEngine(ptr)
            Log.i(TAG, "startEngine($ptr) result: $started")
            started
        } catch (e: Throwable) {
            Log.e(TAG, "Exception in jni.startEngine", e)
            false
        }
    }

    override fun stop() {
        val ptr = handle.get()
        if (ptr != 0L && !isClosed.get()) {
            try {
                jni.stopEngine(ptr)
                Log.i(TAG, "stopEngine($ptr) called")
            } catch (e: Throwable) {
                Log.e(TAG, "Exception in jni.stopEngine", e)
            }
        }
    }

    override fun setMasterGain(gain: Float) {
        val ptr = handle.get()
        if (ptr != 0L && !isClosed.get()) {
            val clamped = gain.coerceIn(0.5f, 8.0f)
            jni.setMasterGain(ptr, clamped)
        }
    }

    override fun setBandGain(frequencyHz: Int, gainDb: Float) {
        val ptr = handle.get()
        if (ptr != 0L && !isClosed.get()) {
            jni.setBandGain(ptr, frequencyHz, gainDb.coerceIn(-12f, 18f))
        }
    }

    override fun getBandGain(frequencyHz: Int): Float {
        val ptr = handle.get()
        return if (ptr != 0L && !isClosed.get()) {
            jni.getBandGain(ptr, frequencyHz)
        } else 0f
    }

    override fun setAiNoiseMode(mode: AiNoiseMode) {
        val ptr = handle.get()
        if (ptr != 0L && !isClosed.get()) {
            val modeCode = when (mode) {
                AiNoiseMode.STUDIO_QUIET -> 0
                AiNoiseMode.BALANCED -> 1
                AiNoiseMode.CONVERSATION_FOCUS -> 2
                AiNoiseMode.OFF -> 3
            }
            jni.setAiNoiseMode(ptr, modeCode)
        }
    }

    override fun setFeedbackPrevention(enabled: Boolean) {
        val ptr = handle.get()
        if (ptr != 0L && !isClosed.get()) {
            jni.setFeedbackPrevention(ptr, enabled)
        }
    }

    override fun setEarBalance(balance: Float) {
        val ptr = handle.get()
        if (ptr != 0L && !isClosed.get()) {
            jni.setEarBalance(ptr, balance.coerceIn(-1.0f, 1.0f))
        }
    }

    override fun setLeftRightGains(left: Float, right: Float) {
        val ptr = handle.get()
        if (ptr != 0L && !isClosed.get()) {
            jni.setLeftRightGains(ptr, left.coerceIn(0.0f, 2.0f), right.coerceIn(0.0f, 2.0f))
        }
    }

    override fun setMicSensitivity(sensitivity: Float) {
        val ptr = handle.get()
        if (ptr != 0L && !isClosed.get()) {
            jni.setMicSensitivity(ptr, sensitivity.coerceIn(0.5f, 2.5f))
        }
    }

    override fun setTransparencyLevel(level: Float) {
        val ptr = handle.get()
        if (ptr != 0L && !isClosed.get()) {
            jni.setTransparencyLevel(ptr, level.coerceIn(0f, 1f))
        }
    }

    override val currentDb: Float
        get() {
            val ptr = handle.get()
            return if (ptr != 0L && !isClosed.get()) jni.getCurrentDb(ptr) else 30.0f
        }

    override val peakDb: Float
        get() {
            val ptr = handle.get()
            return if (ptr != 0L && !isClosed.get()) jni.getPeakDb(ptr) else 30.0f
        }

    override val latencyMs: Float
        get() {
            val ptr = handle.get()
            return if (ptr != 0L && !isClosed.get()) jni.getLatencyMs(ptr) else 3.8f
        }

    val sampleRate: Int
        get() {
            val ptr = handle.get()
            return if (ptr != 0L && !isClosed.get()) jni.getSampleRate(ptr) else 48000
        }

    val bufferSizeFrames: Int
        get() {
            val ptr = handle.get()
            return if (ptr != 0L && !isClosed.get()) jni.getBufferSizeFrames(ptr) else 0
        }

    val isAAudio: Boolean
        get() {
            val ptr = handle.get()
            return if (ptr != 0L && !isClosed.get()) jni.isAAudio(ptr) else false
        }

    override fun getWaveformSamples(outArray: FloatArray) {
        val ptr = handle.get()
        if (ptr != 0L && !isClosed.get()) {
            jni.getWaveform(ptr, outArray)
        }
    }

    override fun getDiagnostics(): OboeStreamDiagnostics {
        val ptr = handle.get()
        if (ptr == 0L || isClosed.get()) {
            return OboeStreamDiagnostics(isRunning = false)
        }
        val running = jni.isRunning(ptr)
        val sr = jni.getSampleRate(ptr)
        val buf = jni.getBufferSizeFrames(ptr)
        val aaudio = jni.isAAudio(ptr)
        val lat = jni.getLatencyMs(ptr)
        val curDb = jni.getCurrentDb(ptr)
        val pkDb = jni.getPeakDb(ptr)

        return OboeStreamDiagnostics(
            isRunning = running,
            sampleRate = sr,
            bufferSizeFrames = buf,
            isAAudio = aaudio,
            latencyMs = lat,
            currentDb = curDb,
            peakDb = pkDb
        )
    }

    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            val ptr = handle.getAndSet(0L)
            if (ptr != 0L) {
                try {
                    jni.stopEngine(ptr)
                    jni.destroyEngine(ptr)
                    Log.i(TAG, "Native Oboe engine instance $ptr successfully destroyed")
                } catch (e: Throwable) {
                    Log.e(TAG, "Error destroying native Oboe engine handle: $ptr", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "OboeStreamHandle"
    }
}
