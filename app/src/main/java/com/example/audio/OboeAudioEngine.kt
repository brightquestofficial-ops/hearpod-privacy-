package com.example.audio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AudioEngineType(val title: String, val subtitle: String) {
    OBOE_CPP("Oboe C++ (AAudio)", "Direct kernel MMAP low-latency engine"),
    ANDROID_AUDIO("Standard Android", "High-compatibility Java audio pipeline")
}

/**
 * Oboe-based C++ Real-Time Audio Engine.
 *
 * Connects to the native C++ engine via the [OboeAudioEngineJni] interface layer
 * and manages thread-safe stream execution with [OboeStreamHandle].
 *
 * Utilizes Google Oboe to leverage Android AAudio (MMAP direct kernel buffer)
 * with automatic fallback to OpenSL ES for ultra-low latency (< 5ms) mouth-to-ear audio.
 *
 * Signal processing (100Hz HP, 7200Hz LP, 6-Band Parametric EQ, Noise Gate, Limiter)
 * executes directly in C++ on the high-priority real-time audio thread.
 */
class OboeAudioEngine(
    private val context: Context,
    val jniInterface: IOboeAudioEngineJni = OboeAudioEngineJni
) {

    private val scope = CoroutineScope(Dispatchers.Default)
    private val streamHandle = OboeStreamHandle(jniInterface)
    private var telemetryJob: Job? = null

    var onStreamStoppedUnexpectedly: (() -> Unit)? = null

    val frequencies = listOf(250, 500, 1000, 2000, 4000, 8000)

    private val _engineState = MutableStateFlow(
        AudioEngineState(
            isRunning = false,
            latencyMs = 3.8f,
            masterGain = 3.0f,
            engineType = AudioEngineType.OBOE_CPP
        )
    )
    val engineState: StateFlow<AudioEngineState> = _engineState.asStateFlow()

    companion object {
        private const val TAG = "OboeAudioEngine"
        val isLibraryLoaded: Boolean
            get() = OboeAudioEngineJni.isLibraryLoaded
    }

    init {
        if (isLibraryLoaded && streamHandle.isValid) {
            applyPreset("Speech Clarity")
        }
    }

    fun startTransmission(): Boolean {
        if (!isLibraryLoaded || !streamHandle.isValid) {
            Log.e(TAG, "Cannot start Oboe engine: native library not available or stream handle invalid")
            return false
        }
        if (_engineState.value.isRunning && streamHandle.isRunning) return true

        val started = streamHandle.start()
        if (started) {
            _engineState.value = _engineState.value.copy(
                isRunning = true,
                latencyMs = streamHandle.latencyMs
            )
            startTelemetryLoop()
            Log.i(TAG, "Oboe native engine transmission started")
        } else {
            Log.e(TAG, "streamHandle.start() returned false")
            _engineState.value = _engineState.value.copy(isRunning = false)
        }
        return started
    }

    fun stopTransmission() {
        if (!isLibraryLoaded) return
        stopTelemetryLoop()
        streamHandle.stop()
        _engineState.value = _engineState.value.copy(isRunning = false)
        Log.i(TAG, "Oboe native engine transmission stopped")
    }

    fun setMasterGain(gain: Float) {
        val clamped = gain.coerceIn(0.5f, 8.0f)
        _engineState.value = _engineState.value.copy(masterGain = clamped)
        if (isLibraryLoaded && streamHandle.isValid) {
            streamHandle.setMasterGain(clamped)
        }
    }

    fun setEcoMode(enabled: Boolean) {
        _engineState.value = _engineState.value.copy(
            isEcoModeActive = enabled,
            isDspThrottled = enabled,
            dspThrottleRate = if (enabled) "4x Throttle (Eco Saver ~8 FPS)" else "1x (Normal 30 FPS)"
        )
    }

    fun setEarBalance(balance: Float) {
        val clamped = balance.coerceIn(-1.0f, 1.0f)
        _engineState.value = _engineState.value.copy(earBalance = clamped)
        if (isLibraryLoaded && streamHandle.isValid) {
            streamHandle.setEarBalance(clamped)
        }
    }

    fun setLeftRightGains(left: Float, right: Float) {
        val clampedLeft = left.coerceIn(0.0f, 2.0f)
        val clampedRight = right.coerceIn(0.0f, 2.0f)
        if (isLibraryLoaded && streamHandle.isValid) {
            streamHandle.setLeftRightGains(clampedLeft, clampedRight)
        }
    }

    fun setPodMicRouting(routing: Int) {
        when (routing) {
            0 -> { // LEFT_POD_ONLY
                setLeftRightGains(1.35f, 0.40f)
                setTransparencyLevel(0.90f)
            }
            1 -> { // RIGHT_POD_ONLY
                setLeftRightGains(0.40f, 1.35f)
                setTransparencyLevel(0.90f)
            }
            2 -> { // DUAL_POD_BEAMFORMING
                setLeftRightGains(1.15f, 1.15f)
                setTransparencyLevel(0.95f)
            }
            3 -> { // PHONE_DIRECTIONAL
                setLeftRightGains(1.0f, 1.0f)
                setTransparencyLevel(0.85f)
            }
        }
    }

    fun setMicSensitivity(sensitivity: Float) {
        val clamped = sensitivity.coerceIn(0.5f, 2.5f)
        _engineState.value = _engineState.value.copy(micSensitivity = clamped)
        if (isLibraryLoaded && streamHandle.isValid) {
            streamHandle.setMicSensitivity(clamped)
        }
    }

    fun setTransparencyLevel(level: Float) {
        val clamped = level.coerceIn(0f, 1f)
        if (isLibraryLoaded && streamHandle.isValid) {
            streamHandle.setTransparencyLevel(clamped)
        }
    }

    fun setBandGain(frequencyHz: Int, gainDb: Float) {
        if (isLibraryLoaded && streamHandle.isValid) {
            streamHandle.setBandGain(frequencyHz, gainDb.coerceIn(-12f, 18f))
        }
    }

    fun getBandGain(frequencyHz: Int): Float {
        return if (isLibraryLoaded && streamHandle.isValid) {
            streamHandle.getBandGain(frequencyHz)
        } else 0f
    }

    fun getAllBandGains(): Map<Int, Float> {
        return frequencies.associateWith { getBandGain(it) }
    }

    fun setAiNoiseMode(mode: AiNoiseMode) {
        _engineState.value = _engineState.value.copy(aiNoiseMode = mode)
        if (isLibraryLoaded && streamHandle.isValid) {
            streamHandle.setAiNoiseMode(mode)
        }
    }

    fun setFeedbackPrevention(enabled: Boolean) {
        _engineState.value = _engineState.value.copy(feedbackPreventionEnabled = enabled)
        if (isLibraryLoaded && streamHandle.isValid) {
            streamHandle.setFeedbackPrevention(enabled)
        }
    }

    fun setSpeechFocus(enabled: Boolean) {
        _engineState.value = _engineState.value.copy(speechFocusEnabled = enabled)
        if (enabled) {
            setAiNoiseMode(AiNoiseMode.CONVERSATION_FOCUS)
            setBandGain(250, -3f)
            setBandGain(500, 1f)
            setBandGain(1000, 4f)
            setBandGain(2000, 6f)
            setBandGain(4000, 6f)
            setBandGain(8000, 3f)
        } else {
            setAiNoiseMode(AiNoiseMode.BALANCED)
        }
    }

    fun applyPreset(presetName: String) {
        when (presetName) {
            "Speech Clarity" -> {
                setBandGain(250, 0f)
                setBandGain(500, 1f)
                setBandGain(1000, 3f)
                setBandGain(2000, 5f)
                setBandGain(4000, 5f)
                setBandGain(8000, 3f)
                setMasterGain(1.0f)
                setAiNoiseMode(AiNoiseMode.CONVERSATION_FOCUS)
            }
            "Noisy Restaurant" -> {
                setBandGain(250, -4f)
                setBandGain(500, 0f)
                setBandGain(1000, 3f)
                setBandGain(2000, 6f)
                setBandGain(4000, 6f)
                setBandGain(8000, 2f)
                setMasterGain(1.20f)
                setAiNoiseMode(AiNoiseMode.CONVERSATION_FOCUS)
            }
            "TV / Media" -> {
                setBandGain(250, 0f)
                setBandGain(500, 1f)
                setBandGain(1000, 3f)
                setBandGain(2000, 4f)
                setBandGain(4000, 4f)
                setBandGain(8000, 2f)
                setMasterGain(1.05f)
                setAiNoiseMode(AiNoiseMode.BALANCED)
            }
            "Outdoor / Wind" -> {
                setBandGain(250, -6f)
                setBandGain(500, 0f)
                setBandGain(1000, 2f)
                setBandGain(2000, 4f)
                setBandGain(4000, 4f)
                setBandGain(8000, 1f)
                setMasterGain(1.00f)
                setAiNoiseMode(AiNoiseMode.CONVERSATION_FOCUS)
            }
            "Balanced Natural" -> {
                setBandGain(250, 0f)
                setBandGain(500, 0f)
                setBandGain(1000, 2f)
                setBandGain(2000, 3f)
                setBandGain(4000, 3f)
                setBandGain(8000, 2f)
                setMasterGain(1.00f)
                setAiNoiseMode(AiNoiseMode.BALANCED)
            }
        }
    }

    fun getDiagnostics(): OboeStreamDiagnostics {
        return streamHandle.getDiagnostics()
    }

    private fun startTelemetryLoop() {
        telemetryJob?.cancel()
        telemetryJob = scope.launch {
            val waveformArray = FloatArray(32)
            while (isActive && _engineState.value.isRunning) {
                if (streamHandle.isValid) {
                    if (!streamHandle.isRunning) {
                        Log.w(TAG, "Native Oboe stream stopped running unexpectedly")
                        _engineState.value = _engineState.value.copy(isRunning = false)
                        onStreamStoppedUnexpectedly?.invoke()
                        break
                    }
                    val db = streamHandle.currentDb
                    val peak = streamHandle.peakDb
                    val latency = streamHandle.latencyMs
                    streamHandle.getWaveformSamples(waveformArray)

                    _engineState.value = _engineState.value.copy(
                        currentDb = db,
                        peakDb = peak,
                        latencyMs = latency,
                        waveformSamples = waveformArray.toList()
                    )
                }
                delay(if (_engineState.value.isEcoModeActive) 130L else 33L) // ~30 FPS in Normal, ~7.7 FPS in Eco Mode
            }
        }
    }

    private fun stopTelemetryLoop() {
        telemetryJob?.cancel()
        telemetryJob = null
    }

    fun release() {
        stopTransmission()
        streamHandle.close()
    }
}
