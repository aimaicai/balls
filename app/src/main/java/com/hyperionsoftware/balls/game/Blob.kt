package com.hyperionsoftware.balls.game

import kotlin.math.atan2
import kotlin.math.cos
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
    var isBoosting: Boolean = false

    // The joystick/AI drives movement directly (joystick magnitude scales speed
    // immediately), but facingDirection still turns gradually toward wherever it's aimed
    // (see steerTowards) instead of snapping straight to the opposite heading, and
    // movement follows that smoothed direction. The exhaust (visual and the push it
    // applies to others) comes out the opposite side of facingDirection.
    var facingDirection: Vector2 = Vector2(0f, -1f)
    var isThrusting: Boolean = false
    var exhaustPhase: Float = 0f

    private var speedBoostTimer: Float = 0f
    private var invisibilityTimer: Float = 0f
    private var shieldTimer: Float = 0f
    private var frozenTimer: Float = 0f

    val isInvisible: Boolean get() = invisibilityTimer > 0f
    val isSpeedBoosted: Boolean get() = speedBoostTimer > 0f
    val isShielded: Boolean get() = shieldTimer > 0f
    val isFrozen: Boolean get() = frozenTimer > 0f

    // A single carried/active item slot - picked up like a regular power-up but stored
    // instead of applied immediately, spent later via a dedicated button. Picking up a new
    // one while already holding one replaces it, whatever the two types are.
    var carriedItem: PowerUpType? = null
        private set

    // Permanent stat multipliers from SPEED_UP/AGILITY_UP pickups. Each pickup advances one
    // discrete tier toward its cap (see increasePermanentSpeed/Agility), so a single pickup
    // always moves the HUD by exactly one pip and stacking stays bounded.
    private var permanentSpeedTier = 0
    private var permanentTurnRateTier = 0
    var permanentSpeedMultiplier: Float = 1f
        private set
    var permanentTurnRateMultiplier: Float = 1f
        private set

    abstract fun decideDirection(engine: GameEngine, dt: Float): Vector2

    open fun update(dt: Float, engine: GameEngine, baseSpeed: Float) {
        if (!alive) return

        if (isFrozen) {
            frozenTimer = max(0f, frozenTimer - dt)
            isThrusting = false
        } else {
            // The raw vector's length doubles as desired-speed fraction: bots always return
            // unit-length vectors (full speed), while the player's joystick vector length
            // reflects how far the stick is pushed, giving proportional analog control.
            val rawDirection = decideDirection(engine, dt)
            val magnitude = rawDirection.length().coerceAtMost(1f)
            val hasHeading = magnitude > 0.05f

            if (hasHeading) {
                val desiredHeading = rawDirection.normalized()
                val turnRate = GameConfig.TURN_RATE_RADIANS_PER_SECOND * permanentTurnRateMultiplier
                facingDirection = steerTowards(facingDirection, desiredHeading, turnRate * dt)
            }

            isThrusting = hasHeading

            val speed = effectiveSpeed(baseSpeed)
            val movement = facingDirection * (speed * magnitude * dt)
            position += movement
            clampToWorld()
        }

        exhaustPhase += dt
        if (speedBoostTimer > 0f) speedBoostTimer = max(0f, speedBoostTimer - dt)
        if (invisibilityTimer > 0f) invisibilityTimer = max(0f, invisibilityTimer - dt)
        if (shieldTimer > 0f) shieldTimer = max(0f, shieldTimer - dt)
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
    // outside - so standing still is never truly safe, only "safer" - except while
    // shielded, which pauses this leak entirely. Hitting ZONE_DEATH_RADIUS deflates the
    // balloon for good. Returns true if this was lethal.
    fun applyAmbientDeflation(inSafeZone: Boolean, dt: Float): Boolean {
        if (isShielded) return false
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

    fun applyPowerUp(type: PowerUpType, growthMultiplier: Float = GameConfig.POWERUP_GROWTH_MULTIPLIER) {
        when (type) {
            PowerUpType.SHIELD -> shieldTimer = GameConfig.POWERUP_SHIELD_DURATION
            PowerUpType.GROWTH -> {
                radius = min(GameConfig.MAX_RADIUS, radius * growthMultiplier)
            }
            PowerUpType.SPEED_UP -> increasePermanentSpeed()
            PowerUpType.AGILITY_UP -> increasePermanentAgility()
            PowerUpType.SPEED, PowerUpType.INVISIBILITY, PowerUpType.REPEL, PowerUpType.FREEZE -> pickUpCarriedItem(type)
        }
    }

    fun pickUpCarriedItem(type: PowerUpType) {
        carriedItem = type
    }

    // Spends whatever's carried, if anything, clearing the slot. Returns the spent item so
    // the caller can apply its actual effect.
    fun consumeCarriedItem(): PowerUpType? {
        val item = carriedItem
        carriedItem = null
        return item
    }

    // These two are only ever invoked from GameEngine.activateCarriedItem, once the player
    // (or bot) actually spends a carried SPEED/INVISIBILITY rather than the instant they
    // pick it up - so a duration is never ticking away unused before it's wanted.
    fun activateSpeedBoost() {
        speedBoostTimer = GameConfig.POWERUP_SPEED_DURATION
    }

    fun activateInvisibility() {
        invisibilityTimer = GameConfig.POWERUP_INVISIBILITY_DURATION
    }

    // Applied to targets caught by someone else's FREEZE, not to oneself.
    fun applyFreeze(duration: Float) {
        frozenTimer = duration
    }

    // Each pickup advances one tier out of PERMANENT_STAT_TIER_COUNT, linearly interpolating
    // toward the cap - a flat, predictable step per pickup instead of a diminishing one.
    private fun increasePermanentSpeed() {
        if (permanentSpeedTier >= GameConfig.PERMANENT_STAT_TIER_COUNT) return
        permanentSpeedTier++
        permanentSpeedMultiplier = 1f +
            (GameConfig.PERMANENT_SPEED_MAX_MULTIPLIER - 1f) * permanentSpeedTier / GameConfig.PERMANENT_STAT_TIER_COUNT
    }

    private fun increasePermanentAgility() {
        if (permanentTurnRateTier >= GameConfig.PERMANENT_STAT_TIER_COUNT) return
        permanentTurnRateTier++
        permanentTurnRateMultiplier = 1f +
            (GameConfig.PERMANENT_TURN_RATE_MAX_MULTIPLIER - 1f) * permanentTurnRateTier / GameConfig.PERMANENT_STAT_TIER_COUNT
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
        return baseSpeed * sizeFactor * powerUpBoost * dashBoost * permanentSpeedMultiplier
    }

    private fun areaOf(r: Float): Float = Math.PI.toFloat() * r * r
}
