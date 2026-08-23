package com.hyperionsoftware.balls.cosmetics

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.hyperionsoftware.balls.R
import com.hyperionsoftware.balls.achievements.Achievement

// The shape of the player's own exhaust puffs (see GameView.drawExhaust) - purely cosmetic,
// same unlock pattern as PlayerColor/BalloonSticker/BalloonCord: a couple free, the rest
// behind achievements or buyable outright with Helium via costHelium (see CustomizeActivity).
// Bots always keep the original plain circle puffs, this only ever affects the player's own
// trail.
enum class ExhaustStyle(val labelResId: Int, val requiredAchievement: Achievement?, val costHelium: Int = 0) {
    CLASSIC(R.string.exhaust_classic, null), // the original, unchanged default
    BUBBLES(R.string.exhaust_bubbles, null),
    STARDUST(R.string.exhaust_stardust, Achievement.USE_SPEED, 100),
    HEARTS(R.string.exhaust_hearts, Achievement.USE_INVISIBILITY, 100),
    CLOUDS(R.string.exhaust_clouds, Achievement.MAX_SIZE, 100);

    // Draws a single puff centered at (cx, cy) with the given color/alpha already set on
    // paint. Shared by the actual in-game exhaust trail and the CustomizeActivity preview
    // swatch, so both always look identical.
    fun drawPuff(canvas: Canvas, paint: Paint, cx: Float, cy: Float, radius: Float) {
        when (this) {
            CLASSIC -> canvas.drawCircle(cx, cy, radius, paint)
            BUBBLES -> drawBubble(canvas, paint, cx, cy, radius)
            STARDUST -> canvas.drawPath(sparkPath(cx, cy, radius), paint)
            HEARTS -> drawHeart(canvas, paint, cx, cy, radius)
            CLOUDS -> drawCloud(canvas, paint, cx, cy, radius)
        }
    }

    // A hollow ring instead of a filled disc - reuses paint's own current color/alpha via a
    // fresh Paint copy so the caller never has to restore mutated style/strokeWidth state.
    private fun drawBubble(canvas: Canvas, paint: Paint, cx: Float, cy: Float, radius: Float) {
        val ringPaint = Paint(paint).apply {
            style = Paint.Style.STROKE
            strokeWidth = (radius * 0.28f).coerceAtLeast(1f)
        }
        canvas.drawCircle(cx, cy, radius * 0.8f, ringPaint)
    }

    private fun sparkPath(cx: Float, cy: Float, radius: Float): Path {
        val diamond = Path().apply {
            moveTo(cx, cy - radius)
            lineTo(cx + radius * 0.34f, cy)
            lineTo(cx, cy + radius)
            lineTo(cx - radius * 0.34f, cy)
            close()
        }
        val cross = Path().apply {
            moveTo(cx - radius, cy)
            lineTo(cx, cy - radius * 0.34f)
            lineTo(cx + radius, cy)
            lineTo(cx, cy + radius * 0.34f)
            close()
        }
        diamond.addPath(cross)
        return diamond
    }

    private fun drawHeart(canvas: Canvas, paint: Paint, cx: Float, cy: Float, radius: Float) {
        val lobeR = radius * 0.55f
        canvas.drawCircle(cx - lobeR * 0.95f, cy - lobeR * 0.15f, lobeR, paint)
        canvas.drawCircle(cx + lobeR * 0.95f, cy - lobeR * 0.15f, lobeR, paint)
        val point = Path().apply {
            moveTo(cx - lobeR * 1.85f, cy - lobeR * 0.05f)
            lineTo(cx + lobeR * 1.85f, cy - lobeR * 0.05f)
            lineTo(cx, cy + radius * 1.05f)
            close()
        }
        canvas.drawPath(point, paint)
    }

    // A little cluster of three overlapping circles instead of one, for a fluffier, more
    // cloud-like silhouette.
    private fun drawCloud(canvas: Canvas, paint: Paint, cx: Float, cy: Float, radius: Float) {
        canvas.drawCircle(cx, cy, radius * 0.72f, paint)
        canvas.drawCircle(cx - radius * 0.55f, cy + radius * 0.18f, radius * 0.52f, paint)
        canvas.drawCircle(cx + radius * 0.55f, cy + radius * 0.18f, radius * 0.52f, paint)
    }
}
