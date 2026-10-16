package com.notchbuddy.android.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import com.notchbuddy.android.cutout.CutoutInfo
import com.notchbuddy.android.model.ReactionSource
import com.notchbuddy.android.model.SpeedUnit
import com.notchbuddy.android.model.TelemetrySnapshot
import java.util.Locale

@SuppressLint("ViewConstructor")
class PunchHoleSpeedRingView(
    context: Context,
    private val cutoutInfo: CutoutInfo,
    private val onRingClicked: () -> Unit
) : View(context) {

    private var telemetry = TelemetrySnapshot()
    private var speedUnit = SpeedUnit.BYTES
    private var reactionSource = ReactionSource.INTERNET_SPEED
    private var runningTime = 0f

    private var detectedCutoutRadius: Float? = null

    // Circular camera notch progress paints
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6.0f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#334155")
        setShadowLayer(4f, 0f, 1f, Color.argb(160, 0, 0, 0))
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7.0f
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(6f, 0f, 1f, Color.argb(180, 0, 0, 0))
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12.0f
        alpha = 65
    }

    // Typography paints with deep high-contrast colors matching status bar
    private val downLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = Color.parseColor("#10B981") // Bright Emerald
        setShadowLayer(3f, 0f, 1.5f, Color.argb(200, 0, 0, 0))
    }
    private val downValPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        color = Color.parseColor("#34D399") // Mint Green
        setShadowLayer(3f, 0f, 1.5f, Color.argb(200, 0, 0, 0))
    }

    private val upLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = Color.parseColor("#0284C7") // Ocean Cyan
        setShadowLayer(3f, 0f, 1.5f, Color.argb(200, 0, 0, 0))
    }
    private val upValPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        color = Color.parseColor("#38BDF8") // Sky Blue
        setShadowLayer(3f, 0f, 1.5f, Color.argb(200, 0, 0, 0))
    }

    private val cpuValPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        color = Color.parseColor("#C084FC") // Luminous Vibrant Lilac - highly visible
        setShadowLayer(3f, 0f, 1.5f, Color.argb(200, 0, 0, 0))
    }
    private val cpuLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = Color.parseColor("#CBD5E1") // Slate Light
        setShadowLayer(3f, 0f, 1.5f, Color.argb(200, 0, 0, 0))
    }

    init {
        isClickable = true
        isFocusable = false
        val density = resources.displayMetrics.density
        trackPaint.strokeWidth = 2.4f * density
        progressPaint.strokeWidth = 2.8f * density
        glowPaint.strokeWidth = 5.0f * density
        // Entirely transparent view background
        setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            requestApplyInsets()
        }
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val cutout = insets.displayCutout
            if (cutout != null) {
                val rect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cutout.boundingRectTop
                } else {
                    cutout.boundingRects.firstOrNull { it.top <= 120 }
                }
                if (rect != null && !rect.isEmpty) {
                    val realR = (rect.width().coerceAtLeast(rect.height()) / 2f) + (3.5f * resources.displayMetrics.density)
                    this.detectedCutoutRadius = realR
                    invalidate()
                }
            }
        }
        return super.onApplyWindowInsets(insets)
    }

    fun updateTelemetry(snapshot: TelemetrySnapshot, unit: SpeedUnit, source: ReactionSource) {
        this.telemetry = snapshot
        this.speedUnit = unit
        this.reactionSource = source
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        runningTime += 0.016f

        val w = width.toFloat()
        val h = height.toFloat()
        val density = resources.displayMetrics.density

        val cx = w / 2f
        val cy = h / 2f

        // Increased text size to 12.0sp to match phone's native status bar clock & battery font
        val textSize = 12.0f * density
        downLabelPaint.textSize = textSize
        downValPaint.textSize = textSize
        upLabelPaint.textSize = textSize
        upValPaint.textSize = textSize
        cpuValPaint.textSize = textSize
        cpuLabelPaint.textSize = 9.5f * density

        val textY = cy + (textSize * 0.35f)

        // ── 1. CENTER: Dynamic Camera Cutout Progress Ring ─────────────────
        // Outer halo radius: ~16.5dp (33dp diameter) so it circles outside the camera glass cleanly
        val ringRadius = detectedCutoutRadius ?: if (cutoutInfo.hasHardwareCutout && cutoutInfo.radius > 10f) {
            cutoutInfo.radius + 3.5f * density
        } else {
            16.5f * density
        }
        val ringRect = RectF(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius)

        val progressRatio = if (reactionSource == ReactionSource.CPU_LOAD) {
            (telemetry.cpuUsagePercent / 100.0).coerceIn(0.05, 1.0)
        } else {
            (telemetry.downloadBytesPerSec / (5.0 * 1024.0 * 1024.0)).coerceIn(0.05, 1.0)
        }

        val ringColor = when {
            progressRatio >= 0.85 -> Color.parseColor("#EF4444") // Red
            progressRatio >= 0.50 -> Color.parseColor("#F59E0B") // Amber
            progressRatio >= 0.20 -> Color.parseColor("#06B6D4") // Cyan
            else -> Color.parseColor("#10B981")                  // Emerald
        }

        // Circular track
        canvas.drawCircle(cx, cy, ringRadius, trackPaint)

        // Progress arc
        val sweepAngle = (progressRatio * 360.0).toFloat()
        glowPaint.color = ringColor
        canvas.drawArc(ringRect, -90f, sweepAngle, false, glowPaint)

        progressPaint.color = ringColor
        canvas.drawArc(ringRect, -90f, sweepAngle, false, progressPaint)

        // ── 2. LEFT SIDE: Horizontal "↓ {down}  ↑ {up}" ───────────────────
        val downText = formatCompactRate(telemetry.downloadBytesPerSec, speedUnit)
        val upText = formatCompactRate(telemetry.uploadBytesPerSec, speedUnit)

        val downArrowW = downLabelPaint.measureText("↓")
        val downValW = downValPaint.measureText(downText)
        val upArrowW = upLabelPaint.measureText("↑")
        val upValW = upValPaint.measureText(upText)
        val spacing = 4f * density

        val leftTotalWidth = downArrowW + 2f * density + downValW + spacing + upArrowW + 2f * density + upValW
        var curLeftX = cx - ringRadius - 6f * density - leftTotalWidth

        // Draw Download "↓ 10.2M"
        canvas.drawText("↓", curLeftX, textY, downLabelPaint)
        curLeftX += downArrowW + 2f * density
        canvas.drawText(downText, curLeftX, textY, downValPaint)
        curLeftX += downValW + spacing

        // Draw Upload "↑ 1.4M"
        canvas.drawText("↑", curLeftX, textY, upLabelPaint)
        curLeftX += upArrowW + 2f * density
        canvas.drawText(upText, curLeftX, textY, upValPaint)

        // ── 3. RIGHT SIDE: Horizontal "{cpu}% CPU" ─────────────────────────
        var curRightX = cx + ringRadius + 6f * density
        val cpuValText = "${telemetry.cpuUsagePercent.toInt()}%"

        canvas.drawText(cpuValText, curRightX, textY, cpuValPaint)
        curRightX += cpuValPaint.measureText(cpuValText) + 2f * density
        canvas.drawText("CPU", curRightX, textY, cpuLabelPaint)

        postInvalidateOnAnimation()
    }

    private fun formatCompactRate(bytesPerSec: Double, unit: SpeedUnit): String {
        return if (unit == SpeedUnit.BITS) {
            val bits = bytesPerSec * 8.0
            when {
                bits >= 1_000_000_000 -> String.format(Locale.US, "%.1fG", bits / 1_000_000_000)
                bits >= 1_000_000 -> String.format(Locale.US, "%.1fM", bits / 1_000_000)
                bits >= 1_000 -> String.format(Locale.US, "%.0fK", bits / 1_000)
                else -> String.format(Locale.US, "%.0fb", bits)
            }
        } else {
            when {
                bytesPerSec >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1fG", bytesPerSec / (1024 * 1024 * 1024))
                bytesPerSec >= 1024 * 1024 -> String.format(Locale.US, "%.1fM", bytesPerSec / (1024 * 1024))
                bytesPerSec >= 1024 -> String.format(Locale.US, "%.0fK", bytesPerSec / 1024)
                else -> String.format(Locale.US, "%.0fB", bytesPerSec)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            onRingClicked()
            return true
        }
        return true
    }
}
