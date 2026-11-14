package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class AudioEngineState(
    val isRunning: Boolean = false,
    val isRecording: Boolean = false,
    val currentDb: Float = 35f,
    val peakDb: Float = 35f,
    val masterGain: Float = 1.0f,
    val aiNoiseMode: AiNoiseMode = AiNoiseMode.BALANCED,
    val isHardwareNoiseSuppressionActive: Boolean = false,
    val feedbackPreventionEnabled: Boolean = true,
    val speechFocusEnabled: Boolean = false,
    val latencyMs: Float = 4.0f,
    val recordingDurationSeconds: Int = 0,
    val waveformSamples: List<Float> = List(32) { 0.08f },
    val engineType: AudioEngineType = AudioEngineType.ANDROID_AUDIO,
    val earBalance: Float = 0.0f,
    val micSensitivity: Float = 1.0f,
    val isNoiseGateEnabled: Boolean = true,
    val isNoiseGateOpen: Boolean = false,
    val noiseGateSensitivity: NoiseGateSensitivity = NoiseGateSensitivity.BALANCED,
    val noiseGateSuppressionDb: Float = -18f,
    val isEcoModeActive: Boolean = false,
    val isDspThrottled: Boolean = false,
    val dspThrottleRate: String = "1x (Normal 30 FPS)"
)

class HearingAidAudioEngine(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    @Volatile
    private var isDspRunning = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Query native hardware sample rate to eliminate AudioFlinger resampling latency
    private val sampleRate: Int = run {
        val nativeRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
        if (nativeRate != null && nativeRate in 44100..48000) nativeRate else 48000
    }

    private val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
    private val channelConfigOut = AudioFormat.CHANNEL_OUT_STEREO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var hardwareNoiseSuppressor: NoiseSuppressor? = null
    private var hardwareEchoCanceler: AcousticEchoCanceler? = null

    private var processingJob: Job? = null
    private var recordingTimerJob: Job? = null

    // AI Spectral Noise Filter
    val aiSpectralFilter = AiSpectralNoiseFilter(sampleRate = sampleRate.toFloat())

    // Digital Noise Gate (Real-Time Voice-Activity Squelch)
    val digitalNoiseGate = DigitalNoiseGate(sampleRate = sampleRate.toFloat())

    // 6-Band Biquad Equalizer filters
    val frequencies = listOf(250, 500, 1000, 2000, 4000, 8000)
    private val filters = frequencies.map { freq ->
        BiquadFilter(centerFrequency = freq.toFloat(), sampleRate = sampleRate.toFloat())
    }

    private val _engineState = MutableStateFlow(
        AudioEngineState(latencyMs = (256f / sampleRate) * 1000f)
    )
    val engineState: StateFlow<AudioEngineState> = _engineState.asStateFlow()

    // Session Recording variables
    private var recordingFile: File? = null
    private var recordingStream: FileOutputStream? = null
    private var recordingPcmBytesWritten: Long = 0
    private var recordingStartTime: Long = 0
    private var recordingPeakDb: Float = 0f
    private var recordingDbSum: Double = 0.0
    private var recordingSamplesCount: Long = 0

    // Audiogram pure tone playback
    private val toneLock = Any()
    private var toneJob: Job? = null
    private var toneTrack: AudioTrack? = null

    init {
        // Crystal-clear speech presence: boosted consonants (2kHz - 8kHz), natural vocal warmth
        setBandGain(250, 0f)
        setBandGain(500, 1f)
        setBandGain(1000, 3f)
        setBandGain(2000, 5f)
        setBandGain(4000, 5f)
        setBandGain(8000, 3f)
        setMasterGain(1.0f)
    }

    fun setAiNoiseMode(mode: AiNoiseMode) {
        aiSpectralFilter.mode = mode
        _engineState.value = _engineState.value.copy(aiNoiseMode = mode)
    }

    fun setNoiseGateEnabled(enabled: Boolean) {
        digitalNoiseGate.isEnabled = enabled
        _engineState.value = _engineState.value.copy(isNoiseGateEnabled = enabled)
    }

    fun setNoiseGateSensitivity(sensitivity: NoiseGateSensitivity) {
        digitalNoiseGate.sensitivity = sensitivity
        _engineState.value = _engineState.value.copy(
            noiseGateSensitivity = sensitivity,
            noiseGateSuppressionDb = sensitivity.suppressionDb
        )
    }

    /**
     * Toggles low-power DSP processing frequency when the device is at low battery levels.
     * Decimates telemetry frequency (~8 FPS instead of 30 FPS), decimates RMS energy calculations,
     * and bypasses redundant flat biquad filters to significantly extend battery life.
     */
    fun setEcoMode(enabled: Boolean) {
        aiSpectralFilter.isEcoMode = enabled
        digitalNoiseGate.isEcoMode = enabled
        _engineState.value = _engineState.value.copy(
            isEcoModeActive = enabled,
            isDspThrottled = enabled,
            dspThrottleRate = if (enabled) "4x Throttle (Eco Saver ~8 FPS)" else "1x (Normal 30 FPS)"
        )
    }

    fun setTransparencyLevel(level: Float) {
        val clamped = level.coerceIn(0f, 1.0f)
        aiSpectralFilter.transparencyLevel = clamped
    }

    fun setBandGain(frequencyHz: Int, gainDb: Float) {
        val index = frequencies.indexOf(frequencyHz)
        if (index != -1) {
            filters[index].setGain(gainDb.coerceIn(-12f, 18f))
        }
    }

    fun getBandGain(frequencyHz: Int): Float {
        val index = frequencies.indexOf(frequencyHz)
        return if (index != -1) filters[index].currentGainDb else 0f
    }

    fun getAllBandGains(): Map<Int, Float> {
        return frequencies.associateWith { getBandGain(it) }
    }

    fun setMasterGain(gain: Float) {
        _engineState.value = _engineState.value.copy(masterGain = gain.coerceIn(0.5f, 8.0f))
    }

    @Volatile private var leftChannelGain = 1.0f
    @Volatile private var rightChannelGain = 1.0f
    @Volatile private var currentEarBalance = 0.0f
    @Volatile var isSpeakerOutput = false

    fun setEarBalance(balance: Float) {
        val clamped = balance.coerceIn(-1.0f, 1.0f)
        currentEarBalance = clamped
        _engineState.value = _engineState.value.copy(earBalance = clamped)
    }

    fun setLeftRightGains(left: Float, right: Float) {
        leftChannelGain = left.coerceIn(0.0f, 2.0f)
        rightChannelGain = right.coerceIn(0.0f, 2.0f)
    }

    fun setMicSensitivity(sensitivity: Float) {
        _engineState.value = _engineState.value.copy(micSensitivity = sensitivity.coerceIn(0.5f, 2.5f))
    }

    fun setPodMicRouting(routing: PodMicRouting) {
        when (routing) {
            PodMicRouting.LEFT_POD_ONLY -> {
                setLeftRightGains(1.35f, 0.40f)
                aiSpectralFilter.transparencyLevel = 0.90f
            }
            PodMicRouting.RIGHT_POD_ONLY -> {
                setLeftRightGains(0.40f, 1.35f)
                aiSpectralFilter.transparencyLevel = 0.90f
            }
            PodMicRouting.DUAL_POD_BEAMFORMING -> {
                setLeftRightGains(1.15f, 1.15f)
                aiSpectralFilter.transparencyLevel = 0.95f
            }
            PodMicRouting.PHONE_DIRECTIONAL -> {
                setLeftRightGains(1.0f, 1.0f)
                aiSpectralFilter.transparencyLevel = 0.85f
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val inputDevice = if (routing == PodMicRouting.PHONE_DIRECTIONAL) null else findConnectedInputDevice()
                audioRecord?.preferredDevice = inputDevice
            } catch (e: Throwable) {
                // Ignore safe update
            }
        }
    }

    private fun findConnectedInputDevice(): AudioDeviceInfo? {
        return try {
            val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            inputs.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun setFeedbackPrevention(enabled: Boolean) {
        _engineState.value = _engineState.value.copy(feedbackPreventionEnabled = enabled)
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

    @SuppressLint("MissingPermission")
    fun startTransmission(
        preferredInputDevice: AudioDeviceInfo? = null,
        preferredOutputDevice: AudioDeviceInfo? = null
    ): Boolean {
        if (_engineState.value.isRunning) return true

        // Clean up any stale hardware instances before starting anew
        cleanupAudioHardware()

        try {
            // Determine working sample rate and AudioRecord configuration with automatic hardware fallback
            val ratesToTry = listOf(sampleRate, 48000, 44100, 16000).distinct()
            val sourcesToTry = listOf(
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.DEFAULT,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION
            )

            var record: AudioRecord? = null
            var actualRate = sampleRate
            var actualRecBufSize = 0

            // Try up to 4 attempts with short backoff so AudioFlinger teardown never fails immediate restart
            outer@ for (attempt in 1..4) {
                for (rate in ratesToTry) {
                    val minRec = AudioRecord.getMinBufferSize(rate, channelConfigIn, audioFormat)
                    if (minRec <= 0) continue

                    val bufSize = (minRec * 2).coerceAtLeast(minRec).coerceAtLeast(1024)

                    for (source in sourcesToTry) {
                        try {
                            val candidate = AudioRecord(
                                source,
                                rate,
                                channelConfigIn,
                                audioFormat,
                                bufSize
                            )
                            if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                                record = candidate
                                actualRate = rate
                                actualRecBufSize = bufSize
                                Log.i("HearingAidAudioEngine", "AudioRecord initialized successfully at ${rate}Hz (source=$source, attempt=$attempt)")
                                break@outer
                            } else {
                                candidate.release()
                            }
                        } catch (e: Throwable) {
                            // Try next candidate
                        }
                    }
                }
                if (attempt < 4) {
                    try { Thread.sleep(30) } catch (e: Throwable) {}
                }
            }

            if (record == null) {
                Log.e("HearingAidAudioEngine", "AudioRecord initialization failed across all hardware configurations")
                return false
            }

            // Route AudioRecord to preferred device if provided and safe (skip TYPE_BLUETOOTH_SCO which requires active SCO link)
            if (preferredInputDevice != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (preferredInputDevice.type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                    try {
                        record.preferredDevice = preferredInputDevice
                        Log.i("HearingAidAudioEngine", "AudioRecord preferred device set to: ${preferredInputDevice.productName}")
                    } catch (e: Throwable) {
                        Log.w("HearingAidAudioEngine", "Could not set preferred input device: ${e.message}")
                    }
                }
            }

            audioRecord = record

            // Hardware NoiseSuppressor & AcousticEchoCanceler if supported
            var hwNsActive = false
            try {
                val sessionId = record.audioSessionId
                if (NoiseSuppressor.isAvailable()) {
                    hardwareNoiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                        enabled = true
                    }
                    hwNsActive = hardwareNoiseSuppressor?.enabled == true
                }
                if (AcousticEchoCanceler.isAvailable()) {
                    hardwareEchoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                        enabled = true
                    }
                }
            } catch (e: Throwable) {
                Log.w("HearingAidAudioEngine", "Hardware effects unavailable: ${e.message}")
            }

            // AudioTrack configuration with multi-builder fallback for maximum device compatibility
            val minTrack = AudioTrack.getMinBufferSize(actualRate, channelConfigOut, audioFormat)
            val trackBufSize = if (minTrack > 0) (minTrack * 2).coerceAtLeast(1024) else 4096

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(actualRate)
                .setEncoding(audioFormat)
                .setChannelMask(channelConfigOut)
                .build()

            var track: AudioTrack? = null

            // Builder Attempt 1: Low-latency performance mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val candidate = AudioTrack.Builder()
                        .setAudioAttributes(audioAttributes)
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(trackBufSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                        .build()
                    if (candidate.state == AudioTrack.STATE_INITIALIZED) {
                        track = candidate
                    } else {
                        candidate.release()
                    }
                } catch (e: Throwable) {
                    Log.w("HearingAidAudioEngine", "Low-latency AudioTrack creation failed, falling back")
                }
            }

            // Builder Attempt 2: Standard performance mode
            if (track == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val candidate = AudioTrack.Builder()
                        .setAudioAttributes(audioAttributes)
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(trackBufSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                    if (candidate.state == AudioTrack.STATE_INITIALIZED) {
                        track = candidate
                    } else {
                        candidate.release()
                    }
                } catch (e: Throwable) {
                    Log.w("HearingAidAudioEngine", "Standard AudioTrack builder failed, falling back")
                }
            }

            // Builder Attempt 3: Legacy constructor
            if (track == null) {
                try {
                    @Suppress("DEPRECATION")
                    val candidate = AudioTrack(
                        audioAttributes,
                        format,
                        trackBufSize,
                        AudioTrack.MODE_STREAM,
                        AudioManager.AUDIO_SESSION_ID_GENERATE
                    )
                    if (candidate.state == AudioTrack.STATE_INITIALIZED) {
                        track = candidate
                    } else {
                        candidate.release()
                    }
                } catch (e: Throwable) {
                    Log.e("HearingAidAudioEngine", "Legacy AudioTrack constructor failed", e)
                }
            }

            if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
                Log.e("HearingAidAudioEngine", "AudioTrack failed to initialize across all attempts")
                cleanupAudioHardware()
                return false
            }

            try {
                track.setVolume(1.0f)
            } catch (e: Throwable) {
                // Ignore
            }

            // Route AudioTrack: safely check that preferredOutputDevice is NOT an inactive SCO device
            if (preferredOutputDevice != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (preferredOutputDevice.type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                    try {
                        track.preferredDevice = preferredOutputDevice
                        Log.i("HearingAidAudioEngine", "AudioTrack preferred output set to: ${preferredOutputDevice.productName}")
                    } catch (e: Throwable) {
                        Log.w("HearingAidAudioEngine", "Could not set preferred output device: ${e.message}")
                    }
                }
            }

            audioTrack = track

            // Ensure Android system media volume is not completely muted (gentle 35% fallback only if 0)
            try {
                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                if (currentVol == 0 && maxVol > 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.35f).toInt().coerceAtLeast(1), 0)
                }
            } catch (e: Throwable) {
                // Ignore volume check failures
            }

            // Real-time ultra low-latency chunk size: match native fast mixer burst for true zero-delay (< 15ms)
            val nativeBurst = try {
                audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 192
            } catch (e: Throwable) { 192 }
            val chunkFrames = nativeBurst.coerceIn(128, 256)
            val calculatedLatencyMs = (chunkFrames.toFloat() / actualRate.toFloat()) * 1000f

            // 1. Start AudioRecord with retry mechanism
            var recordStarted = false
            for (recAttempt in 1..3) {
                try {
                    record.startRecording()
                    if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        recordStarted = true
                        Log.i("HearingAidAudioEngine", "AudioRecord started recording on attempt $recAttempt")
                        break
                    }
                } catch (e: Throwable) {
                    Log.w("HearingAidAudioEngine", "audioRecord.startRecording attempt $recAttempt failed: ${e.message}")
                    try { Thread.sleep(50) } catch (t: Throwable) {}
                }
            }
            if (!recordStarted) {
                Log.e("HearingAidAudioEngine", "audioRecord.startRecording could not start after 3 attempts")
                cleanupAudioHardware()
                return false
            }

            // 2. Start AudioTrack with retry mechanism (NO blocking prefill or artificial buffer constriction)
            var trackStarted = false
            for (trackAttempt in 1..3) {
                try {
                    track.play()
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        trackStarted = true
                        Log.i("HearingAidAudioEngine", "AudioTrack started playing on attempt $trackAttempt")
                        break
                    }
                } catch (e: Throwable) {
                    Log.w("HearingAidAudioEngine", "audioTrack.play attempt $trackAttempt failed: ${e.message}")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try { track.preferredDevice = null } catch (t: Throwable) {}
                    }
                    try { Thread.sleep(50) } catch (t: Throwable) {}
                }
            }
            if (!trackStarted) {
                Log.e("HearingAidAudioEngine", "audioTrack.play failed across all attempts")
                cleanupAudioHardware()
                return false
            }

            aiSpectralFilter.updateSampleRate(actualRate.toFloat())
            aiSpectralFilter.reset()
            digitalNoiseGate.updateSampleRate(actualRate.toFloat())
            digitalNoiseGate.reset()

            _engineState.value = _engineState.value.copy(
                isRunning = true,
                isHardwareNoiseSuppressionActive = hwNsActive,
                latencyMs = calculatedLatencyMs
            )

            startDspLoop(chunkFrames, actualRate)
            return true
        } catch (e: Throwable) {
            Log.e("HearingAidAudioEngine", "Error starting audio transmission", e)
            stopTransmission()
            return false
        }
    }

    private fun startDspLoop(chunkSize: Int = 192, currentRate: Int = sampleRate) {
        processingJob?.cancel()
        isDspRunning = true
        processingJob = scope.launch(Dispatchers.IO) {
            // Set urgent audio priority so Android never throttles when the screen is locked
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            } catch (e: Throwable) {
                // Ignore
            }

            val shortBuffer = ShortArray(chunkSize)
            val stereoBuffer = ShortArray(chunkSize * 2)
            val drainBuffer = ShortArray(chunkSize)
            val floatBuffer = FloatArray(chunkSize)
            val byteBuffer = ByteArray(chunkSize * 2)

            val record = audioRecord ?: return@launch
            val track = audioTrack ?: return@launch

            // 1. Initial drain of any startup backlog (done once at start to guarantee real-time mouth-to-ear sync)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    while (record.read(drainBuffer, 0, chunkSize, AudioRecord.READ_NON_BLOCKING) > 0) {
                        // Discard startup junk
                    }
                } catch (e: Throwable) {
                    // Ignore
                }
            }

            var frameCount = 0
            val waveWindow = FloatArray(32)
            var currentLimiterGain = 1.0f
            var consecutiveHowlFrames = 0
            var feedbackDampening = 1.0f
            val outputHistory = FloatArray(2048)
            var outputHistoryIdx = 0

            while (isActive && isDspRunning && _engineState.value.isRunning) {
                // Read fresh real-time audio chunk directly from microphone
                val samplesRead = try {
                    record.read(shortBuffer, 0, chunkSize)
                } catch (e: Throwable) {
                    Log.w("HearingAidAudioEngine", "AudioRecord read transient exception: ${e.message}")
                    try { delay(15) } catch (t: Throwable) {}
                    continue
                }
                if (samplesRead <= 0) {
                    if (samplesRead < 0) {
                        try {
                            delay(10)
                        } catch (e: Throwable) {
                            // ignore
                        }
                    }
                    continue
                }

                val masterGain = _engineState.value.masterGain
                val feedbackProtect = _engineState.value.feedbackPreventionEnabled
                val micSens = _engineState.value.micSensitivity

                // Calibrated vocal gain for crystal-clear fidelity without analog saturation distortion
                val vocalGain = masterGain * 1.35f

                val bal = currentEarBalance
                val balLeft = if (bal <= 0f) 1.0f else (1.0f - bal)
                val balRight = if (bal >= 0f) 1.0f else (1.0f + bal)
                val effLeft = (leftChannelGain * balLeft).coerceIn(0f, 2.0f)
                val effRight = (rightChannelGain * balRight).coerceIn(0f, 2.0f)

                // Software Acoustic Echo Shield: In speaker mode, duck mic if speaker is actively playing
                var echoDucking = 1.0f
                if (isSpeakerOutput) {
                    val delayFrames = (currentRate * 0.025f).toInt() // ~25ms acoustic reflection window
                    var delayedSpeakerEnergy = 0.0f
                    for (d in 0 until samplesRead) {
                        val histPos = (outputHistoryIdx - delayFrames - d + outputHistory.size) % outputHistory.size
                        delayedSpeakerEnergy += abs(outputHistory[histPos])
                    }
                    val avgSpeakerLevel = delayedSpeakerEnergy / samplesRead
                    if (avgSpeakerLevel > 0.07f) {
                        // Speaker is loud: duck mic input proportionally to eliminate loopback canyon echo
                        echoDucking = (0.20f + 0.80f * (0.07f / avgSpeakerLevel)).coerceIn(0.18f, 1.0f)
                    }
                }

                // Convert to float normalized [-1.0f, 1.0f] and scale by mic sensitivity & echo shield
                val effectiveMicScale = (micSens / 32768.0f) * echoDucking
                for (i in 0 until samplesRead) {
                    floatBuffer[i] = shortBuffer[i] * effectiveMicScale
                }

                // AI Speech Clarifier & Background White Noise Silencer
                aiSpectralFilter.processFrame(floatBuffer, samplesRead)

                // Multi-band Equalizer filtering
                // In Eco Mode, bypass flat filters with near-zero gain (<0.25 dB) to eliminate redundant biquad math
                val isEco = _engineState.value.isEcoModeActive
                val activeFilters = if (isEco) {
                    filters.filter { abs(it.currentGainDb) > 0.25f }
                } else {
                    filters
                }

                for (i in 0 until samplesRead) {
                    var sample = floatBuffer[i]
                    for (f in activeFilters) {
                        sample = f.processSample(sample)
                    }

                    val absSample = abs(sample)
                    val sign = if (sample >= 0f) 1.0f else -1.0f
                    // Transparent WDRC (Wide Dynamic Range Compression):
                    // Gentle +4.5dB boost for quiet speech, 1:1 transparency for normal speech
                    val vocalLift = 1.0f + 0.65f * (1.0f - (absSample / 0.20f)).coerceIn(0f, 1f)
                    var amplified = sign * absSample * vocalGain * vocalLift * feedbackDampening

                    // Soft-knee analog saturation prevents harsh digital clipping
                    if (amplified > 0.88f) {
                        amplified = 0.88f + 0.12f * ((amplified - 0.88f) / (1.0f + (amplified - 0.88f)))
                    } else if (amplified < -0.88f) {
                        amplified = -0.88f - 0.12f * ((-amplified - 0.88f) / (1.0f + (-amplified - 0.88f)))
                    }

                    sample = amplified.coerceIn(-0.98f, 0.98f)
                    floatBuffer[i] = sample
                }

                // Digital Noise Gate: Automatically suppresses background noise when user is not speaking
                digitalNoiseGate.process(floatBuffer, samplesRead)

                // Smooth Lookahead Soft Limiter & Howling Feedback Squelch
                var maxPeak = 0.0f
                for (i in 0 until samplesRead) {
                    val p = abs(floatBuffer[i])
                    if (p > maxPeak) maxPeak = p
                }

                // If output persistently saturates near 1.0 on speaker, suppress acoustic feedback howl
                if (maxPeak > 0.82f) {
                    consecutiveHowlFrames++
                    if (consecutiveHowlFrames >= 3) {
                        feedbackDampening = 0.35f // Instantly drop gain by -9dB to terminate howl
                    }
                } else {
                    consecutiveHowlFrames = 0
                    feedbackDampening = (feedbackDampening + 0.04f).coerceAtMost(1.0f)
                }

                val targetLimiterGain = if (feedbackProtect && maxPeak > 0.85f) {
                    0.85f / maxPeak
                } else {
                    1.0f
                }

                // Smooth sample-by-sample linear interpolation
                val limiterStep = (targetLimiterGain - currentLimiterGain) / samplesRead

                var sumSquare = 0.0
                var peakAmp = 0.0f

                for (i in 0 until samplesRead) {
                    currentLimiterGain += limiterStep
                    var s = floatBuffer[i] * currentLimiterGain

                    // Soft-knee analog-style saturation prevents harsh digital clipping
                    if (s > 0.90f) {
                        s = 0.90f + 0.10f * ((s - 0.90f) / (1.0f + (s - 0.90f)))
                    } else if (s < -0.90f) {
                        s = -0.90f - 0.10f * ((-s - 0.90f) / (1.0f + (-s - 0.90f)))
                    }

                    s = s.coerceIn(-1.0f, 1.0f)
                    sumSquare += s * s
                    val absS = abs(s)
                    if (absS > peakAmp) peakAmp = absS

                    val leftS = (s * effLeft).coerceIn(-1.0f, 1.0f)
                    val rightS = (s * effRight).coerceIn(-1.0f, 1.0f)

                    val leftOut = (leftS * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
                    val rightOut = (rightS * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
                    val monoOut = (s * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()

                    stereoBuffer[i * 2] = leftOut
                    stereoBuffer[i * 2 + 1] = rightOut

                    if (_engineState.value.isRecording) {
                        byteBuffer[i * 2] = (monoOut.toInt() and 0xFF).toByte()
                        byteBuffer[i * 2 + 1] = ((monoOut.toInt() shr 8) and 0xFF).toByte()
                    }
                }

                // Update speaker output history for Software Acoustic Echo Shield
                for (i in 0 until samplesRead) {
                    outputHistory[outputHistoryIdx] = floatBuffer[i]
                    outputHistoryIdx = (outputHistoryIdx + 1) % outputHistory.size
                }

                // Direct non-blocking write to AudioTrack in stereo (audio plays out immediately with zero delay)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        track.write(stereoBuffer, 0, samplesRead * 2, AudioTrack.WRITE_NON_BLOCKING)
                    } else {
                        track.write(stereoBuffer, 0, samplesRead * 2)
                    }
                } catch (e: Throwable) {
                    Log.w("HearingAidAudioEngine", "AudioTrack write dropped frame: ${e.message}")
                }

                // Write to recording stream if recording
                if (_engineState.value.isRecording && recordingStream != null) {
                    try {
                        recordingStream?.write(byteBuffer, 0, samplesRead * 2)
                        recordingPcmBytesWritten += (samplesRead * 2)
                        recordingSamplesCount += samplesRead
                    } catch (e: Throwable) {
                        Log.e("HearingAidAudioEngine", "Failed writing recording", e)
                    }
                }

                frameCount++
                val telemetryInterval = if (_engineState.value.isEcoModeActive) 24 else 6
                if (frameCount % telemetryInterval == 0) {
                    // Emit metering at ~30 FPS in Normal mode; throttled to ~7.8 FPS in Eco Mode (75% lower CPU coroutine wakeups)
                    val rms = sqrt(sumSquare / samplesRead).toFloat()
                    val db = (30f + 20f * log10(rms.coerceAtLeast(0.0001f) * 10f + 1f)).coerceIn(25f, 98f)
                    val currentPeak = (30f + 20f * log10(peakAmp.coerceAtLeast(0.0001f) * 10f + 1f)).coerceIn(25f, 98f)

                    if (_engineState.value.isRecording) {
                        recordingDbSum += db
                        if (currentPeak > recordingPeakDb) recordingPeakDb = currentPeak
                    }

                    val waveIdx = (frameCount / telemetryInterval) % 32
                    waveWindow[waveIdx] = (rms * 4.5f).coerceIn(0.06f, 1.0f)

                    _engineState.value = _engineState.value.copy(
                        currentDb = db,
                        peakDb = currentPeak,
                        waveformSamples = waveWindow.toList(),
                        isNoiseGateOpen = digitalNoiseGate.isGateOpen
                    )
                }
            }
        }
    }

    private fun cleanupAudioHardware() {
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Throwable) {
            // Ignore
        }

        try {
            hardwareNoiseSuppressor?.enabled = false
            hardwareNoiseSuppressor?.release()
        } catch (e: Throwable) {
            // Ignore
        } finally {
            hardwareNoiseSuppressor = null
        }

        try {
            hardwareEchoCanceler?.enabled = false
            hardwareEchoCanceler?.release()
        } catch (e: Throwable) {
            // Ignore
        } finally {
            hardwareEchoCanceler = null
        }

        val rec = audioRecord
        audioRecord = null
        try {
            if (rec?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                rec.stop()
            }
        } catch (e: Throwable) {
            // Ignore
        }
        try {
            rec?.release()
        } catch (e: Throwable) {
            // Ignore
        }

        val trk = audioTrack
        audioTrack = null
        try {
            if (trk?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                trk.stop()
            }
        } catch (e: Throwable) {
            // Ignore
        }
        try {
            trk?.release()
        } catch (e: Throwable) {
            // Ignore
        }
    }

    fun stopTransmission() {
        isDspRunning = false
        // Mark running as false FIRST so worker DSP loop exits immediately
        _engineState.value = _engineState.value.copy(
            isRunning = false,
            currentDb = 30f,
            peakDb = 30f,
            waveformSamples = List(32) { 0.08f }
        )

        if (_engineState.value.isRecording) {
            stopRecording()
        }

        // Gracefully stop recording and playback first so in-flight blocking read/write returns cleanly
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
        } catch (t: Throwable) {
            // Ignore
        }
        try {
            if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack?.stop()
            }
        } catch (t: Throwable) {
            // Ignore
        }

        // Cancel processing job immediately
        val job = processingJob
        processingJob = null
        job?.cancel()

        cleanupAudioHardware()
        filters.forEach { it.reset() }
        aiSpectralFilter.reset()
        digitalNoiseGate.reset()
    }

    // --- Session Recording to WAV file ---

    fun startRecording(): File? {
        if (!_engineState.value.isRunning) {
            val started = startTransmission()
            if (!started) return null
        }

        val recDir = File(context.filesDir, "audio_sessions")
        if (!recDir.exists()) recDir.mkdirs()

        val timestamp = System.currentTimeMillis()
        val file = File(recDir, "hearing_session_$timestamp.wav")
        recordingFile = file
        recordingPcmBytesWritten = 0
        recordingStartTime = System.currentTimeMillis()
        recordingPeakDb = 0f
        recordingDbSum = 0.0
        recordingSamplesCount = 0

        try {
            val fos = FileOutputStream(file)
            fos.write(ByteArray(44))
            recordingStream = fos

            _engineState.value = _engineState.value.copy(
                isRecording = true,
                recordingDurationSeconds = 0
            )

            recordingTimerJob?.cancel()
            recordingTimerJob = scope.launch(Dispatchers.Default) {
                var seconds = 0
                while (isActive && _engineState.value.isRecording) {
                    delay(1000L)
                    seconds++
                    _engineState.value = _engineState.value.copy(recordingDurationSeconds = seconds)
                }
            }

            return file
        } catch (e: Exception) {
            Log.e("HearingAidAudioEngine", "Failed to start recording file", e)
            recordingFile = null
            recordingStream = null
            return null
        }
    }

    data class RecordingResult(
        val file: File,
        val durationSeconds: Int,
        val averageDb: Float,
        val peakDb: Float
    )

    fun stopRecording(): RecordingResult? {
        recordingTimerJob?.cancel()
        recordingTimerJob = null

        val file = recordingFile ?: return null
        val fos = recordingStream ?: return null

        try {
            fos.flush()
            fos.close()

            writeWavHeader(file, recordingPcmBytesWritten, sampleRate, 1, 16)

            val durationSec = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt().coerceAtLeast(1)
            val avgDb = if (recordingSamplesCount > 0) {
                (recordingDbSum / (recordingSamplesCount / 100).coerceAtLeast(1)).toFloat().coerceIn(30f, 95f)
            } else {
                55f
            }
            val peak = recordingPeakDb.coerceIn(35f, 98f)

            _engineState.value = _engineState.value.copy(
                isRecording = false,
                recordingDurationSeconds = 0
            )

            recordingFile = null
            recordingStream = null

            return RecordingResult(
                file = file,
                durationSeconds = durationSec,
                averageDb = avgDb,
                peakDb = peak
            )
        } catch (e: Exception) {
            Log.e("HearingAidAudioEngine", "Error finalizing wav recording", e)
            return null
        }
    }

    private fun writeWavHeader(file: File, pcmDataLength: Long, sRate: Int, channels: Int, bitsPerSample: Int) {
        val totalDataLen = pcmDataLength + 36
        val byteRate = sRate * channels * bitsPerSample / 8
        val raf = RandomAccessFile(file, "rw")
        raf.seek(0)

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(totalDataLen.toInt())
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1.toShort())
        header.putShort(channels.toShort())
        header.putInt(sRate)
        header.putInt(byteRate)
        header.putShort((channels * bitsPerSample / 8).toShort())
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray())
        header.putInt(pcmDataLength.toInt())

        raf.write(header.array())
        raf.close()
    }

    // --- Audiometric Pure Tone Generation ---

    fun playCalibrationTone(frequencyHz: Int, hearingLevelDb: Int) {
        stopCalibrationTone()

        toneJob = scope.launch(Dispatchers.Default) {
            var track: AudioTrack? = null
            try {
                val amp = (10.0.pow((hearingLevelDb - 70) / 20.0) * 0.45).coerceIn(0.002, 0.5).toFloat()
                val durationMs = 800
                val numSamples = (sampleRate * durationMs / 1000)
                val toneBuffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val angle = 2.0 * PI * i * frequencyHz / sampleRate
                    val envelope = when {
                        i < 500 -> i / 500.0f
                        i > numSamples - 500 -> (numSamples - i) / 500.0f
                        else -> 1.0f
                    }
                    val sampleVal = (sin(angle) * amp * envelope * 32767).toInt().coerceIn(-32768, 32767).toShort()
                    toneBuffer[i] = sampleVal
                }

                val minBufSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val newTrack = AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                    minBufSize.coerceAtLeast(toneBuffer.size * 2),
                    AudioTrack.MODE_STATIC,
                    android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
                )

                synchronized(toneLock) {
                    track = newTrack
                    toneTrack = newTrack
                }

                newTrack.write(toneBuffer, 0, toneBuffer.size)
                newTrack.play()

                // Wait for playback to complete then automatically release resource
                delay(durationMs.toLong() + 50L)
            } catch (e: Exception) {
                Log.e("HearingAidAudioEngine", "Error playing calibration tone", e)
            } finally {
                synchronized(toneLock) {
                    try {
                        track?.stop()
                        track?.release()
                    } catch (e: Exception) {
                        // Ignore
                    }
                    if (toneTrack == track) {
                        toneTrack = null
                    }
                }
            }
        }
    }

    fun stopCalibrationTone() {
        toneJob?.cancel()
        toneJob = null
        synchronized(toneLock) {
            try {
                toneTrack?.stop()
                toneTrack?.release()
            } catch (e: Exception) {
                // Ignore
            } finally {
                toneTrack = null
            }
        }
    }

    fun playSoundCheckChime() {
        stopCalibrationTone()
        toneJob = scope.launch(Dispatchers.IO) {
            var track: AudioTrack? = null
            try {
                // Ensure media volume is up
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.85f).toInt(), 0)

                val freqs = listOf(523.25, 659.25, 783.99, 1046.50) // C5, E5, G5, C6 melodic hearing chime
                val durationMs = 1200
                val totalSamples = (sampleRate * durationMs / 1000)
                val toneBuffer = ShortArray(totalSamples)

                val noteSamples = totalSamples / freqs.size
                for (n in freqs.indices) {
                    val f = freqs[n]
                    val start = n * noteSamples
                    val end = ((n + 1) * noteSamples).coerceAtMost(totalSamples)
                    for (i in start until end) {
                        val localI = i - start
                        val angle = 2.0 * Math.PI * localI * f / sampleRate
                        val env = kotlin.math.sin(Math.PI * localI / noteSamples).toFloat()
                        val sampleVal = (kotlin.math.sin(angle) * env * 0.85f * 32767).toInt().coerceIn(-32768, 32767).toShort()
                        toneBuffer[i] = sampleVal
                    }
                }

                val minBufSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val newTrack = AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                    minBufSize.coerceAtLeast(toneBuffer.size * 2),
                    AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )

                synchronized(toneLock) {
                    track = newTrack
                    toneTrack = newTrack
                }

                newTrack.write(toneBuffer, 0, toneBuffer.size)
                newTrack.play()
                delay(durationMs.toLong() + 100L)
            } catch (e: Exception) {
                Log.e("HearingAidAudioEngine", "Error playing sound check chime", e)
            } finally {
                synchronized(toneLock) {
                    try {
                        track?.stop()
                        track?.release()
                    } catch (e: Exception) {
                        // Ignore
                    }
                    if (toneTrack == track) {
                        toneTrack = null
                    }
                }
            }
        }
    }
}
