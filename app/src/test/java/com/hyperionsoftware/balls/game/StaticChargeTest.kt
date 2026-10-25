package com.hyperionsoftware.balls.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Covers static electricity: balloons that bounce off each other charge up in proportion to how
// far their latex slid past the other's, a full charge stays full until released, releasing it
// only works when full, and bots let theirs go on their own after a while.
class StaticChargeTest {

    private fun newEngine(listener: TestGameListener = TestGameListener()) =
        GameEngine(botCount = 1, powerUpFrequencyLevel = 1, listener = listener)

    // Keeps the player and the bot pressed side by side every tick (the engine's bounce would
    // otherwise push them apart), the player driving along the contact rather than into it,
    // past a bot held still (frozen) so the bot's own wandering doesn't change how far they slide.
    private fun rubFor(engine: GameEngine, bot: Blob, seconds: Float) {
        val player = engine.player
        val dt = 1f / 60f
        bot.applyFreeze(seconds + 1f)
        repeat((seconds / dt).toInt()) {
            player.radius = 40f
            bot.radius = 40f
            player.position = Vector2(1000f, 1000f)
            bot.position = Vector2(1000f + 72f, 1000f)
            player.facingDirection = Vector2(0f, -1f)
            player.inputDirection = Vector2(0f, -1f)
            engine.update(dt)
        }
    }

    @Test
    fun `sliding contact charges both balloons`() {
        val engine = newEngine()
        val bot = engine.blobs.first { it !== engine.player }
        rubFor(engine, bot, 1f)
        assertTrue("player should have picked up some charge", engine.player.staticCharge > 0.1f)
        assertTrue("so should the bot it rubbed against", bot.staticCharge > 0.1f)
        assertTrue("but not a full charge from a single second", engine.player.staticCharge < 1f)
        // A second of sliding at full speed is that fraction of the full rub distance.
        assertEquals(GameConfig.PLAYER_BASE_SPEED / GameConfig.STATIC_CHARGE_FULL_RUB_DISTANCE, engine.player.staticCharge, 0.03f)
    }

    @Test
    fun `sustained rubbing fills the charge exactly once`() {
        val listener = TestGameListener()
        val engine = newEngine(listener)
        val bot = engine.blobs.first { it !== engine.player }
        rubFor(engine, bot, 11f)
        assertTrue(engine.player.isFullyCharged)
        assertEquals(1f, engine.player.staticCharge, 0f)
        assertTrue("both filled up, each reported once", listener.staticFullyChargedCount in 1..2)
    }

    @Test
    fun `rubbing two balloons at once charges no faster than one`() {
        val engine = GameEngine(botCount = 2, powerUpFrequencyLevel = 1, listener = TestGameListener())
        val player = engine.player
        val bots = engine.blobs.filter { it !== player }
        val dt = 1f / 60f
        bots.forEach { it.applyFreeze(5f) }
        repeat(60) {
            player.radius = 40f
            player.position = Vector2(1000f, 1000f)
            player.facingDirection = Vector2(0f, -1f)
            player.inputDirection = Vector2(0f, -1f)
            bots.forEachIndexed { i, bot ->
                bot.radius = 40f
                bot.position = Vector2(if (i == 0) 1072f else 928f, 1000f)
            }
            engine.update(dt)
        }
        assertEquals(GameConfig.PLAYER_BASE_SPEED / GameConfig.STATIC_CHARGE_FULL_RUB_DISTANCE, player.staticCharge, 0.03f)
    }

    @Test
    fun `balloons apart do not charge`() {
        val engine = newEngine()
        val bot = engine.blobs.first { it !== engine.player }
        engine.player.position = Vector2(500f, 500f)
        bot.position = Vector2(1500f, 1500f)
        repeat(60) { engine.update(1f / 60f) }
        assertEquals(0f, engine.player.staticCharge, 0f)
    }

    @Test
    fun `discharging only works when full and empties the charge`() {
        val listener = TestGameListener()
        val engine = newEngine(listener)
        val player = engine.player
        player.addStaticCharge(0.5f)
        engine.dischargeStatic(player)
        assertEquals(0.5f, player.staticCharge, 0.0001f)
        assertEquals(0, listener.staticDischargedCount)

        player.addStaticCharge(1f)
        engine.dischargeStatic(player)
        assertEquals(0f, player.staticCharge, 0f)
        assertEquals(1, listener.staticDischargedCount)
    }

    @Test
    fun `bots release a full charge after holding it for a while`() {
        val listener = TestGameListener()
        val engine = newEngine(listener)
        val bot = engine.blobs.first { it !== engine.player }
        engine.player.position = Vector2(300f, 300f)
        bot.position = Vector2(1800f, 1800f)
        bot.addStaticCharge(1f)
        val dt = 1f / 60f
        repeat(((GameConfig.BOT_STATIC_HOLD_SECONDS + 0.5f) / dt).toInt()) { engine.update(dt) }
        assertEquals(0f, bot.staticCharge, 0f)
        assertEquals(1, listener.staticDischargedCount)
    }
}
