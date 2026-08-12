package com.hyperionsoftware.balls

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hyperionsoftware.balls.databinding.ActivityHighScoresBinding
import com.hyperionsoftware.balls.score.HighScores

// Old-school arcade cabinet style on purpose: just rank, three-letter initials and score,
// monospace and phosphor-green on black - no dates, no match stats, nothing else.
class HighScoresActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHighScoresBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHighScoresBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

    private fun arcadeText(line: String) = TextView(this).apply {
        text = line
        setTextColor(getColor(R.color.arcade_green))
        typeface = Typeface.MONOSPACE
        textSize = 22f
        letterSpacing = 0.1f
        gravity = Gravity.CENTER
        setPadding(0, 10, 0, 10)
    }
}
