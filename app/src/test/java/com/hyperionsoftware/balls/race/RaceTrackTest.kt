package com.hyperionsoftware.balls.race

import com.hyperionsoftware.balls.game.Vector2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Covers RaceTrack's pure geometry: on/off-track distance, the figure-eight's deliberate
// shared crossing point (see RaceTrack.FIGURE_EIGHT's comment), and the arc-length model that
// makes lap progress checkpoint-free (see RaceEngine.updateRaceProgress).
class RaceTrackTest {

    @Test
    fun `standing exactly on a checkpoint is on the track surface`() {
        for (track in RaceTrack.values()) {
            for (checkpoint in track.checkpoints) {
                assertTrue(
                    "${track.name} checkpoint $checkpoint should be on-track",
                    track.distanceOffTrack(checkpoint) <= 0f
                )
            }
        }
    }

    @Test
    fun `a point far from every segment is reported well off-track`() {
        // Comfortably outside any checkpoint's halfWidth for either track (both under 400),
        // and far from every segment of either loop.
        val farAway = Vector2(-5000f, -5000f)
        for (track in RaceTrack.values()) {
            assertTrue(track.distanceOffTrack(farAway) > RaceConfig.OFF_TRACK_MARGIN)
        }
    }

    @Test
    fun `a point squarely at the midpoint between two checkpoints is on-track`() {
        for (track in RaceTrack.values()) {
            val a = track.checkpoints[0]
            val b = track.checkpoints[1]
            val midpoint = Vector2((a.x + b.x) / 2f, (a.y + b.y) / 2f)
            assertTrue(track.distanceOffTrack(midpoint) <= 0f)
        }
    }

    @Test
    fun `FIGURE_EIGHT's two halves share the exact same crossing coordinate`() {
        val checkpoints = RaceTrack.FIGURE_EIGHT.checkpoints
        assertEquals(checkpoints[2], checkpoints[6])
    }

    @Test
    fun `the crossing point of FIGURE_EIGHT is on-track`() {
        val crossing = RaceTrack.FIGURE_EIGHT.checkpoints[2]
        assertTrue(RaceTrack.FIGURE_EIGHT.distanceOffTrack(crossing) <= 0f)
    }

    @Test
    fun `totalLength equals the sum of every segment, including the last back to the first`() {
        for (track in RaceTrack.values()) {
            var expected = 0f
            for (i in track.checkpoints.indices) {
                expected += track.checkpoints[i].distanceTo(track.checkpoints[(i + 1) % track.checkpoints.size])
            }
            assertEquals(expected, track.totalLength, 0.5f)
        }
    }

    @Test
    fun `closestArcLength is zero right at checkpoint 0 and grows toward checkpoint 1`() {
        for (track in RaceTrack.values()) {
            assertEquals(0f, track.closestArcLength(track.checkpoints[0]), 0.5f)
            val expectedAtCheckpoint1 = track.checkpoints[0].distanceTo(track.checkpoints[1])
            assertEquals(expectedAtCheckpoint1, track.closestArcLength(track.checkpoints[1]), 0.5f)
        }
    }

    @Test
    fun `closestArcLength on the final segment approaches totalLength, not zero`() {
        for (track in RaceTrack.values()) {
            val last = track.checkpoints.size - 1
            val midOfLastSegment = Vector2(
                (track.checkpoints[last].x + track.checkpoints[0].x) / 2f,
                (track.checkpoints[last].y + track.checkpoints[0].y) / 2f
            )
            val arc = track.closestArcLength(midOfLastSegment)
            assertTrue(
                "${track.name}: expected the last segment's midpoint to read as most of the way " +
                    "around (arc=$arc, totalLength=${track.totalLength}), not back near zero",
                arc > track.totalLength * 0.75f
            )
        }
    }

    @Test
    fun `pointAtArcLength(0) is checkpoint 0, and it wraps at totalLength`() {
        for (track in RaceTrack.values()) {
            val atZero = track.pointAtArcLength(0f)
            assertEquals(track.checkpoints[0].x, atZero.x, 0.5f)
            assertEquals(track.checkpoints[0].y, atZero.y, 0.5f)

            val atFullLoop = track.pointAtArcLength(track.totalLength)
            assertEquals(track.checkpoints[0].x, atFullLoop.x, 0.5f)
            assertEquals(track.checkpoints[0].y, atFullLoop.y, 0.5f)

            val atOneLoopPast = track.pointAtArcLength(track.totalLength + 123f)
            val atOffsetAlone = track.pointAtArcLength(123f)
            assertEquals(atOffsetAlone.x, atOneLoopPast.x, 0.5f)
            assertEquals(atOffsetAlone.y, atOneLoopPast.y, 0.5f)
        }
    }

    @Test
    fun `pointAtArcLength and closestArcLength round-trip at each checkpoint`() {
        for (track in RaceTrack.values()) {
            for (checkpoint in track.checkpoints) {
                val arc = track.closestArcLength(checkpoint)
                val roundTripped = track.pointAtArcLength(arc)
                assertEquals(checkpoint.x, roundTripped.x, 1f)
                assertEquals(checkpoint.y, roundTripped.y, 1f)
            }
        }
    }
}
