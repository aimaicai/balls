package com.hyperionsoftware.balls

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.hyperionsoftware.balls.audio.BackgroundMusicPlayer
import com.hyperionsoftware.balls.audio.MusicSettings
import com.hyperionsoftware.balls.audio.SoundEffectPlayer
import com.hyperionsoftware.balls.audio.SoundSettingsBinder
import com.hyperionsoftware.balls.databinding.ActivityRaceBinding
import com.hyperionsoftware.balls.databinding.SoundSettingsPanelBinding
import com.hyperionsoftware.balls.game.PowerUpType
import com.hyperionsoftware.balls.race.RaceConfig
import com.hyperionsoftware.balls.race.RaceEndReason
import com.hyperionsoftware.balls.race.RaceTrack
import com.hyperionsoftware.balls.ui.RaceView

// Grand Prix mode's Activity - a lighter sibling of GameActivity (see RaceView): the same
// pause/boost/active-item control wiring, but no high scores/achievements/helium integration
// yet, deliberately deferred for this first iteration (see the race package's design notes).
class RaceActivity : AppCompatActivity(), RaceView.Callback {

    private lateinit var binding: ActivityRaceBinding
    private var botCount = RaceConfig.DEFAULT_BOT_COUNT
    private var track = RaceTrack.OVAL
    private var laps = RaceConfig.DEFAULT_LAPS
    private val musicPlayer = BackgroundMusicPlayer(this)
    private val sfxPlayer = SoundEffectPlayer(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRaceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        botCount = intent.getIntExtra(EXTRA_BOT_COUNT, RaceConfig.DEFAULT_BOT_COUNT)
        track = intent.getStringExtra(EXTRA_TRACK)?.let {
            runCatching { RaceTrack.valueOf(it) }.getOrNull()
        } ?: RaceTrack.OVAL
        laps = intent.getIntExtra(EXTRA_LAPS, RaceConfig.DEFAULT_LAPS)

        binding.joystickView.listener = binding.raceView
        binding.raceView.callback = this
        binding.pauseButton.setOnClickListener { showPauseDialog() }
        binding.boostButton.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> binding.raceView.setBoosting(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> binding.raceView.setBoosting(false)
            }
            true
        }
        binding.activeItemButton.setOnClickListener { binding.raceView.useActiveItem() }
        binding.boostButton.alpha = BOOST_UNAVAILABLE_ALPHA
        binding.activeItemButton.alpha = ACTIVE_ITEM_UNAVAILABLE_ALPHA

        binding.raceView.startRace(botCount, track, laps)
    }

    override fun onResume() {
        super.onResume()
        binding.raceView.resumeRace()
        if (MusicSettings.isEnabled(this)) musicPlayer.start(MusicSettings.getSelectedTrack(this))
    }

    override fun onPause() {
        super.onPause()
        binding.raceView.setBoosting(false)
        binding.raceView.pauseRace()
        musicPlayer.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        sfxPlayer.release()
    }

    private fun showPauseDialog() {
        binding.raceView.setBoosting(false)
        binding.raceView.pauseRace()
        val soundSettings = SoundSettingsPanelBinding.inflate(layoutInflater)
        SoundSettingsBinder.bind(this, soundSettings, musicPlayer, sfxPlayer)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.pause_title))
            .setView(soundSettings.root)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.pause_resume)) { _, _ -> binding.raceView.resumeRace() }
            .setNeutralButton(getString(R.string.pause_restart)) { _, _ ->
                binding.raceView.restart(botCount, track, laps)
                if (MusicSettings.isEnabled(this)) musicPlayer.start(MusicSettings.getSelectedTrack(this))
            }
            .setNegativeButton(getString(R.string.pause_menu)) { _, _ -> goToMenu() }
            .show()
    }

    override fun onRaceOver(
        playerWon: Boolean,
        reason: RaceEndReason,
        finalRadius: Int,
        lapsCompleted: Int,
        elapsedSeconds: Int
    ) {
        musicPlayer.stop()
        sfxPlayer.play(
            if (playerWon) SoundEffectPlayer.Effect.GAME_OVER_WIN else SoundEffectPlayer.Effect.GAME_OVER_LOSE
        )
        val resultText = if (playerWon) {
            if (reason == RaceEndReason.FINISH_LINE) {
                getString(R.string.race_result_win_finish)
            } else {
                getString(R.string.race_result_win_elimination)
            }
        } else {
            getString(R.string.race_result_lose)
        }
        val statsText = getString(R.string.race_result_stats_format, lapsCompleted, elapsedSeconds)

        AlertDialog.Builder(this)
            .setTitle(resultText)
            .setMessage(statsText)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.race_result_play_again)) { _, _ ->
                startActivity(
                    Intent(this, RaceActivity::class.java)
                        .putExtra(EXTRA_BOT_COUNT, botCount)
                        .putExtra(EXTRA_TRACK, track.name)
                        .putExtra(EXTRA_LAPS, laps)
                )
                finish()
            }
            .setNegativeButton(getString(R.string.pause_menu)) { _, _ -> goToMenu() }
            .show()
    }

    override fun onBoostAvailabilityChanged(available: Boolean) {
        binding.boostButton.alpha = if (available) 1f else BOOST_UNAVAILABLE_ALPHA
    }

    override fun onCarriedItemChanged(type: PowerUpType?) {
        binding.activeItemButton.alpha = if (type != null) 1f else ACTIVE_ITEM_UNAVAILABLE_ALPHA
        val iconRes = when (type) {
            PowerUpType.SPEED -> R.drawable.ic_item_speed
            PowerUpType.INVISIBILITY -> R.drawable.ic_item_invisibility
            PowerUpType.REPEL -> R.drawable.ic_item_repel
            PowerUpType.FREEZE -> R.drawable.ic_item_freeze
            PowerUpType.HOOK -> R.drawable.ic_item_hook
            else -> R.drawable.ic_active_item
        }
        binding.activeItemButton.setImageResource(iconRes)
        binding.activeItemButton.contentDescription = when (type) {
            PowerUpType.SPEED -> getString(R.string.active_item_speed)
            PowerUpType.INVISIBILITY -> getString(R.string.active_item_invisibility)
            PowerUpType.REPEL -> getString(R.string.active_item_repel)
            PowerUpType.FREEZE -> getString(R.string.active_item_freeze)
            PowerUpType.HOOK -> getString(R.string.active_item_hook)
            else -> getString(R.string.active_item_button)
        }
    }

    private fun goToMenu() {
        startActivity(Intent(this, MainMenuActivity::class.java))
        finish()
    }

    companion object {
        const val EXTRA_BOT_COUNT = "extra_race_bot_count"
        const val EXTRA_TRACK = "extra_race_track"
        const val EXTRA_LAPS = "extra_race_laps"
        private const val BOOST_UNAVAILABLE_ALPHA = 0.35f
        private const val ACTIVE_ITEM_UNAVAILABLE_ALPHA = 0.35f
    }
}
