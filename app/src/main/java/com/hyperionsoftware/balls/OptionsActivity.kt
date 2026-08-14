package com.hyperionsoftware.balls

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.hyperionsoftware.balls.audio.BackgroundMusicPlayer
import com.hyperionsoftware.balls.audio.MusicSettings
import com.hyperionsoftware.balls.audio.MusicTrack
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
    private val musicPlayer = BackgroundMusicPlayer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOptionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        botCount = GameSettings.getBotCount(this)
        powerUpFrequencyLevel = GameSettings.getPowerUpFrequency(this)
        arenaSize = GameSettings.getArenaSize(this)

        // android:min/android:max in the layout already bound each SeekBar to its range,
        // so progress IS the actual value directly (API 26+ semantics).
        binding.botsSeekBar.progress = botCount
        binding.powerUpSeekBar.progress = powerUpFrequencyLevel
        binding.arenaSizeSeekBar.progress = arenaSize.ordinal
        updateBotCountLabel()
        updatePowerUpLabel()
        updateArenaSizeLabel()

        binding.botsSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                botCount = progress
                updateBotCountLabel()
                GameSettings.setBotCount(this@OptionsActivity, botCount)
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

        binding.musicSwitch.isChecked = MusicSettings.isEnabled(this)
        binding.musicSwitch.setOnCheckedChangeListener { _, isEnabled ->
            MusicSettings.setEnabled(this, isEnabled)
            if (isEnabled) musicPlayer.start(MusicSettings.getSelectedTrack(this)) else musicPlayer.stop()
        }

        val tracks = MusicTrack.entries.toList()
        binding.musicTrackSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            tracks.map { getString(it.labelResId) }
        )
        binding.musicTrackSpinner.setSelection(tracks.indexOf(MusicSettings.getSelectedTrack(this)))
        binding.musicTrackSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val track = tracks[position]
                MusicSettings.setSelectedTrack(this@OptionsActivity, track)
                if (MusicSettings.isEnabled(this@OptionsActivity)) musicPlayer.start(track)
            }

            override fun onNothingSelected(parent: AdapterView<*>) = Unit
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
}
