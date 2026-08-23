package com.hyperionsoftware.balls

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hyperionsoftware.balls.achievements.Achievements
import com.hyperionsoftware.balls.cosmetics.BalloonCord
import com.hyperionsoftware.balls.cosmetics.BalloonSticker
import com.hyperionsoftware.balls.cosmetics.ExhaustStyle
import com.hyperionsoftware.balls.cosmetics.PlayerColor
import com.hyperionsoftware.balls.databinding.ActivityCustomizeBinding
import com.hyperionsoftware.balls.settings.CosmeticsSettings
import kotlin.math.min

// Lets the player pick their own balloon's color, sticker, string color and exhaust style -
// a few of each free from the start, the rest unlocked by achievements already worth chasing
// for their own sake (see PlayerColor/BalloonSticker/BalloonCord/ExhaustStyle). Bots always
// keep their own fixed look, none of this ever affects them.
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
        val selectedColor = CosmeticsSettings.getSelectedColor(this)
        PlayerColor.entries.chunked(COLUMNS).forEach { rowColors ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowColors.forEach { color ->
                row.addView(
                    swatchView(color, color == selectedColor),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                )
            }
            binding.colorGrid.addView(row)
        }

        binding.stickerGrid.removeAllViews()
        val selectedSticker = CosmeticsSettings.getSelectedSticker(this)
        BalloonSticker.entries.chunked(COLUMNS).forEach { rowStickers ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowStickers.forEach { sticker ->
                row.addView(
                    stickerSwatchView(sticker, sticker == selectedSticker),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                )
            }
            binding.stickerGrid.addView(row)
        }

        binding.cordGrid.removeAllViews()
        val selectedCord = CosmeticsSettings.getSelectedCord(this)
        BalloonCord.entries.chunked(COLUMNS).forEach { rowCords ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowCords.forEach { cord ->
                row.addView(
                    cordSwatchView(cord, cord == selectedCord),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                )
            }
            binding.cordGrid.addView(row)
        }

        binding.exhaustGrid.removeAllViews()
        val selectedExhaustStyle = CosmeticsSettings.getSelectedExhaustStyle(this)
        ExhaustStyle.entries.chunked(COLUMNS).forEach { rowStyles ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowStyles.forEach { style ->
                row.addView(
                    exhaustSwatchView(style, style == selectedExhaustStyle),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                )
            }
            binding.exhaustGrid.addView(row)
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

    private fun stickerSwatchView(sticker: BalloonSticker, isSelected: Boolean): View {
        val requiredAchievement = sticker.requiredAchievement
        val unlocked = requiredAchievement == null || Achievements.isUnlocked(this, requiredAchievement)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(12))
        }

        container.addView(object : View(this) {
            // A plain dark disc standing in for the balloon (the sticker's own ink color is
            // fixed, not tied to any particular balloon color) with the sticker itself drawn
            // via the exact same BalloonSticker.drawInto used on the actual balloon in-game,
            // so the preview always matches gameplay exactly.
            private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#37474F") }
            private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(4).toFloat()
                color = Color.WHITE
            }
            private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F5F5F5") }
            private val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#212121") }

            override fun onDraw(canvas: Canvas) {
                val cx = width / 2f
                val cy = height / 2f
                val discRadius = min(width, height) / 2f - ringPaint.strokeWidth / 2f
                canvas.drawCircle(cx, cy, discRadius, discPaint)
                if (isSelected) canvas.drawCircle(cx, cy, discRadius, ringPaint)
                canvas.save()
                canvas.translate(cx, cy)
                sticker.drawInto(canvas, inkPaint, detailPaint, discRadius * 0.55f)
                canvas.restore()
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
            alpha = if (unlocked) 1f else 0.3f
        })

        container.addView(TextView(this).apply {
            text = getString(sticker.labelResId)
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
                CosmeticsSettings.setSelectedSticker(this, sticker)
                refreshGrid()
            }
        }

        return container
    }

    private fun cordSwatchView(cord: BalloonCord, isSelected: Boolean): View {
        val requiredAchievement = cord.requiredAchievement
        val unlocked = requiredAchievement == null || Achievements.isUnlocked(this, requiredAchievement)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(12))
        }

        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(cord.colorInt)
                if (isSelected) setStroke(dp(4), Color.WHITE)
            }
            alpha = if (unlocked) 1f else 0.3f
        })

        container.addView(TextView(this).apply {
            text = getString(cord.labelResId)
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
                CosmeticsSettings.setSelectedCord(this, cord)
                refreshGrid()
            }
        }

        return container
    }

    private fun exhaustSwatchView(exhaustStyle: ExhaustStyle, isSelected: Boolean): View {
        val requiredAchievement = exhaustStyle.requiredAchievement
        val unlocked = requiredAchievement == null || Achievements.isUnlocked(this, requiredAchievement)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(12))
        }

        container.addView(object : View(this) {
            // A little trio of puffs standing in for a trail, drawn via the exact same
            // ExhaustStyle.drawPuff used on the actual in-game exhaust, so the preview always
            // matches gameplay exactly.
            private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#37474F") }
            private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(4).toFloat()
                color = Color.WHITE
            }
            private val puffPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#B3E5FC") }

            override fun onDraw(canvas: Canvas) {
                val cx = width / 2f
                val cy = height / 2f
                val discRadius = min(width, height) / 2f - ringPaint.strokeWidth / 2f
                canvas.drawCircle(cx, cy, discRadius, discPaint)
                if (isSelected) canvas.drawCircle(cx, cy, discRadius, ringPaint)
                exhaustStyle.drawPuff(canvas, puffPaint, cx - discRadius * 0.35f, cy + discRadius * 0.25f, discRadius * 0.22f)
                exhaustStyle.drawPuff(canvas, puffPaint, cx, cy, discRadius * 0.3f)
                exhaustStyle.drawPuff(canvas, puffPaint, cx + discRadius * 0.4f, cy - discRadius * 0.28f, discRadius * 0.38f)
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
            alpha = if (unlocked) 1f else 0.3f
        })

        container.addView(TextView(this).apply {
            text = getString(exhaustStyle.labelResId)
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
                CosmeticsSettings.setSelectedExhaustStyle(this, exhaustStyle)
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
