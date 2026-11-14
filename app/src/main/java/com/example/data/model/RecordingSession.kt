package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recording_sessions")
data class RecordingSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Int,
    val filePath: String,
    val averageDecibels: Float = 0f,
    val peakDecibels: Float = 0f,
    val presetUsed: String = "Normal",
    val notes: String = ""
)

data class HearingThreshold(
    val frequencyHz: Int,
    val thresholdDbHl: Int // 0 to 70 dB Hearing Level
)

data class HearingProfile(
    val name: String = "Default Calibrated",
    val timestamp: Long = System.currentTimeMillis(),
    val thresholds: List<HearingThreshold> = listOf(
        HearingThreshold(250, 15),
        HearingThreshold(500, 20),
        HearingThreshold(1000, 25),
        HearingThreshold(2000, 35),
        HearingThreshold(4000, 45),
        HearingThreshold(8000, 40)
    )
) {
    /**
     * Translates clinical hearing thresholds into frequency equalizer boost compensations
     */
    fun toEqGains(): Map<Int, Float> {
        return thresholds.associate { threshold ->
            // Clinical rule of thumb: half-gain rule for sensorineural compensation
            // Mild/moderate deficit (20-60 dB HL) compensated by ~30-50% in linear dB EQ gain
            val deficit = (threshold.thresholdDbHl - 15).coerceAtLeast(0)
            val boost = (deficit * 0.4f).coerceIn(0f, 15f)
            threshold.frequencyHz to boost
        }
    }
}
