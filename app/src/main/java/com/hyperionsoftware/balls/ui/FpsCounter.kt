package com.hyperionsoftware.balls.ui

import android.graphics.Color
import com.hyperionsoftware.balls.ui.gdx.Painter
import com.hyperionsoftware.balls.ui.gdx.TextStyle

// A developer-mode-only overlay (see DeveloperSettings) shared by GameRenderer and
// RaceRenderer: average FPS plus the single slowest frame over the last half second. The
// average alone hides the occasional long frame that actually reads as a stutter, which is what
// matters for judging smoothness. Both numbers are measured between consecutive draw() calls on
// the render thread, so they include the whole frame (update, render, buffer swap).
class FpsCounter(private val density: Float) {
    private val textStyle = TextStyle(13f * density, bold = true, align = TextStyle.Align.CENTER)
    private var lastFrameNanos = 0L
    private var windowStartNanos = 0L
    private var windowFrames = 0
    private var windowWorstNanos = 0L
    private var text = ""
    private var textColor = Color.WHITE

    // Called once per frame (in screen space, after everything else but its own text - see
    // drawText): measures the frame and draws the dark backdrop. Placed bottom-center, the one
    // spot on screen not taken by the joystick, the boost and item buttons, or the HUD text
    // along the top.
    fun drawBackground(painter: Painter, screenWidth: Float, screenHeight: Float) {
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
            textColor = when {
                fps >= 55f && worstMs <= 25f -> GOOD_COLOR
                fps >= 40f -> OK_COLOR
                else -> BAD_COLOR
            }
            windowStartNanos = now
            windowFrames = 0
            windowWorstNanos = 0L
        }
        if (text.isEmpty()) return
        val x = screenWidth / 2f
        val y = screenHeight - 12f * density
        val halfWidth = painter.measure(text, textStyle) / 2f + 8f * density
        painter.color(BACKGROUND_COLOR)
        painter.rect(x - halfWidth, y - textStyle.size - 4f * density, halfWidth * 2f, textStyle.size + 10f * density)
    }

    fun drawText(painter: Painter, screenWidth: Float, screenHeight: Float) {
        if (text.isEmpty()) return
        painter.text(text, screenWidth / 2f, screenHeight - 12f * density, textStyle, textColor)
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
        private val GOOD_COLOR = Color.parseColor("#8BC34A")
        private val OK_COLOR = Color.parseColor("#FFC107")
        private val BAD_COLOR = Color.parseColor("#EF5350")
        private val BACKGROUND_COLOR = Color.argb(140, 0, 0, 0)
    }
}
