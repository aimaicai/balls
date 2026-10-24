package com.hyperionsoftware.balls.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.hyperionsoftware.balls.game.PowerUpType
import com.hyperionsoftware.balls.game.Vector2
import com.hyperionsoftware.balls.race.RaceEndReason
import com.hyperionsoftware.balls.race.RaceTrack
import com.hyperionsoftware.balls.ui.gdx.GdxHostFragment
import com.hyperionsoftware.balls.ui.gdx.attachGdxHost

// Grand Prix mode's surface - the Android-side host of RaceRenderer, the same way GameView hosts
// GameRenderer: same public API RaceActivity always used, input forwarded through the renderer's
// @Volatile request fields, callbacks relayed back on the UI thread.
class RaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs), JoystickView.Listener {

    interface Callback {
        fun onRaceOver(
            playerWon: Boolean,
            reason: RaceEndReason,
            finalRadius: Int,
            lapsCompleted: Int,
            elapsedSeconds: Int
        )
        fun onBoostAvailabilityChanged(available: Boolean)
        fun onCarriedItemChanged(type: PowerUpType?)
    }

    var callback: Callback? = null

    private val renderer = RaceRenderer(this)
    private var host: GdxHostFragment? = null
    private var started = false

    fun startRace(botCount: Int, track: RaceTrack, laps: Int) {
        if (started) return
        started = true
        if (host == null) host = attachGdxHost(this, renderer)
        // Unpaused here on the UI thread, which owns this flag - see GameView.startGame.
        renderer.paused = false
        renderer.pendingStart = RaceRenderer.StartParams(botCount, track, laps)
        setContinuousRendering(true)
    }

    fun pauseRace() {
        renderer.paused = true
        setContinuousRendering(false)
    }

    fun resumeRace() {
        if (!started) return
        renderer.paused = false
        setContinuousRendering(true)
    }

    fun restart(botCount: Int, track: RaceTrack, laps: Int) {
        started = false
        startRace(botCount, track, laps)
    }

    fun setBoosting(active: Boolean) {
        if (started) renderer.playerBoosting = active
    }

    fun useActiveItem() {
        if (started) renderer.activeItemRequested = true
    }

    override fun onDirectionChanged(x: Float, y: Float) {
        renderer.playerDirection = Vector2(x, y)
    }

    private fun setContinuousRendering(continuous: Boolean) {
        val graphics = host?.graphics ?: return
        graphics.isContinuousRendering = continuous
        graphics.requestRendering()
    }
}
