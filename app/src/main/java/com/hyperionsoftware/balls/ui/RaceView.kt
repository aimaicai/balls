package com.hyperionsoftware.balls.ui

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.content.ContextCompat
import com.hyperionsoftware.balls.R
import com.hyperionsoftware.balls.cosmetics.BalloonCord
import com.hyperionsoftware.balls.cosmetics.BalloonSticker
import com.hyperionsoftware.balls.cosmetics.ExhaustStyle
import com.hyperionsoftware.balls.game.PowerUp
import com.hyperionsoftware.balls.game.PowerUpType
import com.hyperionsoftware.balls.game.Vector2
import com.hyperionsoftware.balls.race.RaceBlob
import com.hyperionsoftware.balls.race.RaceConfig
import com.hyperionsoftware.balls.race.RaceEndReason
import com.hyperionsoftware.balls.race.RaceEngine
import com.hyperionsoftware.balls.race.RaceListener
import com.hyperionsoftware.balls.race.RaceTrack
import com.hyperionsoftware.balls.settings.CosmeticsSettings
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sin

// Grand Prix mode's own SurfaceView, a lighter port of GameView: no achievements, personal
// records, daily challenges or helium integration - a first-iteration rendering layer for an
// experimental mode (see RaceEngine), not a full match of the classic mode's polish. Racers
// are drawn with the exact same balloon look (egg body, knot, string, exhaust) and the
// player's own cosmetics (see CosmeticsSettings) as classic mode, though, since a Grand Prix
// racer that doesn't even look like the same balloon would be a strange first impression.
class RaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, JoystickView.Listener {

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

    private lateinit var engine: RaceEngine
    private var loopThread: RaceThread? = null
    private var surfaceReady = false
    private var started = false
    private var lastBoostAvailable = false
    private var lastCarriedItemType: PowerUpType? = null

    // Same cross-thread pattern as GameView.activeItemRequested - only ever set here from the
    // UI thread, only ever consumed (and cleared) by RaceThread at the top of its next tick.
    @Volatile
    private var activeItemRequested = false

    private var countdownActive = false
    private var countdownRemaining = 0f
    private var lastDirection = Vector2(0f, 0f)
    private var beaconPhase = 0f

    // Read once per race start, same as the player's chosen color - purely cosmetic, drawn
    // only on the player's own balloon (see drawSticker/drawString/drawExhaust), never
    // threaded through RaceEngine.
    private var selectedSticker: BalloonSticker = BalloonSticker.NONE
    private var selectedCord: BalloonCord = BalloonCord.CLASSIC_GREY
    private var selectedExhaustStyle: ExhaustStyle = ExhaustStyle.CLASSIC
    private val exhaustPuffCount = 4

    private data class FloatingText(
        val x: Float,
        val y: Float,
        val text: String,
        val color: Int,
        var elapsed: Float = 0f,
        val duration: Float = 1.3f
    )

    private val floatingTexts = mutableListOf<FloatingText>()

    // The track corridor is drawn as three concentric thick strokes of the same closed-loop
    // path (see buildTrackPath): a faint outer shoulder showing the tolerated off-track
    // margin, a bright curb border, and the actual asphalt surface on top of it - narrower
    // than the curb, so only a thin ring of it peeks out as a clearly visible edge. A dashed
    // racing line on top reinforces the path itself. Colors are chosen for strong contrast
    // against the dark floor - the flat, low-contrast surface this used to be was barely
    // readable at a glance.
    private val trackShoulderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#332F3B45")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val trackCurbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#ECEFF1")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val trackSurfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A5A6A")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val trackCenterLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD54F")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 6f
        pathEffect = DashPathEffect(floatArrayOf(46f, 34f), 0f)
    }
    private val checkpointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCFFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val nextCheckpointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    private val startLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 10f
    }

    // Balloon rendering below mirrors GameView's exactly (same fields/technique), so a Grand
    // Prix racer - player or bot - reads as the same balloon as classic mode, not a different,
    // simplified stand-in.
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val knotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#CFD8DC")
    }
    private val stickerInkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F5F5F5")
        alpha = 235
    }
    private val stickerDetailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#212121")
        alpha = 200
    }
    private val speedBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFEB3B") }
    // Soft-edged via BlurMaskFilter instead of a hard shape - safe here because SurfaceView's
    // canvas is a plain software bitmap canvas, and BlurMaskFilter only renders on those.
    private val exhaustPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B3E5FC")
        maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
    }
    private val shieldAuraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#804FC3F7")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val frozenOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#66B3E5FC") }
    private val powerUpPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val powerUpIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val hudTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.hud_text)
        textSize = 40f
    }
    private val floatingTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 32f
        isFakeBoldText = true
    }
    private val countdownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 140f
        isFakeBoldText = true
    }

    // More elongated than classic mode's own 1.12/0.94 (see GameView) - a race is exactly the
    // context where "which way is this thing actually facing" needs to read at a glance, so
    // the egg shape here is a deliberately more obvious torpedo, not just a subtle stretch.
    private val balloonStretch = 1.3f
    private val balloonSquash = 0.82f
    private val balloonMatrix = Matrix()
    private val balloonLocalOutlinePath = Path()
    private val balloonWorldOutlinePath = Path()
    private val knotPath = Path()
    private val stringPath = Path()
    private val nosePath = Path()
    private val nosePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    private val trackPath = Path()

    // A small always-on-screen overview of the whole circuit - just the track shape and every
    // blob's live position, no camera-viewport indicator or per-checkpoint detail, to keep
    // this a "simple" minimap as asked rather than a second full HUD. Tucked in the top-right
    // corner, below the pause button and timer, rather than top-center - sitting in the
    // middle of the screen covered too much of the actual play area right in front of the
    // camera.
    private val minimapWidth = 130f
    private val minimapHeight = minimapWidth * (RaceConfig.WORLD_HEIGHT / RaceConfig.WORLD_WIDTH)
    private val minimapScale = minimapWidth / RaceConfig.WORLD_WIDTH
    private val minimapMarginRight = 20f
    private val minimapTop = 150f
    private val minimapPadding = 10f
    private val minimapDotRadius = 7f / minimapScale
    private val minimapBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(175, 10, 16, 22) }
    private val minimapBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val minimapTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A5A6A")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 5f / minimapScale
    }
    private val minimapDotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val minimapPlayerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f / minimapScale
    }

    init {
        holder.addCallback(this)
    }

    fun startRace(botCount: Int, track: RaceTrack, laps: Int) {
        if (started) return
        started = true
        floatingTexts.clear()
        countdownActive = true
        countdownRemaining = RaceConfig.COUNTDOWN_SECONDS
        lastBoostAvailable = false
        lastCarriedItemType = null
        selectedSticker = CosmeticsSettings.getSelectedSticker(context)
        selectedCord = CosmeticsSettings.getSelectedCord(context)
        selectedExhaustStyle = CosmeticsSettings.getSelectedExhaustStyle(context)
        buildTrackPath(track)

        engine = RaceEngine(
            botCount = botCount,
            track = track,
            totalLaps = laps,
            playerColor = CosmeticsSettings.getSelectedColor(context).colorInt,
            listener = object : RaceListener {
                override fun onVibrate() = Unit

                override fun onAbsorb(x: Float, y: Float, sizeGain: Int, byPlayer: Boolean, absorberId: Int, victimId: Int) {
                    val color = if (byPlayer) Color.parseColor("#8BC34A") else Color.WHITE
                    floatingTexts.add(FloatingText(x, y, "+$sizeGain", color))
                }

                override fun onPowerUpCollected(x: Float, y: Float, type: PowerUpType, byPlayer: Boolean) {
                    val label = when (type) {
                        PowerUpType.SPEED -> context.getString(R.string.game_powerup_speed_ready)
                        PowerUpType.GROWTH -> context.getString(R.string.game_powerup_growth)
                        PowerUpType.INVISIBILITY -> context.getString(R.string.game_powerup_invisibility_ready)
                        PowerUpType.SHIELD -> context.getString(R.string.game_powerup_shield)
                        PowerUpType.REPEL -> context.getString(R.string.game_powerup_repel_ready)
                        PowerUpType.FREEZE -> context.getString(R.string.game_powerup_freeze_ready)
                        PowerUpType.HOOK -> context.getString(R.string.game_powerup_hook_ready)
                        PowerUpType.SPEED_UP -> context.getString(R.string.game_powerup_speed_permanent)
                        PowerUpType.AGILITY_UP -> context.getString(R.string.game_powerup_agility_permanent)
                        PowerUpType.POTENCY_UP -> context.getString(R.string.game_powerup_potency_permanent)
                    }
                    floatingTexts.add(FloatingText(x, y, label, Color.parseColor("#FFD54F")))
                }

                override fun onActiveItemUsed(
                    x: Float,
                    y: Float,
                    type: PowerUpType,
                    byPlayer: Boolean,
                    sourceRadius: Float,
                    sourcePotencyMultiplier: Float
                ) {
                    val label = when (type) {
                        PowerUpType.REPEL -> context.getString(R.string.game_active_repelled)
                        PowerUpType.FREEZE -> context.getString(R.string.game_active_frozen)
                        PowerUpType.HOOK -> context.getString(R.string.game_active_hooked)
                        PowerUpType.SPEED -> context.getString(R.string.game_active_speed)
                        PowerUpType.INVISIBILITY -> context.getString(R.string.game_active_invisibility)
                        else -> return
                    }
                    floatingTexts.add(FloatingText(x, y, label, powerUpColor(type)))
                }

                override fun onDeflateDeath(x: Float, y: Float, wasPlayer: Boolean) {
                    floatingTexts.add(
                        FloatingText(x, y, context.getString(R.string.game_deflate_death), Color.parseColor("#FF6F00"))
                    )
                }

                override fun onLapCompleted(byPlayer: Boolean, lapsCompleted: Int, totalLaps: Int) {
                    if (!byPlayer) return
                    val player = engine.player
                    floatingTexts.add(
                        FloatingText(
                            player.position.x, player.position.y,
                            context.getString(R.string.race_feed_lap_completed_format, lapsCompleted, totalLaps),
                            Color.parseColor("#FFD54F"),
                            duration = 1.6f
                        )
                    )
                }

                override fun onRaceOver(
                    playerWon: Boolean,
                    reason: RaceEndReason,
                    finalRadius: Float,
                    lapsCompleted: Int,
                    elapsedSeconds: Float
                ) {
                    loopThread?.running = false
                    post {
                        callback?.onRaceOver(playerWon, reason, finalRadius.toInt(), lapsCompleted, elapsedSeconds.toInt())
                    }
                }
            }
        )
        if (surfaceReady) launchLoop()
    }

    fun pauseRace() {
        loopThread?.running = false
    }

    fun resumeRace() {
        if (started && surfaceReady && (loopThread == null || !loopThread!!.isAlive)) {
            launchLoop()
        }
    }

    fun restart(botCount: Int, track: RaceTrack, laps: Int) {
        loopThread?.running = false
        loopThread?.join(500)
        started = false
        startRace(botCount, track, laps)
    }

    fun setBoosting(active: Boolean) {
        if (started) engine.player.isBoosting = active
    }

    fun useActiveItem() {
        if (started) activeItemRequested = true
    }

    private fun checkBoostAvailability() {
        val available = engine.player.radius > RaceConfig.BASE_RADIUS * 0.6f
        if (available != lastBoostAvailable) {
            lastBoostAvailable = available
            post { callback?.onBoostAvailabilityChanged(available) }
        }
    }

    private fun checkCarriedItemAvailability() {
        val type = engine.player.carriedItem
        if (type != lastCarriedItemType) {
            lastCarriedItemType = type
            post { callback?.onCarriedItemChanged(type) }
        }
    }

    override fun onDirectionChanged(x: Float, y: Float) {
        lastDirection = Vector2(x, y)
        if (started) engine.player.inputDirection = lastDirection
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        if (started) launchLoop()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        loopThread?.running = false
        loopThread?.join(500)
    }

    private fun launchLoop() {
        loopThread?.running = false
        loopThread?.join(500)
        loopThread = RaceThread().also { it.start() }
    }

    private inner class RaceThread : Thread("RaceLoop") {
        @Volatile var running = true

        override fun run() {
            var lastTime = System.nanoTime()
            while (running) {
                val now = System.nanoTime()
                var dt = (now - lastTime) / 1_000_000_000f
                lastTime = now
                dt = min(dt, 0.05f)

                if (activeItemRequested) {
                    activeItemRequested = false
                    engine.activateCarriedItem(engine.player)
                }

                if (countdownActive) {
                    countdownRemaining -= dt
                    if (countdownRemaining <= 0f) countdownActive = false
                } else {
                    engine.update(dt)
                    checkBoostAvailability()
                    checkCarriedItemAvailability()
                }
                drawFrame(dt)

                val frameTimeMs = (System.nanoTime() - now) / 1_000_000
                val sleepTime = 16 - frameTimeMs
                if (sleepTime > 0) {
                    try {
                        sleep(sleepTime)
                    } catch (_: InterruptedException) {
                        running = false
                    }
                }
            }
        }
    }

    private fun drawFrame(dt: Float) {
        val canvas = try {
            holder.lockCanvas()
        } catch (_: Exception) {
            null
        } ?: return
        try {
            render(canvas, dt)
        } finally {
            try {
                holder.unlockCanvasAndPost(canvas)
            } catch (_: Exception) {
                // Surface may have been torn down mid-frame; nothing to recover.
            }
        }
    }

    private fun render(canvas: Canvas, dt: Float) {
        canvas.drawColor(Color.parseColor("#0F1620"))
        beaconPhase += dt

        val player = engine.player
        val camX = cameraCoord(player.position.x, width, RaceConfig.WORLD_WIDTH)
        val camY = cameraCoord(player.position.y, height, RaceConfig.WORLD_HEIGHT)
        val offsetX = width / 2f - camX
        val offsetY = height / 2f - camY

        drawTrack(canvas, offsetX, offsetY)

        for (powerUp in engine.powerUps) {
            drawPowerUp(canvas, powerUp, offsetX, offsetY)
        }
        for (blob in engine.blobs) {
            if (blob.alive) drawBlob(canvas, blob, offsetX, offsetY)
        }

        drawFloatingTexts(canvas, offsetX, offsetY, dt)
        drawHud(canvas)
        drawMinimap(canvas)
        drawCountdown(canvas)
    }

    private fun cameraCoord(playerCoord: Float, viewportSize: Int, worldSize: Float): Float {
        val halfViewport = viewportSize / 2f
        if (worldSize <= viewportSize) return worldSize / 2f
        return playerCoord.coerceIn(halfViewport, worldSize - halfViewport)
    }

    // Builds the closed-loop corridor path once per race (see buildTrackPath) instead of every
    // frame - the checkpoints themselves never move mid-race.
    private fun buildTrackPath(track: RaceTrack) {
        trackPath.reset()
        val checkpoints = track.checkpoints
        trackPath.moveTo(checkpoints[0].x, checkpoints[0].y)
        for (i in 1 until checkpoints.size) {
            trackPath.lineTo(checkpoints[i].x, checkpoints[i].y)
        }
        trackPath.close()
        trackShoulderPaint.strokeWidth = (track.halfWidth + RaceConfig.OFF_TRACK_MARGIN) * 2f
        // Wider than the surface stroke drawn on top of it, so only a curb-width ring shows.
        trackCurbPaint.strokeWidth = track.halfWidth * 2f + CURB_THICKNESS * 2f
        trackSurfacePaint.strokeWidth = track.halfWidth * 2f
    }

    private fun drawTrack(canvas: Canvas, offsetX: Float, offsetY: Float) {
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.drawPath(trackPath, trackShoulderPaint)
        canvas.drawPath(trackPath, trackCurbPaint)
        canvas.drawPath(trackPath, trackSurfacePaint)
        canvas.drawPath(trackPath, trackCenterLinePaint)
        canvas.restore()

        val track = engine.track
        for (checkpoint in track.checkpoints) {
            canvas.drawCircle(
                checkpoint.x + offsetX, checkpoint.y + offsetY, RaceConfig.CHECKPOINT_RADIUS * 0.3f, checkpointPaint
            )
        }
        val start = track.checkpoints[0]
        canvas.drawLine(
            start.x + offsetX, start.y + offsetY - track.halfWidth,
            start.x + offsetX, start.y + offsetY + track.halfWidth,
            startLinePaint
        )

        // The player's own next waypoint, highlighted distinctly from every other checkpoint
        // marker - there's no camera-viewport indicator on the minimap yet, so this is the
        // main on-screen guidance toward where to go next.
        val target = track.checkpoints[engine.player.nextCheckpointIndex]
        val pulse = RaceConfig.CHECKPOINT_RADIUS * (0.4f + 0.08f * sin(beaconPhase * 4f))
        canvas.drawCircle(target.x + offsetX, target.y + offsetY, pulse, nextCheckpointPaint)
    }

    private fun drawBlob(canvas: Canvas, blob: RaceBlob, offsetX: Float, offsetY: Float) {
        val cx = blob.position.x + offsetX
        val cy = blob.position.y + offsetY
        val alpha = if (blob.isInvisible) 70 else 255

        drawExhaust(canvas, blob, cx, cy, alpha)
        drawBalloonBody(canvas, blob, cx, cy, alpha)
        drawNose(canvas, blob, cx, cy, alpha)
        drawSticker(canvas, blob, cx, cy, alpha)
        if (blob.isFrozen) {
            frozenOverlayPaint.alpha = (alpha * 0.4f).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, blob.radius * 1.05f, frozenOverlayPaint)
        }
        if (blob.isShielded) {
            val pulse = 0.6f + 0.4f * sin(blob.exhaustPhase * 3f)
            shieldAuraPaint.alpha = (alpha * 0.5f * pulse).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, blob.radius * 1.18f, shieldAuraPaint)
        }
        drawSpeedBadge(canvas, blob, cx, cy, alpha)
        drawKnot(canvas, blob, cx, cy, alpha)
        drawString(canvas, blob, cx, cy, alpha)
    }

    // The true silhouette is an egg shape stretched along the facing axis, built as a
    // world-space path via a rotation matrix - exactly GameView's technique, so a Grand Prix
    // balloon reads as the same balloon as classic mode, not a plain circle standing in for it.
    private fun drawBalloonBody(canvas: Canvas, blob: RaceBlob, cx: Float, cy: Float, alpha: Int) {
        bodyPaint.color = blob.color
        bodyPaint.alpha = alpha

        val angleDeg = facingAngleDeg(blob)
        balloonMatrix.reset()
        balloonMatrix.postScale(balloonSquash, balloonStretch)
        balloonMatrix.postRotate(angleDeg)
        balloonMatrix.postTranslate(cx, cy)

        balloonLocalOutlinePath.reset()
        balloonLocalOutlinePath.addCircle(0f, 0f, blob.radius, Path.Direction.CW)
        balloonWorldOutlinePath.reset()
        balloonLocalOutlinePath.transform(balloonMatrix, balloonWorldOutlinePath)

        canvas.drawPath(balloonWorldOutlinePath, bodyPaint)

        canvas.save()
        canvas.clipPath(balloonWorldOutlinePath)
        shadePaint.color = darken(blob.color, 0.6f)
        shadePaint.alpha = (alpha * 0.45f).toInt()
        canvas.drawCircle(cx + blob.radius * 0.22f, cy + blob.radius * 0.28f, blob.radius * 0.95f, shadePaint)
        highlightPaint.alpha = (alpha * 0.5f).toInt()
        canvas.drawOval(
            cx - blob.radius * 0.55f, cy - blob.radius * 0.65f,
            cx - blob.radius * 0.05f, cy - blob.radius * 0.15f,
            highlightPaint
        )
        canvas.restore()
    }

    // A small bright wedge at the FRONT of the balloon (opposite the knot, which trails at
    // the back) - an unambiguous "which way is this thing pointed" cue that doesn't depend
    // on noticing the body's own egg-shape rotation, since a race is exactly the context
    // where that needs to be obvious at a glance, for every racer, not just the player's own.
    private fun drawNose(canvas: Canvas, blob: RaceBlob, cx: Float, cy: Float, alpha: Int) {
        val forwardX = blob.facingDirection.x
        val forwardY = blob.facingDirection.y
        val edgeRadius = blob.radius * balloonStretch
        val noseLength = blob.radius * 0.36f
        val noseWidth = blob.radius * 0.3f
        val tipX = cx + forwardX * (edgeRadius + noseLength * 0.35f)
        val tipY = cy + forwardY * (edgeRadius + noseLength * 0.35f)
        val baseX = cx + forwardX * (edgeRadius - noseLength * 0.65f)
        val baseY = cy + forwardY * (edgeRadius - noseLength * 0.65f)
        val perpX = -forwardY * noseWidth * 0.5f
        val perpY = forwardX * noseWidth * 0.5f

        nosePaint.alpha = (200 * (alpha / 255f)).toInt().coerceIn(0, 255)
        nosePath.reset()
        nosePath.moveTo(baseX + perpX, baseY + perpY)
        nosePath.lineTo(baseX - perpX, baseY - perpY)
        nosePath.lineTo(tipX, tipY)
        nosePath.close()
        canvas.drawPath(nosePath, nosePaint)
    }

    // Only ever drawn on the player's own balloon (see BalloonSticker) - bots always keep
    // their plain look, exactly like classic mode.
    private fun drawSticker(canvas: Canvas, blob: RaceBlob, cx: Float, cy: Float, alpha: Int) {
        if (selectedSticker == BalloonSticker.NONE || blob !== engine.player) return
        stickerInkPaint.alpha = (235 * (alpha / 255f)).toInt().coerceIn(0, 255)
        stickerDetailPaint.alpha = (200 * (alpha / 255f)).toInt().coerceIn(0, 255)
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(facingAngleDeg(blob))
        canvas.scale(balloonSquash, balloonStretch)
        selectedSticker.drawInto(canvas, stickerInkPaint, stickerDetailPaint, blob.radius * 0.5f)
        canvas.restore()
    }

    private fun facingAngleDeg(blob: RaceBlob): Float =
        Math.toDegrees(atan2(blob.facingDirection.y, blob.facingDirection.x).toDouble()).toFloat() + 90f

    private fun drawSpeedBadge(canvas: Canvas, blob: RaceBlob, cx: Float, cy: Float, alpha: Int) {
        if (!blob.isSpeedBoosted) return
        val size = blob.radius * 0.55f
        speedBadgePaint.alpha = alpha
        canvas.save()
        canvas.translate(cx, cy)
        val bolt = Path().apply {
            moveTo(size * 0.15f, -size * 0.6f)
            lineTo(-size * 0.35f, size * 0.05f)
            lineTo(size * 0.05f, size * 0.05f)
            lineTo(-size * 0.15f, size * 0.6f)
            lineTo(size * 0.45f, -size * 0.05f)
            lineTo(size * 0.05f, -size * 0.05f)
            close()
        }
        canvas.drawPath(bolt, speedBadgePaint)
        canvas.restore()
    }

    private fun drawKnot(canvas: Canvas, blob: RaceBlob, cx: Float, cy: Float, alpha: Int) {
        val backX = -blob.facingDirection.x
        val backY = -blob.facingDirection.y
        val edgeRadius = blob.radius * balloonStretch
        val knotSize = blob.radius * 0.22f
        val baseX = cx + backX * edgeRadius * 0.85f
        val baseY = cy + backY * edgeRadius * 0.85f
        val tipX = cx + backX * (edgeRadius + knotSize)
        val tipY = cy + backY * (edgeRadius + knotSize)
        val perpX = -backY * knotSize * 0.5f
        val perpY = backX * knotSize * 0.5f

        knotPaint.color = darken(blob.color, 0.55f)
        knotPaint.alpha = alpha
        knotPath.reset()
        knotPath.moveTo(baseX + perpX, baseY + perpY)
        knotPath.lineTo(baseX - perpX, baseY - perpY)
        knotPath.lineTo(tipX, tipY)
        knotPath.close()
        canvas.drawPath(knotPath, knotPaint)
    }

    private fun drawString(canvas: Canvas, blob: RaceBlob, cx: Float, cy: Float, alpha: Int) {
        val backX = -blob.facingDirection.x
        val backY = -blob.facingDirection.y
        val edgeRadius = blob.radius * balloonStretch
        val knotSize = blob.radius * 0.22f
        val startX = cx + backX * (edgeRadius + knotSize)
        val startY = cy + backY * (edgeRadius + knotSize)
        val length = blob.radius * 0.9f
        val sway = sin(blob.exhaustPhase * 2.3f) * blob.radius * 0.15f

        stringPaint.color = if (blob === engine.player) selectedCord.colorInt else BalloonCord.CLASSIC_GREY.colorInt
        stringPaint.alpha = (alpha * 0.6f).toInt()
        stringPath.reset()
        stringPath.moveTo(startX, startY)
        stringPath.quadTo(
            startX + backX * length * 0.5f + sway, startY + backY * length * 0.5f,
            startX + backX * length + sway * 0.6f, startY + backY * length
        )
        canvas.drawPath(stringPath, stringPaint)
    }

    // A trail of soft, shrinking puffs, same technique as classic mode - only the player's
    // own puffs ever use a non-default ExhaustStyle.
    private fun drawExhaust(canvas: Canvas, blob: RaceBlob, cx: Float, cy: Float, alpha: Int) {
        if (!blob.isThrusting && !blob.isBoosting) return

        val boosting = blob.isBoosting
        val backX = -blob.facingDirection.x
        val backY = -blob.facingDirection.y
        val perpX = -backY
        val perpY = backX
        val pulse = 0.7f + 0.3f * sin(blob.exhaustPhase * (if (boosting) 22f else 14f))
        val intensity = if (boosting) 1.7f else 1f
        val edgeRadius = blob.radius * balloonStretch
        val trailLength = blob.radius * (1.4f + 0.5f * pulse) * intensity
        val baseAlphaFraction = if (boosting) 0.75f else 0.5f
        val style = if (blob === engine.player) selectedExhaustStyle else ExhaustStyle.CLASSIC

        exhaustPaint.color = if (boosting) Color.parseColor("#FF7043") else Color.parseColor("#B3E5FC")
        for (i in 0 until exhaustPuffCount) {
            val fraction = i / (exhaustPuffCount - 1).toFloat()
            val distance = edgeRadius + trailLength * fraction
            val wobble = sin(blob.exhaustPhase * (5f + i * 2.1f) + i * 1.9f) * blob.radius * 0.22f * fraction
            val puffX = cx + backX * distance + perpX * wobble
            val puffY = cy + backY * distance + perpY * wobble
            val puffRadius = (blob.radius * (0.21f + fraction * 0.24f) * intensity).coerceAtLeast(1f)
            exhaustPaint.alpha = (alpha * baseAlphaFraction * pulse * (1f - fraction * 0.7f)).toInt().coerceIn(0, 255)
            style.drawPuff(canvas, exhaustPaint, puffX, puffY, puffRadius)
        }
    }

    private fun darken(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(Color.alpha(color), r, g, b)
    }

    private fun powerUpColor(type: PowerUpType): Int = when (type) {
        PowerUpType.SPEED -> Color.parseColor("#FFC107")
        PowerUpType.GROWTH -> Color.parseColor("#81C784")
        PowerUpType.INVISIBILITY -> Color.parseColor("#7E57C2")
        PowerUpType.SHIELD -> Color.parseColor("#4FC3F7")
        PowerUpType.REPEL -> Color.parseColor("#EC407A")
        PowerUpType.FREEZE -> Color.parseColor("#26C6DA")
        PowerUpType.HOOK -> Color.parseColor("#A1887F")
        PowerUpType.SPEED_UP -> Color.parseColor("#FF7043")
        PowerUpType.AGILITY_UP -> Color.parseColor("#00897B")
        PowerUpType.POTENCY_UP -> Color.parseColor("#D32F2F")
    }

    private fun powerUpIcon(type: PowerUpType): String = when (type) {
        PowerUpType.SPEED -> ">>"
        PowerUpType.GROWTH -> "+"
        PowerUpType.INVISIBILITY -> "?"
        PowerUpType.SHIELD -> "S"
        PowerUpType.REPEL -> "R"
        PowerUpType.FREEZE -> "F"
        PowerUpType.HOOK -> "H"
        PowerUpType.SPEED_UP -> "SU"
        PowerUpType.AGILITY_UP -> "AU"
        PowerUpType.POTENCY_UP -> "PU"
    }

    private fun drawPowerUp(canvas: Canvas, powerUp: PowerUp, offsetX: Float, offsetY: Float) {
        val cx = powerUp.position.x + offsetX
        val cy = powerUp.position.y + offsetY
        powerUpPaint.color = powerUpColor(powerUp.type)
        canvas.drawCircle(cx, cy, powerUp.radius, powerUpPaint)
        powerUpIconPaint.textSize = powerUp.radius
        canvas.drawText(powerUpIcon(powerUp.type), cx, cy + powerUp.radius * 0.35f, powerUpIconPaint)
    }

    private fun drawFloatingTexts(canvas: Canvas, offsetX: Float, offsetY: Float, dt: Float) {
        val iterator = floatingTexts.iterator()
        while (iterator.hasNext()) {
            val text = iterator.next()
            text.elapsed += dt
            if (text.elapsed >= text.duration) {
                iterator.remove()
                continue
            }
            val progress = text.elapsed / text.duration
            floatingTextPaint.color = text.color
            floatingTextPaint.alpha = (255 * (1f - progress)).toInt().coerceIn(0, 255)
            canvas.drawText(
                text.text, text.x + offsetX, text.y + offsetY - progress * 60f, floatingTextPaint
            )
        }
    }

    private fun drawHud(canvas: Canvas) {
        val player = engine.player
        val currentLap = (player.lapsCompleted + 1).coerceAtMost(engine.totalLaps)
        canvas.drawText(
            context.getString(R.string.race_hud_lap_format, currentLap, engine.totalLaps), 24f, 56f, hudTextPaint
        )
        canvas.drawText(
            context.getString(R.string.race_hud_racers_format, engine.aliveCount()), 24f, 96f, hudTextPaint
        )
        val elapsedSeconds = engine.matchElapsed.toInt()
        val timeText = "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)
        val timeWidth = hudTextPaint.measureText(timeText)
        canvas.drawText(timeText, width - timeWidth - 24f, 56f, hudTextPaint)
    }

    // A simple always-visible overview: the track's shape scaled down to fit a small panel,
    // plus a dot per live blob (the player's own ringed in white to stand out) - no
    // camera-viewport rectangle or checkpoint detail, deliberately kept minimal.
    private fun drawMinimap(canvas: Canvas) {
        val left = width - minimapMarginRight - minimapWidth
        val top = minimapTop

        canvas.drawRect(
            left - minimapPadding, top - minimapPadding,
            left + minimapWidth + minimapPadding, top + minimapHeight + minimapPadding,
            minimapBackgroundPaint
        )
        canvas.drawRect(
            left - minimapPadding, top - minimapPadding,
            left + minimapWidth + minimapPadding, top + minimapHeight + minimapPadding,
            minimapBorderPaint
        )

        canvas.save()
        canvas.translate(left, top)
        canvas.scale(minimapScale, minimapScale)
        canvas.drawPath(trackPath, minimapTrackPaint)
        for (blob in engine.blobs) {
            if (!blob.alive) continue
            minimapDotPaint.color = blob.color
            canvas.drawCircle(blob.position.x, blob.position.y, minimapDotRadius, minimapDotPaint)
            if (blob === engine.player) {
                canvas.drawCircle(blob.position.x, blob.position.y, minimapDotRadius * 1.7f, minimapPlayerRingPaint)
            }
        }
        canvas.restore()
    }

    private fun drawCountdown(canvas: Canvas) {
        if (!countdownActive) return
        canvas.drawColor(Color.argb(120, 0, 0, 0))
        val secondsLeft = ceil(countdownRemaining).toInt().coerceAtLeast(1)
        canvas.drawText(secondsLeft.toString(), width / 2f, height / 2f + 60f, countdownPaint)
    }

    companion object {
        private const val CURB_THICKNESS = 14f
    }
}
