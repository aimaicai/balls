package com.hyperionsoftware.balls.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.hyperionsoftware.balls.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

class JoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onDirectionChanged(x: Float, y: Float)
    }

    var listener: Listener? = null

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.joystick_base)
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.joystick_knob)
    }

    private var centerX = 0f
    private var centerY = 0f
    private var baseRadius = 0f
    private var knobX = 0f
    private var knobY = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        baseRadius = min(w, h) / 2f
        knobX = centerX
        knobY = centerY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> updateKnob(event.x, event.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                knobX = centerX
                knobY = centerY
                listener?.onDirectionChanged(0f, 0f)
                invalidate()
            }
        }
        return true
    }

    private fun updateKnob(x: Float, y: Float) {
        val dx = x - centerX
        val dy = y - centerY
        val distance = min(baseRadius, hypot(dx, dy))
        val angle = atan2(dy, dx)
        knobX = centerX + cos(angle) * distance
        knobY = centerY + sin(angle) * distance

        val normalizedX = if (baseRadius > 0f) (knobX - centerX) / baseRadius else 0f
        val normalizedY = if (baseRadius > 0f) (knobY - centerY) / baseRadius else 0f
        listener?.onDirectionChanged(normalizedX, normalizedY)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawCircle(centerX, centerY, baseRadius, basePaint)
        canvas.drawCircle(knobX, knobY, baseRadius * 0.4f, knobPaint)
    }
}
