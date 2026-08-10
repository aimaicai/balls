package com.hyperionsoftware.balls.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

// A short, cheerful tune looped forever - synthesized entirely in code like every other
// sound in the game, no audio assets. A plucked-bell pentatonic melody, quiet enough to
// sit under the sound effects without fighting them for attention.
class BackgroundMusicPlayer {
    private var track: AudioTrack? = null

    fun start() {
        if (track != null) return
        track = buildTrack().also { it.play() }
    }

    fun stop() {
        track?.apply {
            stop()
            release()
        }
        track = null
    }

    private fun buildTrack(): AudioTrack {
        val sampleRate = 22050
        val noteHz = floatArrayOf(
            523.25f, 659.25f, 783.99f, 659.25f,
            587.33f, 783.99f, 880.00f, 783.99f,
            523.25f, 659.25f, 587.33f, 440.00f
        )
        val noteSeconds = 0.32f
        val framesPerNote = (sampleRate * noteSeconds).toInt()
        val buffer = ShortArray(framesPerNote * noteHz.size)

        for (noteIndex in noteHz.indices) {
            val freq = noteHz[noteIndex]
            val base = noteIndex * framesPerNote
            for (i in 0 until framesPerNote) {
                val t = i / sampleRate.toFloat()
                // A quick pluck-like decay per note, warmed up with a touch of its own
                // octave overtone instead of a flat sine buzz.
                val envelope = exp(-t * 6f)
                val fundamental = sin(2.0 * PI * freq * t)
                val overtone = sin(2.0 * PI * freq * 2f * t) * 0.25
                val sample = ((fundamental + overtone) * envelope * 0.22).toFloat()
                buffer[base + i] = (sample * Short.MAX_VALUE).toInt().toShort()
            }
        }

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
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
        audioTrack.write(buffer, 0, buffer.size)
        audioTrack.setLoopPoints(0, buffer.size, -1)
        return audioTrack
    }
}
