package com.hyperionsoftware.balls.game

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class BotBlob(
    id: Int,
    position: Vector2,
    color: Int
) : Blob(id, position, GameConfig.BASE_RADIUS, color) {

    private var wanderDirection = randomDirection()
    private var wanderTimer = Random.nextFloat() * 2f + 1f

    override fun decideDirection(engine: GameEngine, dt: Float): Vector2 {
        val aliveFraction = engine.aliveCount().toFloat() / engine.initialBlobCount
        val aggression = 1f + (1f - aliveFraction) * GameConfig.BOT_MAX_AGGRESSION_BONUS
        val visionRadius = (radius * 8f + 200f) * aggression

        var threat: Blob? = null
        var threatDistance = Float.MAX_VALUE
        var prey: Blob? = null
        var preyDistance = Float.MAX_VALUE

        for (other in engine.blobs) {
            if (other === this || !other.alive || other.isInvisible) continue
            val distance = position.distanceTo(other.position)
            if (distance > visionRadius) continue

            if (other.radius / radius >= GameConfig.ABSORB_RATIO && distance < threatDistance) {
                threat = other
                threatDistance = distance
            } else if (radius / other.radius >= GameConfig.ABSORB_RATIO && distance < preyDistance) {
                prey = other
                preyDistance = distance
            }
        }

        // Ambient deflation never stops, in or out of the zone, so "safe to sprint" is now
        // about having a real margin above death to spend - not about being above
        // baseRadius, since drifting below it is the normal state, not an emergency.
        val safeToSprint = radius > GameConfig.BOT_MIN_SPRINT_RADIUS

        // An about-to-collide threat always overrides everything else - no amount of
        // courage helps if something is already close enough to absorb you.
        val nearThreat = threat
        if (nearThreat != null && threatDistance < (radius + nearThreat.radius) * 1.5f) {
            isBoosting = safeToSprint
            return (position - nearThreat.position).normalized()
        }

        // Braver than a plain "flee any visible threat": staying outside the safe zone is
        // a slower but more certain death than a distant predator, so getting back to
        // safety wins over merely fleeing something that isn't already on top of them.
        val distanceFromZoneCenter = hypot(position.x - engine.safeZoneCenterX, position.y - engine.safeZoneCenterY)
        if (distanceFromZoneCenter > engine.safeZoneRadius) {
            isBoosting = safeToSprint
            return Vector2(engine.safeZoneCenterX - position.x, engine.safeZoneCenterY - position.y).normalized()
        }

        // Constant deflation means running low is a real survival problem, not just a
        // setback: chase down the nearest growth power-up instead of whatever's merely
        // closest, sprinting there since every second spent low is size lost for good.
        if (radius < baseRadius * GameConfig.BOT_LOW_SIZE_FRACTION) {
            val refill = engine.powerUps
                .filter { it.type == PowerUpType.GROWTH }
                .minByOrNull { position.distanceTo(it.position) }
            if (refill != null && position.distanceTo(refill.position) < visionRadius) {
                isBoosting = safeToSprint
                return (refill.position - position).normalized()
            }
        }

        if (threat != null) {
            isBoosting = safeToSprint
            return (position - threat.position).normalized()
        }

        if (prey != null) {
            // Absorbing prey is the other way to fight the constant leak, so it's worth
            // sprinting to actually catch them instead of just drifting along behind.
            isBoosting = safeToSprint
            return (prey.position - position).normalized()
        }

        val nearestPowerUp = engine.powerUps.minByOrNull { position.distanceTo(it.position) }
        if (nearestPowerUp != null && position.distanceTo(nearestPowerUp.position) < visionRadius) {
            isBoosting = safeToSprint
            return (nearestPowerUp.position - position).normalized()
        }

        isBoosting = false
        wanderTimer -= dt
        if (wanderTimer <= 0f) {
            wanderDirection = randomDirection()
            wanderTimer = Random.nextFloat() * 2f + 1.5f
        }
        return wanderDirection
    }

    private fun randomDirection(): Vector2 {
        val angle = Random.nextFloat() * (Math.PI * 2).toFloat()
        return Vector2(cos(angle), sin(angle))
    }
}
