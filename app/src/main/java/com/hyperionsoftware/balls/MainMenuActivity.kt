package com.hyperionsoftware.balls

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hyperionsoftware.balls.audio.BackgroundMusicPlayer
import com.hyperionsoftware.balls.audio.MusicSettings
import com.hyperionsoftware.balls.challenges.DailyChallenges
import com.hyperionsoftware.balls.databinding.ActivityMainMenuBinding
import com.hyperionsoftware.balls.settings.DeveloperSettings
import com.hyperionsoftware.balls.settings.GameSettings
import com.hyperionsoftware.balls.ui.fitContentInsideSystemBars

// Deliberately just one primary action (Play) plus a short stack of secondary buttons -
// match setup and audio settings live on OptionsActivity now instead of competing with
// Play for attention here. Match settings are read fresh from GameSettings whenever a game
// actually starts, since they can change on that other screen while this one isn't visible.
class MainMenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainMenuBinding
    private val musicPlayer = BackgroundMusicPlayer(this)

    // Developer mode's unlock gesture: tap the title DEVELOPER_MODE_TAP_COUNT times within
    // DEVELOPER_MODE_TAP_WINDOW_MS of each other - the classic "tap the build number" pattern,
    // just moved onto this screen's title since there's no build-number row here.
    private var secretTapCount = 0
    private var secretTapWindowStartMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fitContentInsideSystemBars(binding.root)

        binding.titleText.setOnClickListener { onTitleTapped() }

        binding.playButton.setOnClickListener {
            startActivity(
                Intent(this, GameActivity::class.java)
                    .putExtra(GameActivity.EXTRA_BOT_COUNT, GameSettings.getBotCount(this))
                    .putExtra(GameActivity.EXTRA_POWERUP_FREQUENCY, GameSettings.getPowerUpFrequency(this))
                    .putExtra(GameActivity.EXTRA_ARENA_SIZE, GameSettings.getArenaSize(this).name)
                    .putExtra(GameActivity.EXTRA_BOT_AGGRESSIVENESS, GameSettings.getBotAggressiveness(this))
                    .putExtra(GameActivity.EXTRA_SAFE_ZONE_SHRINK_SPEED, GameSettings.getSafeZoneShrinkSpeed(this))
            )
        }
        binding.grandPrixButton.setOnClickListener {
            startActivity(Intent(this, RaceSetupActivity::class.java))
        }
        binding.testFinaleButton.setOnClickListener {
            startActivity(
                Intent(this, GameActivity::class.java)
                    .putExtra(GameActivity.EXTRA_BOT_COUNT, GameSettings.getBotCount(this))
                    .putExtra(GameActivity.EXTRA_POWERUP_FREQUENCY, GameSettings.getPowerUpFrequency(this))
                    .putExtra(GameActivity.EXTRA_ARENA_SIZE, GameSettings.getArenaSize(this).name)
                    .putExtra(GameActivity.EXTRA_BOT_AGGRESSIVENESS, GameSettings.getBotAggressiveness(this))
                    .putExtra(GameActivity.EXTRA_SAFE_ZONE_SHRINK_SPEED, GameSettings.getSafeZoneShrinkSpeed(this))
                    .putExtra(GameActivity.EXTRA_SKIP_TO_FINAL_ROUND, true)
            )
        }
        binding.highScoresButton.setOnClickListener {
            startActivity(Intent(this, HighScoresActivity::class.java))
        }
        binding.achievementsButton.setOnClickListener {
            startActivity(Intent(this, AchievementsActivity::class.java))
        }
        binding.recordsButton.setOnClickListener {
            startActivity(Intent(this, RecordsActivity::class.java))
        }
        binding.optionsButton.setOnClickListener {
            startActivity(Intent(this, OptionsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        if (MusicSettings.isEnabled(this)) musicPlayer.start(MusicSettings.getSelectedTrack(this))
        updateDailyChallengeText()
        updateDeveloperModeVisibility()
    }

    private fun onTitleTapped() {
        val now = System.currentTimeMillis()
        if (now - secretTapWindowStartMs > DEVELOPER_MODE_TAP_WINDOW_MS) {
            secretTapWindowStartMs = now
            secretTapCount = 0
        }
        secretTapCount++
        if (secretTapCount >= DEVELOPER_MODE_TAP_COUNT) {
            secretTapCount = 0
            val enabled = !DeveloperSettings.isEnabled(this)
            DeveloperSettings.setEnabled(this, enabled)
            Toast.makeText(
                this,
                if (enabled) R.string.developer_mode_enabled else R.string.developer_mode_disabled,
                Toast.LENGTH_SHORT
            ).show()
            updateDeveloperModeVisibility()
        }
    }

    private fun updateDeveloperModeVisibility() {
        val visibility = if (DeveloperSettings.isEnabled(this)) View.VISIBLE else View.GONE
        binding.grandPrixButton.visibility = visibility
        binding.testFinaleButton.visibility = visibility
    }

    // Refreshed here rather than only in onCreate so it's current whether we're arriving
    // fresh or returning from a just-finished match (or from Options) without needing this
    // Activity to be told about the result some other way.
    private fun updateDailyChallengeText() {
        binding.dailyChallengeText.text = if (DailyChallenges.isCompletedToday(this)) {
            getString(R.string.daily_challenge_completed_format, DailyChallenges.streak(this))
        } else {
            val challenge = DailyChallenges.todaysChallenge()
            getString(R.string.daily_challenge_format, getString(challenge.descriptionResId))
        }
    }

    override fun onPause() {
        super.onPause()
        musicPlayer.stop()
    }

    private companion object {
        const val DEVELOPER_MODE_TAP_COUNT = 7
        const val DEVELOPER_MODE_TAP_WINDOW_MS = 3000L
    }
}
