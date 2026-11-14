package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun aiSpectralNoiseFilter_cleansAudioWithoutArtifacts() {
    val filter = com.example.audio.AiSpectralNoiseFilter(sampleRate = 48000f)
    filter.mode = com.example.audio.AiNoiseMode.STUDIO_QUIET

    val frame = FloatArray(384) { i ->
      // Mixed speech + low AC hum (60Hz)
      (kotlin.math.sin(2.0 * Math.PI * 60.0 * i / 48000.0) * 0.1f +
       kotlin.math.sin(2.0 * Math.PI * 1000.0 * i / 48000.0) * 0.3f).toFloat()
    }

    filter.processFrame(frame, frame.size)

    for (sample in frame) {
      assertFalse("Sample should not be NaN", sample.isNaN())
      assertFalse("Sample should not be Infinite", sample.isInfinite())
      assertTrue("Sample should be bounded [-1.0, 1.0]", sample in -1.0f..1.0f)
    }
  }

  @Test
  fun aiSpectralNoiseFilter_preservesVoice() {
    val filter = com.example.audio.AiSpectralNoiseFilter(sampleRate = 48000f)
    filter.mode = com.example.audio.AiNoiseMode.CONVERSATION_FOCUS

    val voiceFrame = FloatArray(384) { i ->
      (kotlin.math.sin(2.0 * Math.PI * 1200.0 * i / 48000.0) * 0.25f).toFloat()
    }

    filter.processFrame(voiceFrame, voiceFrame.size)
    var energy = 0f
    for (s in voiceFrame) {
      energy += kotlin.math.abs(s)
    }
    assertTrue("Voice energy must remain strong and clear", energy > 10f)
  }

  @Test
  fun oboeJniInterface_mockImplementationAllowsRealtimeControl() {
    var started = false
    var currentMasterGain = 1.0f
    val gains = mutableMapOf<Int, Float>()

    val mockJni = object : com.example.audio.IOboeAudioEngineJni {
      override fun createEngine(): Long = 0x12345678L
      override fun destroyEngine(engineHandle: Long) {}
      override fun startEngine(engineHandle: Long): Boolean {
        started = true
        return true
      }
      override fun stopEngine(engineHandle: Long) {
        started = false
      }
      override fun isRunning(engineHandle: Long): Boolean = started
      override fun setMasterGain(engineHandle: Long, gain: Float) {
        currentMasterGain = gain
      }
      override fun setBandGain(engineHandle: Long, freq: Int, gainDb: Float) {
        gains[freq] = gainDb
      }
      override fun getBandGain(engineHandle: Long, freq: Int): Float = gains[freq] ?: 0f
      override fun setAiNoiseMode(engineHandle: Long, mode: Int) {}
      override fun setFeedbackPrevention(engineHandle: Long, enabled: Boolean) {}
      override fun getCurrentDb(engineHandle: Long): Float = 42.0f
      override fun getPeakDb(engineHandle: Long): Float = 55.0f
      override fun getLatencyMs(engineHandle: Long): Float = 3.6f
      override fun getWaveform(engineHandle: Long, outArray: FloatArray) {
        for (i in outArray.indices) outArray[i] = 0.5f
      }
      override fun setEarBalance(engineHandle: Long, balance: Float) {}
      override fun setLeftRightGains(engineHandle: Long, left: Float, right: Float) {}
      override fun getEarBalance(engineHandle: Long): Float = 0.0f
      override fun setMicSensitivity(engineHandle: Long, sensitivity: Float) {}
      override fun getMicSensitivity(engineHandle: Long): Float = 1.0f
      override fun getSampleRate(engineHandle: Long): Int = 48000
      override fun getBufferSizeFrames(engineHandle: Long): Int = 192
      override fun isAAudio(engineHandle: Long): Boolean = true
      override fun setTransparencyLevel(engineHandle: Long, level: Float) {}
    }

    assertEquals(0x12345678L, mockJni.createEngine())
    assertTrue(mockJni.startEngine(0x12345678L))
    assertTrue(mockJni.isRunning(0x12345678L))
    mockJni.setMasterGain(0x12345678L, 1.25f)
    assertEquals(1.25f, currentMasterGain, 0.001f)
    mockJni.setBandGain(0x12345678L, 1000, 4.0f)
    assertEquals(4.0f, mockJni.getBandGain(0x12345678L, 1000), 0.001f)
    assertEquals(3.6f, mockJni.getLatencyMs(0x12345678L), 0.001f)
    assertTrue(mockJni.isAAudio(0x12345678L))
    mockJni.stopEngine(0x12345678L)
    assertFalse(mockJni.isRunning(0x12345678L))
  }

  @Test
  fun oboeStreamDiagnostics_formatsCorrectly() {
    val diag = com.example.audio.OboeStreamDiagnostics(
      isRunning = true,
      sampleRate = 48000,
      bufferSizeFrames = 192,
      isAAudio = true,
      latencyMs = 3.8f,
      currentDb = 45.0f,
      peakDb = 58.0f,
      masterGain = 1.15f
    )
    assertTrue(diag.isAAudio)
    assertEquals("AAudio (Kernel MMAP Direct)", diag.apiName)
    assertEquals(3.8f, diag.latencyMs, 0.01f)
    assertEquals(48000, diag.sampleRate)
  }

  @Test
  fun podProfiles_adaptsAcousticCurveForAirPodsPro() {
    val profile = com.example.audio.PodProfiles.forModel(com.example.audio.PodModel.AIRPODS_PRO)
    assertEquals("airpods_pro", profile.id)
    assertEquals("AirPods Pro Acoustic Profile", profile.name)
    assertEquals(-3.0f, profile.antiOcclusionDb, 0.01f)
    assertTrue("High frequencies should have treble boost for clarity", (profile.compensationGains[4000] ?: 0f) > 2.0f)
    assertEquals(com.example.audio.PodTransparencyMode.AMPLIFIED_TRANSPARENCY, profile.recommendedTransparencyMode)
  }

  @Test
  fun podProfiles_adaptsAcousticCurveForGalaxyBuds() {
    val profile = com.example.audio.PodProfiles.forModel(com.example.audio.PodModel.GALAXY_BUDS_PRO)
    assertEquals("galaxy_buds_pro", profile.id)
    assertTrue("Sub-bass should be attenuated to reduce boominess", (profile.compensationGains[250] ?: 0f) < 0f)
    assertTrue("Vocal presence should be boosted", (profile.compensationGains[2000] ?: 0f) >= 3.0f)
  }

  @Test
  fun podProfiles_adaptsAcousticCurveForPixelBuds() {
    val profile = com.example.audio.PodProfiles.forModel(com.example.audio.PodModel.PIXEL_BUDS_PRO)
    assertEquals("pixel_buds_pro", profile.id)
    assertEquals(com.example.audio.PodTransparencyMode.CONVERSATION_BOOST, profile.recommendedTransparencyMode)
  }

  @Test
  fun podProfiles_phoneSpeakerAppliesAntiFeedbackCut() {
    val profile = com.example.audio.PodProfiles.forModel(com.example.audio.PodModel.PHONE_SPEAKER)
    assertEquals("phone_speaker", profile.id)
    assertTrue("Speaker mode must notch vocal frequencies to avoid howling feedback loop", (profile.compensationGains[1000] ?: 0f) <= -4.0f)
    assertEquals(com.example.audio.PodTransparencyMode.QUIET_FOCUS, profile.recommendedTransparencyMode)
  }

  @Test
  fun digitalNoiseGate_suppressesBackgroundNoiseWhenSilent() {
    val gate = com.example.audio.DigitalNoiseGate(sampleRate = 48000f)
    gate.sensitivity = com.example.audio.NoiseGateSensitivity.BALANCED // -18 dB
    gate.isEnabled = true

    // Low-level ambient noise / microphone thermal hiss
    val noiseBuffer = FloatArray(480) { 0.001f }
    gate.process(noiseBuffer, noiseBuffer.size)

    assertFalse("Gate should be closed for background noise", gate.isGateOpen)
    assertTrue("Gain reduction should be active (below -10 dB)", gate.currentGainReductionDb <= -10f)

    for (s in noiseBuffer) {
      assertTrue("Noise sample must be attenuated", kotlin.math.abs(s) < 0.001f)
      assertFalse("Sample must not be NaN", s.isNaN())
    }
  }

  @Test
  fun digitalNoiseGate_opensInstantlyOnSpeech() {
    val gate = com.example.audio.DigitalNoiseGate(sampleRate = 48000f)
    gate.sensitivity = com.example.audio.NoiseGateSensitivity.BALANCED
    gate.isEnabled = true

    // Voice frame: 1 kHz tone at healthy vocal level (0.2f amplitude)
    val speechBuffer = FloatArray(480) { i ->
      (kotlin.math.sin(2.0 * Math.PI * 1000.0 * i / 48000.0) * 0.2f).toFloat()
    }

    gate.process(speechBuffer, speechBuffer.size)

    assertTrue("Gate must open when speech is detected", gate.isGateOpen)
    assertEquals("Gain reduction should be 0 dB when speaking", 0f, gate.currentGainReductionDb, 1.0f)
  }

  @Test
  fun digitalNoiseGate_bypassesWhenDisabled() {
    val gate = com.example.audio.DigitalNoiseGate(sampleRate = 48000f)
    gate.isEnabled = false

    val noiseBuffer = FloatArray(240) { 0.001f }
    val originalFirst = noiseBuffer[0]
    gate.process(noiseBuffer, noiseBuffer.size)

    assertEquals("Sample should not be attenuated when gate is disabled", originalFirst, noiseBuffer[0], 0.00001f)
  }

  @Test
  fun audioEngineState_defaultMasterGainIsSafeAndGentle() {
    val state = com.example.audio.AudioEngineState()
    assertEquals("Default master gain must be 1.0f (0 dB / natural comfort volume)", 1.0f, state.masterGain, 0.01f)
    assertTrue("Volume must not exceed safe 1.0f on initialization", state.masterGain <= 1.0f)
  }

  @Test
  fun presetConfigurations_areCalibratedToGentleComfortLevels() {
    val state = com.example.audio.AudioEngineState()
    // Initial state is gentle
    assertEquals(1.0f, state.masterGain, 0.001f)
    assertFalse(state.speechFocusEnabled)
    assertTrue("Feedback prevention must be active", state.feedbackPreventionEnabled)
    assertTrue("Noise gate must be active by default for crystal clear audio", state.isNoiseGateEnabled)
  }

  @Test
  fun batteryState_ecoModeEngagesAtLowBattery() {
    val stateLow = com.example.audio.BatteryState(
      level = 14,
      isCharging = false,
      isLowBattery = true,
      autoEcoModeEnabled = true
    )
    val computedLow = com.example.audio.BatteryMonitor.computeState(stateLow)
    assertTrue("Eco mode must engage when battery is 14% and auto eco mode is enabled", computedLow.isEcoModeActive)

    val stateCharging = stateLow.copy(isCharging = true, isLowBattery = false)
    val computedCharging = com.example.audio.BatteryMonitor.computeState(stateCharging)
    assertFalse("Eco mode must disengage when charging", computedCharging.isEcoModeActive)

    val stateDisabled = stateLow.copy(autoEcoModeEnabled = false)
    val computedDisabled = com.example.audio.BatteryMonitor.computeState(stateDisabled)
    assertFalse("Eco mode must not engage if user disabled auto eco mode", computedDisabled.isEcoModeActive)
  }

  @Test
  fun subscriptionPlans_areProperlyConfiguredForPlayStoreMonetization() {
    val monthly = com.example.data.SubscriptionPlan.MONTHLY
    val annual = com.example.data.SubscriptionPlan.ANNUAL

    assertEquals("Pro Monthly", monthly.title)
    assertEquals("$4.99", monthly.price)
    assertEquals("/month", monthly.period)

    assertEquals("Pro Annual", annual.title)
    assertEquals("$39.99", annual.price)
    assertEquals("/year", annual.period)
    assertNotNull(annual.savings)
  }
}
