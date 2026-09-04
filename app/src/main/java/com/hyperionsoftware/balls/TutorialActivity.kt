package com.hyperionsoftware.balls

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hyperionsoftware.balls.databinding.ActivityTutorialBinding
import com.hyperionsoftware.balls.game.BotPersonality

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
        // A playtesting aid (see BotPersonality's own doc comment): bot color always means
        // the same personality, so this legend lets a player learn to read the arena at a
        // glance. Easy to drop later - just this call plus the personality*/colorSwatches
        // methods below.
        binding.tutorialContainer.addView(personalityLegendSection())

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

    // Same title+body shape as sectionView, with one colored row per BotPersonality appended
    // underneath - the row's swatches come straight from BotPersonality.colorsFor, so this
    // can never list a color the game doesn't actually use for that personality.
    private fun personalityLegendSection(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
            addView(TextView(this@TutorialActivity).apply {
                text = getString(R.string.tutorial_personality_title)
                setTextColor(getColor(R.color.player_color))
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(this@TutorialActivity).apply {
                text = getString(R.string.tutorial_personality_intro)
                setTextColor(getColor(R.color.hud_text))
                alpha = 0.85f
                textSize = 15f
                setPadding(0, 6, 0, 8)
            })
            BotPersonality.entries.forEach { personality -> addView(personalityRow(personality)) }
        }
    }

    private fun personalityRow(personality: BotPersonality): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
            addView(colorSwatches(BotPersonality.colorsFor(personality)))
            addView(LinearLayout(this@TutorialActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
                addView(TextView(this@TutorialActivity).apply {
                    text = getString(personalityLabelResId(personality))
                    setTextColor(getColor(R.color.hud_text))
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                })
                addView(TextView(this@TutorialActivity).apply {
                    text = getString(personalityDescriptionResId(personality))
                    setTextColor(getColor(R.color.hud_text))
                    alpha = 0.75f
                    textSize = 13f
                    setPadding(0, 2, 0, 0)
                })
            })
        }
    }

    private fun colorSwatches(colors: List<Int>): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            colors.forEach { color ->
                addView(View(this@TutorialActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(4) }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(color)
                    }
                })
            }
        }
    }

    private fun personalityLabelResId(personality: BotPersonality): Int = when (personality) {
        BotPersonality.BALANCED -> R.string.bot_personality_balanced_name
        BotPersonality.HUNTER -> R.string.bot_personality_hunter_name
        BotPersonality.CAUTIOUS -> R.string.bot_personality_cautious_name
        BotPersonality.COLLECTOR -> R.string.bot_personality_collector_name
    }

    private fun personalityDescriptionResId(personality: BotPersonality): Int = when (personality) {
        BotPersonality.BALANCED -> R.string.bot_personality_balanced_desc
        BotPersonality.HUNTER -> R.string.bot_personality_hunter_desc
        BotPersonality.CAUTIOUS -> R.string.bot_personality_cautious_desc
        BotPersonality.COLLECTOR -> R.string.bot_personality_collector_desc
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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
