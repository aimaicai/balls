package com.hyperionsoftware.balls.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.hyperionsoftware.balls.audio.HissPlayer
import com.hyperionsoftware.balls.audio.SfxSettings
import com.hyperionsoftware.balls.game.GameConfig
import com.hyperionsoftware.balls.game.PowerUpType
import com.hyperionsoftware.balls.game.Vector2
import com.hyperionsoftware.balls.ui.gdx.GdxHostFragment
import com.hyperionsoftware.balls.ui.gdx.attachGdxHost

// Classic mode's game surface. The match itself now runs and renders on libGDX (see
// GameRenderer); this view is the Android side of it: it hosts the libGDX surface inside itself,
// keeps the same public API GameActivity always used, forwards input to the renderer and plays
// the sprint hiss. Every method here runs on the UI thread and only ever hands requests to the
// renderer through its @Volatile fields - never touches the engine directly.
class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs), JoystickView.Listener {

    interface Callback {
        fun onGameOver(
            playerWon: Boolean,
            finalRadius: Int,
            playersRemaining: Int,
            opponentsAbsorbed: Int,
            elapsedSeconds: Int,
            reachedFinalRound: Boolean
        )
        fun onBoostAvailabilityChanged(available: Boolean)
        fun onCarriedItemChanged(type: PowerUpType?)
        // The final round is meant to always feel the same regardless of whichever track
        // the player picked for the rest of the match - see GameActivity, which switches
        // background music to a dedicated final-round track on this.
        fun onFinalRoundStarted()
    }

    var callback: Callback? = null

    private val renderer = GameRenderer(this)
    private var host: GdxHostFragment? = null
    private var started = false
    private val hiss = HissPlayer()

    fun startGame(
        botCount: Int,
        powerUpFrequencyLevel: Int,
        arenaSize: GameConfig.ArenaSize = GameConfig.ArenaSize.NORMAL,
        skipToFinalRound: Boolean = false,
        botAggressivenessLevel: Int = GameConfig.BOT_AGGRESSIVENESS_DEFAULT_LEVEL,
        safeZoneShrinkSpeedLevel: Int = GameConfig.SAFE_ZONE_SHRINK_SPEED_DEFAULT_LEVEL
    ) {
        if (started) return
        started = true
        if (host == null) host = attachGdxHost(this, renderer)
        // Unpaused here on the UI thread, which owns this flag, rather than by the renderer once
        // it picks the start up - a pause tapped in between would otherwise be overwritten.
        renderer.paused = false
        renderer.pendingStart = GameRenderer.StartParams(
            botCount, powerUpFrequencyLevel, arenaSize, skipToFinalRound, botAggressivenessLevel, safeZoneShrinkSpeedLevel
        )
        setContinuousRendering(true)
    }

    // Freezes the match on its current frame - and stops redrawing it, so a paused match costs
    // nothing - until resumeGame.
    fun pauseGame() {
        renderer.paused = true
        setContinuousRendering(false)
    }

    fun resumeGame() {
        if (!started) return
        renderer.paused = false
        setContinuousRendering(true)
    }

    fun restart(
        botCount: Int,
        powerUpFrequencyLevel: Int,
        arenaSize: GameConfig.ArenaSize = GameConfig.ArenaSize.NORMAL,
        skipToFinalRound: Boolean = false,
        botAggressivenessLevel: Int = GameConfig.BOT_AGGRESSIVENESS_DEFAULT_LEVEL,
        safeZoneShrinkSpeedLevel: Int = GameConfig.SAFE_ZONE_SHRINK_SPEED_DEFAULT_LEVEL
    ) {
        started = false
        startGame(botCount, powerUpFrequencyLevel, arenaSize, skipToFinalRound, botAggressivenessLevel, safeZoneShrinkSpeedLevel)
    }

    fun setBoosting(active: Boolean) {
        if (started) renderer.playerBoosting = active
        if (active && SfxSettings.isEnabled(context)) hiss.start(SfxSettings.getVolume(context) / 100f) else hiss.stop()
    }

    // Spends whatever the player is currently carrying, if anything - the renderer applies it at
    // the top of its next frame (see GameRenderer.step).
    fun useActiveItem() {
        if (started) renderer.activeItemRequested = true
    }

    override fun onDirectionChanged(x: Float, y: Float) {
        renderer.playerDirection = Vector2(x, y)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        hiss.release()
        renderer.releaseAudio()
    }

    private fun setContinuousRendering(continuous: Boolean) {
        val graphics = host?.graphics ?: return
        graphics.isContinuousRendering = continuous
        graphics.requestRendering()
    }
}
