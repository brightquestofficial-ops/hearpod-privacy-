package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FrequencyEqualizerView(
    bandGains: Map<Int, Float>,
    selectedPreset: String,
    onBandGainChange: (Int, Float) -> Unit,
    onPresetSelect: (String) -> Unit,
    onResetFlat: () -> Unit,
    hasCalibratedProfile: Boolean,
    modifier: Modifier = Modifier
) {
    val presets = listOfNotNull(
        "Speech Clarity",
        "Noisy Restaurant",
        "TV / Media",
        "Outdoor / Wind",
        "Balanced Natural",
        if (hasCalibratedProfile) "My Audiogram Profile" else null
    )

    val frequencies = listOf(250, 500, 1000, 2000, 4000, 8000)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header with reset button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Frequency Clarity Equalizer",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Fine-tune frequencies for vocal clarity and hearing assistance",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onResetFlat,
                    modifier = Modifier.testTag("reset_eq_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset EQ to Flat",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Quick Preset Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    val isSelected = selectedPreset == preset
                    FilterChip(
                        selected = isSelected,
                        onClick = { onPresetSelect(preset) },
                        label = {
                            Text(
                                text = preset,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            // Frequency Response Curve Canvas
            val primaryColor = MaterialTheme.colorScheme.primary
            val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                val width = size.width
                val height = size.height
                val midY = height * 0.6f // 0 dB baseline

                // Baseline 0 dB
                drawLine(
                    color = outlineColor,
                    start = Offset(0f, midY),
                    end = Offset(width, midY),
                    strokeWidth = 1.dp.toPx()
                )

                // Draw curve through points
                val points = frequencies.mapIndexed { index, freq ->
                    val gain = bandGains[freq] ?: 0f
                    // Map -12 dB to +18 dB to pixel Y
                    // +18 dB -> Y = 8dp, 0 dB -> midY, -12 dB -> height - 8dp
                    val normalizedY = midY - (gain / 18f) * (midY - 8.dp.toPx())
                    val x = (index.toFloat() / (frequencies.size - 1)) * (width - 32.dp.toPx()) + 16.dp.toPx()
                    Offset(x, normalizedY.coerceIn(4f, height - 4f))
                }

                val path = Path()
                if (points.isNotEmpty()) {
                    path.moveTo(points[0].x, points[0].y)
                    for (i in 0 until points.size - 1) {
                        val p0 = points[i]
                        val p1 = points[i + 1]
                        val cx = (p0.x + p1.x) / 2f
                        path.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                    }
                }

                drawPath(
                    path = path,
                    color = primaryColor,
                    style = Stroke(width = 3.dp.toPx())
                )

                // Draw dots at frequency nodes
                points.forEach { pt ->
                    drawCircle(
                        color = primaryColor,
                        radius = 4.dp.toPx(),
                        center = pt
                    )
                }
            }

            // Interactive Sliders for each of the 6 frequencies
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                frequencies.forEach { freq ->
                    val gain = bandGains[freq] ?: 0f
                    val label = when (freq) {
                        250 -> "250 Hz (Low Bass / Rumble)"
                        500 -> "500 Hz (Low Warmth)"
                        1000 -> "1 kHz (Speech Fundamental)"
                        2000 -> "2 kHz (Vowel Articulation)"
                        4000 -> "4 kHz (Consonants & Clarity)"
                        8000 -> "8 kHz (Crisp Highs)"
                        else -> "$freq Hz"
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val gainText = if (gain > 0) "+${gain.toInt()} dB" else "${gain.toInt()} dB"
                            Text(
                                text = gainText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (gain > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }

                        Slider(
                            value = gain,
                            onValueChange = { newGain -> onBandGainChange(freq, newGain) },
                            valueRange = -12f..18f,
                            steps = 29, // 1 dB increments
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .testTag("slider_freq_$freq"),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }
        }
    }
}
