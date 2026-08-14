package com.hyperionsoftware.balls.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Covers the three carried/activatable items - REPEL, FREEZE, HOOK - and the range formula
// they share (reachRangeFromCenter: always the source's current edge plus a fixed reach
// beyond it, so a grown source's own body never swallows its own effect radius).
class PowerUpEffectsTest {

    private fun newEngine(botCount: Int = 1) =
        GameEngine(botCount = botCount, powerUpFrequencyLevel = 1, listener = TestGameListener())

    @Test
    fun `REPEL pushes a nearby target away from the source`() {
        val engine = newEngine()
        val player = engine.player
        val bot = engine.blobs.first { it.id != player.id }
        player.position = Vector2(1000f, 1000f)
        bot.position = Vector2(1100f, 1000f)

        player.pickUpCarriedItem(PowerUpType.REPEL)
        engine.activateCarriedItem(player)

        assertTrue("REPEL should push the target further away", bot.position.x > 1100f)
    }

    @Test
    fun `REPEL does not affect a target outside its range`() {
        val engine = newEngine()
        val player = engine.player
        val bot = engine.blobs.first { it.id != player.id }
        player.radius = GameConfig.BASE_RADIUS
        player.position = Vector2(1000f, 1000f)
        // Comfortably beyond range (edge + baseRadius * REPEL_RANGE_MULTIPLIER).
        val outOfRange = player.radius + GameConfig.BASE_RADIUS * GameConfig.REPEL_RANGE_MULTIPLIER + bot.radius + 500f
        bot.position = Vector2(1000f + outOfRange, 1000f)

        player.pickUpCarriedItem(PowerUpType.REPEL)
        engine.activateCarriedItem(player)

        assertEquals(1000f + outOfRange, bot.position.x, 0.001f)
    }

    @Test
    fun `REPEL still reaches beyond its own body once the source has grown large`() {
        // Regression guard: range used to be based on baseRadius alone, so a grown source's
        // own body could exceed the range itself, leaving REPEL unable to affect anything
        // past its own edge. reachRangeFromCenter fixes this by always adding the reach on
        // top of the CURRENT radius.
        val engine = newEngine()
        val player = engine.player
        val bot = engine.blobs.first { it.id != player.id }
        player.radius = 300f // much bigger than baseRadius (40)
        player.position = Vector2(1000f, 1000f)
        // Just past the player's own edge - would have been unreachable under the old,
        // baseRadius-only formula (baseRadius * 6 = 240 < player.radius = 300).
        bot.position = Vector2(1000f + player.radius + 10f, 1000f)

        player.pickUpCarriedItem(PowerUpType.REPEL)
        engine.activateCarriedItem(player)

        assertTrue("REPEL should still reach just past a grown source's own edge", bot.position.x > 1000f + player.radius + 10f)
    }

    @Test
    fun `REPEL force scales with permanentPotencyMultiplier`() {
        val baseline = newEngine()
        val basePlayer = baseline.player
        val baseBot = baseline.blobs.first { it.id != basePlayer.id }
        basePlayer.position = Vector2(1000f, 1000f)
        baseBot.position = Vector2(1100f, 1000f)
        basePlayer.pickUpCarriedItem(PowerUpType.REPEL)
        baseline.activateCarriedItem(basePlayer)
        val baselinePush = baseBot.position.x - 1100f

        val boosted = newEngine()
        val boostedPlayer = boosted.player
        val boostedBot = boosted.blobs.first { it.id != boostedPlayer.id }
        repeat(GameConfig.PERMANENT_STAT_TIER_COUNT) { boostedPlayer.applyPowerUp(PowerUpType.POTENCY_UP) }
        boostedPlayer.position = Vector2(1000f, 1000f)
        boostedBot.position = Vector2(1100f, 1000f)
        boostedPlayer.pickUpCarriedItem(PowerUpType.REPEL)
        boosted.activateCarriedItem(boostedPlayer)
        val boostedPush = boostedBot.position.x - 1100f

        assertTrue("Maxed-out potency should push harder than the default", boostedPush > baselinePush)
    }

    @Test
    fun `FREEZE freezes a target within range for FREEZE_DURATION_SECONDS`() {
        val engine = newEngine()
        val player = engine.player
        val bot = engine.blobs.first { it.id != player.id }
        player.position = Vector2(1000f, 1000f)
        bot.position = Vector2(1100f, 1000f)

        player.pickUpCarriedItem(PowerUpType.FREEZE)
        engine.activateCarriedItem(player)

        assertTrue(bot.isFrozen)
    }

    @Test
    fun `FREEZE does not affect a target outside its range`() {
        val engine = newEngine()
        val player = engine.player
        val bot = engine.blobs.first { it.id != player.id }
        player.radius = GameConfig.BASE_RADIUS
        player.position = Vector2(1000f, 1000f)
        val outOfRange = player.radius + GameConfig.BASE_RADIUS * GameConfig.FREEZE_RANGE_MULTIPLIER + bot.radius + 500f
        bot.position = Vector2(1000f + outOfRange, 1000f)

        player.pickUpCarriedItem(PowerUpType.FREEZE)
        engine.activateCarriedItem(player)

        assertFalse(bot.isFrozen)
    }

    @Test
    fun `HOOK pulls a nearby target closer without overshooting past the source`() {
        // Regression guard for the reported bug: HOOK used to add a flat HOOK_FORCE toward
        // the source regardless of the actual gap, so a target already closer than that got
        // yanked straight through the source and out the far side.
        val engine = newEngine()
        val player = engine.player
        val bot = engine.blobs.first { it.id != player.id }
        player.radius = 15f // small, matching the reported scenario
        bot.radius = 20f
        player.position = Vector2(1000f, 1000f)
        bot.position = Vector2(1100f, 1000f) // gap smaller than HOOK_FORCE

        player.pickUpCarriedItem(PowerUpType.HOOK)
        engine.activateCarriedItem(player)

        assertTrue("The target should stay on the side it approached from", bot.position.x > player.position.x)
        assertTrue("The target should have been pulled closer", bot.position.x < 1100f)
    }

    @Test
    fun `HOOK stops the target right at the source's edge, never past it`() {
        val engine = newEngine()
        val player = engine.player
        val bot = engine.blobs.first { it.id != player.id }
        player.radius = 100f
        bot.radius = 20f
        player.position = Vector2(1000f, 1000f)
        bot.position = Vector2(1250f, 1000f)

        player.pickUpCarriedItem(PowerUpType.HOOK)
        engine.activateCarriedItem(player)

        val finalGap = bot.position.x - player.position.x - player.radius - bot.radius
        assertEquals(0f, finalGap, 0.5f)
    }

    @Test
    fun `HOOK does not push an already-touching target backward`() {
        val engine = newEngine()
        val player = engine.player
        val bot = engine.blobs.first { it.id != player.id }
        player.radius = 50f
        bot.radius = 20f
        player.position = Vector2(1000f, 1000f)
        bot.position = Vector2(1030f, 1000f) // already overlapping (radii sum to 70)

        player.pickUpCarriedItem(PowerUpType.HOOK)
        val before = bot.position.x
        engine.activateCarriedItem(player)

        assertEquals(before, bot.position.x, 0.01f)
    }

    @Test
    fun `HOOK targets whichever alive blob is nearest, not just the first one in the list`() {
        val engine = newEngine(botCount = 2)
        val player = engine.player
        val bots = engine.blobs.filter { it.id != player.id }
        player.position = Vector2(1000f, 1000f)
        bots[0].position = Vector2(2000f, 1000f) // far
        bots[1].position = Vector2(1100f, 1000f) // near - this one should be pulled

        player.pickUpCarriedItem(PowerUpType.HOOK)
        engine.activateCarriedItem(player)

        assertEquals(2000f, bots[0].position.x, 0.01f)
        assertTrue("The nearer bot should have moved", bots[1].position.x != 1100f)
    }

    @Test
    fun `activating an item with nothing carried is a no-op`() {
        val engine = newEngine()
        val player = engine.player
        val bot = engine.blobs.first { it.id != player.id }
        val before = bot.position.copy()

        engine.activateCarriedItem(player) // nothing picked up

        assertEquals(before, bot.position)
    }
}
