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

    private var timeSinceAbsorb: Float = 0f
    private var speedBoostTimer: Float = 0f
    private var invisibilityTimer: Float = 0f

    val isInvisible: Boolean get() = invisibilityTimer > 0f
    val isSpeedBoosted: Boolean get() = speedBoostTimer > 0f

    abstract fun decideDirection(engine: GameEngine, dt: Float): Vector2

    open fun update(dt: Float, engine: GameEngine, baseSpeed: Float) {
        if (!alive) return

        val direction = decideDirection(engine, dt).normalized()
        val speed = effectiveSpeed(baseSpeed)
        position += direction * (speed * dt)
        clampToWorld()

        if (speedBoostTimer > 0f) speedBoostTimer = max(0f, speedBoostTimer - dt)
        if (invisibilityTimer > 0f) invisibilityTimer = max(0f, invisibilityTimer - dt)

        timeSinceAbsorb += dt
        if (timeSinceAbsorb > GameConfig.DEFLATE_GRACE_SECONDS && radius > baseRadius) {
            val shrink = radius * GameConfig.DEFLATE_RATE_PER_SECOND * dt
            radius = max(baseRadius, radius - shrink)
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

    fun applyPowerUp(type: PowerUpType) {
        when (type) {
            PowerUpType.SPEED -> speedBoostTimer = GameConfig.POWERUP_SPEED_DURATION
            PowerUpType.INVISIBILITY -> invisibilityTimer = GameConfig.POWERUP_INVISIBILITY_DURATION
            PowerUpType.GROWTH -> {
                val newArea = areaOf(radius) * GameConfig.POWERUP_GROWTH_FACTOR
                radius = min(GameConfig.MAX_RADIUS, sqrt(newArea / Math.PI.toFloat()))
                timeSinceAbsorb = 0f
            }
        }
    }

    private fun effectiveSpeed(baseSpeed: Float): Float {
        // Bigger blobs move slower; smaller ones are more nimble.
        val sizeFactor = sqrt(baseRadius / radius)
        val boost = if (isSpeedBoosted) GameConfig.POWERUP_SPEED_MULTIPLIER else 1f
        return baseSpeed * sizeFactor * boost
    }

    private fun areaOf(r: Float): Float = Math.PI.toFloat() * r * r
}
