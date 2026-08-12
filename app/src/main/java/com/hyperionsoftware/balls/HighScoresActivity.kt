package com.hyperionsoftware.balls

import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hyperionsoftware.balls.databinding.ActivityHighScoresBinding
import com.hyperionsoftware.balls.score.HighScores
import java.text.SimpleDateFormat
import java.util.Locale

class HighScoresActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHighScoresBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHighScoresBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }

        val entries = HighScores.loadAll(this)
        if (entries.isEmpty()) {
            binding.scoresContainer.addView(
                TextView(this).apply {
                    text = getString(R.string.high_scores_empty)
                    setTextColor(getColor(R.color.hud_text))
                    textSize = 16f
                    gravity = Gravity.CENTER
                }
            )
            return
        }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        entries.forEachIndexed { index, entry ->
            val resultLabel = if (entry.playerWon) {
                getString(R.string.high_scores_won)
            } else {
                getString(R.string.high_scores_lost)
            }
            val timeText = String.format(
                Locale.getDefault(),
                "%02d:%02d",
                entry.elapsedSeconds / 60,
                entry.elapsedSeconds % 60
            )
            val text = getString(
                R.string.high_scores_entry,
                index + 1,
                entry.score,
                "$resultLabel · ${dateFormat.format(entry.timestampMillis)}",
                entry.finalRadius,
                entry.opponentsAbsorbed,
                timeText
            )
            binding.scoresContainer.addView(
                TextView(this).apply {
                    this.text = text
                    setTextColor(getColor(R.color.hud_text))
                    textSize = 16f
                    setPadding(0, 16, 0, 16)
                }
            )
        }
    }
}
