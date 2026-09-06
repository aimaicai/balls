package com.hyperionsoftware.balls.race

import com.hyperionsoftware.balls.game.Vector2
import org.junit.Assert.assertEquals
import org.junit.Test

// Covers RaceEngine.updateRaceProgress: strictly sequential checkpoint advancement (the anti-
// shortcut guarantee - reaching a checkpoint out of order doesn't count), lap counting, and
// the finish-line win condition after the configured number of laps.
class RaceProgressTest {

    private fun newEngine(track: RaceTrack = RaceTrack.OVAL, totalLaps: Int = 1, listener: TestRaceListener = TestRaceListener()) =
        RaceEngine(botCount = 1, track = track, totalLaps = totalLaps, listener = listener).also { engine ->
            // Freeze the one bot far out of the way so it can't interfere with (or race ahead
            // of) the player during these player-progress assertions.
            val bot = engine.blobs[1]
            bot.position = Vector2(50f, 50f)
            bot.applyFreeze(9999f)
        }

    private fun tick(engine: RaceEngine, position: Vector2, dt: Float = 1f / 30f) {
        engine.player.position = Vector2(position.x, position.y)
        engine.update(dt)
    }

    @Test
    fun `reaching the next checkpoint in order advances nextCheckpointIndex`() {
        val engine = newEngine()
        assertEquals(1, engine.player.nextCheckpointIndex)
        tick(engine, RaceTrack.OVAL.checkpoints[1])
        assertEquals(2, engine.player.nextCheckpointIndex)
    }

    @Test
    fun `teleporting to a far-ahead checkpoint out of order does not advance progress`() {
        val engine = newEngine()
        // Standing right on checkpoint 5 while still needing checkpoint 1 - simulates cutting
        // across the infield to skip most of the lap.
        tick(engine, RaceTrack.OVAL.checkpoints[5])
        assertEquals(
            "Reaching a checkpoint out of sequence must not count toward progress",
            1,
            engine.player.nextCheckpointIndex
        )
        assertEquals(0, engine.player.lapsCompleted)
    }

    @Test
    fun `visiting every checkpoint in order completes a lap`() {
        val engine = newEngine(totalLaps = 5)
        val order = (1 until RaceTrack.OVAL.checkpoints.size) + listOf(0)
        for (index in order) {
            tick(engine, RaceTrack.OVAL.checkpoints[index])
        }
        assertEquals(1, engine.player.lapsCompleted)
        assertEquals(1, engine.player.nextCheckpointIndex)
    }

    @Test
    fun `completing the configured number of laps wins the race by the finish line`() {
        val listener = TestRaceListener()
        val engine = newEngine(totalLaps = 2, listener = listener)
        val lapOrder = (1 until RaceTrack.OVAL.checkpoints.size) + listOf(0)

        for (index in lapOrder) tick(engine, RaceTrack.OVAL.checkpoints[index])
        assertEquals(1, engine.player.lapsCompleted)
        assertEquals(0, listener.raceOverCount)

        for (index in lapOrder) tick(engine, RaceTrack.OVAL.checkpoints[index])
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
        val lapOrder = (1 until RaceTrack.OVAL.checkpoints.size) + listOf(0)
        for (index in lapOrder) tick(engine, RaceTrack.OVAL.checkpoints[index])

        assertEquals(1, listener.raceOverCount)
        val elapsedAtFinish = engine.matchElapsed
        engine.update(1f)
        assertEquals(elapsedAtFinish, engine.matchElapsed, 0.0001f)
        assertEquals("onRaceOver must not fire more than once", 1, listener.raceOverCount)
    }
}
