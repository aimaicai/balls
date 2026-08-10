package com.hyperionsoftware.balls.game

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

abstract class Blob(
    val id: Int,
    var position: Vector2,
    val baseRadius: Float,
    val color: Int
) {
    var radius: Float = baseRadius
    var alive: Boolean = true

    // Voluntary speed boost: while true, size drains (with no floor, on top of the
    // ambient leak) in exchange for extra speed. Both the player and bots can set this.
    var isBoosting: Boolean = false

    // Balloons drift, so they need a "front" independent of any single frame's input: it
    // only updates while actually thrusting and holds steady otherwise. The exhaust (visual
    // and the push it applies to others) comes out the opposite side.
    var facingDirection: Vector2 = Vector2(0f, -1f)
    var isThrusting: Boolean = false
    var exhaustPhase: Float = 0f

    private var speedBoostTimer: Float = 0f
    private var invisibilityTimer: Float = 0f

    val isInvisible: Boolean get() = invisibilityTimer > 0f
    val isSpeedBoosted: Boolean get() = speedBoostTimer > 0f

    abstract fun decideDirection(engine: GameEngine, dt: Float): Vector2

    open fun update(dt: Float, engine: GameEngine, baseSpeed: Float) {
        if (!alive) return

        // The raw vector's length doubles as desired-speed fraction: bots always return
        // unit-length vectors (full speed), while the player's joystick vector length
        // reflects how far the stick is pushed, giving proportional analog control.
        val rawDirection = decideDirection(engine, dt)
        val magnitude = rawDirection.length().coerceAtMost(1f)
        val heading = rawDirection.normalized()

        isThrusting = magnitude > 0.05f
        if (isThrusting) {
            facingDirection = heading
        }
        exhaustPhase += dt

        val speed = effectiveSpeed(baseSpeed)
        val movement = heading * (speed * magnitude * dt)
        position += movement
        clampToWorld()

        if (speedBoostTimer > 0f) speedBoostTimer = max(0f, speedBoostTimer - dt)
        if (invisibilityTimer > 0f) invisibilityTimer = max(0f, invisibilityTimer - dt)
    }

    fun clampToWorld() {
        position.x = position.x.coerceIn(radius, GameConfig.WORLD_WIDTH - radius)
        position.y = position.y.coerceIn(radius, GameConfig.WORLD_HEIGHT - radius)
    }

    fun absorb(other: Blob) {
        val newArea = areaOf(radius) + areaOf(other.radius)
        radius = min(GameConfig.MAX_RADIUS, sqrt(newArea / Math.PI.toFloat()))
        other.alive = false
    }

    // Balloons always leak air, in or out of the safe zone - slower inside it, faster
    // outside - so standing still is never truly safe, only "safer". Hitting
    // ZONE_DEATH_RADIUS deflates the balloon for good. Returns true if this was lethal.
    fun applyAmbientDeflation(inSafeZone: Boolean, dt: Float): Boolean {
        val rate = if (inSafeZone) {
            GameConfig.AMBIENT_DEFLATE_RATE_PER_SECOND
        } else {
            GameConfig.OUT_OF_ZONE_DEFLATE_RATE_PER_SECOND
        }
        val shrink = radius * rate * dt
        radius -= shrink
        if (radius < GameConfig.ZONE_DEATH_RADIUS) {
            alive = false
            return true
        }
        return false
    }

    // Sprinting works everywhere, any time, with no floor at baseRadius: it always drains
    // size in exchange for speed, and burning all the way down to ZONE_DEATH_RADIUS kills
    // the balloon. Returns true if this drain was lethal.
    fun applyBoostDrain(dt: Float): Boolean {
        val drain = radius * GameConfig.BOOST_DRAIN_RATE_PER_SECOND * dt
        radius -= drain
        if (radius < GameConfig.ZONE_DEATH_RADIUS) {
            alive = false
            return true
        }
        return false
    }

    fun applyPowerUp(type: PowerUpType) {
        when (type) {
            PowerUpType.SPEED -> speedBoostTimer = GameConfig.POWERUP_SPEED_DURATION
            PowerUpType.INVISIBILITY -> invisibilityTimer = GameConfig.POWERUP_INVISIBILITY_DURATION
            PowerUpType.GROWTH -> {
                radius = min(GameConfig.MAX_RADIUS, radius * GameConfig.POWERUP_GROWTH_MULTIPLIER)
            }
        }
    }

    private fun effectiveSpeed(baseSpeed: Float): Float {
        // Bigger blobs move slower; smaller ones are more nimble.
        val sizeFactor = sqrt(baseRadius / radius)
        val powerUpBoost = if (isSpeedBoosted) GameConfig.POWERUP_SPEED_MULTIPLIER else 1f
        // Rewarded as long as there is still any size left to burn - sprinting keeps
        // working right up until it kills the balloon.
        val dashBoost = if (isBoosting && radius > GameConfig.ZONE_DEATH_RADIUS) {
            GameConfig.BOOST_SPEED_MULTIPLIER
        } else {
            1f
        }
        return baseSpeed * sizeFactor * powerUpBoost * dashBoost
    }

    private fun areaOf(r: Float): Float = Math.PI.toFloat() * r * r
}
