package com.hyperionsoftware.balls.race

import com.hyperionsoftware.balls.game.Vector2

// A circuit: a closed loop of checkpoints. checkpoints[0] is the start/finish line. Race
// progress is continuous along this loop's own path (see closestArcLength/totalLength, used
// by RaceEngine.updateRaceProgress) rather than a sequence of waypoints that must each be
// touched in order - simply driving the track normally, a little off to either side included,
// advances a blob toward completing a lap; there's no gate to get stuck behind if one is
// missed. The track SURFACE is the corridor within halfWidth (plus RaceConfig.OFF_TRACK_MARGIN)
// of the polyline connecting the checkpoints in a loop, last back to first - see
// distanceOffTrack. No Android/UI dependency here on purpose, same as the game package, so
// this is fully unit testable; display names live in the UI layer instead (see
// RaceSetupActivity).
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
    // visited once per loop of each half - nothing about the arc-length model below needs a
    // track to be a simple non-self-intersecting loop.
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

    // Total distance around the closed loop, last checkpoint back to first included - the
    // denominator for lap completion: covering this much cumulative distance along the
    // track's own path (see RaceEngine.updateRaceProgress) completes one lap.
    val totalLength: Float = run {
        var total = 0f
        for (i in checkpoints.indices) {
            total += checkpoints[i].distanceTo(checkpoints[(i + 1) % checkpoints.size])
        }
        total
    }

    // How far beyond the track's own half-width a position sits - zero or negative means on
    // the track surface itself, positive means that far off it. Checked against every
    // segment of the loop (not just whichever part of it a blob is currently near) so it
    // reflects the track's actual physical shape regardless of race progress.
    fun distanceOffTrack(position: Vector2): Float {
        var closest = Float.MAX_VALUE
        for (i in checkpoints.indices) {
            val a = checkpoints[i]
            val b = checkpoints[(i + 1) % checkpoints.size]
            val t = projectionT(position, a, b)
            val distance = position.distanceTo(Vector2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t))
            if (distance < closest) closest = distance
        }
        return closest - halfWidth
    }

    // How far along the track's own closed path (measured from checkpoints[0], forward
    // through [1], [2], ... and back to [0]) the point on the track nearest `position` sits -
    // the free-roaming foundation for lap progress: a blob's own cumulative version of this
    // (see RaceBlob.trackArcPosition/lapDistanceTraveled) is what actually completes laps, not
    // touching any specific waypoint.
    fun closestArcLength(position: Vector2): Float {
        var bestDistance = Float.MAX_VALUE
        var bestArc = 0f
        var cumulative = 0f
        for (i in checkpoints.indices) {
            val a = checkpoints[i]
            val b = checkpoints[(i + 1) % checkpoints.size]
            val segmentLength = a.distanceTo(b)
            val t = projectionT(position, a, b)
            val distance = position.distanceTo(Vector2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t))
            if (distance < bestDistance) {
                bestDistance = distance
                bestArc = cumulative + t * segmentLength
            }
            cumulative += segmentLength
        }
        return bestArc
    }

    // The point on the track's own path at the given arc-length offset, wrapping around past
    // totalLength - a sliding lookahead target (see RaceBotBlob) that always sits some
    // distance further along the loop from wherever a blob currently is, instead of a fixed
    // next-checkpoint waypoint it has to reach exactly.
    fun pointAtArcLength(arcLength: Float): Vector2 {
        val wrapped = ((arcLength % totalLength) + totalLength) % totalLength
        var cumulative = 0f
        for (i in checkpoints.indices) {
            val a = checkpoints[i]
            val b = checkpoints[(i + 1) % checkpoints.size]
            val segmentLength = a.distanceTo(b)
            if (wrapped <= cumulative + segmentLength) {
                val t = if (segmentLength < 0.0001f) 0f else ((wrapped - cumulative) / segmentLength).coerceIn(0f, 1f)
                return Vector2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            }
            cumulative += segmentLength
        }
        return checkpoints[0]
    }

    private fun projectionT(p: Vector2, a: Vector2, b: Vector2): Float {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val lengthSquared = abx * abx + aby * aby
        if (lengthSquared < 0.0001f) return 0f
        return (((p.x - a.x) * abx + (p.y - a.y) * aby) / lengthSquared).coerceIn(0f, 1f)
    }
}
