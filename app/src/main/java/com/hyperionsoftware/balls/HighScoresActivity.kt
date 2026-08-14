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
// monospace on the app's own dark/amber palette - no dates, no match stats, nothing else.
// Shown directly at the end of a match (see GameActivity.onGameOver) instead of behind a
// button, with that match's result and a "play again" shortcut when arriving that way.
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

        val highlightTimestamp = if (intent.hasExtra(EXTRA_HIGHLIGHT_TIMESTAMP)) {
            intent.getLongExtra(EXTRA_HIGHLIGHT_TIMESTAMP, -1L)
        } else {
            null
        }

        entries.forEachIndexed { index, entry ->
            val rank = (index + 1).toString().padStart(2, '0')
            val initials = entry.initials.padEnd(3, ' ')
            val score = entry.score.toString().padStart(6, '0')
            val isHighlighted = highlightTimestamp != null && entry.timestampMillis == highlightTimestamp
            binding.scoresContainer.addView(arcadeText("$rank  $initials  $score", isHighlighted))

            // Rank #1 landing on the entry this match just recorded is a brand-new high
            // score - worth more than just the usual row highlight.
            if (isHighlighted && index == 0) {
                celebrateNewRecord()
            }
        }
    }

    // Only ever called once per screen (index == 0 can match at most one row): a gold
    // banner plus a burst of fireworks/confetti to make a new #1 feel like an event.
    private fun celebrateNewRecord() {
        binding.newRecordBanner.visibility = View.VISIBLE
        binding.fireworksView.visibility = View.VISIBLE
        binding.fireworksView.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.fireworksView.stop()
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
                    .putExtra(
                        GameActivity.EXTRA_BOT_AGGRESSIVENESS,
                        intent.getIntExtra(GameActivity.EXTRA_BOT_AGGRESSIVENESS, GameConfig.BOT_AGGRESSIVENESS_DEFAULT_LEVEL)
                    )
            )
            finish()
        }
    }

    // highlighted marks the entry this match just recorded - a different color and weight
    // so the player can find where they landed at a glance instead of scanning ranks.
    private fun arcadeText(line: String, highlighted: Boolean = false) = TextView(this).apply {
        text = line
        setTextColor(getColor(if (highlighted) R.color.player_color else R.color.accent))
        typeface = Typeface.MONOSPACE
        textSize = 22f
        letterSpacing = 0.1f
        gravity = Gravity.CENTER
        setPadding(0, 10, 0, 10)
        if (highlighted) setTypeface(typeface, Typeface.BOLD)
    }

    companion object {
        const val EXTRA_MATCH_WON = "extra_match_won"
        const val EXTRA_MATCH_SCORE = "extra_match_score"
        const val EXTRA_HIGHLIGHT_TIMESTAMP = "extra_highlight_timestamp"
        private const val DEFAULT_BOTS = 100
    }
}
