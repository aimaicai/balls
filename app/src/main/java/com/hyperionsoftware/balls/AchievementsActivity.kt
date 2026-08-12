package com.hyperionsoftware.balls

import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hyperionsoftware.balls.achievements.Achievement
import com.hyperionsoftware.balls.achievements.Achievements
import com.hyperionsoftware.balls.databinding.ActivityAchievementsBinding

// Lists every achievement, locked or not - descriptions stay hidden ("???") until unlocked so
// they read as things to discover through play rather than a checklist handed out up front.
class AchievementsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAchievementsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAchievementsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }

        val entries = Achievement.entries
        binding.progressText.text = getString(
            R.string.achievements_progress,
            Achievements.unlockedCount(this),
            entries.size
        )

        entries.forEach { achievement ->
            binding.achievementsContainer.addView(achievementRow(achievement))
        }
    }

    private fun achievementRow(achievement: Achievement): LinearLayout {
        val unlocked = Achievements.isUnlocked(this, achievement)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
            addView(TextView(this@AchievementsActivity).apply {
                text = getString(achievement.titleResId)
                setTextColor(getColor(if (unlocked) R.color.player_color else R.color.hud_text))
                alpha = if (unlocked) 1f else 0.5f
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(this@AchievementsActivity).apply {
                text = if (unlocked) {
                    getString(achievement.descriptionResId)
                } else {
                    getString(R.string.achievement_locked_desc)
                }
                setTextColor(getColor(R.color.hud_text))
                alpha = if (unlocked) 0.85f else 0.4f
                textSize = 15f
            })
        }
    }
}
