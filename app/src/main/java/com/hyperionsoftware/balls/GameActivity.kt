package com.hyperionsoftware.balls

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.hyperionsoftware.balls.databinding.ActivityGameBinding
import com.hyperionsoftware.balls.ui.GameView

class GameActivity : AppCompatActivity(), GameView.Callback {

    private lateinit var binding: ActivityGameBinding
    private var botCount = 6

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        botCount = intent.getIntExtra(EXTRA_BOT_COUNT, 6)

        binding.joystickView.listener = binding.gameView
        binding.gameView.callback = this
        binding.pauseButton.setOnClickListener { showPauseDialog() }

        binding.gameView.startGame(botCount)
    }

    override fun onResume() {
        super.onResume()
        binding.gameView.resumeGame()
    }

    override fun onPause() {
        super.onPause()
        binding.gameView.pauseGame()
    }

    private fun showPauseDialog() {
        binding.gameView.pauseGame()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.pause_title))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.pause_resume)) { _, _ -> binding.gameView.resumeGame() }
            .setNeutralButton(getString(R.string.pause_restart)) { _, _ -> binding.gameView.restart(botCount) }
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
            .setPositiveButton(getString(R.string.game_over_restart)) { _, _ -> binding.gameView.restart(botCount) }
            .setNegativeButton(getString(R.string.game_over_menu)) { _, _ -> goToMenu() }
            .show()
    }

    private fun goToMenu() {
        startActivity(Intent(this, MainMenuActivity::class.java))
        finish()
    }

    companion object {
        const val EXTRA_BOT_COUNT = "extra_bot_count"
    }
}
