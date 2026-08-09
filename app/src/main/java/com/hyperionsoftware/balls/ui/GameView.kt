package com.hyperionsoftware.balls.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Region
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.hyperionsoftware.balls.game.Blob
import com.hyperionsoftware.balls.game.GameConfig
import com.hyperionsoftware.balls.game.GameEngine
import com.hyperionsoftware.balls.game.GameListener
import com.hyperionsoftware.balls.game.PowerUp
import com.hyperionsoftware.balls.game.PowerUpType
import com.hyperionsoftware.balls.game.Vector2
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, JoystickView.Listener {

    interface Callback {
        fun onGameOver(playerWon: Boolean, finalRadius: Int, playersRemaining: Int, opponentsAbsorbed: Int)
        fun onBoostAvailabilityChanged(available: Boolean)
    }

    var callback: Callback? = null

    private lateinit var engine: GameEngine
    private var loopThread: GameThread? = null
    private var surfaceReady = false
    private var started = false
    private var lastBoostAvailable = false

    private var countdownActive = false
    private var countdownRemaining = 0f

    private class FloatingText(
        var x: Float,
        var y: Float,
        val text: String,
        val color: Int,
        var ttl: Float,
        val maxTtl: Float = ttl
    )

    private val floatingTexts = mutableListOf<FloatingText>()

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private val toneGenerator: ToneGenerator by lazy { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }
    private var hissTrack: AudioTrack? = null

    private val floorCellSize = 200f
    private val floorColorA = Color.parseColor("#121B26")
    private val floorColorB = Color.parseColor("#0B1119")
    private val floorPaint = Paint()
    private val borderPaint = Paint().apply {
        color = Color.parseColor("#FFC107")
        style = Paint.Style.STROKE
        strokeWidth = 24f
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val knotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val exhaustPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#B3E5FC") }
    private val speedBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFEB3B") }
    private val stringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#CFD8DC")
    }

    // Balloons are subtly egg-shaped along their direction of travel instead of perfect
    // circles - stretched on the facing axis, squashed on the perpendicular one.
    private val balloonStretch = 1.12f
    private val balloonSquash = 0.94f
    private val balloonMatrix = Matrix()
    private val safeZoneFillColor = Color.argb(90, 200, 40, 40)
    private val safeZonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF5350")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val powerUpPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2.5f
    }
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val indicatorArrow = Path().apply {
        moveTo(0f, -18f)
        lineTo(14f, 14f)
        lineTo(-14f, 14f)
        close()
    }
    private val floatingTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 34f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val hudTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
        isFakeBoldText = true
    }
    private val zoneHudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF5350")
        textSize = 34f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val countdownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 180f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private var lastDirection = Vector2(0f, 0f)

    init {
        holder.addCallback(this)
    }

    fun startGame(botCount: Int, powerUpFrequencyLevel: Int) {
        if (started) return
        started = true
        floatingTexts.clear()
        countdownActive = true
        countdownRemaining = GameConfig.COUNTDOWN_SECONDS
        lastBoostAvailable = false
        engine = GameEngine(
            botCount = botCount,
            powerUpFrequencyLevel = powerUpFrequencyLevel,
            listener = object : GameListener {
                override fun onVibrate() {
                    vibrateBounce()
                }

                override fun onAbsorb(x: Float, y: Float, sizeGain: Int, byPlayer: Boolean) {
                    val color = if (byPlayer) Color.parseColor("#8BC34A") else Color.WHITE
                    floatingTexts.add(FloatingText(x, y, "+$sizeGain", color, 1.2f))
                    if (byPlayer) {
                        vibrateAbsorb()
                        toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 150)
                    }
                }

                override fun onPowerUpCollected(x: Float, y: Float, type: PowerUpType, byPlayer: Boolean) {
                    val label = when (type) {
                        PowerUpType.SPEED -> "Velocità!"
                        PowerUpType.GROWTH -> "Ingrandimento!"
                        PowerUpType.INVISIBILITY -> "Invisibilità!"
                    }
                    floatingTexts.add(FloatingText(x, y, label, Color.parseColor("#FFD54F"), 1.4f))
                    if (byPlayer) {
                        vibratePowerUp()
                        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                    }
                }

                override fun onZoneDeath(x: Float, y: Float, wasPlayer: Boolean) {
                    floatingTexts.add(FloatingText(x, y, "Eliminato!", Color.parseColor("#B71C1C"), 1.3f))
                }

                override fun onBoostDeath(x: Float, y: Float, wasPlayer: Boolean) {
                    floatingTexts.add(FloatingText(x, y, "Sgonfiato!", Color.parseColor("#FF6F00"), 1.3f))
                }

                override fun onGameOver(
                    playerWon: Boolean,
                    finalRadius: Float,
                    playersRemaining: Int,
                    opponentsAbsorbed: Int
                ) {
                    loopThread?.running = false
                    toneGenerator.startTone(
                        if (playerWon) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK,
                        400
                    )
                    post { callback?.onGameOver(playerWon, finalRadius.toInt(), playersRemaining, opponentsAbsorbed) }
                }
            }
        )
        if (surfaceReady) {
            launchLoop()
        }
    }

    fun pauseGame() {
        loopThread?.running = false
    }

    fun resumeGame() {
        if (started && surfaceReady && (loopThread == null || !loopThread!!.isAlive)) {
            launchLoop()
        }
    }

    fun restart(botCount: Int, powerUpFrequencyLevel: Int) {
        loopThread?.running = false
        loopThread?.join(500)
        started = false
        startGame(botCount, powerUpFrequencyLevel)
    }

    fun setBoosting(active: Boolean) {
        if (started) {
            engine.player.isBoosting = active
        }
        if (active) {
            try {
                val track = hissTrack ?: createHissTrack().also { hissTrack = it }
                track.setPlaybackHeadPosition(0)
                track.play()
            } catch (_: Exception) {
                // Audio device may be briefly unavailable; the visual/vibration
                // feedback still lands, so silently skip the sound this time.
            }
        } else {
            try {
                hissTrack?.pause()
                hissTrack?.setPlaybackHeadPosition(0)
            } catch (_: Exception) {
            }
        }
    }

    // A synthesized air-escaping hiss for sprinting: soft-clipped white noise looped
    // seamlessly, closer to a deflating balloon than raw hard noise or a beep.
    private fun createHissTrack(): AudioTrack {
        val sampleRate = 22050
        val frameCount = sampleRate / 2
        val buffer = ShortArray(frameCount)
        val random = java.util.Random()
        for (i in buffer.indices) {
            val softened = (random.nextFloat() + random.nextFloat() + random.nextFloat() - 1.5f) / 1.5f
            buffer[i] = (softened * Short.MAX_VALUE * 0.35f).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(buffer.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(buffer, 0, buffer.size)
        track.setLoopPoints(0, frameCount, -1)
        return track
    }

    private fun checkBoostAvailability() {
        // Sprint now works everywhere, any time, with no floor at baseRadius - only
        // dim the button once there's truly nothing left to burn without dying.
        val player = engine.player
        val available = player.alive && player.radius > GameConfig.ZONE_DEATH_RADIUS + 0.5f
        if (available != lastBoostAvailable) {
            lastBoostAvailable = available
            post { callback?.onBoostAvailabilityChanged(available) }
        }
    }

    override fun onDirectionChanged(x: Float, y: Float) {
        lastDirection = Vector2(x, y)
        if (started) {
            engine.player.inputDirection = lastDirection
        }
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

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        hissTrack?.release()
        hissTrack = null
    }

    private fun launchLoop() {
        loopThread?.running = false
        loopThread?.join(500)
        loopThread = GameThread().also { it.start() }
    }

    private fun vibrateBounce() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun vibrateAbsorb() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 40, 60), -1))
        }
    }

    private fun vibratePowerUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(35, 90))
        }
    }

    private inner class GameThread : Thread("GameLoop") {
        @Volatile var running = true

        override fun run() {
            var lastTime = System.nanoTime()
            while (running) {
                val now = System.nanoTime()
                var dt = (now - lastTime) / 1_000_000_000f
                lastTime = now
                dt = min(dt, 0.05f)

                if (countdownActive) {
                    countdownRemaining -= dt
                    if (countdownRemaining <= 0f) countdownActive = false
                } else {
                    engine.update(dt)
                    checkBoostAvailability()
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

        val player = engine.player
        val camX = cameraCoord(player.position.x, width, GameConfig.WORLD_WIDTH)
        val camY = cameraCoord(player.position.y, height, GameConfig.WORLD_HEIGHT)
        val offsetX = width / 2f - camX
        val offsetY = height / 2f - camY

        drawFloor(canvas, offsetX, offsetY)
        drawWorldBorder(canvas, offsetX, offsetY)
        drawSafeZone(canvas, offsetX, offsetY)

        for (powerUp in engine.powerUps) {
            drawPowerUp(canvas, powerUp, offsetX, offsetY)
        }

        for (blob in engine.blobs) {
            if (blob.alive) drawBlob(canvas, blob, offsetX, offsetY)
        }

        drawDangerIndicator(canvas, offsetX, offsetY)
        drawFloatingTexts(canvas, offsetX, offsetY, dt)
        drawHud(canvas)
        drawCountdown(canvas)
    }

    private fun cameraCoord(playerCoord: Float, viewportSize: Int, worldSize: Float): Float {
        val halfViewport = viewportSize / 2f
        if (worldSize <= viewportSize) return worldSize / 2f
        return playerCoord.coerceIn(halfViewport, worldSize - halfViewport)
    }

    private fun drawFloor(canvas: Canvas, offsetX: Float, offsetY: Float) {
        // Checkerboard tiles give the arena a visible floor instead of an empty void,
        // and double as a scale reference while moving. Only the cells actually on
        // screen are drawn, so this stays cheap regardless of world size.
        val startCol = ((-offsetX).coerceAtLeast(0f) / floorCellSize).toInt()
        val endCol = ((width - offsetX).coerceAtMost(GameConfig.WORLD_WIDTH) / floorCellSize).toInt()
        val startRow = ((-offsetY).coerceAtLeast(0f) / floorCellSize).toInt()
        val endRow = ((height - offsetY).coerceAtMost(GameConfig.WORLD_HEIGHT) / floorCellSize).toInt()

        for (row in startRow..endRow) {
            for (col in startCol..endCol) {
                floorPaint.color = if ((row + col) % 2 == 0) floorColorA else floorColorB
                val left = col * floorCellSize + offsetX
                val top = row * floorCellSize + offsetY
                canvas.drawRect(left, top, left + floorCellSize, top + floorCellSize, floorPaint)
            }
        }
    }

    private fun drawWorldBorder(canvas: Canvas, offsetX: Float, offsetY: Float) {
        canvas.drawRect(
            offsetX,
            offsetY,
            GameConfig.WORLD_WIDTH + offsetX,
            GameConfig.WORLD_HEIGHT + offsetY,
            borderPaint
        )
    }

    private fun drawSafeZone(canvas: Canvas, offsetX: Float, offsetY: Float) {
        val cx = engine.safeZoneCenterX + offsetX
        val cy = engine.safeZoneCenterY + offsetY
        val r = engine.safeZoneRadius

        // Dim everything outside the shrinking safe circle so the danger area reads
        // clearly, then stroke its edge. SurfaceView's canvas is a plain software
        // bitmap canvas (not the accelerated View pipeline), so DIFFERENCE clipping
        // is safe here.
        canvas.save()
        val path = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
        @Suppress("DEPRECATION")
        canvas.clipPath(path, Region.Op.DIFFERENCE)
        canvas.drawColor(safeZoneFillColor)
        canvas.restore()

        canvas.drawCircle(cx, cy, r, safeZonePaint)
    }

    private fun drawBlob(canvas: Canvas, blob: Blob, offsetX: Float, offsetY: Float) {
        val cx = blob.position.x + offsetX
        val cy = blob.position.y + offsetY
        val alpha = if (blob.isInvisible) 70 else 255

        drawExhaust(canvas, blob, cx, cy, alpha)
        drawBalloonBody(canvas, blob, cx, cy, alpha)
        drawSpeedBadge(canvas, blob, cx, cy, alpha)
        drawKnot(canvas, blob, cx, cy, alpha)
        drawString(canvas, blob, cx, cy, alpha)
    }

    private fun drawBalloonBody(canvas: Canvas, blob: Blob, cx: Float, cy: Float, alpha: Int) {
        bodyPaint.color = blob.color
        bodyPaint.alpha = alpha

        // The true silhouette is an egg shape stretched along the facing axis, built as a
        // world-space path via a rotation matrix. Shading/highlight below clip to this same
        // path but are drawn without rotating the canvas, so the light direction stays fixed
        // regardless of which way the balloon is pointed.
        val angleDeg = Math.toDegrees(atan2(blob.facingDirection.y, blob.facingDirection.x).toDouble()).toFloat() + 90f
        balloonMatrix.reset()
        balloonMatrix.postScale(balloonSquash, balloonStretch)
        balloonMatrix.postRotate(angleDeg)
        balloonMatrix.postTranslate(cx, cy)

        val localOutline = Path().apply { addCircle(0f, 0f, blob.radius, Path.Direction.CW) }
        val worldOutline = Path()
        localOutline.transform(balloonMatrix, worldOutline)

        canvas.drawPath(worldOutline, bodyPaint)

        canvas.save()
        canvas.clipPath(worldOutline)

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

    private fun drawSpeedBadge(canvas: Canvas, blob: Blob, cx: Float, cy: Float, alpha: Int) {
        // A lightning bolt imprinted on the balloon signals the SPEED power-up is active -
        // distinct from the exhaust, which signals actual sprint usage instead.
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

    private fun drawKnot(canvas: Canvas, blob: Blob, cx: Float, cy: Float, alpha: Int) {
        // The knot (where the air comes from) sits at the back, opposite whichever way
        // the balloon is currently facing - right on the stretched egg's pointed end.
        val back = blob.facingDirection * -1f
        val edgeRadius = blob.radius * balloonStretch
        val knotSize = blob.radius * 0.22f
        val baseX = cx + back.x * edgeRadius * 0.85f
        val baseY = cy + back.y * edgeRadius * 0.85f
        val tipX = cx + back.x * (edgeRadius + knotSize)
        val tipY = cy + back.y * (edgeRadius + knotSize)
        val perpX = -back.y * knotSize * 0.5f
        val perpY = back.x * knotSize * 0.5f

        knotPaint.color = darken(blob.color, 0.55f)
        knotPaint.alpha = alpha
        val path = Path().apply {
            moveTo(baseX + perpX, baseY + perpY)
            lineTo(baseX - perpX, baseY - perpY)
            lineTo(tipX, tipY)
            close()
        }
        canvas.drawPath(path, knotPaint)
    }

    private fun drawString(canvas: Canvas, blob: Blob, cx: Float, cy: Float, alpha: Int) {
        // A thin string dangling from the knot, swaying gently, for balloon realism.
        val back = blob.facingDirection * -1f
        val edgeRadius = blob.radius * balloonStretch
        val knotSize = blob.radius * 0.22f
        val startX = cx + back.x * (edgeRadius + knotSize)
        val startY = cy + back.y * (edgeRadius + knotSize)
        val length = blob.radius * 0.9f
        val sway = sin(blob.exhaustPhase * 2.3f) * blob.radius * 0.15f

        stringPaint.alpha = (alpha * 0.6f).toInt()
        val path = Path().apply {
            moveTo(startX, startY)
            quadTo(
                startX + back.x * length * 0.5f + sway, startY + back.y * length * 0.5f,
                startX + back.x * length + sway * 0.6f, startY + back.y * length
            )
        }
        canvas.drawPath(path, stringPaint)
    }

    private fun drawExhaust(canvas: Canvas, blob: Blob, cx: Float, cy: Float, alpha: Int) {
        if (!blob.isThrusting && !blob.isBoosting) return

        // Sprinting produces a visibly different exhaust (larger, fiercer, orange) than
        // ordinary movement (calm pale blue), so it's obvious at a glance who is burning
        // size for speed versus just cruising.
        val boosting = blob.isBoosting
        val back = blob.facingDirection * -1f
        val pulse = 0.7f + 0.3f * sin(blob.exhaustPhase * (if (boosting) 22f else 14f))
        val intensity = if (boosting) 1.7f else 1f
        val edgeRadius = blob.radius * balloonStretch
        val length = blob.radius * (1.4f + 0.5f * pulse) * intensity
        val width = blob.radius * 0.5f * pulse * intensity

        val baseX = cx + back.x * edgeRadius
        val baseY = cy + back.y * edgeRadius
        val tipX = cx + back.x * (edgeRadius + length)
        val tipY = cy + back.y * (edgeRadius + length)
        val perpX = -back.y * width
        val perpY = back.x * width

        exhaustPaint.color = if (boosting) Color.parseColor("#FF7043") else Color.parseColor("#B3E5FC")
        exhaustPaint.alpha = (alpha * (if (boosting) 0.75f else 0.5f) * pulse).toInt().coerceIn(0, 255)
        val path = Path().apply {
            moveTo(baseX + perpX, baseY + perpY)
            lineTo(tipX, tipY)
            lineTo(baseX - perpX, baseY - perpY)
            close()
        }
        canvas.drawPath(path, exhaustPaint)
    }

    private fun darken(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun drawPowerUp(canvas: Canvas, powerUp: PowerUp, offsetX: Float, offsetY: Float) {
        powerUpPaint.color = when (powerUp.type) {
            PowerUpType.SPEED -> Color.parseColor("#FFD54F")
            PowerUpType.GROWTH -> Color.parseColor("#81C784")
            PowerUpType.INVISIBILITY -> Color.parseColor("#B39DDB")
        }
        val cx = powerUp.position.x + offsetX
        val cy = powerUp.position.y + offsetY
        canvas.drawCircle(cx, cy, powerUp.radius, powerUpPaint)
        drawPowerUpIcon(canvas, powerUp, cx, cy)
    }

    private fun drawPowerUpIcon(canvas: Canvas, powerUp: PowerUp, cx: Float, cy: Float) {
        iconPaint.color = Color.WHITE
        when (powerUp.type) {
            PowerUpType.SPEED -> {
                // Two right-pointing chevrons suggest motion.
                for (i in 0..1) {
                    val ox = cx - 5f + i * 7f
                    canvas.drawLine(ox - 4f, cy - 6f, ox + 4f, cy, iconPaint)
                    canvas.drawLine(ox + 4f, cy, ox - 4f, cy + 6f, iconPaint)
                }
            }
            PowerUpType.GROWTH -> {
                canvas.drawLine(cx - 7f, cy, cx + 7f, cy, iconPaint)
                canvas.drawLine(cx, cy - 7f, cx, cy + 7f, iconPaint)
            }
            PowerUpType.INVISIBILITY -> {
                iconPaint.style = Paint.Style.STROKE
                canvas.drawCircle(cx, cy, 7f, iconPaint)
                iconPaint.style = Paint.Style.FILL
                canvas.drawLine(cx - 8f, cy - 8f, cx + 8f, cy + 8f, iconPaint)
            }
        }
    }

    private fun drawDangerIndicator(canvas: Canvas, offsetX: Float, offsetY: Float) {
        val player = engine.player
        var threat: Blob? = null
        var threatDistance = Float.MAX_VALUE
        var prey: Blob? = null
        var preyDistance = Float.MAX_VALUE

        for (blob in engine.blobs) {
            if (blob === player || !blob.alive || blob.isInvisible) continue
            val distance = player.position.distanceTo(blob.position)
            if (distance > GameConfig.AWARENESS_RADIUS) continue

            if (blob.radius / player.radius >= GameConfig.ABSORB_RATIO && distance < threatDistance) {
                threat = blob
                threatDistance = distance
            } else if (player.radius / blob.radius >= GameConfig.ABSORB_RATIO && distance < preyDistance) {
                prey = blob
                preyDistance = distance
            }
        }

        val target = threat ?: prey ?: return
        val margin = 60f
        val screenX = target.position.x + offsetX
        val screenY = target.position.y + offsetY
        // Already visible on screen: no need for an off-screen indicator.
        if (screenX in -margin..(width + margin) && screenY in -margin..(height + margin)) return

        val dirX = target.position.x - player.position.x
        val dirY = target.position.y - player.position.y
        val length = hypot(dirX, dirY)
        if (length < 0.001f) return
        val ndx = dirX / length
        val ndy = dirY / length

        val halfW = width / 2f - margin
        val halfH = height / 2f - margin
        val tx = if (ndx != 0f) halfW / abs(ndx) else Float.MAX_VALUE
        val ty = if (ndy != 0f) halfH / abs(ndy) else Float.MAX_VALUE
        val t = min(tx, ty)
        val px = width / 2f + ndx * t
        val py = height / 2f + ndy * t

        indicatorPaint.color = if (target === threat) Color.parseColor("#EF5350") else Color.parseColor("#8BC34A")
        val angleDeg = Math.toDegrees(atan2(ndy, ndx).toDouble()).toFloat() + 90f

        canvas.save()
        canvas.translate(px, py)
        canvas.rotate(angleDeg)
        canvas.drawPath(indicatorArrow, indicatorPaint)
        canvas.restore()
    }

    private fun drawFloatingTexts(canvas: Canvas, offsetX: Float, offsetY: Float, dt: Float) {
        val iterator = floatingTexts.iterator()
        while (iterator.hasNext()) {
            val text = iterator.next()
            text.ttl -= dt
            if (text.ttl <= 0f) {
                iterator.remove()
                continue
            }
            text.y -= 30f * dt
            floatingTextPaint.color = text.color
            floatingTextPaint.alpha = (255 * (text.ttl / text.maxTtl)).toInt().coerceIn(0, 255)
            canvas.drawText(text.text, text.x + offsetX, text.y + offsetY, floatingTextPaint)
        }
    }

    private fun drawHud(canvas: Canvas) {
        val player = engine.player
        canvas.drawText("Dimensione: ${player.radius.toInt()}", 24f, 56f, hudTextPaint)

        val playersText = "Giocatori: ${engine.aliveCount()}"
        val textWidth = hudTextPaint.measureText(playersText)
        canvas.drawText(playersText, width - textWidth - 24f, 56f, hudTextPaint)

        val zonePercent = (engine.safeZoneProgress * 100f).toInt()
        canvas.drawText("Zona: $zonePercent%", width / 2f, 56f, zoneHudPaint)
    }

    private fun drawCountdown(canvas: Canvas) {
        if (!countdownActive) return
        canvas.drawColor(Color.argb(120, 0, 0, 0))
        val secondsLeft = ceil(countdownRemaining).toInt().coerceAtLeast(1)
        canvas.drawText(secondsLeft.toString(), width / 2f, height / 2f + 60f, countdownPaint)
    }
}
