package com.hyperionsoftware.balls

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hyperionsoftware.balls.databinding.ActivityTutorialBinding

// A rundown of the core mechanics, shown automatically the first time the app ever launches
// (see SplashActivity/TutorialSettings) and reachable afterwards any time from Options - same
// plain title+ScrollView+button shape as AchievementsActivity, just with static content
// instead of dynamic rows. The action button's label/destination changes depending on how
// this screen was reached: onward into the main menu on first launch, or back to Options.
class TutorialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTutorialBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTutorialBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SECTIONS.forEach { (titleResId, bodyResId) ->
            binding.tutorialContainer.addView(sectionView(titleResId, bodyResId))
        }

        if (intent.getBooleanExtra(EXTRA_FIRST_LAUNCH, false)) {
            binding.actionButton.text = getString(R.string.tutorial_start_playing)
            binding.actionButton.setOnClickListener {
                startActivity(Intent(this, MainMenuActivity::class.java))
                finish()
            }
        } else {
            binding.actionButton.text = getString(R.string.tutorial_back)
            binding.actionButton.setOnClickListener { finish() }
        }
    }

    private fun sectionView(titleResId: Int, bodyResId: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
            addView(TextView(this@TutorialActivity).apply {
                text = getString(titleResId)
                setTextColor(getColor(R.color.player_color))
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(this@TutorialActivity).apply {
                text = getString(bodyResId)
                setTextColor(getColor(R.color.hud_text))
                alpha = 0.85f
                textSize = 15f
                setPadding(0, 6, 0, 0)
            })
        }
    }

    companion object {
        const val EXTRA_FIRST_LAUNCH = "extra_first_launch"

        private val SECTIONS = listOf(
            R.string.tutorial_movement_title to R.string.tutorial_movement_body,
            R.string.tutorial_absorb_title to R.string.tutorial_absorb_body,
            R.string.tutorial_boost_title to R.string.tutorial_boost_body,
            R.string.tutorial_powerups_title to R.string.tutorial_powerups_body,
            R.string.tutorial_permanent_title to R.string.tutorial_permanent_body,
            R.string.tutorial_safezone_title to R.string.tutorial_safezone_body,
            R.string.tutorial_finalround_title to R.string.tutorial_finalround_body,
            R.string.tutorial_score_title to R.string.tutorial_score_body
        )
    }
}
