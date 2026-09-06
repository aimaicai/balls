package com.hyperionsoftware.balls.race

import com.hyperionsoftware.balls.game.Vector2

// A circuit: a closed loop of checkpoints. checkpoints[0] is the start/finish line - every
// blob begins there and completes a lap by reaching it again after touching every other
// checkpoint in order (see RaceEngine.updateRaceProgress) - reaching one out of order simply
// doesn't count, which is what keeps a blob from cutting across the infield to skip ahead.
// The track SURFACE is the corridor within halfWidth (plus RaceConfig.OFF_TRACK_MARGIN) of
// the polyline connecting them in a loop, last back to first - see distanceOffTrack. No
// Android/UI dependency here on purpose, same as the game package, so this is fully unit
// testable; display names live in the UI layer instead (see RaceSetupActivity).
enum class RaceTrack(val checkpoints: List<Vector2>, val halfWidth: Float) {
    OVAL(
        checkpoints = listOf(
            Vector2(2100f, 300f),
            Vector2(3600f, 600f),
            Vector2(3900f, 1500f),
            Vector2(3600f, 2400f),
            Vector2(2100f, 2700f),
            Vector2(600f, 2400f),
            Vector2(300f, 1500f),
            Vector2(600f, 600f)
        ),
        halfWidth = 350f
    ),

    // A true figure-eight: checkpoints 2 and 6 share the same crossing point in the middle,
    // visited once per loop of each half - nothing about the sequential/index-based
    // checkpoint model below needs a track to be a simple non-self-intersecting loop.
    FIGURE_EIGHT(
        checkpoints = listOf(
            Vector2(2100f, 400f),
            Vector2(3200f, 900f),
            Vector2(2100f, 1500f),
            Vector2(3200f, 2100f),
            Vector2(2100f, 2600f),
            Vector2(1000f, 2100f),
            Vector2(2100f, 1500f),
            Vector2(1000f, 900f)
        ),
        halfWidth = 300f
    );

    // How far beyond the track's own half-width a position sits - zero or negative means on
    // the track surface itself, positive means that far off it. Checked against every
    // segment of the loop (not just whichever checkpoint a blob is currently racing toward)
    // so it reflects the track's actual physical shape regardless of race progress.
    fun distanceOffTrack(position: Vector2): Float {
        var closest = Float.MAX_VALUE
        for (i in checkpoints.indices) {
            val a = checkpoints[i]
            val b = checkpoints[(i + 1) % checkpoints.size]
            val distance = distanceToSegment(position, a, b)
            if (distance < closest) closest = distance
        }
        return closest - halfWidth
    }

    private fun distanceToSegment(p: Vector2, a: Vector2, b: Vector2): Float {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val lengthSquared = abx * abx + aby * aby
        if (lengthSquared < 0.0001f) return p.distanceTo(a)
        val t = (((p.x - a.x) * abx + (p.y - a.y) * aby) / lengthSquared).coerceIn(0f, 1f)
        return p.distanceTo(Vector2(a.x + abx * t, a.y + aby * t))
    }
}
