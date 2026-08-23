package com.hyperionsoftware.balls.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.animation.doOnEnd
import com.hyperionsoftware.balls.R
import kotlin.math.cos
import kotlin.math.sin

// A Street-Fighter-style title card instead of a calm logo screen: two Canvas-drawn
// balloons (same primitives as every in-game balloon, no image assets) walk in from the
// edges, the player-blue one lunges and lands a hit, the struck one deflates in a wobbling
// panic, and the title stamps in on the impact. One elapsed-time clock drives every phase
// below rather than several independent animations, so the beats stay in sync with each
// other (the shake, the flash and the title all key off the same lunge/impact instant).
class SplashFightView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val winnerBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4FC3F7") }
    private val winnerShadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2E9FD6") }
    private val loserBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EF5350") }
    private val loserShadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#B71C1C") }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 200
    }
    private val stringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#CFD8DC")
    }
    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val burstPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFC107") }
    private val impactLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.parseColor("#FFC107")
    }
    // Mural-sized on purpose - fitTitleTextSize below solves for whatever size actually
    // fills the screen width, so this starting value only matters before the first layout
    // pass. A dark shadow layer gives the giant letters some poster-like depth instead of
    // reading as flat color.
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFC107")
        textSize = 68f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        setShadowLayer(14f, 0f, 6f, Color.argb(180, 0, 0, 0))
    }

    // The full game name (see game_full_name), distinct from the shorter app_name used for
    // the actual Android app label/launcher icon - reads from strings.xml rather than a
    // hardcoded literal, so a future rename only ever needs to happen there.
    private val title: String = context.getString(R.string.game_full_name)

    private var elapsedMs = 0f
    private var animator: ValueAnimator? = null

    fun start(onFinished: () -> Unit) {
        animator?.cancel()
        elapsedMs = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = TOTAL_MS
            addUpdateListener {
                elapsedMs = it.currentPlayTime.toFloat()
                invalidate()
            }
            doOnEnd { onFinished() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0) fitTitleTextSize(w * 0.85f)
    }

    // Solves for whatever text size actually fills targetWidth, instead of a fixed guess -
    // "mural-sized" needs to mean the same thing on a phone and a tablet.
    private fun fitTitleTextSize(targetWidth: Float) {
        var size = targetWidth
        titlePaint.textSize = size
        while (titlePaint.measureText(title) > targetWidth && size > 10f) {
            size -= 4f
            titlePaint.textSize = size
        }
    }

    private fun clamp01(x: Float) = x.coerceIn(0f, 1f)
    private fun easeOutCubic(t: Float): Float {
        val u = 1f - t
        return 1f - u * u * u
    }
    private fun easeInCubic(t: Float): Float = t * t * t
    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val radius = (w.coerceAtMost(h) / 2f) * 0.24f
        val cy = h * 0.36f + sin(elapsedMs * 0.006f) * 3f

        val leftRestX = w * 0.34f
        val rightRestX = w * 0.66f
        val leftStartX = -radius * 3f
        val rightStartX = w + radius * 3f
        val lungeX = rightRestX - radius * 1.85f

        val entranceEase = easeOutCubic(clamp01(elapsedMs / ENTRANCE_END_MS))
        val leftX = when {
            elapsedMs <= HOLD_END_MS -> lerp(leftStartX, leftRestX, entranceEase)
            elapsedMs <= IMPACT_MS ->
                lerp(leftRestX, lungeX, easeInCubic(clamp01((elapsedMs - HOLD_END_MS) / (IMPACT_MS - HOLD_END_MS))))
            else ->
                lerp(lungeX, leftRestX + radius * 0.15f, easeOutCubic(clamp01((elapsedMs - IMPACT_MS) / 250f)))
        }
        val rightX = lerp(rightStartX, rightRestX, entranceEase)

        // Screen shake: a decaying, deterministic jitter right on the hit, not true
        // randomness - still reads as an impact without needing a random source.
        val shakeWindow = elapsedMs in IMPACT_MS..SHAKE_END_MS
        val shakeDecay = if (shakeWindow) 1f - clamp01((elapsedMs - IMPACT_MS) / (SHAKE_END_MS - IMPACT_MS)) else 0f
        val shakeX = sin(elapsedMs * 0.9f) * 10f * shakeDecay
        val shakeY = cos(elapsedMs * 1.3f) * 6f * shakeDecay

        canvas.save()
        canvas.translate(shakeX, shakeY)

        drawBalloon(canvas, leftX, cy, radius, winnerBodyPaint, winnerShadePaint, wobble = 0f, alpha = 255)

        if (elapsedMs < IMPACT_MS) {
            drawBalloon(canvas, rightX, cy, radius, loserBodyPaint, loserShadePaint, wobble = 0f, alpha = 255)
        } else {
            val deflateT = clamp01((elapsedMs - IMPACT_MS) / (DEFLATE_END_MS - IMPACT_MS))
            val rightRadius = radius * (1f - easeInCubic(deflateT))
            if (rightRadius > 1f) {
                val wobble = sin(elapsedMs * 0.05f) * radius * 0.3f * (1f - deflateT)
                val driftY = radius * 0.7f * easeInCubic(deflateT)
                val alpha = ((1f - deflateT) * 255).toInt().coerceIn(0, 255)
                drawBalloon(
                    canvas, rightX + wobble, cy + driftY, rightRadius,
                    loserBodyPaint, loserShadePaint, wobble = wobble, alpha = alpha
                )
            }
        }

        // Hit spark: a quick white flash plus a few radiating lines right at the contact
        // point, straight out of a fighting game's impact frame.
        if (elapsedMs in IMPACT_MS..(IMPACT_MS + FLASH_MS)) {
            val flashT = clamp01((elapsedMs - IMPACT_MS) / FLASH_MS)
            val contactX = (leftX + rightX) / 2f + radius * 0.3f
            flashPaint.alpha = ((1f - flashT) * 200).toInt().coerceIn(0, 255)
            canvas.drawCircle(contactX, cy, radius * (0.5f + flashT * 0.6f), flashPaint)
            impactLinePaint.alpha = ((1f - flashT) * 255).toInt().coerceIn(0, 255)
            val sparkLength = radius * (0.6f + flashT * 0.9f)
            for (i in 0 until 6) {
                val angle = (Math.PI * 2 * i / 6).toFloat()
                canvas.drawLine(
                    contactX + cos(angle) * radius * 0.3f,
                    cy + sin(angle) * radius * 0.3f,
                    contactX + cos(angle) * sparkLength,
                    cy + sin(angle) * sparkLength,
                    impactLinePaint
                )
            }
        }

        canvas.restore()

        // Title stamp: a warm starburst flash behind the text, then the name itself
        // snapping down from an oversized punch-in to its resting size.
        if (elapsedMs >= TITLE_START_MS) {
            val titleCx = w / 2f
            val titleCy = h * 0.72f
            val burstT = clamp01((elapsedMs - TITLE_START_MS) / 350f)
            burstPaint.alpha = ((1f - burstT) * 130).toInt().coerceIn(0, 255)
            canvas.drawCircle(titleCx, titleCy, radius * (1.2f + burstT * 2.2f), burstPaint)

            val punchT = clamp01((elapsedMs - TITLE_START_MS) / TITLE_PUNCH_MS)
            val scale = 1f + 0.7f * (1f - punchT) * (1f - punchT)
            titlePaint.alpha = (clamp01((elapsedMs - TITLE_START_MS) / 150f) * 255).toInt()
            canvas.save()
            canvas.scale(scale, scale, titleCx, titleCy)
            canvas.drawText(title, titleCx, titleCy + titlePaint.textSize * 0.32f, titlePaint)
            canvas.restore()
        }
    }

    private fun drawBalloon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        bodyPaint: Paint,
        shadePaint: Paint,
        wobble: Float,
        alpha: Int
    ) {
        if (radius <= 0f) return
        bodyPaint.alpha = alpha
        shadePaint.alpha = alpha
        highlightPaint.alpha = (200 * alpha / 255).coerceIn(0, 255)
        stringPaint.alpha = alpha

        canvas.drawCircle(cx, cy, radius, bodyPaint)
        canvas.drawOval(
            cx - radius * 0.55f, cy - radius * 0.75f,
            cx + radius * 0.1f, cy - radius * 0.1f,
            highlightPaint
        )
        canvas.drawOval(
            cx + radius * 0.25f, cy + radius * 0.25f,
            cx + radius * 0.85f, cy + radius * 0.8f,
            shadePaint
        )

        val knotPath = Path().apply {
            moveTo(cx - radius * 0.12f, cy + radius * 0.92f)
            lineTo(cx + radius * 0.12f, cy + radius * 0.92f)
            lineTo(cx, cy + radius * 1.1f)
            close()
        }
        canvas.drawPath(knotPath, shadePaint)

        val stringPath = Path().apply {
            moveTo(cx, cy + radius * 1.1f)
            quadTo(cx + wobble, cy + radius * 1.8f, cx, cy + radius * 2.4f)
        }
        canvas.drawPath(stringPath, stringPaint)
    }

    companion object {
        private const val TOTAL_MS = 3000L
        private const val ENTRANCE_END_MS = 500f
        private const val HOLD_END_MS = 800f
        private const val IMPACT_MS = 1050f
        private const val SHAKE_END_MS = 1200f
        private const val FLASH_MS = 180f
        private const val DEFLATE_END_MS = 1750f
        private const val TITLE_START_MS = 1500f
        private const val TITLE_PUNCH_MS = 300f
    }
}
