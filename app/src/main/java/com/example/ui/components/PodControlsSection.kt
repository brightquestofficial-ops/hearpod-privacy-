package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.ConnectedEarpodInfo
import com.example.audio.HostDeviceInfo
import com.example.audio.OcclusionReliefMode
import com.example.audio.PodAcousticProfile
import com.example.audio.PodControlsState
import com.example.audio.PodMicRouting
import com.example.audio.PodModel
import com.example.audio.PodTransparencyMode
import com.example.ui.theme.SoundModerateAmber
import com.example.ui.theme.SoundSafeGreen
import kotlin.math.roundToInt

@Composable
fun PodControlsSection(
    connectedDevice: ConnectedEarpodInfo,
    hostDevice: HostDeviceInfo,
    podControlsState: PodControlsState,
    onSetLeftVolume: (Float) -> Unit,
    onSetRightVolume: (Float) -> Unit,
    onToggleLeftMute: () -> Unit,
    onToggleRightMute: () -> Unit,
    onSetEarBalance: (Float) -> Unit,
    onSetTransparencyMode: (PodTransparencyMode) -> Unit,
    onSetTransparencyLevel: (Float) -> Unit,
    onSetOcclusionCompensation: (OcclusionReliefMode) -> Unit,
    onSetMicRouting: (PodMicRouting) -> Unit,
    onToggleInEarDetection: () -> Unit,
    onToggleLeftInEar: () -> Unit,
    onToggleRightInEar: () -> Unit,
    onToggleAutoAdapt: (Boolean) -> Unit,
    onSelectProfile: (PodAcousticProfile) -> Unit,
    onOpenFitTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showProfileDialog by remember { mutableStateOf(false) }

    if (showProfileDialog) {
        PodProfileSelectorDialog(
            currentProfile = podControlsState.activeProfile,
            onSelectProfile = { profile ->
                onSelectProfile(profile)
            },
            onDismiss = { showProfileDialog = false }
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("pod_controls_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Header: Pod Identification & Status ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = "Pods Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = connectedDevice.model.displayName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (connectedDevice.isRecommendedForHearingAid) SoundSafeGreen else SoundModerateAmber)
                            )
                        }

                        Text(
                            text = "${connectedDevice.audioCodec} • ${hostDevice.manufacturer} ${hostDevice.model} (${hostDevice.nativeSampleRate / 1000}kHz)",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Seal / Fit Test Quick Action Button
                OutlinedButton(
                    onClick = onOpenFitTest,
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                    modifier = Modifier.testTag("fit_test_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Hearing,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Fit Test",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // --- Pod Battery & In-Ear Sensor Status ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Left Pod Pill
                PodStatusPill(
                    label = "Left Pod",
                    battery = podControlsState.leftBatteryPercent,
                    isInEar = podControlsState.leftPodInEar,
                    isMuted = podControlsState.isLeftMuted,
                    onClickInEar = onToggleLeftInEar,
                    modifier = Modifier.weight(1f)
                )

                // Right Pod Pill
                PodStatusPill(
                    label = "Right Pod",
                    battery = podControlsState.rightBatteryPercent,
                    isInEar = podControlsState.rightPodInEar,
                    isMuted = podControlsState.isRightMuted,
                    onClickInEar = onToggleRightInEar,
                    modifier = Modifier.weight(1f)
                )

                // Case Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = Modifier.weight(0.7f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Case",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BatteryFull,
                                contentDescription = null,
                                tint = SoundSafeGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${podControlsState.caseBatteryPercent}%",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // --- Auto-Adapt to Pods Banner & Profile Selector ---
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Auto-Adapt to Pods",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "Auto-tunes EQ curve, target latency & seal filters for ${connectedDevice.model.brand}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = podControlsState.autoAdaptEnabled,
                            onCheckedChange = { onToggleAutoAdapt(it) },
                            modifier = Modifier.testTag("auto_adapt_switch")
                        )
                    }

                    // Active Adapted Profile Chip & Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                            .clickable { showProfileDialog = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Profile: ${podControlsState.activeProfile.name}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = "Change",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // --- Independent Pod Volume & Ear Balance ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Ear Balance & Independent Volume",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Left & Right Volume Sliders side-by-side or stacked
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left Ear Volume
                    VolumeControlCard(
                        label = "Left Ear",
                        volume = podControlsState.leftVolume,
                        isMuted = podControlsState.isLeftMuted,
                        onVolumeChange = onSetLeftVolume,
                        onToggleMute = onToggleLeftMute,
                        modifier = Modifier.weight(1f)
                    )

                    // Right Ear Volume
                    VolumeControlCard(
                        label = "Right Ear",
                        volume = podControlsState.rightVolume,
                        isMuted = podControlsState.isRightMuted,
                        onVolumeChange = onSetRightVolume,
                        onToggleMute = onToggleRightMute,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Master Ear Balance Slider
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "L/R Balance",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            val balanceText = when {
                                podControlsState.earBalance < -0.05f -> "Left +${(-podControlsState.earBalance * 100).roundToInt()}%"
                                podControlsState.earBalance > 0.05f -> "Right +${(podControlsState.earBalance * 100).roundToInt()}%"
                                else -> "Centered (0%)"
                            }

                            Text(
                                text = balanceText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "L",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = podControlsState.earBalance,
                                onValueChange = onSetEarBalance,
                                valueRange = -1.0f..1.0f,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                                    .testTag("ear_balance_slider"),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Text(
                                text = "R",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (podControlsState.earBalance != 0.0f) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = "Reset Center",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    ),
                                    modifier = Modifier
                                        .clickable { onSetEarBalance(0.0f) }
                                        .padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // --- Pod Transparency & Ambient Modes ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Pod Transparency & Ambient Modes",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PodTransparencyChip(
                        mode = PodTransparencyMode.AMPLIFIED_TRANSPARENCY,
                        selected = podControlsState.transparencyMode == PodTransparencyMode.AMPLIFIED_TRANSPARENCY,
                        onClick = { onSetTransparencyMode(PodTransparencyMode.AMPLIFIED_TRANSPARENCY) },
                        modifier = Modifier.weight(1f)
                    )
                    PodTransparencyChip(
                        mode = PodTransparencyMode.NATURAL_PASSTHROUGH,
                        selected = podControlsState.transparencyMode == PodTransparencyMode.NATURAL_PASSTHROUGH,
                        onClick = { onSetTransparencyMode(PodTransparencyMode.NATURAL_PASSTHROUGH) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PodTransparencyChip(
                        mode = PodTransparencyMode.CONVERSATION_BOOST,
                        selected = podControlsState.transparencyMode == PodTransparencyMode.CONVERSATION_BOOST,
                        onClick = { onSetTransparencyMode(PodTransparencyMode.CONVERSATION_BOOST) },
                        modifier = Modifier.weight(1f)
                    )
                    PodTransparencyChip(
                        mode = PodTransparencyMode.QUIET_FOCUS,
                        selected = podControlsState.transparencyMode == PodTransparencyMode.QUIET_FOCUS,
                        onClick = { onSetTransparencyMode(PodTransparencyMode.QUIET_FOCUS) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Transparency Mix Level Slider
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Ambient Passthrough Mix",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${(podControlsState.transparencyLevel * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        Slider(
                            value = podControlsState.transparencyLevel,
                            onValueChange = onSetTransparencyLevel,
                            valueRange = 0.0f..1.0f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("transparency_level_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }

            // --- Ear Canal Occlusion Relief (Bone Conduction Compensation) ---
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Ear Occlusion Relief",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "Relieves the boomy 'hollow-head' sensation caused by sealed silicone tips blocking the ear canal.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OcclusionReliefMode.values().forEach { mode ->
                            val isSelected = podControlsState.occlusionCompensation == mode
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSetOcclusionCompensation(mode) },
                                label = {
                                    Text(
                                        text = mode.title,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("occlusion_${mode.name.lowercase()}"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }

            // --- Pod Microphone Beamforming Routing ---
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Pod Microphone Routing",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "Select microphone pickup source for mouth-to-ear low-latency audio transmission.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MicRoutingChip(
                                routing = PodMicRouting.DUAL_POD_BEAMFORMING,
                                selected = podControlsState.podMicRouting == PodMicRouting.DUAL_POD_BEAMFORMING,
                                onClick = { onSetMicRouting(PodMicRouting.DUAL_POD_BEAMFORMING) },
                                modifier = Modifier.weight(1f)
                            )
                            MicRoutingChip(
                                routing = PodMicRouting.LEFT_POD_ONLY,
                                selected = podControlsState.podMicRouting == PodMicRouting.LEFT_POD_ONLY,
                                onClick = { onSetMicRouting(PodMicRouting.LEFT_POD_ONLY) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MicRoutingChip(
                                routing = PodMicRouting.RIGHT_POD_ONLY,
                                selected = podControlsState.podMicRouting == PodMicRouting.RIGHT_POD_ONLY,
                                onClick = { onSetMicRouting(PodMicRouting.RIGHT_POD_ONLY) },
                                modifier = Modifier.weight(1f)
                            )
                            MicRoutingChip(
                                routing = PodMicRouting.PHONE_DIRECTIONAL,
                                selected = podControlsState.podMicRouting == PodMicRouting.PHONE_DIRECTIONAL,
                                onClick = { onSetMicRouting(PodMicRouting.PHONE_DIRECTIONAL) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // --- Smart In-Ear Detection Auto-Pause Toggle ---
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Smart In-Ear Auto-Mute",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Automatically mutes side when pod is removed to avoid screeching feedback loop",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = podControlsState.inEarDetectionEnabled,
                        onCheckedChange = { onToggleInEarDetection() },
                        modifier = Modifier.testTag("in_ear_detection_switch")
                    )
                }
            }
        }
    }
}

@Composable
private fun PodStatusPill(
    label: String,
    battery: Int,
    isInEar: Boolean,
    isMuted: Boolean,
    onClickInEar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = if (!isInEar) MaterialTheme.colorScheme.error else if (isMuted) SoundModerateAmber else SoundSafeGreen

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        modifier = modifier.clickable { onClickInEar() }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Battery6Bar,
                    contentDescription = null,
                    tint = SoundSafeGreen,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "$battery%",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = statusColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = if (!isInEar) "Out of Ear" else if (isMuted) "Muted" else "In-Ear",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    ),
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun VolumeControlCard(
    label: String,
    volume: Float,
    isMuted: Boolean,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Mute",
                        tint = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = if (isMuted) "Muted" else "${(volume * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            )

            Slider(
                value = if (isMuted) 0f else volume,
                onValueChange = onVolumeChange,
                valueRange = 0.0f..1.5f,
                enabled = !isMuted,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun PodTransparencyChip(
    mode: PodTransparencyMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = mode.title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp
                    )
                )
            }
        },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@Composable
private fun MicRoutingChip(
    routing: PodMicRouting,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = routing.title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 10.sp
                )
            )
        },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}
