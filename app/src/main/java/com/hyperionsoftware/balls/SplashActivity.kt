package com.hyperionsoftware.balls

import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.hyperionsoftware.balls.audio.SfxSettings
import com.hyperionsoftware.balls.databinding.ActivitySplashBinding
import com.hyperionsoftware.balls.settings.TutorialSettings
import com.hyperionsoftware.balls.ui.fitContentInsideSystemBars

// A brief animated title card shown once at launch, ahead of the main menu: the red and yellow
// balloons' clash ending on the title, a video with its own sound (res/raw/balloons_arena_splash
// .mp4), played once - or cut short with a tap - before handing off to the main menu, or, the
// very first time the app is ever opened, to the tutorial instead (see TutorialSettings).
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val handler = Handler(Looper.getMainLooper())
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fitContentInsideSystemBars(binding.root)

        binding.splashRoot.setOnClickListener { leave() }
        binding.splashRoot.addOnLayoutChangeListener { root, _, _, _, _, _, _, _, _ -> fitVideoFrame(root.width) }
        val video = binding.splashVideo
        // A splash shouldn't pause whatever music the player has going in another app.
        video.setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE)
        video.setOnPreparedListener { player ->
            // Its sound counts as a sound effect: muted, or at the effects' volume, as set.
            val volume = if (SfxSettings.isEnabled(this)) SfxSettings.getVolume(this) / 100f else 0f
            player.setVolume(volume, volume)
            player.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) binding.splashPoster.visibility = View.GONE
                false
            }
            video.start()
        }
        // A beat on the final title frame before moving on.
        video.setOnCompletionListener { handler.postDelayed({ leave() }, HOLD_LAST_FRAME_MILLIS) }
        video.setOnErrorListener { _, _, _ ->
            // Can't play it on this device: the poster frame stays up for a moment instead.
            handler.postDelayed({ leave() }, FALLBACK_MILLIS)
            true
        }
        video.setVideoURI(Uri.parse("android.resource://$packageName/${R.raw.balloons_arena_splash}"))
        // In case playback never reports finishing.
        handler.postDelayed({ leave() }, SAFETY_TIMEOUT_MILLIS)
    }

    // Full width at the video's own aspect; ConstraintLayout centers it vertically, overflowing
    // (and so cropped) evenly at the top and bottom when the screen is shorter than 9:16.
    private fun fitVideoFrame(width: Int) {
        val frame = binding.splashFrame
        val height = width * VIDEO_HEIGHT / VIDEO_WIDTH
        val params = frame.layoutParams
        if (params.width == width && params.height == height) return
        params.width = width
        params.height = height
        frame.layoutParams = params
    }

    private fun leave() {
        if (finished || isFinishing) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        binding.splashVideo.stopPlayback()
        if (TutorialSettings.hasSeenTutorial(this)) {
            startActivity(Intent(this, MainMenuActivity::class.java))
        } else {
            // Marked seen right away, before the tutorial is even shown, rather than only once
            // dismissed - so backing out early still counts, and it never auto-shows again
            // regardless of how the player leaves it.
            TutorialSettings.setHasSeenTutorial(this, true)
            startActivity(
                Intent(this, TutorialActivity::class.java)
                    .putExtra(TutorialActivity.EXTRA_FIRST_LAUNCH, true)
            )
        }
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        binding.splashVideo.stopPlayback()
        super.onDestroy()
    }

    private companion object {
        const val VIDEO_WIDTH = 720
        const val VIDEO_HEIGHT = 1280
        const val HOLD_LAST_FRAME_MILLIS = 400L
        const val FALLBACK_MILLIS = 2500L
        const val SAFETY_TIMEOUT_MILLIS = 9000L
    }
}
