package com.hyperionsoftware.balls

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.hyperionsoftware.balls.audio.BackgroundMusicPlayer
import com.hyperionsoftware.balls.audio.MusicSettings
import com.hyperionsoftware.balls.audio.SoundEffectPlayer
import com.hyperionsoftware.balls.audio.SoundSettingsBinder
import com.hyperionsoftware.balls.databinding.ActivityOptionsBinding
import com.hyperionsoftware.balls.settings.DeveloperSettings
import com.hyperionsoftware.balls.ui.fitContentInsideSystemBars

// Audio settings plus secondary destinations, split off the main menu so that screen can stay
// focused on its one primary action (Play) with everything else demoted to a secondary
// "Opzioni" destination instead of competing with it for attention. Match setup (bot count,
// power-up frequency, etc.) moved to MatchSettingsActivity, which only developer mode exposes.
class OptionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOptionsBinding
    private val musicPlayer = BackgroundMusicPlayer(this)
    private val sfxPlayer = SoundEffectPlayer(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOptionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fitContentInsideSystemBars(binding.root)

        SoundSettingsBinder.bind(this, binding.soundSettingsPanel, musicPlayer, sfxPlayer)

        binding.matchSettingsButton.setOnClickListener {
            startActivity(Intent(this, MatchSettingsActivity::class.java))
        }
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
        binding.matchSettingsButton.visibility =
            if (DeveloperSettings.isEnabled(this)) View.VISIBLE else View.GONE
    }

    override fun onPause() {
        super.onPause()
        musicPlayer.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        sfxPlayer.release()
    }
}
