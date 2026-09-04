package com.hyperionsoftware.balls.game

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class BotBlob(
    id: Int,
    position: Vector2,
    color: Int,
    // Defaults to the original, personality-less behavior exactly (see BotPersonality) -
    // every caller that doesn't pass one (including every existing test) gets the same bot
    // AI as before this existed.
    val personality: BotPersonality = BotPersonality.BALANCED
) : Blob(id, position, GameConfig.BASE_RADIUS, color) {

    private var wanderDirection = randomDirection()
    private var wanderTimer = Random.nextFloat() * 2f + 1f

    override fun decideDirection(engine: GameEngine, dt: Float): Vector2 {
        val finalRound = engine.isFinalRoundActive
        val aliveFraction = engine.aliveCount().toFloat() / engine.initialBlobCount
        // The endgame-alive-count ramp and the player-chosen difficulty slider stack
        // multiplicatively - at the default slider level (1x) this is exactly the same
        // aggression value as before that slider existed.
        val aggression = (1f + (1f - aliveFraction) * GameConfig.BOT_MAX_AGGRESSION_BONUS) *
            engine.botAggressivenessMultiplier
        val visionRadius = (radius * 8f + 200f) * aggression * personality.visionMultiplier

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
        // place, either way it's spent to survive rather than saved for later. HOOK is the
        // one exception - it would pull the very threat that's already on top of them even
        // closer, so it's held back rather than used defensively.
        val nearThreat = threat
        if (nearThreat != null &&
            threatDistance < (radius + nearThreat.radius) * 1.5f * personality.fleeBufferMultiplier
        ) {
            if (carriedItem != null && carriedItem != PowerUpType.HOOK) {
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
        // rate for it. The final round uses its own, tighter margin (see
        // BOT_FINAL_ROUND_ZONE_SAFETY_MARGIN_FRACTION) since its zone never stops shrinking
        // and turning still takes time to catch up (see Blob.steerTowards) - the normal
        // phase's margin alone isn't enough of a lead there. Not an emergency yet either
        // way, so no sprinting: burning size to get back early would just trade one drain
        // for another.
        val zoneMarginFraction = (
            if (finalRound) {
                GameConfig.BOT_FINAL_ROUND_ZONE_SAFETY_MARGIN_FRACTION
            } else {
                GameConfig.BOT_ZONE_SAFETY_MARGIN_FRACTION
            }
            ) * personality.zoneMarginMultiplier
        if (distanceFromZoneCenter > engine.safeZoneRadius * zoneMarginFraction) {
            isBoosting = false
            return Vector2(engine.safeZoneCenterX - position.x, engine.safeZoneCenterY - position.y).normalized()
        }

        // Past this point nothing is a genuine emergency, so save whatever size is left
        // instead of spending it - a bot that's merely peckish or cautious shouldn't
        // sprint itself to death before an opponent ever gets the chance to fight it.
        isBoosting = false

        if (finalRound) {
            // The final round's constant leak never lets up and its zone keeps shrinking
            // forever, so growing bigger or turning sharper (AGILITY_UP, which directly
            // helps them react to that shrinking zone in time) is the actual survival plan
            // there - not just an occasional refill once running low like the normal phase
            // below. Sought right after immediate danger, ahead of chasing prey, since
            // walking up to a free pickup is safer than picking a fight for one.
            val refill = nearestPowerUpWithin(engine, visionRadius) {
                it == PowerUpType.GROWTH || it == PowerUpType.AGILITY_UP
            }
            if (refill != null) {
                return (refill.position - position).normalized()
            }
        } else if (radius < baseRadius * GameConfig.BOT_LOW_SIZE_FRACTION) {
            // Constant deflation means running low is a real survival problem, not just a
            // setback: chase down the nearest growth power-up instead of whatever's merely
            // closest.
            val refill = nearestPowerUpWithin(engine, visionRadius) { it == PowerUpType.GROWTH }
            if (refill != null) {
                return (refill.position - position).normalized()
            }
        }

        if (threat != null) {
            return (position - threat.position).normalized()
        }

        if (prey != null) {
            // A visible chase used to always win outright, even against a power-up sitting
            // right next to the bot the whole time - which is why pickups so often floated
            // unclaimed with several bots around, since one almost always had some prey in
            // sight. A genuinely close pickup breaks off the chase instead, weighted by how
            // much closer it is than the prey (see BotPersonality.pickupDetourThreshold) and
            // boosted further for a permanent stat pickup, since that benefit outlasts the
            // single pickup while prey might still be there after a short detour.
            val nearbyPowerUp = nearestPowerUpWithin(engine, visionRadius) { true }
            if (nearbyPowerUp != null) {
                val pickupDistance = position.distanceTo(nearbyPowerUp.position)
                val detourThreshold = personality.pickupDetourThreshold *
                    (if (isPermanentUpgrade(nearbyPowerUp.type)) PERMANENT_UPGRADE_DETOUR_BONUS else 1f)
                if (pickupDistance < preyDistance * detourThreshold) {
                    return (nearbyPowerUp.position - position).normalized()
                }
            }

            // HOOK is offensive rather than defensive - reel prey in while it's still in
            // reach instead of only ever saving carried items for emergencies.
            if (carriedItem == PowerUpType.HOOK) {
                engine.activateCarriedItem(this)
            }
            return (prey.position - position).normalized()
        }

        val nearestPowerUp = nearestPowerUpWithin(engine, visionRadius) { true }
        if (nearestPowerUp != null) {
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

    private fun isPermanentUpgrade(type: PowerUpType) =
        type == PowerUpType.SPEED_UP || type == PowerUpType.AGILITY_UP || type == PowerUpType.POTENCY_UP

    // The nearest power-up matching predicate, strictly closer than maxDistance (or null if
    // none qualify) - a single manual pass instead of filter+minByOrNull, which computed
    // distanceTo twice per candidate and allocated a throwaway filtered list every call. This
    // runs from up to four different spots in decideDirection, for every bot, every single
    // frame, so keeping it allocation-free matters a lot once there are a lot of bots.
    private fun nearestPowerUpWithin(engine: GameEngine, maxDistance: Float, predicate: (PowerUpType) -> Boolean): PowerUp? {
        var nearest: PowerUp? = null
        var nearestDistance = maxDistance
        for (powerUp in engine.powerUps) {
            if (!predicate(powerUp.type)) continue
            val d = position.distanceTo(powerUp.position)
            if (d < nearestDistance) {
                nearest = powerUp
                nearestDistance = d
            }
        }
        return nearest
    }

    companion object {
        // On top of personality.pickupDetourThreshold, applied only to permanent stat
        // pickups (SPEED_UP/AGILITY_UP/POTENCY_UP) - their benefit outlasts the single
        // pickup, unlike GROWTH or a carried item, so they're worth a bigger detour.
        private const val PERMANENT_UPGRADE_DETOUR_BONUS = 1.5f
    }
}
