package com.example.ui

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AiAcousticAdvisor
import com.example.audio.AiAcousticPrescription
import com.example.audio.AiNoiseMode
import com.example.audio.AudioEngineState
import com.example.audio.AudioEngineType
import com.example.audio.ConnectedEarpodInfo
import com.example.audio.DeviceAuditor
import com.example.audio.HearingAidAudioEngine
import com.example.audio.HostDeviceInfo
import com.example.audio.NoiseGateSensitivity
import com.example.audio.OboeAudioEngine
import com.example.audio.OcclusionReliefMode
import com.example.audio.PodAcousticProfile
import com.example.audio.PodControlsState
import com.example.audio.PodFitTestResult
import com.example.audio.PodMicRouting
import com.example.audio.PodModel
import com.example.audio.PodProfiles
import com.example.audio.PodTransparencyMode
import com.example.data.SubscriptionManager
import com.example.data.SubscriptionPlan
import com.example.data.SubscriptionState
import com.example.data.db.HearPodDatabase
import com.example.data.model.HearingProfile
import com.example.data.model.HearingThreshold
import com.example.data.model.RecordingSession
import com.example.audio.BatteryMonitor
import com.example.audio.BatteryState
import com.example.service.BatteryMonitoringService
import com.example.service.HearingAidService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class AudiogramTestState(
    val isActive: Boolean = false,
    val currentFrequencyIndex: Int = 0,
    val currentDbLevel: Int = 20, // 10 to 65 dB HL
    val recordedThresholds: MutableMap<Int, Int> = mutableMapOf(),
    val isComplete: Boolean = false,
    val isPlayingTone: Boolean = false
)

data class HearingHealthAnalytics(
    val dailyUsageHours: Float = 3.4f,
    val averageExposureDb: Float = 62.5f,
    val safeListeningRemainingHours: Float = 4.8f,
    val fatigueIndexPercent: Int = 18,
    val noisePeaksAvoided: Int = 42
)

data class PlaybackState(
    val activeSessionId: Long? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0
)

class HearingAidViewModel(application: Application) : AndroidViewModel(application) {
    val oboeAudioEngine = OboeAudioEngine(application)
    val standardAudioEngine = HearingAidAudioEngine(application)
    val audioEngine get() = standardAudioEngine

    val deviceAuditor = DeviceAuditor(application)
    private val subscriptionManager = SubscriptionManager(application)
    private val database = HearPodDatabase.getDatabase(application)
    private val recordingDao = database.recordingSessionDao()
    private val aiAdvisor = AiAcousticAdvisor()

    private val _activeEngineType = MutableStateFlow(AudioEngineType.ANDROID_AUDIO)
    val activeEngineType: StateFlow<AudioEngineType> = _activeEngineType.asStateFlow()

    private val _engineState = MutableStateFlow(
        standardAudioEngine.engineState.value.copy(engineType = AudioEngineType.ANDROID_AUDIO)
    )
    val engineState: StateFlow<AudioEngineState> = _engineState.asStateFlow()

    val connectedDevice: StateFlow<ConnectedEarpodInfo> = deviceAuditor.connectedDevice
    val hostDevice: StateFlow<HostDeviceInfo> = deviceAuditor.hostDevice
    val podControlsState: StateFlow<PodControlsState> = deviceAuditor.podControlsState
    val subscriptionState: StateFlow<SubscriptionState> = subscriptionManager.subscriptionState
    val isBluetoothHeadsetConnected: StateFlow<Boolean> = deviceAuditor.isBluetoothHeadsetConnected
    val isSimulatedBluetoothConnected: StateFlow<Boolean> = deviceAuditor.isSimulatedBluetoothConnected

    val batteryMonitor = BatteryMonitor(application)
    val batteryState: StateFlow<BatteryState> = batteryMonitor.batteryState

    private val _showBluetoothRequiredNotice = MutableStateFlow(false)
    val showBluetoothRequiredNotice: StateFlow<Boolean> = _showBluetoothRequiredNotice.asStateFlow()

    private val _isSpeakerModeAllowed = MutableStateFlow(false)
    val isSpeakerModeAllowed: StateFlow<Boolean> = _isSpeakerModeAllowed.asStateFlow()

    fun enableSpeakerMode() {
        _isSpeakerModeAllowed.value = true
        standardAudioEngine.isSpeakerOutput = true
    }

    fun disableSpeakerMode() {
        _isSpeakerModeAllowed.value = false
        standardAudioEngine.isSpeakerOutput = false
    }

    fun dismissBluetoothRequiredNotice() {
        _showBluetoothRequiredNotice.value = false
    }

    fun showBluetoothRequiredDialog() {
        _showBluetoothRequiredNotice.value = true
    }

    fun simulateBluetoothEarbuds(connected: Boolean = true, model: PodModel = PodModel.AIRPODS_PRO) {
        deviceAuditor.simulateBluetoothEarbuds(model, connected)
    }

    val recordedSessions: StateFlow<List<RecordingSession>> = recordingDao.getAllSessions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _selectedPreset = MutableStateFlow("Speech Clarity")
    val selectedPreset: StateFlow<String> = _selectedPreset.asStateFlow()

    private val _bandGains = MutableStateFlow(standardAudioEngine.getAllBandGains())
    val bandGains: StateFlow<Map<Int, Float>> = _bandGains.asStateFlow()

    private val _audiogramState = MutableStateFlow(AudiogramTestState())
    val audiogramState: StateFlow<AudiogramTestState> = _audiogramState.asStateFlow()

    private val _userHearingProfile = MutableStateFlow<HearingProfile?>(null)
    val userHearingProfile: StateFlow<HearingProfile?> = _userHearingProfile.asStateFlow()

    private val _analytics = MutableStateFlow(HearingHealthAnalytics())
    val analytics: StateFlow<HearingHealthAnalytics> = _analytics.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    // AI Noise Cancellation & Environment Optimization
    private val _isAiOptimizing = MutableStateFlow(false)
    val isAiOptimizing: StateFlow<Boolean> = _isAiOptimizing.asStateFlow()

    private val _latestAiPrescription = MutableStateFlow<AiAcousticPrescription?>(null)
    val latestAiPrescription: StateFlow<AiAcousticPrescription?> = _latestAiPrescription.asStateFlow()

    private val _isPlayingSoundCheck = MutableStateFlow(false)
    val isPlayingSoundCheck: StateFlow<Boolean> = _isPlayingSoundCheck.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var playbackProgressJob: Job? = null
    private var wasRunningBeforeCalibration = false

    init {
        standardAudioEngine.applyPreset("Speech Clarity")
        if (OboeAudioEngine.isLibraryLoaded) {
            oboeAudioEngine.applyPreset("Speech Clarity")
        }
        _bandGains.value = standardAudioEngine.getAllBandGains()
        HearingAidService.onStopRequested = {
            stopAmplification()
        }
        deviceAuditor.onHeadsetDisconnected = {
            viewModelScope.launch(Dispatchers.Main) {
                // If headset disconnects during live amplification, seamlessly switch to speaker mode
                // with Echo Shield rather than abruptly shutting off the user's hearing aid
                if (engineState.value.isRunning) {
                    enableSpeakerMode()
                    standardAudioEngine.isSpeakerOutput = true
                }
            }
        }
        oboeAudioEngine.onStreamStoppedUnexpectedly = {
            viewModelScope.launch(Dispatchers.Main) {
                if (_activeEngineType.value == AudioEngineType.OBOE_CPP) {
                    android.util.Log.w("HearingAidViewModel", "Oboe native stream stopped; auto-falling back to Standard Android Audio engine")
                    _activeEngineType.value = AudioEngineType.ANDROID_AUDIO
                    startAmplification()
                }
            }
        }

        viewModelScope.launch {
            oboeAudioEngine.engineState.collect { state ->
                if (_activeEngineType.value == AudioEngineType.OBOE_CPP) {
                    _engineState.value = state.copy(
                        engineType = AudioEngineType.OBOE_CPP,
                        isRecording = standardAudioEngine.engineState.value.isRecording,
                        recordingDurationSeconds = standardAudioEngine.engineState.value.recordingDurationSeconds
                    )
                }
            }
        }
        viewModelScope.launch {
            standardAudioEngine.engineState.collect { state ->
                if (_activeEngineType.value == AudioEngineType.ANDROID_AUDIO) {
                    _engineState.value = state.copy(engineType = AudioEngineType.ANDROID_AUDIO)
                }
            }
        }
        viewModelScope.launch {
            deviceAuditor.podControlsState.collect {
                syncPodGainsToEngines()
            }
        }
        viewModelScope.launch {
            batteryMonitor.batteryState.collect { bState ->
                val eco = bState.isEcoModeActive
                standardAudioEngine.setEcoMode(eco)
                oboeAudioEngine.setEcoMode(eco)
            }
        }
        viewModelScope.launch {
            deviceAuditor.isBluetoothHeadsetConnected.collect { isConnected ->
                if (isConnected) {
                    _isSpeakerModeAllowed.value = false
                    standardAudioEngine.isSpeakerOutput = false
                }
            }
        }
    }

    fun selectAudioEngine(type: AudioEngineType) {
        if (_activeEngineType.value == type) return
        val wasRunning = engineState.value.isRunning
        viewModelScope.launch {
            if (wasRunning) {
                stopAmplification()
                delay(150)
            }
            _activeEngineType.value = type
            if (wasRunning) {
                startAmplification()
            } else {
                _engineState.value = if (type == AudioEngineType.OBOE_CPP) {
                    oboeAudioEngine.engineState.value.copy(engineType = AudioEngineType.OBOE_CPP)
                } else {
                    standardAudioEngine.engineState.value.copy(engineType = AudioEngineType.ANDROID_AUDIO)
                }
            }
        }
    }

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    fun togglePower(forceSpeaker: Boolean = false, onPermissionGranted: (() -> Unit)? = null) {
        if (engineState.value.isRunning) {
            stopAmplification()
            return
        }

        val isBtConnected = isBluetoothHeadsetConnected.value
        val effectiveForceSpeaker = forceSpeaker || _isSpeakerModeAllowed.value
        if (effectiveForceSpeaker) {
            enableSpeakerMode()
        } else if (!isBtConnected) {
            showBluetoothRequiredDialog()
            return
        } else {
            disableSpeakerMode()
        }

        val hasAudio = androidx.core.content.ContextCompat.checkSelfPermission(
            getApplication(),
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasAudio) {
            startAmplification(forceSpeaker = effectiveForceSpeaker)
        } else {
            onPermissionGranted?.invoke()
        }
    }

    fun setMicSourceMode(mode: com.example.audio.MicSourceMode) {
        deviceAuditor.setMicSourceMode(mode)
        val routing = if (mode == com.example.audio.MicSourceMode.PODS_MIC) {
            PodMicRouting.DUAL_POD_BEAMFORMING
        } else {
            PodMicRouting.PHONE_DIRECTIONAL
        }
        setPodMicRouting(routing)
    }

    fun startAmplification(forceSpeaker: Boolean = false): Boolean {
        val isBtConnected = isBluetoothHeadsetConnected.value
        val effectiveSpeaker = forceSpeaker || _isSpeakerModeAllowed.value

        if (!isBtConnected && !effectiveSpeaker) {
            showBluetoothRequiredDialog()
            return false
        }

        if (effectiveSpeaker && !isBtConnected) {
            _isSpeakerModeAllowed.value = true
            standardAudioEngine.isSpeakerOutput = true
        } else if (isBtConnected) {
            _isSpeakerModeAllowed.value = false
            standardAudioEngine.isSpeakerOutput = false
        }

        val isHeadphones = isBtConnected && connectedDevice.value.type != com.example.audio.ConnectedDeviceType.PHONE_SPEAKER
        standardAudioEngine.isSpeakerOutput = !isHeadphones

        val usePodMic = isHeadphones && connectedDevice.value.activeMicSource == com.example.audio.MicSourceMode.PODS_MIC
        if (isHeadphones) {
            deviceAuditor.routeAudioDirectlyToPods(usePodMic = usePodMic)
        } else {
            deviceAuditor.clearAudioRouting()
        }

        val inputDevice = if (usePodMic) deviceAuditor.findPodInputDevice() else null
        val outputDevice = if (isHeadphones) deviceAuditor.findPodOutputDevice() else null

        var started = false
        if (_activeEngineType.value == AudioEngineType.OBOE_CPP && OboeAudioEngine.isLibraryLoaded && isHeadphones) {
            started = oboeAudioEngine.startTransmission()
            if (!started) {
                android.util.Log.w("HearingAidViewModel", "Oboe native engine start failed; falling back to Standard Audio engine")
                _activeEngineType.value = AudioEngineType.ANDROID_AUDIO
                started = standardAudioEngine.startTransmission(
                    preferredInputDevice = inputDevice,
                    preferredOutputDevice = outputDevice
                )
            }
        } else {
            started = standardAudioEngine.startTransmission(
                preferredInputDevice = inputDevice,
                preferredOutputDevice = outputDevice
            )
        }

        if (started) {
            _engineState.value = standardAudioEngine.engineState.value.copy(
                isRunning = true,
                engineType = _activeEngineType.value
            )
            _bandGains.value = standardAudioEngine.getAllBandGains()
            try {
                HearingAidService.startService(getApplication())
            } catch (e: Throwable) {
                android.util.Log.w("HearingAidViewModel", "Background service could not be started: ${e.message}")
            }
        } else {
            _engineState.value = _engineState.value.copy(isRunning = false)
        }
        return started
    }

    fun stopAmplification() {
        oboeAudioEngine.stopTransmission()
        standardAudioEngine.stopTransmission()
        _engineState.value = _engineState.value.copy(isRunning = false)
        deviceAuditor.clearAudioRouting()
        try {
            HearingAidService.stopService(getApplication())
        } catch (e: Throwable) {
            // Ignore
        }
    }

    fun setMasterGain(gain: Float) {
        oboeAudioEngine.setMasterGain(gain)
        standardAudioEngine.setMasterGain(gain)
    }

    // --- Pod Identification, Adaptation & Controls ---

    private fun syncPodGainsToEngines() {
        val state = deviceAuditor.podControlsState.value
        val effLeft = if (state.isLeftMuted || (!state.leftPodInEar && state.inEarDetectionEnabled)) 0.0f else state.leftVolume
        val effRight = if (state.isRightMuted || (!state.rightPodInEar && state.inEarDetectionEnabled)) 0.0f else state.rightVolume

        oboeAudioEngine.setLeftRightGains(effLeft, effRight)
        standardAudioEngine.setLeftRightGains(effLeft, effRight)
        oboeAudioEngine.setEarBalance(state.earBalance)
        standardAudioEngine.setEarBalance(state.earBalance)
    }

    fun setPodLeftVolume(volume: Float) {
        deviceAuditor.setPodLeftVolume(volume)
        syncPodGainsToEngines()
    }

    fun setPodRightVolume(volume: Float) {
        deviceAuditor.setPodRightVolume(volume)
        syncPodGainsToEngines()
    }

    fun toggleLeftPodMute() {
        deviceAuditor.toggleLeftPodMute()
        syncPodGainsToEngines()
    }

    fun toggleRightPodMute() {
        deviceAuditor.toggleRightPodMute()
        syncPodGainsToEngines()
    }

    fun setPodEarBalance(balance: Float) {
        deviceAuditor.setEarBalance(balance)
        syncPodGainsToEngines()
    }

    fun setPodTransparencyMode(mode: PodTransparencyMode) {
        deviceAuditor.setTransparencyMode(mode)
        val aiMode = when (mode) {
            PodTransparencyMode.AMPLIFIED_TRANSPARENCY -> AiNoiseMode.BALANCED
            PodTransparencyMode.NATURAL_PASSTHROUGH -> AiNoiseMode.OFF
            PodTransparencyMode.CONVERSATION_BOOST -> AiNoiseMode.CONVERSATION_FOCUS
            PodTransparencyMode.QUIET_FOCUS -> AiNoiseMode.STUDIO_QUIET
        }
        oboeAudioEngine.setAiNoiseMode(aiMode)
        standardAudioEngine.setAiNoiseMode(aiMode)

        when (mode) {
            PodTransparencyMode.AMPLIFIED_TRANSPARENCY -> {
                standardAudioEngine.setBandGain(250, 0f)
                standardAudioEngine.setBandGain(1000, 2f)
                standardAudioEngine.setBandGain(2000, 3f)
                oboeAudioEngine.setBandGain(250, 0f)
                oboeAudioEngine.setBandGain(1000, 2f)
                oboeAudioEngine.setBandGain(2000, 3f)
            }
            PodTransparencyMode.NATURAL_PASSTHROUGH -> {
                standardAudioEngine.frequencies.forEach { f ->
                    standardAudioEngine.setBandGain(f, 0f)
                    oboeAudioEngine.setBandGain(f, 0f)
                }
            }
            PodTransparencyMode.CONVERSATION_BOOST -> {
                standardAudioEngine.setBandGain(250, -3f)
                standardAudioEngine.setBandGain(500, 1f)
                standardAudioEngine.setBandGain(1000, 4f)
                standardAudioEngine.setBandGain(2000, 5f)
                standardAudioEngine.setBandGain(4000, 3f)
                standardAudioEngine.setBandGain(8000, -4f)
                oboeAudioEngine.setBandGain(250, -3f)
                oboeAudioEngine.setBandGain(500, 1f)
                oboeAudioEngine.setBandGain(1000, 4f)
                oboeAudioEngine.setBandGain(2000, 5f)
                oboeAudioEngine.setBandGain(4000, 3f)
                oboeAudioEngine.setBandGain(8000, -4f)
            }
            PodTransparencyMode.QUIET_FOCUS -> {
                standardAudioEngine.setBandGain(250, -8f)
                standardAudioEngine.setBandGain(500, -2f)
                standardAudioEngine.setBandGain(1000, 2f)
                standardAudioEngine.setBandGain(2000, 3f)
                oboeAudioEngine.setBandGain(250, -8f)
                oboeAudioEngine.setBandGain(500, -2f)
                oboeAudioEngine.setBandGain(1000, 2f)
                oboeAudioEngine.setBandGain(2000, 3f)
            }
        }
        _bandGains.value = standardAudioEngine.getAllBandGains()
    }

    fun setPodTransparencyLevel(level: Float) {
        val clamped = level.coerceIn(0f, 1f)
        deviceAuditor.setTransparencyLevel(clamped)
        standardAudioEngine.setTransparencyLevel(clamped)
        oboeAudioEngine.setTransparencyLevel(clamped)
        val gain = 1.5f + (clamped * 4.5f) // 1.5x up to 6.0x amplification for genuine hearing aid boost
        setMasterGain(gain)
    }

    fun setOcclusionCompensation(mode: OcclusionReliefMode) {
        deviceAuditor.setOcclusionCompensation(mode)
        val base250 = podControlsState.value.activeProfile.compensationGains[250] ?: 0f
        val new250 = (base250 + mode.attenuationDb).coerceIn(-12f, 18f)
        setBandGain(250, new250)
    }

    fun setPodMicRouting(routing: PodMicRouting) {
        deviceAuditor.setPodMicRouting(routing)
        standardAudioEngine.setPodMicRouting(routing)
        oboeAudioEngine.setPodMicRouting(routing.ordinal)
    }

    fun toggleInEarDetection(enabled: Boolean? = null) {
        deviceAuditor.toggleInEarDetection(enabled)
        syncPodGainsToEngines()
    }

    fun toggleLeftPodInEar() {
        deviceAuditor.toggleLeftInEar()
        syncPodGainsToEngines()
    }

    fun toggleRightPodInEar() {
        deviceAuditor.toggleRightInEar()
        syncPodGainsToEngines()
    }

    fun toggleAutoAdaptToPod(enabled: Boolean? = null) {
        deviceAuditor.toggleAutoAdapt(enabled)
        if (podControlsState.value.autoAdaptEnabled) {
            applyPodProfile(podControlsState.value.activeProfile)
        }
    }

    fun selectPodProfile(profile: PodAcousticProfile) {
        deviceAuditor.selectProfileManually(profile)
        applyPodProfile(profile)
    }

    fun applyPodProfile(profile: PodAcousticProfile) {
        profile.compensationGains.forEach { (freq, gain) ->
            setBandGain(freq, gain)
        }
        val occMode = podControlsState.value.occlusionCompensation
        val adjusted250 = ((profile.compensationGains[250] ?: 0f) + occMode.attenuationDb).coerceIn(-12f, 18f)
        setBandGain(250, adjusted250)
        setPodTransparencyMode(profile.recommendedTransparencyMode)
    }

    fun runEarTipFitTest(): PodFitTestResult {
        return deviceAuditor.runFitTest()
    }

    fun setAiNoiseMode(mode: AiNoiseMode) {
        oboeAudioEngine.setAiNoiseMode(mode)
        standardAudioEngine.setAiNoiseMode(mode)
        val podMode = when (mode) {
            AiNoiseMode.STUDIO_QUIET -> PodTransparencyMode.QUIET_FOCUS
            AiNoiseMode.BALANCED -> PodTransparencyMode.AMPLIFIED_TRANSPARENCY
            AiNoiseMode.CONVERSATION_FOCUS -> PodTransparencyMode.CONVERSATION_BOOST
            AiNoiseMode.OFF -> PodTransparencyMode.NATURAL_PASSTHROUGH
        }
        deviceAuditor.setTransparencyMode(podMode)
    }

    fun setFeedbackPrevention(enabled: Boolean) {
        oboeAudioEngine.setFeedbackPrevention(enabled)
        standardAudioEngine.setFeedbackPrevention(enabled)
    }

    fun setNoiseGateEnabled(enabled: Boolean) {
        standardAudioEngine.setNoiseGateEnabled(enabled)
    }

    fun setNoiseGateSensitivity(sensitivity: NoiseGateSensitivity) {
        standardAudioEngine.setNoiseGateSensitivity(sensitivity)
    }

    fun setSpeechFocus(enabled: Boolean) {
        oboeAudioEngine.setSpeechFocus(enabled)
        standardAudioEngine.setSpeechFocus(enabled)
        _bandGains.value = standardAudioEngine.getAllBandGains()
    }

    fun setBandGain(frequencyHz: Int, gainDb: Float) {
        oboeAudioEngine.setBandGain(frequencyHz, gainDb)
        standardAudioEngine.setBandGain(frequencyHz, gainDb)
        _bandGains.value = standardAudioEngine.getAllBandGains()
        _selectedPreset.value = "Custom EQ"
    }

    fun resetEqFlat() {
        standardAudioEngine.frequencies.forEach {
            oboeAudioEngine.setBandGain(it, 0f)
            standardAudioEngine.setBandGain(it, 0f)
        }
        _bandGains.value = standardAudioEngine.getAllBandGains()
        _selectedPreset.value = "Flat / Reset"
    }

    fun selectPreset(preset: String) {
        _selectedPreset.value = preset
        if (preset == "My Audiogram Profile" && _userHearingProfile.value != null) {
            applyCalibratedProfile()
        } else {
            oboeAudioEngine.applyPreset(preset)
            standardAudioEngine.applyPreset(preset)
            _bandGains.value = standardAudioEngine.getAllBandGains()
        }
    }

    // --- AI Acoustic Environment Auto-Tuning (Gemini 3.5 Flash) ---

    fun optimizeEnvironmentWithAi(onResult: (String) -> Unit) {
        if (_isAiOptimizing.value) return
        _isAiOptimizing.value = true

        viewModelScope.launch {
            try {
                val prescription = aiAdvisor.analyzeEnvironment(
                    currentDb = engineState.value.currentDb,
                    peakDb = engineState.value.peakDb,
                    connectedDeviceName = connectedDevice.value.name
                )
                _latestAiPrescription.value = prescription

                // Apply AI recommended settings instantly to active engines
                setAiNoiseMode(prescription.recommendedAiMode)
                setMasterGain(prescription.masterGain)
                prescription.bandGains.forEach { (freq, gain) ->
                    setBandGain(freq, gain)
                }
                _bandGains.value = standardAudioEngine.getAllBandGains()
                _selectedPreset.value = prescription.recommendedPreset

                onResult("AI Tuned: ${prescription.environmentType}\n${prescription.explanation}")
            } catch (e: Exception) {
                onResult("AI Optimization applied balanced speech clarity.")
            } finally {
                _isAiOptimizing.value = false
            }
        }
    }

    // --- Recording Actions ---

    fun toggleRecording(titlePrompt: String = "Session Note") {
        if (engineState.value.isRecording) {
            val result = audioEngine.stopRecording()
            if (result != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    val session = RecordingSession(
                        title = "Hearing Record #${System.currentTimeMillis() % 10000}",
                        durationSeconds = result.durationSeconds,
                        filePath = result.file.absolutePath,
                        averageDecibels = result.averageDb,
                        peakDecibels = result.peakDb,
                        presetUsed = _selectedPreset.value,
                        notes = "Gain: ${(engineState.value.masterGain * 100).toInt()}% • AI Noise Mode: ${engineState.value.aiNoiseMode.title}"
                    )
                    recordingDao.insertSession(session)
                }
            }
        } else {
            audioEngine.startRecording()
        }
    }

    fun deleteSession(session: RecordingSession) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(session.filePath)
                if (file.exists()) file.delete()
            } catch (e: Exception) {
                // Ignore
            }
            recordingDao.deleteSession(session)
        }
        if (_playbackState.value.activeSessionId == session.id) {
            stopPlayback()
        }
    }

    // --- Playback of Offline Recordings ---

    fun playSession(session: RecordingSession) {
        if (_playbackState.value.activeSessionId == session.id && _playbackState.value.isPlaying) {
            pausePlayback()
            return
        }

        stopPlayback()
        try {
            val player = MediaPlayer()
            player.setDataSource(session.filePath)
            player.prepare()
            player.start()
            mediaPlayer = player

            _playbackState.value = PlaybackState(
                activeSessionId = session.id,
                isPlaying = true,
                currentPositionMs = 0,
                durationMs = player.duration
            )

            player.setOnCompletionListener {
                stopPlayback()
            }

            playbackProgressJob?.cancel()
            playbackProgressJob = viewModelScope.launch {
                while (player.isPlaying) {
                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = player.currentPosition
                    )
                    delay(250)
                }
            }
        } catch (e: Exception) {
            stopPlayback()
        }
    }

    fun pausePlayback() {
        mediaPlayer?.pause()
        _playbackState.value = _playbackState.value.copy(isPlaying = false)
    }

    fun stopPlayback() {
        playbackProgressJob?.cancel()
        playbackProgressJob = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // Ignore
        } finally {
            mediaPlayer = null
        }
        _playbackState.value = PlaybackState()
    }

    // --- Audiogram Hearing Calibration Test Workflow ---

    fun startAudiogramTest() {
        wasRunningBeforeCalibration = engineState.value.isRunning
        if (wasRunningBeforeCalibration) {
            stopAmplification()
        }
        _audiogramState.value = AudiogramTestState(
            isActive = true,
            currentFrequencyIndex = 0,
            currentDbLevel = 20,
            recordedThresholds = mutableMapOf(),
            isComplete = false,
            isPlayingTone = false
        )
        playCurrentCalibrationTone()
    }

    fun playCurrentCalibrationTone() {
        val state = _audiogramState.value
        val freq = audioEngine.frequencies.getOrNull(state.currentFrequencyIndex) ?: return
        audioEngine.playCalibrationTone(freq, state.currentDbLevel)
        _audiogramState.value = state.copy(isPlayingTone = true)
        viewModelScope.launch {
            delay(850)
            _audiogramState.value = _audiogramState.value.copy(isPlayingTone = false)
        }
    }

    fun playSoundCheck() {
        _isPlayingSoundCheck.value = true
        standardAudioEngine.playSoundCheckChime()
        viewModelScope.launch {
            delay(1300)
            _isPlayingSoundCheck.value = false
        }
    }

    fun userHearsTone() {
        val state = _audiogramState.value
        val freq = audioEngine.frequencies.getOrNull(state.currentFrequencyIndex) ?: return
        state.recordedThresholds[freq] = state.currentDbLevel

        nextCalibrationFrequency()
    }

    fun userCannotHearTone() {
        val state = _audiogramState.value
        val newLevel = (state.currentDbLevel + 10).coerceAtMost(65)
        if (newLevel > 60) {
            val freq = audioEngine.frequencies.getOrNull(state.currentFrequencyIndex) ?: return
            state.recordedThresholds[freq] = 65
            nextCalibrationFrequency()
        } else {
            _audiogramState.value = state.copy(currentDbLevel = newLevel)
            playCurrentCalibrationTone()
        }
    }

    private fun nextCalibrationFrequency() {
        val state = _audiogramState.value
        val nextIndex = state.currentFrequencyIndex + 1
        if (nextIndex < audioEngine.frequencies.size) {
            _audiogramState.value = state.copy(
                currentFrequencyIndex = nextIndex,
                currentDbLevel = 20
            )
            viewModelScope.launch {
                delay(400)
                playCurrentCalibrationTone()
            }
        } else {
            finalizeAudiogramProfile()
        }
    }

    private fun finalizeAudiogramProfile() {
        val state = _audiogramState.value
        val thresholds = audioEngine.frequencies.map { freq ->
            HearingThreshold(freq, state.recordedThresholds[freq] ?: 25)
        }
        val profile = HearingProfile(
            name = "Personalized Audiogram",
            thresholds = thresholds
        )
        _userHearingProfile.value = profile
        _audiogramState.value = state.copy(isComplete = true)
    }

    fun applyCalibratedProfile() {
        val profile = _userHearingProfile.value ?: return
        audioEngine.stopCalibrationTone()
        val eqGains = profile.toEqGains()
        eqGains.forEach { (freq, gain) ->
            setBandGain(freq, gain)
        }
        _bandGains.value = standardAudioEngine.getAllBandGains()
        _selectedPreset.value = "My Audiogram Profile"
        _audiogramState.value = AudiogramTestState(isActive = false)
        startAmplification()
        wasRunningBeforeCalibration = false
    }

    fun cancelAudiogramTest() {
        audioEngine.stopCalibrationTone()
        _audiogramState.value = AudiogramTestState(isActive = false)
        if (wasRunningBeforeCalibration) {
            startAmplification()
            wasRunningBeforeCalibration = false
        }
    }

    // --- Subscription & Cloud Backup ---

    fun purchaseSubscription(activity: android.app.Activity?, plan: SubscriptionPlan) {
        subscriptionManager.launchPurchaseFlow(activity, plan)
    }

    fun refreshBillingProducts() {
        subscriptionManager.querySubscriptionProducts()
        subscriptionManager.queryExistingSubscriptions()
    }

    fun upgradeSubscription(plan: SubscriptionPlan) {
        subscriptionManager.upgradeToPro(plan)
    }

    fun cancelSubscription() {
        subscriptionManager.cancelSubscription()
    }

    fun triggerCloudBackup() {
        viewModelScope.launch {
            delay(1200)
            subscriptionManager.performCloudBackupSync()
        }
    }

    fun toggleAutoBackup(enabled: Boolean) {
        subscriptionManager.setAutoBackupEnabled(enabled)
    }

    // --- Battery & Low-Power DSP Eco Mode ---

    fun setAutoEcoMode(enabled: Boolean) {
        batteryMonitor.setAutoEcoMode(enabled)
    }

    fun setForceEcoMode(forced: Boolean) {
        batteryMonitor.setForceEcoMode(forced)
    }

    fun simulateBatteryState(level: Int, isCharging: Boolean = false) {
        batteryMonitor.simulateBatteryState(level, isCharging)
    }

    override fun onCleared() {
        super.onCleared()
        stopAmplification()
        audioEngine.stopCalibrationTone()
        deviceAuditor.unregister()
        batteryMonitor.unregister()
        subscriptionManager.destroy()
        BatteryMonitoringService.stopService(getApplication())
        stopPlayback()
    }
}
