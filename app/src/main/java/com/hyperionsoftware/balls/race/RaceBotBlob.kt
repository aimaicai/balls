package com.hyperionsoftware.balls.race

import com.hyperionsoftware.balls.game.PowerUpType
import com.hyperionsoftware.balls.game.Vector2

// Waypoint-following race AI: mostly just chases the next checkpoint at all times - there's
// no shrinking safe zone forcing survivors together like classic mode, so racing forward IS
// the survival strategy - but still reacts to immediate threats/prey/power-ups along the
// way. A race win and an elimination win (see RaceEngine) are both valid, so absorbing a
// rival that's conveniently in the way is always worth it, though never worth detouring far
// for - unlike classic mode's bots, these aren't hunting.
class RaceBotBlob(
    id: Int,
    position: Vector2,
    color: Int
) : RaceBlob(id, position, RaceConfig.BASE_RADIUS, color) {

    override fun decideDirection(engine: RaceEngine, dt: Float): Vector2 {
        val visionRadius = RaceConfig.BOT_VISION_RADIUS

        var threat: RaceBlob? = null
        var threatDistance = Float.MAX_VALUE
        var prey: RaceBlob? = null
        var preyDistance = Float.MAX_VALUE

        for (other in engine.blobs) {
            if (other === this || !other.alive || other.isInvisible) continue
            val distance = position.distanceTo(other.position)
            if (distance > visionRadius) continue
            if (other.radius / radius >= RaceConfig.ABSORB_RATIO && distance < threatDistance) {
                threat = other
                threatDistance = distance
            } else if (radius / other.radius >= RaceConfig.ABSORB_RATIO && distance < preyDistance) {
                prey = other
                preyDistance = distance
            }
        }

        val safeToSprint = radius > RaceConfig.BOT_SPRINT_MIN_RADIUS

        // An about-to-collide threat overrides everything else, same panic logic as classic
        // mode's bots.
        val nearThreat = threat
        if (nearThreat != null && threatDistance < (radius + nearThreat.radius) * 1.5f) {
            if (carriedItem != null && carriedItem != PowerUpType.HOOK) {
                engine.activateCarriedItem(this)
            }
            isBoosting = safeToSprint
            return (position - nearThreat.position).normalized()
        }

        // Off-track by more than a comfortable margin: heading back toward the next
        // checkpoint (which is always on the track by definition) wins over anything else,
        // since the off-track deflation is punishing.
        if (engine.track.distanceOffTrack(position) > RaceConfig.OFF_TRACK_MARGIN * 0.5f) {
            isBoosting = false
            return towardNextCheckpoint(engine)
        }

        isBoosting = false

        // A genuinely close prey right on the way is worth a quick grab, and HOOK is worth
        // using on it - but never worth actually detouring far for, unlike classic mode.
        if (prey != null && preyDistance < radius * 2.5f) {
            if (carriedItem == PowerUpType.HOOK) engine.activateCarriedItem(this)
            return (prey.position - position).normalized()
        }

        val nearbyPowerUp = engine.powerUps.minByOrNull { position.distanceTo(it.position) }
        if (nearbyPowerUp != null && position.distanceTo(nearbyPowerUp.position) < radius * 4f) {
            return (nearbyPowerUp.position - position).normalized()
        }

        return towardNextCheckpoint(engine)
    }

    private fun towardNextCheckpoint(engine: RaceEngine): Vector2 {
        val target = engine.track.checkpoints[nextCheckpointIndex]
        return (target - position).normalized()
    }
}
