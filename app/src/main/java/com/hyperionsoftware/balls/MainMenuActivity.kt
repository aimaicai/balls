package com.hyperionsoftware.balls

import android.content.Intent
import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.hyperionsoftware.balls.audio.BackgroundMusicPlayer
import com.hyperionsoftware.balls.audio.MusicSettings
import com.hyperionsoftware.balls.databinding.ActivityMainMenuBinding
import com.hyperionsoftware.balls.game.GameConfig

class MainMenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainMenuBinding
    private var botCount = DEFAULT_BOTS
    private var powerUpFrequencyLevel = GameConfig.POWERUP_DEFAULT_FREQUENCY_LEVEL
    private var arenaSize = GameConfig.ArenaSize.NORMAL
    private val musicPlayer = BackgroundMusicPlayer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // android:min/android:max in the layout already bound each SeekBar to its range,
        // so progress IS the actual value directly (API 26+ semantics).
        binding.botsSeekBar.progress = botCount
        binding.powerUpSeekBar.progress = powerUpFrequencyLevel
        binding.arenaSizeSeekBar.progress = arenaSize.ordinal
        updateBotCountLabel()
        updatePowerUpLabel()
        updateArenaSizeLabel()

        binding.musicSwitch.isChecked = MusicSettings.isEnabled(this)
        binding.musicSwitch.setOnCheckedChangeListener { _, isEnabled ->
            MusicSettings.setEnabled(this, isEnabled)
            if (isEnabled) musicPlayer.start() else musicPlayer.stop()
        }

        binding.botsSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                botCount = progress
                updateBotCountLabel()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        binding.powerUpSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                powerUpFrequencyLevel = progress
                updatePowerUpLabel()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        binding.arenaSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                arenaSize = GameConfig.ArenaSize.entries[progress]
                updateArenaSizeLabel()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        binding.playButton.setOnClickListener {
            startActivity(
                Intent(this, GameActivity::class.java)
                    .putExtra(GameActivity.EXTRA_BOT_COUNT, botCount)
                    .putExtra(GameActivity.EXTRA_POWERUP_FREQUENCY, powerUpFrequencyLevel)
                    .putExtra(GameActivity.EXTRA_ARENA_SIZE, arenaSize.name)
            )
        }
        binding.testFinaleButton.setOnClickListener {
            startActivity(
                Intent(this, GameActivity::class.java)
                    .putExtra(GameActivity.EXTRA_BOT_COUNT, botCount)
                    .putExtra(GameActivity.EXTRA_POWERUP_FREQUENCY, powerUpFrequencyLevel)
                    .putExtra(GameActivity.EXTRA_ARENA_SIZE, arenaSize.name)
                    .putExtra(GameActivity.EXTRA_SKIP_TO_FINAL_ROUND, true)
            )
        }
        binding.exitButton.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        if (MusicSettings.isEnabled(this)) musicPlayer.start()
    }

    override fun onPause() {
        super.onPause()
        musicPlayer.stop()
    }

    private fun updateBotCountLabel() {
        binding.botsCountText.text = getString(R.string.menu_bots_label, botCount)
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

    companion object {
        private const val DEFAULT_BOTS = 100
    }
}
