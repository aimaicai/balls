package com.hyperionsoftware.balls.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Covers GameEngine.randomSpawnPosition: no two blobs (bots, or a bot against the player's
// fixed center-of-map start) should spawn close enough to bounce or absorb before the match
// has properly begun.
class SpawnPlacementTest {

    @Test
    fun `no two blobs spawn closer than MIN_SPAWN_SEPARATION, including the player`() {
        val engine = GameEngine(botCount = 40, powerUpFrequencyLevel = 1, listener = TestGameListener())
        val blobs = engine.blobs

        for (i in blobs.indices) {
            for (j in i + 1 until blobs.size) {
                val distance = blobs[i].position.distanceTo(blobs[j].position)
                assertTrue(
                    "Blobs ${blobs[i].id} and ${blobs[j].id} spawned only $distance apart " +
                        "(minimum is ${GameConfig.MIN_SPAWN_SEPARATION})",
                    distance >= GameConfig.MIN_SPAWN_SEPARATION
                )
            }
        }
    }

    // A tight fit (lots of bots, a small arena) genuinely may not allow full separation for
    // every pair - this only checks placement still terminates and produces every blob
    // instead of hanging, falling back to "closest attempt" rather than looping forever
    // (see GameConfig.SPAWN_PLACEMENT_MAX_ATTEMPTS).
    @Test
    fun `spawn placement still terminates with a high bot count in a small arena`() {
        val engine = GameEngine(
            botCount = 100,
            powerUpFrequencyLevel = 1,
            arenaSize = GameConfig.ArenaSize.SMALL,
            listener = TestGameListener()
        )
        assertEquals(101, engine.blobs.size)
    }
}
