package com.hyperionsoftware.balls.game

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

interface GameListener {
    fun onVibrate()
    fun onAbsorb(x: Float, y: Float, sizeGain: Int, byPlayer: Boolean, absorberId: Int, victimId: Int)
    fun onPowerUpCollected(x: Float, y: Float, type: PowerUpType, byPlayer: Boolean)
    fun onActiveItemUsed(x: Float, y: Float, type: PowerUpType, byPlayer: Boolean)
    fun onZoneDeath(x: Float, y: Float, wasPlayer: Boolean)
    fun onDeflateDeath(x: Float, y: Float, wasPlayer: Boolean)
    fun onGameOver(playerWon: Boolean, finalRadius: Float, playersRemaining: Int, opponentsAbsorbed: Int, elapsedSeconds: Float)
}

class GameEngine(
    botCount: Int,
    powerUpFrequencyLevel: Int,
    arenaSize: GameConfig.ArenaSize = GameConfig.ArenaSize.NORMAL,
    // Lets the main menu drop straight into the final-round setup (all blobs alive, circled
    // around a single centered power-up) with whatever bot count is chosen, instead of
    // having to play a full match down to a handful of survivors just to test that stage.
    private val skipToFinalRound: Boolean = false,
    private val listener: GameListener
) {
    // Must run before any property below reads WORLD_WIDTH/HEIGHT or
    // SAFE_ZONE_INITIAL_RADIUS, since Kotlin runs init blocks and property initializers
    // in declaration order.
    init {
        GameConfig.applyArenaSize(arenaSize)
    }

    private val powerUpBaseMaxCount = GameConfig.POWERUP_MAX_COUNT_PER_LEVEL * powerUpFrequencyLevel
    private val powerUpFrequency = powerUpFrequencyLevel.toFloat()

    // GROWTH is weighted heavier than the other types (see GameConfig) since size
    // constantly drains away on its own now. SPEED/INVISIBILITY/REPEL/FREEZE/HOOK are all
    // carried items rather than instant effects, but spawn from this same pool; SPEED_UP
    // and AGILITY_UP are instant, permanent stat increases.
    private val weightedPowerUpTypes: List<PowerUpType> = buildList {
        repeat(GameConfig.POWERUP_GROWTH_WEIGHT) { add(PowerUpType.GROWTH) }
        repeat(GameConfig.POWERUP_SPEED_WEIGHT) { add(PowerUpType.SPEED) }
        repeat(GameConfig.POWERUP_INVISIBILITY_WEIGHT) { add(PowerUpType.INVISIBILITY) }
        repeat(GameConfig.POWERUP_REPEL_WEIGHT) { add(PowerUpType.REPEL) }
        repeat(GameConfig.POWERUP_FREEZE_WEIGHT) { add(PowerUpType.FREEZE) }
        repeat(GameConfig.POWERUP_HOOK_WEIGHT) { add(PowerUpType.HOOK) }
        repeat(GameConfig.POWERUP_SPEED_UP_WEIGHT) { add(PowerUpType.SPEED_UP) }
        repeat(GameConfig.POWERUP_AGILITY_UP_WEIGHT) { add(PowerUpType.AGILITY_UP) }
    }

    // More power-ups when the safe zone is large, fewer (but never none) as it shrinks,
    // so density roughly tracks the zone's area instead of staying fixed all match.
    private val powerUpMaxCount: Int
        get() {
            val areaFraction = (safeZoneRadius / GameConfig.SAFE_ZONE_INITIAL_RADIUS).let { it * it }
            val factor = GameConfig.POWERUP_MIN_AREA_FACTOR +
                (1f - GameConfig.POWERUP_MIN_AREA_FACTOR) * areaFraction
            return (powerUpBaseMaxCount * factor).toInt().coerceAtLeast(1)
        }

    val player = PlayerBlob(
        id = 0,
        position = Vector2(GameConfig.WORLD_WIDTH / 2f, GameConfig.WORLD_HEIGHT / 2f),
        color = 0xFF4FC3F7.toInt()
    )

    val blobs: MutableList<Blob> = mutableListOf(player)
    val powerUps: MutableList<PowerUp> = mutableListOf()
    val initialBlobCount: Int = botCount + 1

    val safeZoneCenterX = GameConfig.WORLD_WIDTH / 2f
    val safeZoneCenterY = GameConfig.WORLD_HEIGHT / 2f

    private val safeZoneStageDuration =
        GameConfig.SAFE_ZONE_STAGE_HOLD_SECONDS + GameConfig.SAFE_ZONE_STAGE_SHRINK_SECONDS

    // The zone shrinks in SAFE_ZONE_STAGE_COUNT stages rather than one continuous slide:
    // each stage holds at its current radius for a bit (see isZoneHolding, used by the UI
    // to preview the next circle) before actively shrinking to the next, smaller target.
    val safeZoneStageIndex: Int
        get() = (matchElapsed / safeZoneStageDuration).toInt().coerceIn(0, GameConfig.SAFE_ZONE_STAGE_COUNT - 1)

    val isZoneHolding: Boolean
        get() {
            val timeIntoStage = matchElapsed - safeZoneStageIndex * safeZoneStageDuration
            return timeIntoStage < GameConfig.SAFE_ZONE_STAGE_HOLD_SECONDS
        }

    val safeZoneRadius: Float
        get() {
            if (finalRoundTriggered) {
                // Keeps shrinking at the same rate indefinitely instead of holding once it
                // reaches SAFE_ZONE_FINAL_MIN_RADIUS - a standoff at a fixed floor was still
                // a standoff, just a bigger one. A small floor stops it from going degenerate
                // (zero/negative), but by then it's forcing a decisive end either way.
                val shrinkElapsed = matchElapsed - finalRoundTriggeredAt
                val shrinkRate = (GameConfig.SAFE_ZONE_MIN_RADIUS - GameConfig.SAFE_ZONE_FINAL_MIN_RADIUS) /
                    GameConfig.SAFE_ZONE_FINAL_SHRINK_SECONDS
                return (GameConfig.SAFE_ZONE_MIN_RADIUS - shrinkRate * shrinkElapsed)
                    .coerceAtLeast(GameConfig.SAFE_ZONE_ABSOLUTE_MIN_RADIUS)
            }
            val stage = safeZoneStageIndex
            val timeIntoStage = matchElapsed - stage * safeZoneStageDuration
            val startRadius = stageRadius(stage)
            if (timeIntoStage <= GameConfig.SAFE_ZONE_STAGE_HOLD_SECONDS) return startRadius
            val endRadius = stageRadius(stage + 1)
            val shrinkElapsed = timeIntoStage - GameConfig.SAFE_ZONE_STAGE_HOLD_SECONDS
            val shrinkProgress = (shrinkElapsed / GameConfig.SAFE_ZONE_STAGE_SHRINK_SECONDS).coerceIn(0f, 1f)
            return startRadius + (endRadius - startRadius) * shrinkProgress
        }

    // The upcoming target radius, telegraphed as a preview outline while isZoneHolding.
    val nextSafeZoneRadius: Float
        get() = stageRadius(safeZoneStageIndex + 1)

    val safeZoneProgress: Float
        get() = ((GameConfig.SAFE_ZONE_INITIAL_RADIUS - safeZoneRadius) /
            (GameConfig.SAFE_ZONE_INITIAL_RADIUS - GameConfig.SAFE_ZONE_MIN_RADIUS)).coerceIn(0f, 1f)

    private fun stageRadius(stage: Int): Float {
        val fraction = stage.coerceIn(0, GameConfig.SAFE_ZONE_STAGE_COUNT).toFloat() / GameConfig.SAFE_ZONE_STAGE_COUNT
        return GameConfig.SAFE_ZONE_INITIAL_RADIUS -
            fraction * (GameConfig.SAFE_ZONE_INITIAL_RADIUS - GameConfig.SAFE_ZONE_MIN_RADIUS)
    }

    var matchElapsed = 0f
        private set
    private var nextPowerUpSpawnIn: Float = randomSpawnDelay()
    private var nextSupplyDropIn: Float = randomSupplyDropDelay()
    private var gameOver = false
    private var playerAbsorbCount = 0
    private var finalRoundTriggered = false
    private var finalRoundTriggeredAt = 0f

    init {
        val colors = intArrayOf(
            0xFFEF5350.toInt(), 0xFF66BB6A.toInt(), 0xFFAB47BC.toInt(), 0xFFFFA726.toInt(),
            0xFF26C6DA.toInt(), 0xFFEC407A.toInt(), 0xFF9CCC65.toInt(), 0xFF5C6BC0.toInt()
        )
        repeat(botCount) { index ->
            val margin = GameConfig.BASE_RADIUS * 2f
            val position = Vector2(
                Random.nextFloat() * (GameConfig.WORLD_WIDTH - margin * 2f) + margin,
                Random.nextFloat() * (GameConfig.WORLD_HEIGHT - margin * 2f) + margin
            )
            val bot = BotBlob(id = index + 1, position = position, color = colors[index % colors.size])
            // A little starting-size variance breaks the "everyone's identical, nobody can
            // absorb anybody" opening stalemate - the player still starts at a fair
            // baseRadius, only bots get this.
            bot.radius = GameConfig.BASE_RADIUS * (
                GameConfig.BOT_START_SIZE_MIN_FACTOR +
                    Random.nextFloat() * (GameConfig.BOT_START_SIZE_MAX_FACTOR - GameConfig.BOT_START_SIZE_MIN_FACTOR)
                )
            blobs.add(bot)
        }
        if (skipToFinalRound) triggerFinalRound() else spawnInitialPowerUps()
    }

    // Filling in a chunk of the cap immediately, instead of waiting for the usual
    // one-at-a-time timer to trickle them in from zero, so there's actually something to
    // find in the opening minute instead of an empty map.
    private fun spawnInitialPowerUps() {
        val initialCount = (powerUpMaxCount * GameConfig.POWERUP_INITIAL_FILL_FRACTION).toInt()
        repeat(initialCount) { spawnPowerUp() }
    }

    fun update(dt: Float) {
        if (gameOver) return
        matchElapsed += dt

        if (!finalRoundTriggered && safeZoneProgress >= 1f) {
            triggerFinalRound()
        }

        for (blob in blobs) {
            val baseSpeed = if (blob is PlayerBlob) GameConfig.PLAYER_BASE_SPEED else GameConfig.BOT_BASE_SPEED
            blob.update(dt, this, baseSpeed)
        }

        applyThrustEffects(dt)
        applyBoostEffects(dt)
        applyAmbientDeflation(dt)
        resolveCollisions()
        cullStrandedPowerUps()
        updatePowerUps(dt)
        updateSupplyDrop(dt)
        checkGameOver()
    }

    // Every balloon that's actively thrusting blows a cone of air out its back, shoving any
    // other balloon caught in that cone further away - a side effect of moving, not an
    // ability anyone has to activate.
    private fun applyThrustEffects(dt: Float) {
        for (source in blobs) {
            if (!source.alive || !source.isThrusting) continue
            val exhaustDir = source.facingDirection * -1f
            val maxRange = source.radius * GameConfig.THRUST_RANGE_MULTIPLIER

            for (target in blobs) {
                if (target === source || !target.alive) continue
                val offset = target.position - source.position
                val distance = offset.length()
                if (distance < 0.01f || distance > maxRange) continue

                val towardTarget = offset * (1f / distance)
                val alignment = exhaustDir.dot(towardTarget)
                if (alignment <= GameConfig.THRUST_CONE_MIN_ALIGNMENT) continue

                val falloff = 1f - distance / maxRange
                val strength = GameConfig.THRUST_FORCE_PER_SECOND * falloff * alignment * dt
                target.position += towardTarget * strength
                target.clampToWorld()
            }
        }
    }

    // Sprinting drains size everywhere, in or out of the zone, stacking on top of the
    // ambient leak below, and can kill on its own.
    private fun applyBoostEffects(dt: Float) {
        for (blob in blobs) {
            if (!blob.alive || !blob.isBoosting) continue
            val died = blob.applyBoostDrain(dt)
            if (died) {
                listener.onDeflateDeath(blob.position.x, blob.position.y, blob === player)
            }
        }
    }

    // Power-ups left behind outside the shrinking zone are unreachable without taking
    // zone damage to fetch them; drop them so their slot can respawn somewhere the
    // fight is actually happening.
    private fun cullStrandedPowerUps() {
        val radius = safeZoneRadius
        powerUps.removeAll { hypot(it.position.x - safeZoneCenterX, it.position.y - safeZoneCenterY) > radius }
    }

    // Balloons always leak air, everywhere - slower inside the safe zone, faster outside
    // it. A death caught outside the zone is reported distinctly (onZoneDeath) from one
    // that came from simply running dry over time while inside it (onDeflateDeath), even
    // though both end the match for that blob the same way.
    private fun applyAmbientDeflation(dt: Float) {
        val radius = safeZoneRadius
        for (blob in blobs) {
            if (!blob.alive) continue
            val distance = hypot(blob.position.x - safeZoneCenterX, blob.position.y - safeZoneCenterY)
            val inZone = distance <= radius
            val died = blob.applyAmbientDeflation(inZone, dt)
            if (died) {
                if (inZone) {
                    listener.onDeflateDeath(blob.position.x, blob.position.y, blob === player)
                } else {
                    listener.onZoneDeath(blob.position.x, blob.position.y, blob === player)
                }
            }
        }
    }

    // Once the zone finishes shrinking, a pile of max-size blobs just shoving each other
    // around at the same spot isn't fun. Reset the stage instead: every survivor shrinks
    // back to its original small size (so the absorb ratio actually matters again instead
    // of everyone already being tied at MAX_RADIUS), spread evenly around the (now fixed)
    // zone edge already facing the center, with a single power-up dead center pulling
    // everyone together instead of an aimless scrum.
    private fun triggerFinalRound() {
        finalRoundTriggered = true
        finalRoundTriggeredAt = matchElapsed
        val survivors = blobs.filter { it.alive }
        if (survivors.isNotEmpty()) {
            val placementRadius = safeZoneRadius * 0.9f
            survivors.forEachIndexed { index, blob ->
                val angle = (index.toFloat() / survivors.size) * 2f * Math.PI.toFloat()
                blob.radius = blob.baseRadius
                blob.position = Vector2(
                    safeZoneCenterX + cos(angle) * placementRadius,
                    safeZoneCenterY + sin(angle) * placementRadius
                )
                blob.facingDirection = Vector2(-cos(angle), -sin(angle))
                blob.clampToWorld()
            }
        }
        // Only this very first power-up is dead center - every one after it (see
        // updatePowerUps) lands somewhere random in the zone instead.
        powerUps.clear()
        powerUps.add(PowerUp(weightedPowerUpTypes.random(), Vector2(safeZoneCenterX, safeZoneCenterY)))
    }

    // Spends whatever the blob is carrying, if anything, and applies its effect centered on
    // the blob's current position. A no-op if the slot is empty.
    fun activateCarriedItem(blob: Blob) {
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
        listener.onActiveItemUsed(blob.position.x, blob.position.y, item, blob === player)
    }

    private fun applyRepelBlast(source: Blob) {
        // Range comes from baseRadius, not the current (post-GROWTH) radius, so a tiny
        // balloon repels exactly as far as a huge one - it shouldn't be a weaker tool just
        // because its holder happens to be small right now.
        val range = source.baseRadius * GameConfig.REPEL_RANGE_MULTIPLIER
        for (target in blobs) {
            if (target === source || !target.alive) continue
            val offset = target.position - source.position
            val distance = offset.length()
            // The check is a circle-circle overlap (effect radius vs the target's own body),
            // not center-to-center against a bare range - otherwise a huge target right next
            // to the source could still be "out of range" because its center is far away even
            // though its edge is touching.
            val effectiveRange = range + target.radius
            if (distance > effectiveRange || distance < 0.01f) continue
            val direction = offset * (1f / distance)
            val falloff = 1f - distance / effectiveRange
            val strength = GameConfig.REPEL_FORCE * (0.4f + 0.6f * falloff)
            target.position += direction * strength
            target.clampToWorld()
        }
    }

    private fun applyFreezeBlast(source: Blob) {
        // Same reasoning as applyRepelBlast: range off baseRadius, and checked as a
        // circle-circle overlap against the target's own body, not just its center point.
        val range = source.baseRadius * GameConfig.FREEZE_RANGE_MULTIPLIER
        for (target in blobs) {
            if (target === source || !target.alive) continue
            if (target.position.distanceTo(source.position) > range + target.radius) continue
            target.applyFreeze(GameConfig.FREEZE_DURATION_SECONDS)
        }
    }

    // REPEL's opposite: yanks only the single nearest blob toward the source instead of
    // pushing everyone away - a targeted grapple rather than an area push.
    private fun applyHookPull(source: Blob) {
        val range = source.baseRadius * GameConfig.HOOK_RANGE_MULTIPLIER
        val target = blobs
            .filter { it !== source && it.alive }
            .minByOrNull { it.position.distanceTo(source.position) }
            ?: return
        val offset = source.position - target.position
        val distance = offset.length()
        if (distance > range + target.radius || distance < 0.01f) return
        target.position += offset * (1f / distance) * GameConfig.HOOK_FORCE
        target.clampToWorld()
    }

    fun aliveCount(): Int = blobs.count { it.alive }

    private fun resolveCollisions() {
        val alive = blobs.filter { it.alive }
        for (i in alive.indices) {
            val a = alive[i]
            if (!a.alive) continue
            for (j in i + 1 until alive.size) {
                handlePair(a, alive[j])
            }
        }

        for (blob in blobs) {
            if (!blob.alive) continue
            for (powerUp in powerUps) {
                if (powerUp.collected) continue
                if (blob.position.distanceTo(powerUp.position) < blob.radius + powerUp.radius) {
                    val growthMultiplier = if (finalRoundTriggered) {
                        GameConfig.POWERUP_GROWTH_MULTIPLIER_FINAL_ROUND
                    } else {
                        GameConfig.POWERUP_GROWTH_MULTIPLIER
                    }
                    blob.applyPowerUp(powerUp.type, growthMultiplier)
                    powerUp.collected = true
                    listener.onPowerUpCollected(powerUp.position.x, powerUp.position.y, powerUp.type, blob === player)
                }
            }
        }
        powerUps.removeAll { it.collected }
    }

    private fun handlePair(a: Blob, b: Blob) {
        if (!a.alive || !b.alive) return
        val distance = a.position.distanceTo(b.position)
        if (distance >= a.radius + b.radius) return

        val bigger = if (a.radius >= b.radius) a else b
        val smaller = if (a.radius >= b.radius) b else a
        val ratio = bigger.radius / smaller.radius

        // A frozen balloon can't act at all, including absorbing - it's still fair game to
        // be absorbed itself (freeze offers no protection there), so only the bigger side
        // being frozen turns this into a harmless bounce instead of an absorption.
        if (ratio >= GameConfig.ABSORB_RATIO && !bigger.isFrozen) {
            val radiusBefore = bigger.radius
            val x = smaller.position.x
            val y = smaller.position.y
            bigger.absorb(smaller)
            if (bigger === player) playerAbsorbCount++
            listener.onAbsorb(x, y, (bigger.radius - radiusBefore).toInt(), bigger === player, bigger.id, smaller.id)
        } else {
            bounce(a, b, distance)
            if (a is PlayerBlob || b is PlayerBlob) {
                listener.onVibrate()
            }
        }
    }

    private fun bounce(a: Blob, b: Blob, distance: Float) {
        val normal = if (distance < 0.001f) Vector2(1f, 0f) else (a.position - b.position).normalized()
        val overlap = (a.radius + b.radius) - distance
        val push = overlap / 2f + 1f
        a.position += normal * push
        b.position += normal * (-push)
        a.clampToWorld()
        b.clampToWorld()
    }

    private fun updatePowerUps(dt: Float) {
        if (finalRoundTriggered) {
            // Exactly one power-up on screen at a time once the zone stops shrinking - the
            // next one appears the instant the current one is taken, not on a timer, and
            // (unlike the very first one) placed randomly rather than dead center.
            if (powerUps.isEmpty()) {
                spawnPowerUp()
            }
            return
        }
        nextPowerUpSpawnIn -= dt
        if (nextPowerUpSpawnIn <= 0f && powerUps.size < powerUpMaxCount) {
            spawnPowerUp()
            nextPowerUpSpawnIn = randomSpawnDelay()
        }
    }

    private fun spawnPowerUp() {
        // Spawn inside the current safe zone (not the whole map) so power-ups stay where
        // the action is as the zone shrinks, instead of being stranded in the danger area.
        val margin = GameConfig.POWERUP_RADIUS * 2f
        val spawnRadius = (safeZoneRadius - margin).coerceAtLeast(margin)
        val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
        val distance = sqrt(Random.nextFloat()) * spawnRadius
        val position = Vector2(
            safeZoneCenterX + cos(angle) * distance,
            safeZoneCenterY + sin(angle) * distance
        )
        powerUps.add(PowerUp(weightedPowerUpTypes.random(), position))
    }

    private fun randomSpawnDelay(): Float {
        val base = GameConfig.POWERUP_SPAWN_MIN_SECONDS +
            Random.nextFloat() * (GameConfig.POWERUP_SPAWN_MAX_SECONDS - GameConfig.POWERUP_SPAWN_MIN_SECONDS)
        return base / powerUpFrequency
    }

    // A rare, separately-timed SHIELD drop, at most one alive at a time - the regular
    // weighted pool never produces SHIELD, so this is the only way to get one. Skipped
    // during the final round, which already has its own single-power-up rhythm.
    private fun updateSupplyDrop(dt: Float) {
        if (finalRoundTriggered) return
        if (powerUps.any { it.type == PowerUpType.SHIELD }) return
        nextSupplyDropIn -= dt
        if (nextSupplyDropIn <= 0f) {
            spawnSupplyDrop()
            nextSupplyDropIn = randomSupplyDropDelay()
        }
    }

    private fun spawnSupplyDrop() {
        val margin = GameConfig.POWERUP_RADIUS * GameConfig.SUPPLY_DROP_RADIUS_MULTIPLIER * 2f
        val spawnRadius = (safeZoneRadius - margin).coerceAtLeast(margin)
        val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
        val distance = sqrt(Random.nextFloat()) * spawnRadius
        val position = Vector2(
            safeZoneCenterX + cos(angle) * distance,
            safeZoneCenterY + sin(angle) * distance
        )
        powerUps.add(PowerUp(PowerUpType.SHIELD, position))
    }

    private fun randomSupplyDropDelay(): Float =
        GameConfig.SUPPLY_DROP_MIN_SECONDS +
            Random.nextFloat() * (GameConfig.SUPPLY_DROP_MAX_SECONDS - GameConfig.SUPPLY_DROP_MIN_SECONDS)

    private fun checkGameOver() {
        if (!player.alive) {
            gameOver = true
            listener.onGameOver(false, player.radius, aliveCount(), playerAbsorbCount, matchElapsed)
            return
        }
        if (aliveCount() <= 1) {
            gameOver = true
            listener.onGameOver(true, player.radius, 1, playerAbsorbCount, matchElapsed)
        }
    }
}
