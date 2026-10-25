package com.hyperionsoftware.balls.ui.gdx

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// The outline of a real party balloon, in balloon radii, front (direction of travel) toward -y:
// round and widest a little above the middle, tapering into a short neck at the back, closed by
// the little rolled knot ("tortellino") of tied-off latex. Its width matches the round body the
// physics uses (radius 1); only the neck and knot extend past it.
//
// Pure numbers, no Android types, so both the baked sprite (Atlas.balloonPath) and the membrane
// simulation (BalloonBody, which needs to know where the latex actually is along each spoke)
// work from the very same shape.
object BalloonOutline {
    // The body as cubic segments from the top, clockwise, each one's 2 control points and end
    // point (x, y pairs); the right half's last segment ends at the neck, the left half mirrors it.
    const val TOP_Y = -1.06f
    val RIGHT_HALF = floatArrayOf(
        0.56f, -1.06f, 0.98f, -0.66f, 0.98f, -0.14f,
        0.98f, 0.42f, 0.5f, 0.9f, 0.09f, Atlas.NECK_Y
    )
    val LEFT_HALF = floatArrayOf(
        -0.5f, 0.9f, -0.98f, 0.42f, -0.98f, -0.14f,
        -0.98f, -0.66f, -0.56f, -1.06f, 0f, -1.06f
    )
    const val NECK_HALF_WIDTH = 0.09f
    const val KNOT_HALF_WIDTH = 0.11f

    // How far the latex (or knot) reaches from the center along each of `spokes` evenly spaced
    // directions, angle 0 along +x and increasing toward +y - the same spoke layout BalloonBody
    // uses. Where a ray crosses the outline more than once (the pinch where the neck meets the
    // knot), the farthest crossing counts.
    fun radialProfile(spokes: Int): FloatArray {
        val points = outlinePolygon()
        return FloatArray(spokes) { k ->
            val angle = k * TWO_PI / spokes
            farthestCrossing(points, cos(angle), sin(angle))
        }
    }

    // The closed outline as a polygon (x, y pairs), the curves sampled finely enough that their
    // chords are well under a pixel off at any size the game draws.
    private fun outlinePolygon(): FloatArray {
        val out = ArrayList<Float>()
        fun add(x: Float, y: Float) {
            out.add(x)
            out.add(y)
        }
        fun curves(segments: FloatArray, startX: Float, startY: Float) {
            var x0 = startX
            var y0 = startY
            for (s in segments.indices step 6) {
                for (step in 1..CURVE_SAMPLES) {
                    val t = step / CURVE_SAMPLES.toFloat()
                    val u = 1f - t
                    val a = u * u * u
                    val b = 3f * u * u * t
                    val c = 3f * u * t * t
                    val d = t * t * t
                    add(
                        a * x0 + b * segments[s] + c * segments[s + 2] + d * segments[s + 4],
                        a * y0 + b * segments[s + 1] + c * segments[s + 3] + d * segments[s + 5]
                    )
                }
                x0 = segments[s + 4]
                y0 = segments[s + 5]
            }
        }
        add(0f, TOP_Y)
        curves(RIGHT_HALF, 0f, TOP_Y)
        // The knot hangs from the neck - as far as a ray from the center is concerned, a little
        // block reaching down to its rolled lip.
        add(KNOT_HALF_WIDTH, Atlas.KNOT_END_Y)
        add(-KNOT_HALF_WIDTH, Atlas.KNOT_END_Y)
        add(-NECK_HALF_WIDTH, Atlas.NECK_Y)
        curves(LEFT_HALF, -NECK_HALF_WIDTH, Atlas.NECK_Y)
        return out.toFloatArray()
    }

    private fun farthestCrossing(points: FloatArray, dirX: Float, dirY: Float): Float {
        var farthest = 0f
        val n = points.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            val x0 = points[i * 2]
            val y0 = points[i * 2 + 1]
            val ex = points[j * 2] - x0
            val ey = points[j * 2 + 1] - y0
            // Solve t * dir = p0 + u * e for the ray's t and the segment's u.
            val det = ex * dirY - ey * dirX
            if (abs(det) < 1e-9f) continue
            val t = (ex * y0 - ey * x0) / det
            val u = (dirX * y0 - dirY * x0) / det
            if (u in 0f..1f && t > farthest) farthest = t
        }
        return farthest
    }

    private const val CURVE_SAMPLES = 24
    private const val TWO_PI = (Math.PI * 2).toFloat()
}
