package com.hyperionsoftware.balls

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.hyperionsoftware.balls.audio.BackgroundMusicPlayer
import com.hyperionsoftware.balls.audio.MusicSettings
import com.hyperionsoftware.balls.databinding.ActivityGameBinding
import com.hyperionsoftware.balls.game.GameConfig
import com.hyperionsoftware.balls.game.PowerUpType
import com.hyperionsoftware.balls.ui.GameView

class GameActivity : AppCompatActivity(), GameView.Callback {

    private lateinit var binding: ActivityGameBinding
    private var botCount = DEFAULT_BOTS
    private var powerUpFrequencyLevel = GameConfig.POWERUP_DEFAULT_FREQUENCY_LEVEL
    private var arenaSize = GameConfig.ArenaSize.NORMAL
    private var skipToFinalRound = false
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

        binding.gameView.startGame(botCount, powerUpFrequencyLevel, arenaSize, skipToFinalRound)
    }

    override fun onResume() {
        super.onResume()
        binding.gameView.resumeGame()
        if (MusicSettings.isEnabled(this)) musicPlayer.start()
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
            .setNeutralButton(getString(R.string.pause_restart)) { _, _ -> binding.gameView.restart(botCount, powerUpFrequencyLevel, arenaSize, skipToFinalRound) }
            .setNegativeButton(getString(R.string.pause_menu)) { _, _ -> goToMenu() }
            .show()
    }

    override fun onGameOver(
        playerWon: Boolean,
        finalRadius: Int,
        playersRemaining: Int,
        opponentsAbsorbed: Int,
        elapsedSeconds: Int
    ) {
        val title = if (playerWon) {
            getString(R.string.game_over_win_title)
        } else {
            getString(R.string.game_over_lose_title)
        }
        val timeText = String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(getString(R.string.game_over_stats, timeText, finalRadius, playersRemaining, opponentsAbsorbed))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.game_over_restart)) { _, _ -> binding.gameView.restart(botCount, powerUpFrequencyLevel, arenaSize, skipToFinalRound) }
            .setNegativeButton(getString(R.string.game_over_menu)) { _, _ -> goToMenu() }
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
            else -> R.drawable.ic_active_item
        }
        binding.activeItemButton.setImageResource(iconRes)
        binding.activeItemButton.contentDescription = when (type) {
            PowerUpType.SPEED -> getString(R.string.active_item_speed)
            PowerUpType.INVISIBILITY -> getString(R.string.active_item_invisibility)
            PowerUpType.REPEL -> getString(R.string.active_item_repel)
            PowerUpType.FREEZE -> getString(R.string.active_item_freeze)
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
        private const val BOOST_UNAVAILABLE_ALPHA = 0.35f
        private const val ACTIVE_ITEM_UNAVAILABLE_ALPHA = 0.35f
        private const val DEFAULT_BOTS = 100
    }
}
