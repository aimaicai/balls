package com.hyperionsoftware.balls.game

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
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
    // For the player this is also the sole thrust input (see wantsToAccelerate below);
    // bots keep it strictly for the emergency dash on top of their normal movement.
    var isBoosting: Boolean = false

    // The joystick/AI only ever aims this - it turns gradually (see steerTowards), never
    // snapping straight to the opposite heading. Actual motion is carried by velocity,
    // which thrusting builds up and drag bleeds off; the exhaust (visual and the push it
    // applies to others) comes out the opposite side of facingDirection.
    var facingDirection: Vector2 = Vector2(0f, -1f)
    var velocity: Vector2 = Vector2(0f, 0f)
    var isThrusting: Boolean = false
    var exhaustPhase: Float = 0f

    private var speedBoostTimer: Float = 0f
    private var invisibilityTimer: Float = 0f

    val isInvisible: Boolean get() = invisibilityTimer > 0f
    val isSpeedBoosted: Boolean get() = speedBoostTimer > 0f

    abstract fun decideDirection(engine: GameEngine, dt: Float): Vector2

    // Bots thrust whenever they have somewhere to go, same as always. The player only
    // thrusts while actually holding sprint - the joystick alone just aims, it never moves
    // them - so this is overridden in PlayerBlob.
    protected open fun wantsToAccelerate(hasHeading: Boolean): Boolean = hasHeading

    open fun update(dt: Float, engine: GameEngine, baseSpeed: Float) {
        if (!alive) return

        val rawDirection = decideDirection(engine, dt)
        val hasHeading = rawDirection.length() > 0.05f
        if (hasHeading) {
            val desiredHeading = rawDirection.normalized()
            facingDirection = steerTowards(facingDirection, desiredHeading, GameConfig.TURN_RATE_RADIANS_PER_SECOND * dt)
        }

        isThrusting = wantsToAccelerate(hasHeading)
        exhaustPhase += dt

        if (isThrusting) {
            val maxSpeed = effectiveSpeed(baseSpeed)
            velocity += facingDirection * (GameConfig.MOVEMENT_ACCELERATION_PER_SECOND * dt)
            val speed = velocity.length()
            if (speed > maxSpeed) {
                velocity *= maxSpeed / speed
            }
        } else {
            // No thrust doesn't mean no motion - existing momentum bleeds off gradually
            // instead of stopping the instant the button is released.
            velocity *= exp(-GameConfig.MOVEMENT_DRAG_PER_SECOND * dt)
        }

        position += velocity * dt
        clampToWorld()

        if (speedBoostTimer > 0f) speedBoostTimer = max(0f, speedBoostTimer - dt)
        if (invisibilityTimer > 0f) invisibilityTimer = max(0f, invisibilityTimer - dt)
    }

    // Turns current toward desired at a bounded rate instead of snapping - the source of
    // "no abrupt reversals" for facingDirection.
    private fun steerTowards(current: Vector2, desired: Vector2, maxTurnRadians: Float): Vector2 {
        val currentAngle = atan2(current.y, current.x)
        val desiredAngle = atan2(desired.y, desired.x)
        val twoPi = (Math.PI * 2).toFloat()
        var diff = (desiredAngle - currentAngle) % twoPi
        if (diff > Math.PI.toFloat()) diff -= twoPi
        if (diff < -Math.PI.toFloat()) diff += twoPi
        val clamped = diff.coerceIn(-maxTurnRadians, maxTurnRadians)
        val newAngle = currentAngle + clamped
        return Vector2(cos(newAngle), sin(newAngle))
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
