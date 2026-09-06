package com.hyperionsoftware.balls.race

import com.hyperionsoftware.balls.game.Vector2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Covers RaceTrack's pure geometry: on/off-track distance, and the figure-eight's deliberate
// shared crossing point (see RaceTrack.FIGURE_EIGHT's comment).
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
}
