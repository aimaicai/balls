package com.hyperionsoftware.balls.game

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

interface GameListener {
    fun onVibrate()
    fun onAbsorb(x: Float, y: Float, sizeGain: Int, byPlayer: Boolean, absorberId: Int, victimId: Int)
    // Fired whenever an absorb actually extends an existing streak (comboCount >= 2) - the
    // UI layer uses this to show a "COMBO xN!" moment distinct from the plain per-absorb
    // "+size" callout.
    fun onComboAchieved(x: Float, y: Float, comboCount: Int, byPlayer: Boolean)
    fun onPowerUpCollected(x: Float, y: Float, type: PowerUpType, byPlayer: Boolean)
    // sourceRadius and sourcePotencyMultiplier are the user's current radius and POTENCY_UP
    // multiplier at the moment of use - the UI needs both to draw REPEL/FREEZE/HOOK's ripple
    // at the same reach the effect actually has (see GameEngine.reachRangeFromCenter and
    // applyRepelBlast/applyFreezeBlast/applyHookPull), since range now depends on current
    // size and potency, not just baseRadius.
    fun onActiveItemUsed(
        x: Float,
        y: Float,
        type: PowerUpType,
        byPlayer: Boolean,
        sourceRadius: Float,
        sourcePotencyMultiplier: Float
    )
    fun onZoneDeath(x: Float, y: Float, wasPlayer: Boolean)
    fun onDeflateDeath(x: Float, y: Float, wasPlayer: Boolean)
    // Fired once, the instant the final round is set up (see triggerFinalRound) - the UI
    // layer uses this to cut to a dramatic transition before play resumes.
    fun onFinalRoundStarted()
    fun onGameOver(
        playerWon: Boolean,
        finalRadius: Float,
        playersRemaining: Int,
        opponentsAbsorbed: Int,
        elapsedSeconds: Float,
        reachedFinalRound: Boolean
    )
}

class GameEngine(
    botCount: Int,
    powerUpFrequencyLevel: Int,
    arenaSize: GameConfig.ArenaSize = GameConfig.ArenaSize.NORMAL,
    // Lets the main menu drop straight into the final-round setup (all blobs alive, circled
    // around a single centered power-up) with whatever bot count is chosen, instead of
    // having to play a full match down to a handful of survivors just to test that stage.
    private val skipToFinalRound: Boolean = false,
    // Cosmetic only - which color the player's own balloon is drawn in (see
    // com.hyperionsoftware.balls.cosmetics.PlayerColor). Defaults to the original hardcoded
    // blue so nothing changes for callers that don't care about this.
    playerColor: Int = 0xFF4FC3F7.toInt(),
    // How sharply bots notice threats/prey/power-ups, independent of botCount - see
    // BotBlob.decideDirection and GameConfig.botAggressivenessMultiplier.
    botAggressivenessLevel: Int = GameConfig.BOT_AGGRESSIVENESS_DEFAULT_LEVEL,
    // How fast the safe zone closes in, independent of arena size - see
    // GameConfig.applyShrinkSpeed.
    safeZoneShrinkSpeedLevel: Int = GameConfig.SAFE_ZONE_SHRINK_SPEED_DEFAULT_LEVEL,
    private val listener: GameListener
) {
    // Must run before any property below reads WORLD_WIDTH/HEIGHT, SAFE_ZONE_INITIAL_RADIUS,
    // SAFE_ZONE_STAGE_SHRINK_SECONDS or SAFE_ZONE_FINAL_SHRINK_SECONDS, since Kotlin runs
    // init blocks and property initializers in declaration order.
    init {
        GameConfig.applyArenaSize(arenaSize)
        GameConfig.applyShrinkSpeed(safeZoneShrinkSpeedLevel)
    }

    val botAggressivenessMultiplier: Float = GameConfig.botAggressivenessMultiplier(botAggressivenessLevel)

    private val powerUpBaseMaxCount = GameConfig.POWERUP_MAX_COUNT_PER_LEVEL * powerUpFrequencyLevel
    private val powerUpFrequency = powerUpFrequencyLevel.toFloat()

    // GROWTH is weighted heavier than the other types (see GameConfig) since size
    // constantly drains away on its own now. SPEED/INVISIBILITY/REPEL/FREEZE/HOOK are all
    // carried items rather than instant effects, but spawn from this same pool; SPEED_UP,
    // AGILITY_UP and POTENCY_UP are instant, permanent stat increases.
    private val weightedPowerUpTypes: List<PowerUpType> = buildList {
        repeat(GameConfig.POWERUP_GROWTH_WEIGHT) { add(PowerUpType.GROWTH) }
        repeat(GameConfig.POWERUP_SPEED_WEIGHT) { add(PowerUpType.SPEED) }
        repeat(GameConfig.POWERUP_INVISIBILITY_WEIGHT) { add(PowerUpType.INVISIBILITY) }
        repeat(GameConfig.POWERUP_REPEL_WEIGHT) { add(PowerUpType.REPEL) }
        repeat(GameConfig.POWERUP_FREEZE_WEIGHT) { add(PowerUpType.FREEZE) }
        repeat(GameConfig.POWERUP_HOOK_WEIGHT) { add(PowerUpType.HOOK) }
        repeat(GameConfig.POWERUP_SPEED_UP_WEIGHT) { add(PowerUpType.SPEED_UP) }
        repeat(GameConfig.POWERUP_AGILITY_UP_WEIGHT) { add(PowerUpType.AGILITY_UP) }
        repeat(GameConfig.POWERUP_POTENCY_UP_WEIGHT) { add(PowerUpType.POTENCY_UP) }
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
        color = playerColor
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
                // (zero/negative), but by then it's forcing a decisive end either way. Starts
                // from SAFE_ZONE_FINAL_START_RADIUS, not SAFE_ZONE_MIN_RADIUS - the two used
                // to be the same constant, but that tied the final round's starting size to
                // the normal phase's floor, which needed to shrink independently of it.
                val shrinkElapsed = matchElapsed - finalRoundTriggeredAt
                val shrinkRate = (GameConfig.SAFE_ZONE_FINAL_START_RADIUS - GameConfig.SAFE_ZONE_FINAL_MIN_RADIUS) /
                    GameConfig.SAFE_ZONE_FINAL_SHRINK_SECONDS
                return (GameConfig.SAFE_ZONE_FINAL_START_RADIUS - shrinkRate * shrinkElapsed)
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

    // How much real match time is left before the staged shrink finishes and the final
    // round triggers - lets the UI warn the player a few seconds ahead, while normal play
    // is still running, instead of the cut to the final round being a total surprise.
    val secondsUntilFinalRound: Float
        get() {
            if (finalRoundTriggered) return 0f
            val totalStagedDuration = GameConfig.SAFE_ZONE_STAGE_COUNT * safeZoneStageDuration
            return (totalStagedDuration - matchElapsed).coerceAtLeast(0f)
        }

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

    // Per-blob (keyed by id) combo tracking: when the same blob's absorbs land close enough
    // together in time (see registerAbsorbCombo), each one after the first in that streak
    // grants bonus growth and gets called out on screen. Bounded in size by botCount + 1 -
    // dead blobs' entries are simply never touched again, not worth cleaning up.
    private val comboStreak = mutableMapOf<Int, Int>()
    private val lastAbsorbAt = mutableMapOf<Int, Float>()

    // Exposed read-only so the UI layer can react to the transition (e.g. an achievement
    // for reaching the final round) without needing its own copy of the trigger logic.
    val isFinalRoundActive: Boolean get() = finalRoundTriggered

    // Exposed so the HUD can show a live running score during the match, not just the
    // final tally at game over.
    val playerOpponentsAbsorbed: Int get() = playerAbsorbCount

    init {
        val palette = BotPersonality.PALETTE
        repeat(botCount) { index ->
            val position = randomSpawnPosition()
            // Color and personality come from the same palette entry (see BotPersonality) -
            // a bot's color always means the same character, cycling through the palette by
            // spawn order so the same few "characters" repeat once there are more bots than
            // personalities, same as Pac-Man's ghosts.
            val (color, personality) = palette[index % palette.size]
            val bot = BotBlob(id = index + 1, position = position, color = color, personality = personality)
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

    // Plain uniform-random placement could still land a bot right on top of another bot,
    // or of the player's fixed center-of-map start, close enough to bounce or even absorb
    // before the match has properly begun. Re-rolls against every blob already placed
    // (checked incrementally as blobs are added, starting from just the player) until one
    // lands far enough from all of them, or gives up after enough tries - see
    // GameConfig.MIN_SPAWN_SEPARATION/SPAWN_PLACEMENT_MAX_ATTEMPTS.
    private fun randomSpawnPosition(): Vector2 {
        val margin = GameConfig.BASE_RADIUS * 2f
        var candidate = Vector2(0f, 0f)
        for (attempt in 0 until GameConfig.SPAWN_PLACEMENT_MAX_ATTEMPTS) {
            candidate = Vector2(
                Random.nextFloat() * (GameConfig.WORLD_WIDTH - margin * 2f) + margin,
                Random.nextFloat() * (GameConfig.WORLD_HEIGHT - margin * 2f) + margin
            )
            val tooClose = blobs.any { candidate.distanceTo(it.position) < GameConfig.MIN_SPAWN_SEPARATION }
            if (!tooClose) break
        }
        return candidate
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
        listener.onFinalRoundStarted()
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
        listener.onActiveItemUsed(
            blob.position.x, blob.position.y, item, blob === player, blob.radius, blob.permanentPotencyMultiplier
        )
    }

    // How far a REPEL/FREEZE/HOOK reaches from the source's CENTER: always its current
    // edge (source.radius) plus a fixed "reach" beyond that edge (baseRadius * multiplier).
    // Using baseRadius alone for the whole range - as this used to - meant a grown balloon's
    // own body could be bigger than the range itself, leaving the effect unable to reach past
    // its own edge at all once radius grew past roughly baseRadius * multiplier. Anchoring to
    // the current edge instead keeps the same reach-beyond-the-body at any size. The caller
    // folds permanentPotencyMultiplier into the multiplier it passes in, so POTENCY_UP
    // extends reach the same way it strengthens each item's own effect below.
    private fun reachRangeFromCenter(source: Blob, multiplier: Float): Float =
        source.radius + source.baseRadius * multiplier

    private fun applyRepelBlast(source: Blob) {
        val potency = source.permanentPotencyMultiplier
        val range = reachRangeFromCenter(source, GameConfig.REPEL_RANGE_MULTIPLIER * potency)
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
            val strength = GameConfig.REPEL_FORCE * potency * (0.4f + 0.6f * falloff)
            target.position += direction * strength
            target.clampToWorld()
        }
    }

    private fun applyFreezeBlast(source: Blob) {
        // Same reasoning as applyRepelBlast: range measured from the source's current edge,
        // and checked as a circle-circle overlap against the target's own body, not just its
        // center point.
        val potency = source.permanentPotencyMultiplier
        val range = reachRangeFromCenter(source, GameConfig.FREEZE_RANGE_MULTIPLIER * potency)
        for (target in blobs) {
            if (target === source || !target.alive) continue
            if (target.position.distanceTo(source.position) > range + target.radius) continue
            target.applyFreeze(GameConfig.FREEZE_DURATION_SECONDS * potency)
        }
    }

    // REPEL's opposite: yanks only the single nearest blob toward the source instead of
    // pushing everyone away - a targeted grapple rather than an area push.
    private fun applyHookPull(source: Blob) {
        val potency = source.permanentPotencyMultiplier
        val range = reachRangeFromCenter(source, GameConfig.HOOK_RANGE_MULTIPLIER * potency)
        val target = blobs
            .filter { it !== source && it.alive }
            .minByOrNull { it.position.distanceTo(source.position) }
            ?: return
        val offset = source.position - target.position
        val distance = offset.length()
        if (distance > range + target.radius || distance < 0.01f) return
        val direction = offset * (1f / distance)
        // Clamped to the actual gap left once their two bodies are touching, not a flat
        // HOOK_FORCE - a target already closer than that (the common case when the source
        // is small, since range doesn't shrink with it but real gaps do) would otherwise get
        // yanked straight through the source and out the far side instead of stopping next
        // to it, landing further away on the opposite side than where it started.
        val restingGap = source.radius + target.radius
        val pull = (distance - restingGap).coerceIn(0f, GameConfig.HOOK_FORCE * potency)
        target.position += direction * pull
        target.clampToWorld()
    }

    fun aliveCount(): Int = blobs.count { it.alive }

    private fun resolveCollisions() {
        // Iterates blobs directly (skipping dead ones inline) rather than filtering into a
        // fresh list first - this runs every single tick, so that filtered list was one more
        // allocation per frame on top of the pairwise checks below.
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
                    val growthRadiusBonus = if (finalRoundTriggered) {
                        GameConfig.POWERUP_GROWTH_RADIUS_BONUS_FINAL_ROUND
                    } else {
                        GameConfig.POWERUP_GROWTH_RADIUS_BONUS
                    }
                    blob.applyPowerUp(powerUp.type, growthRadiusBonus)
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
        // being frozen turns this into a harmless bounce instead of an absorption. SHIELD is
        // the opposite: it protects the smaller side from being absorbed (on top of already
        // pausing its own ambient deflation - see Blob.applyAmbientDeflation), since a
        // supply-drop-only power-up that a bigger balloon could just walk through and pop
        // anyway would be worth almost nothing.
        if (ratio >= GameConfig.ABSORB_RATIO && !bigger.isFrozen && !smaller.isShielded) {
            val radiusBefore = bigger.radius
            val x = smaller.position.x
            val y = smaller.position.y
            bigger.absorb(smaller)
            if (bigger === player) playerAbsorbCount++

            val comboCount = registerAbsorbCombo(bigger)
            if (comboCount >= 2) {
                bigger.radius = min(GameConfig.MAX_RADIUS, bigger.radius + GameConfig.COMBO_GROWTH_BONUS)
            }

            listener.onAbsorb(x, y, (bigger.radius - radiusBefore).toInt(), bigger === player, bigger.id, smaller.id)
            if (comboCount >= 2) {
                listener.onComboAchieved(x, y, comboCount, bigger === player)
            }
        } else {
            bounce(a, b, distance)
            if (a is PlayerBlob || b is PlayerBlob) {
                listener.onVibrate()
            }
        }
    }

    // Absorbs by the same blob land in a combo when they're no more than COMBO_WINDOW_SECONDS
    // apart - anything slower than that resets the streak back to a fresh 1. Returns the
    // streak length this absorb just extended it to.
    private fun registerAbsorbCombo(absorber: Blob): Int {
        val lastAt = lastAbsorbAt[absorber.id]
        val count = if (lastAt != null && matchElapsed - lastAt <= GameConfig.COMBO_WINDOW_SECONDS) {
            (comboStreak[absorber.id] ?: 1) + 1
        } else {
            1
        }
        lastAbsorbAt[absorber.id] = matchElapsed
        comboStreak[absorber.id] = count
        return count
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
            listener.onGameOver(false, player.radius, aliveCount(), playerAbsorbCount, matchElapsed, finalRoundTriggered)
            return
        }
        if (aliveCount() <= 1) {
            gameOver = true
            listener.onGameOver(true, player.radius, 1, playerAbsorbCount, matchElapsed, finalRoundTriggered)
        }
    }
}
