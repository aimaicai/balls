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
import com.hyperionsoftware.balls.ui.GameView

class GameActivity : AppCompatActivity(), GameView.Callback {

    private lateinit var binding: ActivityGameBinding
    private var botCount = DEFAULT_BOTS
    private var powerUpFrequencyLevel = GameConfig.POWERUP_DEFAULT_FREQUENCY_LEVEL
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
        // Matches the player's starting size (no excess to burn yet): boost looks
        // disabled until GameView reports it's actually available.
        binding.boostButton.alpha = BOOST_UNAVAILABLE_ALPHA

        binding.gameView.startGame(botCount, powerUpFrequencyLevel)
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
            .setNeutralButton(getString(R.string.pause_restart)) { _, _ -> binding.gameView.restart(botCount, powerUpFrequencyLevel) }
            .setNegativeButton(getString(R.string.pause_menu)) { _, _ -> goToMenu() }
            .show()
    }

    override fun onGameOver(playerWon: Boolean, finalRadius: Int, playersRemaining: Int, opponentsAbsorbed: Int) {
        val title = if (playerWon) {
            getString(R.string.game_over_win_title)
        } else {
            getString(R.string.game_over_lose_title)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(getString(R.string.game_over_stats, finalRadius, playersRemaining, opponentsAbsorbed))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.game_over_restart)) { _, _ -> binding.gameView.restart(botCount, powerUpFrequencyLevel) }
            .setNegativeButton(getString(R.string.game_over_menu)) { _, _ -> goToMenu() }
            .show()
    }

    override fun onBoostAvailabilityChanged(available: Boolean) {
        binding.boostButton.alpha = if (available) 1f else BOOST_UNAVAILABLE_ALPHA
    }

    private fun goToMenu() {
        startActivity(Intent(this, MainMenuActivity::class.java))
        finish()
    }

    companion object {
        const val EXTRA_BOT_COUNT = "extra_bot_count"
        const val EXTRA_POWERUP_FREQUENCY = "extra_powerup_frequency"
        private const val BOOST_UNAVAILABLE_ALPHA = 0.35f
        private const val DEFAULT_BOTS = 100
    }
}
