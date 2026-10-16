package com.notchbuddy.android.model

enum class GhostExpressionTier(val rawValue: String) {
    SLEEP("sleep"),
    HAPPY("happy"),
    FOCUS("focus"),
    STRAIN("strain"),
    PANIC("panic"),
    DEAD("dead")
}

enum class ReactionSource {
    INTERNET_SPEED,
    CPU_LOAD
}

enum class SpeedUnit {
    BYTES, // KB/s, MB/s
    BITS   // Kbps, Mbps
}

data class TelemetrySnapshot(
    val downloadBytesPerSec: Double = 0.0,
    val uploadBytesPerSec: Double = 0.0,
    val peakDownloadBytesPerSec: Double = 0.0,
    val peakUploadBytesPerSec: Double = 0.0,
    val cpuUsagePercent: Double = 0.0,
    val pingMs: Long = 28L,
    val tier: GhostExpressionTier = GhostExpressionTier.SLEEP,
    val speedHistory: List<Double> = List(20) { 0.0 },
    val cpuHistory: List<Double> = List(20) { 0.0 },
    val interfaceName: String = "wlan0 (Wi-Fi)",
    val ipAddress: String = "127.0.0.1",
    val sessionTotalBytes: Long = 0L,
    val monthlyQuotaBytes: Long = 100L * 1024L * 1024L * 1024L, // 100 GB
    val primaryValueFormatted: String = "0 bps",
    val secondaryValueFormatted: String = "0 bps",
    val peakDownloadFormatted: String = "0 bps",
    val peakUploadFormatted: String = "0 bps",
    val cpuFormatted: String = "0%",
    val tierDescription: String = "Idle (< 10 KB/s) · Drifting peacefully..."
)

data class AppBandwidthUsage(
    val appName: String,
    val packageName: String,
    val usageFormatted: String,
    val bytes: Long
)
