package com.hyperionsoftware.balls

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.hyperionsoftware.balls.databinding.ActivityMainMenuBinding

class MainMenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainMenuBinding
    private var botCount = 6

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateBotCountLabel()

        binding.decreaseBotsButton.setOnClickListener {
            botCount = (botCount - 1).coerceAtLeast(MIN_BOTS)
            updateBotCountLabel()
        }
        binding.increaseBotsButton.setOnClickListener {
            botCount = (botCount + 1).coerceAtMost(MAX_BOTS)
            updateBotCountLabel()
        }
        binding.playButton.setOnClickListener {
            startActivity(
                Intent(this, GameActivity::class.java)
                    .putExtra(GameActivity.EXTRA_BOT_COUNT, botCount)
            )
        }
        binding.exitButton.setOnClickListener { finish() }
    }

    private fun updateBotCountLabel() {
        binding.botsCountText.text = getString(R.string.menu_bots_label, botCount)
    }

    companion object {
        private const val MIN_BOTS = 2
        private const val MAX_BOTS = 12
    }
}
