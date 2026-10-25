package com.hyperionsoftware.balls.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatImageButton
import com.hyperionsoftware.balls.R
import kotlin.math.sin

// The static-discharge button, doubling as the charge meter: a ring around it fills up as the
// player's balloon picks up static from rubbing (see GameEngine.rub). It only works once the
// ring is full - then the bolt lights up and the ring crackles with a flickering glow.
class StaticChargeButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageButton(context, attrs) {

    var charge: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (clamped == field) return
            field = clamped
            refreshState()
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val ringWidth = 4f * density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ringWidth
        color = Color.argb(70, 255, 255, 255)
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ringWidth
        strokeCap = Paint.Cap.ROUND
        color = CHARGING_COLOR
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ringWidth * 2.4f
        color = FULL_COLOR
    }
    private val ring = RectF()
    private var flicker = 0f
    private val flickerAnimator = ValueAnimator.ofFloat(0f, 1000f).apply {
        duration = 1_000_000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            flicker = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        refreshState()
    }

    private val isFull: Boolean get() = charge >= 1f

    private fun refreshState() {
        isEnabled = isFull
        imageAlpha = if (isFull) 255 else 110
        contentDescription = if (isFull) {
            context.getString(R.string.static_discharge_button)
        } else {
            context.getString(R.string.static_discharge_charging, (charge * 100f).toInt())
        }
        if (isFull && isAttachedToWindow) {
            if (!flickerAnimator.isStarted) flickerAnimator.start()
        } else {
            flickerAnimator.cancel()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshState()
    }

    override fun onDetachedFromWindow() {
        flickerAnimator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = ringWidth / 2f + density
        ring.set(inset, inset, width - inset, height - inset)
        if (isFull) {
            // Two out-of-step sines (in seconds, flicker counts milliseconds): an uneven,
            // electric flicker rather than a smooth pulse.
            val t = flicker / 1000f
            val strength = 0.55f + 0.45f * sin(t * 23f) * sin(t * 9.7f + 1.3f)
            glowPaint.alpha = (110 * strength).toInt().coerceIn(20, 255)
            canvas.drawOval(ring, glowPaint)
            progressPaint.color = FULL_COLOR
            canvas.drawOval(ring, progressPaint)
        } else {
            canvas.drawOval(ring, trackPaint)
            if (charge > 0f) {
                progressPaint.color = CHARGING_COLOR
                canvas.drawArc(ring, -90f, 360f * charge, false, progressPaint)
            }
        }
    }

    companion object {
        private val CHARGING_COLOR = Color.parseColor("#8C9EFF")
        private val FULL_COLOR = Color.parseColor("#E1F5FE")
    }
}
