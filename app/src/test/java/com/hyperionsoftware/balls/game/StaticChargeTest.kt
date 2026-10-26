package com.hyperionsoftware.balls.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Covers static electricity: balloons that bounce off each other charge up in proportion to how
// far their latex slid past the other's, a full charge stays full until released, releasing it
// only works when full, bots let theirs go once someone's in reach, and the discharge's bolt
// chains from balloon to balloon.
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
    fun `bots hold a full charge until someone is in reach`() {
        val listener = TestGameListener()
        val engine = newEngine(listener)
        val player = engine.player
        val bot = engine.blobs.first { it !== player }
        player.position = Vector2(300f, 300f)
        bot.position = Vector2(1800f, 1800f)
        bot.addStaticCharge(1f)
        val dt = 1f / 60f
        repeat(120) {
            player.position = Vector2(300f, 300f)
            bot.position = Vector2(1800f, 1800f)
            engine.update(dt)
        }
        assertTrue("nobody in reach, so it keeps it", bot.isFullyCharged)

        repeat(60) {
            player.position = Vector2(1800f + 200f, 1800f)
            bot.position = Vector2(1800f, 1800f)
            engine.update(dt)
        }
        assertEquals(0f, bot.staticCharge, 0f)
        assertEquals(1, listener.staticDischargedCount)
        assertTrue("and the player took the bolt", player.isStunned || listener.arcs.any { it.toId == player.id })
    }

    // ---- The discharge itself ----------------------------------------------------------------

    // Bots lined up to the player's right, every one of them still (frozen keeps them from
    // wandering off, and a frozen balloon can still be struck), each `gap` apart edge to edge.
    private fun lineUp(botCount: Int, gap: Float, listener: TestGameListener): GameEngine {
        val engine = GameEngine(botCount = botCount, powerUpFrequencyLevel = 1, listener = listener)
        engine.powerUps.clear()
        val player = engine.player
        player.radius = 40f
        player.position = Vector2(1000f, 1000f)
        engine.blobs.filter { it !== player }.forEachIndexed { i, bot ->
            bot.radius = 40f
            bot.position = Vector2(1000f + (i + 1) * (80f + gap), 1000f)
            bot.applyFreeze(30f)
        }
        return engine
    }

    private fun bots(engine: GameEngine) = engine.blobs.filter { it !== engine.player }

    private fun runFor(engine: GameEngine, seconds: Float) {
        val dt = 1f / 60f
        repeat((seconds / dt).toInt()) { engine.update(dt) }
    }

    @Test
    fun `a discharge strikes the nearest balloon, deflating and stunning it`() {
        val listener = TestGameListener()
        val engine = lineUp(1, 100f, listener)
        val player = engine.player
        val bot = bots(engine)[0]
        player.addStaticCharge(1f)
        engine.dischargeStatic(player)
        assertEquals(1, listener.arcs.size)
        assertEquals(bot.id, listener.arcs[0].toId)
        assertTrue(bot.isStunned)
        assertEquals(40f * (1f - GameConfig.STATIC_SHOCK_DEFLATE_FRACTION), bot.radius, 0.001f)
        assertTrue(listener.arcs[0].byPlayer)
    }

    @Test
    fun `nobody in reach means the discharge backfires, stunning without deflating`() {
        val listener = TestGameListener()
        val engine = lineUp(1, GameConfig.BASE_RADIUS * GameConfig.STATIC_DISCHARGE_RANGE_MULTIPLIER + 50f, listener)
        val player = engine.player
        player.addStaticCharge(1f)
        engine.dischargeStatic(player)
        assertEquals(1, listener.staticDischargedCount)
        assertTrue(listener.arcs.isEmpty())
        assertTrue(bots(engine).none { it.isStunned })
        assertEquals(1, listener.backfireCount)
        assertTrue(player.isStunned)
        assertEquals(40f, player.radius, 0.001f)
    }

    @Test
    fun `a chain running out of neighbours doesn't backfire on anyone`() {
        val listener = TestGameListener()
        val engine = lineUp(1, 100f, listener)
        engine.player.addStaticCharge(1f)
        engine.dischargeStatic(engine.player)
        runFor(engine, 1f)
        assertEquals(0, listener.backfireCount)
        assertTrue(!engine.player.isStunned)
    }

    @Test
    fun `the bolt jumps along the chain one hop at a time and never back to the discharger`() {
        val listener = TestGameListener()
        val engine = lineUp(3, 60f, listener)
        val player = engine.player
        player.addStaticCharge(1f)
        engine.dischargeStatic(player)
        assertEquals(1, listener.arcs.size)
        runFor(engine, GameConfig.STATIC_CHAIN_HOP_SECONDS * 0.5f)
        assertEquals("the next hop waits its turn", 1, listener.arcs.size)
        runFor(engine, GameConfig.STATIC_CHAIN_HOP_SECONDS * 6f)
        val ids = bots(engine).map { it.id }
        assertEquals(ids, listener.arcs.map { it.toId })
        assertTrue(listener.arcs.none { it.toId == player.id })
        assertTrue(bots(engine).all { it.isStunned })
        assertTrue(!player.isStunned)
    }

    @Test
    fun `hops only reach a close neighbour`() {
        val listener = TestGameListener()
        val engine = lineUp(2, 100f, listener)
        // Moves the second bot out of hop reach of the first, but leaves the first in reach.
        val far = bots(engine)[1]
        far.position = Vector2(far.position.x + GameConfig.BASE_RADIUS * GameConfig.STATIC_CHAIN_RANGE_MULTIPLIER, 1000f)
        engine.player.addStaticCharge(1f)
        engine.dischargeStatic(engine.player)
        runFor(engine, 1f)
        assertEquals(listOf(bots(engine)[0].id), listener.arcs.map { it.toId })
        assertTrue(!far.isStunned)
    }

    @Test
    fun `striking a fully charged balloon sets its own discharge off, which can hit the discharger`() {
        val listener = TestGameListener()
        val engine = lineUp(1, 100f, listener)
        val player = engine.player
        val bot = bots(engine)[0]
        player.addStaticCharge(1f)
        bot.addStaticCharge(1f)
        engine.dischargeStatic(player)
        assertEquals(0f, bot.staticCharge, 0f)
        assertEquals(2, listener.staticDischargedCount)
        runFor(engine, GameConfig.STATIC_CHAIN_HOP_SECONDS * 3f)
        assertEquals(listOf(bot.id, player.id), listener.arcs.map { it.toId })
        assertTrue(player.isStunned)
        assertTrue(!listener.arcs[1].byPlayer)
        assertTrue(listener.arcs[1].hitPlayer)
    }

    @Test
    fun `a shielded balloon is immune and passes the bolt straight on`() {
        val listener = TestGameListener()
        val engine = lineUp(2, 60f, listener)
        val (shielded, behind) = bots(engine)
        shielded.applyPowerUp(PowerUpType.SHIELD)
        shielded.addStaticCharge(1f)
        engine.player.addStaticCharge(1f)
        engine.dischargeStatic(engine.player)
        // No hop delay after a shield: both strikes land at once.
        assertEquals(listOf(shielded.id, behind.id), listener.arcs.map { it.toId })
        assertTrue(listener.arcs[0].blocked)
        assertTrue(!listener.arcs[1].blocked)
        assertEquals(40f, shielded.radius, 0.001f)
        assertTrue(!shielded.isStunned)
        assertTrue("its own charge isn't set off either", shielded.isFullyCharged)
        assertTrue(behind.isStunned)
    }

    @Test
    fun `a stunned balloon can't move or absorb`() {
        val listener = TestGameListener()
        val engine = lineUp(1, 100f, listener)
        val player = engine.player
        val bot = bots(engine)[0]
        player.addStaticCharge(1f)
        bot.addStaticCharge(1f)
        engine.dischargeStatic(player)
        runFor(engine, GameConfig.STATIC_CHAIN_HOP_SECONDS * 2f)
        assertTrue(player.isStunned)
        val before = Vector2(player.position.x, player.position.y)
        player.inputDirection = Vector2(1f, 0f)
        engine.update(1f / 60f)
        assertEquals(0f, player.currentSpeed, 0f)
        assertTrue(player.position.distanceTo(before) < 1f)

        // Tiny and pressed against the stunned player, the bot would normally be absorbed.
        bot.radius = 15f
        bot.position = Vector2(player.position.x + 40f, player.position.y)
        engine.update(1f / 60f)
        assertTrue(bot.alive)
        runFor(engine, GameConfig.STATIC_SHOCK_STUN_SECONDS + 0.1f)
        assertTrue(!player.isStunned)
    }

    @Test
    fun `range queries match what the items actually do`() {
        val listener = TestGameListener()
        val engine = lineUp(1, 100f, listener)
        val player = engine.player
        val bot = bots(engine)[0]
        assertEquals(bot, engine.hookTarget(player))
        assertEquals(bot, engine.staticDischargeTarget(player))
        assertEquals(40f + GameConfig.BASE_RADIUS * GameConfig.FREEZE_RANGE_MULTIPLIER, engine.itemRange(player, PowerUpType.FREEZE), 0.001f)
        assertEquals(0f, engine.itemRange(player, PowerUpType.SPEED), 0f)
        bot.position = Vector2(1000f + 40f + engine.itemRange(player, PowerUpType.HOOK) + 60f, 1000f)
        assertEquals(null, engine.hookTarget(player))
    }
}
