package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.BiquadFilter
import com.example.audio.DeviceAuditor
import com.example.data.model.HearingProfile
import com.example.data.model.HearingThreshold
import com.example.ui.HearingAidViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("HearPod", appName)
  }

  @Test
  fun `biquad filter processes audio samples without NaN`() {
    val filter = BiquadFilter(centerFrequency = 1000f, sampleRate = 44100f)
    filter.setGain(6f)
    val output = filter.processSample(0.5f)
    assertTrue(!output.isNaN())
    assertTrue(output > 0f)
  }

  @Test
  fun `hearing profile calculates compensation gains`() {
    val profile = HearingProfile(
      thresholds = listOf(
        HearingThreshold(1000, 40),
        HearingThreshold(2000, 50)
      )
    )
    val eqGains = profile.toEqGains()
    val boost1k = eqGains[1000] ?: 0f
    assertTrue("Should provide boost for 40 dB HL deficit", boost1k > 0f)
  }

  @Test
  fun `device auditor instantiates without crash`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val auditor = DeviceAuditor(context)
    val device = auditor.connectedDevice.value
    assertNotNull(device)
    assertNotNull(device.name)
    auditor.unregister()
  }

  @Test
  fun `hearing aid view model instantiates on app launch without crash`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = HearingAidViewModel(application)
    assertNotNull(viewModel.connectedDevice.value)
    assertNotNull(viewModel.engineState.value)
    assertNotNull(viewModel.subscriptionState.value)
  }

  @Test
  fun `audiogram test lifecycle manages test state safely`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = HearingAidViewModel(application)

    viewModel.startAudiogramTest()
    assertTrue(viewModel.audiogramState.value.isActive)

    viewModel.userHearsTone()
    assertEquals(1, viewModel.audiogramState.value.currentFrequencyIndex)

    viewModel.cancelAudiogramTest()
    assertTrue(!viewModel.audiogramState.value.isActive)
  }

  @Test
  fun `transparency mode and level update properly`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = HearingAidViewModel(application)

    viewModel.setPodTransparencyMode(com.example.audio.PodTransparencyMode.AMPLIFIED_TRANSPARENCY)
    assertEquals(com.example.audio.PodTransparencyMode.AMPLIFIED_TRANSPARENCY, viewModel.podControlsState.value.transparencyMode)

    viewModel.setPodTransparencyLevel(0.85f)
    assertEquals(0.85f, viewModel.podControlsState.value.transparencyLevel, 0.01f)
  }

  @Test
  fun `bluetooth headset check blocks start when no earbuds connected`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = HearingAidViewModel(application)

    // Initially in Robolectric environment without physical Bluetooth earpods
    var permissionCallbackTriggered = false
    viewModel.togglePower {
      permissionCallbackTriggered = true
    }

    // Power toggle must NOT proceed to audio start callback when no headset is connected
    assertTrue("Permission start callback should not trigger without headset", !permissionCallbackTriggered)
    assertTrue("Bluetooth required notice should be displayed", viewModel.showBluetoothRequiredNotice.value)

    // Direct startAmplification should also return false
    val started = viewModel.startAmplification()
    assertTrue("Audio transmission should not start without Bluetooth headset", !started)
  }

  @Test
  fun `simulating or connecting bluetooth earbuds allows amplification`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = HearingAidViewModel(application)

    viewModel.simulateBluetoothEarbuds(true, com.example.audio.PodModel.AIRPODS_PRO)
    assertTrue("Bluetooth headset should be reported as connected", viewModel.isBluetoothHeadsetConnected.value)

    var permissionCallbackTriggered = false
    viewModel.togglePower {
      permissionCallbackTriggered = true
    }

    assertTrue("Permission start callback should trigger once earbuds are connected", permissionCallbackTriggered)
  }

  @Test
  fun `digital noise gate toggle and sensitivity updates properly`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = HearingAidViewModel(application)

    assertTrue("Noise gate should be enabled by default", viewModel.engineState.value.isNoiseGateEnabled)

    viewModel.setNoiseGateSensitivity(com.example.audio.NoiseGateSensitivity.DEEP_QUIET)
    assertEquals(com.example.audio.NoiseGateSensitivity.DEEP_QUIET, viewModel.engineState.value.noiseGateSensitivity)
    assertEquals(-24f, viewModel.engineState.value.noiseGateSuppressionDb, 0.1f)

    viewModel.setNoiseGateEnabled(false)
    assertFalse(viewModel.engineState.value.isNoiseGateEnabled)
  }

  @Test
  fun `force speaker allows starting amplification on speaker with echo shield active`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = HearingAidViewModel(application)

    var permissionCallbackTriggered = false
    viewModel.togglePower(forceSpeaker = true) {
      permissionCallbackTriggered = true
    }

    assertTrue("Permission start callback should trigger when user selects speaker mode", permissionCallbackTriggered)
    assertTrue("Speaker output mode should be activated for Echo Shield", viewModel.standardAudioEngine.isSpeakerOutput)
  }

  @Test
  fun `second launch and multiple start stop cycles handle audio hardware cleanly without throwing`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = HearingAidViewModel(application)

    // First cycle
    viewModel.simulateBluetoothEarbuds(true)
    viewModel.startAmplification()
    viewModel.stopAmplification()

    // Second launch cycle - simulate relaunch
    viewModel.stopAmplification()
    viewModel.simulateBluetoothEarbuds(true)
    val restartResult = viewModel.startAmplification()
    // Should not throw or crash
    viewModel.stopAmplification()
    assertFalse(viewModel.engineState.value.isRunning)
  }

  @Test
  fun `battery monitor detects low battery and activates eco mode`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = HearingAidViewModel(application)

    // Simulate normal battery level
    viewModel.simulateBatteryState(80, isCharging = false)
    assertFalse(viewModel.batteryState.value.isLowBattery)
    assertFalse(viewModel.batteryState.value.isEcoModeActive)
    assertFalse(viewModel.standardAudioEngine.engineState.value.isEcoModeActive)

    // Simulate low battery (<= 20%)
    viewModel.simulateBatteryState(15, isCharging = false)
    assertTrue("15% should trigger low battery", viewModel.batteryState.value.isLowBattery)
    assertTrue("Low battery should activate Eco Mode", viewModel.batteryState.value.isEcoModeActive)
    assertTrue("Audio engine should reflect throttled DSP in eco mode", viewModel.standardAudioEngine.engineState.value.isEcoModeActive)
    assertTrue("Audio engine should mark DSP throttled", viewModel.standardAudioEngine.engineState.value.isDspThrottled)
    assertTrue("Audio gate should have isEcoMode enabled", viewModel.standardAudioEngine.digitalNoiseGate.isEcoMode)
    assertTrue("Spectral filter should have isEcoMode enabled", viewModel.standardAudioEngine.aiSpectralFilter.isEcoMode)

    // Simulate charging connected -> Eco mode should deactivate
    viewModel.simulateBatteryState(15, isCharging = true)
    assertFalse("Charging should clear low battery", viewModel.batteryState.value.isLowBattery)
    assertFalse("Charging should deactivate Eco Mode", viewModel.batteryState.value.isEcoModeActive)
  }

  @Test
  fun `force eco mode throttles DSP regardless of high battery level`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = HearingAidViewModel(application)

    viewModel.simulateBatteryState(95, isCharging = false)
    assertFalse(viewModel.batteryState.value.isEcoModeActive)

    // Force eco mode
    viewModel.setForceEcoMode(true)
    assertTrue(viewModel.batteryState.value.isEcoModeActive)
    assertTrue(viewModel.standardAudioEngine.engineState.value.isEcoModeActive)

    // Release force eco mode
    viewModel.setForceEcoMode(false)
    assertFalse(viewModel.batteryState.value.isEcoModeActive)
  }

  @Test
  fun `default initial volume is gentle and safe at unity gain`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = HearingAidViewModel(application)

    // Master gain must be calibrated to gentle 1.0f (0 dB / comfort level) rather than loud 3.0f (+9.5 dB)
    assertEquals(1.0f, viewModel.engineState.value.masterGain, 0.01f)
    assertEquals(1.0f, viewModel.standardAudioEngine.engineState.value.masterGain, 0.01f)
  }

  @Test
  fun `switching off inside app allows switching back on seamlessly`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = HearingAidViewModel(application)

    // User enables speaker mode or pairs earbuds
    viewModel.enableSpeakerMode()
    assertTrue(viewModel.isSpeakerModeAllowed.value)

    var startCount = 0
    // First turn-on
    viewModel.togglePower {
      startCount++
      viewModel.startAmplification()
    }
    assertEquals("Should start on first toggle", 1, startCount)

    // User turns OFF inside app
    viewModel.togglePower()
    assertFalse("Should be stopped", viewModel.engineState.value.isRunning)

    // User turns ON AGAIN inside app
    viewModel.togglePower {
      startCount++
      viewModel.startAmplification()
    }
    assertEquals("Should successfully start again on second toggle", 2, startCount)
    assertFalse("Bluetooth dialog should not block re-power when speaker mode is authorized", viewModel.showBluetoothRequiredNotice.value)
  }

  @Test
  fun `battery monitoring service starts and stops cleanly`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    com.example.service.BatteryMonitoringService.startService(application)
    com.example.service.BatteryMonitoringService.stopService(application)
  }
}
