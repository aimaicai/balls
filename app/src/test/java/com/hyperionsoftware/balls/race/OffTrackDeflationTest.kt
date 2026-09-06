package com.hyperionsoftware.balls.race

import com.hyperionsoftware.balls.game.Vector2
import org.junit.Assert.assertTrue
import org.junit.Test

// Covers the off-track penalty the user asked for: drifting slightly off the track's edge is
// tolerated (see RaceConfig.OFF_TRACK_MARGIN), but being properly off it deflates faster than
// staying on it - the same two-rate principle as classic mode's safe zone, just on/off the
// track surface instead of in/out of a shrinking circle.
class OffTrackDeflationTest {

    private fun newEngine() = RaceEngine(
        botCount = 1,
        track = RaceTrack.OVAL,
        totalLaps = 3,
        listener = TestRaceListener()
    ).also { engine ->
        // Freeze the one bot somewhere it can't collide with or otherwise disturb the player
        // being tracked below - far enough from BOTH pinned test positions (checkpoints[0]
        // and the world corner the off-track scenario's player clamps to) that it can never
        // be within collision range of either. A closer spot here was an intermittent flake:
        // an unlucky bounce could push the off-track player away from the exact corner it's
        // pinned to every tick, occasionally breaking the strict less-than assertion below.
        val bot = engine.blobs[1]
        bot.position = Vector2(-50000f, -50000f)
        bot.applyFreeze(9999f)
    }

    private fun pinnedAdvance(engine: RaceEngine, position: Vector2, ticks: Int, dt: Float = 1f / 30f) {
        repeat(ticks) {
            engine.player.position = Vector2(position.x, position.y)
            engine.update(dt)
        }
    }

    @Test
    fun `a position on the track surface is not considered off-track`() {
        val onTrackSpot = RaceTrack.OVAL.checkpoints[0]
        assertTrue(RaceTrack.OVAL.distanceOffTrack(onTrackSpot) <= 0f)
    }

    @Test
    fun `a position far past the track's edge is considered off-track`() {
        val farOff = Vector2(-5000f, -5000f)
        assertTrue(RaceTrack.OVAL.distanceOffTrack(farOff) > RaceConfig.OFF_TRACK_MARGIN)
    }

    @Test
    fun `staying off-track deflates the balloon faster than staying on-track`() {
        val onTrackEngine = newEngine()
        pinnedAdvance(onTrackEngine, RaceTrack.OVAL.checkpoints[0], ticks = 30)
        val onTrackRadius = onTrackEngine.player.radius

        val offTrackEngine = newEngine()
        pinnedAdvance(offTrackEngine, Vector2(-5000f, -5000f), ticks = 30)
        val offTrackRadius = offTrackEngine.player.radius

        assertTrue(
            "Expected staying off-track for the same duration to shrink the balloon more " +
                "(on-track ended at $onTrackRadius, off-track ended at $offTrackRadius)",
            offTrackRadius < onTrackRadius
        )
    }

    @Test
    fun `a slight drift just past the track edge, within the tolerated margin, still counts as on-track`() {
        // A point that's off the polyline itself but within OFF_TRACK_MARGIN of the edge -
        // exactly the "si può uscire leggermente" case the user asked for.
        val edge = RaceTrack.OVAL.checkpoints[0]
        val nudgedJustPastEdge = Vector2(edge.x, edge.y - RaceTrack.OVAL.halfWidth - RaceConfig.OFF_TRACK_MARGIN * 0.5f)
        val distanceOff = RaceTrack.OVAL.distanceOffTrack(nudgedJustPastEdge)
        assertTrue(distanceOff > 0f)
        assertTrue(distanceOff <= RaceConfig.OFF_TRACK_MARGIN)
    }
}
