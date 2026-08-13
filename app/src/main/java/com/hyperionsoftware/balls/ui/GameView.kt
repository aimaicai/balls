package com.hyperionsoftware.balls.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
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
import com.hyperionsoftware.balls.R
import com.hyperionsoftware.balls.achievements.Achievement
import com.hyperionsoftware.balls.achievements.Achievements
import com.hyperionsoftware.balls.game.Blob
import com.hyperionsoftware.balls.game.GameConfig
import com.hyperionsoftware.balls.game.GameEngine
import com.hyperionsoftware.balls.game.GameListener
import com.hyperionsoftware.balls.game.PowerUp
import com.hyperionsoftware.balls.game.PowerUpType
import com.hyperionsoftware.balls.game.Vector2
import com.hyperionsoftware.balls.score.HighScores
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
    }

    var callback: Callback? = null

    private lateinit var engine: GameEngine
    private var loopThread: GameThread? = null
    private var surfaceReady = false
    private var started = false
    private var lastBoostAvailable = false
    private var lastCarriedItemType: PowerUpType? = null
    private var botNames: List<String> = emptyList()

    private val achievementAbsorbStreakTarget = 5

    private var countdownActive = false
    private var countdownRemaining = 0f

    // Cinematic cut into the final round (see GameListener.onFinalRoundStarted): gameplay
    // freezes, same as the match-start countdown, while drawFinalRoundTransition plays a
    // title-card flash followed by its own 3-2-1.
    private var finalRoundTransitionActive = false
    private var finalRoundTransitionElapsed = 0f

    private class FloatingText(
        var x: Float,
        var y: Float,
        val text: String,
        val color: Int,
        var ttl: Float,
        val maxTtl: Float = ttl
    )

    private val floatingTexts = mutableListOf<FloatingText>()

    private class FeedEntry(val text: String, var ttl: Float, val maxTtl: Float = ttl)

    // A small elimination feed (like a battle-royale kill feed) reporting every absorb,
    // not just ones involving the player - so bot-vs-bot fights read as a living arena
    // instead of only mattering when they happen on screen.
    private val feedEntries = mutableListOf<FeedEntry>()

    private class EffectRipple(val x: Float, val y: Float, val maxRadius: Float, val ringColor: Int, var ttl: Float, val maxTtl: Float = ttl)

    // An expanding ring wherever a carried item (REPEL/FREEZE) gets used, so the burst
    // reads clearly even though it only affects things for an instant.
    private val effectRipples = mutableListOf<EffectRipple>()

    // The victim otherwise just vanishes the instant it dies (Blob.alive flips off and the
    // render loop skips it) - this shrinks and pulls it toward whoever's still eating it,
    // so an absorption actually reads as being swallowed instead of despawning.
    private class AbsorbAnim(
        val startX: Float,
        val startY: Float,
        val startRadius: Float,
        val color: Int,
        val absorber: Blob,
        var elapsed: Float = 0f,
        val duration: Float = 0.35f
    )

    private val absorbAnims = mutableListOf<AbsorbAnim>()

    // Shown as a stacked banner near the top of the screen, in screen space (not world
    // space like FloatingText) so it reads clearly regardless of where the player is.
    private class AchievementPopup(val text: String, var ttl: Float, val maxTtl: Float = ttl)

    private val achievementPopups = mutableListOf<AchievementPopup>()

    // Per-match latches for achievements that would otherwise need checking every frame -
    // once true, checkLiveAchievements stops re-testing that condition for the rest of
    // the match, on top of Achievements.unlock's own one-time-ever guard. FINAL_ROUND
    // doesn't need one of these: it's unlocked straight from onFinalRoundStarted instead.
    private var maxSizeAchievementChecked = false
    private var maxBoostAchievementChecked = false

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
    // Telegraphs the next, smaller circle during a zone hold phase, so there's time to
    // plan a rotation instead of the shrink just starting with no warning.
    private val nextZonePreviewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD54F")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(20f, 14f), 0f)
    }
    private val shieldAuraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4FC3F7")
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val frozenOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B3E5FC")
        style = Paint.Style.FILL
    }
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val absorbAnimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val supplyDropBeaconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4FC3F7")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val feedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
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
    private val timerHudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 30f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    // The headline stat, not just another line among Dimensione/Giocatori - centered,
    // larger than the timer above it, and in the app's own accent color (the same amber
    // used for buttons and the world border) rather than a plain HUD white.
    private val liveScoreHudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFC107")
        textSize = 40f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val statsHudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0BEC5")
        textSize = 26f
    }
    private val statPipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val statPipEmptyColor = Color.parseColor("#3A4750")
    private val countdownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 180f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val achievementPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD700")
        textSize = 32f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    // The final-round entrance card: red like the safe zone it's warning about, distinct
    // from the plain white match-start countdown so the two don't read as the same beat.
    private val finalRoundTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF5350")
        textSize = 58f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val finalRoundCountdownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFC107")
        textSize = 180f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    // The heads-up shown DURING normal play, before the freeze - smaller and drawn over
    // live gameplay rather than a full-screen cut, so it warns without blocking the view.
    private val finalRoundWarningTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF5350")
        textSize = 34f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val finalRoundWarningCountdownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFC107")
        textSize = 70f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private var lastDirection = Vector2(0f, 0f)
    private var beaconPhase = 0f

    init {
        holder.addCallback(this)
    }

    fun startGame(
        botCount: Int,
        powerUpFrequencyLevel: Int,
        arenaSize: GameConfig.ArenaSize = GameConfig.ArenaSize.NORMAL,
        skipToFinalRound: Boolean = false
    ) {
        if (started) return
        started = true
        floatingTexts.clear()
        absorbAnims.clear()
        achievementPopups.clear()
        maxSizeAchievementChecked = false
        maxBoostAchievementChecked = false
        finalRoundTransitionActive = false
        finalRoundTransitionElapsed = 0f
        // Skip-to-final-round testing gets its own dramatic entrance the instant the
        // engine sets it up (see onFinalRoundStarted) instead of the plain match-start
        // countdown - showing both back to back would be redundant.
        countdownActive = !skipToFinalRound
        countdownRemaining = GameConfig.COUNTDOWN_SECONDS
        lastBoostAvailable = false
        lastCarriedItemType = null
        botNames = BotNames.generate(botCount)
        engine = GameEngine(
            botCount = botCount,
            powerUpFrequencyLevel = powerUpFrequencyLevel,
            arenaSize = arenaSize,
            skipToFinalRound = skipToFinalRound,
            listener = object : GameListener {
                override fun onVibrate() {
                    vibrateBounce()
                }

                override fun onAbsorb(
                    x: Float,
                    y: Float,
                    sizeGain: Int,
                    byPlayer: Boolean,
                    absorberId: Int,
                    victimId: Int
                ) {
                    val color = if (byPlayer) Color.parseColor("#8BC34A") else Color.WHITE
                    floatingTexts.add(FloatingText(x, y, "+$sizeGain", color, 1.2f))
                    if (byPlayer) {
                        vibrateAbsorb()
                        toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 150)
                        unlockAchievement(Achievement.FIRST_ABSORB)
                    }
                    addFeedEntry("${blobLabel(absorberId)} ha inglobato ${blobLabel(victimId)}")

                    val victim = engine.blobs.find { it.id == victimId }
                    val absorber = engine.blobs.find { it.id == absorberId }
                    if (victim != null && absorber != null) {
                        absorbAnims.add(AbsorbAnim(x, y, victim.radius, victim.color, absorber))
                    }
                }

                override fun onPowerUpCollected(x: Float, y: Float, type: PowerUpType, byPlayer: Boolean) {
                    val label = when (type) {
                        PowerUpType.SPEED -> "Velocità pronta!"
                        PowerUpType.GROWTH -> "Ingrandimento!"
                        PowerUpType.INVISIBILITY -> "Invisibilità pronta!"
                        PowerUpType.SHIELD -> "Scudo!"
                        PowerUpType.REPEL -> "Respingi pronto!"
                        PowerUpType.FREEZE -> "Congela pronto!"
                        PowerUpType.HOOK -> "Aggancio pronto!"
                        PowerUpType.SPEED_UP -> "Velocità permanente!"
                        PowerUpType.AGILITY_UP -> "Agilità permanente!"
                    }
                    floatingTexts.add(FloatingText(x, y, label, Color.parseColor("#FFD54F"), 1.4f))
                    if (byPlayer) {
                        vibratePowerUp()
                        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                        when (type) {
                            // SHIELD is never a regular spawn, only ever a supply drop (see
                            // PowerUp.radius) - so collecting one always means this.
                            PowerUpType.SHIELD -> unlockAchievement(Achievement.SUPPLY_DROP)
                            PowerUpType.SPEED_UP -> if (engine.player.permanentSpeedMultiplier >=
                                GameConfig.PERMANENT_SPEED_MAX_MULTIPLIER
                            ) {
                                unlockAchievement(Achievement.MAX_SPEED_STAT)
                            }
                            PowerUpType.AGILITY_UP -> if (engine.player.permanentTurnRateMultiplier >=
                                GameConfig.PERMANENT_TURN_RATE_MAX_MULTIPLIER
                            ) {
                                unlockAchievement(Achievement.MAX_AGILITY_STAT)
                            }
                            else -> {}
                        }
                    }
                }

                override fun onActiveItemUsed(x: Float, y: Float, type: PowerUpType, byPlayer: Boolean) {
                    val label = when (type) {
                        PowerUpType.REPEL -> "Respinto!"
                        PowerUpType.FREEZE -> "Congelato!"
                        PowerUpType.HOOK -> "Agganciato!"
                        PowerUpType.SPEED -> "Velocità!"
                        PowerUpType.INVISIBILITY -> "Invisibilità!"
                        else -> return
                    }
                    val color = when (type) {
                        PowerUpType.REPEL -> Color.parseColor("#FFB74D")
                        PowerUpType.FREEZE -> Color.parseColor("#4FC3F7")
                        PowerUpType.HOOK -> Color.parseColor("#A1887F")
                        PowerUpType.SPEED -> Color.parseColor("#FFD54F")
                        else -> Color.parseColor("#B39DDB")
                    }
                    floatingTexts.add(FloatingText(x, y, label, color, 1.2f))
                    // Only the area effects get an expanding ring - SPEED/INVISIBILITY are
                    // self-buffs with nothing to telegraph in the world.
                    val rippleRadius = when (type) {
                        PowerUpType.REPEL -> GameConfig.BASE_RADIUS * GameConfig.REPEL_RANGE_MULTIPLIER
                        PowerUpType.FREEZE -> GameConfig.BASE_RADIUS * GameConfig.FREEZE_RANGE_MULTIPLIER
                        PowerUpType.HOOK -> GameConfig.BASE_RADIUS * GameConfig.HOOK_RANGE_MULTIPLIER
                        else -> null
                    }
                    if (rippleRadius != null) {
                        effectRipples.add(EffectRipple(x, y, rippleRadius, color, 0.5f))
                    }
                    if (byPlayer) {
                        vibratePowerUp()
                        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 150)
                        when (type) {
                            PowerUpType.SPEED -> unlockAchievement(Achievement.USE_SPEED)
                            PowerUpType.INVISIBILITY -> unlockAchievement(Achievement.USE_INVISIBILITY)
                            PowerUpType.REPEL -> unlockAchievement(Achievement.USE_REPEL)
                            PowerUpType.FREEZE -> unlockAchievement(Achievement.USE_FREEZE)
                            PowerUpType.HOOK -> unlockAchievement(Achievement.USE_HOOK)
                            else -> {}
                        }
                    }
                }

                override fun onZoneDeath(x: Float, y: Float, wasPlayer: Boolean) {
                    floatingTexts.add(FloatingText(x, y, "Eliminato!", Color.parseColor("#B71C1C"), 1.3f))
                }

                override fun onDeflateDeath(x: Float, y: Float, wasPlayer: Boolean) {
                    floatingTexts.add(FloatingText(x, y, "Sgonfiato!", Color.parseColor("#FF6F00"), 1.3f))
                }

                override fun onGameOver(
                    playerWon: Boolean,
                    finalRadius: Float,
                    playersRemaining: Int,
                    opponentsAbsorbed: Int,
                    elapsedSeconds: Float,
                    reachedFinalRound: Boolean
                ) {
                    loopThread?.running = false
                    toneGenerator.startTone(
                        if (playerWon) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK,
                        400
                    )
                    if (playerWon) unlockAchievement(Achievement.FIRST_WIN)
                    if (opponentsAbsorbed >= achievementAbsorbStreakTarget) {
                        unlockAchievement(Achievement.ABSORB_STREAK)
                    }
                    post {
                        callback?.onGameOver(
                            playerWon, finalRadius.toInt(), playersRemaining, opponentsAbsorbed,
                            elapsedSeconds.toInt(), reachedFinalRound
                        )
                    }
                }

                override fun onFinalRoundStarted() {
                    finalRoundTransitionActive = true
                    finalRoundTransitionElapsed = 0f
                    vibrateFinalRoundAlert()
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
                    unlockAchievement(Achievement.FINAL_ROUND)
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

    fun restart(
        botCount: Int,
        powerUpFrequencyLevel: Int,
        arenaSize: GameConfig.ArenaSize = GameConfig.ArenaSize.NORMAL,
        skipToFinalRound: Boolean = false
    ) {
        loopThread?.running = false
        loopThread?.join(500)
        started = false
        startGame(botCount, powerUpFrequencyLevel, arenaSize, skipToFinalRound)
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

    // Spends whatever the player is currently carrying (REPEL or FREEZE), if anything.
    // A no-op if the slot is empty.
    fun useActiveItem() {
        if (started) {
            engine.activateCarriedItem(engine.player)
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

    private fun blobLabel(id: Int): String = if (id == 0) "Tu" else botNames.getOrElse(id - 1) { "Bot $id" }

    private fun addFeedEntry(text: String) {
        feedEntries.add(FeedEntry(text, 3.5f))
        if (feedEntries.size > 5) feedEntries.removeAt(0)
    }

    // Unlocks are idempotent (Achievements.unlock only returns true the first time ever),
    // so every call site can fire unconditionally without checking isUnlocked itself.
    private fun unlockAchievement(achievement: Achievement) {
        if (Achievements.unlock(context, achievement)) {
            achievementPopups.add(
                AchievementPopup(
                    context.getString(R.string.achievement_unlocked_format, context.getString(achievement.titleResId)),
                    3f
                )
            )
        }
    }

    // Conditions that can't be caught from a single discrete event (onAbsorb/onPowerUpCollected/
    // etc.) - checked once per frame but latched off as soon as each one fires so the
    // per-frame cost drops to nothing for the rest of the match.
    private fun checkLiveAchievements(player: Blob) {
        if (!maxSizeAchievementChecked && player.radius >= GameConfig.MAX_RADIUS) {
            maxSizeAchievementChecked = true
            unlockAchievement(Achievement.MAX_SIZE)
        }
        if (!maxBoostAchievementChecked && player.isBoostAtMaxPower) {
            maxBoostAchievementChecked = true
            unlockAchievement(Achievement.MAX_BOOST)
        }
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

    private fun checkCarriedItemAvailability() {
        val player = engine.player
        val type = if (player.alive) player.carriedItem else null
        if (type != lastCarriedItemType) {
            lastCarriedItemType = type
            post { callback?.onCarriedItemChanged(type) }
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

    private fun vibrateFinalRoundAlert() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 120, 80, 120, 80, 200), -1))
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
                } else if (finalRoundTransitionActive) {
                    finalRoundTransitionElapsed += dt
                    val totalDuration = GameConfig.FINAL_ROUND_BANNER_SECONDS + GameConfig.FINAL_ROUND_COUNTDOWN_SECONDS
                    if (finalRoundTransitionElapsed >= totalDuration) finalRoundTransitionActive = false
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

        drawAbsorbAnimations(canvas, offsetX, offsetY, dt)
        drawEffectRipples(canvas, offsetX, offsetY, dt)
        drawDangerIndicator(canvas, offsetX, offsetY)
        drawFloatingTexts(canvas, offsetX, offsetY, dt)
        drawHud(canvas)
        drawFeed(canvas, dt)
        drawFinalRoundWarning(canvas)
        drawCountdown(canvas)
        drawFinalRoundTransition(canvas)
        if (!countdownActive && !finalRoundTransitionActive) checkLiveAchievements(player)
        drawAchievementPopups(canvas, dt)
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

        // While holding, telegraph where the zone is about to shrink to next, so there's
        // time to plan a rotation instead of the shrink starting with no warning.
        if (engine.isZoneHolding) {
            canvas.drawCircle(cx, cy, engine.nextSafeZoneRadius, nextZonePreviewPaint)
        }
    }

    private fun drawBlob(canvas: Canvas, blob: Blob, offsetX: Float, offsetY: Float) {
        val cx = blob.position.x + offsetX
        val cy = blob.position.y + offsetY
        val alpha = if (blob.isInvisible) 70 else 255

        drawExhaust(canvas, blob, cx, cy, alpha)
        drawBalloonBody(canvas, blob, cx, cy, alpha)
        drawFrozenOverlay(canvas, blob, cx, cy, alpha)
        drawShieldAura(canvas, blob, cx, cy, alpha)
        drawSpeedBadge(canvas, blob, cx, cy, alpha)
        drawKnot(canvas, blob, cx, cy, alpha)
        drawString(canvas, blob, cx, cy, alpha)
    }

    private fun drawFrozenOverlay(canvas: Canvas, blob: Blob, cx: Float, cy: Float, alpha: Int) {
        // A pale icy tint over the whole body - distinct from the shield ring, signals
        // movement is locked rather than a defensive buff running.
        if (!blob.isFrozen) return
        frozenOverlayPaint.alpha = (alpha * 0.4f).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, blob.radius * 1.05f, frozenOverlayPaint)
    }

    private fun drawAbsorbAnimations(canvas: Canvas, offsetX: Float, offsetY: Float, dt: Float) {
        val iterator = absorbAnims.iterator()
        while (iterator.hasNext()) {
            val anim = iterator.next()
            anim.elapsed += dt
            val progress = (anim.elapsed / anim.duration).coerceIn(0f, 1f)
            if (progress >= 1f) {
                iterator.remove()
                continue
            }
            // Eased pull toward the absorber's current (moving) position, shrinking and
            // fading out as it goes, so it visibly gets dragged in and swallowed.
            val eased = progress * progress
            val x = anim.startX + (anim.absorber.position.x - anim.startX) * eased
            val y = anim.startY + (anim.absorber.position.y - anim.startY) * eased
            val radius = anim.startRadius * (1f - progress)
            absorbAnimPaint.color = anim.color
            absorbAnimPaint.alpha = (255 * (1f - progress)).toInt().coerceIn(0, 255)
            canvas.drawCircle(x + offsetX, y + offsetY, radius.coerceAtLeast(0f), absorbAnimPaint)
        }
    }

    private fun drawEffectRipples(canvas: Canvas, offsetX: Float, offsetY: Float, dt: Float) {
        val iterator = effectRipples.iterator()
        while (iterator.hasNext()) {
            val ripple = iterator.next()
            ripple.ttl -= dt
            if (ripple.ttl <= 0f) {
                iterator.remove()
                continue
            }
            val progress = 1f - (ripple.ttl / ripple.maxTtl)
            ripplePaint.color = ripple.ringColor
            ripplePaint.alpha = (255 * (1f - progress)).toInt().coerceIn(0, 255)
            canvas.drawCircle(
                ripple.x + offsetX,
                ripple.y + offsetY,
                ripple.maxRadius * progress,
                ripplePaint
            )
        }
    }

    private fun drawShieldAura(canvas: Canvas, blob: Blob, cx: Float, cy: Float, alpha: Int) {
        // A gently pulsing ring around a shielded balloon - distinct from the speed badge,
        // signals the ambient leak is paused rather than an active power-up timer running.
        if (!blob.isShielded) return
        val pulse = 0.6f + 0.4f * sin(blob.exhaustPhase * 3f)
        shieldAuraPaint.alpha = (alpha * 0.5f * pulse).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, blob.radius * 1.18f, shieldAuraPaint)
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
            PowerUpType.SHIELD -> Color.parseColor("#4FC3F7")
            PowerUpType.REPEL -> Color.parseColor("#FFB74D")
            PowerUpType.FREEZE -> Color.parseColor("#80DEEA")
            PowerUpType.HOOK -> Color.parseColor("#A1887F")
            PowerUpType.SPEED_UP -> Color.parseColor("#FF7043")
            PowerUpType.AGILITY_UP -> Color.parseColor("#CE93D8")
        }
        val cx = powerUp.position.x + offsetX
        val cy = powerUp.position.y + offsetY
        if (powerUp.type == PowerUpType.SHIELD) {
            drawSupplyDropBeacon(canvas, cx, cy)
        }
        canvas.drawCircle(cx, cy, powerUp.radius, powerUpPaint)
        drawPowerUpIcon(canvas, powerUp, cx, cy)
    }

    // A rare supply drop (always SHIELD) is telegraphed with pulsing outward rings so it
    // draws a scramble instead of blending in with the regular power-ups.
    private fun drawSupplyDropBeacon(canvas: Canvas, cx: Float, cy: Float) {
        val cyclePhase = (beaconPhase % 1.2f) / 1.2f
        val ringRadius = GameConfig.POWERUP_RADIUS * (1.6f + cyclePhase * 3f)
        supplyDropBeaconPaint.alpha = ((1f - cyclePhase) * 180).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, ringRadius, supplyDropBeaconPaint)
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
            PowerUpType.SHIELD -> {
                iconPaint.style = Paint.Style.STROKE
                val path = Path().apply {
                    moveTo(cx, cy - 8f)
                    lineTo(cx + 7f, cy - 4f)
                    lineTo(cx + 7f, cy + 4f)
                    lineTo(cx, cy + 9f)
                    lineTo(cx - 7f, cy + 4f)
                    lineTo(cx - 7f, cy - 4f)
                    close()
                }
                canvas.drawPath(path, iconPaint)
                iconPaint.style = Paint.Style.FILL
            }
            PowerUpType.REPEL -> {
                // Four short diagonal strokes pointing outward suggest a push/burst.
                val dirs = arrayOf(0.707f to 0.707f, -0.707f to 0.707f, 0.707f to -0.707f, -0.707f to -0.707f)
                for ((dx, dy) in dirs) {
                    canvas.drawLine(cx + dx * 3f, cy + dy * 3f, cx + dx * 9f, cy + dy * 9f, iconPaint)
                }
            }
            PowerUpType.FREEZE -> {
                // A simple snowflake: three crossing lines.
                canvas.drawLine(cx - 8f, cy, cx + 8f, cy, iconPaint)
                canvas.drawLine(cx - 6f, cy - 6f, cx + 6f, cy + 6f, iconPaint)
                canvas.drawLine(cx - 6f, cy + 6f, cx + 6f, cy - 6f, iconPaint)
            }
            PowerUpType.HOOK -> {
                // A curved hook (like a shepherd's crook), echoing the balloon's own string.
                iconPaint.style = Paint.Style.STROKE
                val path = Path().apply {
                    moveTo(cx - 3f, cy - 8f)
                    lineTo(cx - 3f, cy + 2f)
                    quadTo(cx - 3f, cy + 8f, cx + 4f, cy + 6f)
                }
                canvas.drawPath(path, iconPaint)
                iconPaint.style = Paint.Style.FILL
            }
            PowerUpType.SPEED_UP -> {
                // A bold, stacked double chevron - a permanent boost rather than the
                // temporary SPEED item's sideways motion lines.
                for (i in 0..1) {
                    val oy = cy + 5f - i * 7f
                    canvas.drawLine(cx - 6f, oy + 4f, cx, oy - 4f, iconPaint)
                    canvas.drawLine(cx, oy - 4f, cx + 6f, oy + 4f, iconPaint)
                }
            }
            PowerUpType.AGILITY_UP -> {
                // A curved arrow suggests sharper turning.
                iconPaint.style = Paint.Style.STROKE
                canvas.drawArc(cx - 7f, cy - 7f, cx + 7f, cy + 7f, -30f, 270f, false, iconPaint)
                iconPaint.style = Paint.Style.FILL
                canvas.drawLine(cx + 6f, cy - 4f, cx + 9f, cy - 1f, iconPaint)
                canvas.drawLine(cx + 6f, cy - 4f, cx + 3f, cy - 6f, iconPaint)
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

    private fun drawFeed(canvas: Canvas, dt: Float) {
        val iterator = feedEntries.iterator()
        var row = 0
        while (iterator.hasNext()) {
            val entry = iterator.next()
            entry.ttl -= dt
            if (entry.ttl <= 0f) {
                iterator.remove()
                continue
            }
            feedTextPaint.alpha = (255 * (entry.ttl / entry.maxTtl)).toInt().coerceIn(0, 255)
            canvas.drawText(entry.text, 24f, 100f + row * 34f, feedTextPaint)
            row++
        }
    }

    private fun drawAchievementPopups(canvas: Canvas, dt: Float) {
        val iterator = achievementPopups.iterator()
        var row = 0
        while (iterator.hasNext()) {
            val popup = iterator.next()
            popup.ttl -= dt
            if (popup.ttl <= 0f) {
                iterator.remove()
                continue
            }
            achievementPaint.alpha = (255 * (popup.ttl / popup.maxTtl).coerceAtMost(1f)).toInt().coerceIn(0, 255)
            canvas.drawText(popup.text, width / 2f, 160f + row * 44f, achievementPaint)
            row++
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

        val elapsedSeconds = engine.matchElapsed.toInt()
        val timeText = "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)
        canvas.drawText(timeText, width / 2f, 90f, timerHudPaint)

        // playerWon is always false here since the match is still running - this omits
        // the win bonus that HighScores.computeScore would add once it actually ends. Every
        // other term (elapsed time, absorbs, reaching the final round) only ever increases
        // over a match, so unlike the old radius-based term, this can never tick down frame
        // to frame. Centered below the timer instead of the left column, where it used to
        // blend into the elimination feed right underneath it.
        val liveScore = HighScores.computeScore(
            playerWon = false,
            elapsedSeconds = elapsedSeconds,
            opponentsAbsorbed = engine.playerOpponentsAbsorbed,
            reachedFinalRound = engine.isFinalRoundActive
        )
        canvas.drawText("Punteggio: $liveScore", width / 2f, 134f, liveScoreHudPaint)

        drawPermanentStatsHud(canvas, player)
    }

    // Small, muted pip bars (GameConfig.PERMANENT_STAT_TIER_COUNT of them) instead of raw
    // percentages - started low enough to clear the pause button in the same corner, which
    // used to sit right on top of this text.
    private fun drawPermanentStatsHud(canvas: Canvas, player: Blob) {
        val density = resources.displayMetrics.density
        var y = (16f + 52f + 14f) * density
        y = drawStatPips(
            canvas, "Velocità", player.permanentSpeedMultiplier,
            GameConfig.PERMANENT_SPEED_MAX_MULTIPLIER, Color.parseColor("#FF7043"), y
        )
        drawStatPips(
            canvas, "Agilità", player.permanentTurnRateMultiplier,
            GameConfig.PERMANENT_TURN_RATE_MAX_MULTIPLIER, Color.parseColor("#CE93D8"), y
        )
    }

    private fun drawStatPips(
        canvas: Canvas,
        label: String,
        multiplier: Float,
        maxMultiplier: Float,
        filledColor: Int,
        y: Float
    ): Float {
        val pipCount = GameConfig.PERMANENT_STAT_TIER_COUNT
        val fraction = ((multiplier - 1f) / (maxMultiplier - 1f)).coerceIn(0f, 1f)
        val filledCount = Math.round(fraction * pipCount).coerceIn(0, pipCount)

        val pipSize = 13f
        val pipGap = 4f
        val pipsWidth = pipCount * pipSize + (pipCount - 1) * pipGap
        val rightEdge = width - 24f
        val blockLeft = rightEdge - pipsWidth
        val labelWidth = statsHudPaint.measureText(label)
        canvas.drawText(label, blockLeft - labelWidth - 12f, y, statsHudPaint)

        for (i in 0 until pipCount) {
            val left = blockLeft + i * (pipSize + pipGap)
            val top = y - pipSize + 4f
            statPipPaint.color = if (i < filledCount) filledColor else statPipEmptyColor
            canvas.drawRoundRect(left, top, left + pipSize, top + pipSize, 3f, 3f, statPipPaint)
        }
        return y + 28f
    }

    private fun drawCountdown(canvas: Canvas) {
        if (!countdownActive) return
        canvas.drawColor(Color.argb(120, 0, 0, 0))
        val secondsLeft = ceil(countdownRemaining).toInt().coerceAtLeast(1)
        canvas.drawText(secondsLeft.toString(), width / 2f, height / 2f + 60f, countdownPaint)
    }

    // Two-beat cinematic cut into the final round: a quick white flash with the title card
    // scaling in, then a themed 3-2-1. Gameplay is frozen throughout (see GameThread),
    // the same treatment the match-start countdown already gets.
    private fun drawFinalRoundTransition(canvas: Canvas) {
        if (!finalRoundTransitionActive) return
        canvas.drawColor(Color.argb(170, 40, 0, 0))

        val bannerSeconds = GameConfig.FINAL_ROUND_BANNER_SECONDS
        if (finalRoundTransitionElapsed < bannerSeconds) {
            val flashAlpha = (255 * (1f - finalRoundTransitionElapsed / 0.2f)).toInt().coerceIn(0, 255)
            if (flashAlpha > 0) canvas.drawColor(Color.argb(flashAlpha, 255, 255, 255))

            val scale = 0.6f + 0.4f * (finalRoundTransitionElapsed / 0.4f).coerceIn(0f, 1f)
            canvas.save()
            canvas.scale(scale, scale, width / 2f, height / 2f)
            canvas.drawText("SFIDA FINALE", width / 2f, height / 2f, finalRoundTitlePaint)
            canvas.restore()
        } else {
            canvas.drawText("SFIDA FINALE", width / 2f, height / 2f - 100f, finalRoundTitlePaint)

            val countdownElapsed = finalRoundTransitionElapsed - bannerSeconds
            val secondsLeft = ceil(GameConfig.FINAL_ROUND_COUNTDOWN_SECONDS - countdownElapsed).toInt().coerceAtLeast(1)
            // A small punch-in at the start of each second, easing back to full size.
            val fractionIntoSecond = countdownElapsed - countdownElapsed.toInt()
            val punch = 1.4f - 0.4f * fractionIntoSecond.coerceIn(0f, 1f)
            canvas.save()
            canvas.scale(punch, punch, width / 2f, height / 2f + 60f)
            canvas.drawText(secondsLeft.toString(), width / 2f, height / 2f + 60f, finalRoundCountdownPaint)
            canvas.restore()
        }
    }

    // The advance warning: still normal, playable gameplay underneath (no freeze, no
    // full-screen overlay) with a small pulsing "il finale sta arrivando" + countdown
    // drawn over it for the last few seconds before the engine actually triggers the
    // final round - which is when drawFinalRoundTransition takes over.
    private fun drawFinalRoundWarning(canvas: Canvas) {
        if (countdownActive || finalRoundTransitionActive) return
        val secondsRemaining = engine.secondsUntilFinalRound
        if (secondsRemaining <= 0f || secondsRemaining > GameConfig.FINAL_ROUND_WARNING_SECONDS) return

        val secondsLeft = ceil(secondsRemaining).toInt().coerceAtLeast(1)
        // How far into the current displayed second we are (0 = just ticked, 1 = about to
        // tick again), driving the same punch-in used by the frozen countdown.
        val fractionIntoSecond = ceil(secondsRemaining) - secondsRemaining
        val punch = 1.3f - 0.3f * fractionIntoSecond.coerceIn(0f, 1f)

        canvas.drawText("IL FINALE STA ARRIVANDO", width / 2f, height / 2f - 130f, finalRoundWarningTitlePaint)
        canvas.save()
        canvas.scale(punch, punch, width / 2f, height / 2f - 70f)
        canvas.drawText(secondsLeft.toString(), width / 2f, height / 2f - 70f, finalRoundWarningCountdownPaint)
        canvas.restore()
    }
}
