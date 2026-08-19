package com.hyperionsoftware.balls.game

import kotlin.math.PI
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Covers absorption: only a blob at least ABSORB_RATIO times bigger than another can absorb
// it on contact, the resulting radius follows conservation of area (not just adding radii),
// and a frozen bigger blob can't absorb (though it can still be absorbed itself).
class AbsorptionTest {

    private fun newEngine(botCount: Int = 1) =
        GameEngine(botCount = botCount, powerUpFrequencyLevel = 1, listener = TestGameListener())

    @Test
    fun `a blob big enough absorbs a touching smaller one and grows by conserved area`() {
        val engine = newEngine()
        val player = engine.player
        val bot = engine.blobs.first { it.id != player.id }

        player.radius = 50f
        bot.radius = 50f / GameConfig.ABSORB_RATIO // exactly at the absorb threshold
        player.position = Vector2(1000f, 1000f)
        bot.position = Vector2(1000f + player.radius + bot.radius - 1f, 1000f) // overlapping

        val expectedArea = (PI.toFloat() * player.radius * player.radius) + (PI.toFloat() * bot.radius * bot.radius)
        val expectedRadius = sqrt(expectedArea / PI.toFloat())

        engine.update(1f / 60f)

        assertFalse("The absorbed bot should no longer be alive", bot.alive)
        assertEquals(expectedRadius, player.radius, 0.5f)
    }

    @Test
    fun `a blob just under the absorb ratio only bounces, it does not absorb`() {
        val engine = newEngine()
        val player = engine.player
        val bot = engine.blobs.first { it.id != player.id }

        player.radius = 50f
        // Just below ABSORB_RATIO - close enough to overlap, not enough to absorb.
        bot.radius = 50f / (GameConfig.ABSORB_RATIO - 0.05f)
        player.position = Vector2(1000f, 1000f)
        bot.position = Vector2(1000f + player.radius + bot.radius - 1f, 1000f)

        engine.update(1f / 60f)

        assertTrue("Neither side should be absorbed below the ratio", player.alive && bot.alive)
    }

    @Test
    fun `growth never exceeds MAX_RADIUS`() {
        val engine = newEngine()
        val player = engine.player
        val bot = engine.blobs.first { it.id != player.id }

        player.radius = GameConfig.MAX_RADIUS - 1f
        bot.radius = GameConfig.MAX_RADIUS // huge victim, would overshoot MAX_RADIUS if uncapped
        player.position = Vector2(1000f, 1000f)
        bot.position = Vector2(1000f + player.radius * 0.5f, 1000f)

        player.absorb(bot)

        assertEquals(GameConfig.MAX_RADIUS, player.radius, 0.001f)
    }

    @Test
    fun `a frozen bigger blob cannot absorb, but can still be absorbed itself`() {
        val engine = newEngine()
        val player = engine.player
        val bot = engine.blobs.first { it.id != player.id }

        player.radius = 50f
        bot.radius = 50f / GameConfig.ABSORB_RATIO
        player.position = Vector2(1000f, 1000f)
        bot.position = Vector2(1000f + player.radius + bot.radius - 1f, 1000f)
        player.applyFreeze(5f)

        engine.update(1f / 60f)

        assertTrue("A frozen bigger blob should not have absorbed anything", bot.alive)
        assertTrue("Freezing offers no protection against being absorbed", player.alive)
    }

    @Test
    fun `a shielded smaller blob cannot be absorbed, even by a much bigger one`() {
        val engine = newEngine()
        val player = engine.player
        val bot = engine.blobs.first { it.id != player.id }

        bot.radius = 50f
        player.radius = 50f / GameConfig.ABSORB_RATIO
        bot.position = Vector2(1000f, 1000f)
        player.position = Vector2(1000f + bot.radius + player.radius - 1f, 1000f)
        player.applyPowerUp(PowerUpType.SHIELD)

        engine.update(1f / 60f)

        assertTrue("A shielded blob should not have been absorbed", player.alive)
        assertTrue("The bigger blob should not have absorbed anything either", bot.alive)
    }

    @Test
    fun `a shielded bigger blob still absorbs a smaller one normally`() {
        val engine = newEngine()
        val player = engine.player
        val bot = engine.blobs.first { it.id != player.id }

        player.radius = 50f
        bot.radius = 50f / GameConfig.ABSORB_RATIO
        player.position = Vector2(1000f, 1000f)
        bot.position = Vector2(1000f + player.radius + bot.radius - 1f, 1000f)
        player.applyPowerUp(PowerUpType.SHIELD)

        engine.update(1f / 60f)

        assertFalse("Shield on the absorbing side shouldn't stop it from absorbing", bot.alive)
    }

    @Test
    fun `absorbing the victim marks it dead and does not touch its own radius field`() {
        val engine = newEngine()
        val player = engine.player
        val bot = engine.blobs.first { it.id != player.id }
        bot.radius = 30f

        player.radius = 100f
        player.absorb(bot)

        assertFalse(bot.alive)
        assertEquals(30f, bot.radius, 0.001f)
    }
}
