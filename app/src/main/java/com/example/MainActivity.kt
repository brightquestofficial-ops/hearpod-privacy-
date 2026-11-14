package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.DashboardScreen
import com.example.ui.HearingAidViewModel
import com.example.ui.theme.HearPodTheme

class MainActivity : ComponentActivity() {
    private val viewModel: HearingAidViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()

            HearPodTheme(darkTheme = isDarkMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HearPodApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (isFinishing) {
                viewModel.stopAmplification()
            }
        } catch (e: Throwable) {
            // Ignore
        }
    }
}

@Composable
fun HearPodApp(viewModel: HearingAidViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val audioEngineState by viewModel.engineState.collectAsStateWithLifecycle()
    val connectedDevice by viewModel.connectedDevice.collectAsStateWithLifecycle()
    val hostDevice by viewModel.hostDevice.collectAsStateWithLifecycle()
    val bandGains by viewModel.bandGains.collectAsStateWithLifecycle()
    val selectedPreset by viewModel.selectedPreset.collectAsStateWithLifecycle()
    val audiogramState by viewModel.audiogramState.collectAsStateWithLifecycle()
    val userProfile by viewModel.userHearingProfile.collectAsStateWithLifecycle()
    val recordedSessions by viewModel.recordedSessions.collectAsStateWithLifecycle()
    val subscriptionState by viewModel.subscriptionState.collectAsStateWithLifecycle()
    val analytics by viewModel.analytics.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val isAiOptimizing by viewModel.isAiOptimizing.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (recordAudioGranted) {
            val started = viewModel.startAmplification()
            if (!started) {
                Toast.makeText(context, "Unable to initialize audio hardware", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(
                context,
                "Microphone permission is required to amplify sound for your earpods",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val requestPermissionsAndStart: () -> Unit = {
        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasAudio) {
            val started = viewModel.startAmplification()
            if (!started) {
                Toast.makeText(context, "Unable to initialize audio hardware", Toast.LENGTH_SHORT).show()
            }
        } else {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    // Auto-prompt microphone permissions on first open so user can immediately hear amplified sound
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasAudio) {
            val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    DashboardScreen(
        viewModel = viewModel,
        audioEngineState = audioEngineState,
        connectedDevice = connectedDevice,
        hostDevice = hostDevice,
        bandGains = bandGains,
        selectedPreset = selectedPreset,
        audiogramState = audiogramState,
        userProfile = userProfile,
        recordedSessions = recordedSessions,
        subscriptionState = subscriptionState,
        analytics = analytics,
        playbackState = playbackState,
        isDarkMode = isDarkMode,
        isAiOptimizing = isAiOptimizing,
        onRequestRecordPermission = requestPermissionsAndStart
    )
}
