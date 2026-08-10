package com.hyperionsoftware.balls.game

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

interface GameListener {
    fun onVibrate()
    fun onAbsorb(x: Float, y: Float, sizeGain: Int, byPlayer: Boolean)
    fun onPowerUpCollected(x: Float, y: Float, type: PowerUpType, byPlayer: Boolean)
    fun onZoneDeath(x: Float, y: Float, wasPlayer: Boolean)
    fun onDeflateDeath(x: Float, y: Float, wasPlayer: Boolean)
    fun onGameOver(playerWon: Boolean, finalRadius: Float, playersRemaining: Int, opponentsAbsorbed: Int)
}

class GameEngine(
    botCount: Int,
    powerUpFrequencyLevel: Int,
    private val listener: GameListener
) {
    private val powerUpBaseMaxCount = GameConfig.POWERUP_MAX_COUNT_PER_LEVEL * powerUpFrequencyLevel
    private val powerUpFrequency = powerUpFrequencyLevel.toFloat()

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
    val safeZoneProgress: Float
        get() = (matchElapsed / GameConfig.SAFE_ZONE_SHRINK_DURATION_SECONDS).coerceIn(0f, 1f)
    val safeZoneRadius: Float
        get() = GameConfig.SAFE_ZONE_INITIAL_RADIUS -
            safeZoneProgress * (GameConfig.SAFE_ZONE_INITIAL_RADIUS - GameConfig.SAFE_ZONE_MIN_RADIUS)

    private var matchElapsed = 0f
    private var nextPowerUpSpawnIn: Float = randomSpawnDelay()
    private var gameOver = false
    private var playerAbsorbCount = 0

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
            blobs.add(BotBlob(id = index + 1, position = position, color = colors[index % colors.size]))
        }
    }

    fun update(dt: Float) {
        if (gameOver) return
        matchElapsed += dt

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
                    blob.applyPowerUp(powerUp.type)
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

        if (ratio >= GameConfig.ABSORB_RATIO) {
            val radiusBefore = bigger.radius
            val x = smaller.position.x
            val y = smaller.position.y
            bigger.absorb(smaller)
            if (bigger === player) playerAbsorbCount++
            listener.onAbsorb(x, y, (bigger.radius - radiusBefore).toInt(), bigger === player)
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
        powerUps.add(PowerUp(PowerUpType.entries.random(), position))
    }

    private fun randomSpawnDelay(): Float {
        val base = GameConfig.POWERUP_SPAWN_MIN_SECONDS +
            Random.nextFloat() * (GameConfig.POWERUP_SPAWN_MAX_SECONDS - GameConfig.POWERUP_SPAWN_MIN_SECONDS)
        return base / powerUpFrequency
    }

    private fun checkGameOver() {
        if (!player.alive) {
            gameOver = true
            listener.onGameOver(false, player.radius, aliveCount(), playerAbsorbCount)
            return
        }
        if (aliveCount() <= 1) {
            gameOver = true
            listener.onGameOver(true, player.radius, 1, playerAbsorbCount)
        }
    }
}
