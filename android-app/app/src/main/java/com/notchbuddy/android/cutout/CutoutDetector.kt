package com.notchbuddy.android.cutout

import android.content.Context
import android.graphics.PointF
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.DisplayCutout
import android.view.WindowManager
import android.view.WindowMetrics

data class CutoutInfo(
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val isCenter: Boolean,
    val hasHardwareCutout: Boolean,
    val statusBarHeight: Int
)

class CutoutDetector(private val context: Context) {

    fun detectCutout(windowManager: WindowManager, displayCutout: DisplayCutout? = null): CutoutInfo {
        val (screenWidth, screenHeight) = getScreenDimensions(windowManager)
        val statusBarHeight = getStatusBarHeight()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && displayCutout != null) {
            val topRect = displayCutout.boundingRectTop
            if (!topRect.isEmpty) {
                val cx = topRect.exactCenterX()
                val cy = topRect.exactCenterY()
                val r = (topRect.width().coerceAtLeast(topRect.height()) / 2f) + 4f
                val isCenter = Math.abs(cx - (screenWidth / 2f)) < 60f

                return CutoutInfo(
                    centerX = cx,
                    centerY = cy,
                    radius = r,
                    isCenter = isCenter,
                    hasHardwareCutout = true,
                    statusBarHeight = statusBarHeight
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && displayCutout != null) {
            val rects = displayCutout.boundingRects
            val topRect = rects.firstOrNull { it.top <= statusBarHeight }
            if (topRect != null && !topRect.isEmpty) {
                val cx = topRect.exactCenterX()
                val cy = topRect.exactCenterY()
                val r = (topRect.width().coerceAtLeast(topRect.height()) / 2f) + 4f
                val isCenter = Math.abs(cx - (screenWidth / 2f)) < 60f

                return CutoutInfo(
                    centerX = cx,
                    centerY = cy,
                    radius = r,
                    isCenter = isCenter,
                    hasHardwareCutout = true,
                    statusBarHeight = statusBarHeight
                )
            }
        }

        // Fallback for devices without standard DisplayCutout API or emulators:
        // Assume centered camera at top status bar
        val defaultCx = screenWidth / 2f
        val defaultCy = statusBarHeight / 2f
        val defaultRadius = (statusBarHeight * 0.40f).coerceAtLeast(18f)

        return CutoutInfo(
            centerX = defaultCx,
            centerY = defaultCy,
            radius = defaultRadius,
            isCenter = true,
            hasHardwareCutout = false,
            statusBarHeight = statusBarHeight
        )
    }

    private fun getScreenDimensions(windowManager: WindowManager): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics: WindowMetrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(dm)
            Pair(dm.widthPixels, dm.heightPixels)
        }
    }

    private fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = context.resources.getDimensionPixelSize(resourceId)
        }
        return if (result > 0) result else 72
    }
}
