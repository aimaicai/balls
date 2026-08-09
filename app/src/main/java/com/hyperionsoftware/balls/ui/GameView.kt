package com.hyperionsoftware.balls.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, JoystickView.Listener {

    interface Callback {
        fun onGameOver(playerWon: Boolean, finalRadius: Int, playersRemaining: Int)
    }

    var callback: Callback? = null

    private lateinit var engine: GameEngine
    private var loopThread: GameThread? = null
    private var surfaceReady = false
    private var started = false

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

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
    private val powerUpPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hudTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
        isFakeBoldText = true
    }

    private var lastDirection = Vector2(0f, 0f)

    init {
        holder.addCallback(this)
    }

    fun startGame(botCount: Int) {
        if (started) return
        started = true
        engine = GameEngine(
            botCount = botCount,
            listener = object : GameListener {
                override fun onVibrate() {
                    triggerVibration()
                }

                override fun onGameOver(playerWon: Boolean, finalRadius: Float, playersRemaining: Int) {
                    loopThread?.running = false
                    post { callback?.onGameOver(playerWon, finalRadius.toInt(), playersRemaining) }
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

    fun restart(botCount: Int) {
        loopThread?.running = false
        loopThread?.join(500)
        started = false
        startGame(botCount)
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

    private fun triggerVibration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
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

                engine.update(dt)
                drawFrame()

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

    private fun drawFrame() {
        val canvas = try {
            holder.lockCanvas()
        } catch (_: Exception) {
            null
        } ?: return
        try {
            render(canvas)
        } finally {
            try {
                holder.unlockCanvasAndPost(canvas)
            } catch (_: Exception) {
                // Surface may have been torn down mid-frame; nothing to recover.
            }
        }
    }

    private fun render(canvas: Canvas) {
        canvas.drawColor(Color.parseColor("#0F1620"))

        val player = engine.player
        val camX = cameraCoord(player.position.x, width, GameConfig.WORLD_WIDTH)
        val camY = cameraCoord(player.position.y, height, GameConfig.WORLD_HEIGHT)
        val offsetX = width / 2f - camX
        val offsetY = height / 2f - camY

        drawFloor(canvas, offsetX, offsetY)
        drawWorldBorder(canvas, offsetX, offsetY)

        for (powerUp in engine.powerUps) {
            drawPowerUp(canvas, powerUp, offsetX, offsetY)
        }

        for (blob in engine.blobs) {
            if (blob.alive) drawBlob(canvas, blob, offsetX, offsetY)
        }

        drawHud(canvas)
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
        canvas.drawCircle(powerUp.position.x + offsetX, powerUp.position.y + offsetY, powerUp.radius, powerUpPaint)
    }

    private fun drawHud(canvas: Canvas) {
        val player = engine.player
        canvas.drawText("Dimensione: ${player.radius.toInt()}", 24f, 56f, hudTextPaint)

        val playersText = "Giocatori: ${engine.aliveCount()}"
        val textWidth = hudTextPaint.measureText(playersText)
        canvas.drawText(playersText, width - textWidth - 24f, 56f, hudTextPaint)
    }
}
