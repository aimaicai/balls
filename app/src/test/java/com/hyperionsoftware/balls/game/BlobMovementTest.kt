package com.hyperionsoftware.balls.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Covers the "always moves forward, joystick only steers" movement model: direction input
// never gates or scales speed, it only nudges facingDirection toward wherever it's aimed at
// a bounded turn rate (see Blob.update/steerTowards).
class BlobMovementTest {

    private fun newEngine() = GameEngine(botCount = 0, powerUpFrequencyLevel = 1, listener = TestGameListener())

    @Test
    fun `a blob with zero input direction still moves every frame`() {
        val engine = newEngine()
        val player = engine.player
        player.inputDirection = Vector2(0f, 0f)
        val start = player.position.copy()

        player.update(1f / 30f, engine, GameConfig.PLAYER_BASE_SPEED)

        assertTrue("Position should change even with no steering input", player.position != start)
        assertTrue("isThrusting should be true while alive and unfrozen", player.isThrusting)
    }

    @Test
    fun `zero input direction keeps the previous facing instead of stopping`() {
        val engine = newEngine()
        val player = engine.player
        player.facingDirection = Vector2(1f, 0f)
        player.inputDirection = Vector2(0f, 0f)

        player.update(1f / 30f, engine, GameConfig.PLAYER_BASE_SPEED)

        assertEquals(1f, player.facingDirection.x, 0.0001f)
        assertEquals(0f, player.facingDirection.y, 0.0001f)
    }

    @Test
    fun `steering turns gradually toward the input direction, not instantly`() {
        val engine = newEngine()
        val player = engine.player
        player.facingDirection = Vector2(1f, 0f)
        player.inputDirection = Vector2(-1f, 0f) // a full 180-degree reversal

        player.update(1f / 30f, engine, GameConfig.PLAYER_BASE_SPEED)

        // A single 1/30s frame can only turn by TURN_RATE_RADIANS_PER_SECOND/30 radians -
        // nowhere near a full reversal, so it should still be pointing mostly rightward.
        assertTrue("A single short frame should not complete a 180-degree turn", player.facingDirection.x > 0f)
    }

    @Test
    fun `steering eventually reaches the desired direction given enough time`() {
        val engine = newEngine()
        val player = engine.player
        player.facingDirection = Vector2(1f, 0f)
        player.inputDirection = Vector2(-1f, 0f)

        // A full reversal takes about TURN_RATE_RADIANS_PER_SECOND radians/sec worth of time
        // to cover pi radians - well under 3 seconds at the configured turn rate.
        repeat(3 * 30) { player.update(1f / 30f, engine, GameConfig.PLAYER_BASE_SPEED) }

        assertTrue("Facing should have turned to point mostly leftward by now", player.facingDirection.x < -0.99f)
    }

    @Test
    fun `a frozen blob does not move and is not thrusting`() {
        val engine = newEngine()
        val player = engine.player
        player.inputDirection = Vector2(1f, 0f)
        player.applyFreeze(1f)
        val start = player.position.copy()

        player.update(1f / 30f, engine, GameConfig.PLAYER_BASE_SPEED)

        assertEquals(start.x, player.position.x, 0.0001f)
        assertEquals(start.y, player.position.y, 0.0001f)
        assertFalse(player.isThrusting)
    }

    @Test
    fun `a bigger blob covers less ground per frame than a smaller one`() {
        val bigEngine = newEngine()
        val big = bigEngine.player
        big.radius = GameConfig.BASE_RADIUS * 3f
        big.inputDirection = Vector2(0f, 0f)
        val bigStart = big.position.copy()
        big.update(1f / 30f, bigEngine, GameConfig.PLAYER_BASE_SPEED)
        val bigDistance = big.position.distanceTo(bigStart)

        val smallEngine = newEngine()
        val small = smallEngine.player
        small.radius = GameConfig.BASE_RADIUS
        small.inputDirection = Vector2(0f, 0f)
        val smallStart = small.position.copy()
        small.update(1f / 30f, smallEngine, GameConfig.PLAYER_BASE_SPEED)
        val smallDistance = small.position.distanceTo(smallStart)

        assertTrue("A 3x bigger blob should move slower per frame", bigDistance < smallDistance)
    }

    @Test
    fun `boosting ramps up speed the longer it is held continuously`() {
        val engine = newEngine()
        val player = engine.player
        player.inputDirection = Vector2(0f, 0f)
        player.isBoosting = true

        // Sampled partway through the ramp, not at the very end - moving in a straight line
        // (no steering input) for the ramp's full duration plus a margin covers enough
        // ground to run into the world's edge and get clamped there, which would make the
        // very last frame's distance zero for a reason that has nothing to do with boosting.
        val midFrame = (GameConfig.BOOST_RAMP_UP_SECONDS * 30 / 2).toInt()
        var firstFrameDistance = 0f
        var midRampDistance = 0f
        repeat(midFrame + 1) { frame ->
            val before = player.position.copy()
            player.update(1f / 30f, engine, GameConfig.PLAYER_BASE_SPEED)
            val distance = player.position.distanceTo(before)
            if (frame == 0) firstFrameDistance = distance
            if (frame == midFrame) midRampDistance = distance
        }

        assertTrue(
            "Speed should have increased partway through the boost ramp",
            midRampDistance > firstFrameDistance
        )
        assertFalse("The ramp should not be complete yet at its own halfway point", player.isBoostAtMaxPower)

        // isBoostAtMaxPower only depends on elapsed boosted time, not on how far the blob
        // actually moved, so it's unaffected by the wall-clamping risk above - safe to keep
        // running well past the ramp's duration to confirm it flips on.
        repeat(GameConfig.BOOST_RAMP_UP_SECONDS.toInt() * 30) {
            player.update(1f / 30f, engine, GameConfig.PLAYER_BASE_SPEED)
        }
        assertTrue("isBoostAtMaxPower should flip on once the ramp completes", player.isBoostAtMaxPower)
    }
}
