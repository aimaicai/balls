package com.hyperionsoftware.balls.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

// A short, driving electronic loop - synthesized entirely in code like every other sound
// in the game, no audio assets. Three layers mixed together: a punchy bass line on the
// beat, a fast 16th-note lead arpeggio, and a kick-drum thump, closer to an energetic
// arcade/EDM feel than a calm melody.
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
        val bpm = 150.0
        val beatSeconds = 60.0 / bpm
        val stepsPerBeat = 4
        val beats = 8
        val framesPerBeat = (sampleRate * beatSeconds).toInt()
        val framesPerStep = framesPerBeat / stepsPerBeat
        val totalFrames = framesPerBeat * beats
        val mix = DoubleArray(totalFrames)

        // Bass: a driving root-note riff, a fundamental plus a fifth-ish harmonic for a
        // saw-like buzz, with a fast pluck envelope per beat so it pushes forward instead
        // of droning.
        val bassNotesHz = doubleArrayOf(110.00, 110.00, 130.81, 98.00, 110.00, 110.00, 146.83, 98.00)
        for (beat in 0 until beats) {
            val freq = bassNotesHz[beat % bassNotesHz.size]
            val base = beat * framesPerBeat
            for (i in 0 until framesPerBeat) {
                val idx = base + i
                if (idx >= mix.size) break
                val t = i / sampleRate.toDouble()
                val envelope = exp(-t * 4.0)
                val wave = sin(2.0 * PI * freq * t) + 0.5 * sin(2.0 * PI * freq * 2.0 * t)
                mix[idx] += wave * envelope * 0.28
            }
        }

        // Lead: quick 16th-note arpeggio plucks, higher register and brighter (an extra
        // overtone) so it cuts through the bass instead of blending into it.
        val leadNotesHz = doubleArrayOf(440.0, 523.25, 659.25, 880.0, 659.25, 523.25, 880.0, 659.25)
        val totalSteps = beats * stepsPerBeat
        for (step in 0 until totalSteps) {
            val freq = leadNotesHz[step % leadNotesHz.size]
            val base = step * framesPerStep
            for (i in 0 until framesPerStep) {
                val idx = base + i
                if (idx >= mix.size) break
                val t = i / sampleRate.toDouble()
                val envelope = exp(-t * 12.0)
                val wave = sin(2.0 * PI * freq * t) + 0.35 * sin(2.0 * PI * freq * 2.0 * t)
                mix[idx] += wave * envelope * 0.16
            }
        }

        // Kick: a fast pitch-drop thump on every beat, for the punch a driving track needs.
        val kickFrames = (sampleRate * 0.09).toInt()
        for (beat in 0 until beats) {
            val base = beat * framesPerBeat
            for (i in 0 until kickFrames) {
                val idx = base + i
                if (idx >= mix.size) break
                val t = i / sampleRate.toDouble()
                val envelope = exp(-t * 28.0)
                val freq = 120.0 * exp(-t * 18.0) + 40.0
                mix[idx] += sin(2.0 * PI * freq * t) * envelope * 0.5
            }
        }

        val buffer = ShortArray(totalFrames)
        for (i in mix.indices) {
            val clamped = mix[i].coerceIn(-1.0, 1.0)
            buffer[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
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
