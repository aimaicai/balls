package com.hyperionsoftware.balls.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

// Short one-shot effects synthesized in code, the same way the game's music used to be
// before that moved to bundled MP3s - these are tiny and numerous enough (and don't need a
// specific musical identity the way a whole track does) that synthesizing them is still the
// better fit, and it's what lets volume/mute apply per-effect at all. Replaces the old
// ToneGenerator beeps, which all sounded like the same flat telephone blip regardless of
// what actually happened - absorbing, picking up a power-up and winning the match were all
// indistinguishable by ear.
class SoundEffectPlayer(private val context: Context) {
    enum class Effect {
        ABSORB, COMBO, POWERUP, ACTIVE_ITEM, FINAL_ROUND_ALERT, GAME_OVER_WIN, GAME_OVER_LOSE
    }

    private val sampleRate = 22050
    // Each effect is synthesized once and cached, then just restarted from the top on every
    // trigger (same technique GameView's boost hiss loop already uses) - cheap enough to
    // replay several times a second (e.g. a fast absorb combo) without re-synthesizing.
    private val cache = mutableMapOf<Effect, AudioTrack>()

    fun play(effect: Effect) {
        if (!SfxSettings.isEnabled(context)) return
        val track = cache.getOrPut(effect) { build(effect) }
        track.setVolume(SfxSettings.getVolume(context) / 100f)
        track.stop()
        track.setPlaybackHeadPosition(0)
        track.play()
    }

    fun release() {
        cache.values.forEach { it.release() }
        cache.clear()
    }

    private fun build(effect: Effect): AudioTrack = when (effect) {
        Effect.ABSORB -> buildAbsorb()
        Effect.COMBO -> buildCombo()
        Effect.POWERUP -> buildPowerUp()
        Effect.ACTIVE_ITEM -> buildActiveItem()
        Effect.FINAL_ROUND_ALERT -> buildFinalRoundAlert()
        Effect.GAME_OVER_WIN -> buildGameOverWin()
        Effect.GAME_OVER_LOSE -> buildGameOverLose()
    }

    // A satisfying "gulp": a quick upward pitch sweep with a short burst of noise riding
    // under it, like something being swallowed rather than a flat beep.
    private fun buildAbsorb(): AudioTrack {
        val duration = 0.16
        val frames = (sampleRate * duration).toInt()
        val mix = DoubleArray(frames)
        for (i in 0 until frames) {
            val t = i / sampleRate.toDouble()
            val progress = t / duration
            val freq = 260.0 + 520.0 * progress
            val envelope = exp(-t * 16.0)
            val tone = sin(2.0 * PI * freq * t) + 0.3 * sin(2.0 * PI * freq * 2.0 * t)
            val noise = (Math.random() * 2.0 - 1.0) * exp(-t * 45.0) * 0.35
            mix[i] = (tone * 0.75 + noise) * envelope
        }
        return render(mix)
    }

    // A bright three-note ascending arpeggio - a proper "combo!" flourish rather than a
    // single repeated beep, so back-to-back combos actually feel like they're building.
    private fun buildCombo(): AudioTrack {
        val notesHz = doubleArrayOf(523.25, 659.25, 987.77)
        val noteDuration = 0.09
        val framesPerNote = (sampleRate * noteDuration).toInt()
        val mix = DoubleArray(framesPerNote * notesHz.size)
        for (n in notesHz.indices) {
            val freq = notesHz[n]
            val base = n * framesPerNote
            for (i in 0 until framesPerNote) {
                val t = i / sampleRate.toDouble()
                val envelope = exp(-t * 9.0)
                val wave = sin(2.0 * PI * freq * t) + 0.4 * sin(2.0 * PI * freq * 2.0 * t)
                mix[base + i] += wave * envelope * 0.5
            }
        }
        return render(mix)
    }

    // A sparkly two-note "ding-ding" pickup chime, brighter and higher than the absorb sound
    // so the two are easy to tell apart by ear alone.
    private fun buildPowerUp(): AudioTrack {
        val notesHz = doubleArrayOf(880.0, 1318.51)
        val noteDuration = 0.11
        val framesPerNote = (sampleRate * noteDuration).toInt()
        val mix = DoubleArray(framesPerNote * notesHz.size)
        for (n in notesHz.indices) {
            val freq = notesHz[n]
            val base = n * framesPerNote
            for (i in 0 until framesPerNote) {
                val t = i / sampleRate.toDouble()
                val envelope = exp(-t * 7.0)
                val wave = sin(2.0 * PI * freq * t) + 0.5 * sin(2.0 * PI * freq * 2.0 * t) +
                    0.25 * sin(2.0 * PI * freq * 3.0 * t)
                mix[base + i] += wave * envelope * 0.45
            }
        }
        return render(mix)
    }

    // A quick downward whoosh-zap for spending a carried item - the opposite motion from
    // the absorb "gulp" (falling instead of rising), so using an item reads as a distinct,
    // deliberate action rather than another pickup blip.
    private fun buildActiveItem(): AudioTrack {
        val duration = 0.18
        val frames = (sampleRate * duration).toInt()
        val mix = DoubleArray(frames)
        for (i in 0 until frames) {
            val t = i / sampleRate.toDouble()
            val progress = t / duration
            val freq = 1100.0 - 750.0 * progress
            val envelope = exp(-t * 11.0)
            val tone = sin(2.0 * PI * freq * t)
            val noise = (Math.random() * 2.0 - 1.0) * 0.5
            mix[i] = (tone * 0.6 + noise * 0.4) * envelope
        }
        return render(mix)
    }

    // A rising, tremolo-shaken alarm stinger - urgent rather than another plain beep, for
    // the one moment in a match (the final round starting) that's meant to feel dramatic.
    private fun buildFinalRoundAlert(): AudioTrack {
        val duration = 0.5
        val frames = (sampleRate * duration).toInt()
        val mix = DoubleArray(frames)
        for (i in 0 until frames) {
            val t = i / sampleRate.toDouble()
            val progress = t / duration
            val freq = 220.0 + 520.0 * progress
            val tremolo = 0.6 + 0.4 * sin(2.0 * PI * 18.0 * t)
            val envelope = exp(-t * 2.5)
            val wave = sin(2.0 * PI * freq * t) + 0.4 * sin(2.0 * PI * freq * 2.0 * t)
            mix[i] = wave * tremolo * envelope * 0.6
        }
        return render(mix)
    }

    // A short triumphant major arpeggio - four notes climbing and landing on the octave.
    private fun buildGameOverWin(): AudioTrack {
        val notesHz = doubleArrayOf(523.25, 659.25, 783.99, 1046.50)
        val noteDuration = 0.15
        val framesPerNote = (sampleRate * noteDuration).toInt()
        val mix = DoubleArray(framesPerNote * notesHz.size + (sampleRate * 0.2).toInt())
        for (n in notesHz.indices) {
            val freq = notesHz[n]
            val base = n * framesPerNote
            val noteFrames = if (n == notesHz.lastIndex) framesPerNote + (sampleRate * 0.2).toInt() else framesPerNote
            for (i in 0 until noteFrames) {
                val idx = base + i
                if (idx >= mix.size) break
                val t = i / sampleRate.toDouble()
                val envelope = exp(-t * 4.0)
                val wave = sin(2.0 * PI * freq * t) + 0.5 * sin(2.0 * PI * freq * 2.0 * t)
                mix[idx] += wave * envelope * 0.45
            }
        }
        return render(mix)
    }

    // A descending, wavering minor motif with a pitch-droop finish - a proper "aww" rather
    // than a harsh negative buzz.
    private fun buildGameOverLose(): AudioTrack {
        val notesHz = doubleArrayOf(440.0, 349.23, 293.66)
        val noteDuration = 0.22
        val framesPerNote = (sampleRate * noteDuration).toInt()
        val mix = DoubleArray(framesPerNote * notesHz.size)
        for (n in notesHz.indices) {
            val freq = notesHz[n]
            val base = n * framesPerNote
            val isLast = n == notesHz.lastIndex
            for (i in 0 until framesPerNote) {
                val t = i / sampleRate.toDouble()
                // The last note droops downward in pitch as it dies out, rather than
                // holding steady, for a wilting rather than clipped ending.
                val droop = if (isLast) 1.0 - 0.25 * (t / noteDuration) else 1.0
                val envelope = exp(-t * 5.5)
                val wave = sin(2.0 * PI * freq * droop * t) + 0.3 * sin(2.0 * PI * freq * droop * 2.0 * t)
                mix[base + i] += wave * envelope * 0.45
            }
        }
        return render(mix)
    }

    private fun render(mix: DoubleArray): AudioTrack {
        val buffer = ShortArray(mix.size)
        for (i in mix.indices) {
            buffer[i] = (mix[i].coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
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
        return track
    }
}
