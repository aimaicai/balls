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

        // Sprinting is reserved for genuine emergencies with a comfortable buffer to spend
        // - not routine chasing or wandering - so bots don't burn themselves out before
        // ever getting into a fight.
        val safeToSprint = radius > GameConfig.BOT_SPRINT_MIN_RADIUS

        // An about-to-collide threat always overrides everything else - no amount of
        // courage helps if something is already close enough to absorb you. A carried
        // item is a panic button here: REPEL shoves the threat away, FREEZE locks it in
        // place, either way it's spent to survive rather than saved for later.
        val nearThreat = threat
        if (nearThreat != null && threatDistance < (radius + nearThreat.radius) * 1.5f) {
            if (carriedItem != null) {
                engine.activateCarriedItem(this)
            }
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

        // Proactive: head back in well before actually leaving, since the zone keeps
        // shrinking underneath them - reacting only once already outside meant bots
        // routinely got caught by surprise and paid the much faster out-of-zone deflation
        // rate for it. Not an emergency yet, so no need to spend size sprinting for it.
        if (distanceFromZoneCenter > engine.safeZoneRadius * GameConfig.BOT_ZONE_SAFETY_MARGIN_FRACTION) {
            return Vector2(engine.safeZoneCenterX - position.x, engine.safeZoneCenterY - position.y).normalized()
        }

        // Past this point nothing is a genuine emergency, so save whatever size is left
        // instead of spending it - a bot that's merely peckish or cautious shouldn't
        // sprint itself to death before an opponent ever gets the chance to fight it.
        isBoosting = false

        // Constant deflation means running low is a real survival problem, not just a
        // setback: chase down the nearest growth power-up instead of whatever's merely
        // closest.
        if (radius < baseRadius * GameConfig.BOT_LOW_SIZE_FRACTION) {
            val refill = engine.powerUps
                .filter { it.type == PowerUpType.GROWTH }
                .minByOrNull { position.distanceTo(it.position) }
            if (refill != null && position.distanceTo(refill.position) < visionRadius) {
                return (refill.position - position).normalized()
            }
        }

        if (threat != null) {
            return (position - threat.position).normalized()
        }

        if (prey != null) {
            return (prey.position - position).normalized()
        }

        val nearestPowerUp = engine.powerUps.minByOrNull { position.distanceTo(it.position) }
        if (nearestPowerUp != null && position.distanceTo(nearestPowerUp.position) < visionRadius) {
            return (nearestPowerUp.position - position).normalized()
        }

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
