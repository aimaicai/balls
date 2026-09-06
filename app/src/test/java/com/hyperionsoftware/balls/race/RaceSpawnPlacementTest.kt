package com.hyperionsoftware.balls.race

import org.junit.Assert.assertTrue
import org.junit.Test

// Covers the starting grid: with a full field of racers, nobody should spawn overlapping
// another blob's body (unlike classic mode's open-arena rejection sampling, the grid here is
// a fixed staggered layout behind the start/finish line - see RaceEngine.startGridPosition).
class RaceSpawnPlacementTest {

    @Test
    fun `no two blobs spawn close enough to overlap, at max bot count, on either track`() {
        for (track in RaceTrack.values()) {
            val engine = RaceEngine(
                botCount = RaceConfig.MAX_BOT_COUNT,
                track = track,
                totalLaps = 3,
                listener = TestRaceListener()
            )
            val maxPossibleRadiusSum = RaceConfig.BASE_RADIUS * RaceConfig.BOT_START_SIZE_MAX_FACTOR * 2f
            for (i in engine.blobs.indices) {
                for (j in i + 1 until engine.blobs.size) {
                    val a = engine.blobs[i]
                    val b = engine.blobs[j]
                    val distance = a.position.distanceTo(b.position)
                    assertTrue(
                        "${track.name}: blobs $i and $j spawned $distance apart, " +
                            "closer than the largest possible combined radius $maxPossibleRadiusSum",
                        distance >= maxPossibleRadiusSum
                    )
                }
            }
        }
    }

    @Test
    fun `every blob spawns inside the world bounds`() {
        val engine = RaceEngine(
            botCount = RaceConfig.MAX_BOT_COUNT,
            track = RaceTrack.OVAL,
            totalLaps = 3,
            listener = TestRaceListener()
        )
        for (blob in engine.blobs) {
            assertTrue(blob.position.x in 0f..RaceConfig.WORLD_WIDTH)
            assertTrue(blob.position.y in 0f..RaceConfig.WORLD_HEIGHT)
        }
    }
}
