package com.hyperionsoftware.balls.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Covers the bot aggressiveness difficulty slider (independent of bot count): the default
// level must reproduce exactly the pre-existing behavior (multiplier 1x), and other levels
// should scale proportionally.
class BotAggressivenessTest {

    @Test
    fun `the default level multiplies by exactly 1x, matching pre-existing behavior`() {
        assertEquals(1f, GameConfig.botAggressivenessMultiplier(GameConfig.BOT_AGGRESSIVENESS_DEFAULT_LEVEL), 0.0001f)
    }

    @Test
    fun `the multiplier scales linearly with level`() {
        val atMin = GameConfig.botAggressivenessMultiplier(GameConfig.BOT_AGGRESSIVENESS_MIN_LEVEL)
        val atDefault = GameConfig.botAggressivenessMultiplier(GameConfig.BOT_AGGRESSIVENESS_DEFAULT_LEVEL)
        val atMax = GameConfig.botAggressivenessMultiplier(GameConfig.BOT_AGGRESSIVENESS_MAX_LEVEL)
        assertTrue("Min level should be less aggressive than default", atMin < atDefault)
        assertTrue("Max level should be more aggressive than default", atMax > atDefault)
    }

    @Test
    fun `the multiplier is clamped to the valid level range`() {
        val belowRange = GameConfig.botAggressivenessMultiplier(GameConfig.BOT_AGGRESSIVENESS_MIN_LEVEL - 5)
        val aboveRange = GameConfig.botAggressivenessMultiplier(GameConfig.BOT_AGGRESSIVENESS_MAX_LEVEL + 5)
        assertEquals(GameConfig.botAggressivenessMultiplier(GameConfig.BOT_AGGRESSIVENESS_MIN_LEVEL), belowRange, 0.0001f)
        assertEquals(GameConfig.botAggressivenessMultiplier(GameConfig.BOT_AGGRESSIVENESS_MAX_LEVEL), aboveRange, 0.0001f)
    }

    @Test
    fun `a higher aggressiveness level makes a bot notice a distant power-up sooner`() {
        val calmEngine = GameEngine(
            botCount = 1, powerUpFrequencyLevel = 1,
            botAggressivenessLevel = GameConfig.BOT_AGGRESSIVENESS_MIN_LEVEL, listener = TestGameListener()
        )
        val sharpEngine = GameEngine(
            botCount = 1, powerUpFrequencyLevel = 1,
            botAggressivenessLevel = GameConfig.BOT_AGGRESSIVENESS_MAX_LEVEL, listener = TestGameListener()
        )

        for (engine in listOf(calmEngine, sharpEngine)) {
            val bot = engine.blobs.first { it.id != engine.player.id }
            bot.radius = GameConfig.BASE_RADIUS
            engine.player.position = Vector2(engine.safeZoneCenterX, engine.safeZoneCenterY - 5000f)
            val cx = engine.safeZoneCenterX
            val cy = engine.safeZoneCenterY
            bot.position = Vector2(cx, cy)
            engine.powerUps.clear()
            // Just beyond the calm bot's vision (radius*8+200 = 520, times its 0.2x
            // multiplier at min level = 104), but well within the sharp bot's (times its
            // 2x multiplier at max level = 1040).
            engine.powerUps.add(PowerUp(PowerUpType.GROWTH, Vector2(cx + 400f, cy)))
        }

        val calmBot = calmEngine.blobs.first { it.id != calmEngine.player.id }
        val sharpBot = sharpEngine.blobs.first { it.id != sharpEngine.player.id }
        val calmDirection = calmBot.decideDirection(calmEngine, 1f / 30f)
        val sharpDirection = sharpBot.decideDirection(sharpEngine, 1f / 30f)

        assertTrue("The sharper bot should beeline for the pickup", sharpDirection.x > 0.9f)

        // The calmer bot shouldn't have noticed the pickup at all and should fall through to
        // its own (fixed but arbitrary) wander direction instead - checked as an exact-value
        // mismatch against (1, 0) rather than a coarse ">0.9" cutoff, since a continuous
        // random wander angle has virtually no chance of landing within 1e-4 of that exact
        // value by coincidence, unlike a wide angular threshold.
        val noticedPickup = kotlin.math.abs(calmDirection.x - 1f) < 0.0001f && kotlin.math.abs(calmDirection.y) < 0.0001f
        assertTrue("The calmer bot shouldn't have noticed a pickup outside its narrower vision", !noticedPickup)
    }
}
