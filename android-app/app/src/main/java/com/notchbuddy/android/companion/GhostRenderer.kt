package com.notchbuddy.android.companion

import android.graphics.*
import com.notchbuddy.android.model.GhostExpressionTier
import kotlin.math.sin
import kotlin.random.Random

data class Particle(
    val emoji: String,
    val dx: Float,
    val dy: Float,
    var progress: Float = 0f
)

class GhostRenderer {

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val bodyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.0f
        color = Color.argb(130, 255, 255, 255)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val visorBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#0B0F19")
    }
    private val visorStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        color = Color.parseColor("#334155")
    }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokeEyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        isFakeBoldText = true
    }

    private val particles = mutableListOf<Particle>()
    private var isPetted = false
    private var petTimer = 0f

    fun triggerPet() {
        isPetted = true
        petTimer = 0f
        particles.clear()
        val emojis = listOf("💖", "✨", "👻", "🌸", "⭐", "💫")
        for (i in 0..2) {
            val emoji = emojis.random()
            val side = if (i % 2 == 0) 1f else -1f
            val dx = side * (Random.nextFloat() * 30f + 20f)
            val dy = Random.nextFloat() * 25f + 10f
            particles.add(Particle(emoji, dx, dy, 0f))
        }
    }

    /**
     * Renders the authentic Mac Glitch companion with LCD visor, animated wavy skirt,
     * and facial expressions into any Android Canvas.
     */
    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        tier: GhostExpressionTier,
        timeSeconds: Float
    ) {
        val cx = width / 2f
        val scale = (width.coerceAtMost(height) / 74f).coerceAtLeast(0.45f)

        // Petting timer update
        if (isPetted) {
            petTimer += 0.016f
            particles.forEach { p ->
                p.progress = (petTimer / 1.1f).coerceIn(0f, 1f)
            }
            if (petTimer > 1.2f) {
                isPetted = false
                particles.clear()
            }
        }

        // Gentle floating bob
        val floatY = (sin(timeSeconds * 2.3) * 3.5f * scale).toFloat()
        val cy = height * 0.46f + floatY

        // Jitter for panic/dead tiers
        var jx = 0f
        if (tier == GhostExpressionTier.PANIC) {
            jx = (sin(timeSeconds * 35.0) * 2.0f * scale).toFloat()
        } else if (tier == GhostExpressionTier.DEAD) {
            jx = (sin(timeSeconds * 50.0) * 3.0f * scale).toFloat()
        }

        val bw = 20f * scale
        val bh = 34f * scale
        val top = cy - bh * 0.5f

        // 1. Ambient Glow Ellipse
        val glowColor = if (isPetted) {
            Color.parseColor("#EC4899") // Pink
        } else {
            when (tier) {
                GhostExpressionTier.DEAD, GhostExpressionTier.PANIC -> Color.parseColor("#EF4444") // Red
                GhostExpressionTier.STRAIN -> Color.parseColor("#F59E0B") // Amber
                GhostExpressionTier.FOCUS -> Color.parseColor("#06B6D4") // Cyan
                GhostExpressionTier.HAPPY -> Color.parseColor("#10B981") // Emerald
                GhostExpressionTier.SLEEP -> Color.parseColor("#64748B") // Slate Blue
            }
        }
        glowPaint.color = glowColor
        glowPaint.alpha = 55
        canvas.drawOval(
            RectF(cx + jx - 30f * scale, cy - 30f * scale, cx + jx + 30f * scale, cy + 30f * scale),
            glowPaint
        )

        // 2. Translucent Soft Ghost Body with 3-Bump Wavy Skirt
        val bodyColor = if (tier == GhostExpressionTier.PANIC || tier == GhostExpressionTier.DEAD) {
            Color.argb(240, 255, 220, 220) // Reddish tint
        } else {
            Color.argb(245, 238, 246, 255) // Pale translucent white
        }
        bodyPaint.color = bodyColor

        val bodyPath = Path()
        // Top dome
        bodyPath.arcTo(
            RectF(cx + jx - bw, top, cx + jx + bw, top + bw * 2f),
            180f, 180f, false
        )
        // Right side
        bodyPath.lineTo(cx + jx + bw, top + bh - 8f * scale)

        // Animated 3-bump wavy skirt
        val wt = timeSeconds * 3.2f
        val wa = 4.5f * scale
        bodyPath.quadTo(
            cx + jx + bw * 0.68f + (sin(wt.toDouble()) * 1.0f).toFloat(),
            top + bh + wa * 0.85f,
            cx + jx + bw * 0.34f,
            top + bh - 2f * scale
        )
        bodyPath.quadTo(
            cx + jx + (sin(wt + 1.05) * 1.5f).toFloat(),
            top + bh + wa,
            cx + jx - bw * 0.34f,
            top + bh - 2f * scale
        )
        bodyPath.quadTo(
            cx + jx - bw * 0.68f + (sin(wt + 2.1) * 1.0f).toFloat(),
            top + bh + wa * 0.85f,
            cx + jx - bw,
            top + bh - 8f * scale
        )
        bodyPath.lineTo(cx + jx - bw, top + bw)
        bodyPath.close()

        canvas.drawPath(bodyPath, bodyPaint)
        canvas.drawPath(bodyPath, bodyStrokePaint)

        // 3. Dark Glossy LCD Visor Screen on Chest
        val sw = 30f * scale
        val sh = 17f * scale
        val sx = cx + jx - sw / 2f
        val sy = top + bw * 0.85f - sh / 2f
        val visorRect = RectF(sx, sy, sx + sw, sy + sh)

        canvas.drawRoundRect(visorRect, 5f * scale, 5f * scale, visorBgPaint)
        canvas.drawRoundRect(visorRect, 5f * scale, 5f * scale, visorStrokePaint)

        // 4. LCD Visor Facial Expressions
        val ecx = sx + sw / 2f
        val ecy = sy + sh / 2f
        val es = 7.5f * scale

        if (isPetted) {
            // Joyful blushing eyes & smiling mouth
            strokeEyePaint.color = Color.parseColor("#EC4899")
            strokeEyePaint.strokeWidth = 1.6f * scale
            drawCrescentEye(canvas, ecx - es, ecy, 3f * scale)
            drawCrescentEye(canvas, ecx + es, ecy, 3f * scale)

            // Rosy blush cheeks
            eyePaint.color = Color.argb(160, 244, 63, 94)
            canvas.drawOval(RectF(ecx - es - 4f * scale, ecy + 2.5f * scale, ecx - es - 1f * scale, ecy + 4.5f * scale), eyePaint)
            canvas.drawOval(RectF(ecx + es + 1f * scale, ecy + 2.5f * scale, ecx + es + 4f * scale, ecy + 4.5f * scale), eyePaint)

            // Smile
            mouthPaint.color = Color.parseColor("#EC4899")
            mouthPaint.strokeWidth = 1.4f * scale
            val smilePath = Path()
            smilePath.arcTo(RectF(ecx - 3.5f * scale, ecy + 1f * scale, ecx + 3.5f * scale, ecy + 5f * scale), 15f, 150f, false)
            canvas.drawPath(smilePath, mouthPaint)

        } else {
            when (tier) {
                GhostExpressionTier.SLEEP -> {
                    // Closed sleeping crescent eyes
                    strokeEyePaint.color = Color.parseColor("#60A5FA")
                    strokeEyePaint.strokeWidth = 1.4f * scale
                    drawCrescentEye(canvas, ecx - es, ecy + 1f * scale, 3f * scale)
                    drawCrescentEye(canvas, ecx + es, ecy + 1f * scale, 3f * scale)

                    // Drifting 'z' floating upward and fading
                    val zProg = (timeSeconds * 1.6f) % 2.5f
                    val zY = ecy - 2f * scale - (zProg * 8f * scale)
                    val zAlpha = ((1f - zProg / 2.5f) * 255).toInt().coerceIn(0, 255)
                    textPaint.color = Color.parseColor("#94A3B8")
                    textPaint.alpha = zAlpha
                    textPaint.textSize = 8.5f * scale
                    canvas.drawText("z", ecx + 10f * scale, zY, textPaint)
                }

                GhostExpressionTier.HAPPY -> {
                    // Emerald glowing eyes + catchlights + friendly curved smile
                    eyePaint.color = Color.parseColor("#10B981")
                    val eyeRadius = 2.1f * scale
                    canvas.drawCircle(ecx - es, ecy, eyeRadius, eyePaint)
                    canvas.drawCircle(ecx + es, ecy, eyeRadius, eyePaint)

                    // Specular white catchlights
                    eyePaint.color = Color.WHITE
                    canvas.drawCircle(ecx - es + 0.6f * scale, ecy - 0.6f * scale, 0.9f * scale, eyePaint)
                    canvas.drawCircle(ecx + es + 0.6f * scale, ecy - 0.6f * scale, 0.9f * scale, eyePaint)

                    // Curved smile mouth
                    mouthPaint.color = Color.parseColor("#10B981")
                    mouthPaint.strokeWidth = 1.4f * scale
                    val smilePath = Path()
                    smilePath.arcTo(RectF(ecx - 3.2f * scale, ecy + 1f * scale, ecx + 3.2f * scale, ecy + 5f * scale), 15f, 150f, false)
                    canvas.drawPath(smilePath, mouthPaint)
                }

                GhostExpressionTier.FOCUS -> {
                    // Cyan concentric targeting rings + intense pupils + focused brow lines
                    val cyanColor = Color.parseColor("#06B6D4")
                    strokeEyePaint.color = Color.argb(120, 6, 182, 212)
                    strokeEyePaint.strokeWidth = 1.1f * scale

                    // Targeting rings
                    val ringR = 4.3f * scale
                    canvas.drawCircle(ecx - es, ecy, ringR, strokeEyePaint)
                    canvas.drawCircle(ecx + es, ecy, ringR, strokeEyePaint)

                    // Pupils
                    eyePaint.color = cyanColor
                    canvas.drawCircle(ecx - es, ecy, 2.5f * scale, eyePaint)
                    canvas.drawCircle(ecx + es, ecy, 2.5f * scale, eyePaint)

                    // Specular catchlights
                    eyePaint.color = Color.WHITE
                    canvas.drawCircle(ecx - es + 0.6f * scale, ecy - 0.6f * scale, 0.9f * scale, eyePaint)
                    canvas.drawCircle(ecx + es + 0.6f * scale, ecy - 0.6f * scale, 0.9f * scale, eyePaint)

                    // Focused brow lines
                    strokeEyePaint.color = cyanColor
                    strokeEyePaint.strokeWidth = 1.3f * scale
                    canvas.drawLine(ecx - es - 4f * scale, ecy - 4.5f * scale, ecx - 1.2f * scale, ecy - 3.2f * scale, strokeEyePaint)
                    canvas.drawLine(ecx + 1.2f * scale, ecy - 3.2f * scale, ecx + es + 4f * scale, ecy - 4.5f * scale, strokeEyePaint)
                }

                GhostExpressionTier.STRAIN -> {
                    // Amber downward arc worried eyes + sweat drop + jagged mouth
                    val amberColor = Color.parseColor("#F59E0B")
                    strokeEyePaint.color = amberColor
                    strokeEyePaint.strokeWidth = 1.5f * scale

                    drawDownArcEye(canvas, ecx - es, ecy, 3f * scale)
                    drawDownArcEye(canvas, ecx + es, ecy, 3f * scale)

                    // Sweat drop on side of visor
                    eyePaint.color = Color.parseColor("#38BDF8")
                    val dropY = ecy - 2f * scale + (sin(timeSeconds * 5.0) * 1.5f * scale).toFloat()
                    canvas.drawCircle(ecx + es + 6f * scale, dropY, 1.8f * scale, eyePaint)

                    // Jagged mouth
                    mouthPaint.color = amberColor
                    mouthPaint.strokeWidth = 1.3f * scale
                    val jagPath = Path()
                    jagPath.moveTo(ecx - 4f * scale, ecy + 3.5f * scale)
                    jagPath.lineTo(ecx - 1.5f * scale, ecy + 5f * scale)
                    jagPath.lineTo(ecx + 1f * scale, ecy + 2.5f * scale)
                    jagPath.lineTo(ecx + 4f * scale, ecy + 4.5f * scale)
                    canvas.drawPath(jagPath, mouthPaint)
                }

                GhostExpressionTier.PANIC -> {
                    // Shaking red X eyes + screaming oval mouth
                    val redColor = Color.parseColor("#EF4444")
                    strokeEyePaint.color = redColor
                    strokeEyePaint.strokeWidth = 1.8f * scale

                    val xr = 2.8f * scale
                    // Left X
                    canvas.drawLine(ecx - es - xr, ecy - xr, ecx - es + xr, ecy + xr, strokeEyePaint)
                    canvas.drawLine(ecx - es - xr, ecy + xr, ecx - es + xr, ecy - xr, strokeEyePaint)
                    // Right X
                    canvas.drawLine(ecx + es - xr, ecy - xr, ecx + es + xr, ecy + xr, strokeEyePaint)
                    canvas.drawLine(ecx + es - xr, ecy + xr, ecx + es + xr, ecy - xr, strokeEyePaint)

                    // Screaming mouth
                    eyePaint.color = redColor
                    canvas.drawOval(RectF(ecx - 2.5f * scale, ecy + 1.5f * scale, ecx + 2.5f * scale, ecy + 6.5f * scale), eyePaint)
                }

                GhostExpressionTier.DEAD -> {
                    // Glitched X eyes + drool droplet
                    val redColor = Color.parseColor("#EF4444")
                    strokeEyePaint.color = redColor
                    strokeEyePaint.strokeWidth = 1.8f * scale

                    val xr = 3f * scale
                    canvas.drawLine(ecx - es - xr, ecy - xr, ecx - es + xr, ecy + xr, strokeEyePaint)
                    canvas.drawLine(ecx - es - xr, ecy + xr, ecx - es + xr, ecy - xr, strokeEyePaint)
                    canvas.drawLine(ecx + es - xr, ecy - xr, ecx + es + xr, ecy + xr, strokeEyePaint)
                    canvas.drawLine(ecx + es - xr, ecy + xr, ecx + es + xr, ecy - xr, strokeEyePaint)

                    // Drool drop
                    eyePaint.color = Color.parseColor("#38BDF8")
                    canvas.drawCircle(ecx + 2f * scale, ecy + 7f * scale, 1.5f * scale, eyePaint)
                }
            }
        }

        // 5. Petting Sparkles Particles
        if (particles.isNotEmpty()) {
            textPaint.textSize = 14f * scale
            particles.forEach { p ->
                val px = cx + p.dx * p.progress * scale
                val py = cy + p.dy * p.progress * scale
                val alpha = ((1f - p.progress) * 255).toInt().coerceIn(0, 255)
                textPaint.alpha = alpha
                canvas.drawText(p.emoji, px, py, textPaint)
            }
        }
    }

    private fun drawCrescentEye(canvas: Canvas, x: Float, y: Float, radius: Float) {
        val path = Path()
        path.arcTo(RectF(x - radius, y - radius, x + radius, y + radius), 200f, 140f, false)
        canvas.drawPath(path, strokeEyePaint)
    }

    private fun drawDownArcEye(canvas: Canvas, x: Float, y: Float, radius: Float) {
        val path = Path()
        path.arcTo(RectF(x - radius, y - radius, x + radius, y + radius), 20f, 140f, false)
        canvas.drawPath(path, strokeEyePaint)
    }
}
