package com.hyperionsoftware.balls

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.MotionEvent
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.hyperionsoftware.balls.audio.BackgroundMusicPlayer
import com.hyperionsoftware.balls.audio.MusicSettings
import com.hyperionsoftware.balls.databinding.ActivityGameBinding
import com.hyperionsoftware.balls.economy.HeliumRewards
import com.hyperionsoftware.balls.economy.Wallet
import com.hyperionsoftware.balls.game.GameConfig
import com.hyperionsoftware.balls.game.PowerUpType
import com.hyperionsoftware.balls.score.HighScores
import com.hyperionsoftware.balls.ui.GameView

class GameActivity : AppCompatActivity(), GameView.Callback {

    private lateinit var binding: ActivityGameBinding
    private var botCount = DEFAULT_BOTS
    private var powerUpFrequencyLevel = GameConfig.POWERUP_DEFAULT_FREQUENCY_LEVEL
    private var arenaSize = GameConfig.ArenaSize.NORMAL
    private var skipToFinalRound = false
    private var botAggressivenessLevel = GameConfig.BOT_AGGRESSIVENESS_DEFAULT_LEVEL
    private var safeZoneShrinkSpeedLevel = GameConfig.SAFE_ZONE_SHRINK_SPEED_DEFAULT_LEVEL
    private val musicPlayer = BackgroundMusicPlayer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        botCount = intent.getIntExtra(EXTRA_BOT_COUNT, DEFAULT_BOTS)
        powerUpFrequencyLevel = intent.getIntExtra(
            EXTRA_POWERUP_FREQUENCY,
            GameConfig.POWERUP_DEFAULT_FREQUENCY_LEVEL
        )
        arenaSize = intent.getStringExtra(EXTRA_ARENA_SIZE)?.let {
            runCatching { GameConfig.ArenaSize.valueOf(it) }.getOrNull()
        } ?: GameConfig.ArenaSize.NORMAL
        skipToFinalRound = intent.getBooleanExtra(EXTRA_SKIP_TO_FINAL_ROUND, false)
        botAggressivenessLevel = intent.getIntExtra(EXTRA_BOT_AGGRESSIVENESS, GameConfig.BOT_AGGRESSIVENESS_DEFAULT_LEVEL)
        safeZoneShrinkSpeedLevel = intent.getIntExtra(
            EXTRA_SAFE_ZONE_SHRINK_SPEED,
            GameConfig.SAFE_ZONE_SHRINK_SPEED_DEFAULT_LEVEL
        )

        binding.joystickView.listener = binding.gameView
        binding.gameView.callback = this
        binding.pauseButton.setOnClickListener { showPauseDialog() }
        binding.boostButton.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> binding.gameView.setBoosting(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> binding.gameView.setBoosting(false)
            }
            true
        }
        binding.activeItemButton.setOnClickListener { binding.gameView.useActiveItem() }
        // Matches the player's starting size (no excess to burn yet): boost looks
        // disabled until GameView reports it's actually available.
        binding.boostButton.alpha = BOOST_UNAVAILABLE_ALPHA
        // No carried item at match start either.
        binding.activeItemButton.alpha = ACTIVE_ITEM_UNAVAILABLE_ALPHA

        binding.gameView.startGame(
            botCount, powerUpFrequencyLevel, arenaSize, skipToFinalRound, botAggressivenessLevel, safeZoneShrinkSpeedLevel
        )
    }

    override fun onResume() {
        super.onResume()
        binding.gameView.resumeGame()
        if (MusicSettings.isEnabled(this)) musicPlayer.start(MusicSettings.getSelectedTrack(this))
    }

    override fun onPause() {
        super.onPause()
        binding.gameView.setBoosting(false)
        binding.gameView.pauseGame()
        musicPlayer.stop()
    }

    private fun showPauseDialog() {
        binding.gameView.setBoosting(false)
        binding.gameView.pauseGame()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.pause_title))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.pause_resume)) { _, _ -> binding.gameView.resumeGame() }
            .setNeutralButton(getString(R.string.pause_restart)) { _, _ ->
                binding.gameView.restart(
                    botCount, powerUpFrequencyLevel, arenaSize, skipToFinalRound, botAggressivenessLevel, safeZoneShrinkSpeedLevel
                )
            }
            .setNegativeButton(getString(R.string.pause_menu)) { _, _ -> goToMenu() }
            .show()
    }

    override fun onGameOver(
        playerWon: Boolean,
        finalRadius: Int,
        playersRemaining: Int,
        opponentsAbsorbed: Int,
        elapsedSeconds: Int,
        reachedFinalRound: Boolean
    ) {
        val score = HighScores.computeScore(playerWon, elapsedSeconds, opponentsAbsorbed, reachedFinalRound)
        // The daily challenge itself is already recorded by GameView's own onGameOver
        // listener, which runs first and needs the streak up to date to check its
        // DAILY_DEDICATION achievement - nothing left to do with it here.

        val heliumEarned = HeliumRewards.forMatch(playerWon, opponentsAbsorbed, elapsedSeconds, reachedFinalRound)
        Wallet.add(this, heliumEarned)

        fun goToScores(highlightTimestamp: Long? = null) {
            startActivity(
                Intent(this, HighScoresActivity::class.java)
                    .putExtra(HighScoresActivity.EXTRA_MATCH_WON, playerWon)
                    .putExtra(HighScoresActivity.EXTRA_MATCH_SCORE, score)
                    .putExtra(HighScoresActivity.EXTRA_MATCH_FINAL_RADIUS, finalRadius)
                    .putExtra(HighScoresActivity.EXTRA_MATCH_ABSORBED, opponentsAbsorbed)
                    .putExtra(HighScoresActivity.EXTRA_MATCH_ELAPSED_SECONDS, elapsedSeconds)
                    .putExtra(HighScoresActivity.EXTRA_MATCH_REACHED_FINAL_ROUND, reachedFinalRound)
                    .putExtra(HighScoresActivity.EXTRA_MATCH_HELIUM_EARNED, heliumEarned)
                    .putExtra(EXTRA_BOT_COUNT, botCount)
                    .putExtra(EXTRA_POWERUP_FREQUENCY, powerUpFrequencyLevel)
                    .putExtra(EXTRA_ARENA_SIZE, arenaSize.name)
                    .putExtra(EXTRA_SKIP_TO_FINAL_ROUND, skipToFinalRound)
                    .putExtra(EXTRA_BOT_AGGRESSIVENESS, botAggressivenessLevel)
                    .putExtra(EXTRA_SAFE_ZONE_SHRINK_SPEED, safeZoneShrinkSpeedLevel)
                    .apply {
                        if (highlightTimestamp != null) {
                            putExtra(HighScoresActivity.EXTRA_HIGHLIGHT_TIMESTAMP, highlightTimestamp)
                        }
                    }
            )
            finish()
        }

        // Classic arcades only ever ask for initials when the score actually earns a spot
        // on the board, not after every single match - and the leaderboard itself is the
        // very next thing shown either way, no separate stats screen first.
        if (HighScores.wouldRank(this, score)) {
            promptForInitials { initials ->
                val timestamp = System.currentTimeMillis()
                HighScores.recordMatch(
                    this, initials, playerWon, finalRadius, opponentsAbsorbed, elapsedSeconds, reachedFinalRound,
                    timestamp
                )
                goToScores(highlightTimestamp = timestamp)
            }
        } else {
            goToScores()
        }
    }

    private fun promptForInitials(onDone: (String) -> Unit) {
        val input = EditText(this).apply {
            filters = arrayOf(InputFilter.LengthFilter(3), InputFilter.AllCaps())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            typeface = Typeface.MONOSPACE
            setText(HighScores.getLastInitials(this@GameActivity))
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.high_scores_enter_initials))
            .setView(input)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.high_scores_confirm)) { _, _ ->
                val initials = input.text.toString().trim().uppercase().ifBlank { "AAA" }.padEnd(3, 'A').take(3)
                onDone(initials)
            }
            .show()
    }

    override fun onBoostAvailabilityChanged(available: Boolean) {
        binding.boostButton.alpha = if (available) 1f else BOOST_UNAVAILABLE_ALPHA
    }

    override fun onCarriedItemChanged(type: PowerUpType?) {
        binding.activeItemButton.alpha = if (type != null) 1f else ACTIVE_ITEM_UNAVAILABLE_ALPHA
        val iconRes = when (type) {
            PowerUpType.SPEED -> R.drawable.ic_item_speed
            PowerUpType.INVISIBILITY -> R.drawable.ic_item_invisibility
            PowerUpType.REPEL -> R.drawable.ic_item_repel
            PowerUpType.FREEZE -> R.drawable.ic_item_freeze
            PowerUpType.HOOK -> R.drawable.ic_item_hook
            else -> R.drawable.ic_active_item
        }
        binding.activeItemButton.setImageResource(iconRes)
        binding.activeItemButton.contentDescription = when (type) {
            PowerUpType.SPEED -> getString(R.string.active_item_speed)
            PowerUpType.INVISIBILITY -> getString(R.string.active_item_invisibility)
            PowerUpType.REPEL -> getString(R.string.active_item_repel)
            PowerUpType.FREEZE -> getString(R.string.active_item_freeze)
            PowerUpType.HOOK -> getString(R.string.active_item_hook)
            else -> getString(R.string.active_item_button)
        }
    }

    private fun goToMenu() {
        startActivity(Intent(this, MainMenuActivity::class.java))
        finish()
    }

    companion object {
        const val EXTRA_BOT_COUNT = "extra_bot_count"
        const val EXTRA_POWERUP_FREQUENCY = "extra_powerup_frequency"
        const val EXTRA_ARENA_SIZE = "extra_arena_size"
        const val EXTRA_SKIP_TO_FINAL_ROUND = "extra_skip_to_final_round"
        const val EXTRA_BOT_AGGRESSIVENESS = "extra_bot_aggressiveness"
        const val EXTRA_SAFE_ZONE_SHRINK_SPEED = "extra_safe_zone_shrink_speed"
        private const val BOOST_UNAVAILABLE_ALPHA = 0.35f
        private const val ACTIVE_ITEM_UNAVAILABLE_ALPHA = 0.35f
        private const val DEFAULT_BOTS = 100
    }
}
