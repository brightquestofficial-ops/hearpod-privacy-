package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.audio.BatteryMonitor

/**
 * Service that continuously monitors device battery health and broadcast state changes
 * during audio amplification sessions to reduce DSP processing overhead at low battery.
 */
class BatteryMonitoringService : Service() {
    private var batteryMonitor: BatteryMonitor? = null

    companion object {
        const val ACTION_START = "com.example.service.BATTERY_MONITOR_START"
        const val ACTION_STOP = "com.example.service.BATTERY_MONITOR_STOP"

        var isRunning: Boolean = false
            private set

        fun startService(context: Context) {
            try {
                val intent = Intent(context, BatteryMonitoringService::class.java).apply {
                    action = ACTION_START
                }
                context.startService(intent)
            } catch (e: Throwable) {
                Log.w("BatteryMonitoringService", "Could not start battery service: ${e.message}")
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, BatteryMonitoringService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Throwable) {
                Log.w("BatteryMonitoringService", "Could not stop battery service: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        batteryMonitor = BatteryMonitor(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                isRunning = false
                batteryMonitor?.unregister()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                isRunning = true
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        batteryMonitor?.unregister()
        batteryMonitor = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
