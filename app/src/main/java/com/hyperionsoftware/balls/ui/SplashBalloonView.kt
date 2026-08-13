package com.hyperionsoftware.balls.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.core.animation.doOnEnd
import kotlin.math.sin

// The launch splash's balloon: the same Canvas primitives as every in-game balloon (a
// circle body, an ellipse highlight, a shaded crescent, a triangular knot and a curved
// string) - no image assets - but with its own pop-in-then-bob animation instead of
// following a Blob.
class SplashBalloonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4FC3F7") }
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2E9FD6") }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 200
    }
    private val knotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2E9FD6") }
    private val stringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#CFD8DC")
    }

    private var introProgress = 0f
    private var bobPhase = 0f
    private var animator: ValueAnimator? = null

    // Pop-in with a small overshoot bounce, then settles into a slow, endless bob - call
    // once the view is on screen.
    fun start() {
        animator?.cancel()
        introProgress = 0f
        bobPhase = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = INTRO_DURATION_MS
            interpolator = OvershootInterpolator(2.2f)
            addUpdateListener {
                introProgress = it.animatedValue as Float
                invalidate()
            }
            doOnEnd { startBob() }
            start()
        }
    }

    private fun startBob() {
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = BOB_DURATION_MS
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                bobPhase += 1f / 60f
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        // Anchored toward the top of the view, not center, so the knot and string have
        // room to hang below without being clipped.
        val cy = height * 0.32f + sin(bobPhase * 1.4f) * 14f
        val radius = (width.coerceAtMost(height) / 2f) * 0.42f * introProgress
        if (radius <= 0f) return

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
        canvas.drawPath(knotPath, knotPaint)

        val stringSway = sin(bobPhase * 1.4f + 1f) * radius * 0.25f
        val stringPath = Path().apply {
            moveTo(cx, cy + radius * 1.1f)
            quadTo(cx + stringSway, cy + radius * 2.2f, cx, cy + radius * 3.2f)
        }
        canvas.drawPath(stringPath, stringPaint)
    }

    companion object {
        private const val INTRO_DURATION_MS = 650L
        private const val BOB_DURATION_MS = 10_000L
    }
}
