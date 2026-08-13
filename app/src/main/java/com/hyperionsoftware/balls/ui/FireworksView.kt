package com.hyperionsoftware.balls.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.animation.doOnEnd
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// A one-shot celebration overlay for a brand-new #1 high score: a few firework bursts
// (particles exploding outward, gravity-fed, fading out) plus confetti streamers drifting
// down from the top. Pure Canvas and no external assets, matching how the rest of the game
// draws everything else. Call start() once the view is visible; it stops itself when done.
class FireworksView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val color: Int,
        var life: Float,
        val maxLife: Float,
        val isConfetti: Boolean
    )

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animator: ValueAnimator? = null
    private var burstCooldown = 0f
    private var elapsedSeconds = 0f

    private val colors = intArrayOf(
        Color.parseColor("#FFC107"), Color.parseColor("#EF5350"),
        Color.parseColor("#4FC3F7"), Color.parseColor("#8BC34A"),
        Color.parseColor("#FFD700"), Color.parseColor("#CE93D8")
    )

    fun start() {
        // Cancel any previous run through a local var first: cancel() synchronously fires
        // the old animator's doOnEnd{ stop() }, which would otherwise clear the particle
        // list this call is about to populate if `animator` still pointed at it.
        val previous = animator
        animator = null
        previous?.cancel()

        particles.clear()
        elapsedSeconds = 0f
        burstCooldown = 0f
        repeat(CONFETTI_COUNT) { particles.add(newConfetti()) }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DURATION_MS
            addUpdateListener { step(FRAME_DT_SECONDS) }
            doOnEnd { stop() }
            start()
        }
    }

    fun stop() {
        animator?.cancel()
        particles.clear()
        invalidate()
    }

    private fun newConfetti(): Particle {
        val w = width.takeIf { it > 0 } ?: DEFAULT_SIZE
        return Particle(
            x = Random.nextFloat() * w,
            y = -20f - Random.nextFloat() * 300f,
            vx = (Random.nextFloat() - 0.5f) * 60f,
            vy = 180f + Random.nextFloat() * 140f,
            color = colors.random(),
            life = 4f,
            maxLife = 4f,
            isConfetti = true
        )
    }

    private fun spawnBurst() {
        val w = width.takeIf { it > 0 } ?: DEFAULT_SIZE
        val h = height.takeIf { it > 0 } ?: DEFAULT_SIZE
        val cx = w * (0.2f + Random.nextFloat() * 0.6f)
        val cy = h * (0.2f + Random.nextFloat() * 0.35f)
        val color = colors.random()
        repeat(BURST_PARTICLE_COUNT) {
            val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
            val speed = 220f + Random.nextFloat() * 180f
            particles.add(
                Particle(
                    x = cx, y = cy,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    color = color,
                    life = 1.1f,
                    maxLife = 1.1f,
                    isConfetti = false
                )
            )
        }
    }

    private fun step(dt: Float) {
        elapsedSeconds += dt
        burstCooldown -= dt
        if (burstCooldown <= 0f && elapsedSeconds < DURATION_MS / 1000f - 1f) {
            spawnBurst()
            burstCooldown = 0.5f + Random.nextFloat() * 0.4f
        }
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.life -= dt
            if (p.life <= 0f) {
                iterator.remove()
                continue
            }
            if (!p.isConfetti) p.vy += GRAVITY * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (p in particles) {
            paint.color = p.color
            paint.alpha = (255 * (p.life / p.maxLife).coerceIn(0f, 1f)).toInt()
            canvas.drawCircle(p.x, p.y, if (p.isConfetti) 8f else 6f, paint)
        }
    }

    companion object {
        private const val DURATION_MS = 4500L
        private const val FRAME_DT_SECONDS = 1f / 60f
        private const val CONFETTI_COUNT = 40
        private const val BURST_PARTICLE_COUNT = 28
        private const val GRAVITY = 260f
        private const val DEFAULT_SIZE = 1080
    }
}
