package com.hyperionsoftware.balls

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hyperionsoftware.balls.databinding.ActivityHighScoresBinding
import com.hyperionsoftware.balls.game.GameConfig
import com.hyperionsoftware.balls.score.HighScores

// Old-school arcade cabinet style on purpose: just rank, three-letter initials and score,
// monospace and phosphor-green on black - no dates, no match stats, nothing else. Shown
// directly at the end of a match (see GameActivity.onGameOver) instead of behind a button,
// with that match's result and a "play again" shortcut when arriving that way.
class HighScoresActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHighScoresBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHighScoresBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (intent.hasExtra(EXTRA_MATCH_WON)) {
            showMatchResult()
        }

        binding.backButton.setOnClickListener { finish() }

        val entries = HighScores.loadAll(this)
        if (entries.isEmpty()) {
            binding.scoresContainer.addView(arcadeText(getString(R.string.high_scores_empty)))
            return
        }

        entries.forEachIndexed { index, entry ->
            val rank = (index + 1).toString().padStart(2, '0')
            val initials = entry.initials.padEnd(3, ' ')
            val score = entry.score.toString().padStart(6, '0')
            binding.scoresContainer.addView(arcadeText("$rank  $initials  $score"))
        }
    }

    private fun showMatchResult() {
        val won = intent.getBooleanExtra(EXTRA_MATCH_WON, false)
        val score = intent.getIntExtra(EXTRA_MATCH_SCORE, 0)
        binding.matchResultBanner.visibility = View.VISIBLE
        binding.matchResultBanner.text = getString(
            if (won) R.string.high_scores_match_won else R.string.high_scores_match_lost,
            score
        )

        binding.playAgainButton.visibility = View.VISIBLE
        binding.playAgainButton.setOnClickListener {
            startActivity(
                Intent(this, GameActivity::class.java)
                    .putExtra(GameActivity.EXTRA_BOT_COUNT, intent.getIntExtra(GameActivity.EXTRA_BOT_COUNT, DEFAULT_BOTS))
                    .putExtra(
                        GameActivity.EXTRA_POWERUP_FREQUENCY,
                        intent.getIntExtra(GameActivity.EXTRA_POWERUP_FREQUENCY, GameConfig.POWERUP_DEFAULT_FREQUENCY_LEVEL)
                    )
                    .putExtra(GameActivity.EXTRA_ARENA_SIZE, intent.getStringExtra(GameActivity.EXTRA_ARENA_SIZE))
                    .putExtra(
                        GameActivity.EXTRA_SKIP_TO_FINAL_ROUND,
                        intent.getBooleanExtra(GameActivity.EXTRA_SKIP_TO_FINAL_ROUND, false)
                    )
            )
            finish()
        }
    }

    private fun arcadeText(line: String) = TextView(this).apply {
        text = line
        setTextColor(getColor(R.color.arcade_green))
        typeface = Typeface.MONOSPACE
        textSize = 22f
        letterSpacing = 0.1f
        gravity = Gravity.CENTER
        setPadding(0, 10, 0, 10)
    }

    companion object {
        const val EXTRA_MATCH_WON = "extra_match_won"
        const val EXTRA_MATCH_SCORE = "extra_match_score"
        private const val DEFAULT_BOTS = 100
    }
}
