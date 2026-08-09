package com.hyperionsoftware.balls.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Region
import android.media.AudioManager
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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, JoystickView.Listener {

    interface Callback {
        fun onGameOver(playerWon: Boolean, finalRadius: Int, playersRemaining: Int, opponentsAbsorbed: Int)
    }

    var callback: Callback? = null

    private lateinit var engine: GameEngine
    private var loopThread: GameThread? = null
    private var surfaceReady = false
    private var started = false

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

    private val floorCellSize = 200f
    private val floorColorA = Color.parseColor("#121B26")
    private val floorColorB = Color.parseColor("#0B1119")
    private val floorPaint = Paint()
    private val borderPaint = Paint().apply {
        color = Color.parseColor("#FFC107")
        style = Paint.Style.STROKE
        strokeWidth = 24f
    }
    private val blobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val rollPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
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

        blobPaint.color = blob.color
        blobPaint.alpha = alpha
        canvas.drawCircle(cx, cy, blob.radius, blobPaint)

        drawRollingPattern(canvas, blob, cx, cy, alpha)

        eyePaint.alpha = alpha
        val eyeOffset = blob.radius * 0.35f
        canvas.drawCircle(cx - eyeOffset, cy - eyeOffset * 0.6f, blob.radius * 0.14f, eyePaint)
        canvas.drawCircle(cx + eyeOffset, cy - eyeOffset * 0.6f, blob.radius * 0.14f, eyePaint)
    }

    private fun drawRollingPattern(canvas: Canvas, blob: Blob, cx: Float, cy: Float, alpha: Int) {
        // Two spokes rotating with blob.rotation sell the illusion of the ball rolling
        // across the floor as it moves, on top of the (non-rotating) face.
        rollPaint.color = darken(blob.color, 0.65f)
        rollPaint.alpha = (alpha * 0.8f).toInt()
        rollPaint.strokeWidth = blob.radius * 0.08f

        val spokeRadius = blob.radius * 0.85f
        for (spoke in 0 until 2) {
            val angle = blob.rotation + spoke * (Math.PI / 2).toFloat()
            val dx = cos(angle) * spokeRadius
            val dy = sin(angle) * spokeRadius
            canvas.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, rollPaint)
        }
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
