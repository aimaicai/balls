package com.hyperionsoftware.balls.race

import com.hyperionsoftware.balls.game.BotPersonality
import com.hyperionsoftware.balls.game.PowerUp
import com.hyperionsoftware.balls.game.PowerUpType
import com.hyperionsoftware.balls.game.Vector2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class RaceEndReason { FINISH_LINE, ELIMINATION }

interface RaceListener {
    fun onVibrate()
    fun onAbsorb(x: Float, y: Float, sizeGain: Int, byPlayer: Boolean, absorberId: Int, victimId: Int)
    fun onPowerUpCollected(x: Float, y: Float, type: PowerUpType, byPlayer: Boolean)
    fun onActiveItemUsed(
        x: Float,
        y: Float,
        type: PowerUpType,
        byPlayer: Boolean,
        sourceRadius: Float,
        sourcePotencyMultiplier: Float
    )
    fun onDeflateDeath(x: Float, y: Float, wasPlayer: Boolean)
    fun onLapCompleted(byPlayer: Boolean, lapsCompleted: Int, totalLaps: Int)
    fun onRaceOver(
        playerWon: Boolean,
        reason: RaceEndReason,
        finalRadius: Float,
        lapsCompleted: Int,
        elapsedSeconds: Float
    )
}

// Grand Prix mode's engine - a self-contained sibling to game.GameEngine rather than a reuse
// of it (see RaceBlob), since GameEngine's decideDirection/update are hard-typed to itself and
// its whole model (a shrinking safe zone, a final round) doesn't apply to a lap race at all.
// Win conditions are the two the user asked for, whichever comes first: complete `laps` laps
// before anyone else (RaceEndReason.FINISH_LINE), or be the last blob still alive
// (RaceEndReason.ELIMINATION) - absorbing rivals along the way works toward the second even
// while racing toward the first.
class RaceEngine(
    botCount: Int,
    val track: RaceTrack,
    val totalLaps: Int,
    playerColor: Int = 0xFF4FC3F7.toInt(),
    private val listener: RaceListener
) {
    // Every racer starts already facing the track's own forward direction (checkpoint 0 to
    // checkpoint 1) instead of RaceBlob's fixed default "up" - otherwise the opening seconds
    // of every race showed a grid of balloons facing a direction that had nothing to do with
    // the circuit, only turning to match once steering (or chasing the next checkpoint)
    // kicked in a moment later.
    private val startForward: Vector2 = (track.checkpoints[1] - track.checkpoints[0]).normalized()

    val player = RacePlayerBlob(
        id = 0,
        position = startGridPosition(0),
        color = playerColor
    ).also {
        it.facingDirection = startForward
        it.trackArcPosition = track.closestArcLength(it.position)
    }

    val blobs: MutableList<RaceBlob> = mutableListOf(player)
    val powerUps: MutableList<PowerUp> = mutableListOf()

    var matchElapsed: Float = 0f
        private set
    private var nextPowerUpSpawnIn: Float = randomSpawnDelay()
    private var raceOver = false

    init {
        val palette = BotPersonality.PALETTE
        repeat(botCount) { index ->
            val color = palette[index % palette.size].first
            val bot = RaceBotBlob(id = index + 1, position = startGridPosition(index + 1), color = color)
            bot.facingDirection = startForward
            bot.trackArcPosition = track.closestArcLength(bot.position)
            bot.radius = RaceConfig.BASE_RADIUS * (
                RaceConfig.BOT_START_SIZE_MIN_FACTOR +
                    Random.nextFloat() * (RaceConfig.BOT_START_SIZE_MAX_FACTOR - RaceConfig.BOT_START_SIZE_MIN_FACTOR)
                )
            blobs.add(bot)
        }
        spawnInitialPowerUps()
    }

    // Lines the starting grid up behind the start/finish line, staggered two-wide across the
    // track so nobody starts overlapping - spacing is generous enough relative to
    // MIN_SPAWN_SEPARATION that, unlike classic mode's open-arena spawn, no rejection sampling
    // is needed here.
    private fun startGridPosition(index: Int): Vector2 {
        val start = track.checkpoints[0]
        val perpendicular = Vector2(-startForward.y, startForward.x)
        val row = index / 2
        val lateralSide = if (index % 2 == 0) -1f else 1f
        val lateral = lateralSide * RaceConfig.MIN_SPAWN_SEPARATION * 0.6f
        val backward = (row + 1) * RaceConfig.MIN_SPAWN_SEPARATION * 0.9f
        return Vector2(
            start.x - startForward.x * backward + perpendicular.x * lateral,
            start.y - startForward.y * backward + perpendicular.y * lateral
        )
    }

    private fun spawnInitialPowerUps() {
        val initialCount = (RaceConfig.POWERUP_MAX_COUNT / 2).coerceAtLeast(1)
        repeat(initialCount) { spawnPowerUp() }
    }

    fun update(dt: Float) {
        if (raceOver) return
        matchElapsed += dt

        // Snapshotted before anyone moves this tick - updateRaceProgress needs to know how
        // far each blob actually, physically travelled, to bound how much lap progress a
        // shortcut across the infield can ever earn (see its own comment).
        val positionsBeforeMove = blobs.associate { it.id to Vector2(it.position.x, it.position.y) }

        for (blob in blobs) {
            val baseSpeed = if (blob is RacePlayerBlob) RaceConfig.PLAYER_BASE_SPEED else RaceConfig.BOT_BASE_SPEED
            blob.update(dt, this, baseSpeed)
        }

        applyBoostEffects(dt)
        applyAmbientDeflation(dt)
        resolveCollisions()
        updateRaceProgress(positionsBeforeMove)
        updatePowerUps(dt)
        checkRaceOver()
    }

    private fun applyBoostEffects(dt: Float) {
        for (blob in blobs) {
            if (!blob.alive || !blob.isBoosting) continue
            val died = blob.applyBoostDrain(dt)
            if (died) {
                listener.onDeflateDeath(blob.position.x, blob.position.y, blob === player)
            }
        }
    }

    // Same principle as classic mode's safe zone, just on-track/off-track instead of in/out
    // of a shrinking circle: slower leak on the track surface, faster off it (within
    // OFF_TRACK_MARGIN of the edge still counts as on-track) - a moment spent cutting a
    // corner costs size, not the match outright.
    private fun applyAmbientDeflation(dt: Float) {
        for (blob in blobs) {
            if (!blob.alive) continue
            val onTrack = track.distanceOffTrack(blob.position) <= RaceConfig.OFF_TRACK_MARGIN
            val died = blob.applyAmbientDeflation(onTrack, dt)
            if (died) {
                listener.onDeflateDeath(blob.position.x, blob.position.y, blob === player)
            }
        }
    }

    fun aliveCount(): Int = blobs.count { it.alive }

    private fun resolveCollisions() {
        for (i in blobs.indices) {
            val a = blobs[i]
            if (!a.alive) continue
            for (j in i + 1 until blobs.size) {
                val b = blobs[j]
                if (!b.alive) continue
                handlePair(a, b)
            }
        }

        for (blob in blobs) {
            if (!blob.alive) continue
            for (powerUp in powerUps) {
                if (powerUp.collected) continue
                if (blob.position.distanceTo(powerUp.position) < blob.radius + powerUp.radius) {
                    blob.applyPowerUp(powerUp.type)
                    powerUp.collected = true
                    listener.onPowerUpCollected(powerUp.position.x, powerUp.position.y, powerUp.type, blob === player)
                }
            }
        }
        powerUps.removeAll { it.collected }
    }

    private fun handlePair(a: RaceBlob, b: RaceBlob) {
        if (!a.alive || !b.alive) return
        val distance = a.position.distanceTo(b.position)
        if (distance >= a.radius + b.radius) return

        val bigger = if (a.radius >= b.radius) a else b
        val smaller = if (a.radius >= b.radius) b else a
        val ratio = bigger.radius / smaller.radius

        if (ratio >= RaceConfig.ABSORB_RATIO && !bigger.isFrozen && !smaller.isShielded) {
            val radiusBefore = bigger.radius
            val x = smaller.position.x
            val y = smaller.position.y
            bigger.absorb(smaller)
            listener.onAbsorb(x, y, (bigger.radius - radiusBefore).toInt(), bigger === player, bigger.id, smaller.id)
        } else {
            bounce(a, b, distance)
            if (a === player || b === player) {
                listener.onVibrate()
            }
        }
    }

    private fun bounce(a: RaceBlob, b: RaceBlob, distance: Float) {
        val normal = if (distance < 0.001f) Vector2(1f, 0f) else (a.position - b.position).normalized()
        val overlap = (a.radius + b.radius) - distance
        val push = overlap / 2f + 1f
        a.position += normal * push
        b.position += normal * (-push)
        a.clampToWorld()
        b.clampToWorld()
    }

    // Free-roaming lap progress: no waypoint has to be touched, in order or otherwise - a
    // blob's lapDistanceTraveled simply tracks how far it's covered along the track's own
    // path (see RaceTrack.closestArcLength), and a lap completes once that reaches
    // totalLength. The only guard against cutting across the infield to skip ahead is
    // bounding how much of a tick's raw arc-length change can be credited to how far the
    // blob actually, physically moved that same tick (see RaceConfig.ARC_PROGRESS_SLACK_
    // FACTOR) - a real shortcut only ever earns what it geometrically saved, never a free
    // jump just because the nearest point on the track's path happens to sit far along it.
    private fun updateRaceProgress(positionsBeforeMove: Map<Int, Vector2>) {
        val totalLength = track.totalLength
        for (blob in blobs) {
            if (!blob.alive) continue
            val previousPosition = positionsBeforeMove.getValue(blob.id)
            val movedDistance = blob.position.distanceTo(previousPosition)

            val currentArc = track.closestArcLength(blob.position)
            var delta = currentArc - blob.trackArcPosition
            if (delta > totalLength / 2f) delta -= totalLength
            else if (delta < -totalLength / 2f) delta += totalLength

            val maxCredit = movedDistance * RaceConfig.ARC_PROGRESS_SLACK_FACTOR
            delta = delta.coerceIn(-maxCredit, maxCredit)

            blob.trackArcPosition = currentArc
            blob.lapDistanceTraveled = (blob.lapDistanceTraveled + delta).coerceAtLeast(0f)

            val newLapsCompleted = (blob.lapDistanceTraveled / totalLength).toInt()
            if (newLapsCompleted > blob.lapsCompleted) {
                blob.lapsCompleted = newLapsCompleted
                listener.onLapCompleted(blob === player, blob.lapsCompleted, totalLaps)
                if (blob.lapsCompleted >= totalLaps) {
                    finishRace(blob)
                    return
                }
            }
        }
    }

    private fun finishRace(winner: RaceBlob) {
        raceOver = true
        listener.onRaceOver(
            winner === player,
            RaceEndReason.FINISH_LINE,
            winner.radius,
            winner.lapsCompleted,
            matchElapsed
        )
    }

    fun activateCarriedItem(blob: RaceBlob) {
        if (!blob.alive) return
        val item = blob.consumeCarriedItem() ?: return
        when (item) {
            PowerUpType.REPEL -> applyRepelBlast(blob)
            PowerUpType.FREEZE -> applyFreezeBlast(blob)
            PowerUpType.HOOK -> applyHookPull(blob)
            PowerUpType.SPEED -> blob.activateSpeedBoost()
            PowerUpType.INVISIBILITY -> blob.activateInvisibility()
            else -> Unit
        }
        listener.onActiveItemUsed(
            blob.position.x, blob.position.y, item, blob === player, blob.radius, blob.permanentPotencyMultiplier
        )
    }

    private fun reachRangeFromCenter(source: RaceBlob, multiplier: Float): Float =
        source.radius + source.baseRadius * multiplier

    private fun applyRepelBlast(source: RaceBlob) {
        val potency = source.permanentPotencyMultiplier
        val range = reachRangeFromCenter(source, RaceConfig.REPEL_RANGE_MULTIPLIER * potency)
        for (target in blobs) {
            if (target === source || !target.alive) continue
            val offset = target.position - source.position
            val distance = offset.length()
            val effectiveRange = range + target.radius
            if (distance > effectiveRange || distance < 0.01f) continue
            val direction = offset * (1f / distance)
            val falloff = 1f - distance / effectiveRange
            val strength = RaceConfig.REPEL_FORCE * potency * (0.4f + 0.6f * falloff)
            target.position += direction * strength
            target.clampToWorld()
        }
    }

    private fun applyFreezeBlast(source: RaceBlob) {
        val potency = source.permanentPotencyMultiplier
        val range = reachRangeFromCenter(source, RaceConfig.FREEZE_RANGE_MULTIPLIER * potency)
        for (target in blobs) {
            if (target === source || !target.alive) continue
            if (target.position.distanceTo(source.position) > range + target.radius) continue
            target.applyFreeze(RaceConfig.FREEZE_DURATION_SECONDS * potency)
        }
    }

    private fun applyHookPull(source: RaceBlob) {
        val potency = source.permanentPotencyMultiplier
        val range = reachRangeFromCenter(source, RaceConfig.HOOK_RANGE_MULTIPLIER * potency)
        val target = blobs
            .filter { it !== source && it.alive }
            .minByOrNull { it.position.distanceTo(source.position) }
            ?: return
        val offset = source.position - target.position
        val distance = offset.length()
        if (distance > range + target.radius || distance < 0.01f) return
        val direction = offset * (1f / distance)
        val restingGap = source.radius + target.radius
        val pull = (distance - restingGap).coerceIn(0f, RaceConfig.HOOK_FORCE * potency)
        target.position += direction * pull
        target.clampToWorld()
    }

    private fun updatePowerUps(dt: Float) {
        nextPowerUpSpawnIn -= dt
        if (nextPowerUpSpawnIn <= 0f && powerUps.size < RaceConfig.POWERUP_MAX_COUNT) {
            spawnPowerUp()
            nextPowerUpSpawnIn = randomSpawnDelay()
        }
    }

    // Power-ups spawn scattered along the track's own corridor (a random checkpoint segment,
    // offset perpendicular to it within halfWidth) rather than anywhere in the world, so
    // they're always somewhere a racer will actually pass, never off in unused space.
    private fun spawnPowerUp() {
        val segmentIndex = Random.nextInt(track.checkpoints.size)
        val a = track.checkpoints[segmentIndex]
        val b = track.checkpoints[(segmentIndex + 1) % track.checkpoints.size]
        val t = Random.nextFloat()
        val along = Vector2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        val direction = (b - a)
        val perpendicular = if (direction.length() < 0.01f) Vector2(1f, 0f) else {
            val normalized = direction.normalized()
            Vector2(-normalized.y, normalized.x)
        }
        val offset = (Random.nextFloat() * 2f - 1f) * track.halfWidth * 0.7f
        val position = Vector2(along.x + perpendicular.x * offset, along.y + perpendicular.y * offset)
        // SHIELD gets no separate supply-drop ceremony here unlike classic mode - it's just
        // one more entry in the same pool, a simplification left for v1 (see RaceConfig).
        powerUps.add(PowerUp(PowerUpType.values().random(), position))
    }

    private fun randomSpawnDelay(): Float =
        RaceConfig.POWERUP_SPAWN_INTERVAL_SECONDS * (0.6f + Random.nextFloat() * 0.8f)

    private fun checkRaceOver() {
        if (raceOver) return
        if (!player.alive) {
            raceOver = true
            listener.onRaceOver(false, RaceEndReason.ELIMINATION, player.radius, player.lapsCompleted, matchElapsed)
            return
        }
        if (aliveCount() <= 1) {
            raceOver = true
            val winnerIsPlayer = player.alive
            listener.onRaceOver(
                winnerIsPlayer,
                RaceEndReason.ELIMINATION,
                player.radius,
                player.lapsCompleted,
                matchElapsed
            )
        }
    }
}
