package com.notchbuddy.android.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.notchbuddy.android.companion.GhostRenderer
import com.notchbuddy.android.cutout.CutoutInfo
import com.notchbuddy.android.model.GhostExpressionTier
import com.notchbuddy.android.model.SpeedUnit
import com.notchbuddy.android.model.TelemetrySnapshot
import com.notchbuddy.android.telemetry.NetworkSpeedMonitor
import java.util.Locale
import kotlin.math.sin

@SuppressLint("ViewConstructor")
class TopNotchBarView(
    context: Context,
    private val cutoutInfo: CutoutInfo,
    private val onBarClicked: () -> Unit
) : View(context) {

    private val ghostRenderer = GhostRenderer()
    private var telemetry = TelemetrySnapshot()
    private var speedUnit = SpeedUnit.BITS
    private var haloEnabled = true
    private var runningTime = 0f

    // Paints
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#0A0D14")
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.0f
        color = Color.parseColor("#1C2438")
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.0f
        strokeCap = Paint.Cap.ROUND
    }
    private val haloGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7.0f
        alpha = 65
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val netSparklinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#10B981") // Emerald Green
    }
    private val cpuSparklinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#A855F7") // Neon Purple
    }
    private val pingDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        isClickable = true
        isFocusable = false
    }

    fun updateTelemetry(snapshot: TelemetrySnapshot, unit: SpeedUnit, halo: Boolean) {
        this.telemetry = snapshot
        this.speedUnit = unit
        this.haloEnabled = halo
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        runningTime += 0.016f

        val w = width.toFloat()
        val h = height.toFloat()
        val density = resources.displayMetrics.density

        // 1. Draw Sleek Obsidian Capsule Background
        val cornerRadius = h / 2f
        val pillRect = RectF(2f, 2f, w - 2f, h - 2f)
        canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, bgPaint)
        canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, borderPaint)

        val cx = w / 2f
        val cy = h / 2f

        // 2. Draw Center Camera Punch-Hole Halo Ring
        if (haloEnabled) {
            val haloColor = when (telemetry.tier) {
                GhostExpressionTier.DEAD, GhostExpressionTier.PANIC -> Color.parseColor("#EF4444")
                GhostExpressionTier.STRAIN -> Color.parseColor("#F59E0B")
                GhostExpressionTier.FOCUS -> Color.parseColor("#06B6D4")
                GhostExpressionTier.HAPPY -> Color.parseColor("#10B981")
                GhostExpressionTier.SLEEP -> Color.parseColor("#64748B")
            }

            val pulse = (sin(runningTime * 2.5) * 1.0f).toFloat()
            val cameraRadius = (10f * density) + pulse

            haloGlowPaint.color = haloColor
            canvas.drawCircle(cx, cy - 2f * density, cameraRadius + 2f, haloGlowPaint)

            haloPaint.color = haloColor
            canvas.drawCircle(cx, cy - 2f * density, cameraRadius, haloPaint)
        }

        // 3. Draw Center Alive Glitch Ghost Character
        val ghostSize = 28f * density
        canvas.save()
        canvas.translate(cx - ghostSize / 2f, cy - ghostSize / 2f + 2f * density)
        ghostRenderer.draw(
            canvas = canvas,
            width = ghostSize,
            height = ghostSize,
            tier = telemetry.tier,
            timeSeconds = runningTime
        )
        canvas.restore()

        // ── 4. LEFT WING: Stacked Speeds + Green Network Sparkline ───────────
        val leftWingWidth = cx - 22f * density
        val speedY1 = cy - 2f * density
        val speedY2 = cy + 8f * density

        // Download: Green arrow + formatted rate
        labelPaint.color = Color.parseColor("#10B981")
        labelPaint.textSize = 8.5f * density
        canvas.drawText("↓", 10f * density, speedY1, labelPaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 8.5f * density
        val downText = formatCompactSpeed(telemetry.downloadBytesPerSec, speedUnit)
        canvas.drawText(downText, 18f * density, speedY1, textPaint)

        // Upload: Cyan/Blue arrow + formatted rate
        labelPaint.color = Color.parseColor("#38BDF8")
        labelPaint.textSize = 8.5f * density
        canvas.drawText("↑", 10f * density, speedY2, labelPaint)

        textPaint.color = Color.WHITE
        val upText = formatCompactSpeed(telemetry.uploadBytesPerSec, speedUnit)
        canvas.drawText(upText, 18f * density, speedY2, textPaint)

        // Live Green Network Speed Sparkline hugging the notch
        val netSparkX1 = 48f * density
        val netSparkX2 = leftWingWidth - 2f * density
        drawSparkline(
            canvas = canvas,
            data = telemetry.speedHistory,
            startX = netSparkX1,
            endX = netSparkX2,
            topY = cy - 8f * density,
            bottomY = cy + 8f * density,
            paint = netSparklinePaint
        )

        // ── 5. RIGHT WING: Purple CPU Sparkline + Ping Dot + CPU % ─────────
        val rightWingStart = cx + 22f * density
        val rightWingEnd = w - 10f * density

        // Live Purple CPU Sparkline originating from the notch
        val cpuSparkX1 = rightWingStart + 2f * density
        val cpuSparkX2 = rightWingEnd - 38f * density
        drawSparkline(
            canvas = canvas,
            data = telemetry.cpuHistory,
            startX = cpuSparkX1,
            endX = cpuSparkX2,
            topY = cy - 8f * density,
            bottomY = cy + 8f * density,
            paint = cpuSparklinePaint
        )

        // Ping Dot (Green < 50ms, Amber < 100ms, Red > 100ms)
        val pingColor = when {
            telemetry.pingMs >= 100L -> Color.parseColor("#EF4444")
            telemetry.pingMs >= 50L -> Color.parseColor("#F59E0B")
            else -> Color.parseColor("#10B981")
        }
        pingDotPaint.color = pingColor
        val pingDotX = rightWingEnd - 32f * density
        canvas.drawCircle(pingDotX, cy, 2.5f * density, pingDotPaint)

        // CPU % Text
        labelPaint.color = Color.parseColor("#94A3B8")
        labelPaint.textSize = 7.5f * density
        canvas.drawText("CPU", pingDotX + 6f * density, cy - 2f * density, labelPaint)

        textPaint.color = Color.parseColor("#C084FC") // Purple
        textPaint.textSize = 8.5f * density
        val cpuText = "${telemetry.cpuUsagePercent.toInt()}%"
        canvas.drawText(cpuText, pingDotX + 6f * density, cy + 7f * density, textPaint)

        postInvalidateOnAnimation()
    }

    private fun drawSparkline(
        canvas: Canvas,
        data: List<Double>,
        startX: Float,
        endX: Float,
        topY: Float,
        bottomY: Float,
        paint: Paint
    ) {
        if (data.size < 2 || endX <= startX) return

        val maxVal = (data.maxOrNull() ?: 10.0).coerceAtLeast(10.0)
        val stepX = (endX - startX) / (data.size - 1)
        val h = bottomY - topY

        val path = Path()
        for (i in data.indices) {
            val x = startX + i * stepX
            val ratio = (data[i] / maxVal).coerceIn(0.0, 1.0).toFloat()
            val y = bottomY - (ratio * h)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
    }

    private fun formatCompactSpeed(bytesPerSec: Double, unit: SpeedUnit): String {
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
            val cx = width / 2f
            val density = resources.displayMetrics.density
            val ghostHitRadius = 20f * density

            // Tapping near center pets Glitch!
            if (Math.abs(event.x - cx) < ghostHitRadius) {
                ghostRenderer.triggerPet()
            } else {
                // Tapping wings opens full HUD
                onBarClicked()
            }
            return true
        }
        return super.onTouchEvent(event)
    }
}
