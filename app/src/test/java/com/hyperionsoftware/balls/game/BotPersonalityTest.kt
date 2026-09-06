package com.hyperionsoftware.balls.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Covers BotPersonality: bots get a repeatable archetype tied to their balloon color (see
// GameEngine and BotPersonality.PALETTE), and each archetype's multipliers actually change
// BotBlob's decisions rather than just carrying a different label. BALANCED reproduces the
// original, personality-less numbers exactly - every other test file's bots are built without
// choosing a personality and implicitly rely on that.
class BotPersonalityTest {

    private fun clearThePlayer(engine: GameEngine) {
        engine.player.position = Vector2(engine.safeZoneCenterX, engine.safeZoneCenterY - engine.safeZoneRadius * 0.95f)
    }

    @Test
    fun `a bot created without choosing a personality defaults to BALANCED`() {
        val bot = BotBlob(id = 1, position = Vector2(0f, 0f), color = 0)
        assertEquals(BotPersonality.BALANCED, bot.personality)
    }

    @Test
    fun `GameEngine assigns each bot's color and personality from the same palette entry, cycling by spawn order`() {
        val palette = BotPersonality.PALETTE
        val botCount = palette.size * 2 + 1
        val engine = GameEngine(botCount = botCount, powerUpFrequencyLevel = 1, listener = TestGameListener())
        val bots = engine.blobs.filterIsInstance<BotBlob>().sortedBy { it.id }

        val expected = List(botCount) { palette[it % palette.size] }
        assertEquals(expected.map { it.second }, bots.map { it.personality })
        assertEquals(expected.map { it.first }, bots.map { it.color })
    }

    @Test
    fun `every personality has at least one color and every palette color resolves back to it`() {
        for (personality in BotPersonality.entries) {
            val colors = BotPersonality.colorsFor(personality)
            assertTrue("$personality should have at least one color in the palette", colors.isNotEmpty())
        }
        for ((color, personality) in BotPersonality.PALETTE) {
            assertTrue(
                "The color assigned to $personality should be listed under colorsFor($personality)",
                color in BotPersonality.colorsFor(personality)
            )
        }
    }

    @Test
    fun `a much closer permanent stat pickup pulls even a BALANCED bot off a prey chase`() {
        val engine = GameEngine(botCount = BotPersonality.entries.size, powerUpFrequencyLevel = 1, listener = TestGameListener())
        clearThePlayer(engine)
        val bots = engine.blobs.filterIsInstance<BotBlob>()
        val bot = bots.first { it.personality == BotPersonality.BALANCED }
        val prey = bots.first { it.personality == BotPersonality.HUNTER } // just borrowed as the prey target
        // Parked out of the way so their random spawn radius/position can't occasionally
        // register as an unplanned threat or prey within vision alongside the deliberately
        // placed prey above - a rare, hard-to-reproduce flake otherwise.
        val bystanders = bots.filter { it.personality == BotPersonality.CAUTIOUS || it.personality == BotPersonality.COLLECTOR }

        val cx = engine.safeZoneCenterX
        val cy = engine.safeZoneCenterY
        bot.radius = 40f
        prey.radius = 20f // qualifies as prey (ratio >= ABSORB_RATIO)
        bot.position = Vector2(cx, cy)
        prey.position = Vector2(cx, cy + 150f) // straight down
        bystanders.forEachIndexed { index, bystander -> bystander.position = Vector2(cx + 50_000f, cy + 50_000f + index * 500f) }
        engine.powerUps.clear()
        engine.powerUps.add(PowerUp(PowerUpType.POTENCY_UP, Vector2(cx + 50f, cy))) // straight right, much closer than the prey

        val direction = bot.decideDirection(engine, 1f / 30f)

        assertTrue("A much closer permanent upgrade should win out over chasing prey", direction.x > 0.9f)
    }

    @Test
    fun `a moderately closer plain pickup keeps a HUNTER chasing prey but distracts a COLLECTOR`() {
        val engine = GameEngine(botCount = BotPersonality.entries.size, powerUpFrequencyLevel = 1, listener = TestGameListener())
        clearThePlayer(engine)
        val bots = engine.blobs.filterIsInstance<BotBlob>()
        val hunter = bots.first { it.personality == BotPersonality.HUNTER }
        val collector = bots.first { it.personality == BotPersonality.COLLECTOR }
        val prey = bots.first { it.personality == BotPersonality.CAUTIOUS } // just borrowed as the prey target
        val bystander = bots.first { it.personality == BotPersonality.BALANCED }

        val cx = engine.safeZoneCenterX
        val cy = engine.safeZoneCenterY
        prey.radius = 20f
        prey.position = Vector2(cx, cy + 150f) // straight down from the shared subject position
        engine.powerUps.clear()
        engine.powerUps.add(PowerUp(PowerUpType.GROWTH, Vector2(cx + 120f, cy))) // straight right, closer but not by much
        // Otherwise its random spawn radius could occasionally land far enough from the
        // subject's own to register as an unplanned threat or prey within vision, on top of
        // (or instead of) the deliberately placed prey above - a rare, hard-to-reproduce flake.
        bystander.position = Vector2(cx + 50_000f, cy + 50_000f)

        hunter.radius = 40f
        hunter.position = Vector2(cx, cy)
        collector.radius = 40f
        collector.position = Vector2(cx + 50_000f, cy + 50_000f) // well outside hunter's vision for now

        val hunterDirection = hunter.decideDirection(engine, 1f / 30f)
        assertTrue("HUNTER should keep chasing prey rather than detour this far for a plain pickup", hunterDirection.y > 0.9f)

        hunter.position = Vector2(cx + 50_000f, cy + 50_000f) // out of the way now
        collector.position = Vector2(cx, cy)

        val collectorDirection = collector.decideDirection(engine, 1f / 30f)
        assertTrue("COLLECTOR should happily detour this far for a pickup", collectorDirection.x > 0.9f)
    }

    @Test
    fun `a CAUTIOUS bot panics - boosting and spending its carried item - at a distance a BALANCED bot merely flees from`() {
        val engine = GameEngine(botCount = BotPersonality.entries.size, powerUpFrequencyLevel = 1, listener = TestGameListener())
        clearThePlayer(engine)
        val bots = engine.blobs.filterIsInstance<BotBlob>()
        val balanced = bots.first { it.personality == BotPersonality.BALANCED }
        val cautious = bots.first { it.personality == BotPersonality.CAUTIOUS }
        val threat = bots.first { it.personality == BotPersonality.HUNTER } // just borrowed as the shared threat
        // Parked out of the way so its random spawn radius/position can't occasionally
        // register as an unplanned threat or prey within vision - a rare, hard-to-reproduce
        // flake otherwise.
        val bystander = bots.first { it.personality == BotPersonality.COLLECTOR }
        engine.powerUps.clear()

        val cx = engine.safeZoneCenterX
        val cy = engine.safeZoneCenterY
        bystander.position = Vector2(cx + 50_000f, cy + 50_000f)
        threat.radius = 100f
        threat.position = Vector2(cx + 240f, cy)

        // 240 sits between BALANCED's panic threshold ((40+100) * 1.5 = 210, so this is
        // already outside it) and CAUTIOUS's ((40+100) * 1.5 * 1.3 = 273, still inside it).
        balanced.radius = 40f
        balanced.position = Vector2(cx, cy)
        balanced.pickUpCarriedItem(PowerUpType.REPEL)
        val balancedDirection = balanced.decideDirection(engine, 1f / 30f)
        assertFalse("BALANCED shouldn't be panicking yet at this distance", balanced.isBoosting)
        assertEquals("BALANCED's carried item should still be unspent", PowerUpType.REPEL, balanced.carriedItem)
        assertTrue("BALANCED should still be fleeing the threat, just not panicking", balancedDirection.x < -0.9f)

        cautious.radius = 40f
        cautious.position = Vector2(cx, cy)
        cautious.pickUpCarriedItem(PowerUpType.REPEL)
        val cautiousDirection = cautious.decideDirection(engine, 1f / 30f)
        assertTrue("CAUTIOUS should already be panicking at the same distance", cautious.isBoosting)
        assertNull("CAUTIOUS should have spent its carried item to survive", cautious.carriedItem)
        assertTrue(cautiousDirection.x < -0.9f)
    }
}
