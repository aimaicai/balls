package com.hyperionsoftware.balls.race

import com.hyperionsoftware.balls.game.PowerUpType
import com.hyperionsoftware.balls.game.Vector2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Covers RaceEngine.updateRaceProgress: free-roaming, checkpoint-free lap progress (just
// driving the track counts, no waypoint has to be touched), the anti-shortcut clamp that
// bounds how much of it a cut across the infield can ever earn, lap counting, and the
// finish-line win condition after the configured number of laps.
class RaceProgressTest {

    private fun newEngine(track: RaceTrack = RaceTrack.OVAL, totalLaps: Int = 1, listener: TestRaceListener = TestRaceListener()) =
        RaceEngine(botCount = 1, track = track, totalLaps = totalLaps, listener = listener).also { engine ->
            // Freeze the one bot far out of the way so it can't interfere with (or race ahead
            // of) the player during these player-progress assertions.
            val bot = engine.blobs[1]
            bot.position = Vector2(50f, 50f)
            bot.applyFreeze(9999f)
        }

    // Steers the player toward its own sliding lookahead point (see RaceTrack.
    // pointAtArcLength), exactly the technique RaceBotBlob itself uses - an autopilot that
    // drives the track under its own power, the same way a real player following it would,
    // rather than teleporting between fixed waypoints. Kept shielded throughout so a test
    // driving a full lap (tens of real seconds of simulated time) never risks dying to
    // ordinary ambient deflation - that mechanic is covered separately by
    // OffTrackDeflationTest, not the point of these tests.
    private fun driveLaps(engine: RaceEngine, track: RaceTrack, ticks: Int, dt: Float = 1f / 30f) {
        repeat(ticks) {
            // Shields every blob, not just the player - the frozen bot sitting off-track at
            // (50, 50) would otherwise ambient-deflate to death over these many ticks
            // (freeze blocks its own movement, not the ambient leak), dropping aliveCount to
            // 1 and ending the race by elimination before the player ever finishes a lap.
            for (blob in engine.blobs) blob.applyPowerUp(PowerUpType.SHIELD)
            val lookahead = track.pointAtArcLength(engine.player.trackArcPosition + RaceConfig.LOOKAHEAD_DISTANCE)
            engine.player.inputDirection = (lookahead - engine.player.position).normalized()
            engine.update(dt)
        }
    }

    // Generous margin over the track's own total length divided by the player's base speed -
    // covers steering imprecision around corners without the tests being fragile to exact
    // timing.
    private fun ticksForLaps(track: RaceTrack, laps: Int): Int =
        ((track.totalLength * laps / RaceConfig.PLAYER_BASE_SPEED) * 30f * 1.6f).toInt()

    @Test
    fun `driving forward advances lap distance with no checkpoint to touch`() {
        val engine = newEngine()
        assertEquals(0f, engine.player.lapDistanceTraveled, 0.01f)
        driveLaps(engine, RaceTrack.OVAL, ticks = 60)
        assertTrue(
            "Expected simply driving forward to advance lap progress on its own",
            engine.player.lapDistanceTraveled > 0f
        )
        assertEquals(0, engine.player.lapsCompleted)
    }

    @Test
    fun `a shortcut across the track only ever earns a small, physically-bounded amount of progress`() {
        val engine = newEngine()
        // A clean, known starting state: right at the start/finish line, no progress yet.
        engine.player.position = Vector2(RaceTrack.OVAL.checkpoints[0].x, RaceTrack.OVAL.checkpoints[0].y)
        engine.player.trackArcPosition = 0f
        engine.player.lapDistanceTraveled = 0f
        engine.player.inputDirection = Vector2(0f, 0f)

        // A single tick's real movement is small (effectiveSpeed * dt) - teleporting the
        // rest of the way across the track to checkpoint 5 simulates cutting straight
        // through the infield rather than driving the corridor there.
        engine.player.position = Vector2(RaceTrack.OVAL.checkpoints[5].x, RaceTrack.OVAL.checkpoints[5].y)
        engine.update(1f / 30f)

        val maxPlausibleCredit = (RaceConfig.PLAYER_BASE_SPEED * (1f / 30f) + 1f) * RaceConfig.ARC_PROGRESS_SLACK_FACTOR
        assertTrue(
            "Expected the shortcut to earn at most what a single tick's real movement could " +
                "plausibly justify (got ${engine.player.lapDistanceTraveled}, cap $maxPlausibleCredit)",
            engine.player.lapDistanceTraveled <= maxPlausibleCredit
        )
    }

    @Test
    fun `driving all the way around the track completes exactly one lap`() {
        val engine = newEngine(totalLaps = 5)
        driveLaps(engine, RaceTrack.OVAL, ticks = ticksForLaps(RaceTrack.OVAL, 1))
        assertEquals(1, engine.player.lapsCompleted)
    }

    @Test
    fun `completing the configured number of laps wins the race by the finish line`() {
        val listener = TestRaceListener()
        val engine = newEngine(totalLaps = 2, listener = listener)

        driveLaps(engine, RaceTrack.OVAL, ticks = ticksForLaps(RaceTrack.OVAL, 1))
        assertEquals(1, engine.player.lapsCompleted)
        assertEquals(0, listener.raceOverCount)

        driveLaps(engine, RaceTrack.OVAL, ticks = ticksForLaps(RaceTrack.OVAL, 1))
        assertEquals(2, engine.player.lapsCompleted)
        assertEquals(1, listener.raceOverCount)
        assertEquals(true, listener.lastPlayerWon)
        assertEquals(RaceEndReason.FINISH_LINE, listener.lastReason)
        assertEquals(listOf(1, 2), listener.lapsCompletedByPlayer)
    }

    @Test
    fun `the race stops updating once it's over`() {
        val listener = TestRaceListener()
        val engine = newEngine(totalLaps = 1, listener = listener)
        driveLaps(engine, RaceTrack.OVAL, ticks = ticksForLaps(RaceTrack.OVAL, 1))

        assertEquals(1, listener.raceOverCount)
        val elapsedAtFinish = engine.matchElapsed
        engine.update(1f)
        assertEquals(elapsedAtFinish, engine.matchElapsed, 0.0001f)
        assertEquals("onRaceOver must not fire more than once", 1, listener.raceOverCount)
    }
}
