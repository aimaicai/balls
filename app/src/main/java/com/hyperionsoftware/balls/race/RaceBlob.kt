package com.hyperionsoftware.balls.race

import com.hyperionsoftware.balls.game.PowerUpType
import com.hyperionsoftware.balls.game.Vector2
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Movement/absorb/power-up physics for Grand Prix mode - ported from game.Blob rather than
// reused directly, since GameEngine-flavored decideDirection (safe zone, final round, alive-
// fraction aggression) doesn't apply to racing at all and Blob.decideDirection/update are
// hard-typed to GameEngine specifically. Deliberately its own small hierarchy so nothing
// about this experimental mode can affect the tested classic engine, or vice versa.
abstract class RaceBlob(
    val id: Int,
    var position: Vector2,
    val baseRadius: Float,
    val color: Int
) {
    var radius: Float = baseRadius
    var alive: Boolean = true

    var isBoosting: Boolean = false
    private var boostHoldSeconds: Float = 0f

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
    val isBoostAtMaxPower: Boolean get() = isBoosting && boostHoldSeconds >= RaceConfig.BOOST_RAMP_UP_SECONDS

    var carriedItem: PowerUpType? = null
        private set

    private var permanentSpeedTier = 0
    private var permanentTurnRateTier = 0
    private var permanentPotencyTier = 0
    var permanentSpeedMultiplier: Float = 1f
        private set
    var permanentTurnRateMultiplier: Float = 1f
        private set
    var permanentPotencyMultiplier: Float = 1f
        private set

    // Race progress: which checkpoint this blob needs next (index into
    // RaceTrack.checkpoints), and how many full laps it's completed so far - see
    // RaceEngine.updateRaceProgress. Every blob starts at checkpoint 0 (the start/finish
    // line), so the next one it needs is index 1.
    var nextCheckpointIndex: Int = 1
    var lapsCompleted: Int = 0

    abstract fun decideDirection(engine: RaceEngine, dt: Float): Vector2

    open fun update(dt: Float, engine: RaceEngine, baseSpeed: Float) {
        if (!alive) return

        if (isFrozen) {
            frozenTimer = max(0f, frozenTimer - dt)
            isThrusting = false
        } else {
            val rawDirection = decideDirection(engine, dt)
            val hasHeading = rawDirection.length() > 0.05f

            if (hasHeading) {
                val desiredHeading = rawDirection.normalized()
                val turnRate = RaceConfig.TURN_RATE_RADIANS_PER_SECOND * permanentTurnRateMultiplier
                facingDirection = steerTowards(facingDirection, desiredHeading, turnRate * dt)
            }

            isThrusting = true
            boostHoldSeconds = if (isBoosting) boostHoldSeconds + dt else 0f

            val speed = effectiveSpeed(baseSpeed)
            val step = speed * dt
            position.x += facingDirection.x * step
            position.y += facingDirection.y * step
            clampToWorld()
        }

        exhaustPhase += dt
        if (speedBoostTimer > 0f) speedBoostTimer = max(0f, speedBoostTimer - dt)
        if (invisibilityTimer > 0f) invisibilityTimer = max(0f, invisibilityTimer - dt)
        if (shieldTimer > 0f) shieldTimer = max(0f, shieldTimer - dt)
    }

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
        position.x = position.x.coerceIn(radius, RaceConfig.WORLD_WIDTH - radius)
        position.y = position.y.coerceIn(radius, RaceConfig.WORLD_HEIGHT - radius)
    }

    fun absorb(other: RaceBlob) {
        val newArea = areaOf(radius) + areaOf(other.radius)
        radius = min(RaceConfig.MAX_RADIUS, sqrt(newArea / Math.PI.toFloat()))
        other.alive = false
    }

    // Same principle as classic mode's safe zone: slower leak on-track, faster off it -
    // shielded pauses it entirely. Returns true if this was lethal.
    fun applyAmbientDeflation(onTrack: Boolean, dt: Float): Boolean {
        if (isShielded) return false
        val rate = if (onTrack) RaceConfig.ON_TRACK_DEFLATE_RATE_PER_SECOND else RaceConfig.OFF_TRACK_DEFLATE_RATE_PER_SECOND
        radius -= radius * rate * dt
        if (radius < RaceConfig.ZONE_DEATH_RADIUS) {
            alive = false
            return true
        }
        return false
    }

    fun applyBoostDrain(dt: Float): Boolean {
        radius -= radius * RaceConfig.BOOST_DRAIN_RATE_PER_SECOND * dt
        if (radius < RaceConfig.ZONE_DEATH_RADIUS) {
            alive = false
            return true
        }
        return false
    }

    fun applyPowerUp(type: PowerUpType, growthRadiusBonus: Float = RaceConfig.POWERUP_GROWTH_RADIUS_BONUS) {
        when (type) {
            PowerUpType.SHIELD -> shieldTimer = RaceConfig.POWERUP_SHIELD_DURATION
            PowerUpType.GROWTH -> radius = min(RaceConfig.MAX_RADIUS, radius + growthRadiusBonus)
            PowerUpType.SPEED_UP -> increasePermanentSpeed()
            PowerUpType.AGILITY_UP -> increasePermanentAgility()
            PowerUpType.POTENCY_UP -> increasePermanentPotency()
            PowerUpType.SPEED, PowerUpType.INVISIBILITY, PowerUpType.REPEL,
            PowerUpType.FREEZE, PowerUpType.HOOK -> pickUpCarriedItem(type)
        }
    }

    fun pickUpCarriedItem(type: PowerUpType) {
        carriedItem = type
    }

    fun consumeCarriedItem(): PowerUpType? {
        val item = carriedItem
        carriedItem = null
        return item
    }

    fun activateSpeedBoost() {
        speedBoostTimer = RaceConfig.POWERUP_SPEED_DURATION
    }

    fun activateInvisibility() {
        invisibilityTimer = RaceConfig.POWERUP_INVISIBILITY_DURATION
    }

    fun applyFreeze(duration: Float) {
        frozenTimer = duration
    }

    private fun increasePermanentSpeed() {
        if (permanentSpeedTier >= RaceConfig.PERMANENT_STAT_TIER_COUNT) return
        permanentSpeedTier++
        permanentSpeedMultiplier = 1f +
            (RaceConfig.PERMANENT_SPEED_MAX_MULTIPLIER - 1f) * permanentSpeedTier / RaceConfig.PERMANENT_STAT_TIER_COUNT
    }

    private fun increasePermanentAgility() {
        if (permanentTurnRateTier >= RaceConfig.PERMANENT_STAT_TIER_COUNT) return
        permanentTurnRateTier++
        permanentTurnRateMultiplier = 1f +
            (RaceConfig.PERMANENT_TURN_RATE_MAX_MULTIPLIER - 1f) * permanentTurnRateTier / RaceConfig.PERMANENT_STAT_TIER_COUNT
    }

    private fun increasePermanentPotency() {
        if (permanentPotencyTier >= RaceConfig.PERMANENT_STAT_TIER_COUNT) return
        permanentPotencyTier++
        permanentPotencyMultiplier = 1f +
            (RaceConfig.PERMANENT_POTENCY_MAX_MULTIPLIER - 1f) * permanentPotencyTier / RaceConfig.PERMANENT_STAT_TIER_COUNT
    }

    private fun effectiveSpeed(baseSpeed: Float): Float {
        val sizeFactor = sqrt(baseRadius / radius)
        val powerUpBoost = if (isSpeedBoosted) RaceConfig.POWERUP_SPEED_MULTIPLIER else 1f
        val dashBoost = if (isBoosting && radius > RaceConfig.ZONE_DEATH_RADIUS) {
            val rampProgress = (boostHoldSeconds / RaceConfig.BOOST_RAMP_UP_SECONDS).coerceIn(0f, 1f)
            RaceConfig.BOOST_SPEED_MULTIPLIER +
                (RaceConfig.BOOST_MAX_SPEED_MULTIPLIER - RaceConfig.BOOST_SPEED_MULTIPLIER) * rampProgress
        } else {
            1f
        }
        return baseSpeed * sizeFactor * powerUpBoost * dashBoost * permanentSpeedMultiplier
    }

    private fun areaOf(r: Float): Float = Math.PI.toFloat() * r * r
}
