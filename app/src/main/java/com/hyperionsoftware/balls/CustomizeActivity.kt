package com.hyperionsoftware.balls

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hyperionsoftware.balls.achievements.Achievements
import com.hyperionsoftware.balls.cosmetics.PlayerColor
import com.hyperionsoftware.balls.databinding.ActivityCustomizeBinding
import com.hyperionsoftware.balls.settings.CosmeticsSettings

// Lets the player pick their own balloon's color - a few free from the start, the rest
// unlocked by achievements already worth chasing for their own sake (see PlayerColor). Bots
// always keep their own fixed palette, this only ever affects the player's own balloon.
class CustomizeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomizeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomizeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }
        refreshGrid()
    }

    // Plain rows of 3 equal-width columns, built the same way AchievementsActivity builds
    // its rows - simpler and more predictable than android.widget.GridLayout's column-
    // weight quirks for something this small.
    private fun refreshGrid() {
        binding.colorGrid.removeAllViews()
        val selected = CosmeticsSettings.getSelectedColor(this)
        PlayerColor.entries.chunked(COLUMNS).forEach { rowColors ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowColors.forEach { color ->
                row.addView(
                    swatchView(color, color == selected),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                )
            }
            binding.colorGrid.addView(row)
        }
    }

    private fun swatchView(color: PlayerColor, isSelected: Boolean): View {
        val requiredAchievement = color.requiredAchievement
        val unlocked = requiredAchievement == null || Achievements.isUnlocked(this, requiredAchievement)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(12))
        }

        container.addView(View(this).apply {
            // A plain colored circle drawn with a GradientDrawable rather than a bitmap
            // asset - the whole app is Canvas/drawable-based, no image assets at all.
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color.colorInt)
                if (isSelected) setStroke(dp(4), Color.WHITE)
            }
            alpha = if (unlocked) 1f else 0.3f
        })

        container.addView(TextView(this).apply {
            text = getString(color.labelResId)
            setTextColor(getColor(R.color.hud_text))
            textSize = 13f
            gravity = Gravity.CENTER
            alpha = if (unlocked) 0.9f else 0.5f
            setPadding(0, dp(6), 0, 0)
        })

        if (!unlocked && requiredAchievement != null) {
            container.addView(TextView(this).apply {
                text = getString(R.string.customize_locked_hint, getString(requiredAchievement.titleResId))
                setTextColor(getColor(R.color.hud_text))
                textSize = 10f
                gravity = Gravity.CENTER
                alpha = 0.55f
                setPadding(0, dp(2), 0, 0)
            })
        }

        if (unlocked) {
            container.isClickable = true
            container.isFocusable = true
            container.setOnClickListener {
                CosmeticsSettings.setSelectedColor(this, color)
                refreshGrid()
            }
        }

        return container
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val COLUMNS = 3
    }
}
