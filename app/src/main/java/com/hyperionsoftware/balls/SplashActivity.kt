package com.hyperionsoftware.balls

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.hyperionsoftware.balls.databinding.ActivitySplashBinding
import com.hyperionsoftware.balls.settings.TutorialSettings

// A brief animated title card shown once at launch, ahead of the main menu: a Street-
// Fighter-style clash between two Canvas-drawn balloons (see SplashFightView), which hands
// off to the main menu itself the instant its own animation finishes - or, the very first
// time the app is ever opened, to the tutorial instead (see TutorialSettings).
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.splashView.start {
            if (TutorialSettings.hasSeenTutorial(this)) {
                startActivity(Intent(this, MainMenuActivity::class.java))
            } else {
                // Marked seen right away, before the tutorial is even shown, rather than
                // only once dismissed - so backing out early still counts, and it never
                // auto-shows again regardless of how the player leaves it.
                TutorialSettings.setHasSeenTutorial(this, true)
                startActivity(
                    Intent(this, TutorialActivity::class.java)
                        .putExtra(TutorialActivity.EXTRA_FIRST_LAUNCH, true)
                )
            }
            finish()
        }
    }
}
