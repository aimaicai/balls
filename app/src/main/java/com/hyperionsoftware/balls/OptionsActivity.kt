package com.hyperionsoftware.balls

import android.content.Intent
import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.hyperionsoftware.balls.audio.BackgroundMusicPlayer
import com.hyperionsoftware.balls.audio.MusicSettings
import com.hyperionsoftware.balls.audio.SoundEffectPlayer
import com.hyperionsoftware.balls.audio.SoundSettingsBinder
import com.hyperionsoftware.balls.databinding.ActivityOptionsBinding
import com.hyperionsoftware.balls.game.GameConfig
import com.hyperionsoftware.balls.settings.GameSettings

// Match setup and audio settings, split off the main menu so that screen can stay focused
// on its one primary action (Play) with everything else demoted to a secondary "Opzioni"
// destination instead of competing with it for attention.
class OptionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOptionsBinding
    private var botCount = GameSettings.DEFAULT_BOT_COUNT
    private var powerUpFrequencyLevel = GameConfig.POWERUP_DEFAULT_FREQUENCY_LEVEL
    private var arenaSize = GameConfig.ArenaSize.NORMAL
    private var botAggressivenessLevel = GameConfig.BOT_AGGRESSIVENESS_DEFAULT_LEVEL
    private var safeZoneShrinkSpeedLevel = GameConfig.SAFE_ZONE_SHRINK_SPEED_DEFAULT_LEVEL
    private val musicPlayer = BackgroundMusicPlayer(this)
    private val sfxPlayer = SoundEffectPlayer(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOptionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
                GameSettings.setBotCount(this@OptionsActivity, botCount)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        binding.botAggressivenessSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                botAggressivenessLevel = progress
                updateBotAggressivenessLabel()
                GameSettings.setBotAggressiveness(this@OptionsActivity, botAggressivenessLevel)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        binding.powerUpSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                powerUpFrequencyLevel = progress
                updatePowerUpLabel()
                GameSettings.setPowerUpFrequency(this@OptionsActivity, powerUpFrequencyLevel)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        binding.arenaSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                arenaSize = GameConfig.ArenaSize.entries[progress]
                updateArenaSizeLabel()
                GameSettings.setArenaSize(this@OptionsActivity, arenaSize)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        binding.safeZoneShrinkSpeedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                safeZoneShrinkSpeedLevel = progress
                updateSafeZoneShrinkSpeedLabel()
                GameSettings.setSafeZoneShrinkSpeed(this@OptionsActivity, safeZoneShrinkSpeedLevel)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        SoundSettingsBinder.bind(this, binding.soundSettingsPanel, musicPlayer, sfxPlayer)

        binding.customizeButton.setOnClickListener {
            startActivity(Intent(this, CustomizeActivity::class.java))
        }
        binding.tutorialButton.setOnClickListener {
            startActivity(Intent(this, TutorialActivity::class.java))
        }
        binding.backButton.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        if (MusicSettings.isEnabled(this)) musicPlayer.start(MusicSettings.getSelectedTrack(this))
    }

    override fun onPause() {
        super.onPause()
        musicPlayer.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        sfxPlayer.release()
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
