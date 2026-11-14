package com.example.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State representing the host device battery and DSP power-saving configuration.
 */
data class BatteryState(
    val level: Int = 100, // 0 - 100%
    val isCharging: Boolean = false,
    val isLowBattery: Boolean = false, // level <= 20% && !isCharging, or system ACTION_BATTERY_LOW
    val isPowerSaveMode: Boolean = false,
    val autoEcoModeEnabled: Boolean = true,
    val forceEcoMode: Boolean = false,
    val isEcoModeActive: Boolean = false,
    val dspFrequencyDesc: String = "Normal Rate (30 FPS / Full 6-Band Multiband)",
    val estimatedUsageExtensionMinutes: Int = 0
)

/**
 * Monitors the Android device's battery level and charging state via system broadcasts.
 * Automatically enables low-power DSP processing when battery drops below 20% to extend
 * hearing aid listening time without audio degradation.
 */
class BatteryMonitor(private val context: Context) {
    private val _batteryState = MutableStateFlow(BatteryState())
    val batteryState: StateFlow<BatteryState> = _batteryState.asStateFlow()

    private var isRegistered = false
    var onEcoModeChanged: ((Boolean) -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            intent?.let { handleIntent(it) }
        }
    }

    init {
        readInitialBattery()
        register()
    }

    fun register() {
        if (isRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        try {
            context.registerReceiver(receiver, filter)
            isRegistered = true
        } catch (e: Exception) {
            Log.w("BatteryMonitor", "Could not register battery receiver: ${e.message}")
        }
    }

    fun unregister() {
        if (!isRegistered) return
        try {
            context.unregisterReceiver(receiver)
            isRegistered = false
        } catch (e: Exception) {
            Log.w("BatteryMonitor", "Could not unregister battery receiver: ${e.message}")
        }
    }

    private fun readInitialBattery() {
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus: Intent? = context.registerReceiver(null, filter)
            if (batteryStatus != null) {
                handleIntent(batteryStatus)
            }
        } catch (e: Exception) {
            Log.w("BatteryMonitor", "Error querying initial battery: ${e.message}")
        }
    }

    private fun handleIntent(intent: Intent) {
        val current = _batteryState.value
        var newLevel = current.level
        var newCharging = current.isCharging
        var systemLow = current.isLowBattery

        when (intent.action) {
            Intent.ACTION_BATTERY_CHANGED -> {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    newLevel = (level * 100f / scale).toInt().coerceIn(0, 100)
                }
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                newCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                systemLow = newLevel <= 20 && !newCharging
            }
            Intent.ACTION_BATTERY_LOW -> {
                systemLow = true
            }
            Intent.ACTION_BATTERY_OKAY -> {
                systemLow = false
            }
            Intent.ACTION_POWER_CONNECTED -> {
                newCharging = true
                systemLow = false
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                newCharging = false
                systemLow = newLevel <= 20
            }
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSave = powerManager?.isPowerSaveMode == true

        updateState(
            level = newLevel,
            isCharging = newCharging,
            isLowBattery = systemLow || (newLevel <= 20 && !newCharging),
            isPowerSaveMode = isPowerSave
        )
    }

    fun setAutoEcoMode(enabled: Boolean) {
        val current = _batteryState.value
        _batteryState.value = computeState(
            current.copy(autoEcoModeEnabled = enabled)
        )
        notifyEcoModeChange()
    }

    fun setForceEcoMode(force: Boolean) {
        val current = _batteryState.value
        _batteryState.value = computeState(
            current.copy(forceEcoMode = force)
        )
        notifyEcoModeChange()
    }

    fun simulateBatteryState(level: Int, isCharging: Boolean = false) {
        updateState(
            level = level.coerceIn(0, 100),
            isCharging = isCharging,
            isLowBattery = level <= 20 && !isCharging,
            isPowerSaveMode = false
        )
    }

    private fun updateState(level: Int, isCharging: Boolean, isLowBattery: Boolean, isPowerSaveMode: Boolean) {
        val current = _batteryState.value
        _batteryState.value = computeState(
            current.copy(
                level = level,
                isCharging = isCharging,
                isLowBattery = isLowBattery,
                isPowerSaveMode = isPowerSaveMode
            )
        )
        notifyEcoModeChange()
    }

    companion object {
        fun computeState(base: BatteryState): BatteryState {
            val isEcoActive = base.forceEcoMode || (base.autoEcoModeEnabled && (base.isLowBattery || base.isPowerSaveMode))
            val dspDesc = if (isEcoActive) {
                "Throttled Eco DSP (~8 FPS Metering • Decimated RMS • Flat-Band Bypass)"
            } else {
                "Normal Full-Rate DSP (30 FPS Metering • All 6 Biquads Active)"
            }
            val extensionMins = if (isEcoActive) {
                (base.level * 1.8f).toInt()
            } else 0

            return base.copy(
                isEcoModeActive = isEcoActive,
                dspFrequencyDesc = dspDesc,
                estimatedUsageExtensionMinutes = extensionMins
            )
        }
    }

    private fun notifyEcoModeChange() {
        val eco = _batteryState.value.isEcoModeActive
        onEcoModeChanged?.invoke(eco)
    }
}
