package com.notchbuddy.android.overlay

import android.content.Context
import android.graphics.*
import android.view.View
import com.notchbuddy.android.cutout.CutoutInfo
import com.notchbuddy.android.model.GhostExpressionTier
import kotlin.math.sin

class PunchHoleHaloView(
    context: Context,
    private val cutoutInfo: CutoutInfo
) : View(context) {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        alpha = 80
    }

    private var tier: GhostExpressionTier = GhostExpressionTier.SLEEP
    private var animTime = 0f

    fun updateTelemetry(newTier: GhostExpressionTier) {
        this.tier = newTier
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        animTime += 0.035f

        val cx = cutoutInfo.centerX
        val cy = cutoutInfo.centerY
        val baseRadius = cutoutInfo.radius

        // Breathing pulse
        val pulse = (sin(animTime * 2.5) * 1.5f).toFloat()
        val currentRadius = baseRadius + pulse

        // Color mapping
        val (primaryColor, glowColor) = when (tier) {
            GhostExpressionTier.DEAD, GhostExpressionTier.PANIC -> Pair(Color.parseColor("#EF4444"), Color.parseColor("#B91C1C"))
            GhostExpressionTier.STRAIN -> Pair(Color.parseColor("#F59E0B"), Color.parseColor("#D97706"))
            GhostExpressionTier.FOCUS -> Pair(Color.parseColor("#06B6D4"), Color.parseColor("#0891B2"))
            GhostExpressionTier.HAPPY -> Pair(Color.parseColor("#10B981"), Color.parseColor("#059669"))
            GhostExpressionTier.SLEEP -> Pair(Color.parseColor("#64748B"), Color.parseColor("#475569"))
        }

        // Sweeping gradient around camera
        val angle = (animTime * 60f) % 360f
        val sweepGradient = SweepGradient(
            cx, cy,
            intArrayOf(primaryColor, glowColor, primaryColor),
            floatArrayOf(0f, 0.5f, 1f)
        )
        val matrix = Matrix().apply { postRotate(angle, cx, cy) }
        sweepGradient.setLocalMatrix(matrix)

        // Draw Outer Soft Glow
        glowPaint.color = glowColor
        glowPaint.strokeWidth = 10f
        glowPaint.alpha = 60
        canvas.drawCircle(cx, cy, currentRadius + 2f, glowPaint)

        // Draw Sharp Primary Ring
        ringPaint.shader = sweepGradient
        ringPaint.strokeWidth = 3.5f
        canvas.drawCircle(cx, cy, currentRadius, ringPaint)

        // Invalidate for 60 FPS continuous halo rotation
        postInvalidateOnAnimation()
    }
}
