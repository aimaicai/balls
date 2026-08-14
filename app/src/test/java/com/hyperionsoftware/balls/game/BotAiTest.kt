package com.hyperionsoftware.balls.game

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Covers BotBlob's final-round-specific behavior fixed earlier: a tighter, no-sprint zone
// margin (the final round's zone shrinks continuously and fast, unlike the normal phase's
// staged shrink, and turning has inertia - see Blob.steerTowards), and treating GROWTH/
// AGILITY_UP as a standing priority there instead of only chasing GROWTH once already low.
class BotAiTest {

    // triggerFinalRound() (run during construction when skipToFinalRound=true) places every
    // survivor 90% of the way to the zone edge at evenly spaced angles - moving the player
    // well clear of wherever a test then places the bot keeps it from being mistaken for
    // prey/a threat and confusing the assertions below.
    private fun clearThePlayer(engine: GameEngine) {
        engine.player.position = Vector2(engine.safeZoneCenterX, engine.safeZoneCenterY - engine.safeZoneRadius * 0.95f)
    }

    @Test
    fun `in the final round, a bot outside the tighter margin heads back toward the zone center`() {
        val engine = GameEngine(botCount = 1, powerUpFrequencyLevel = 1, skipToFinalRound = true, listener = TestGameListener())
        val bot = engine.blobs.first { it.id != engine.player.id }
        bot.radius = 100f
        clearThePlayer(engine)

        val cx = engine.safeZoneCenterX
        val cy = engine.safeZoneCenterY
        val r = engine.safeZoneRadius
        // 75% of the zone radius: inside the NORMAL-phase margin (0.85, wouldn't react
        // before), but outside the tighter final-round margin (0.65) - should now head back.
        bot.position = Vector2(cx + r * 0.75f, cy)
        val startX = bot.position.x

        repeat(3 * 30) { engine.update(1f / 30f) }

        assertTrue("Bot should be heading back toward the zone center", bot.position.x < startX)
    }

    @Test
    fun `in the normal phase, the same 75 percent position does not trigger a return yet`() {
        val engine = GameEngine(botCount = 1, powerUpFrequencyLevel = 1, listener = TestGameListener())
        val bot = engine.blobs.first { it.id != engine.player.id }
        bot.radius = 100f
        clearThePlayer(engine)
        // Isolate this to the zone-margin decision alone - otherwise the generic "go to the
        // nearest power-up" fallback could pick something whose direction happens to land
        // close to "toward center" purely by chance, since a few power-ups spawn immediately
        // in the normal phase.
        engine.powerUps.clear()

        val cx = engine.safeZoneCenterX
        val cy = engine.safeZoneCenterY
        val r = engine.safeZoneRadius
        bot.position = Vector2(cx + r * 0.75f, cy)
        val direction = bot.decideDirection(engine, 1f / 30f)

        // 0.75 is inside BOTH the normal (0.85) and final-round (0.65) margins in absolute
        // terms, but this specifically checks the normal-phase margin wasn't accidentally
        // tightened too - the bot should not be steering back toward center yet. The bot sits
        // directly right of center, so "returning to center" would be exactly (-1, 0); an
        // exact-value check (rather than a coarse angular threshold) keeps this from ever
        // being confused with the bot's own (fixed but arbitrary) wander direction.
        val isReturningToCenter = kotlin.math.abs(direction.x - (-1f)) < 0.0001f &&
            kotlin.math.abs(direction.y) < 0.0001f
        assertFalse("Bot should not yet be treating 75% as outside the normal-phase margin", isReturningToCenter)
    }

    @Test
    fun `in the final round, a bot proactively returning to the zone does not sprint`() {
        val engine = GameEngine(botCount = 1, powerUpFrequencyLevel = 1, skipToFinalRound = true, listener = TestGameListener())
        val bot = engine.blobs.first { it.id != engine.player.id }
        bot.radius = 100f
        clearThePlayer(engine)

        val cx = engine.safeZoneCenterX
        val cy = engine.safeZoneCenterY
        val r = engine.safeZoneRadius
        bot.position = Vector2(cx + r * 0.75f, cy)

        bot.decideDirection(engine, 1f / 30f)

        assertFalse(
            "Heading back proactively (not yet an emergency) should not spend size sprinting",
            bot.isBoosting
        )
    }

    @Test
    fun `in the final round, a bot beelines for a nearby GROWTH pickup even at a comfortable size`() {
        val engine = GameEngine(botCount = 1, powerUpFrequencyLevel = 1, skipToFinalRound = true, listener = TestGameListener())
        val bot = engine.blobs.first { it.id != engine.player.id }
        // Comfortably above BOT_LOW_SIZE_FRACTION * baseRadius - would NOT have chased
        // GROWTH under the normal phase's "only when running low" gate.
        bot.radius = 120f
        clearThePlayer(engine)

        val cx = engine.safeZoneCenterX
        val cy = engine.safeZoneCenterY
        bot.position = Vector2(cx, cy)
        engine.powerUps.clear()
        engine.powerUps.add(PowerUp(PowerUpType.GROWTH, Vector2(cx + 150f, cy)))

        val direction = bot.decideDirection(engine, 1f / 30f)

        assertTrue("Bot should steer toward the GROWTH pickup", direction.x > 0.9f)
    }

    @Test
    fun `in the final round, a bot beelines for a nearby AGILITY_UP pickup the same way`() {
        val engine = GameEngine(botCount = 1, powerUpFrequencyLevel = 1, skipToFinalRound = true, listener = TestGameListener())
        val bot = engine.blobs.first { it.id != engine.player.id }
        bot.radius = 120f
        clearThePlayer(engine)

        val cx = engine.safeZoneCenterX
        val cy = engine.safeZoneCenterY
        bot.position = Vector2(cx, cy)
        engine.powerUps.clear()
        engine.powerUps.add(PowerUp(PowerUpType.AGILITY_UP, Vector2(cx + 150f, cy)))

        val direction = bot.decideDirection(engine, 1f / 30f)

        assertTrue("Bot should steer toward the AGILITY_UP pickup", direction.x > 0.9f)
    }

    // The generic "go to the nearest power-up if nothing else is going on" fallback that
    // already existed before the final-round-specific change applies in BOTH phases, so a
    // GROWTH pickup with nothing else around gets chased either way - that alone doesn't
    // distinguish the two phases. The actual behavioral difference is PRIORITY: putting a
    // prey blob and a GROWTH pickup in different directions at once shows the final round
    // choosing GROWTH first (ahead of prey), while the normal phase (bot not low on size)
    // chases the prey first, only falling back to the generic power-up pass afterward.
    @Test
    fun `the final round prioritizes a GROWTH pickup over chasing visible prey`() {
        val engine = GameEngine(botCount = 2, powerUpFrequencyLevel = 1, skipToFinalRound = true, listener = TestGameListener())
        clearThePlayer(engine)
        val (bot, prey) = engine.blobs.filter { it.id != engine.player.id }

        bot.radius = 120f // comfortable, not "low" by BOT_LOW_SIZE_FRACTION
        prey.radius = 50f // small enough to qualify as prey (ratio >= ABSORB_RATIO)
        val cx = engine.safeZoneCenterX
        val cy = engine.safeZoneCenterY
        bot.position = Vector2(cx, cy)
        prey.position = Vector2(cx, cy + 150f) // straight down - would steer toward +y if chased
        engine.powerUps.clear()
        engine.powerUps.add(PowerUp(PowerUpType.GROWTH, Vector2(cx + 150f, cy))) // straight right

        val direction = bot.decideDirection(engine, 1f / 30f)

        assertTrue("Final round should prioritize GROWTH over chasing visible prey", direction.x > 0.9f)
    }

    @Test
    fun `the normal phase still prioritizes visible prey over a GROWTH pickup at a comfortable size`() {
        val engine = GameEngine(botCount = 2, powerUpFrequencyLevel = 1, listener = TestGameListener())
        clearThePlayer(engine)
        val (bot, prey) = engine.blobs.filter { it.id != engine.player.id }

        bot.radius = 120f
        prey.radius = 50f
        val cx = engine.safeZoneCenterX
        val cy = engine.safeZoneCenterY
        bot.position = Vector2(cx, cy)
        prey.position = Vector2(cx, cy + 150f)
        engine.powerUps.clear()
        engine.powerUps.add(PowerUp(PowerUpType.GROWTH, Vector2(cx + 150f, cy)))

        val direction = bot.decideDirection(engine, 1f / 30f)

        assertTrue("Normal phase should still chase visible prey ahead of a GROWTH pickup", direction.y > 0.9f)
    }
}
