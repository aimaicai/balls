package com.hyperionsoftware.balls.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Covers the shrinking safe zone: the normal phase's staged hold/shrink cycle, the trigger
// into the final round, and the final round's own indefinite shrink - including the
// SAFE_ZONE_MIN_RADIUS / SAFE_ZONE_FINAL_START_RADIUS decoupling fixed earlier (see
// GameConfig's comments on both constants).
class SafeZoneTest {

    private fun newEngine(skipToFinalRound: Boolean = false) =
        GameEngine(botCount = 1, powerUpFrequencyLevel = 1, skipToFinalRound = skipToFinalRound, listener = TestGameListener())

    // Every blob needs to stay alive for these tests to keep advancing matchElapsed at all
    // (GameEngine.update becomes a no-op once the match ends). Shielding everyone on every
    // step blocks the ambient leak regardless of position or how far the zone has shrunk -
    // but shield does NOT block absorption, only deflation, so that alone isn't enough: bots
    // spawn with a randomized starting size (see BOT_START_SIZE_MIN/MAX_FACTOR) and actively
    // hunt anything smaller, including a player left standing still for tens of simulated
    // seconds here. Forcing every alive blob back to the same fixed radius every single step
    // (undoing any GROWTH pickups too, not just the spawn-time randomization) keeps the
    // absorb ratio pinned at exactly 1 the whole time, so neither side can ever be eligible
    // to absorb the other no matter how the random bot AI plays out.
    private fun advance(engine: GameEngine, totalSeconds: Float, step: Float = 1f / 30f) {
        var remaining = totalSeconds
        while (remaining > 0.0001f) {
            for (blob in engine.blobs) {
                if (blob.alive) {
                    blob.applyPowerUp(PowerUpType.SHIELD)
                    blob.radius = GameConfig.BASE_RADIUS
                }
            }
            val dt = minOf(step, remaining)
            engine.update(dt)
            remaining -= dt
        }
    }

    private fun totalStagedDuration() =
        GameConfig.SAFE_ZONE_STAGE_COUNT * (GameConfig.SAFE_ZONE_STAGE_HOLD_SECONDS + GameConfig.SAFE_ZONE_STAGE_SHRINK_SECONDS)

    @Test
    fun `the zone starts at its full initial radius and holds there`() {
        val engine = newEngine()
        assertEquals(GameConfig.SAFE_ZONE_INITIAL_RADIUS, engine.safeZoneRadius, 0.01f)
        assertTrue(engine.isZoneHolding)
    }

    @Test
    fun `after the hold phase, the zone shrinks toward the previewed next-stage target`() {
        val engine = newEngine()
        val nextTarget = engine.nextSafeZoneRadius
        advance(engine, GameConfig.SAFE_ZONE_STAGE_HOLD_SECONDS + GameConfig.SAFE_ZONE_STAGE_SHRINK_SECONDS)
        assertEquals(nextTarget, engine.safeZoneRadius, 1f)
    }

    @Test
    fun `the staged shrink reaches exactly SAFE_ZONE_MIN_RADIUS after every stage completes`() {
        val engine = newEngine()
        // The last stage's own shrink rate is steep enough that even a modest time buffer
        // before the boundary leaves a non-trivial gap from SAFE_ZONE_MIN_RADIUS - stopping
        // just short of it (rather than landing exactly on/past it, which could tip over into
        // triggering the final round within the same update() call - see the trigger test
        // below) keeps this both accurate and clear of that transition.
        advance(engine, totalStagedDuration() - 0.01f)
        assertEquals(GameConfig.SAFE_ZONE_MIN_RADIUS, engine.safeZoneRadius, 1f)
    }

    @Test
    fun `secondsUntilFinalRound counts down and hits zero once the final round triggers`() {
        val engine = newEngine()
        assertEquals(totalStagedDuration(), engine.secondsUntilFinalRound, 0.01f)

        advance(engine, totalStagedDuration() + 1f)

        assertEquals(0f, engine.secondsUntilFinalRound, 0.01f)
        assertTrue(engine.isFinalRoundActive)
    }

    @Test
    fun `finishing the staged shrink fires onFinalRoundStarted exactly once`() {
        val listener = TestGameListener()
        val engine = GameEngine(botCount = 1, powerUpFrequencyLevel = 1, listener = listener)
        advance(engine, totalStagedDuration() + 2f)
        assertEquals(1, listener.finalRoundStartedCount)
    }

    @Test
    fun `the final round starts at SAFE_ZONE_FINAL_START_RADIUS, independent of SAFE_ZONE_MIN_RADIUS`() {
        val engine = newEngine(skipToFinalRound = true)
        assertEquals(GameConfig.SAFE_ZONE_FINAL_START_RADIUS, engine.safeZoneRadius, 0.01f)
        assertTrue(engine.isFinalRoundActive)
    }

    @Test
    fun `SAFE_ZONE_MIN_RADIUS and SAFE_ZONE_FINAL_START_RADIUS are intentionally decoupled`() {
        // Regression guard: these two used to be the same constant, so halving the normal
        // phase's floor also shrank the final round's starting size and could even invert its
        // shrink direction (if SAFE_ZONE_FINAL_MIN_RADIUS ended up bigger than the new,
        // smaller floor). They must stay independent constants going forward.
        assertTrue(GameConfig.SAFE_ZONE_FINAL_START_RADIUS > GameConfig.SAFE_ZONE_MIN_RADIUS)
        assertTrue(GameConfig.SAFE_ZONE_FINAL_START_RADIUS > GameConfig.SAFE_ZONE_FINAL_MIN_RADIUS)
    }

    @Test
    fun `the final round shrinks at the constant rate implied by its own constants`() {
        val engine = newEngine(skipToFinalRound = true)
        advance(engine, 1f)
        val shrinkRate = (GameConfig.SAFE_ZONE_FINAL_START_RADIUS - GameConfig.SAFE_ZONE_FINAL_MIN_RADIUS) /
            GameConfig.SAFE_ZONE_FINAL_SHRINK_SECONDS
        assertEquals(GameConfig.SAFE_ZONE_FINAL_START_RADIUS - shrinkRate, engine.safeZoneRadius, 1f)
    }

    @Test
    fun `the final round keeps shrinking past SAFE_ZONE_FINAL_MIN_RADIUS instead of holding there`() {
        val engine = newEngine(skipToFinalRound = true)
        advance(engine, GameConfig.SAFE_ZONE_FINAL_SHRINK_SECONDS + 2f)
        assertTrue(
            "Expected the zone to have shrunk past its old fixed floor by now",
            engine.safeZoneRadius < GameConfig.SAFE_ZONE_FINAL_MIN_RADIUS
        )
    }

    @Test
    fun `the final round shrink is clamped at SAFE_ZONE_ABSOLUTE_MIN_RADIUS, never going negative`() {
        val engine = newEngine(skipToFinalRound = true)
        // Comfortably past the point where the raw linear formula would go negative -
        // using a bigger step since this is purely a formula check, not a physics one.
        advance(engine, 200f, step = 2f)
        assertEquals(GameConfig.SAFE_ZONE_ABSOLUTE_MIN_RADIUS, engine.safeZoneRadius, 0.01f)
        assertFalse(engine.safeZoneRadius < 0f)
    }
}
