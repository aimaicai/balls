package com.hyperionsoftware.balls

import android.content.Intent
import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.hyperionsoftware.balls.databinding.ActivityRaceSetupBinding
import com.hyperionsoftware.balls.race.RaceConfig
import com.hyperionsoftware.balls.race.RaceTrack
import com.hyperionsoftware.balls.settings.RaceSettings

// Grand Prix's match setup screen - track, lap count and opponent count, reached from the main
// menu's "Grand Prix" button (see MainMenuActivity) as the entry point into RaceActivity. A
// separate screen rather than folding these into OptionsActivity, since match setup there is
// specific to classic mode's own settings (arena size, safe zone speed, etc.) that don't apply
// here at all.
class RaceSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRaceSetupBinding
    private var track = RaceTrack.OVAL
    private var laps = RaceConfig.DEFAULT_LAPS
    private var botCount = RaceConfig.DEFAULT_BOT_COUNT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRaceSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        track = RaceSettings.getTrack(this)
        laps = RaceSettings.getLaps(this).coerceIn(RaceConfig.MIN_LAPS, RaceConfig.MAX_LAPS)
        botCount = RaceSettings.getBotCount(this).coerceIn(RaceConfig.MIN_BOT_COUNT, RaceConfig.MAX_BOT_COUNT)

        binding.lapsSeekBar.min = RaceConfig.MIN_LAPS
        binding.lapsSeekBar.max = RaceConfig.MAX_LAPS
        binding.lapsSeekBar.progress = laps

        binding.botsSeekBar.min = RaceConfig.MIN_BOT_COUNT
        binding.botsSeekBar.max = RaceConfig.MAX_BOT_COUNT
        binding.botsSeekBar.progress = botCount

        updateTrackLabel()
        updateLapsLabel()
        updateBotsLabel()

        binding.trackButton.setOnClickListener {
            val tracks = RaceTrack.values()
            track = tracks[(track.ordinal + 1) % tracks.size]
            RaceSettings.setTrack(this, track)
            updateTrackLabel()
        }

        binding.lapsSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                laps = progress
                updateLapsLabel()
                RaceSettings.setLaps(this@RaceSetupActivity, laps)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        binding.botsSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                botCount = progress
                updateBotsLabel()
                RaceSettings.setBotCount(this@RaceSetupActivity, botCount)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        binding.startRaceButton.setOnClickListener {
            startActivity(
                Intent(this, RaceActivity::class.java)
                    .putExtra(RaceActivity.EXTRA_BOT_COUNT, botCount)
                    .putExtra(RaceActivity.EXTRA_TRACK, track.name)
                    .putExtra(RaceActivity.EXTRA_LAPS, laps)
            )
        }
        binding.raceSetupBackButton.setOnClickListener { finish() }
    }

    private fun updateTrackLabel() {
        val trackName = when (track) {
            RaceTrack.OVAL -> getString(R.string.race_track_oval)
            RaceTrack.FIGURE_EIGHT -> getString(R.string.race_track_figure_eight)
        }
        binding.trackButton.text = getString(R.string.race_setup_track_label, trackName)
    }

    private fun updateLapsLabel() {
        binding.lapsText.text = getString(R.string.race_setup_laps_label, laps)
    }

    private fun updateBotsLabel() {
        binding.botsText.text = getString(R.string.race_setup_bots_label, botCount)
    }
}
