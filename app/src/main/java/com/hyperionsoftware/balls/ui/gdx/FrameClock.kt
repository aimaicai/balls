package com.hyperionsoftware.balls.ui.gdx

import kotlin.math.abs
import kotlin.math.min

// Turns libGDX's raw per-frame delta into the dt the game advances by. libGDX measures it when
// its render thread wakes up, which jitters by a fraction of a millisecond around the real
// display refresh even when no frame is ever missed - and since movement is dt * speed, that
// jitter shows up as a faint unevenness in motion. While frames arrive steadily, this uses a
// running average instead (exactly one refresh interval's worth per frame); a genuine hitch
// (well off the average) passes through as-is so time never drifts, capped like the old loop.
class FrameClock {
    private var average = 0f
    private var primed = false

    fun next(rawDelta: Float): Float {
        // The first frame after a (re)start or a pause measures the whole gap since the last
        // frame drawn, which was never game time - it advances nothing, like the old loop's
        // first frame, and doesn't feed the average either.
        if (!primed) {
            primed = true
            return 0f
        }
        if (rawDelta <= 0f) return 0f
        if (average == 0f) {
            average = rawDelta
            return min(rawDelta, MAX_DT)
        }
        val steady = abs(rawDelta - average) < average * STEADY_TOLERANCE
        average += (rawDelta - average) * SMOOTHING
        return min(if (steady) average else rawDelta, MAX_DT)
    }

    fun reset() {
        average = 0f
        primed = false
    }

    companion object {
        // Same 50 ms cap the old loop had, so a hitch (or the first frame after a resume) can't
        // teleport anything through walls or other balloons.
        private const val MAX_DT = 0.05f
        private const val STEADY_TOLERANCE = 0.25f
        private const val SMOOTHING = 0.1f
    }
}
