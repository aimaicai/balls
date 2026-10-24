package com.hyperionsoftware.balls.ui

import android.os.Handler
import android.os.HandlerThread
import android.view.Choreographer
import kotlin.math.min

// The game/race loop, driven by the display's own vsync instead of a hand-rolled
// sleep(16ms) - shared by GameView and RaceView. The old sleep loop capped everything at
// ~60 FPS even on 90/120 Hz screens, and since its wake-ups had nothing to do with when the
// display actually refreshed, frames landed unevenly against vsync (a faint judder even at a
// steady 60). Choreographer instead calls onFrame once per display refresh, at whatever rate
// the screen runs, and dt comes from consecutive vsync timestamps rather than from whenever
// the thread happened to wake up, so movement advances by exactly one refresh's worth each
// frame.
//
// Runs on its own HandlerThread (Choreographer is per-Looper), so the update and render work
// stays off the UI thread exactly like the old dedicated loop thread did.
class FrameLoop(name: String, private val onFrame: (dt: Float) -> Unit) {
    private val thread = HandlerThread(name)

    @Volatile
    private var running = false
    private var lastFrameNanos = 0L

    // Whether frames are still being scheduled - false as soon as a stop is requested, even
    // if the thread is still finishing its last frame, so a quick pause/resume never sees a
    // stopping loop as still running (the caller's restart joins the old thread anyway).
    val isRunning: Boolean get() = running

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            // Same 50 ms cap the old loop had, so a hitch (or the first frame after a
            // resume) can't teleport anything through walls or other balloons.
            val dt = if (lastFrameNanos == 0L) 0f else min((frameTimeNanos - lastFrameNanos) / 1_000_000_000f, MAX_DT)
            lastFrameNanos = frameTimeNanos
            onFrame(dt)
            // Re-checked after onFrame, since onFrame itself can stop the loop (game over).
            if (running) Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun start() {
        running = true
        thread.start()
        Handler(thread.looper).post { Choreographer.getInstance().postFrameCallback(frameCallback) }
    }

    // Safe from any thread, including from inside onFrame itself: no further frames run, and
    // the thread winds down right after the current one (if any) finishes.
    fun requestStop() {
        running = false
        thread.quitSafely()
    }

    // For callers on another thread that must not touch the game state again until the loop
    // is really finished (surface teardown, restart) - never call this from inside onFrame.
    fun stopAndJoin(timeoutMs: Long = 500) {
        requestStop()
        thread.join(timeoutMs)
    }

    companion object {
        private const val MAX_DT = 0.05f
    }
}
