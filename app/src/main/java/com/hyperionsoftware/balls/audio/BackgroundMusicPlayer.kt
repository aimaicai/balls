package com.hyperionsoftware.balls.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

// Plays one of the bundled MP3s (see MusicTrack/res/raw) on a loop - replaced the old
// from-scratch AudioTrack synthesis, which built a whole loop's worth of raw PCM in memory
// every time. MediaPlayer decodes the compressed file itself, so all this needs to do is
// load, loop and release it.
class BackgroundMusicPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var currentTrack: MusicTrack? = null

    // No-ops if this exact track is already playing, so callers can call start() freely
    // (e.g. on every onResume) without interrupting playback already in progress.
    fun start(track: MusicTrack = MusicTrack.CIELI_DI_ZUCCHERO) {
        if (currentTrack == track && mediaPlayer != null) return
        stop()
        currentTrack = track
        // Null if the resource fails to load/decode - left as a silent no-op rather than
        // crashing the match over background music.
        mediaPlayer = MediaPlayer.create(context, track.rawResId)?.apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            isLooping = true
            start()
        }
    }

    fun stop() {
        mediaPlayer?.apply {
            stop()
            release()
        }
        mediaPlayer = null
        currentTrack = null
    }
}
