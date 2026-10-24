package com.hyperionsoftware.balls.ui

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.MathUtils
import com.hyperionsoftware.balls.R
import com.hyperionsoftware.balls.achievements.Achievement
import com.hyperionsoftware.balls.achievements.Achievements
import com.hyperionsoftware.balls.audio.SoundEffectPlayer
import com.hyperionsoftware.balls.audio.VibrationSettings
import com.hyperionsoftware.balls.challenges.DailyChallenges
import com.hyperionsoftware.balls.challenges.MatchResult
import com.hyperionsoftware.balls.cosmetics.BalloonCord
import com.hyperionsoftware.balls.cosmetics.BalloonSticker
import com.hyperionsoftware.balls.cosmetics.ExhaustStyle
import com.hyperionsoftware.balls.economy.HeliumRewards
import com.hyperionsoftware.balls.economy.Wallet
import com.hyperionsoftware.balls.game.Blob
import com.hyperionsoftware.balls.game.GameConfig
import com.hyperionsoftware.balls.game.GameEngine
import com.hyperionsoftware.balls.game.GameListener
import com.hyperionsoftware.balls.game.PowerUp
import com.hyperionsoftware.balls.game.PowerUpType
import com.hyperionsoftware.balls.game.Vector2
import com.hyperionsoftware.balls.records.PersonalRecords
import com.hyperionsoftware.balls.records.RecordType
import com.hyperionsoftware.balls.score.HighScores
import com.hyperionsoftware.balls.settings.CosmeticsSettings
import com.hyperionsoftware.balls.settings.DeveloperSettings
import com.hyperionsoftware.balls.ui.gdx.Atlas
import com.hyperionsoftware.balls.ui.gdx.BalloonPainter
import com.hyperionsoftware.balls.ui.gdx.FrameClock
import com.hyperionsoftware.balls.ui.gdx.Painter
import com.hyperionsoftware.balls.ui.gdx.Particles
import com.hyperionsoftware.balls.ui.gdx.TextStyle
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Classic mode's match, on libGDX: runs the GameEngine and draws it, all on libGDX's own render
// thread (which is paced by the display, like the old Choreographer loop). Everything that
// isn't drawing - achievements, personal records, daily challenges, sounds, the kill feed - is
// the old GameView's logic carried over unchanged. GameView itself is now just the Android-side
// host: it owns this, forwards input to it, and relays callbacks to the activity.
//
// Threading: GameView's methods run on the UI thread and only ever set the @Volatile request
// fields below; everything else (engine, effect lists, drawing) belongs to the render thread.
class GameRenderer(private val view: GameView) : ApplicationAdapter() {

    class StartParams(
        val botCount: Int,
        val powerUpFrequencyLevel: Int,
        val arenaSize: GameConfig.ArenaSize,
        val skipToFinalRound: Boolean,
        val botAggressivenessLevel: Int,
        val safeZoneShrinkSpeedLevel: Int
    )

    private val context: Context = view.context
    private val density = context.resources.displayMetrics.density

    // Requests from the UI thread, consumed at the top of the next frame.
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

    private var engine: GameEngine? = null
    private var matchOver = false
    private var sinceMatchOver = 0f
    private var lastBoostAvailable = false
    private var lastCarriedItemType: PowerUpType? = null
    private var botNames: List<String> = emptyList()
    private var selectedSticker: BalloonSticker = BalloonSticker.NONE
    private var selectedCord: BalloonCord = BalloonCord.CLASSIC_GREY
    private var selectedExhaustStyle: ExhaustStyle = ExhaustStyle.CLASSIC
    private var showFps = false
    private val fpsCounter = FpsCounter(density)

    private var countdownActive = false
    private var countdownRemaining = 0f
    private var finalRoundTransitionActive = false
    private var finalRoundTransitionElapsed = 0f
    private var beaconPhase = 0f
    private var elapsedRealTime = 0f

    private class FloatingText(var x: Float, var y: Float, val text: String, val color: Int, var ttl: Float, val maxTtl: Float = ttl)
    private class FeedEntry(val text: String, var ttl: Float, val maxTtl: Float = ttl)
    private class EffectRipple(
        val x: Float,
        val y: Float,
        val maxRadius: Float,
        val ringColor: Int,
        var ttl: Float,
        val maxTtl: Float = ttl,
        val inward: Boolean = false
    )
    private class AbsorbAnim(
        val startX: Float,
        val startY: Float,
        val startRadius: Float,
        val color: Int,
        val absorber: Blob,
        var elapsed: Float = 0f,
        val duration: Float = 0.35f
    )
    // A brief additive burst of light - where a balloon popped, or an item went off.
    private class Flash(val x: Float, val y: Float, val radius: Float, val color: Int, var ttl: Float, val maxTtl: Float = ttl)
    private class AchievementPopup(val text: String, var ttl: Float, val maxTtl: Float = ttl)

    private val floatingTexts = mutableListOf<FloatingText>()
    private val feedEntries = mutableListOf<FeedEntry>()
    private val effectRipples = mutableListOf<EffectRipple>()
    private val absorbAnims = mutableListOf<AbsorbAnim>()
    private val flashes = mutableListOf<Flash>()
    private val achievementPopups = mutableListOf<AchievementPopup>()

    // Per-match latches - see the old GameView for the reasoning, all unchanged.
    private var maxSizeAchievementChecked = false
    private var maxBoostAchievementChecked = false
    private var matchPeakSize = 0
    private var matchBestCombo = 0
    private val announcedRecordsThisMatch = mutableSetOf<RecordType>()

    private val achievementAbsorbStreakTarget = 5
    private val achievementDailyStreakTarget = 3

    // The HUD text only changes when its underlying number does - see drawHud.
    private var hudSizeValue = Int.MIN_VALUE
    private var hudSizeText = ""
    private var hudAbsorbedValue = Int.MIN_VALUE
    private var hudAbsorbedText = ""
    private var hudPlayersValue = Int.MIN_VALUE
    private var hudPlayersText = ""
    private var hudZoneValue = Int.MIN_VALUE
    private var hudZoneText = ""
    private var hudTimeValue = Int.MIN_VALUE
    private var hudTimeText = ""
    private var hudScoreValue = Int.MIN_VALUE
    private var hudScoreText = ""
    private val statSpeedLabel = context.getString(R.string.stat_speed_label)
    private val statAgilityLabel = context.getString(R.string.stat_agility_label)
    private val statPotencyLabel = context.getString(R.string.stat_potency_label)
    private val finalRoundTitle = context.getString(R.string.game_final_round_title)
    private val finalRoundWarning = context.getString(R.string.game_final_round_warning)

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
    private val soundEffectPlayer: SoundEffectPlayer by lazy { SoundEffectPlayer(context) }
    private var soundEffectPlayerCreated = false

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
        // Whatever time passed while the app was in the background isn't game time.
        clock.reset()
        fpsCounter.reset()
    }

    override fun dispose() {
        if (::painter.isInitialized) painter.dispose()
    }

    // Called by GameView when the view goes away - on the UI thread, after rendering stopped.
    fun releaseAudio() {
        if (soundEffectPlayerCreated) soundEffectPlayer.release()
    }

    override fun render() {
        pendingStart?.let {
            pendingStart = null
            startMatch(it)
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
        // Once the match is over, keep animating only long enough for the last effects to play
        // out, then stop redrawing a frame nobody is playing anymore (GameView turns continuous
        // rendering back on if it's ever resumed).
        if (matchOver) {
            sinceMatchOver += dt
            if (sinceMatchOver > SETTLE_AFTER_END_SECONDS) Gdx.graphics.isContinuousRendering = false
        }
    }

    private fun step(engine: GameEngine, dt: Float) {
        engine.player.isBoosting = playerBoosting
        engine.player.inputDirection = playerDirection
        if (activeItemRequested) {
            activeItemRequested = false
            engine.activateCarriedItem(engine.player)
        }
        if (countdownActive) {
            countdownRemaining -= dt
            if (countdownRemaining <= 0f) countdownActive = false
        } else if (finalRoundTransitionActive) {
            finalRoundTransitionElapsed += dt
            if (finalRoundTransitionElapsed >= GameConfig.FINAL_ROUND_BANNER_SECONDS) {
                finalRoundTransitionActive = false
            }
        } else if (!matchOver) {
            engine.update(dt)
            checkBoostAvailability(engine)
            checkCarriedItemAvailability(engine)
        }
    }

    private fun startMatch(params: StartParams) {
        floatingTexts.clear()
        feedEntries.clear()
        effectRipples.clear()
        absorbAnims.clear()
        flashes.clear()
        achievementPopups.clear()
        particles.clear()
        maxSizeAchievementChecked = false
        maxBoostAchievementChecked = false
        matchPeakSize = 0
        matchBestCombo = 0
        announcedRecordsThisMatch.clear()
        finalRoundTransitionActive = false
        finalRoundTransitionElapsed = 0f
        // Skip-to-final-round testing gets its own dramatic entrance instead of the plain
        // match-start countdown - showing both back to back would be redundant.
        countdownActive = !params.skipToFinalRound
        countdownRemaining = GameConfig.COUNTDOWN_SECONDS
        lastBoostAvailable = false
        lastCarriedItemType = null
        matchOver = false
        sinceMatchOver = 0f
        activeItemRequested = false
        hudSizeValue = Int.MIN_VALUE
        hudAbsorbedValue = Int.MIN_VALUE
        hudPlayersValue = Int.MIN_VALUE
        hudZoneValue = Int.MIN_VALUE
        hudTimeValue = Int.MIN_VALUE
        hudScoreValue = Int.MIN_VALUE
        botNames = BotNames.generate(params.botCount)
        selectedSticker = CosmeticsSettings.getSelectedSticker(context)
        selectedCord = CosmeticsSettings.getSelectedCord(context)
        selectedExhaustStyle = CosmeticsSettings.getSelectedExhaustStyle(context)
        showFps = DeveloperSettings.isEnabled(context)
        fpsCounter.reset()
        clock.reset()
        engine = GameEngine(
            botCount = params.botCount,
            powerUpFrequencyLevel = params.powerUpFrequencyLevel,
            arenaSize = params.arenaSize,
            skipToFinalRound = params.skipToFinalRound,
            playerColor = CosmeticsSettings.getSelectedColor(context).colorInt,
            botAggressivenessLevel = params.botAggressivenessLevel,
            safeZoneShrinkSpeedLevel = params.safeZoneShrinkSpeedLevel,
            listener = matchListener
        )
    }

    private val matchListener = object : GameListener {
        override fun onVibrate() {
            vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
        }

        override fun onAbsorb(x: Float, y: Float, sizeGain: Int, byPlayer: Boolean, absorberId: Int, victimId: Int) {
            val engine = engine ?: return
            val color = if (byPlayer) PLAYER_GAIN_COLOR else Color.WHITE
            floatingTexts.add(FloatingText(x, y, "+$sizeGain", color, 1.2f))
            if (byPlayer) {
                vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 40, 60), -1))
                playSound(SoundEffectPlayer.Effect.ABSORB)
                unlockAchievement(Achievement.FIRST_ABSORB)
                checkLiveRecord(RecordType.ABSORPTIONS, engine.playerOpponentsAbsorbed)
            }
            addFeedEntry(context.getString(R.string.game_feed_absorbed, blobLabel(absorberId), blobLabel(victimId)))

            val victim = engine.blobs.find { it.id == victimId }
            val absorber = engine.blobs.find { it.id == absorberId }
            if (victim != null && absorber != null) {
                absorbAnims.add(AbsorbAnim(x, y, victim.radius, victim.color, absorber))
                popBurst(x, y, victim.radius, victim.color)
            }
        }

        override fun onComboAchieved(x: Float, y: Float, comboCount: Int, byPlayer: Boolean) {
            val color = if (byPlayer) Color.parseColor("#FFD54F") else Color.parseColor("#FFF59D")
            floatingTexts.add(FloatingText(x, y, context.getString(R.string.game_combo, comboCount), color, 1.6f))
            if (byPlayer) {
                vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 40, 60), -1))
                playSound(SoundEffectPlayer.Effect.COMBO)
                if (comboCount >= 3) unlockAchievement(Achievement.COMBO_MASTER)
                matchBestCombo = maxOf(matchBestCombo, comboCount)
                checkLiveRecord(RecordType.COMBO, matchBestCombo)
            }
        }

        override fun onPowerUpCollected(x: Float, y: Float, type: PowerUpType, byPlayer: Boolean) {
            val engine = engine ?: return
            floatingTexts.add(FloatingText(x, y, powerUpLabel(type), Color.parseColor("#FFD54F"), 1.4f))
            sparkBurst(x, y, powerUpColor(type), 9)
            if (byPlayer) {
                vibrate(VibrationEffect.createOneShot(35, 90))
                playSound(SoundEffectPlayer.Effect.POWERUP)
                when (type) {
                    // SHIELD is never a regular spawn, only ever a supply drop - so collecting
                    // one always means this.
                    PowerUpType.SHIELD -> unlockAchievement(Achievement.SUPPLY_DROP)
                    PowerUpType.SPEED_UP -> if (engine.player.permanentSpeedMultiplier >= GameConfig.PERMANENT_SPEED_MAX_MULTIPLIER) {
                        unlockAchievement(Achievement.MAX_SPEED_STAT)
                    }
                    PowerUpType.AGILITY_UP -> if (engine.player.permanentTurnRateMultiplier >=
                        GameConfig.PERMANENT_TURN_RATE_MAX_MULTIPLIER
                    ) {
                        unlockAchievement(Achievement.MAX_AGILITY_STAT)
                    }
                    PowerUpType.POTENCY_UP -> if (engine.player.permanentPotencyMultiplier >=
                        GameConfig.PERMANENT_POTENCY_MAX_MULTIPLIER
                    ) {
                        unlockAchievement(Achievement.MAX_POTENCY_STAT)
                    }
                    else -> {}
                }
            }
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
            val color = powerUpColor(type)
            floatingTexts.add(FloatingText(x, y, label, color, 1.2f))
            // Only the area effects get a ring - matches GameEngine.reachRangeFromCenter exactly
            // (including POTENCY_UP's multiplier), so it always shows the effect's real reach.
            val rippleRadius = when (type) {
                PowerUpType.REPEL ->
                    sourceRadius + GameConfig.BASE_RADIUS * GameConfig.REPEL_RANGE_MULTIPLIER * sourcePotencyMultiplier
                PowerUpType.FREEZE ->
                    sourceRadius + GameConfig.BASE_RADIUS * GameConfig.FREEZE_RANGE_MULTIPLIER * sourcePotencyMultiplier
                PowerUpType.HOOK ->
                    sourceRadius + GameConfig.BASE_RADIUS * GameConfig.HOOK_RANGE_MULTIPLIER * sourcePotencyMultiplier
                else -> null
            }
            if (rippleRadius != null) {
                effectRipples.add(EffectRipple(x, y, rippleRadius, color, 0.5f, inward = type == PowerUpType.HOOK))
                flashes.add(Flash(x, y, rippleRadius * 0.8f, color, 0.35f))
            } else {
                flashes.add(Flash(x, y, sourceRadius * 2f, color, 0.3f))
            }
            if (byPlayer) {
                vibrate(VibrationEffect.createOneShot(35, 90))
                playSound(SoundEffectPlayer.Effect.ACTIVE_ITEM)
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
            floatingTexts.add(FloatingText(x, y, context.getString(R.string.game_zone_death), Color.parseColor("#B71C1C"), 1.3f))
            deflateBurst(x, y)
        }

        override fun onDeflateDeath(x: Float, y: Float, wasPlayer: Boolean) {
            floatingTexts.add(FloatingText(x, y, context.getString(R.string.game_deflate_death), Color.parseColor("#FF6F00"), 1.3f))
            deflateBurst(x, y)
        }

        override fun onGameOver(
            playerWon: Boolean,
            finalRadius: Float,
            playersRemaining: Int,
            opponentsAbsorbed: Int,
            elapsedSeconds: Float,
            reachedFinalRound: Boolean
        ) {
            matchOver = true
            playSound(if (playerWon) SoundEffectPlayer.Effect.GAME_OVER_WIN else SoundEffectPlayer.Effect.GAME_OVER_LOSE)
            if (playerWon) unlockAchievement(Achievement.FIRST_WIN)
            if (playerWon && opponentsAbsorbed == 0) unlockAchievement(Achievement.PACIFIST_VICTORY)
            if (opponentsAbsorbed >= achievementAbsorbStreakTarget) unlockAchievement(Achievement.ABSORB_STREAK)
            // Recorded here rather than in GameActivity, so the streak is already up to date by
            // the time DAILY_DEDICATION checks it just below.
            val justCompletedDailyChallenge = DailyChallenges.recordMatchResult(
                context,
                MatchResult(playerWon, finalRadius.toInt(), opponentsAbsorbed, elapsedSeconds.toInt(), reachedFinalRound)
            )
            if (justCompletedDailyChallenge) Wallet.add(context, HeliumRewards.DAILY_CHALLENGE_BONUS)
            if (DailyChallenges.streak(context) >= achievementDailyStreakTarget) unlockAchievement(Achievement.DAILY_DEDICATION)
            // Silent catch-ups, no popup - see the old GameView for why each one lives here.
            PersonalRecords.tryBeat(context, RecordType.SIZE, finalRadius.toInt())
            PersonalRecords.tryBeat(context, RecordType.LONGEST_MATCH, elapsedSeconds.toInt())
            PersonalRecords.tryBeat(context, RecordType.DAILY_STREAK, DailyChallenges.streak(context))
            if (playerWon) PersonalRecords.incrementTotalWins(context)
            view.post {
                view.callback?.onGameOver(
                    playerWon, finalRadius.toInt(), playersRemaining, opponentsAbsorbed, elapsedSeconds.toInt(), reachedFinalRound
                )
            }
        }

        override fun onFinalRoundStarted() {
            finalRoundTransitionActive = true
            finalRoundTransitionElapsed = 0f
            vibrate(VibrationEffect.createWaveform(longArrayOf(0, 120, 80, 120, 80, 200), -1))
            playSound(SoundEffectPlayer.Effect.FINAL_ROUND_ALERT)
            unlockAchievement(Achievement.FINAL_ROUND)
            view.post { view.callback?.onFinalRoundStarted() }
        }
    }

    // ---- Effects -------------------------------------------------------------------------

    // A popped balloon: confetti in its color flying out, a few bright sparks and a flash.
    private fun popBurst(x: Float, y: Float, radius: Float, color: Int) {
        val pieces = 16
        for (i in 0 until pieces) {
            val angle = MathUtils.random(MathUtils.PI2)
            val speed = MathUtils.random(160f, 420f) * (0.6f + radius / 120f)
            particles.spawn(
                Particles.Kind.CONFETTI, x, y,
                MathUtils.cos(angle) * speed, MathUtils.sin(angle) * speed,
                MathUtils.random(0.5f, 0.85f),
                MathUtils.random(9f, 16f), 7f,
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
                MathUtils.random(0.3f, 0.55f),
                MathUtils.random(18f, 30f), 4f,
                color,
                drag = 4f
            )
        }
    }

    // A balloon running out of air: a last little sigh of smoke.
    private fun deflateBurst(x: Float, y: Float) {
        for (i in 0 until 10) {
            val angle = MathUtils.random(MathUtils.PI2)
            val speed = MathUtils.random(30f, 110f)
            particles.spawn(
                Particles.Kind.SMOKE, x, y,
                MathUtils.cos(angle) * speed, MathUtils.sin(angle) * speed,
                MathUtils.random(0.6f, 1.1f),
                22f, 70f,
                DEFLATE_SMOKE_COLOR,
                alpha = 0.35f,
                drag = 1.5f
            )
        }
    }

    // ---- Frame -----------------------------------------------------------------------------

    private fun draw(engine: GameEngine, dt: Float, width: Float, height: Float) {
        beaconPhase += dt
        elapsedRealTime += dt
        particles.update(dt)

        val player = engine.player
        val camX = cameraCoord(player.position.x, width, GameConfig.WORLD_WIDTH)
        val camY = cameraCoord(player.position.y, height, GameConfig.WORLD_HEIGHT)
        worldCamera.position.set(camX, camY, 0f)
        worldCamera.update()
        val left = camX - width / 2f
        val top = camY - height / 2f
        val right = left + width
        val bottom = top + height

        painter.begin(worldCamera.combined)
        // The floor is fully opaque, so it skips blending altogether.
        painter.blending(false)
        drawFloor(left, top, right, bottom)
        painter.blending(true)
        drawWorldBorder()
        drawSafeZone(engine, left, top, right, bottom)

        // Light that sits under everything else: the zone wall and the power-up halos.
        painter.additive(true)
        drawSafeZoneGlow(engine)
        for (powerUp in engine.powerUps) {
            if (isVisible(powerUp.position.x, powerUp.position.y, powerUp.radius * 4f, left, top, right, bottom)) {
                drawPowerUpHalo(powerUp)
            }
        }
        painter.additive(false)

        for (powerUp in engine.powerUps) {
            // Covers the supply drop beacon's widest ring too (see drawSupplyDropBeacon).
            val extent = powerUp.radius + GameConfig.POWERUP_RADIUS * 4.6f
            if (isVisible(powerUp.position.x, powerUp.position.y, extent, left, top, right, bottom)) {
                drawPowerUp(powerUp)
            }
        }

        // Only balloons actually on screen get drawn - with 100 bots spread over the whole
        // arena, only a handful are ever visible at once.
        for (blob in engine.blobs) {
            if (!blob.alive || !isBlobVisible(blob, left, top, right, bottom)) continue
            balloons.drawShadow(stateOf(blob))
        }
        particles.draw(painter, Particles.Kind.SMOKE, painter.atlas.glow, left, top, right, bottom)
        val smokeMargin = width * 0.25f
        for (blob in engine.blobs) {
            if (!blob.alive) continue
            // Smoke keeps being emitted a little beyond the screen edge, so a balloon flying in
            // arrives with its trail already behind it.
            if (isVisible(blob.position.x, blob.position.y, blob.radius * BalloonPainter.VISUAL_EXTENT + smokeMargin, left, top, right, bottom)) {
                balloons.emitSmoke(stateOf(blob), dt)
            }
            if (isBlobVisible(blob, left, top, right, bottom)) balloons.drawBalloon(stateOf(blob))
        }

        drawAbsorbAnimations(dt)
        drawEffectRipples(dt)
        particles.draw(painter, Particles.Kind.CONFETTI, painter.atlas.white, left, top, right, bottom)

        painter.additive(true)
        for (blob in engine.blobs) {
            if (blob.alive && isBlobVisible(blob, left, top, right, bottom)) balloons.drawGlow(stateOf(blob))
        }
        particles.draw(painter, Particles.Kind.SPARK, painter.atlas.sparkle, left, top, right, bottom)
        drawFlashes(dt)
        painter.additive(false)

        drawFloatingTexts(dt)
        painter.end()

        // Screen space, grouped by texture - atlas shapes, then text, then the overlays drawn over
        // the HUD, then their own text - so the whole HUD costs a handful of draw calls rather
        // than one per string, without changing what's layered over what.
        painter.begin(screenCamera.combined)
        drawDangerIndicator(engine, camX - width / 2f, camY - height / 2f, width, height)
        drawPermanentStatsHud(player, width, text = false)
        drawHud(engine, width)
        drawPermanentStatsHud(player, width, text = true)
        drawFeed(dt)
        drawFinalRoundWarning(engine, width, height)
        drawCountdown(width, height, text = false)
        drawFinalRoundTransition(width, height, text = false)
        drawAchievementPopups(dt, width, text = false)
        if (showFps) fpsCounter.drawBackground(painter, width, height)
        drawCountdown(width, height, text = true)
        drawFinalRoundTransition(width, height, text = true)
        if (!countdownActive && !finalRoundTransitionActive && !paused) checkLiveAchievements(player)
        drawAchievementPopups(dt, width, text = true)
        if (showFps) fpsCounter.drawText(painter, width, height)
        painter.end()
    }

    private fun stateOf(blob: Blob): BalloonPainter.State = balloonState.apply {
        val isPlayer = blob === engine?.player
        x = blob.position.x
        y = blob.position.y
        radius = blob.radius
        facingX = blob.facingDirection.x
        facingY = blob.facingDirection.y
        color = blob.color
        alpha = if (blob.isInvisible) 70f / 255f else 1f
        exhaustPhase = blob.exhaustPhase
        isThrusting = blob.isThrusting
        isBoosting = blob.isBoosting
        isFrozen = blob.isFrozen
        isShielded = blob.isShielded
        isSpeedBoosted = blob.isSpeedBoosted
        // Cosmetics only ever apply to the player's own balloon - bots keep the plain look.
        sticker = if (isPlayer) selectedSticker else BalloonSticker.NONE
        cordColor = if (isPlayer) selectedCord.colorInt else BalloonCord.CLASSIC_GREY.colorInt
        exhaustStyle = if (isPlayer) selectedExhaustStyle else ExhaustStyle.CLASSIC
    }

    private fun isBlobVisible(blob: Blob, left: Float, top: Float, right: Float, bottom: Float) =
        isVisible(blob.position.x, blob.position.y, blob.radius * BalloonPainter.VISUAL_EXTENT, left, top, right, bottom)

    private fun isVisible(x: Float, y: Float, extent: Float, left: Float, top: Float, right: Float, bottom: Float) =
        x + extent >= left && x - extent <= right && y + extent >= top && y - extent <= bottom

    private fun cameraCoord(playerCoord: Float, viewportSize: Float, worldSize: Float): Float {
        val halfViewport = viewportSize / 2f
        if (worldSize <= viewportSize) return worldSize / 2f
        return playerCoord.coerceIn(halfViewport, worldSize - halfViewport)
    }

    // Checkerboard tiles give the arena a visible floor and double as a scale reference while
    // moving. Only the cells actually on screen are drawn. The screen's vignette is baked into
    // the tiles' corner colors (see Painter.vignetteDarkness) rather than drawn as a separate
    // darkening layer over the whole screen - the arena reads as lit from the middle at no
    // extra cost per pixel.
    private fun drawFloor(left: Float, top: Float, right: Float, bottom: Float) {
        val startCol = (left.coerceAtLeast(0f) / FLOOR_CELL).toInt()
        val endCol = (right.coerceAtMost(GameConfig.WORLD_WIDTH) / FLOOR_CELL).toInt()
        val startRow = (top.coerceAtLeast(0f) / FLOOR_CELL).toInt()
        val endRow = (bottom.coerceAtMost(GameConfig.WORLD_HEIGHT) / FLOOR_CELL).toInt()
        val centerX = (left + right) / 2f
        val centerY = (top + bottom) / 2f
        val halfDiagonal = hypot(right - left, bottom - top) / 2f
        fun shade(color: Int, x: Float, y: Float): Float {
            val light = 1f - Painter.vignetteDarkness(hypot(x - centerX, y - centerY) / halfDiagonal)
            return Painter.packedColor(scaleRgb(color, light))
        }
        for (row in startRow..endRow) {
            for (col in startCol..endCol) {
                val color = if ((row + col) % 2 == 0) FLOOR_COLOR_A else FLOOR_COLOR_B
                val x0 = col * FLOOR_CELL
                val y0 = row * FLOOR_CELL
                val x1 = min(x0 + FLOOR_CELL, GameConfig.WORLD_WIDTH)
                val y1 = min(y0 + FLOOR_CELL, GameConfig.WORLD_HEIGHT)
                if (x1 <= x0 || y1 <= y0) continue
                painter.shadedRect(
                    x0, y0, x1 - x0, y1 - y0,
                    shade(color, x0, y0), shade(color, x1, y0), shade(color, x1, y1), shade(color, x0, y1)
                )
            }
        }
    }

    private fun scaleRgb(color: Int, factor: Float): Int = Color.rgb(
        (Color.red(color) * factor).toInt(),
        (Color.green(color) * factor).toInt(),
        (Color.blue(color) * factor).toInt()
    )

    private fun drawWorldBorder() {
        painter.color(BORDER_COLOR)
        painter.rectStroke(0f, 0f, GameConfig.WORLD_WIDTH, GameConfig.WORLD_HEIGHT, 24f)
    }

    private fun drawSafeZone(engine: GameEngine, left: Float, top: Float, right: Float, bottom: Float) {
        val cx = engine.safeZoneCenterX
        val cy = engine.safeZoneCenterY
        val r = engine.safeZoneRadius
        // Dim everything outside the shrinking safe circle: a band from the zone edge out to the
        // farthest screen corner, so it covers exactly what the old clip-out covered.
        val farthest = max(
            max(hypot(left - cx, top - cy), hypot(right - cx, top - cy)),
            max(hypot(left - cx, bottom - cy), hypot(right - cx, bottom - cy))
        ) + 8f
        painter.ring(cx, cy, r, farthest, SAFE_ZONE_FILL_COLOR, 1f, SAFE_ZONE_FILL_COLOR, 1f)
        // A faint red haze creeping in just inside the edge, so the wall reads from a distance.
        painter.ring(cx, cy, r - 110f, r, SAFE_ZONE_EDGE_COLOR, 0f, SAFE_ZONE_EDGE_COLOR, 0.22f)
        painter.color(SAFE_ZONE_EDGE_COLOR)
        painter.circleStroke(cx, cy, r, 6f)
        // While holding, telegraph where the zone is about to shrink to next.
        if (engine.isZoneHolding) {
            painter.color(NEXT_ZONE_COLOR)
            painter.circleStroke(cx, cy, engine.nextSafeZoneRadius, 4f, dashed = true)
        }
    }

    // The zone's wall glows, pulsing gently - additive, so it lights up whatever it passes over.
    private fun drawSafeZoneGlow(engine: GameEngine) {
        val cx = engine.safeZoneCenterX
        val cy = engine.safeZoneCenterY
        val r = engine.safeZoneRadius
        val pulse = 0.3f + 0.12f * sin(elapsedRealTime * 3.2f)
        painter.ring(cx, cy, r - 34f, r, SAFE_ZONE_GLOW_COLOR, 0f, SAFE_ZONE_GLOW_COLOR, pulse)
        painter.ring(cx, cy, r, r + 34f, SAFE_ZONE_GLOW_COLOR, pulse, SAFE_ZONE_GLOW_COLOR, 0f)
    }

    private fun drawPowerUpHalo(powerUp: PowerUp) {
        val pulse = 0.75f + 0.25f * sin(beaconPhase * 4f + powerUp.position.x * 0.01f)
        painter.color(powerUpColor(powerUp.type), 0.5f * pulse)
        painter.glow(powerUp.position.x, powerUp.position.y, powerUp.radius * 3.2f)
    }

    private fun drawPowerUp(powerUp: PowerUp) {
        val cx = powerUp.position.x
        val cy = powerUp.position.y
        if (powerUp.type == PowerUpType.SHIELD) drawSupplyDropBeacon(cx, cy)
        painter.color(powerUpColor(powerUp.type))
        painter.disc(cx, cy, powerUp.radius)
        // A little gloss, like the balloons.
        painter.color(Color.WHITE, 0.35f)
        painter.glow(cx - powerUp.radius * 0.35f, cy - powerUp.radius * 0.4f, powerUp.radius * 0.55f)
        painter.color(Color.WHITE)
        val iconSize = Atlas.ICON_CELL / Atlas.GAME_ICON_SCALE
        painter.region(painter.atlas.gameIcons[powerUp.type.ordinal], cx, cy, iconSize, iconSize)
    }

    // A rare supply drop (always SHIELD) is telegraphed with pulsing outward rings.
    private fun drawSupplyDropBeacon(cx: Float, cy: Float) {
        val cyclePhase = (beaconPhase % 1.2f) / 1.2f
        val ringRadius = GameConfig.POWERUP_RADIUS * (1.6f + cyclePhase * 3f)
        painter.color(SUPPLY_BEACON_COLOR, (1f - cyclePhase) * 180f / 255f)
        painter.circleStroke(cx, cy, ringRadius, 4f)
    }

    private fun drawAbsorbAnimations(dt: Float) {
        val iterator = absorbAnims.iterator()
        while (iterator.hasNext()) {
            val anim = iterator.next()
            anim.elapsed += dt
            val progress = (anim.elapsed / anim.duration).coerceIn(0f, 1f)
            if (progress >= 1f) {
                iterator.remove()
                continue
            }
            // Eased pull toward the absorber's current (moving) position, shrinking and fading
            // as it goes, so it visibly gets dragged in and swallowed.
            val eased = progress * progress
            val x = anim.startX + (anim.absorber.position.x - anim.startX) * eased
            val y = anim.startY + (anim.absorber.position.y - anim.startY) * eased
            val radius = (anim.startRadius * (1f - progress)).coerceAtLeast(0f)
            painter.color(anim.color, 1f - progress)
            painter.disc(x, y, radius)
        }
    }

    private fun drawEffectRipples(dt: Float) {
        val iterator = effectRipples.iterator()
        while (iterator.hasNext()) {
            val ripple = iterator.next()
            ripple.ttl -= dt
            if (ripple.ttl <= 0f) {
                iterator.remove()
                continue
            }
            val progress = 1f - (ripple.ttl / ripple.maxTtl)
            val radiusProgress = if (ripple.inward) 1f - progress else progress
            painter.color(ripple.ringColor, 1f - progress)
            painter.circleStroke(ripple.x, ripple.y, ripple.maxRadius * radiusProgress, 6f)
        }
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
            text.ttl -= dt
            if (text.ttl <= 0f) {
                iterator.remove()
                continue
            }
            text.y -= 30f * dt
            val alpha = (text.ttl / text.maxTtl).coerceIn(0f, 1f)
            // A soft dark drop behind the text keeps it readable over bright balloons.
            painter.text(text.text, text.x + 2f, text.y + 2f, FLOATING_TEXT_STYLE, Color.BLACK, alpha * 0.5f)
            painter.text(text.text, text.x, text.y, FLOATING_TEXT_STYLE, text.color, alpha)
        }
    }

    // Off-screen pointer to the nearest threat (or prey, if no threat) within awareness range.
    private fun drawDangerIndicator(engine: GameEngine, offsetLeft: Float, offsetTop: Float, width: Float, height: Float) {
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
        val screenX = target.position.x - offsetLeft
        val screenY = target.position.y - offsetTop
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
        val color = if (target === threat) THREAT_COLOR else PREY_COLOR

        // Same arrow as before (tip 18px ahead of center, 28px base 14px behind it), with a
        // pulsing glow so it catches the eye at the edge of the screen.
        painter.additive(true)
        painter.color(color, 0.35f + 0.15f * sin(elapsedRealTime * 8f))
        painter.glow(px, py, 34f)
        painter.additive(false)
        painter.color(color)
        painter.triangle(px - ndx * 14f, py - ndy * 14f, px + ndx * 18f, py + ndy * 18f, 28f)
    }

    private fun drawHud(engine: GameEngine, width: Float) {
        val player = engine.player
        val size = player.radius.toInt()
        if (size != hudSizeValue) {
            hudSizeValue = size
            hudSizeText = context.getString(R.string.game_hud_size_format, size)
        }
        hudText(hudSizeText, 24f, 56f, HUD_TEXT_STYLE, Color.WHITE)
        val absorbed = engine.playerOpponentsAbsorbed
        if (absorbed != hudAbsorbedValue) {
            hudAbsorbedValue = absorbed
            hudAbsorbedText = context.getString(R.string.game_hud_absorptions_format, absorbed)
        }
        hudText(hudAbsorbedText, 24f, 90f, HUD_TEXT_STYLE, Color.WHITE)
        val playersAlive = engine.aliveCount()
        if (playersAlive != hudPlayersValue) {
            hudPlayersValue = playersAlive
            hudPlayersText = context.getString(R.string.game_hud_players_format, playersAlive)
        }
        hudText(hudPlayersText, 24f, 124f, HUD_TEXT_STYLE, Color.WHITE)

        val zonePercent = (engine.safeZoneProgress * 100f).toInt()
        if (zonePercent != hudZoneValue) {
            hudZoneValue = zonePercent
            hudZoneText = context.getString(R.string.game_hud_zone_format, zonePercent)
        }
        hudText(hudZoneText, width / 2f, 56f, ZONE_HUD_STYLE, SAFE_ZONE_EDGE_COLOR)

        val elapsedSeconds = engine.matchElapsed.toInt()
        if (elapsedSeconds != hudTimeValue) {
            hudTimeValue = elapsedSeconds
            hudTimeText = "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)
        }
        hudText(hudTimeText, width / 2f, 90f, TIMER_HUD_STYLE, Color.WHITE)

        // playerWon is always false here since the match is still running - this omits the win
        // bonus HighScores.computeScore adds once it actually ends.
        val liveScore = HighScores.computeScore(
            playerWon = false,
            elapsedSeconds = elapsedSeconds,
            opponentsAbsorbed = engine.playerOpponentsAbsorbed,
            reachedFinalRound = engine.isFinalRoundActive
        )
        if (liveScore != hudScoreValue) {
            hudScoreValue = liveScore
            hudScoreText = context.getString(R.string.game_hud_score_format, liveScore)
        }
        hudText(hudScoreText, width / 2f, 134f, LIVE_SCORE_HUD_STYLE, ACCENT_COLOR)

    }

    private fun hudText(value: String, x: Float, y: Float, style: TextStyle, argb: Int) {
        painter.text(value, x, y, style, argb)
    }

    // Small pip bars per permanent stat, below the pause button - the pips (text = false) and
    // their labels (text = true) are drawn in separate passes, see draw().
    private fun drawPermanentStatsHud(player: Blob, width: Float, text: Boolean) {
        var y = (16f + 52f + 14f) * density
        y = drawStatPips(statSpeedLabel, player.permanentSpeedMultiplier, GameConfig.PERMANENT_SPEED_MAX_MULTIPLIER, powerUpColor(PowerUpType.SPEED_UP), y, width, text)
        y = drawStatPips(statAgilityLabel, player.permanentTurnRateMultiplier, GameConfig.PERMANENT_TURN_RATE_MAX_MULTIPLIER, powerUpColor(PowerUpType.AGILITY_UP), y, width, text)
        drawStatPips(statPotencyLabel, player.permanentPotencyMultiplier, GameConfig.PERMANENT_POTENCY_MAX_MULTIPLIER, powerUpColor(PowerUpType.POTENCY_UP), y, width, text)
    }

    private fun drawStatPips(label: String, multiplier: Float, maxMultiplier: Float, filledColor: Int, y: Float, width: Float, text: Boolean): Float {
        val pipCount = GameConfig.PERMANENT_STAT_TIER_COUNT
        val fraction = ((multiplier - 1f) / (maxMultiplier - 1f)).coerceIn(0f, 1f)
        val filledCount = Math.round(fraction * pipCount).coerceIn(0, pipCount)
        val pipSize = 13f
        val pipGap = 4f
        val pipsWidth = pipCount * pipSize + (pipCount - 1) * pipGap
        val rightEdge = width - 24f
        val blockLeft = rightEdge - pipsWidth
        if (text) {
            val labelWidth = painter.measure(label, STATS_HUD_STYLE)
            painter.text(label, blockLeft - labelWidth - 12f, y, STATS_HUD_STYLE, STATS_LABEL_COLOR)
            return y + 28f
        }
        for (i in 0 until pipCount) {
            val left = blockLeft + i * (pipSize + pipGap)
            val top = y - pipSize + 4f
            painter.color(if (i < filledCount) filledColor else STAT_PIP_EMPTY_COLOR)
            painter.rect(left, top, pipSize, pipSize)
        }
        return y + 28f
    }

    private fun drawFeed(dt: Float) {
        val iterator = feedEntries.iterator()
        var row = 0
        while (iterator.hasNext()) {
            val entry = iterator.next()
            entry.ttl -= dt
            if (entry.ttl <= 0f) {
                iterator.remove()
                continue
            }
            val alpha = (entry.ttl / entry.maxTtl).coerceIn(0f, 1f)
            painter.text(entry.text, 24f, FEED_TOP + row * FEED_ROW_HEIGHT, FEED_TEXT_STYLE, Color.WHITE, alpha)
            row++
        }
    }

    // A soft golden glow behind each popup (text = false), then the popups themselves, which is
    // also where they age and expire (text = true).
    private fun drawAchievementPopups(dt: Float, width: Float, text: Boolean) {
        if (!text) {
            painter.additive(true)
            achievementPopups.forEachIndexed { row, popup ->
                val alpha = (popup.ttl / popup.maxTtl).coerceIn(0f, 1f)
                painter.color(ACHIEVEMENT_COLOR, 0.25f * alpha)
                val glowWidth = painter.measure(popup.text, ACHIEVEMENT_STYLE) + 120f
                painter.region(painter.atlas.glow, width / 2f, ACHIEVEMENT_POPUP_TOP + row * 44f - 10f, glowWidth, 70f)
            }
            painter.additive(false)
            return
        }
        val iterator = achievementPopups.iterator()
        var row = 0
        while (iterator.hasNext()) {
            val popup = iterator.next()
            popup.ttl -= dt
            if (popup.ttl <= 0f) {
                iterator.remove()
                continue
            }
            val alpha = (popup.ttl / popup.maxTtl).coerceIn(0f, 1f)
            painter.text(popup.text, width / 2f, ACHIEVEMENT_POPUP_TOP + row * 44f, ACHIEVEMENT_STYLE, ACHIEVEMENT_COLOR, alpha)
            row++
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
            painter.glow(width / 2f, height / 2f, 220f)
            painter.additive(false)
            return
        }
        val secondsLeft = ceil(countdownRemaining).toInt().coerceAtLeast(1)
        // Each number punches in and settles, rather than just swapping.
        val intoSecond = 1f - (countdownRemaining - (secondsLeft - 1))
        val punch = 1f + 0.35f * (1f - intoSecond.coerceIn(0f, 1f) / 0.25f).coerceAtLeast(0f)
        painter.text(secondsLeft.toString(), width / 2f, height / 2f + 60f, COUNTDOWN_STYLE, Color.WHITE, 1f, punch)
    }

    // Cinematic cut into the final round: a quick white flash with the title card scaling in.
    private fun drawFinalRoundTransition(width: Float, height: Float, text: Boolean) {
        if (!finalRoundTransitionActive) return
        val scale = 0.6f + 0.4f * (finalRoundTransitionElapsed / 0.4f).coerceIn(0f, 1f)
        if (text) {
            painter.text(finalRoundTitle, width / 2f, height / 2f, FINAL_ROUND_TITLE_STYLE, SAFE_ZONE_EDGE_COLOR, 1f, scale)
            return
        }
        painter.color(Color.argb(170, 40, 0, 0))
        painter.rect(0f, 0f, width, height)
        val flashAlpha = (1f - finalRoundTransitionElapsed / 0.2f).coerceIn(0f, 1f)
        if (flashAlpha > 0f) {
            painter.color(Color.WHITE, flashAlpha)
            painter.rect(0f, 0f, width, height)
        }
        painter.additive(true)
        painter.color(SAFE_ZONE_EDGE_COLOR, 0.35f)
        painter.region(painter.atlas.glow, width / 2f, height / 2f - 20f, width * 1.1f, 260f)
        painter.additive(false)
    }

    // The advance warning: still normal, playable gameplay underneath, with a pulsing heads-up
    // and countdown over it for the last few seconds before the final round triggers.
    private fun drawFinalRoundWarning(engine: GameEngine, width: Float, height: Float) {
        if (countdownActive || finalRoundTransitionActive) return
        val secondsRemaining = engine.secondsUntilFinalRound
        if (secondsRemaining <= 0f || secondsRemaining > GameConfig.FINAL_ROUND_WARNING_SECONDS) return
        val secondsLeft = ceil(secondsRemaining).toInt().coerceAtLeast(1)
        val fractionIntoSecond = ceil(secondsRemaining) - secondsRemaining
        val punch = 1.3f - 0.3f * fractionIntoSecond.coerceIn(0f, 1f)
        hudText(finalRoundWarning, width / 2f, height / 2f - 130f, FINAL_ROUND_WARNING_TITLE_STYLE, SAFE_ZONE_EDGE_COLOR)
        painter.text(secondsLeft.toString(), width / 2f, height / 2f - 70f, FINAL_ROUND_WARNING_COUNTDOWN_STYLE, ACCENT_COLOR, 1f, punch)
    }

    // ---- Non-drawing logic, unchanged from the old GameView --------------------------------

    private fun blobLabel(id: Int): String = if (id == 0) {
        context.getString(R.string.game_you_label)
    } else {
        botNames.getOrElse(id - 1) { context.getString(R.string.game_bot_label_format, id) }
    }

    private fun addFeedEntry(text: String) {
        feedEntries.add(FeedEntry(text, 3.5f))
        if (feedEntries.size > MAX_FEED_ENTRIES) feedEntries.removeAt(0)
    }

    private fun showPopup(text: String) {
        achievementPopups.add(AchievementPopup(text, 3f))
    }

    // Unlocks are idempotent, so every call site fires unconditionally. Every brand-new unlock
    // also pays out a small one-time Helium bonus.
    private fun unlockAchievement(achievement: Achievement) {
        if (Achievements.unlock(context, achievement)) {
            Wallet.add(context, HeliumRewards.ACHIEVEMENT_UNLOCK_BONUS)
            showPopup(context.getString(R.string.achievement_unlocked_format, context.getString(achievement.titleResId)))
        }
    }

    private fun checkLiveRecord(type: RecordType, value: Int) {
        if (PersonalRecords.tryBeat(context, type, value) && announcedRecordsThisMatch.add(type)) {
            showPopup(
                context.getString(
                    R.string.record_broken_format,
                    context.getString(type.titleResId),
                    PersonalRecords.formatValue(context, type, value)
                )
            )
        }
    }

    private fun checkLiveAchievements(player: Blob) {
        if (!maxSizeAchievementChecked && player.radius >= GameConfig.MAX_RADIUS) {
            maxSizeAchievementChecked = true
            unlockAchievement(Achievement.MAX_SIZE)
        }
        if (!maxBoostAchievementChecked && player.isBoostAtMaxPower) {
            maxBoostAchievementChecked = true
            unlockAchievement(Achievement.MAX_BOOST)
        }
        if (RecordType.SIZE !in announcedRecordsThisMatch) {
            matchPeakSize = maxOf(matchPeakSize, player.radius.toInt())
            checkLiveRecord(RecordType.SIZE, matchPeakSize)
        }
    }

    private fun checkBoostAvailability(engine: GameEngine) {
        // Sprint works everywhere, any time - only dim the button once there's truly nothing
        // left to burn without dying.
        val player = engine.player
        val available = player.alive && player.radius > GameConfig.ZONE_DEATH_RADIUS + 0.5f
        if (available != lastBoostAvailable) {
            lastBoostAvailable = available
            view.post { view.callback?.onBoostAvailabilityChanged(available) }
        }
    }

    private fun checkCarriedItemAvailability(engine: GameEngine) {
        val player = engine.player
        val type = if (player.alive) player.carriedItem else null
        if (type != lastCarriedItemType) {
            lastCarriedItemType = type
            view.post { view.callback?.onCarriedItemChanged(type) }
        }
    }

    private fun playSound(effect: SoundEffectPlayer.Effect) {
        soundEffectPlayerCreated = true
        soundEffectPlayer.play(effect)
    }

    private fun vibrate(effect: VibrationEffect) {
        if (VibrationSettings.isEnabled(context)) vibrator.vibrate(effect)
    }

    private fun powerUpLabel(type: PowerUpType): String = when (type) {
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

    companion object {
        private const val PARTICLE_CAPACITY = 2400
        private const val SETTLE_AFTER_END_SECONDS = 2f
        private const val FLOOR_CELL = 200f
        private const val MAX_FEED_ENTRIES = 5
        private const val FEED_TOP = 172f
        private const val FEED_ROW_HEIGHT = 34f
        private const val ACHIEVEMENT_POPUP_TOP = FEED_TOP + MAX_FEED_ENTRIES * FEED_ROW_HEIGHT + 18f

        private const val BACKGROUND_R = 0x0F / 255f
        private const val BACKGROUND_G = 0x16 / 255f
        private const val BACKGROUND_B = 0x20 / 255f
        private val FLOOR_COLOR_A = Color.parseColor("#121B26")
        private val FLOOR_COLOR_B = Color.parseColor("#0B1119")
        private val BORDER_COLOR = Color.parseColor("#FFC107")
        private val ACCENT_COLOR = Color.parseColor("#FFC107")
        private val SAFE_ZONE_FILL_COLOR = Color.argb(90, 200, 40, 40)
        private val SAFE_ZONE_EDGE_COLOR = Color.parseColor("#EF5350")
        private val SAFE_ZONE_GLOW_COLOR = Color.parseColor("#FF5252")
        private val NEXT_ZONE_COLOR = Color.parseColor("#FFD54F")
        private val SUPPLY_BEACON_COLOR = Color.parseColor("#4FC3F7")
        private val THREAT_COLOR = Color.parseColor("#EF5350")
        private val PREY_COLOR = Color.parseColor("#8BC34A")
        private val PLAYER_GAIN_COLOR = Color.parseColor("#8BC34A")
        private val STATS_LABEL_COLOR = Color.parseColor("#B0BEC5")
        private val STAT_PIP_EMPTY_COLOR = Color.parseColor("#3A4750")
        private val ACHIEVEMENT_COLOR = Color.parseColor("#FFD700")
        private val DEFLATE_SMOKE_COLOR = Color.parseColor("#9AA7B2")

        private val FLOATING_TEXT_STYLE = TextStyle(34f, bold = true, align = TextStyle.Align.CENTER)
        private val HUD_TEXT_STYLE = TextStyle(42f, bold = true)
        private val ZONE_HUD_STYLE = TextStyle(34f, bold = true, align = TextStyle.Align.CENTER)
        private val TIMER_HUD_STYLE = TextStyle(30f, bold = true, align = TextStyle.Align.CENTER)
        private val LIVE_SCORE_HUD_STYLE = TextStyle(40f, bold = true, align = TextStyle.Align.CENTER)
        private val STATS_HUD_STYLE = TextStyle(26f)
        private val FEED_TEXT_STYLE = TextStyle(28f)
        private val COUNTDOWN_STYLE = TextStyle(180f, bold = true, align = TextStyle.Align.CENTER)
        private val ACHIEVEMENT_STYLE = TextStyle(32f, bold = true, align = TextStyle.Align.CENTER)
        private val FINAL_ROUND_TITLE_STYLE = TextStyle(58f, bold = true, align = TextStyle.Align.CENTER)
        private val FINAL_ROUND_WARNING_TITLE_STYLE = TextStyle(34f, bold = true, align = TextStyle.Align.CENTER)
        private val FINAL_ROUND_WARNING_COUNTDOWN_STYLE = TextStyle(70f, bold = true, align = TextStyle.Align.CENTER)

        // Single source of truth for each power-up's color - the pickup, its halo, its use
        // ripple and the stat pips all share it.
        fun powerUpColor(type: PowerUpType): Int = POWER_UP_COLORS[type.ordinal]

        private val POWER_UP_COLORS = PowerUpType.values().map {
            when (it) {
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
        }.toIntArray()
    }
}
