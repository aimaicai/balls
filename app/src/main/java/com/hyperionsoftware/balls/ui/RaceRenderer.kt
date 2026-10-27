package com.hyperionsoftware.balls.ui

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.MathUtils
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
import com.hyperionsoftware.balls.settings.DeveloperSettings
import com.hyperionsoftware.balls.ui.gdx.Atlas
import com.hyperionsoftware.balls.ui.gdx.BalloonBodies
import com.hyperionsoftware.balls.ui.gdx.BalloonPainter
import com.hyperionsoftware.balls.ui.gdx.FrameClock
import com.hyperionsoftware.balls.ui.gdx.Painter
import com.hyperionsoftware.balls.ui.gdx.Particles
import com.hyperionsoftware.balls.ui.gdx.TextStyle
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Grand Prix mode on libGDX - the race counterpart of GameRenderer, and a lighter one, like the
// old RaceView it replaces: no achievements, records or helium. Racers are drawn by the same
// BalloonPainter as classic mode. Same threading rules as GameRenderer: RaceView only sets the
// @Volatile request fields, everything else lives on the render thread.
class RaceRenderer(private val view: RaceView) : ApplicationAdapter() {

    class StartParams(val botCount: Int, val track: RaceTrack, val laps: Int)

    private val context: Context = view.context
    private val density = context.resources.displayMetrics.density

    @Volatile var pendingStart: StartParams? = null
    @Volatile var paused = false
    private var wasPaused = false
    @Volatile var playerBoosting = false
    @Volatile var activeItemRequested = false
    @Volatile var playerDirection: Vector2 = Vector2(0f, 0f)

    private lateinit var painter: Painter
    private lateinit var balloons: BalloonPainter
    private lateinit var particles: Particles
    private val worldCamera = OrthographicCamera()
    private val screenCamera = OrthographicCamera()
    private val clock = FrameClock()
    private val balloonState = BalloonPainter.State()
    private val bodies = BalloonBodies()
    private val fpsCounter = FpsCounter(density)

    private var engine: RaceEngine? = null
    private var raceOver = false
    private var sinceRaceOver = 0f
    private var showFps = false
    private var countdownActive = false
    private var countdownRemaining = 0f
    private var lastBoostAvailable = false
    private var lastCarriedItemType: PowerUpType? = null
    private var selectedSticker: BalloonSticker = BalloonSticker.NONE
    private var selectedCord: BalloonCord = BalloonCord.CLASSIC_GREY
    private var selectedExhaustStyle: ExhaustStyle = ExhaustStyle.CLASSIC
    private var elapsedRealTime = 0f

    private class FloatingText(
        val x: Float,
        val y: Float,
        val text: String,
        val color: Int,
        var elapsed: Float = 0f,
        val duration: Float = 1.3f
    )
    private class Flash(val x: Float, val y: Float, val radius: Float, val color: Int, var ttl: Float, val maxTtl: Float = ttl)

    private val floatingTexts = mutableListOf<FloatingText>()
    private val flashes = mutableListOf<Flash>()

    // The track, flattened once per race into plain coordinates: the corridor's segments
    // (x1, y1, x2, y2 per segment, same for the dashed racing line). Drawn as three layers of
    // wide strokes - a faint shoulder for the tolerated off-track margin, a bright curb, and the
    // asphalt on top of it, narrower than the curb so only a thin ring of it peeks out - with
    // round joints at every checkpoint, exactly the look of the old stroked Path.
    private var track: RaceTrack? = null
    private var trackSegments = FloatArray(0)
    private var centerLineDashes = FloatArray(0)
    private var trackBoundsPadding = 0f

    private var hudLapValue = Int.MIN_VALUE
    private var hudLapText = ""
    private var hudRacersValue = Int.MIN_VALUE
    private var hudRacersText = ""
    private var hudTimeValue = Int.MIN_VALUE
    private var hudTimeText = ""
    private val hudTextColor = ContextCompat.getColor(context, R.color.hud_text)

    override fun create() {
        painter = Painter()
        particles = Particles(PARTICLE_CAPACITY)
        balloons = BalloonPainter(painter, particles)
        resize(Gdx.graphics.width, Gdx.graphics.height)
    }

    override fun resize(width: Int, height: Int) {
        worldCamera.setToOrtho(true, width.toFloat(), height.toFloat())
        screenCamera.setToOrtho(true, width.toFloat(), height.toFloat())
    }

    override fun resume() {
        clock.reset()
        fpsCounter.reset()
    }

    override fun dispose() {
        if (::painter.isInitialized) painter.dispose()
    }

    override fun render() {
        pendingStart?.let {
            pendingStart = null
            startRace(it)
        }
        val engine = engine
        val width = Gdx.graphics.width.toFloat()
        val height = Gdx.graphics.height.toFloat()
        Gdx.gl.glClearColor(BACKGROUND_R, BACKGROUND_G, BACKGROUND_B, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        if (engine == null || width <= 0f || height <= 0f) return

        if (paused != wasPaused) {
            // Coming back from a pause: neither the game clock nor the FPS counter should count
            // the time spent paused as a frame.
            wasPaused = paused
            clock.reset()
            fpsCounter.reset()
        }
        val dt = if (paused) 0f else clock.next(Gdx.graphics.deltaTime)
        if (!paused) step(engine, dt)
        painter.beginFrame()
        draw(engine, dt, width, height)
        // Same as GameRenderer: stop redrawing once the race is over and its effects settled.
        if (raceOver) {
            sinceRaceOver += dt
            if (sinceRaceOver > SETTLE_AFTER_END_SECONDS) Gdx.graphics.isContinuousRendering = false
        }
    }

    private fun step(engine: RaceEngine, dt: Float) {
        engine.player.isBoosting = playerBoosting
        engine.player.inputDirection = playerDirection
        if (activeItemRequested) {
            activeItemRequested = false
            engine.activateCarriedItem(engine.player)
        }
        if (countdownActive) {
            countdownRemaining -= dt
            if (countdownRemaining <= 0f) countdownActive = false
        } else if (!raceOver) {
            engine.update(dt)
            checkBoostAvailability(engine)
            checkCarriedItemAvailability(engine)
        }
    }

    private fun startRace(params: StartParams) {
        floatingTexts.clear()
        flashes.clear()
        particles.clear()
        bodies.clear()
        showFps = DeveloperSettings.isEnabled(context)
        bodies.intensity = DeveloperSettings.getDeformationPercent(context) / 100f
        countdownActive = true
        countdownRemaining = RaceConfig.COUNTDOWN_SECONDS
        lastBoostAvailable = false
        lastCarriedItemType = null
        raceOver = false
        sinceRaceOver = 0f
        activeItemRequested = false
        selectedSticker = CosmeticsSettings.getSelectedSticker(context)
        selectedCord = CosmeticsSettings.getSelectedCord(context)
        selectedExhaustStyle = CosmeticsSettings.getSelectedExhaustStyle(context)
        hudLapValue = Int.MIN_VALUE
        hudRacersValue = Int.MIN_VALUE
        hudTimeValue = Int.MIN_VALUE
        fpsCounter.reset()
        clock.reset()
        buildTrack(params.track)
        engine = RaceEngine(
            botCount = params.botCount,
            track = params.track,
            totalLaps = params.laps,
            playerColor = CosmeticsSettings.getSelectedColor(context).colorInt,
            listener = raceListener
        )
    }

    private val raceListener = object : RaceListener {
        override fun onVibrate() = Unit

        override fun onAbsorb(x: Float, y: Float, sizeGain: Int, byPlayer: Boolean, absorberId: Int, victimId: Int) {
            val color = if (byPlayer) Color.parseColor("#8BC34A") else Color.WHITE
            floatingTexts.add(FloatingText(x, y, "+$sizeGain", color))
            val victim = engine?.blobs?.find { it.id == victimId } ?: return
            popBurst(x, y, victim.radius, victim.color)
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
            sparkBurst(x, y, GameRenderer.powerUpColor(type), 9)
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
            val color = GameRenderer.powerUpColor(type)
            floatingTexts.add(FloatingText(x, y, label, color))
            flashes.add(Flash(x, y, sourceRadius * 2.5f, color, 0.35f))
        }

        override fun onDeflateDeath(x: Float, y: Float, wasPlayer: Boolean) {
            floatingTexts.add(FloatingText(x, y, context.getString(R.string.game_deflate_death), Color.parseColor("#FF6F00")))
            for (i in 0 until 10) {
                val angle = MathUtils.random(MathUtils.PI2)
                val speed = MathUtils.random(30f, 110f)
                particles.spawn(
                    Particles.Kind.SMOKE, x, y,
                    MathUtils.cos(angle) * speed, MathUtils.sin(angle) * speed,
                    MathUtils.random(0.6f, 1.1f), 22f, 70f, DEFLATE_SMOKE_COLOR, alpha = 0.35f, drag = 1.5f
                )
            }
        }

        override fun onLapCompleted(byPlayer: Boolean, lapsCompleted: Int, totalLaps: Int) {
            if (!byPlayer) return
            val player = engine?.player ?: return
            floatingTexts.add(
                FloatingText(
                    player.position.x, player.position.y,
                    context.getString(R.string.race_feed_lap_completed_format, lapsCompleted, totalLaps),
                    Color.parseColor("#FFD54F"),
                    duration = 1.6f
                )
            )
            sparkBurst(player.position.x, player.position.y, Color.parseColor("#FFD54F"), 14)
        }

        override fun onRaceOver(playerWon: Boolean, reason: RaceEndReason, finalRadius: Float, lapsCompleted: Int, elapsedSeconds: Float) {
            raceOver = true
            view.post {
                view.callback?.onRaceOver(playerWon, reason, finalRadius.toInt(), lapsCompleted, elapsedSeconds.toInt())
            }
        }
    }

    private fun popBurst(x: Float, y: Float, radius: Float, color: Int) {
        for (i in 0 until 16) {
            val angle = MathUtils.random(MathUtils.PI2)
            val speed = MathUtils.random(160f, 420f) * (0.6f + radius / 120f)
            particles.spawn(
                Particles.Kind.CONFETTI, x, y,
                MathUtils.cos(angle) * speed, MathUtils.sin(angle) * speed,
                MathUtils.random(0.5f, 0.85f), MathUtils.random(9f, 16f), 7f,
                if (i % 4 == 0) BalloonPainter.darken(color, 0.7f) else color,
                drag = 3.2f
            )
        }
        sparkBurst(x, y, Color.WHITE, 6)
        flashes.add(Flash(x, y, radius * 2.2f, color, 0.3f))
    }

    private fun sparkBurst(x: Float, y: Float, color: Int, count: Int) {
        for (i in 0 until count) {
            val angle = MathUtils.random(MathUtils.PI2)
            val speed = MathUtils.random(120f, 300f)
            particles.spawn(
                Particles.Kind.SPARK, x, y,
                MathUtils.cos(angle) * speed, MathUtils.sin(angle) * speed,
                MathUtils.random(0.3f, 0.55f), MathUtils.random(18f, 30f), 4f, color, drag = 4f
            )
        }
    }

    // ---- Track geometry ----------------------------------------------------------------------

    private fun buildTrack(track: RaceTrack) {
        this.track = track
        val points = track.checkpoints
        trackSegments = FloatArray(points.size * 4)
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            trackSegments[i * 4] = a.x
            trackSegments[i * 4 + 1] = a.y
            trackSegments[i * 4 + 2] = b.x
            trackSegments[i * 4 + 3] = b.y
        }
        trackBoundsPadding = track.halfWidth + RaceConfig.OFF_TRACK_MARGIN

        // The racing line's dashes, continuing the dash pattern across checkpoints the way
        // DashPathEffect did along the whole closed path.
        val dashes = ArrayList<Float>()
        var onPhase = true
        var remaining = DASH_ON
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val length = sqrt(dx * dx + dy * dy)
            if (length < 0.001f) continue
            var travelled = 0f
            while (travelled < length) {
                val step = min(remaining, length - travelled)
                if (onPhase) {
                    val t0 = travelled / length
                    val t1 = (travelled + step) / length
                    dashes.add(a.x + dx * t0)
                    dashes.add(a.y + dy * t0)
                    dashes.add(a.x + dx * t1)
                    dashes.add(a.y + dy * t1)
                }
                travelled += step
                remaining -= step
                if (remaining <= 0.001f) {
                    onPhase = !onPhase
                    remaining = if (onPhase) DASH_ON else DASH_OFF
                }
            }
        }
        centerLineDashes = dashes.toFloatArray()
    }

    private fun drawTrack(track: RaceTrack, left: Float, top: Float, right: Float, bottom: Float) {
        // The shoulder and the curb are wide strokes too, but almost all of each is hidden under
        // the next one up - so only the band of each that actually shows (its outer rim) is
        // drawn, then the asphalt over the middle. Same picture, far fewer pixels filled.
        val surfaceHalf = track.halfWidth
        val curbHalf = surfaceHalf + CURB_THICKNESS
        val shoulderHalf = surfaceHalf + RaceConfig.OFF_TRACK_MARGIN
        drawTrackBand(curbHalf - BAND_OVERLAP, shoulderHalf, TRACK_SHOULDER_COLOR, left, top, right, bottom)
        drawTrackBand(surfaceHalf - BAND_OVERLAP, curbHalf, TRACK_CURB_COLOR, left, top, right, bottom)
        drawTrackSurface(surfaceHalf * 2f, TRACK_SURFACE_COLOR, left, top, right, bottom)

        painter.color(TRACK_CENTER_LINE_COLOR)
        var i = 0
        while (i < centerLineDashes.size) {
            val x1 = centerLineDashes[i]
            val y1 = centerLineDashes[i + 1]
            val x2 = centerLineDashes[i + 2]
            val y2 = centerLineDashes[i + 3]
            if (segmentVisible(x1, y1, x2, y2, 6f, left, top, right, bottom)) painter.line(x1, y1, x2, y2, 6f)
            i += 4
        }

        // Drawn perpendicular to the track's own direction at checkpoints[0], using the same
        // finishLineForward RaceEngine checks crossings against, so the two can never disagree.
        val start = track.checkpoints[0]
        val forward = track.finishLineForward
        val px = -forward.y
        val py = forward.x
        painter.color(Color.WHITE)
        painter.line(
            start.x + px * track.halfWidth, start.y + py * track.halfWidth,
            start.x - px * track.halfWidth, start.y - py * track.halfWidth,
            10f
        )
    }

    // The part of a round-joined stroke along the track that lies between `inner` and `outer`
    // distance from the center line: a strip along each side of every segment, plus a ring
    // around every checkpoint for the round joins. Where these overlap the inside of the track
    // (around joints), the layers drawn after cover them; `inner` sits a few pixels under the
    // next layer up, so the strip's soft inner edge never shows either.
    private fun drawTrackBand(inner: Float, outer: Float, color: Int, left: Float, top: Float, right: Float, bottom: Float) {
        val middle = (inner + outer) / 2f
        val width = outer - inner
        painter.color(color)
        forEachVisibleSegment(outer, left, top, right, bottom) { x1, y1, x2, y2, jointVisible ->
            if (segmentVisible(x1, y1, x2, y2, outer, left, top, right, bottom)) {
                val dx = x2 - x1
                val dy = y2 - y1
                val length = sqrt(dx * dx + dy * dy)
                if (length > 0.001f) {
                    val nx = -dy / length * middle
                    val ny = dx / length * middle
                    painter.line(x1 + nx, y1 + ny, x2 + nx, y2 + ny, width)
                    painter.line(x1 - nx, y1 - ny, x2 - nx, y2 - ny, width)
                }
            }
            if (jointVisible) painter.circleStroke(x1, y1, middle, width)
        }
    }

    // The asphalt: it covers most of the screen, so its body is drawn opaque with blending off -
    // the cheapest way to fill that many pixels - and only a thin soft edge along its outline is
    // blended, for antialiasing. Where an edge runs through the inside of the stroke (around
    // joints), it's the stroke's own color over itself, so it doesn't show.
    private fun drawTrackSurface(strokeWidth: Float, color: Int, left: Float, top: Float, right: Float, bottom: Float) {
        val half = strokeWidth / 2f
        painter.color(color)
        painter.blending(false)
        forEachVisibleSegment(half, left, top, right, bottom) { x1, y1, x2, y2, jointVisible ->
            if (segmentVisible(x1, y1, x2, y2, half, left, top, right, bottom)) painter.line(x1, y1, x2, y2, strokeWidth, soft = false)
            // The round join at this checkpoint.
            if (jointVisible) painter.filledCircle(x1, y1, half, rim = false)
        }
        painter.blending(true)
        forEachVisibleSegment(half, left, top, right, bottom) { x1, y1, x2, y2, jointVisible ->
            if (segmentVisible(x1, y1, x2, y2, half, left, top, right, bottom)) {
                val dx = x2 - x1
                val dy = y2 - y1
                val length = sqrt(dx * dx + dy * dy)
                if (length > 0.001f) {
                    val nx = -dy / length * half
                    val ny = dx / length * half
                    painter.line(x1 + nx, y1 + ny, x2 + nx, y2 + ny, TRACK_EDGE_WIDTH)
                    painter.line(x1 - nx, y1 - ny, x2 - nx, y2 - ny, TRACK_EDGE_WIDTH)
                }
            }
            if (jointVisible) painter.circleStroke(x1, y1, half, TRACK_EDGE_WIDTH)
        }
    }

    private inline fun forEachVisibleSegment(
        half: Float,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        action: (x1: Float, y1: Float, x2: Float, y2: Float, jointVisible: Boolean) -> Unit
    ) {
        var i = 0
        while (i < trackSegments.size) {
            val x1 = trackSegments[i]
            val y1 = trackSegments[i + 1]
            val jointVisible = x1 + half >= left && x1 - half <= right && y1 + half >= top && y1 - half <= bottom
            action(x1, y1, trackSegments[i + 2], trackSegments[i + 3], jointVisible)
            i += 4
        }
    }

    private fun segmentVisible(x1: Float, y1: Float, x2: Float, y2: Float, pad: Float, left: Float, top: Float, right: Float, bottom: Float) =
        max(x1, x2) + pad >= left && min(x1, x2) - pad <= right && max(y1, y2) + pad >= top && min(y1, y2) - pad <= bottom

    // ---- Frame -------------------------------------------------------------------------------

    private fun draw(engine: RaceEngine, dt: Float, width: Float, height: Float) {
        elapsedRealTime += dt
        particles.update(dt)
        val player = engine.player
        val camX = cameraCoord(player.position.x, width, RaceConfig.WORLD_WIDTH)
        val camY = cameraCoord(player.position.y, height, RaceConfig.WORLD_HEIGHT)
        worldCamera.position.set(camX, camY, 0f)
        worldCamera.update()
        val left = camX - width / 2f
        val top = camY - height / 2f
        val right = left + width
        val bottom = top + height

        painter.begin(worldCamera.combined)
        track?.let { drawTrack(it, left, top, right, bottom) }

        painter.additive(true)
        for (powerUp in engine.powerUps) {
            if (isVisible(powerUp.position.x, powerUp.position.y, powerUp.radius * 4f, left, top, right, bottom)) {
                val pulse = 0.75f + 0.25f * sin(elapsedRealTime * 4f + powerUp.position.x * 0.01f)
                painter.color(GameRenderer.powerUpColor(powerUp.type), 0.5f * pulse)
                painter.glow(powerUp.position.x, powerUp.position.y, powerUp.radius * 3.2f)
            }
        }
        painter.additive(false)
        for (powerUp in engine.powerUps) {
            if (isVisible(powerUp.position.x, powerUp.position.y, powerUp.radius, left, top, right, bottom)) drawPowerUp(powerUp)
        }

        // The latex of the balloons on screen (see BalloonBodies), same as classic mode.
        bodies.beginFrame()
        for (blob in engine.blobs) {
            if (!blob.alive || !isBlobVisible(blob, left, top, right, bottom)) continue
            bodies.track(
                blob.id, blob === engine.player, blob.position.x, blob.position.y, blob.radius,
                blob.facingDirection.x, blob.facingDirection.y, blob.currentSpeed,
                blob.isThrusting, blob.isBoosting, blob.isFrozen, dt
            )
        }
        bodies.step(dt)
        for (blob in engine.blobs) {
            if (blob.alive && isBlobVisible(blob, left, top, right, bottom)) balloons.drawShadow(stateOf(blob))
        }
        particles.draw(painter, Particles.Kind.SMOKE, painter.atlas.glow, left, top, right, bottom)
        val smokeMargin = width * 0.25f
        for (blob in engine.blobs) {
            if (!blob.alive) continue
            if (isVisible(blob.position.x, blob.position.y, blob.radius * BalloonPainter.VISUAL_EXTENT + smokeMargin, left, top, right, bottom)) {
                balloons.emitSmoke(stateOf(blob), dt)
            }
            if (isBlobVisible(blob, left, top, right, bottom)) {
                val state = stateOf(blob)
                balloons.emitStatusParticles(state, dt)
                balloons.drawBalloon(state)
            }
        }
        particles.draw(painter, Particles.Kind.MIST, painter.atlas.glow, left, top, right, bottom)
        particles.draw(painter, Particles.Kind.CONFETTI, painter.atlas.white, left, top, right, bottom)

        painter.additive(true)
        for (blob in engine.blobs) {
            if (blob.alive && isBlobVisible(blob, left, top, right, bottom)) balloons.drawGlow(stateOf(blob))
        }
        particles.draw(painter, Particles.Kind.SPARK, painter.atlas.sparkle, left, top, right, bottom)
        particles.draw(painter, Particles.Kind.FLAKE, painter.atlas.snowflake, left, top, right, bottom)
        drawFlashes(dt)
        painter.additive(false)

        drawFloatingTexts(dt)
        painter.end()

        // Screen space, grouped by texture (atlas shapes, then text, then overlays, then their
        // text) so the HUD costs a handful of draw calls - see GameRenderer.draw. No vignette
        // here, unlike classic mode: with no floor to bake it into, it would cost one more layer
        // over almost the whole screen, on top of the track's own.
        painter.begin(screenCamera.combined)
        drawMinimap(engine, width)
        drawHud(engine, width)
        drawCountdown(width, height, text = false)
        if (showFps) fpsCounter.drawBackground(painter, width, height)
        drawCountdown(width, height, text = true)
        if (showFps) fpsCounter.drawText(painter, width, height)
        painter.end()
    }

    private fun stateOf(blob: RaceBlob): BalloonPainter.State = balloonState.apply {
        val isPlayer = blob === engine?.player
        val body = bodies[blob.id]
        this.body = body
        if (body != null) {
            x = body.drawnX
            y = body.drawnY
            facingX = MathUtils.cos(body.drawnHeading)
            facingY = MathUtils.sin(body.drawnHeading)
        } else {
            x = blob.position.x
            y = blob.position.y
            facingX = blob.facingDirection.x
            facingY = blob.facingDirection.y
        }
        radius = blob.radius
        color = blob.color
        alpha = if (blob.isInvisible) 70f / 255f else 1f
        isInvisible = blob.isInvisible
        exhaustPhase = blob.exhaustPhase
        isThrusting = blob.isThrusting
        isBoosting = blob.isBoosting
        isFrozen = blob.isFrozen
        isShielded = blob.isShielded
        isSpeedBoosted = blob.isSpeedBoosted
        sticker = if (isPlayer) selectedSticker else BalloonSticker.NONE
        cordColor = if (isPlayer) selectedCord.colorInt else BalloonCord.CLASSIC_GREY.colorInt
        exhaustStyle = if (isPlayer) selectedExhaustStyle else ExhaustStyle.CLASSIC
    }

    private fun isBlobVisible(blob: RaceBlob, left: Float, top: Float, right: Float, bottom: Float) =
        isVisible(blob.position.x, blob.position.y, blob.radius * BalloonPainter.VISUAL_EXTENT, left, top, right, bottom)

    private fun isVisible(x: Float, y: Float, extent: Float, left: Float, top: Float, right: Float, bottom: Float) =
        x + extent >= left && x - extent <= right && y + extent >= top && y - extent <= bottom

    private fun cameraCoord(playerCoord: Float, viewportSize: Float, worldSize: Float): Float {
        val halfViewport = viewportSize / 2f
        if (worldSize <= viewportSize) return worldSize / 2f
        return playerCoord.coerceIn(halfViewport, worldSize - halfViewport)
    }

    private fun drawPowerUp(powerUp: PowerUp) {
        val cx = powerUp.position.x
        val cy = powerUp.position.y
        painter.color(GameRenderer.powerUpColor(powerUp.type))
        painter.disc(cx, cy, powerUp.radius)
        painter.color(Color.WHITE, 0.35f)
        painter.glow(cx - powerUp.radius * 0.35f, cy - powerUp.radius * 0.4f, powerUp.radius * 0.55f)
        // The letter label scales with the power-up, like the old textSize = radius.
        val size = Atlas.ICON_CELL * (powerUp.radius / 16f) / Atlas.RACE_ICON_SCALE
        painter.color(Color.WHITE)
        painter.region(painter.atlas.raceIcons[powerUp.type.ordinal], cx, cy, size, size)
    }

    private fun drawFlashes(dt: Float) {
        val iterator = flashes.iterator()
        while (iterator.hasNext()) {
            val flash = iterator.next()
            flash.ttl -= dt
            if (flash.ttl <= 0f) {
                iterator.remove()
                continue
            }
            val progress = 1f - flash.ttl / flash.maxTtl
            painter.color(flash.color, 0.7f * (1f - progress))
            painter.glow(flash.x, flash.y, flash.radius * (0.6f + 0.4f * progress))
        }
    }

    private fun drawFloatingTexts(dt: Float) {
        val iterator = floatingTexts.iterator()
        while (iterator.hasNext()) {
            val text = iterator.next()
            text.elapsed += dt
            if (text.elapsed >= text.duration) {
                iterator.remove()
                continue
            }
            val progress = text.elapsed / text.duration
            val y = text.y - progress * 60f
            painter.text(text.text, text.x + 2f, y + 2f, FLOATING_TEXT_STYLE, Color.BLACK, (1f - progress) * 0.5f)
            painter.text(text.text, text.x, y, FLOATING_TEXT_STYLE, text.color, 1f - progress)
        }
    }

    private fun drawHud(engine: RaceEngine, width: Float) {
        val player = engine.player
        val currentLap = (player.lapsCompleted + 1).coerceAtMost(engine.totalLaps)
        if (currentLap != hudLapValue) {
            hudLapValue = currentLap
            hudLapText = context.getString(R.string.race_hud_lap_format, currentLap, engine.totalLaps)
        }
        hudText(hudLapText, 24f, 56f, HUD_LEFT_STYLE)
        val racers = engine.aliveCount()
        if (racers != hudRacersValue) {
            hudRacersValue = racers
            hudRacersText = context.getString(R.string.race_hud_racers_format, racers)
        }
        hudText(hudRacersText, 24f, 96f, HUD_LEFT_STYLE)
        val elapsedSeconds = engine.matchElapsed.toInt()
        if (elapsedSeconds != hudTimeValue) {
            hudTimeValue = elapsedSeconds
            hudTimeText = "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)
        }
        hudText(hudTimeText, width - 24f, 56f, HUD_RIGHT_STYLE)
    }

    private fun hudText(value: String, x: Float, y: Float, style: TextStyle) {
        painter.text(value, x, y, style, hudTextColor)
    }

    // A simple always-visible overview: the track's shape scaled down to fit a small panel,
    // plus a dot per live racer (the player's own ringed in white).
    private fun drawMinimap(engine: RaceEngine, width: Float) {
        val left = width - MINIMAP_MARGIN_RIGHT - MINIMAP_WIDTH
        val top = MINIMAP_TOP
        painter.color(MINIMAP_BACKGROUND_COLOR)
        painter.rect(left - MINIMAP_PADDING, top - MINIMAP_PADDING, MINIMAP_WIDTH + MINIMAP_PADDING * 2f, MINIMAP_HEIGHT + MINIMAP_PADDING * 2f)
        painter.color(MINIMAP_BORDER_COLOR)
        painter.rectStroke(left - MINIMAP_PADDING, top - MINIMAP_PADDING, MINIMAP_WIDTH + MINIMAP_PADDING * 2f, MINIMAP_HEIGHT + MINIMAP_PADDING * 2f, 3f)

        painter.color(TRACK_SURFACE_COLOR)
        var i = 0
        while (i < trackSegments.size) {
            val x1 = left + trackSegments[i] * MINIMAP_SCALE
            val y1 = top + trackSegments[i + 1] * MINIMAP_SCALE
            painter.line(x1, y1, left + trackSegments[i + 2] * MINIMAP_SCALE, top + trackSegments[i + 3] * MINIMAP_SCALE, 5f)
            painter.disc(x1, y1, 2.5f)
            i += 4
        }
        for (blob in engine.blobs) {
            if (!blob.alive) continue
            val x = left + blob.position.x * MINIMAP_SCALE
            val y = top + blob.position.y * MINIMAP_SCALE
            painter.color(blob.color)
            painter.disc(x, y, MINIMAP_DOT_RADIUS)
            if (blob === engine.player) {
                painter.color(Color.WHITE)
                painter.circleStroke(x, y, MINIMAP_DOT_RADIUS * 1.7f, 3f)
            }
        }
    }

    // The dimmed backdrop (text = false), then the number itself (text = true).
    private fun drawCountdown(width: Float, height: Float, text: Boolean) {
        if (!countdownActive) return
        if (!text) {
            painter.color(Color.argb(120, 0, 0, 0))
            painter.rect(0f, 0f, width, height)
            painter.additive(true)
            painter.color(Color.WHITE, 0.18f)
            painter.glow(width / 2f, height / 2f, 200f)
            painter.additive(false)
            return
        }
        val secondsLeft = ceil(countdownRemaining).toInt().coerceAtLeast(1)
        val intoSecond = 1f - (countdownRemaining - (secondsLeft - 1))
        val punch = 1f + 0.35f * (1f - intoSecond.coerceIn(0f, 1f) / 0.25f).coerceAtLeast(0f)
        painter.text(secondsLeft.toString(), width / 2f, height / 2f + 60f, COUNTDOWN_STYLE, Color.WHITE, 1f, punch)
    }

    private fun checkBoostAvailability(engine: RaceEngine) {
        val available = engine.player.radius > RaceConfig.BASE_RADIUS * 0.6f
        if (available != lastBoostAvailable) {
            lastBoostAvailable = available
            view.post { view.callback?.onBoostAvailabilityChanged(available) }
        }
    }

    private fun checkCarriedItemAvailability(engine: RaceEngine) {
        val type = engine.player.carriedItem
        if (type != lastCarriedItemType) {
            lastCarriedItemType = type
            view.post { view.callback?.onCarriedItemChanged(type) }
        }
    }

    companion object {
        private const val PARTICLE_CAPACITY = 1200
        private const val SETTLE_AFTER_END_SECONDS = 2f
        private const val CURB_THICKNESS = 14f
        private const val TRACK_EDGE_WIDTH = 2.5f
        private const val BAND_OVERLAP = 3f
        private const val DASH_ON = 46f
        private const val DASH_OFF = 34f

        private const val BACKGROUND_R = 0x0F / 255f
        private const val BACKGROUND_G = 0x16 / 255f
        private const val BACKGROUND_B = 0x20 / 255f
        private val BACKGROUND_COLOR = Color.parseColor("#0F1620")
        // The shoulder was a translucent #332F3B45 over the background; drawn here as the
        // equivalent opaque color, so overlapping segments and joints never darken each other.
        private val TRACK_SHOULDER_COLOR = blendOver(Color.parseColor("#332F3B45"), BACKGROUND_COLOR)
        private val TRACK_CURB_COLOR = Color.parseColor("#ECEFF1")
        private val TRACK_SURFACE_COLOR = Color.parseColor("#4A5A6A")
        private val TRACK_CENTER_LINE_COLOR = Color.parseColor("#FFD54F")
        private val DEFLATE_SMOKE_COLOR = Color.parseColor("#9AA7B2")

        // A small panel tucked in the top-right corner, below the pause button and timer.
        private const val MINIMAP_WIDTH = 130f
        private const val MINIMAP_HEIGHT = MINIMAP_WIDTH * (RaceConfig.WORLD_HEIGHT / RaceConfig.WORLD_WIDTH)
        private const val MINIMAP_SCALE = MINIMAP_WIDTH / RaceConfig.WORLD_WIDTH
        private const val MINIMAP_MARGIN_RIGHT = 20f
        private const val MINIMAP_TOP = 150f
        private const val MINIMAP_PADDING = 10f
        private const val MINIMAP_DOT_RADIUS = 7f
        private val MINIMAP_BACKGROUND_COLOR = Color.argb(175, 10, 16, 22)
        private val MINIMAP_BORDER_COLOR = Color.parseColor("#66FFFFFF")

        private val FLOATING_TEXT_STYLE = TextStyle(32f, bold = true, align = TextStyle.Align.CENTER)
        private val HUD_LEFT_STYLE = TextStyle(40f)
        private val HUD_RIGHT_STYLE = TextStyle(40f, align = TextStyle.Align.RIGHT)
        private val COUNTDOWN_STYLE = TextStyle(140f, bold = true, align = TextStyle.Align.CENTER)

        private fun blendOver(foreground: Int, background: Int): Int {
            val a = Color.alpha(foreground) / 255f
            fun mix(f: Int, b: Int) = (f * a + b * (1f - a)).toInt().coerceIn(0, 255)
            return Color.rgb(
                mix(Color.red(foreground), Color.red(background)),
                mix(Color.green(foreground), Color.green(background)),
                mix(Color.blue(foreground), Color.blue(background))
            )
        }
    }
}
