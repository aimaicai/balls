package com.hyperionsoftware.balls.race

import com.hyperionsoftware.balls.game.PowerUp
import com.hyperionsoftware.balls.game.PowerUpType
import com.hyperionsoftware.balls.game.Vector2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Covers the second win condition the user asked for - absorbing every other racer - plus
// absorption and power-up pickups still behaving normally mid-race, same as classic mode.
class EliminationAndAbsorptionTest {

    @Test
    fun `absorbing the only other racer wins the race by elimination`() {
        val listener = TestRaceListener()
        val engine = RaceEngine(botCount = 1, track = RaceTrack.OVAL, totalLaps = 3, listener = listener)
        val bot = engine.blobs[1]

        engine.player.radius = 100f
        bot.radius = 50f
        bot.position = Vector2(engine.player.position.x, engine.player.position.y)

        engine.update(1f / 30f)

        assertFalse(bot.alive)
        assertEquals(1, listener.absorbCount)
        assertEquals(1, listener.raceOverCount)
        assertEquals(true, listener.lastPlayerWon)
        assertEquals(RaceEndReason.ELIMINATION, listener.lastReason)
    }

    @Test
    fun `the player losing all its air ends the race as a loss`() {
        val listener = TestRaceListener()
        val engine = RaceEngine(botCount = 1, track = RaceTrack.OVAL, totalLaps = 3, listener = listener)
        val bot = engine.blobs[1]
        bot.position = Vector2(50f, 50f)
        bot.applyFreeze(9999f)
        bot.applyPowerUp(PowerUpType.SHIELD)

        engine.player.radius = RaceConfig.ZONE_DEATH_RADIUS + 1f
        // Deep off-track so the fast deflation rate finishes it off quickly.
        engine.player.position = Vector2(-5000f, -5000f)
        repeat(60) {
            engine.player.position = Vector2(-5000f, -5000f)
            engine.update(1f / 10f)
        }

        assertFalse(engine.player.alive)
        assertEquals(1, listener.raceOverCount)
        assertEquals(false, listener.lastPlayerWon)
    }

    @Test
    fun `absorbing a rival mid-race with more than one bot left doesn't end the race`() {
        val listener = TestRaceListener()
        val engine = RaceEngine(botCount = 2, track = RaceTrack.OVAL, totalLaps = 3, listener = listener)
        val victim = engine.blobs[1]
        val bystander = engine.blobs[2]
        bystander.position = Vector2(50f, 50f)
        bystander.applyFreeze(9999f)

        engine.player.radius = 100f
        victim.radius = 50f
        victim.position = Vector2(engine.player.position.x, engine.player.position.y)

        engine.update(1f / 30f)

        assertFalse(victim.alive)
        assertTrue(bystander.alive)
        assertEquals(1, listener.absorbCount)
        assertEquals(0, listener.raceOverCount)
    }

    @Test
    fun `a GROWTH power-up picked up mid-race grows the balloon`() {
        val listener = TestRaceListener()
        val engine = RaceEngine(botCount = 1, track = RaceTrack.OVAL, totalLaps = 3, listener = listener)
        val bot = engine.blobs[1]
        bot.position = Vector2(50f, 50f)
        bot.applyFreeze(9999f)

        val radiusBefore = engine.player.radius
        engine.powerUps.clear()
        engine.powerUps.add(PowerUp(PowerUpType.GROWTH, Vector2(engine.player.position.x, engine.player.position.y)))

        engine.update(1f / 30f)

        assertTrue(engine.player.radius > radiusBefore)
        assertEquals(listOf(PowerUpType.GROWTH), listener.powerUpsCollected)
    }

    @Test
    fun `a REPEL item picked up and activated pushes nearby rivals away`() {
        val engine = RaceEngine(botCount = 1, track = RaceTrack.OVAL, totalLaps = 3, listener = TestRaceListener())
        val bot = engine.blobs[1]
        bot.applyFreeze(9999f)
        bot.position = Vector2(engine.player.position.x + 60f, engine.player.position.y)

        engine.player.pickUpCarriedItem(PowerUpType.REPEL)
        val distanceBefore = engine.player.position.distanceTo(bot.position)

        engine.activateCarriedItem(engine.player)

        val distanceAfter = engine.player.position.distanceTo(bot.position)
        assertTrue(distanceAfter > distanceBefore)
    }
}
