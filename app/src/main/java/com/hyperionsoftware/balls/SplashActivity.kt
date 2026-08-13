package com.hyperionsoftware.balls

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.hyperionsoftware.balls.databinding.ActivitySplashBinding

// A brief animated title card shown once at launch, ahead of the main menu: a Street-
// Fighter-style clash between two Canvas-drawn balloons (see SplashFightView), which hands
// off to the main menu itself the instant its own animation finishes.
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.splashView.start {
            startActivity(Intent(this, MainMenuActivity::class.java))
            finish()
        }
    }
}
