package com.hyperionsoftware.balls.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

// A synthesized air-escaping hiss for sprinting: soft-clipped white noise looped seamlessly,
// closer to a deflating balloon than raw hard noise or a beep. Created lazily on the first
// sprint and reused after that.
class HissPlayer {
    private var track: AudioTrack? = null

    fun start(volume: Float) {
        try {
            val track = track ?: createTrack().also { track = it }
            track.setVolume(volume)
            track.setPlaybackHeadPosition(0)
            track.play()
        } catch (_: Exception) {
            // Audio device may be briefly unavailable; the visual/vibration feedback still
            // lands, so silently skip the sound this time.
        }
    }

    fun stop() {
        try {
            track?.pause()
            track?.setPlaybackHeadPosition(0)
        } catch (_: Exception) {
        }
    }

    fun release() {
        track?.release()
        track = null
    }

    private fun createTrack(): AudioTrack {
        val sampleRate = 22050
        val frameCount = sampleRate / 2
        val buffer = ShortArray(frameCount)
        val random = java.util.Random()
        for (i in buffer.indices) {
            val softened = (random.nextFloat() + random.nextFloat() + random.nextFloat() - 1.5f) / 1.5f
            buffer[i] = (softened * Short.MAX_VALUE * 0.35f).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(buffer.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(buffer, 0, buffer.size)
        track.setLoopPoints(0, frameCount, -1)
        return track
    }
}
