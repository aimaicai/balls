package com.hyperionsoftware.balls.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Covers the three permanent stat pickups (SPEED_UP/AGILITY_UP/POTENCY_UP): each one should
// advance exactly one tier out of PERMANENT_STAT_TIER_COUNT per pickup, cap at its own max
// multiplier, and never go further once maxed out.
class PermanentStatsTest {

    private fun newPlayer() =
        GameEngine(botCount = 0, powerUpFrequencyLevel = 1, listener = TestGameListener()).player

    @Test
    fun `permanent multipliers start at exactly 1`() {
        val player = newPlayer()
        assertEquals(1f, player.permanentSpeedMultiplier, 0.0001f)
        assertEquals(1f, player.permanentTurnRateMultiplier, 0.0001f)
        assertEquals(1f, player.permanentPotencyMultiplier, 0.0001f)
    }

    @Test
    fun `one pickup advances speed by exactly one tier`() {
        val player = newPlayer()
        player.applyPowerUp(PowerUpType.SPEED_UP)
        val expectedStep = (GameConfig.PERMANENT_SPEED_MAX_MULTIPLIER - 1f) / GameConfig.PERMANENT_STAT_TIER_COUNT
        assertEquals(1f + expectedStep, player.permanentSpeedMultiplier, 0.0001f)
    }

    @Test
    fun `speed caps at PERMANENT_SPEED_MAX_MULTIPLIER and does not exceed it`() {
        val player = newPlayer()
        repeat(GameConfig.PERMANENT_STAT_TIER_COUNT + 5) { player.applyPowerUp(PowerUpType.SPEED_UP) }
        assertEquals(GameConfig.PERMANENT_SPEED_MAX_MULTIPLIER, player.permanentSpeedMultiplier, 0.0001f)
    }

    @Test
    fun `agility caps at PERMANENT_TURN_RATE_MAX_MULTIPLIER and does not exceed it`() {
        val player = newPlayer()
        repeat(GameConfig.PERMANENT_STAT_TIER_COUNT + 5) { player.applyPowerUp(PowerUpType.AGILITY_UP) }
        assertEquals(GameConfig.PERMANENT_TURN_RATE_MAX_MULTIPLIER, player.permanentTurnRateMultiplier, 0.0001f)
    }

    @Test
    fun `potency caps at PERMANENT_POTENCY_MAX_MULTIPLIER and does not exceed it`() {
        val player = newPlayer()
        repeat(GameConfig.PERMANENT_STAT_TIER_COUNT + 5) { player.applyPowerUp(PowerUpType.POTENCY_UP) }
        assertEquals(GameConfig.PERMANENT_POTENCY_MAX_MULTIPLIER, player.permanentPotencyMultiplier, 0.0001f)
    }

    @Test
    fun `the three stats advance independently of each other`() {
        val player = newPlayer()
        player.applyPowerUp(PowerUpType.SPEED_UP)
        assertTrue(player.permanentSpeedMultiplier > 1f)
        assertEquals(1f, player.permanentTurnRateMultiplier, 0.0001f)
        assertEquals(1f, player.permanentPotencyMultiplier, 0.0001f)
    }

    @Test
    fun `each tier step is identical - linear progression, not diminishing`() {
        val player = newPlayer()
        player.applyPowerUp(PowerUpType.SPEED_UP)
        val firstStep = player.permanentSpeedMultiplier - 1f
        player.applyPowerUp(PowerUpType.SPEED_UP)
        val secondStep = player.permanentSpeedMultiplier - (1f + firstStep)
        assertEquals(firstStep, secondStep, 0.0001f)
    }
}
