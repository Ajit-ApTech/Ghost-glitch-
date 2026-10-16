package com.notchbuddy.android.telemetry

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import com.notchbuddy.android.model.AppBandwidthUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class TopAppsMonitor(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val networkStatsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
    } else null

    suspend fun getTopConsumingApps(): List<AppBandwidthUsage> = withContext(Dispatchers.IO) {
        if (networkStatsManager == null) return@withContext emptyList()

        val results = mutableListOf<AppBandwidthUsage>()
        try {
            val endTime = System.currentTimeMillis()
            val startTime = endTime - (1000 * 60 * 60) // Last 1 hour

            val uidUsageMap = mutableMapOf<Int, Long>()

            // Query WiFi stats
            queryBucket(ConnectivityManager.TYPE_WIFI, startTime, endTime, uidUsageMap)
            // Query Mobile stats
            queryBucket(ConnectivityManager.TYPE_MOBILE, startTime, endTime, uidUsageMap)

            @Suppress("DEPRECATION")
            val installedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                packageManager.getInstalledApplications(0)
            }
            val appMap = installedApps.associateBy { it.uid }

            uidUsageMap.entries
                .filter { it.value > 1024 * 50 } // > 50 KB
                .sortedByDescending { it.value }
                .take(5)
                .forEach { entry ->
                    val uid = entry.key
                    var label: String? = null
                    var pkg: String? = null

                    // 1. Check known system UIDs
                    when (uid) {
                        1000 -> { label = "Android System"; pkg = "android" }
                        1013 -> { label = "Media Server"; pkg = "media.server" }
                        1021 -> { label = "GPS & Location"; pkg = "gps" }
                        1073 -> { label = "Download Manager"; pkg = "android.providers.downloads" }
                    }

                    // 2. Query specific packages for this UID
                    if (label == null) {
                        try {
                            val pkgs = packageManager.getPackagesForUid(uid)
                            if (!pkgs.isNullOrEmpty()) {
                                pkg = pkgs[0]
                                @Suppress("DEPRECATION")
                                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                                label = packageManager.getApplicationLabel(appInfo).toString()
                            }
                        } catch (_: Exception) {}
                    }

                    // 3. Fallback to preloaded app map
                    if (label == null) {
                        val appInfo = appMap[uid]
                        if (appInfo != null) {
                            pkg = appInfo.packageName
                            label = packageManager.getApplicationLabel(appInfo).toString()
                        }
                    }

                    // 4. Final fallback if unresolvable
                    val finalLabel = label ?: "App ($uid)"
                    val finalPkg = pkg ?: "uid.$uid"
                    val formatted = formatBytes(entry.value)
                    results.add(AppBandwidthUsage(finalLabel, finalPkg, formatted, entry.value))
                }

        } catch (_: Throwable) {
            // Permission not granted or query failed
        }

        if (results.isEmpty()) {
            results.add(AppBandwidthUsage("System & Network", "android.system", "Active", 0))
        }

        results
    }

    private fun queryBucket(networkType: Int, start: Long, end: Long, outputMap: MutableMap<Int, Long>) {
        try {
            val stats = networkStatsManager?.querySummary(networkType, null, start, end) ?: return
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val uid = bucket.uid
                if (uid > 0) {
                    val total = bucket.rxBytes + bucket.txBytes
                    outputMap[uid] = (outputMap[uid] ?: 0L) + total
                }
            }
            stats.close()
        } catch (_: Throwable) {
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", bytes.toDouble() / (1024 * 1024 * 1024))
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes.toDouble() / 1024)
            else -> "$bytes B"
        }
    }
}
