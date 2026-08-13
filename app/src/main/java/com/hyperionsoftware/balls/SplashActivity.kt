package com.hyperionsoftware.balls

import android.content.Intent
import android.os.Bundle
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.hyperionsoftware.balls.databinding.ActivitySplashBinding

// A brief animated title card shown once at launch, ahead of the main menu: a Canvas-drawn
// balloon (see SplashBalloonView) pops in and settles into a gentle bob, with the game's
// name fading up beneath it a moment later.
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.splashBalloon.start()

        binding.titleText.alpha = 0f
        binding.titleText.translationY = 40f
        binding.titleText.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(TITLE_DELAY_MS)
            .setDuration(TITLE_DURATION_MS)
            .setInterpolator(OvershootInterpolator())
            .start()

        binding.root.postDelayed({
            startActivity(Intent(this, MainMenuActivity::class.java))
            finish()
        }, SPLASH_DURATION_MS)
    }

    companion object {
        private const val TITLE_DELAY_MS = 350L
        private const val TITLE_DURATION_MS = 500L
        private const val SPLASH_DURATION_MS = 2000L
    }
}
