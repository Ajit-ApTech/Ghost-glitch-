package com.notchbuddy.android

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notchbuddy.android.companion.GhostRenderer
import com.notchbuddy.android.model.GhostExpressionTier
import com.notchbuddy.android.model.ReactionSource
import com.notchbuddy.android.model.SpeedUnit
import com.notchbuddy.android.overlay.OverlayService
import com.notchbuddy.android.settings.AppSettings
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var appSettings: AppSettings

    private val hasOverlayState = mutableStateOf(false)
    private val hasUsageState = mutableStateOf(false)
    private val isBatteryOptimizedState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appSettings = AppSettings.getInstance(this)

        updatePermissions()

        setContent {
            DashboardScreen(
                appSettings = appSettings,
                hasOverlayPermission = hasOverlayState.value,
                hasUsagePermission = hasUsageState.value,
                isBatteryOptimized = isBatteryOptimizedState.value,
                onRequestOverlayPermission = { requestOverlayPermission() },
                onRequestUsagePermission = { requestUsagePermission() },
                onRequestBatteryExemption = { requestBatteryOptimizationExemption() },
                onStartOverlay = { startOverlayService() },
                onStopOverlay = { stopOverlayService() }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissions()
    }

    private fun updatePermissions() {
        hasOverlayState.value = checkOverlayPermission()
        hasUsageState.value = checkUsagePermission()
        isBatteryOptimizedState.value = checkBatteryOptimization()
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun checkUsagePermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestUsagePermission() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun checkBatteryOptimization(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else true
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopOverlayService() {
        stopService(Intent(this, OverlayService::class.java))
    }
}

@Composable
fun DashboardScreen(
    appSettings: AppSettings,
    hasOverlayPermission: Boolean,
    hasUsagePermission: Boolean,
    isBatteryOptimized: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestUsagePermission: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit
) {
    val reactionSource by appSettings.reactionSource.collectAsState()
    val speedUnit by appSettings.speedUnit.collectAsState()
    val hapticEnabled by appSettings.hapticFeedbackEnabled.collectAsState()
    val haloEnabled by appSettings.haloEnabled.collectAsState()
    val happyThreshold by appSettings.happyThreshold.collectAsState()
    val focusThreshold by appSettings.focusThreshold.collectAsState()
    val strainThreshold by appSettings.strainThreshold.collectAsState()

    val isServiceRunning by OverlayService.isRunning.collectAsState()
    val companionActive by appSettings.companionActive.collectAsState()
    val notchRingActive by appSettings.notchRingActive.collectAsState()

    val isCompanionLive = isServiceRunning && companionActive
    val isNotchLive = isServiceRunning && notchRingActive

    val obsidianBg = Color(0xFF0A0D14)
    val obsidianCard = Color(0xFF0E131F)
    val cardBorder = Color(0xFF1C2438)
    val emerald = Color(0xFF10B981)
    val cyan = Color(0xFF06B6D4)
    val purple = Color(0xFFA855F7)
    val amber = Color(0xFFF59E0B)

    // Companion preview animation loop
    var animTime by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(16)
            animTime += 0.016f
        }
    }

    val ghostRenderer = remember { GhostRenderer() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = obsidianBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // App Title & Tagline
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(emerald)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GHOST GLITCH",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Punch-Hole Notch Speed Ring & Glitch Ghost Companion",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
            }

            // Interactive Live Preview Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = obsidianCard),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "LIVE PROCEDURAL COMPANION PREVIEW",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Ghost Canvas preview
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF060910))
                            .border(1.dp, cardBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(80.dp)) {
                            drawIntoCanvas { canvas ->
                                ghostRenderer.draw(
                                    canvas = canvas.nativeCanvas,
                                    width = size.width,
                                    height = size.height,
                                    tier = GhostExpressionTier.HAPPY,
                                    timeSeconds = animTime
                                )
                            }
                        }
                    }
                }
            }

            // SEPARATE LAUNCH CONTROL BARS
            Text(
                text = "OVERLAY CONTROLS",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            // Bar 1: Floating Glitch Companion
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = obsidianCard),
                border = BorderStroke(1.dp, if (isCompanionLive) emerald.copy(alpha = 0.5f) else cardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("👻", fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Floating Companion",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "60dp draggable companion · Double-tap opens HUD",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Surface(
                            color = if (isCompanionLive) emerald.copy(alpha = 0.15f) else Color(0xFF1E293B),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isCompanionLive) "ACTIVE" else "OFF",
                                color = if (isCompanionLive) emerald else Color(0xFF64748B),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (isCompanionLive) {
                                appSettings.setCompanionActive(false)
                                if (!notchRingActive) {
                                    onStopOverlay()
                                }
                            } else {
                                if (hasOverlayPermission) {
                                    appSettings.setCompanionActive(true)
                                    if (!isServiceRunning) onStartOverlay()
                                } else {
                                    onRequestOverlayPermission()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCompanionLive) Color(0xFFE11D48) else emerald
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (isCompanionLive) Icons.Default.Close else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isCompanionLive) "Turn Off Floating Companion" else "Launch Floating Companion",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Bar 2: Camera Notch Speed Ring
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = obsidianCard),
                border = BorderStroke(1.dp, if (isNotchLive) cyan.copy(alpha = 0.5f) else cardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("⚡", fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Camera Notch Speed Ring",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Horizontal meter: ↓ Down  ↑ Up  ( ◯ )  CPU %",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Surface(
                            color = if (isNotchLive) cyan.copy(alpha = 0.15f) else Color(0xFF1E293B),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isNotchLive) "ACTIVE" else "OFF",
                                color = if (isNotchLive) cyan else Color(0xFF64748B),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (isNotchLive) {
                                appSettings.setNotchRingActive(false)
                                if (!companionActive) {
                                    onStopOverlay()
                                }
                            } else {
                                if (hasOverlayPermission) {
                                    appSettings.setNotchRingActive(true)
                                    if (!isServiceRunning) onStartOverlay()
                                } else {
                                    onRequestOverlayPermission()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isNotchLive) Color(0xFFE11D48) else cyan
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (isNotchLive) Icons.Default.Close else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isNotchLive) "Turn Off Speed Ring" else "Launch Notch Speed Ring",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Optional "Stop All" shortcut when service is active
            if (isServiceRunning) {
                OutlinedButton(
                    onClick = {
                        appSettings.setCompanionActive(false)
                        appSettings.setNotchRingActive(false)
                        onStopOverlay()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF475569))
                ) {
                    Text("Stop All Overlays", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }

            // Permissions Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = obsidianCard),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "SYSTEM PERMISSIONS",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Overlay Permission Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Draw over other apps", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Allows floating halo & companion", color = Color(0xFF64748B), fontSize = 11.sp)
                        }
                        if (hasOverlayPermission) {
                            Text("Granted", color = emerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            OutlinedButton(onClick = onRequestOverlayPermission) {
                                Text("Grant", fontSize = 11.sp)
                            }
                        }
                    }

                    // Battery Exemption Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Battery Optimization Exemption", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Prevents OS from killing companion", color = Color(0xFF64748B), fontSize = 11.sp)
                        }
                        if (isBatteryOptimized) {
                            Text("Exempted", color = emerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            OutlinedButton(onClick = onRequestBatteryExemption) {
                                Text("Exempt", fontSize = 11.sp)
                            }
                        }
                    }

                    // Usage Access Row (Optional)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("App Usage Stats (Optional)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("For Top Bandwidth Apps breakdown", color = Color(0xFF64748B), fontSize = 11.sp)
                        }
                        if (hasUsagePermission) {
                            Text("Granted", color = emerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            OutlinedButton(onClick = onRequestUsagePermission) {
                                Text("Enable", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Preferences Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = obsidianCard),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "PREFERENCES & REACTION MODE",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Reaction Source
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isSpeed = reactionSource == ReactionSource.INTERNET_SPEED
                        Button(
                            onClick = { appSettings.setReactionSource(ReactionSource.INTERNET_SPEED) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSpeed) emerald.copy(alpha = 0.20f) else Color.Transparent,
                                contentColor = if (isSpeed) emerald else Color(0xFF94A3B8)
                            ),
                            border = BorderStroke(1.dp, if (isSpeed) emerald else cardBorder),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("⚡ Internet Speed", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = { appSettings.setReactionSource(ReactionSource.CPU_LOAD) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isSpeed) purple.copy(alpha = 0.20f) else Color.Transparent,
                                contentColor = if (!isSpeed) purple else Color(0xFF94A3B8)
                            ),
                            border = BorderStroke(1.dp, if (!isSpeed) purple else cardBorder),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("🧠 CPU Load", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Punch-Hole Halo Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Punch-Hole Camera Halo (Option B)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Glowing reactive ring around front camera", color = Color(0xFF64748B), fontSize = 11.sp)
                        }
                        Switch(
                            checked = haloEnabled,
                            onCheckedChange = { appSettings.setHaloEnabled(it) }
                        )
                    }

                    // Haptic Feedback Toggle (Defaults to FALSE)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Subtle Haptic Feedback", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Vibrate on Glitch/Panic state (Default: Off)", color = Color(0xFF64748B), fontSize = 11.sp)
                        }
                        Switch(
                            checked = hapticEnabled,
                            onCheckedChange = { appSettings.setHapticFeedbackEnabled(it) }
                        )
                    }

                    // Telemetry Units (Bytes vs Bits)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Telemetry Unit", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val isBytes = speedUnit == SpeedUnit.BYTES
                            FilterChip(
                                selected = isBytes,
                                onClick = { appSettings.setSpeedUnit(SpeedUnit.BYTES) },
                                label = { Text("Bytes (KB/s)") }
                            )
                            FilterChip(
                                selected = !isBytes,
                                onClick = { appSettings.setSpeedUnit(SpeedUnit.BITS) },
                                label = { Text("Bits (Kbps)") }
                            )
                        }
                    }
                }
            }

            // Speed Threshold Sliders Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = obsidianCard),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "SPEED THRESHOLDS (KB/S)",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Happy Threshold
                    ThresholdSliderRow(
                        title = "Happy / Active Threshold",
                        value = happyThreshold,
                        range = 10f..200f,
                        color = emerald,
                        onValueChange = { appSettings.setThresholds(it, focusThreshold, strainThreshold) }
                    )

                    // Focus Threshold
                    ThresholdSliderRow(
                        title = "Focus Threshold",
                        value = focusThreshold,
                        range = 100f..1000f,
                        color = cyan,
                        onValueChange = { appSettings.setThresholds(happyThreshold, it, strainThreshold) }
                    )

                    // Strain Threshold
                    ThresholdSliderRow(
                        title = "Strain / Glitch Threshold",
                        value = strainThreshold,
                        range = 500f..5000f,
                        color = amber,
                        onValueChange = { appSettings.setThresholds(happyThreshold, focusThreshold, it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
fun ThresholdSliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    color: Color,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = Color(0xFFCBD5E1), fontSize = 12.sp)
            Text(
                "${value.toInt()} KB/s",
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color
            )
        )
    }
}
