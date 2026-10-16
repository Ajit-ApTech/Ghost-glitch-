package com.notchbuddy.android.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.notchbuddy.android.companion.GhostRenderer
import com.notchbuddy.android.model.*
import com.notchbuddy.android.settings.AppSettings
import com.notchbuddy.android.telemetry.NetworkSpeedMonitor
import com.notchbuddy.android.telemetry.SpeedTestEngine
import com.notchbuddy.android.telemetry.SpeedTestResult
import kotlinx.coroutines.launch
import java.util.Locale

class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}

class ExpandingHudSheet(
    private val context: Context,
    private val appSettings: AppSettings,
    private val onCloseRequested: () -> Unit,
    private val onQuitRequested: () -> Unit
) {

    private val _telemetry = mutableStateOf(TelemetrySnapshot())
    private val _topApps = mutableStateOf<List<AppBandwidthUsage>>(emptyList())
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    fun updateTelemetry(snapshot: TelemetrySnapshot) {
        _telemetry.value = snapshot
    }

    fun updateTopApps(apps: List<AppBandwidthUsage>) {
        _topApps.value = apps
    }

    fun destroy() {
        lifecycleOwner?.destroy()
        lifecycleOwner = null
    }

    fun createView(): ComposeView {
        val owner = OverlayLifecycleOwner()
        this.lifecycleOwner = owner

        return ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                val currentTelemetry by _telemetry
                val topAppsList by _topApps
                val reactionSource by appSettings.reactionSource.collectAsState()
                val speedUnit by appSettings.speedUnit.collectAsState()
                val happyThreshold by appSettings.happyThreshold.collectAsState()
                val focusThreshold by appSettings.focusThreshold.collectAsState()
                val strainThreshold by appSettings.strainThreshold.collectAsState()

                var showSettings by remember { mutableStateOf(false) }

                HudMasterView(
                    telemetry = currentTelemetry,
                    topApps = topAppsList,
                    reactionSource = reactionSource,
                    speedUnit = speedUnit,
                    happyThreshold = happyThreshold,
                    focusThreshold = focusThreshold,
                    strainThreshold = strainThreshold,
                    showSettings = showSettings,
                    onToggleSettings = { showSettings = !showSettings },
                    onReactionChanged = { appSettings.setReactionSource(it) },
                    onSpeedUnitToggle = {
                        val nextUnit = if (speedUnit == SpeedUnit.BITS) SpeedUnit.BYTES else SpeedUnit.BITS
                        appSettings.setSpeedUnit(nextUnit)
                    },
                    onThresholdsChanged = { h, f, s -> appSettings.setThresholds(h, f, s) },
                    onCopyIp = { ip ->
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("IP Address", ip))
                        Toast.makeText(context, "Copied IP: $ip", Toast.LENGTH_SHORT).show()
                    },
                    onClose = onCloseRequested,
                    onQuit = onQuitRequested
                )
            }
        }
    }

    @Composable
    private fun HudMasterView(
        telemetry: TelemetrySnapshot,
        topApps: List<AppBandwidthUsage>,
        reactionSource: ReactionSource,
        speedUnit: SpeedUnit,
        happyThreshold: Float,
        focusThreshold: Float,
        strainThreshold: Float,
        showSettings: Boolean,
        onToggleSettings: () -> Unit,
        onReactionChanged: (ReactionSource) -> Unit,
        onSpeedUnitToggle: () -> Unit,
        onThresholdsChanged: (Float, Float, Float) -> Unit,
        onCopyIp: (String) -> Unit,
        onClose: () -> Unit,
        onQuit: () -> Unit
    ) {
        val obsidianBg = Color(0xFF0A0D14)
        val obsidianCard = Color(0xFF0E131F)
        val cardBorder = Color(0xFF1C2438)
        val emeraldColor = Color(0xFF10B981)
        val cyanColor = Color(0xFF06B6D4)
        val purpleColor = Color(0xFFA855F7)
        val amberColor = Color(0xFFF59E0B)

        val ghostRenderer = remember { GhostRenderer() }
        val speedTestEngine = remember { SpeedTestEngine() }
        val coroutineScope = rememberCoroutineScope()
        var isTestingSpeed by remember { mutableStateOf(false) }
        var speedTestStatus by remember { mutableStateOf("Testing...") }
        var speedTestResult by remember { mutableStateOf<SpeedTestResult?>(null) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable { onClose() },
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 40.dp, start = 14.dp, end = 14.dp, bottom = 20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(obsidianBg)
                    .border(1.dp, cardBorder, RoundedCornerShape(26.dp))
                    .clickable(enabled = false) {}
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── 1. Top Header Row (Identical to Mac) ───────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Glitch Avatar + Title + Dynamic Personality Subtitle
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Glitch Badge
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(obsidianCard)
                                .border(1.dp, cardBorder, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(34.dp)) {
                                drawIntoCanvas { canvas ->
                                    ghostRenderer.draw(
                                        canvas = canvas.nativeCanvas,
                                        width = size.width,
                                        height = size.height,
                                        tier = telemetry.tier,
                                        timeSeconds = 1.0f
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Glitch · Notch Ghost",
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (reactionSource == ReactionSource.CPU_LOAD) "(🧠 CPU)" else "(⚡ Speed)",
                                    color = if (reactionSource == ReactionSource.CPU_LOAD) purpleColor else emeraldColor,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = telemetry.tierDescription,
                                color = if (reactionSource == ReactionSource.CPU_LOAD) purpleColor.copy(alpha = 0.9f) else emeraldColor.copy(alpha = 0.9f),
                                fontSize = 10.5.sp,
                                maxLines = 1
                            )
                        }
                    }

                    // Right: Power (Quit) Button + Settings Gear (Cleanly spaced, NO overlap)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Power Quit Button
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2E1018))
                                .border(1.dp, Color(0xFFF43F5E).copy(alpha = 0.40f), CircleShape)
                                .clickable { onQuit() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PowerSettingsNew,
                                contentDescription = "Quit",
                                tint = Color(0xFFF43F5E),
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        // Settings Gear / Close Toggle
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (showSettings) emeraldColor.copy(alpha = 0.20f) else obsidianCard)
                                .border(1.dp, if (showSettings) emeraldColor else cardBorder, CircleShape)
                                .clickable { onToggleSettings() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (showSettings) Icons.Default.Close else Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = if (showSettings) emeraldColor else Color(0xFF94A3B8),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }

                if (!showSettings) {
                    // ════════════════════ MAIN HUD DASHBOARD ════════════════════

                    // ── 2. Download & Upload Speed Cards Row (with Peak Speeds) ─
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Download Card
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = obsidianCard),
                            border = BorderStroke(1.dp, cardBorder)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("DOWNLOAD SPEED", color = Color(0xFF94A3B8), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "↓ ${telemetry.primaryValueFormatted}",
                                    color = emeraldColor,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Peak: ${telemetry.peakDownloadFormatted}",
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Upload Card
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = obsidianCard),
                            border = BorderStroke(1.dp, cardBorder)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("UPLOAD SPEED", color = Color(0xFF94A3B8), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "↑ ${telemetry.secondaryValueFormatted}",
                                    color = cyanColor,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Peak: ${telemetry.peakUploadFormatted}",
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // ── 3. Top Bandwidth Apps Card ─────────────────────────────
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = obsidianCard),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🔥", fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "TOP BANDWIDTH APPS",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "REAL-TIME",
                                    color = cyanColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            if (topApps.isEmpty()) {
                                Text("Scanning active sockets...", color = Color(0xFF64748B), fontSize = 11.5.sp)
                            } else {
                                topApps.take(4).forEach { app ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("⚡", color = amberColor, fontSize = 11.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = app.appName,
                                                color = Color(0xFFE2E8F0),
                                                fontSize = 12.sp,
                                                maxLines = 1
                                            )
                                        }
                                        Text(
                                            text = app.usageFormatted,
                                            color = emeraldColor,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── 4. Session / Monthly Data Card with Progress Bar ───────
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = obsidianCard),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SESSION / MONTHLY DATA",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                val sessionFormatted = NetworkSpeedMonitor.formatBytes(telemetry.sessionTotalBytes)
                                Text(
                                    text = "$sessionFormatted / 100 GB (1%)",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            // Progress track
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFF1E293B))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.12f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(cyanColor)
                                )
                            }
                        }
                    }

                    // ── 5. Interface, IP Address & Session Chips Row ───────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Interface Chip
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(obsidianCard)
                                .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Column {
                                Text("INTERFACE", color = Color(0xFF64748B), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(telemetry.interfaceName, color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }

                        // IP Address Chip (with copy button)
                        Box(
                            modifier = Modifier
                                .weight(1.3f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(obsidianCard)
                                .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Column {
                                Text("IP ADDRESS", color = Color(0xFF64748B), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = telemetry.ipAddress,
                                        color = Color.White,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF1E293B))
                                            .clickable { onCopyIp(telemetry.ipAddress) }
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text("copy", color = cyanColor, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Session Chip
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(obsidianCard)
                                .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Column {
                                Text("SESSION", color = Color(0xFF64748B), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = NetworkSpeedMonitor.formatBytes(telemetry.sessionTotalBytes),
                                    color = Color.White,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // ── 6. Bottom Action Buttons Row ───────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Run Speedtest Button
                        Button(
                            onClick = {
                                if (!isTestingSpeed) {
                                    isTestingSpeed = true
                                    speedTestStatus = "Ping..."
                                    coroutineScope.launch {
                                        try {
                                            val res = speedTestEngine.runSpeedTest { status, _ ->
                                                speedTestStatus = status
                                            }
                                            speedTestResult = res
                                            Toast.makeText(context, "Latency: ${res.pingMs}ms · Down: ${String.format(Locale.US, "%.1f", res.downloadMbps)} Mbps · Up: ${String.format(Locale.US, "%.1f", res.uploadMbps)} Mbps", Toast.LENGTH_LONG).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Speedtest failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isTestingSpeed = false
                                        }
                                    }
                                }
                            },
                            enabled = !isTestingSpeed,
                            modifier = Modifier.weight(1.3f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            if (isTestingSpeed) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = speedTestStatus,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            } else {
                                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (speedTestResult != null) {
                                        "↓${speedTestResult!!.downloadMbps.toInt()}M ↑${speedTestResult!!.uploadMbps.toInt()}M"
                                    } else "Run Speedtest",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Unit Toggle Button
                        OutlinedButton(
                            onClick = onSpeedUnitToggle,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, cardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Text(
                                text = if (speedUnit == SpeedUnit.BITS) "⇄ to MB/s" else "⇄ to Kbps",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Close Button
                        Button(
                            onClick = onClose,
                            modifier = Modifier.weight(0.9f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Text("Close", fontSize = 12.sp, color = Color(0xFFCBD5E1))
                        }
                    }

                    // Speedtest Results Banner
                    if (speedTestResult != null) {
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, emeraldColor.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Speed, contentDescription = null, tint = emeraldColor, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Ping ${speedTestResult!!.pingMs}ms", color = Color(0xFF94A3B8), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                                Text(
                                    "↓ ${String.format(Locale.US, "%.1f", speedTestResult!!.downloadMbps)} Mbps · ↑ ${String.format(Locale.US, "%.1f", speedTestResult!!.uploadMbps)} Mbps",
                                    color = emeraldColor,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                } else {
                    // ════════════════════ IN-HUD SETTINGS PANEL ════════════════════

                    // Preferences & Telemetry
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = obsidianCard),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("PREFERENCES & TELEMETRY", color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            // Reaction Source Pills
                            Text("Companion Reaction Source:", color = Color(0xFFCBD5E1), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val isSpeed = reactionSource == ReactionSource.INTERNET_SPEED
                                Button(
                                    onClick = { onReactionChanged(ReactionSource.INTERNET_SPEED) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSpeed) Color(0xFF1E293B) else Color.Transparent,
                                        contentColor = if (isSpeed) emeraldColor else Color(0xFF94A3B8)
                                    ),
                                    border = BorderStroke(1.dp, if (isSpeed) emeraldColor else cardBorder),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("⚡ Internet Speed", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { onReactionChanged(ReactionSource.CPU_LOAD) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (!isSpeed) purpleColor.copy(alpha = 0.25f) else Color.Transparent,
                                        contentColor = if (!isSpeed) purpleColor else Color(0xFF94A3B8)
                                    ),
                                    border = BorderStroke(1.dp, if (!isSpeed) purpleColor else cardBorder),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("🧠 CPU Load", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Telemetry Unit Display Pills
                            Text("Telemetry Unit Display:", color = Color(0xFFCBD5E1), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val isBytes = speedUnit == SpeedUnit.BYTES
                                Button(
                                    onClick = { if (!isBytes) onSpeedUnitToggle() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isBytes) cyanColor.copy(alpha = 0.25f) else Color.Transparent,
                                        contentColor = if (isBytes) cyanColor else Color(0xFF94A3B8)
                                    ),
                                    border = BorderStroke(1.dp, if (isBytes) cyanColor else cardBorder),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Bytes (KB/s, MB/s)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { if (isBytes) onSpeedUnitToggle() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (!isBytes) cyanColor.copy(alpha = 0.25f) else Color.Transparent,
                                        contentColor = if (!isBytes) cyanColor else Color(0xFF94A3B8)
                                    ),
                                    border = BorderStroke(1.dp, if (!isBytes) cyanColor else cardBorder),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Bits (Kbps, Mbps)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Network Speed Thresholds
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = obsidianCard),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🎯", fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("NETWORK SPEED THRESHOLDS", color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            // Happy Slider
                            ThresholdSlider(
                                emoji = "😊",
                                label = "Happy Threshold",
                                value = happyThreshold,
                                range = 10f..200f,
                                tint = emeraldColor,
                                onValueChange = { onThresholdsChanged(it, focusThreshold, strainThreshold) }
                            )

                            // Focus Slider
                            ThresholdSlider(
                                emoji = "🎯",
                                label = "Focus Threshold",
                                value = focusThreshold,
                                range = 100f..1000f,
                                tint = cyanColor,
                                onValueChange = { onThresholdsChanged(happyThreshold, it, strainThreshold) }
                            )

                            // Strain Slider
                            ThresholdSlider(
                                emoji = "😤",
                                label = "Strain Threshold",
                                value = strainThreshold,
                                range = 500f..5000f,
                                tint = amberColor,
                                onValueChange = { onThresholdsChanged(happyThreshold, focusThreshold, it) }
                            )
                        }
                    }

                    // Quit Ghost Glitch Danger Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onQuit() },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E1018)),
                        border = BorderStroke(1.dp, Color(0xFFF43F5E).copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = Color(0xFFF43F5E), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Quit Ghost Glitch", color = Color(0xFFF43F5E), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Text("1-TAP", color = Color(0xFFF43F5E).copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ThresholdSlider(
        emoji: String,
        label: String,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        tint: Color,
        onValueChange: (Float) -> Unit
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("$emoji $label", color = Color(0xFFE2E8F0), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(tint.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "${value.toInt()} KB/s",
                        color = tint,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = tint,
                    activeTrackColor = tint
                )
            )
        }
    }
}
