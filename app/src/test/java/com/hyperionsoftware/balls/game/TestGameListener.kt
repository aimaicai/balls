package com.hyperionsoftware.balls.game

// A recording GameListener shared across the game module's unit tests - every callback just
// counts/stores what it was told instead of doing anything, so a test can assert on what
// actually fired without repeating this boilerplate class in every test file.
class TestGameListener : GameListener {
    var vibrateCount = 0
    var absorbCount = 0
    var comboCounts = mutableListOf<Int>()
    var powerUpsCollected = mutableListOf<PowerUpType>()
    var activeItemsUsed = mutableListOf<PowerUpType>()
    var lastActiveItemSourceRadius = 0f
    var lastActiveItemPotency = 1f
    var zoneDeaths = 0
    var deflateDeaths = 0
    var finalRoundStartedCount = 0
    var gameOverCount = 0
    var lastGameOverPlayerWon: Boolean? = null

    override fun onVibrate() {
        vibrateCount++
    }

    override fun onAbsorb(x: Float, y: Float, sizeGain: Int, byPlayer: Boolean, absorberId: Int, victimId: Int) {
        absorbCount++
    }

    override fun onComboAchieved(x: Float, y: Float, comboCount: Int, byPlayer: Boolean) {
        comboCounts.add(comboCount)
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
        lastActiveItemSourceRadius = sourceRadius
        lastActiveItemPotency = sourcePotencyMultiplier
    }

    override fun onZoneDeath(x: Float, y: Float, wasPlayer: Boolean) {
        zoneDeaths++
    }

    override fun onDeflateDeath(x: Float, y: Float, wasPlayer: Boolean) {
        deflateDeaths++
    }

    override fun onFinalRoundStarted() {
        finalRoundStartedCount++
    }

    override fun onGameOver(
        playerWon: Boolean,
        finalRadius: Float,
        playersRemaining: Int,
        opponentsAbsorbed: Int,
        elapsedSeconds: Float,
        reachedFinalRound: Boolean
    ) {
        gameOverCount++
        lastGameOverPlayerWon = playerWon
    }
}
