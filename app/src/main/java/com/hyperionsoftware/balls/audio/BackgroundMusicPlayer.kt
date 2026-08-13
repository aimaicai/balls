package com.hyperionsoftware.balls.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

// Four short loops, synthesized entirely in code like every other sound in the game - no
// audio assets - each aiming at a different taste rather than one fixed style for everyone.
class BackgroundMusicPlayer {
    private var audioTrack: AudioTrack? = null
    private var currentTrack: MusicTrack? = null

    // No-ops if this exact track is already playing, so callers can call start() freely
    // (e.g. on every onResume) without interrupting playback already in progress.
    fun start(track: MusicTrack = MusicTrack.ARCADE) {
        if (currentTrack == track && audioTrack != null) return
        stop()
        currentTrack = track
        audioTrack = buildTrack(track).also { it.play() }
    }

    fun stop() {
        audioTrack?.apply {
            stop()
            release()
        }
        audioTrack = null
        currentTrack = null
    }

    private fun buildTrack(track: MusicTrack): AudioTrack = when (track) {
        MusicTrack.ARCADE -> buildArcadeTrack()
        MusicTrack.CHILL -> buildChillTrack()
        MusicTrack.CHIPTUNE -> buildChiptuneTrack()
        MusicTrack.ROCK -> buildRockTrack()
    }

    // A punchy bass line on the beat, a fast 16th-note lead arpeggio, and a kick-drum
    // thump - an energetic arcade/EDM feel.
    private fun buildArcadeTrack(): AudioTrack {
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

        return renderToAudioTrack(mix, sampleRate)
    }

    // Slow, soft-attack pad chords plus a sparse melody - a calmer counterpoint to the
    // energetic default, for anyone who'd rather not sprint to EDM the whole match.
    private fun buildChillTrack(): AudioTrack {
        val sampleRate = 22050
        val bpm = 85.0
        val beatSeconds = 60.0 / bpm
        val stepsPerBeat = 2
        val beats = 8
        val framesPerBeat = (sampleRate * beatSeconds).toInt()
        val framesPerStep = framesPerBeat / stepsPerBeat
        val totalFrames = framesPerBeat * beats
        val mix = DoubleArray(totalFrames)

        // Soft pad: a slow attack and a slight detune between two close sine waves for a
        // gentle chorus-like warmth, instead of a hard pluck.
        val padNotesHz = doubleArrayOf(220.0, 220.0, 261.63, 196.00, 220.0, 220.0, 293.66, 196.00)
        for (beat in 0 until beats) {
            val freq = padNotesHz[beat % padNotesHz.size]
            val base = beat * framesPerBeat
            for (i in 0 until framesPerBeat) {
                val idx = base + i
                if (idx >= mix.size) break
                val t = i / sampleRate.toDouble()
                val attack = (t / 0.3).coerceAtMost(1.0)
                val decay = exp(-t * 0.6)
                val wave = sin(2.0 * PI * freq * t) + sin(2.0 * PI * (freq * 1.004) * t)
                mix[idx] += wave * attack * decay * 0.18
            }
        }

        // Melody: one soft note every other step, long tail, so it floats over the pad
        // rather than driving the rhythm.
        val melodyNotesHz = doubleArrayOf(440.0, 523.25, 392.0, 440.0)
        val totalSteps = beats * stepsPerBeat
        for (step in 0 until totalSteps) {
            if (step % 2 != 0) continue
            val freq = melodyNotesHz[(step / 2) % melodyNotesHz.size]
            val base = step * framesPerStep
            val noteFrames = framesPerStep * 2
            for (i in 0 until noteFrames) {
                val idx = base + i
                if (idx >= mix.size) break
                val t = i / sampleRate.toDouble()
                val attack = (t / 0.15).coerceAtMost(1.0)
                val decay = exp(-t * 2.0)
                mix[idx] += sin(2.0 * PI * freq * t) * attack * decay * 0.14
            }
        }

        return renderToAudioTrack(mix, sampleRate)
    }

    private fun square(phase: Double): Double = if (sin(phase) >= 0.0) 1.0 else -1.0

    // Fast square-wave arpeggio over a square-wave bass - the classic 8-bit chiptune sound,
    // built from hard on/off waves instead of the smoother sines everywhere else.
    private fun buildChiptuneTrack(): AudioTrack {
        val sampleRate = 22050
        val bpm = 150.0
        val beatSeconds = 60.0 / bpm
        val stepsPerBeat = 4
        val beats = 8
        val framesPerBeat = (sampleRate * beatSeconds).toInt()
        val framesPerStep = framesPerBeat / stepsPerBeat
        val totalFrames = framesPerBeat * beats
        val mix = DoubleArray(totalFrames)

        val arpNotesHz = doubleArrayOf(523.25, 659.25, 783.99, 1046.50, 783.99, 659.25, 987.77, 783.99)
        val totalSteps = beats * stepsPerBeat
        for (step in 0 until totalSteps) {
            val freq = arpNotesHz[step % arpNotesHz.size]
            val base = step * framesPerStep
            for (i in 0 until framesPerStep) {
                val idx = base + i
                if (idx >= mix.size) break
                val t = i / sampleRate.toDouble()
                val envelope = exp(-t * 10.0)
                mix[idx] += square(2.0 * PI * freq * t) * envelope * 0.14
            }
        }

        val bassNotesHz = doubleArrayOf(130.81, 130.81, 196.00, 164.81)
        for (beat in 0 until beats) {
            val freq = bassNotesHz[beat % bassNotesHz.size]
            val base = beat * framesPerBeat
            for (i in 0 until framesPerBeat) {
                val idx = base + i
                if (idx >= mix.size) break
                val t = i / sampleRate.toDouble()
                val envelope = exp(-t * 5.0)
                mix[idx] += square(2.0 * PI * freq * t) * envelope * 0.22
            }
        }

        return renderToAudioTrack(mix, sampleRate)
    }

    // Power-chord-style bass (root, fifth, and an octave layered together) with an
    // alternating kick/snare-like beat - a heavier, mid-tempo rock feel.
    private fun buildRockTrack(): AudioTrack {
        val sampleRate = 22050
        val bpm = 128.0
        val beatSeconds = 60.0 / bpm
        val stepsPerBeat = 2
        val beats = 8
        val framesPerBeat = (sampleRate * beatSeconds).toInt()
        val framesPerStep = framesPerBeat / stepsPerBeat
        val totalFrames = framesPerBeat * beats
        val mix = DoubleArray(totalFrames)

        val bassNotesHz = doubleArrayOf(82.41, 82.41, 110.00, 73.42, 82.41, 82.41, 98.00, 73.42)
        for (beat in 0 until beats) {
            val freq = bassNotesHz[beat % bassNotesHz.size]
            val base = beat * framesPerBeat
            for (i in 0 until framesPerBeat) {
                val idx = base + i
                if (idx >= mix.size) break
                val t = i / sampleRate.toDouble()
                val envelope = exp(-t * 3.0)
                val wave = sin(2.0 * PI * freq * t) +
                    0.6 * sin(2.0 * PI * freq * 1.5 * t) +
                    0.3 * sin(2.0 * PI * freq * 3.0 * t)
                mix[idx] += wave * envelope * 0.30
            }
        }

        // Kick on the downbeat of each pair of steps, a higher-pitched snare-like hit on
        // the off-beat.
        val kickFrames = (sampleRate * 0.1).toInt()
        val snareFrames = (sampleRate * 0.08).toInt()
        val totalSteps = beats * stepsPerBeat
        for (step in 0 until totalSteps) {
            val base = step * framesPerStep
            if (step % 2 == 0) {
                for (i in 0 until kickFrames) {
                    val idx = base + i
                    if (idx >= mix.size) break
                    val t = i / sampleRate.toDouble()
                    val envelope = exp(-t * 24.0)
                    val freq = 110.0 * exp(-t * 16.0) + 45.0
                    mix[idx] += sin(2.0 * PI * freq * t) * envelope * 0.5
                }
            } else {
                for (i in 0 until snareFrames) {
                    val idx = base + i
                    if (idx >= mix.size) break
                    val t = i / sampleRate.toDouble()
                    val envelope = exp(-t * 30.0)
                    val freq = 320.0 * exp(-t * 10.0) + 180.0
                    mix[idx] += sin(2.0 * PI * freq * t) * envelope * 0.32
                }
            }
        }

        return renderToAudioTrack(mix, sampleRate)
    }

    private fun renderToAudioTrack(mix: DoubleArray, sampleRate: Int): AudioTrack {
        val buffer = ShortArray(mix.size)
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
