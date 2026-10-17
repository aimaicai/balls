package com.hyperionsoftware.balls

import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.hyperionsoftware.balls.databinding.ActivityMatchSettingsBinding
import com.hyperionsoftware.balls.game.GameConfig
import com.hyperionsoftware.balls.settings.GameSettings
import com.hyperionsoftware.balls.ui.fitContentInsideSystemBars

// The five match-setup controls split off OptionsActivity - hidden behind developer mode (see
// DeveloperSettings), since in practice almost nobody needs to tune these away from their
// defaults, and surfacing them prominently just cluttered the regular Options screen.
class MatchSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMatchSettingsBinding
    private var botCount = GameSettings.DEFAULT_BOT_COUNT
    private var powerUpFrequencyLevel = GameConfig.POWERUP_DEFAULT_FREQUENCY_LEVEL
    private var arenaSize = GameConfig.ArenaSize.NORMAL
    private var botAggressivenessLevel = GameConfig.BOT_AGGRESSIVENESS_DEFAULT_LEVEL
    private var safeZoneShrinkSpeedLevel = GameConfig.SAFE_ZONE_SHRINK_SPEED_DEFAULT_LEVEL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMatchSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fitContentInsideSystemBars(binding.root)

        botCount = GameSettings.getBotCount(this)
        powerUpFrequencyLevel = GameSettings.getPowerUpFrequency(this)
        arenaSize = GameSettings.getArenaSize(this)
        botAggressivenessLevel = GameSettings.getBotAggressiveness(this)
        safeZoneShrinkSpeedLevel = GameSettings.getSafeZoneShrinkSpeed(this)

        // android:min/android:max in the layout already bound each SeekBar to its range,
        // so progress IS the actual value directly (API 26+ semantics).
        binding.botsSeekBar.progress = botCount
        binding.botAggressivenessSeekBar.progress = botAggressivenessLevel
        binding.powerUpSeekBar.progress = powerUpFrequencyLevel
        binding.arenaSizeSeekBar.progress = arenaSize.ordinal
        binding.safeZoneShrinkSpeedSeekBar.progress = safeZoneShrinkSpeedLevel
        updateBotCountLabel()
        updateBotAggressivenessLabel()
        updatePowerUpLabel()
        updateArenaSizeLabel()
        updateSafeZoneShrinkSpeedLabel()

        binding.botsSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                botCount = progress
                updateBotCountLabel()
                GameSettings.setBotCount(this@MatchSettingsActivity, botCount)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        binding.botAggressivenessSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                botAggressivenessLevel = progress
                updateBotAggressivenessLabel()
                GameSettings.setBotAggressiveness(this@MatchSettingsActivity, botAggressivenessLevel)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        binding.powerUpSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                powerUpFrequencyLevel = progress
                updatePowerUpLabel()
                GameSettings.setPowerUpFrequency(this@MatchSettingsActivity, powerUpFrequencyLevel)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        binding.arenaSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                arenaSize = GameConfig.ArenaSize.entries[progress]
                updateArenaSizeLabel()
                GameSettings.setArenaSize(this@MatchSettingsActivity, arenaSize)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        binding.safeZoneShrinkSpeedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                safeZoneShrinkSpeedLevel = progress
                updateSafeZoneShrinkSpeedLabel()
                GameSettings.setSafeZoneShrinkSpeed(this@MatchSettingsActivity, safeZoneShrinkSpeedLevel)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        binding.backButton.setOnClickListener { finish() }
    }

    private fun updateBotCountLabel() {
        binding.botsCountText.text = getString(R.string.menu_bots_label, botCount)
    }

    private fun updateBotAggressivenessLabel() {
        binding.botAggressivenessText.text = getString(R.string.menu_bot_aggressiveness_label, botAggressivenessLevel)
    }

    private fun updatePowerUpLabel() {
        binding.powerUpCountText.text = getString(R.string.menu_powerup_label, powerUpFrequencyLevel)
    }

    private fun updateArenaSizeLabel() {
        val sizeLabelRes = when (arenaSize) {
            GameConfig.ArenaSize.SMALL -> R.string.arena_size_small
            GameConfig.ArenaSize.NORMAL -> R.string.arena_size_normal
            GameConfig.ArenaSize.LARGE -> R.string.arena_size_large
            GameConfig.ArenaSize.HUGE -> R.string.arena_size_huge
        }
        binding.arenaSizeText.text = getString(R.string.menu_arena_label, getString(sizeLabelRes))
    }

    private fun updateSafeZoneShrinkSpeedLabel() {
        binding.safeZoneShrinkSpeedText.text =
            getString(R.string.menu_safe_zone_shrink_speed_label, safeZoneShrinkSpeedLevel)
    }
}
