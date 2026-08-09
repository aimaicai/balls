package com.hyperionsoftware.balls.game

import kotlin.random.Random

interface GameListener {
    fun onVibrate()
    fun onAbsorb(x: Float, y: Float, sizeGain: Int, byPlayer: Boolean)
    fun onPowerUpCollected(x: Float, y: Float, type: PowerUpType, byPlayer: Boolean)
    fun onGameOver(playerWon: Boolean, finalRadius: Float, playersRemaining: Int, opponentsAbsorbed: Int)
}

class GameEngine(
    botCount: Int,
    powerUpFrequencyLevel: Int,
    private val listener: GameListener
) {
    private val powerUpMaxCount = GameConfig.POWERUP_MAX_COUNT_PER_LEVEL * powerUpFrequencyLevel
    private val powerUpFrequency = powerUpFrequencyLevel.toFloat()

    val player = PlayerBlob(
        id = 0,
        position = Vector2(GameConfig.WORLD_WIDTH / 2f, GameConfig.WORLD_HEIGHT / 2f),
        color = 0xFF4FC3F7.toInt()
    )

    val blobs: MutableList<Blob> = mutableListOf(player)
    val powerUps: MutableList<PowerUp> = mutableListOf()
    val initialBlobCount: Int = botCount + 1

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

        for (blob in blobs) {
            val baseSpeed = if (blob is PlayerBlob) GameConfig.PLAYER_BASE_SPEED else GameConfig.BOT_BASE_SPEED
            blob.update(dt, this, baseSpeed)
        }

        resolveCollisions()
        updatePowerUps(dt)
        checkGameOver()
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
        val margin = GameConfig.POWERUP_RADIUS * 2f
        val position = Vector2(
            Random.nextFloat() * (GameConfig.WORLD_WIDTH - margin * 2f) + margin,
            Random.nextFloat() * (GameConfig.WORLD_HEIGHT - margin * 2f) + margin
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
