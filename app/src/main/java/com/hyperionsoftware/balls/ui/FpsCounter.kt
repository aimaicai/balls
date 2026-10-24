package com.hyperionsoftware.balls.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

// A developer-mode-only overlay (see DeveloperSettings) shared by GameView and RaceView:
// average FPS plus the single slowest frame over the last half second. The average alone
// hides the occasional long frame that actually reads as a stutter, which is what matters
// for judging smoothness. Both numbers are measured between consecutive draw() calls on the
// loop thread, so they include the whole frame (update, render, posting), not just render.
class FpsCounter(private val density: Float) {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f * density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val backgroundPaint = Paint().apply { color = Color.argb(140, 0, 0, 0) }

    private var lastFrameNanos = 0L
    private var windowStartNanos = 0L
    private var windowFrames = 0
    private var windowWorstNanos = 0L
    private var text = ""

    // Called once per frame, as the very last thing drawn, so it sits on top of everything.
    // Placed bottom-center, the one spot on screen not taken by the joystick, the boost and
    // item buttons, or the HUD text along the top.
    fun draw(canvas: Canvas) {
        val now = System.nanoTime()
        if (lastFrameNanos != 0L) {
            windowFrames++
            windowWorstNanos = maxOf(windowWorstNanos, now - lastFrameNanos)
        } else {
            windowStartNanos = now
        }
        lastFrameNanos = now

        val windowNanos = now - windowStartNanos
        if (windowNanos >= WINDOW_NANOS && windowFrames > 0) {
            val fps = windowFrames * 1_000_000_000f / windowNanos
            val worstMs = windowWorstNanos / 1_000_000f
            text = "%.0f FPS · max %.1f ms".format(fps, worstMs)
            // Green only when both the average and the worst frame are comfortably smooth -
            // a high average with a long worst frame still means a visible hitch.
            textPaint.color = when {
                fps >= 55f && worstMs <= 25f -> Color.parseColor("#8BC34A")
                fps >= 40f -> Color.parseColor("#FFC107")
                else -> Color.parseColor("#EF5350")
            }
            windowStartNanos = now
            windowFrames = 0
            windowWorstNanos = 0L
        }
        if (text.isEmpty()) return

        val x = canvas.width / 2f
        val y = canvas.height - 12f * density
        val halfWidth = textPaint.measureText(text) / 2f + 8f * density
        canvas.drawRect(x - halfWidth, y - textPaint.textSize - 4f * density, x + halfWidth, y + 6f * density, backgroundPaint)
        canvas.drawText(text, x, y, textPaint)
    }

    // A pause (or the surface going away) would otherwise count as one enormous frame.
    fun reset() {
        lastFrameNanos = 0L
        windowFrames = 0
        windowWorstNanos = 0L
        text = ""
    }

    companion object {
        private const val WINDOW_NANOS = 500_000_000L
    }
}
