package com.notchbuddy.android.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.notchbuddy.android.companion.GhostRenderer
import com.notchbuddy.android.model.TelemetrySnapshot
import com.notchbuddy.android.settings.AppSettings
import kotlin.math.sqrt

@SuppressLint("ViewConstructor")
class DraggableCompanionView(
    context: Context,
    private val windowManager: WindowManager,
    private val layoutParams: WindowManager.LayoutParams,
    private val appSettings: AppSettings,
    private val onDoubleTapped: () -> Unit
) : View(context) {

    private val ghostRenderer = GhostRenderer()
    private var telemetry = TelemetrySnapshot()
    private var runningTime = 0f

    // Drag physics
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var hasMoved = false
    private var lastTapTime = 0L

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

        // Draw living Mac Glitch Ghost character
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
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = layoutParams.x
                initialY = layoutParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                hasMoved = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()

                val distance = sqrt((dx * dx + dy * dy).toDouble())
                if (distance > 10) {
                    hasMoved = true
                    layoutParams.x = initialX + dx
                    layoutParams.y = initialY + dy
                    try {
                        windowManager.updateViewLayout(this, layoutParams)
                    } catch (_: Exception) {}
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (hasMoved) {
                    // Save final dragged position
                    appSettings.setCompanionPosition(layoutParams.x, layoutParams.y)
                } else {
                    // Quick tap -> Check for double tap to open HUD or single tap to Pet
                    val now = System.currentTimeMillis()
                    if (now - lastTapTime < 350) {
                        onDoubleTapped()
                    } else {
                        // Single tap pets Glitch!
                        ghostRenderer.triggerPet()
                    }
                    lastTapTime = now
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
