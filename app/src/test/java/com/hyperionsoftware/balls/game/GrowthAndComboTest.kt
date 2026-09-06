package com.hyperionsoftware.balls.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthAndComboTest {

    private fun newEngine(botCount: Int = 1) =
        GameEngine(botCount = botCount, powerUpFrequencyLevel = 1, listener = TestGameListener())

    // --- GROWTH: fixed bonus, not a multiplier ---

    @Test
    fun `GROWTH adds the same fixed amount regardless of current size`() {
        val small = newEngine().player.apply { radius = 40f }
        val big = newEngine().player.apply { radius = 200f }

        small.applyPowerUp(PowerUpType.GROWTH)
        big.applyPowerUp(PowerUpType.GROWTH)

        assertEquals(40f + GameConfig.POWERUP_GROWTH_RADIUS_BONUS, small.radius, 0.001f)
        assertEquals(200f + GameConfig.POWERUP_GROWTH_RADIUS_BONUS, big.radius, 0.001f)
    }

    @Test
    fun `GROWTH never exceeds MAX_RADIUS`() {
        val player = newEngine().player
        player.radius = GameConfig.MAX_RADIUS - 1f
        player.applyPowerUp(PowerUpType.GROWTH)
        assertEquals(GameConfig.MAX_RADIUS, player.radius, 0.001f)
    }

    @Test
    fun `the final round's growth bonus takes exactly two pickups to clear ABSORB_RATIO`() {
        val player = newEngine().player
        player.radius = GameConfig.BASE_RADIUS
        val untouchedOpponentRadius = GameConfig.BASE_RADIUS

        player.applyPowerUp(PowerUpType.GROWTH, GameConfig.POWERUP_GROWTH_RADIUS_BONUS_FINAL_ROUND)
        assertTrue(
            "One final-round GROWTH pickup alone should not yet clear ABSORB_RATIO",
            player.radius / untouchedOpponentRadius < GameConfig.ABSORB_RATIO
        )

        player.applyPowerUp(PowerUpType.GROWTH, GameConfig.POWERUP_GROWTH_RADIUS_BONUS_FINAL_ROUND)
        assertTrue(
            "Two final-round GROWTH pickups should clear ABSORB_RATIO",
            player.radius / untouchedOpponentRadius >= GameConfig.ABSORB_RATIO
        )
    }

    // --- Combo: chained absorbs within COMBO_WINDOW_SECONDS ---

    @Test
    fun `a single absorb alone is not a combo - no bonus, no callback`() {
        val listener = TestGameListener()
        val engine = GameEngine(botCount = 1, powerUpFrequencyLevel = 1, listener = listener)
        val player = engine.player
        val bot = engine.blobs.first { it.id != player.id }
        // A stray GROWTH spawned right where the player ends up (rare, but possible) would
        // throw off the exact radius this test checks below.
        engine.powerUps.clear()

        player.radius = 200f
        bot.radius = 20f
        player.position = Vector2(1000f, 1000f)
        bot.position = Vector2(1000f, 1000f)

        val radiusAfterAbsorbOnly = run {
            val area = Math.PI.toFloat() * (200f * 200f + 20f * 20f)
            kotlin.math.sqrt(area / Math.PI.toFloat())
        }
        engine.update(1f / 30f)

        assertEquals(0, listener.comboCounts.size)
        assertEquals(radiusAfterAbsorbOnly, player.radius, 0.5f)
    }

    @Test
    fun `two absorbs within the combo window chain into a combo and grant bonus growth`() {
        val listener = TestGameListener()
        val engine = GameEngine(botCount = 2, powerUpFrequencyLevel = 1, listener = listener)
        val player = engine.player
        val bots = engine.blobs.filter { it.id != player.id }
        // A stray GROWTH spawned right where the player ends up (rare, but possible) would
        // throw off the exact radius this test checks below.
        engine.powerUps.clear()

        player.radius = 200f
        bots.forEach {
            it.radius = 20f
            it.applyFreeze(1000f) // keep them from wandering off before they're absorbed
            it.position = Vector2(1000f, 1000f) // overlapping the player from the start
        }
        player.position = Vector2(1000f, 1000f)

        val radiusBeforeAnyAbsorb = player.radius
        engine.update(1f / 30f) // both bots overlap the player - resolved in the same frame

        // Both absorptions land in the same frame (matchElapsed unchanged between them), so
        // this is a same-frame double-combo: the first is the start of the streak (count 1,
        // no callback), the second extends it to count 2 and grants the bonus.
        assertEquals(listOf(2), listener.comboCounts)

        val expectedArea = Math.PI.toFloat() * (radiusBeforeAnyAbsorb * radiusBeforeAnyAbsorb + 20f * 20f + 20f * 20f)
        val expectedRadiusFromAbsorbAlone = kotlin.math.sqrt(expectedArea / Math.PI.toFloat())
        assertEquals(expectedRadiusFromAbsorbAlone + GameConfig.COMBO_GROWTH_BONUS, player.radius, 0.5f)
    }

    @Test
    fun `absorbs further apart than the combo window do not chain`() {
        val listener = TestGameListener()
        val engine = GameEngine(botCount = 2, powerUpFrequencyLevel = 1, listener = listener)
        val player = engine.player
        val bots = engine.blobs.filter { it.id != player.id }

        player.radius = 200f
        bots.forEach { it.radius = 20f; it.applyFreeze(1000f) }
        player.position = Vector2(1000f, 1000f)
        bots[0].position = Vector2(1000f, 1000f) // absorbed immediately
        bots[1].position = Vector2(50_000f, 50_000f) // far away, absorbed later on purpose

        engine.update(1f / 30f) // absorbs bots[0] - streak count 1, no callback yet

        // Let more time than COMBO_WINDOW_SECONDS pass with no further absorb.
        val idleStep = 1f
        var elapsed = 0f
        while (elapsed < GameConfig.COMBO_WINDOW_SECONDS + 1f) {
            player.applyPowerUp(PowerUpType.SHIELD) // don't let ambient deflation end the match mid-wait
            engine.update(idleStep)
            elapsed += idleStep
        }

        // Now bring the second bot into contact and let it get absorbed.
        bots[1].position = Vector2(player.position.x, player.position.y)
        engine.update(1f / 30f)

        assertEquals("A late second absorb should not have chained into a combo", 0, listener.comboCounts.size)
    }

    @Test
    fun `a three-way simultaneous absorb chains all the way to a x3 combo`() {
        val listener = TestGameListener()
        val engine = GameEngine(botCount = 3, powerUpFrequencyLevel = 1, listener = listener)
        val player = engine.player
        val bots = engine.blobs.filter { it.id != player.id }

        player.radius = 200f
        bots.forEach {
            it.radius = 20f
            it.applyFreeze(1000f)
            it.position = Vector2(1000f, 1000f)
        }
        player.position = Vector2(1000f, 1000f)

        engine.update(1f / 30f)

        // Three absorbs in the same frame: streak counts 1 (no callback), 2, 3.
        assertEquals(listOf(2, 3), listener.comboCounts)
    }

    @Test
    fun `combo streaks are tracked per absorber, not globally`() {
        val listener = TestGameListener()
        val engine = GameEngine(botCount = 3, powerUpFrequencyLevel = 1, listener = listener)
        val player = engine.player
        val bots = engine.blobs.filter { it.id != player.id }.sortedBy { it.id }

        // One bot big enough to absorb another bot; the player separately absorbs a third.
        // Neither should think it's continuing the other's streak.
        player.radius = 200f
        bots[0].radius = 100f // will absorb bots[1]
        bots[1].radius = 10f
        bots[2].radius = 20f // absorbed by the player

        bots.forEach { it.applyFreeze(1000f) }
        bots[0].position = Vector2(1000f, 1000f)
        bots[1].position = Vector2(1000f, 1000f)
        player.position = Vector2(5000f, 5000f)
        bots[2].position = Vector2(5000f, 5000f)

        engine.update(1f / 30f)

        assertFalse("Neither absorber's first kill should register as a combo on its own", listener.comboCounts.contains(2))
        assertEquals(0, listener.comboCounts.size)
    }
}
