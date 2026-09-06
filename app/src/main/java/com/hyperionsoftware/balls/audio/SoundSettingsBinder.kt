package com.hyperionsoftware.balls.audio

import android.content.Context
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import com.hyperionsoftware.balls.databinding.SoundSettingsPanelBinding

// Wires the shared sound-settings panel (see sound_settings_panel.xml) up to its backing
// settings objects - used identically by OptionsActivity and GameActivity's pause dialog, so
// the two can never drift out of sync with each other, and a player only ever needs to learn
// these controls once.
object SoundSettingsBinder {
    fun bind(
        context: Context,
        binding: SoundSettingsPanelBinding,
        musicPlayer: BackgroundMusicPlayer,
        sfxPlayer: SoundEffectPlayer
    ) {
        binding.musicSwitch.isChecked = MusicSettings.isEnabled(context)
        binding.musicSwitch.setOnCheckedChangeListener { _, isEnabled ->
            MusicSettings.setEnabled(context, isEnabled)
            if (isEnabled) musicPlayer.start(MusicSettings.getSelectedTrack(context)) else musicPlayer.stop()
        }

        binding.musicVolumeSeekBar.progress = MusicSettings.getVolume(context)
        binding.musicVolumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                MusicSettings.setVolume(context, progress)
                // Live feedback while dragging, rather than only on the next start() - the
                // slider should feel connected to what's actually playing right now.
                musicPlayer.applyVolumeSetting()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        val tracks = MusicTrack.entries.toList()
        binding.musicTrackSpinner.adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_dropdown_item,
            tracks.map { context.getString(it.labelResId) }
        )
        binding.musicTrackSpinner.setSelection(tracks.indexOf(MusicSettings.getSelectedTrack(context)))
        binding.musicTrackSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val track = tracks[position]
                MusicSettings.setSelectedTrack(context, track)
                if (MusicSettings.isEnabled(context)) musicPlayer.start(track)
            }

            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }

        binding.sfxSwitch.isChecked = SfxSettings.isEnabled(context)
        binding.sfxSwitch.setOnCheckedChangeListener { _, isEnabled -> SfxSettings.setEnabled(context, isEnabled) }

        binding.sfxVolumeSeekBar.progress = SfxSettings.getVolume(context)
        binding.sfxVolumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                SfxSettings.setVolume(context, progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            // A quick sample once the finger lifts (not on every drag tick, which would
            // overlap itself constantly) so the new level is actually audible.
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                sfxPlayer.play(SoundEffectPlayer.Effect.POWERUP)
            }
        })

        binding.vibrationSwitch.isChecked = VibrationSettings.isEnabled(context)
        binding.vibrationSwitch.setOnCheckedChangeListener { _, isEnabled ->
            VibrationSettings.setEnabled(context, isEnabled)
        }
    }
}
