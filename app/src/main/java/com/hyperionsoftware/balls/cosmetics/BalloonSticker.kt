package com.hyperionsoftware.balls.cosmetics

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.hyperionsoftware.balls.R
import com.hyperionsoftware.balls.achievements.Achievement
import kotlin.math.cos
import kotlin.math.sin

// A small decal drawn on top of the player's own balloon - purely cosmetic, same unlock
// pattern as PlayerColor (a few free from the start, the rest behind achievements already
// worth chasing, or buyable outright with Helium via costHelium - see CustomizeActivity).
// Never drawn upside down or skewed with the balloon's facing direction - it's meant to read
// as a sticker the player picked, not a directional indicator. Bots never wear one; this
// only ever affects the player's own balloon (see GameView.drawSticker).
enum class BalloonSticker(val labelResId: Int, val requiredAchievement: Achievement?, val costHelium: Int = 0) {
    NONE(R.string.sticker_none, null), // the default - no sticker, the plain balloon
    STAR(R.string.sticker_star, null),
    HEART(R.string.sticker_heart, null),
    CROWN(R.string.sticker_crown, Achievement.FIRST_WIN, 120),
    SKULL(R.string.sticker_skull, Achievement.ABSORB_STREAK, 120),
    LIGHTNING(R.string.sticker_lightning, Achievement.MAX_BOOST, 120),
    FIRE(R.string.sticker_fire, Achievement.DAILY_DEDICATION, 120),
    SPARK(R.string.sticker_spark, Achievement.COMBO_MASTER, 120);

    // Draws this sticker centered at the canvas's current origin, sized to roughly fit
    // within +/-size on each axis. Shared by the in-game balloon decal (GameView) and the
    // customize-screen preview swatch (CustomizeActivity) so both always look identical.
    // detailPaint is only used by SKULL, for its eye holes - a second, contrasting color so
    // they read as holes rather than just more of the same ink.
    fun drawInto(canvas: Canvas, paint: Paint, detailPaint: Paint, size: Float) {
        when (this) {
            NONE -> {}
            STAR -> canvas.drawPath(starPath(size), paint)
            HEART -> drawHeart(canvas, paint, size)
            CROWN -> canvas.drawPath(crownPath(size), paint)
            SKULL -> drawSkull(canvas, paint, detailPaint, size)
            LIGHTNING -> canvas.drawPath(lightningPath(size), paint)
            FIRE -> canvas.drawPath(firePath(size), paint)
            SPARK -> drawSpark(canvas, paint, size)
        }
    }

    private fun starPath(size: Float): Path {
        val outerR = size
        val innerR = size * 0.42f
        val path = Path()
        for (i in 0 until 10) {
            val angleRad = Math.toRadians((-90f + i * 36f).toDouble()).toFloat()
            val r = if (i % 2 == 0) outerR else innerR
            val x = cos(angleRad) * r
            val y = sin(angleRad) * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    // Built from two overlapping circles (the top lobes) plus a triangle (the bottom point)
    // instead of bezier math - all filled with the same opaque paint, so the overlaps are
    // invisible and the result reads as one solid heart.
    private fun drawHeart(canvas: Canvas, paint: Paint, size: Float) {
        val lobeR = size * 0.55f
        canvas.drawCircle(-lobeR * 0.95f, -lobeR * 0.15f, lobeR, paint)
        canvas.drawCircle(lobeR * 0.95f, -lobeR * 0.15f, lobeR, paint)
        val point = Path().apply {
            moveTo(-lobeR * 1.85f, -lobeR * 0.05f)
            lineTo(lobeR * 1.85f, -lobeR * 0.05f)
            lineTo(0f, size * 1.05f)
            close()
        }
        canvas.drawPath(point, paint)
    }

    private fun crownPath(size: Float): Path {
        val w = size * 1.1f
        val baseY = size * 0.35f
        val topY = -size * 0.85f
        val midY = -size * 0.15f
        return Path().apply {
            moveTo(-w, baseY)
            lineTo(-w, midY)
            lineTo(-w * 0.5f, topY)
            lineTo(0f, midY)
            lineTo(w * 0.5f, topY)
            lineTo(w, midY)
            lineTo(w, baseY)
            close()
        }
    }

    private fun drawSkull(canvas: Canvas, paint: Paint, detailPaint: Paint, size: Float) {
        canvas.drawCircle(0f, -size * 0.1f, size * 0.85f, paint)
        val jaw = Path().apply {
            moveTo(-size * 0.55f, size * 0.35f)
            lineTo(size * 0.55f, size * 0.35f)
            lineTo(size * 0.4f, size * 0.85f)
            lineTo(-size * 0.4f, size * 0.85f)
            close()
        }
        canvas.drawPath(jaw, paint)
        canvas.drawCircle(-size * 0.32f, -size * 0.15f, size * 0.22f, detailPaint)
        canvas.drawCircle(size * 0.32f, -size * 0.15f, size * 0.22f, detailPaint)
        val nose = Path().apply {
            moveTo(0f, size * 0.05f)
            lineTo(-size * 0.12f, size * 0.3f)
            lineTo(size * 0.12f, size * 0.3f)
            close()
        }
        canvas.drawPath(nose, detailPaint)
    }

    // The same bolt silhouette as GameView's SPEED power-up badge, just re-scaled - a
    // deliberately familiar shape rather than a second, subtly-different lightning icon.
    private fun lightningPath(size: Float): Path = Path().apply {
        moveTo(size * 0.15f, -size * 0.6f)
        lineTo(-size * 0.35f, size * 0.05f)
        lineTo(size * 0.05f, size * 0.05f)
        lineTo(-size * 0.15f, size * 0.6f)
        lineTo(size * 0.45f, -size * 0.05f)
        lineTo(size * 0.05f, -size * 0.05f)
        close()
    }

    private fun firePath(size: Float): Path = Path().apply {
        moveTo(0f, size * 0.9f)
        quadTo(-size * 0.75f, size * 0.35f, -size * 0.35f, -size * 0.1f)
        quadTo(-size * 0.55f, -size * 0.5f, -size * 0.05f, -size * 0.95f)
        quadTo(size * 0.15f, -size * 0.55f, 0f, -size * 0.15f)
        quadTo(size * 0.45f, -size * 0.35f, size * 0.35f, size * 0.15f)
        quadTo(size * 0.3f, size * 0.5f, 0f, size * 0.9f)
        close()
    }

    // Two elongated diamonds crossed at 45 degrees - an 8-point twinkle rather than a plain
    // 4 or 5-point star, so it stays visually distinct from STAR.
    private fun drawSpark(canvas: Canvas, paint: Paint, size: Float) {
        val diamond = Path().apply {
            moveTo(0f, -size)
            lineTo(size * 0.18f, 0f)
            lineTo(0f, size)
            lineTo(-size * 0.18f, 0f)
            close()
        }
        canvas.drawPath(diamond, paint)
        canvas.save()
        canvas.rotate(45f)
        canvas.drawPath(diamond, paint)
        canvas.restore()
    }
}
