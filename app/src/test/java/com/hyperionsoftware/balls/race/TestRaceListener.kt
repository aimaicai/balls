package com.hyperionsoftware.balls.race

import com.hyperionsoftware.balls.game.PowerUpType

// A recording RaceListener, mirroring game.TestGameListener's role for the classic engine's
// tests - every callback just counts/stores what it was told so a test can assert on what
// actually fired without repeating this boilerplate in every test file.
class TestRaceListener : RaceListener {
    var vibrateCount = 0
    var absorbCount = 0
    var powerUpsCollected = mutableListOf<PowerUpType>()
    var activeItemsUsed = mutableListOf<PowerUpType>()
    var deflateDeaths = 0
    var lapsCompletedByPlayer = mutableListOf<Int>()
    var lapsCompletedByBot = mutableListOf<Int>()
    var raceOverCount = 0
    var lastPlayerWon: Boolean? = null
    var lastReason: RaceEndReason? = null

    override fun onVibrate() {
        vibrateCount++
    }

    override fun onAbsorb(x: Float, y: Float, sizeGain: Int, byPlayer: Boolean, absorberId: Int, victimId: Int) {
        absorbCount++
    }

    override fun onPowerUpCollected(x: Float, y: Float, type: PowerUpType, byPlayer: Boolean) {
        powerUpsCollected.add(type)
    }

    override fun onActiveItemUsed(
        x: Float,
        y: Float,
        type: PowerUpType,
        byPlayer: Boolean,
        sourceRadius: Float,
        sourcePotencyMultiplier: Float
    ) {
        activeItemsUsed.add(type)
    }

    override fun onDeflateDeath(x: Float, y: Float, wasPlayer: Boolean) {
        deflateDeaths++
    }

    override fun onLapCompleted(byPlayer: Boolean, lapsCompleted: Int, totalLaps: Int) {
        if (byPlayer) lapsCompletedByPlayer.add(lapsCompleted) else lapsCompletedByBot.add(lapsCompleted)
    }

    override fun onRaceOver(
        playerWon: Boolean,
        reason: RaceEndReason,
        finalRadius: Float,
        lapsCompleted: Int,
        elapsedSeconds: Float
    ) {
        raceOverCount++
        lastPlayerWon = playerWon
        lastReason = reason
    }
}
