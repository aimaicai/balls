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

    // Voluntary speed boost: while true (player holds the boost button), size drains
    // toward baseRadius in exchange for extra speed. Bots never set this.
    var isBoosting: Boolean = false

    // Balloons drift, so they need a "front" independent of any single frame's input: it
    // only updates while actually thrusting and holds steady otherwise. The exhaust (visual
    // and the push it applies to others) comes out the opposite side.
    var facingDirection: Vector2 = Vector2(0f, -1f)
    var isThrusting: Boolean = false
    var exhaustPhase: Float = 0f

    private var timeSinceAbsorb: Float = 0f
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

        timeSinceAbsorb += dt
        if (timeSinceAbsorb > GameConfig.DEFLATE_GRACE_SECONDS && radius > baseRadius) {
            val shrink = radius * GameConfig.DEFLATE_RATE_PER_SECOND * dt
            radius = max(baseRadius, radius - shrink)
        }

        if (isBoosting && radius > baseRadius) {
            val drain = radius * GameConfig.BOOST_DRAIN_RATE_PER_SECOND * dt
            radius = max(baseRadius, radius - drain)
        }
    }

    fun clampToWorld() {
        position.x = position.x.coerceIn(radius, GameConfig.WORLD_WIDTH - radius)
        position.y = position.y.coerceIn(radius, GameConfig.WORLD_HEIGHT - radius)
    }

    fun absorb(other: Blob) {
        val newArea = areaOf(radius) + areaOf(other.radius)
        radius = min(GameConfig.MAX_RADIUS, sqrt(newArea / Math.PI.toFloat()))
        timeSinceAbsorb = 0f
        other.alive = false
    }

    // Outside the safe zone, size decays with no floor at baseRadius (unlike normal
    // deflation): staying out too long can shrink a blob below its starting size and,
    // past ZONE_DEATH_RADIUS, kill it outright. Returns true if this damage was lethal.
    fun applyZoneDamage(dt: Float): Boolean {
        val shrink = radius * GameConfig.SAFE_ZONE_DAMAGE_RATE_PER_SECOND * dt
        radius -= shrink
        if (radius < GameConfig.ZONE_DEATH_RADIUS) {
            alive = false
            return true
        }
        return false
    }

    // Getting back inside the safe zone heals lost size back up to baseRadius (but never
    // beyond it - regaining size above that still requires absorbing or a power-up).
    fun healInZone(dt: Float) {
        if (radius < baseRadius) {
            val growth = baseRadius * GameConfig.ZONE_HEAL_RATE_PER_SECOND * dt
            radius = min(baseRadius, radius + growth)
        }
    }

    fun applyPowerUp(type: PowerUpType) {
        when (type) {
            PowerUpType.SPEED -> speedBoostTimer = GameConfig.POWERUP_SPEED_DURATION
            PowerUpType.INVISIBILITY -> invisibilityTimer = GameConfig.POWERUP_INVISIBILITY_DURATION
            PowerUpType.GROWTH -> {
                radius = min(GameConfig.MAX_RADIUS, radius * GameConfig.POWERUP_GROWTH_MULTIPLIER)
                timeSinceAbsorb = 0f
            }
        }
    }

    private fun effectiveSpeed(baseSpeed: Float): Float {
        // Bigger blobs move slower; smaller ones are more nimble.
        val sizeFactor = sqrt(baseRadius / radius)
        val powerUpBoost = if (isSpeedBoosted) GameConfig.POWERUP_SPEED_MULTIPLIER else 1f
        // Only rewarded while there is still size left to burn this frame - matches the
        // drain above, which also stops once radius reaches baseRadius.
        val dashBoost = if (isBoosting && radius > baseRadius) GameConfig.BOOST_SPEED_MULTIPLIER else 1f
        return baseSpeed * sizeFactor * powerUpBoost * dashBoost
    }

    private fun areaOf(r: Float): Float = Math.PI.toFloat() * r * r
}
