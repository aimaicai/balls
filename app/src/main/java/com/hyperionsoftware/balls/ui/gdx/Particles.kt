package com.hyperionsoftware.balls.ui.gdx

import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.MathUtils

// A fixed-capacity pool of simple particles - exhaust smoke, confetti from a popped balloon,
// sparkles - stored as parallel primitive arrays so spawning and updating never allocate. When
// the pool is full, new particles overwrite the oldest slot instead of being dropped, which only
// ever happens in an extreme pile-up and is invisible then anyway.
class Particles(private val capacity: Int) {
    enum class Kind { SMOKE, CONFETTI, SPARK }

    private val x = FloatArray(capacity)
    private val y = FloatArray(capacity)
    private val vx = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    private val life = FloatArray(capacity)
    private val maxLife = FloatArray(capacity)
    private val startSize = FloatArray(capacity)
    private val endSize = FloatArray(capacity)
    private val startAlpha = FloatArray(capacity)
    private val rotation = FloatArray(capacity)
    private val spin = FloatArray(capacity)
    private val drag = FloatArray(capacity)
    private val color = IntArray(capacity)
    private val endColor = IntArray(capacity)
    private val kind = arrayOfNulls<Kind>(capacity)
    private var cursor = 0

    fun clear() {
        life.fill(0f)
    }

    fun spawn(
        kind: Kind,
        x: Float,
        y: Float,
        vx: Float,
        vy: Float,
        lifetime: Float,
        startSize: Float,
        endSize: Float,
        argb: Int,
        endArgb: Int = argb,
        alpha: Float = 1f,
        drag: Float = 0f
    ) {
        val i = cursor
        cursor = (cursor + 1) % capacity
        this.kind[i] = kind
        this.x[i] = x
        this.y[i] = y
        this.vx[i] = vx
        this.vy[i] = vy
        life[i] = lifetime
        maxLife[i] = lifetime
        this.startSize[i] = startSize
        this.endSize[i] = endSize
        color[i] = argb
        endColor[i] = endArgb
        startAlpha[i] = alpha
        this.drag[i] = drag
        rotation[i] = MathUtils.random(360f)
        spin[i] = MathUtils.random(-360f, 360f)
    }

    fun update(dt: Float) {
        if (dt <= 0f) return
        for (i in 0 until capacity) {
            if (life[i] <= 0f) continue
            life[i] -= dt
            val damping = 1f / (1f + drag[i] * dt)
            vx[i] *= damping
            vy[i] *= damping
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            rotation[i] += spin[i] * dt
        }
    }

    // Draws every live particle of `kind` whose bounds touch the given world rectangle.
    fun draw(painter: Painter, kind: Kind, region: TextureRegion, left: Float, top: Float, right: Float, bottom: Float) {
        for (i in 0 until capacity) {
            if (life[i] <= 0f || this.kind[i] != kind) continue
            val progress = 1f - life[i] / maxLife[i]
            val size = startSize[i] + (endSize[i] - startSize[i]) * progress
            val half = size / 2f
            if (x[i] + half < left || x[i] - half > right || y[i] + half < top || y[i] - half > bottom) continue
            // Smoke billows in and then thins out; confetti and sparks just fade toward the end.
            val fade = when (kind) {
                Kind.SMOKE -> (progress / 0.15f).coerceAtMost(1f) * (1f - progress)
                else -> (1f - progress * progress)
            }
            painter.color(lerpColor(color[i], endColor[i], progress), startAlpha[i] * fade)
            if (kind == Kind.CONFETTI) {
                // A flat little card tumbling as it flies.
                painter.transformed(region, x[i], y[i], size, 1f, 0.55f + 0.45f * MathUtils.sinDeg(rotation[i] * 2f), rotation[i])
            } else if (region === painter.atlas.glow) {
                // Round and soft, so its spin never shows - and a big puff of smoke is cheaper
                // drawn as glow() does it.
                painter.glow(x[i], y[i], half)
            } else {
                painter.region(region, x[i], y[i], size, size, rotation[i])
            }
        }
    }

    private fun lerpColor(from: Int, to: Int, t: Float): Int {
        if (from == to) return from
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * t).toInt() and 0xFF
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
