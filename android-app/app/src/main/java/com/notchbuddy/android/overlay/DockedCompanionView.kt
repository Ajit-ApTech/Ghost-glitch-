package com.notchbuddy.android.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import com.notchbuddy.android.companion.GhostRenderer
import com.notchbuddy.android.model.TelemetrySnapshot

@SuppressLint("ViewConstructor")
class DockedCompanionView(
    context: Context,
    private val onCompanionClicked: () -> Unit
) : View(context) {

    private val ghostRenderer = GhostRenderer()
    private var telemetry = TelemetrySnapshot()
    private var runningTime = 0f

    init {
        isClickable = true
        isFocusable = false
    }

    fun updateTelemetry(snapshot: TelemetrySnapshot) {
        this.telemetry = snapshot
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        runningTime += 0.016f

        // Draw alive ghost
        ghostRenderer.draw(
            canvas = canvas,
            width = width.toFloat(),
            height = height.toFloat(),
            tier = telemetry.tier,
            timeSeconds = runningTime
        )

        postInvalidateOnAnimation()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            onCompanionClicked()
            return true
        }
        return super.onTouchEvent(event)
    }
}
