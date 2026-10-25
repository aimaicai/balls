package com.hyperionsoftware.balls.ui.gdx

import kotlin.math.max
import kotlin.math.min

// The membranes (see BalloonBody) of the balloons drawn this frame, keyed by the blob's id, and
// the contacts between them. Off-screen balloons aren't simulated at all: their bodies just
// start again from rest when they come back into view. Shared by GameRenderer and RaceRenderer.
class BalloonBodies {
    private var bodies = arrayOfNulls<BalloonBody>(16)
    private val active = ArrayList<BalloonBody>()
    private val touch = BalloonBody.Touch()
    private var frame = 0L

    // The loudest rubbing this frame, for the squeak (see RubPlayer): how hard, 0..1, and how
    // fast the latex slides, 0..1. Rubbing the player takes part in counts in full, anyone
    // else's only faintly.
    var rubIntensity = 0f
        private set
    var rubSpeed = 0f
        private set

    // How exaggerated the latex's deformation is (1 = default, 0 = rigid balloons) - a
    // developer-mode tuning knob, see DeveloperSettings.getDeformationPercent.
    var intensity = 1f

    fun beginFrame() {
        frame++
        active.clear()
        rubIntensity = 0f
        rubSpeed = 0f
    }

    fun track(
        id: Int,
        isPlayer: Boolean,
        x: Float,
        y: Float,
        radius: Float,
        facingX: Float,
        facingY: Float,
        speed: Float,
        thrusting: Boolean,
        boosting: Boolean,
        frozen: Boolean,
        dt: Float
    ): BalloonBody {
        if (id >= bodies.size) bodies = bodies.copyOf(max(id + 1, bodies.size * 2))
        val body = bodies[id] ?: BalloonBody().also { bodies[id] = it }
        body.intensity = intensity
        body.prepare(frame, isPlayer, x, y, radius, facingX, facingY, speed, thrusting, boosting, frozen, dt)
        active.add(body)
        return body
    }

    // The body tracked this frame for `id`, or null if it wasn't (off screen).
    operator fun get(id: Int): BalloonBody? = bodies.getOrNull(id)?.takeIf { it.frame == frame }

    fun step(dt: Float) {
        // Only the balloons on screen take part, so this stays a handful of pairs.
        for (i in active.indices) {
            val a = active[i]
            for (j in i + 1 until active.size) {
                val b = active[j]
                if (!BalloonBody.touch(a, b, touch)) continue
                val weight = if (a.isPlayer || b.isPlayer) 1f else BYSTANDER_RUB_WEIGHT
                val strength = (touch.slidingSpeed + PRESSING_RUB_SHARE * touch.pressingSpeed) / FULL_RUB_SPEED
                rubIntensity = max(rubIntensity, weight * min(strength, 1f))
                rubSpeed = max(rubSpeed, min(touch.slidingSpeed / FAST_RUB_SPEED, 1f))
            }
        }
        for (body in active) body.settle(dt)
        for (body in active) body.simulate(dt)
    }

    fun clear() {
        bodies.fill(null)
        active.clear()
    }

    companion object {
        private const val BYSTANDER_RUB_WEIGHT = 0.3f
        private const val PRESSING_RUB_SHARE = 0.3f
        private const val FULL_RUB_SPEED = 220f
        private const val FAST_RUB_SPEED = 420f
    }
}
