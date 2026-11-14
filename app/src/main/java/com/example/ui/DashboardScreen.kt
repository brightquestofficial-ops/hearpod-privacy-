package com.example.ui

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import com.example.audio.NoiseGateSensitivity
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.AiNoiseMode
import com.example.ui.components.AnalyticsDialog
import com.example.ui.components.AudiogramDialog
import com.example.ui.components.DecibelMeter
import com.example.ui.components.FrequencyEqualizerView
import com.example.ui.components.PodControlsSection
import com.example.ui.components.PodFitTestDialog
import com.example.ui.components.RecordingsSheet
import com.example.ui.components.SoundVisualizer
import com.example.ui.components.SubscriptionDialog
import com.example.ui.theme.AmberAccentDark
import com.example.ui.theme.SoundLoudRed
import com.example.ui.theme.SoundModerateAmber
import com.example.ui.theme.SoundSafeGreen
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: HearingAidViewModel,
    audioEngineState: com.example.audio.AudioEngineState,
    connectedDevice: com.example.audio.ConnectedEarpodInfo,
    hostDevice: com.example.audio.HostDeviceInfo,
    bandGains: Map<Int, Float>,
    selectedPreset: String,
    audiogramState: AudiogramTestState,
    userProfile: com.example.data.model.HearingProfile?,
    recordedSessions: List<com.example.data.model.RecordingSession>,
    subscriptionState: com.example.data.SubscriptionState,
    analytics: HearingHealthAnalytics,
    playbackState: PlaybackState,
    isDarkMode: Boolean,
    isAiOptimizing: Boolean,
    onRequestRecordPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showAudiogramDialog by remember { mutableStateOf(false) }
    var showRecordingsSheet by remember { mutableStateOf(false) }
    var showSubscriptionDialog by remember { mutableStateOf(false) }
    var showAnalyticsDialog by remember { mutableStateOf(false) }
    var showFitTestDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    val podControlsState by viewModel.podControlsState.collectAsStateWithLifecycle()
    val isPlayingSoundCheck by viewModel.isPlayingSoundCheck.collectAsStateWithLifecycle()
    val isBluetoothHeadsetConnected by viewModel.isBluetoothHeadsetConnected.collectAsStateWithLifecycle()
    val isSimulatedBluetoothConnected by viewModel.isSimulatedBluetoothConnected.collectAsStateWithLifecycle()
    val showBluetoothRequiredNotice by viewModel.showBluetoothRequiredNotice.collectAsStateWithLifecycle()
    val batteryState by viewModel.batteryState.collectAsStateWithLifecycle()
    val isSpeakerModeAllowed by viewModel.isSpeakerModeAllowed.collectAsStateWithLifecycle()

    val infiniteTransition = rememberInfiniteTransition(label = "power_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (audioEngineState.isRunning) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Top Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Hearing,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "HearPod",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Zero-Delay AI Hearing Aid",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.toggleDarkMode() },
                            modifier = Modifier.testTag("dark_mode_toggle")
                        ) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Theme",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { showAnalyticsDialog = true },
                            modifier = Modifier.testTag("analytics_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = "Acoustic Health & Sync",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { showRecordingsSheet = true },
                            modifier = Modifier.testTag("recordings_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (recordedSessions.isNotEmpty()) {
                                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                            Text("${recordedSessions.size}")
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "Saved Audio Sessions",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (subscriptionState.isPro) AmberAccentDark.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (subscriptionState.isPro) AmberAccentDark else Color.Transparent
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { showSubscriptionDialog = true }
                                .testTag("pro_badge_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WorkspacePremium,
                                    contentDescription = null,
                                    tint = if (subscriptionState.isPro) AmberAccentDark else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (subscriptionState.isPro) "PRO" else "GET PRO",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (subscriptionState.isPro) AmberAccentDark else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 2. Main Central Power & Audio Status Hero Card (PROMINENT AT TOP - ZERO SCROLLING)
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Bluetooth Headset / Output Connection Banner
                        if (!isBluetoothHeadsetConnected) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("speaker_mode_active_banner")
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PhoneAndroid,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Phone Speaker Mode Ready",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Echo Shield active to prevent loopback howl • Connect earbuds anytime for binaural sound",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                try {
                                                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                                } catch (e: Exception) {
                                                    viewModel.showBluetoothRequiredDialog()
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                                .testTag("open_bluetooth_settings_button"),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Bluetooth,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Bluetooth",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = { viewModel.simulateBluetoothEarbuds(true) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                                .testTag("pair_demo_pods_button"),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Hearing,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Demo Pods",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SoundSafeGreen.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, SoundSafeGreen.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = SoundSafeGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Earbuds Connected: ${connectedDevice.name}",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (isSimulatedBluetoothConnected) {
                                        TextButton(
                                            onClick = { viewModel.simulateBluetoothEarbuds(false) },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text(
                                                text = "Disconnect Demo",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            if (audioEngineState.isRunning) {
                                Box(
                                    modifier = Modifier
                                        .size(140.dp)
                                        .scale(pulseScale)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                                )
                            }

                            val buttonBg = when {
                                audioEngineState.isRunning -> Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                                else -> Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.90f),
                                        MaterialTheme.colorScheme.primary
                                    )
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(114.dp)
                                    .clip(CircleShape)
                                    .background(buttonBg)
                                    .clickable {
                                        if (audioEngineState.isRunning) {
                                            viewModel.stopAmplification()
                                        } else {
                                            viewModel.togglePower(forceSpeaker = !isBluetoothHeadsetConnected) {
                                                onRequestRecordPermission()
                                            }
                                        }
                                    }
                                    .testTag("power_toggle_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PowerSettingsNew,
                                        contentDescription = when {
                                            audioEngineState.isRunning -> "Turn Hearing Aid Off"
                                            else -> "Turn Hearing Aid On"
                                        },
                                        tint = Color.White,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Text(
                                        text = when {
                                            audioEngineState.isRunning -> "ON\nTAP TO STOP"
                                            else -> "TAP TO START"
                                        },
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            lineHeight = 13.sp
                                        ),
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = when {
                                    audioEngineState.isRunning -> "TRANSMITTING • LIVE AMPLIFICATION"
                                    isBluetoothHeadsetConnected -> "HEARING AID READY • EARBUDS CONNECTED"
                                    else -> "HEARING AID READY • SPEAKER READY"
                                },
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = when {
                                    audioEngineState.isRunning -> MaterialTheme.colorScheme.primary
                                    isBluetoothHeadsetConnected -> SoundSafeGreen
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Text(
                                text = when {
                                    audioEngineState.isRunning -> "Ultra-low latency sound amplification with AI noise cancellation (Works with screen locked)"
                                    isBluetoothHeadsetConnected -> "Tap the power button above to amplify sound immediately into your earbuds"
                                    else -> "Tap the power button above to amplify sound through speaker with Echo Shield"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }

                        // Live Waveform Visualizer
                        SoundVisualizer(
                            waveformSamples = audioEngineState.waveformSamples,
                            isRunning = audioEngineState.isRunning,
                            barColor = MaterialTheme.colorScheme.primary,
                            accentColor = MaterialTheme.colorScheme.secondary
                        )

                        // Decibel Sound Pressure Meter
                        DecibelMeter(
                            currentDb = audioEngineState.currentDb,
                            peakDb = audioEngineState.peakDb,
                            isRunning = audioEngineState.isRunning
                        )

                        // Immediate Audio Output & Amplifier Check Button
                        OutlinedButton(
                            onClick = { viewModel.playSoundCheck() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("sound_check_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = if (isPlayingSoundCheck) Icons.Default.Hearing else Icons.Default.GraphicEq,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isPlayingSoundCheck) "Playing Chime..." else "Audio Output Check (Tap to Hear Sound)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }

            // 3. Master Amplification Slider Card (IMMEDIATELY BELOW POWER BUTTON)
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Master Amplification",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Clean vocal boost delivered to earpods",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            val percent = (audioEngineState.masterGain * 100).toInt()
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "$percent%",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Slider(
                            value = audioEngineState.masterGain.coerceIn(0.5f, 6.0f),
                            onValueChange = { viewModel.setMasterGain(it) },
                            valueRange = 0.5f..6.0f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .testTag("master_gain_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "50% (Comfort)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = "100% (Natural)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = "250% (Maximum)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // 4. AI Noise Cancellation & Ambient Elimination Suite
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "AI Noise Cancellation",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Sub-band spectral subtraction kills AC hum & mic hiss",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (audioEngineState.isHardwareNoiseSuppressionActive) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = SoundSafeGreen.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "HW DSP Active",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = SoundSafeGreen,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Mode Selector Chips
                        val modes = listOf(
                            AiNoiseMode.STUDIO_QUIET,
                            AiNoiseMode.BALANCED,
                            AiNoiseMode.CONVERSATION_FOCUS,
                            AiNoiseMode.OFF
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            modes.forEach { mode ->
                                val isSelected = audioEngineState.aiNoiseMode == mode
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setAiNoiseMode(mode) },
                                    label = {
                                        Text(
                                            text = mode.title,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.testTag("ai_noise_mode_${mode.name}")
                                )
                            }
                        }

                        // Selected mode description
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = audioEngineState.aiNoiseMode.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Gemini AI Auto-Tune Button
                        Button(
                            onClick = {
                                viewModel.optimizeEnvironmentWithAi { resultText ->
                                    Toast.makeText(context, resultText, Toast.LENGTH_LONG).show()
                                }
                            },
                            enabled = !isAiOptimizing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("ai_autotune_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            if (isAiOptimizing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analyzing Room Acoustics...")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("AI Auto-Tune for Current Room")
                            }
                        }
                    }
                }
            }

            // 5. Speech Articulation & Shriek Protection
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Acoustic Protection & Noise Gate",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // 1. Digital Noise Gate (Speech-Gated Suppressor)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .border(
                                    width = 1.dp,
                                    color = if (audioEngineState.isNoiseGateEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.Transparent,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (audioEngineState.isNoiseGateEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RecordVoiceOver,
                                            contentDescription = null,
                                            tint = if (audioEngineState.isNoiseGateEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "Digital Noise Gate",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            // Real-time Gate State Indicator (Shows when gate is Open vs Silencing room drone)
                                            if (audioEngineState.isRunning && audioEngineState.isNoiseGateEnabled) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (audioEngineState.isNoiseGateOpen) SoundSafeGreen.copy(alpha = 0.2f)
                                                    else MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .clip(CircleShape)
                                                                .background(if (audioEngineState.isNoiseGateOpen) SoundSafeGreen else MaterialTheme.colorScheme.secondary)
                                                        )
                                                        Text(
                                                            text = if (audioEngineState.isNoiseGateOpen) "OPEN • SPEECH" else "MUTED (${audioEngineState.noiseGateSuppressionDb.toInt()} dB)",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 9.5.sp,
                                                                fontWeight = FontWeight.Bold
                                                            ),
                                                            color = if (audioEngineState.isNoiseGateOpen) SoundSafeGreen else MaterialTheme.colorScheme.secondary
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Text(
                                            text = "Automatically suppresses room hiss, fan drone, and AC hum when not speaking",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Switch(
                                    checked = audioEngineState.isNoiseGateEnabled,
                                    onCheckedChange = { viewModel.setNoiseGateEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag("noise_gate_switch")
                                )
                            }

                            // Suppression Depth Presets
                            if (audioEngineState.isNoiseGateEnabled) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Background Suppression Depth:",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        // Row 1: Gentle and Balanced
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf(NoiseGateSensitivity.GENTLE, NoiseGateSensitivity.BALANCED).forEach { sensitivity ->
                                                val isSelected = audioEngineState.noiseGateSensitivity == sensitivity
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                    border = BorderStroke(
                                                        width = if (isSelected) 1.5.dp else 1.dp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                                    ),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .clickable { viewModel.setNoiseGateSensitivity(sensitivity) }
                                                        .testTag("noise_gate_sensitivity_${sensitivity.name}")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        if (isSelected) {
                                                            Icon(
                                                                imageVector = Icons.Default.CheckCircle,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                        }
                                                        Text(
                                                            text = sensitivity.title,
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 11.5.sp,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                            ),
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 1,
                                                            softWrap = false
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Row 2: Deep Quiet and Max Cut
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf(NoiseGateSensitivity.DEEP_QUIET, NoiseGateSensitivity.MAXIMUM_CUT).forEach { sensitivity ->
                                                val isSelected = audioEngineState.noiseGateSensitivity == sensitivity
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                    border = BorderStroke(
                                                        width = if (isSelected) 1.5.dp else 1.dp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                                    ),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .clickable { viewModel.setNoiseGateSensitivity(sensitivity) }
                                                        .testTag("noise_gate_sensitivity_${sensitivity.name}")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        if (isSelected) {
                                                            Icon(
                                                                imageVector = Icons.Default.CheckCircle,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                        }
                                                        Text(
                                                            text = sensitivity.title,
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 11.5.sp,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                            ),
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 1,
                                                            softWrap = false
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Text(
                                        text = audioEngineState.noiseGateSensitivity.description,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // Speech Articulation Focus Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Hearing,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Vocal Consonant Isolation",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Amplifies consonants ('s', 't', 'p') for speech recognition",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Switch(
                                checked = audioEngineState.speechFocusEnabled,
                                onCheckedChange = { viewModel.setSpeechFocus(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag("speech_focus_switch")
                            )
                        }

                        // Acoustic Feedback & Shriek Protection Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = SoundSafeGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Acoustic Feedback Limiter",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Dampens sudden loud dropped keys, clatters, and screeching",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Switch(
                                checked = audioEngineState.feedbackPreventionEnabled,
                                onCheckedChange = { viewModel.setFeedbackPrevention(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = SoundSafeGreen,
                                    checkedTrackColor = SoundSafeGreen.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.testTag("feedback_limiter_switch")
                            )
                        }
                    }
                }
            }

            // 5b. Smart Battery Monitor & DSP Eco Saver (Compact Sleek M3 Design)
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (batteryState.isEcoModeActive) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                    } else MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    border = BorderStroke(
                        1.dp,
                        if (batteryState.isEcoModeActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)
                        else if (batteryState.isLowBattery) MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("battery_eco_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            batteryState.isEcoModeActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            batteryState.isLowBattery -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                            else -> SoundSafeGreen.copy(alpha = 0.15f)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when {
                                        batteryState.isCharging -> Icons.Default.BatteryChargingFull
                                        batteryState.isLowBattery -> Icons.Default.BatteryAlert
                                        else -> Icons.Default.BatteryFull
                                    },
                                    contentDescription = "Battery Status",
                                    tint = when {
                                        batteryState.isCharging -> SoundSafeGreen
                                        batteryState.isLowBattery -> MaterialTheme.colorScheme.error
                                        batteryState.isEcoModeActive -> MaterialTheme.colorScheme.primary
                                        else -> SoundSafeGreen
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Battery Saver",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = when {
                                            batteryState.isLowBattery -> MaterialTheme.colorScheme.errorContainer
                                            batteryState.isCharging -> SoundSafeGreen.copy(alpha = 0.18f)
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            if (batteryState.isCharging) {
                                                Icon(
                                                    imageVector = Icons.Default.Bolt,
                                                    contentDescription = null,
                                                    tint = SoundSafeGreen,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                            Text(
                                                text = "${batteryState.level}%",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                ),
                                                color = if (batteryState.isLowBattery) MaterialTheme.colorScheme.onErrorContainer
                                                        else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = if (batteryState.isEcoModeActive) {
                                        "DSP Eco active • Low-power processing saves ~25% battery"
                                    } else {
                                        "Reduces DSP battery usage when battery is low"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = if (batteryState.isEcoModeActive) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }

                        Switch(
                            checked = batteryState.autoEcoModeEnabled,
                            onCheckedChange = { viewModel.setAutoEcoMode(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag("auto_eco_mode_switch")
                        )
                    }
                }
            }

            // 6. Frequency Clarity Equalizer
            item {
                FrequencyEqualizerView(
                    bandGains = bandGains,
                    selectedPreset = selectedPreset,
                    onBandGainChange = { freq, gain -> viewModel.setBandGain(freq, gain) },
                    onPresetSelect = { preset -> viewModel.selectPreset(preset) },
                    onResetFlat = { viewModel.resetEqFlat() },
                    hasCalibratedProfile = userProfile != null
                )
            }

            // 7. Pod Identification, Acoustic Profile & Hardware Controls
            item {
                PodControlsSection(
                    connectedDevice = connectedDevice,
                    hostDevice = hostDevice,
                    podControlsState = podControlsState,
                    onSetLeftVolume = { viewModel.setPodLeftVolume(it) },
                    onSetRightVolume = { viewModel.setPodRightVolume(it) },
                    onToggleLeftMute = { viewModel.toggleLeftPodMute() },
                    onToggleRightMute = { viewModel.toggleRightPodMute() },
                    onSetEarBalance = { viewModel.setPodEarBalance(it) },
                    onSetTransparencyMode = { viewModel.setPodTransparencyMode(it) },
                    onSetTransparencyLevel = { viewModel.setPodTransparencyLevel(it) },
                    onSetOcclusionCompensation = { viewModel.setOcclusionCompensation(it) },
                    onSetMicRouting = { viewModel.setPodMicRouting(it) },
                    onToggleInEarDetection = { viewModel.toggleInEarDetection() },
                    onToggleLeftInEar = { viewModel.toggleLeftPodInEar() },
                    onToggleRightInEar = { viewModel.toggleRightPodInEar() },
                    onToggleAutoAdapt = { viewModel.toggleAutoAdaptToPod(it) },
                    onSelectProfile = { viewModel.selectPodProfile(it) },
                    onOpenFitTest = { showFitTestDialog = true }
                )
            }

            // 8. Audio Engine Architecture & System Calibration Card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Audio Engine Switch: Oboe C++ (AAudio) vs Standard Android
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                                    text = "Audio Engine:",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val isOboe = audioEngineState.engineType == com.example.audio.AudioEngineType.OBOE_CPP
                                FilterChip(
                                    selected = isOboe,
                                    onClick = { viewModel.selectAudioEngine(com.example.audio.AudioEngineType.OBOE_CPP) },
                                    label = {
                                        Text(
                                            "Oboe C++ (AAudio)",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (isOboe) FontWeight.Bold else FontWeight.Normal
                                            )
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.testTag("engine_oboe")
                                )
                                FilterChip(
                                    selected = !isOboe,
                                    onClick = { viewModel.selectAudioEngine(com.example.audio.AudioEngineType.ANDROID_AUDIO) },
                                    label = {
                                        Text(
                                            "Standard Audio",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (!isOboe) FontWeight.Bold else FontWeight.Normal
                                            )
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.testTag("engine_standard")
                                )
                            }
                        }

                        // Host Phone Specs, Background Lock-Screen Support & Audiogram Calibration
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = SoundSafeGreen,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Lock-screen background active",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = SoundSafeGreen,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            FilledTonalButton(
                                onClick = {
                                    viewModel.startAudiogramTest()
                                    showAudiogramDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("calibrate_hearing_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (userProfile != null) "Recalibrate" else "Audiogram Test",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }

            // 9. Live Recording & Offline Storage Bar
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (audioEngineState.isRecording) SoundLoudRed.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (audioEngineState.isRecording) SoundLoudRed
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (audioEngineState.isRecording) SoundLoudRed else MaterialTheme.colorScheme.primaryContainer
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (audioEngineState.isRecording) Icons.Default.FiberManualRecord else Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = if (audioEngineState.isRecording) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = if (audioEngineState.isRecording) "Recording Live Session..." else "Record Hearing Session",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (audioEngineState.isRecording) {
                                        val min = audioEngineState.recordingDurationSeconds / 60
                                        val sec = audioEngineState.recordingDurationSeconds % 60
                                        String.format(Locale.US, "Duration: %02d:%02d • Saving to offline storage", min, sec)
                                    } else {
                                        "Save amplified audio for offline playback"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = if (audioEngineState.isRecording) SoundLoudRed else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (audioEngineState.isRecording) {
                                    viewModel.toggleRecording()
                                } else {
                                    if (audioEngineState.isRunning) {
                                        viewModel.toggleRecording()
                                    } else {
                                        viewModel.togglePower { onRequestRecordPermission() }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (audioEngineState.isRecording) SoundLoudRed else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .height(42.dp)
                                .testTag("record_session_button")
                        ) {
                            Icon(
                                imageVector = if (audioEngineState.isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (audioEngineState.isRecording) "Stop" else "Record")
                        }
                    }
                }
            }

            // Privacy & Compliance Footer
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showPrivacyDialog = true },
                        modifier = Modifier.testTag("privacy_policy_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Privacy Policy & Microphone Data Disclosure",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Dialogs & Sheets
    if (showFitTestDialog) {
        PodFitTestDialog(
            podName = connectedDevice.model.displayName,
            onRunTest = { viewModel.runEarTipFitTest() },
            onDismiss = { showFitTestDialog = false }
        )
    }

    if (showAudiogramDialog) {
        AudiogramDialog(
            state = audiogramState,
            onPlayTone = { viewModel.playCurrentCalibrationTone() },
            onUserHearsTone = { viewModel.userHearsTone() },
            onUserCannotHearTone = { viewModel.userCannotHearTone() },
            onApplyProfile = {
                viewModel.applyCalibratedProfile()
                showAudiogramDialog = false
            },
            onDismiss = {
                viewModel.cancelAudiogramTest()
                showAudiogramDialog = false
            }
        )
    }

    if (showRecordingsSheet) {
        RecordingsSheet(
            sessions = recordedSessions,
            playbackState = playbackState,
            onPlaySession = { viewModel.playSession(it) },
            onPauseSession = { viewModel.pausePlayback() },
            onDeleteSession = { viewModel.deleteSession(it) },
            onDismiss = {
                viewModel.stopPlayback()
                showRecordingsSheet = false
            }
        )
    }

    if (showSubscriptionDialog) {
        SubscriptionDialog(
            subscriptionState = subscriptionState,
            onUpgrade = { activity, plan ->
                viewModel.purchaseSubscription(activity, plan)
            },
            onCancel = {
                try {
                    val sku = subscriptionState.activePlan?.productId ?: "hearpod_pro_annual"
                    val uri = android.net.Uri.parse(
                        "https://play.google.com/store/account/subscriptions?sku=$sku&package=${context.packageName}"
                    )
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (_: Exception) {
                    viewModel.cancelSubscription()
                }
            },
            onDismiss = { showSubscriptionDialog = false }
        )
    }

    if (showBluetoothRequiredNotice) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissBluetoothRequiredNotice() },
            icon = {
                Icon(
                    imageVector = Icons.Default.HeadsetOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Bluetooth Earbuds Required",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "A Bluetooth headset or earbuds (such as AirPods, Galaxy Buds, or Pixel Buds) must be connected before starting the hearing aid.\n\nRunning high-volume amplification through the built-in phone speaker causes high-pitched acoustic howling, feedback, and echo.\n\nPlease connect your earbuds via Bluetooth, or tap Demo EarPods for virtual testing.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.dismissBluetoothRequiredNotice()
                            viewModel.enableSpeakerMode()
                            viewModel.togglePower(forceSpeaker = true) {
                                onRequestRecordPermission()
                            }
                        },
                        modifier = Modifier.testTag("dialog_start_speaker_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Use Speaker")
                    }
                    OutlinedButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Open Android Settings > Bluetooth to connect earbuds", Toast.LENGTH_LONG).show()
                            }
                            viewModel.dismissBluetoothRequiredNotice()
                        }
                    ) {
                        Text("Bluetooth")
                    }
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            viewModel.simulateBluetoothEarbuds(true)
                            viewModel.dismissBluetoothRequiredNotice()
                        }
                    ) {
                        Text("Demo Pods")
                    }
                    TextButton(onClick = { viewModel.dismissBluetoothRequiredNotice() }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    if (showAnalyticsDialog) {
        AnalyticsDialog(
            analytics = analytics,
            subscriptionState = subscriptionState,
            onTriggerCloudSync = { viewModel.triggerCloudBackup() },
            onToggleAutoBackup = { viewModel.toggleAutoBackup(it) },
            onOpenSubscription = {
                showAnalyticsDialog = false
                showSubscriptionDialog = true
            },
            onDismiss = { showAnalyticsDialog = false }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Privacy Policy",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "HearPod processes live microphone audio exclusively in-memory on your device for real-time hearing amplification and noise filtering. No audio or voice data is streamed to external servers.\n\nOptional session recordings (.wav) and audiogram profiles are saved locally on your device and can be deleted anytime.\n\nSubscriptions are processed securely through Google Play Billing.\n\nContact: brightquestofficial@gmail.com",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                Button(onClick = { showPrivacyDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
