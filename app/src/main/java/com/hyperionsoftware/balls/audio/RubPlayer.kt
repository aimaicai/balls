package com.hyperionsoftware.balls.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

// The squeak of two balloons rubbing together, synthesized live since it has to follow how hard
// and how fast they're rubbing. Latex on latex sticks and slips: the surfaces grip, stretch,
// let go and grip again hundreds of times a second, each slip a sharp jolt - a sawtooth whose
// rate rises with the sliding speed - and the membrane rings with it, which gives the squeak
// its bright, harmonic "eek". It never holds one steady pitch: it comes in short chirps that
// glide up or down as the grip changes, with a soft, rough rustle of latex dragging under them.
//
// Runs on its own thread, streaming small buffers to an AudioTrack. update() is cheap and safe
// from any thread; the sound fades out by itself if updates stop (a paused or closed match).
class RubPlayer {
    @Volatile private var targetIntensity = 0f
    @Volatile private var targetSpeed = 0f
    @Volatile private var volume = 1f
    @Volatile private var lastUpdateNanos = 0L
    @Volatile private var running = false
    private var thread: Thread? = null
    private val lock = Object()

    // intensity 0..1 (0 = no contact), speed 0..1 (how fast the latex slides).
    fun update(intensity: Float, speed: Float, volume: Float) {
        targetIntensity = intensity.coerceIn(0f, 1f)
        targetSpeed = speed.coerceIn(0f, 1f)
        this.volume = volume
        lastUpdateNanos = System.nanoTime()
        if (intensity > 0f) {
            if (thread == null) start()
            synchronized(lock) { lock.notifyAll() }
        }
    }

    fun silence() {
        targetIntensity = 0f
    }

    fun release() {
        running = false
        synchronized(lock) { lock.notifyAll() }
        thread?.join(200)
        thread = null
    }

    private fun start() {
        running = true
        thread = Thread({ loop() }, "BalloonRub").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun loop() {
        val track = try {
            createTrack()
        } catch (_: Exception) {
            // No audio device right now - the rubbing still shows (and vibrates), it just stays
            // silent for this match.
            return
        }
        val buffer = ShortArray(BLOCK)
        val synth = Synth()
        var playing = false
        try {
            while (running) {
                val stale = System.nanoTime() - lastUpdateNanos > STALE_NANOS
                val intensity = if (stale) 0f else targetIntensity
                if (intensity <= 0f && synth.isSilent()) {
                    if (playing) {
                        track.pause()
                        track.flush()
                        playing = false
                    }
                    synchronized(lock) {
                        if (running && (targetIntensity <= 0f || System.nanoTime() - lastUpdateNanos > STALE_NANOS)) {
                            lock.wait(IDLE_WAIT_MILLIS)
                        }
                    }
                    continue
                }
                synth.render(buffer, intensity, targetSpeed, volume)
                if (!playing) {
                    track.play()
                    playing = true
                }
                // Blocks until there's room - this is what paces the loop.
                track.write(buffer, 0, buffer.size)
            }
        } catch (_: Exception) {
        } finally {
            track.release()
        }
    }

    private fun createTrack(): AudioTrack {
        val minBytes = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBytes, BLOCK * 2 * 3))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
    }

    // The signal chain, all state kept between blocks so chirps carry across them seamlessly.
    private class Synth {
        private val random = java.util.Random()
        private val formantLow = BandPass()
        private val formantHigh = BandPass()
        private var amplitude = 0f
        private var envelope = 0f
        private var phase = 0f
        private var chirpLeft = 0
        private var chirpLength = 1
        private var gapLeft = 0
        private var pitch = 600f
        private var glide = 0f
        private var rustle = 0f
        private var roughness = 0f

        fun isSilent() = amplitude < 0.002f && envelope < 0.002f

        fun render(out: ShortArray, intensity: Float, speed: Float, volume: Float) {
            for (i in out.indices) {
                amplitude += (intensity - amplitude) * AMPLITUDE_SMOOTHING
                val frequency: Float
                val target: Float
                if (chirpLeft > 0) {
                    chirpLeft--
                    val progress = 1f - chirpLeft.toFloat() / chirpLength
                    frequency = pitch * (1f + glide * progress)
                    target = if (chirpLeft > CHIRP_RELEASE_FRAMES) 1f else 0f
                } else {
                    target = 0f
                    if (gapLeft > 0) {
                        gapLeft--
                    } else if (amplitude > 0.04f) {
                        startChirp(speed)
                    }
                    frequency = pitch
                }
                envelope += (target - envelope) * if (target > envelope) ATTACK else RELEASE
                // Stick (a slow ramp as the latex stretches), then slip (the sudden drop); the
                // period wavers a little from slip to slip.
                phase += frequency / SAMPLE_RATE * (1f + (random.nextFloat() - 0.5f) * 0.08f)
                if (phase >= 1f) phase -= 1f
                val saw = phase * 2f - 1f
                val squeak = 0.35f * saw + 1.6f * formantLow.process(saw) + 0.9f * formantHigh.process(saw)
                // The rustle: noise softened to a low, rough "frrr", wavering in strength.
                rustle += (random.nextFloat() * 2f - 1f - rustle) * 0.18f
                roughness += (random.nextFloat() * 2f - 1f - roughness) * 0.002f
                val drag = rustle * (0.7f + 0.6f * roughness)
                val mix = amplitude * (envelope * squeak * 0.55f * (0.4f + 0.6f * speed) + drag * 0.45f)
                out[i] = (tanh(mix * 1.4f) * volume * OUTPUT_GAIN * Short.MAX_VALUE).toInt().toShort()
            }
        }

        private fun startChirp(speed: Float) {
            chirpLength = (SAMPLE_RATE * (0.05f + random.nextFloat() * 0.11f)).toInt()
            chirpLeft = chirpLength
            pitch = (380f + 620f * speed) * (0.85f + random.nextFloat() * 0.35f)
            glide = -0.25f + random.nextFloat() * 0.6f
            gapLeft = (SAMPLE_RATE * (0.012f + random.nextFloat() * 0.058f) * (1.4f - speed)).toInt()
            formantLow.tune(minOf(pitch * 2.1f, 4000f), 6f)
            formantHigh.tune(minOf(pitch * 3.9f, 7000f), 7f)
        }
    }

    // A two-pole resonant band-pass (the usual biquad, 0 dB at its peak).
    private class BandPass {
        private var b0 = 0f
        private var b2 = 0f
        private var a1 = 0f
        private var a2 = 0f
        private var x1 = 0f
        private var x2 = 0f
        private var y1 = 0f
        private var y2 = 0f

        fun tune(frequency: Float, q: Float) {
            val w = 2.0 * PI * frequency / SAMPLE_RATE
            val alpha = sin(w) / (2.0 * q)
            val a0 = 1.0 + alpha
            b0 = (alpha / a0).toFloat()
            b2 = (-alpha / a0).toFloat()
            a1 = (-2.0 * cos(w) / a0).toFloat()
            a2 = ((1.0 - alpha) / a0).toFloat()
        }

        fun process(x: Float): Float {
            val y = b0 * x + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
            return y
        }
    }

    companion object {
        private const val SAMPLE_RATE = 22050
        // ~12 ms per block: short enough that the squeak starts and stops with the contact.
        private const val BLOCK = 256
        private const val STALE_NANOS = 150_000_000L
        private const val IDLE_WAIT_MILLIS = 250L
        private const val AMPLITUDE_SMOOTHING = 0.003f
        private const val ATTACK = 0.02f
        private const val RELEASE = 0.004f
        private const val CHIRP_RELEASE_FRAMES = SAMPLE_RATE / 50
        private const val OUTPUT_GAIN = 0.7f
    }
}
