package com.notchbuddy.android.overlay

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.notchbuddy.android.MainActivity
import com.notchbuddy.android.R
import com.notchbuddy.android.cutout.CutoutDetector
import com.notchbuddy.android.model.GhostExpressionTier
import com.notchbuddy.android.model.ReactionSource
import com.notchbuddy.android.model.TelemetrySnapshot
import com.notchbuddy.android.settings.AppSettings
import com.notchbuddy.android.telemetry.CpuMonitor
import com.notchbuddy.android.telemetry.NetworkSpeedMonitor
import com.notchbuddy.android.telemetry.TopAppsMonitor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class OverlayService : Service() {

    companion object {
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }

    private lateinit var windowManager: WindowManager
    private lateinit var appSettings: AppSettings
    private lateinit var cutoutDetector: CutoutDetector
    private lateinit var speedMonitor: NetworkSpeedMonitor
    private lateinit var cpuMonitor: CpuMonitor
    private lateinit var topAppsMonitor: TopAppsMonitor

    private var speedRingView: PunchHoleSpeedRingView? = null
    private var companionView: DraggableCompanionView? = null
    private var hudSheet: ExpandingHudSheet? = null
    private var hudComposeView: View? = null

    private var isHudOpen = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        appSettings = AppSettings.getInstance(this)
        cutoutDetector = CutoutDetector(this)
        speedMonitor = NetworkSpeedMonitor()
        cpuMonitor = CpuMonitor()
        topAppsMonitor = TopAppsMonitor(this)

        startForegroundServiceWithNotification()

        if (appSettings.notchRingActive.value) {
            setupTopSpeedRing()
        }
        if (appSettings.companionActive.value) {
            setupDraggableCompanion()
        }

        observeOverlayToggles()
        startTelemetryLoop()
    }

    private fun observeOverlayToggles() {
        scope.launch {
            appSettings.notchRingActive.collect { active ->
                if (active) {
                    if (speedRingView == null) setupTopSpeedRing()
                } else {
                    removeTopSpeedRing()
                    checkShouldStopService()
                }
            }
        }
        scope.launch {
            appSettings.companionActive.collect { active ->
                if (active) {
                    if (companionView == null) setupDraggableCompanion()
                } else {
                    removeDraggableCompanion()
                    checkShouldStopService()
                }
            }
        }
    }

    private fun checkShouldStopService() {
        if (!appSettings.notchRingActive.value && !appSettings.companionActive.value) {
            stopSelf()
        }
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "ghost_glitch_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Ghost Glitch Telemetry Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live status and keeps punch-hole overlay alive"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Ghost Glitch Active")
            .setContentText("Camera punch-hole speed ring & floating Glitch live")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                1001,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(1001, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun setupTopSpeedRing() {
        val cutoutInfo = cutoutDetector.detectCutout(windowManager)

        val ringView = PunchHoleSpeedRingView(this, cutoutInfo) {
            toggleHud()
        }

        val density = resources.displayMetrics.density
        // Single-line horizontal speed metrics: 196dp wide x 38dp tall, transparent background
        val widthPx = (196 * density).toInt()
        val heightPx = (38 * density).toInt()

        val ringParams = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        ringParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        val topOffset = (cutoutInfo.centerY - (heightPx / 2)).toInt().coerceAtLeast(0)
        ringParams.y = topOffset

        try {
            windowManager.addView(ringView, ringParams)
            this.speedRingView = ringView
        } catch (e: Exception) {
            android.util.Log.e("GhostGlitch", "Could not add PunchHoleSpeedRingView", e)
        }
    }

    private fun setupDraggableCompanion() {
        val density = resources.displayMetrics.density
        // 60dp companion size as requested
        val companionSizePx = (60 * density).toInt()

        val companionParams = WindowManager.LayoutParams(
            companionSizePx,
            companionSizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        companionParams.gravity = Gravity.TOP or Gravity.START

        val savedX = appSettings.companionPosX.value
        val savedY = appSettings.companionPosY.value

        if (savedX >= 0 && savedY >= 0) {
            companionParams.x = savedX
            companionParams.y = savedY
        } else {
            // Default position: centered, floating comfortably below the status bar (~85dp from top)
            val screenWidth = resources.displayMetrics.widthPixels
            companionParams.x = (screenWidth / 2) - (companionSizePx / 2)
            companionParams.y = (85 * density).toInt()
        }

        val companion = DraggableCompanionView(
            context = this,
            windowManager = windowManager,
            layoutParams = companionParams,
            appSettings = appSettings,
            onDoubleTapped = { toggleHud() }
        )

        try {
            windowManager.addView(companion, companionParams)
            this.companionView = companion
        } catch (e: Exception) {
            android.util.Log.e("NotchBuddy", "Could not add DraggableCompanionView", e)
        }
    }

    private fun toggleHud() {
        if (isHudOpen) {
            dismissHud()
        } else {
            showHud()
        }
    }

    private fun showHud() {
        if (isHudOpen) return

        val sheet = ExpandingHudSheet(
            context = this,
            appSettings = appSettings,
            onCloseRequested = { dismissHud() },
            onQuitRequested = {
                dismissHud()
                stopSelf()
            }
        )
        val view = sheet.createView()

        val hudParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager.addView(view, hudParams)
            this.hudSheet = sheet
            this.hudComposeView = view
            this.isHudOpen = true
        } catch (e: Exception) {
            android.util.Log.e("NotchBuddy", "Could not open HUD", e)
        }
    }

    private fun dismissHud() {
        hudComposeView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        hudSheet?.destroy()
        hudComposeView = null
        hudSheet = null
        isHudOpen = false
    }

    private fun startTelemetryLoop() {
        speedMonitor.start()
        cpuMonitor.start()

        var (realIface, realIp) = NetworkSpeedMonitor.resolveInterfaceAndIp(this)
        var networkCheckCounter = 0

        scope.launch {
            while (isActive) {
                networkCheckCounter++
                if (networkCheckCounter % 3 == 0) {
                    val refreshed = NetworkSpeedMonitor.resolveInterfaceAndIp(this@OverlayService)
                    realIface = refreshed.first
                    realIp = refreshed.second
                }

                val rx = speedMonitor.downloadBytesPerSec.value
                val tx = speedMonitor.uploadBytesPerSec.value
                val peakRx = speedMonitor.peakDownloadBytes.value
                val peakTx = speedMonitor.peakUploadBytes.value
                val cpu = cpuMonitor.cpuUsagePercent.value
                val ping = speedMonitor.pingMs.value
                val source = appSettings.reactionSource.value
                val unit = appSettings.speedUnit.value
                val history = speedMonitor.speedHistory.value
                val cpuHist = cpuMonitor.cpuHistory.value
                val sessionBytes = speedMonitor.sessionTotalBytes.value

                val tier = computeTier(rx, cpu, source)
                val description = computeTierDescription(tier, source)

                val snapshot = TelemetrySnapshot(
                    downloadBytesPerSec = rx,
                    uploadBytesPerSec = tx,
                    peakDownloadBytesPerSec = peakRx,
                    peakUploadBytesPerSec = peakTx,
                    cpuUsagePercent = cpu,
                    pingMs = ping,
                    tier = tier,
                    speedHistory = history,
                    cpuHistory = cpuHist,
                    interfaceName = realIface,
                    ipAddress = realIp,
                    sessionTotalBytes = sessionBytes,
                    primaryValueFormatted = NetworkSpeedMonitor.formatSpeed(rx, unit),
                    secondaryValueFormatted = NetworkSpeedMonitor.formatSpeed(tx, unit),
                    peakDownloadFormatted = NetworkSpeedMonitor.formatSpeed(peakRx, unit),
                    peakUploadFormatted = NetworkSpeedMonitor.formatSpeed(peakTx, unit),
                    cpuFormatted = String.format(Locale.US, "%.0f%%", cpu),
                    tierDescription = description
                )

                speedRingView?.updateTelemetry(snapshot, unit, source)
                companionView?.updateTelemetry(snapshot)
                hudSheet?.updateTelemetry(snapshot)

                delay(1000)
            }
        }

        // Top apps telemetry loop (every 4 seconds)
        scope.launch {
            while (isActive) {
                if (isHudOpen) {
                    val apps = topAppsMonitor.getTopConsumingApps()
                    hudSheet?.updateTopApps(apps)
                }
                delay(4000)
            }
        }
    }

    private fun computeTier(downloadBytes: Double, cpuUsage: Double, source: ReactionSource): GhostExpressionTier {
        return if (source == ReactionSource.CPU_LOAD) {
            when {
                cpuUsage >= 95.0 -> GhostExpressionTier.DEAD
                cpuUsage >= 85.0 -> GhostExpressionTier.PANIC
                cpuUsage >= 65.0 -> GhostExpressionTier.STRAIN
                cpuUsage >= 40.0 -> GhostExpressionTier.FOCUS
                cpuUsage >= 15.0 -> GhostExpressionTier.HAPPY
                else -> GhostExpressionTier.SLEEP
            }
        } else {
            val downKb = downloadBytes / 1024.0
            val happyThresh = appSettings.happyThreshold.value.toDouble()
            val focusThresh = appSettings.focusThreshold.value.toDouble()
            val strainThresh = appSettings.strainThreshold.value.toDouble()

            when {
                downKb >= strainThresh * 5.0 -> GhostExpressionTier.DEAD
                downKb >= strainThresh * 2.5 -> GhostExpressionTier.PANIC
                downKb >= strainThresh -> GhostExpressionTier.STRAIN
                downKb >= focusThresh -> GhostExpressionTier.FOCUS
                downKb >= happyThresh -> GhostExpressionTier.HAPPY
                else -> GhostExpressionTier.SLEEP
            }
        }
    }

    private fun computeTierDescription(tier: GhostExpressionTier, source: ReactionSource): String {
        return if (source == ReactionSource.CPU_LOAD) {
            when (tier) {
                GhostExpressionTier.SLEEP -> "CPU Idle (0–15%) · Napping peacefully"
                GhostExpressionTier.HAPPY -> "CPU Light (16–40%) · Cruising smoothly"
                GhostExpressionTier.FOCUS -> "CPU Active (41–65%) · Moderate processing"
                GhostExpressionTier.STRAIN -> "CPU Heavy (66–85%) · Heavy workload!"
                GhostExpressionTier.PANIC -> "CPU Stress (86–95%) · Cores maxing out!"
                GhostExpressionTier.DEAD -> "CPU Peak (96–100%) · Pegged at limit!"
            }
        } else {
            when (tier) {
                GhostExpressionTier.SLEEP -> "Idle (< 10 KB/s) · Drifting peacefully..."
                GhostExpressionTier.HAPPY -> "Light Bandwidth (10–500 KB/s) · Cruising"
                GhostExpressionTier.FOCUS -> "Medium Load (500 KB–1 MB/s) · Locked on target"
                GhostExpressionTier.STRAIN -> "Heavy Load (1–5 MB/s) · Working hard!"
                GhostExpressionTier.PANIC -> "Extreme Load (5–10 MB/s) · High Traffic!"
                GhostExpressionTier.DEAD -> "Maxed Out (> 10 MB/s) · Network Saturated"
            }
        }
    }

    private fun removeTopSpeedRing() {
        speedRingView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                android.util.Log.e("GhostGlitch", "Could not remove speedRingView", e)
            }
            speedRingView = null
        }
    }

    private fun removeDraggableCompanion() {
        companionView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                android.util.Log.e("GhostGlitch", "Could not remove companionView", e)
            }
            companionView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        speedMonitor.stop()
        cpuMonitor.stop()
        scope.cancel()
        dismissHud()

        removeTopSpeedRing()
        removeDraggableCompanion()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
