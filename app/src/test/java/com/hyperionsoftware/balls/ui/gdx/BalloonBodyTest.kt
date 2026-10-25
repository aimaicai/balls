package com.hyperionsoftware.balls.ui.gdx

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Covers the balloon membrane: it rests round, flattens its front by a believable amount when
// moving (more at a sprint), swells to the outside of a turn, two balloons pressed together meet
// at a face without their outlines overlapping (each denting by the same depth), and nothing the
// game can throw at it - any frame rate, sharp turns, crowds - makes it blow up.
class BalloonBodyTest {
    private val front = BalloonBody.SPOKES * 3 / 4
    private val rear = BalloonBody.SPOKES / 4
    private val right = 0
    private val left = BalloonBody.SPOKES / 2

    private fun drive(
        body: BalloonBody,
        seconds: Float,
        dt: Float = 1f / 60f,
        speed: Float = 0f,
        turnRate: Float = 0f,
        startFrame: Long = 0L,
        boosting: Boolean = false
    ): Long {
        var frame = startFrame
        var angle = -PI.toFloat() / 2f
        repeat((seconds / dt).toInt()) {
            angle += turnRate * dt
            body.prepare(frame++, true, 500f, 500f, 40f, cos(angle), sin(angle), speed, speed > 0f, boosting, false, dt)
            body.settle(dt)
            body.simulate(dt)
        }
        return frame
    }

    @Test
    fun `outline profile matches the baked balloon shape`() {
        val profile = BalloonBody.PROFILE
        assertEquals(1.06f, profile[front], 0.01f)
        assertEquals(0.98f, profile[right], 0.02f)
        assertEquals(0.98f, profile[left], 0.02f)
        assertEquals(Atlas.KNOT_END_Y, profile[rear], 0.01f)
    }

    @Test
    fun `a balloon at rest stays round`() {
        val body = BalloonBody()
        drive(body, 2f)
        for (s in body.shape) assertEquals(1f, s, 0.001f)
    }

    @Test
    fun `cruising flattens the front a little and a sprint flattens it more`() {
        val cruise = BalloonBody()
        drive(cruise, 3f, speed = 280f)
        val cruiseFlattening = 1f - cruise.shape[front]
        assertTrue("cruise flattening $cruiseFlattening", cruiseFlattening in 0.02f..0.07f)
        assertTrue("sides swell", cruise.shape[right] > 1f && cruise.shape[left] > 1f)

        val sprint = BalloonBody()
        drive(sprint, 3f, speed = 280f * 2.6f, boosting = false)
        val sprintFlattening = 1f - sprint.shape[front]
        assertTrue("sprint flattening $sprintFlattening", sprintFlattening in 0.09f..0.18f)
        assertTrue(sprintFlattening > cruiseFlattening * 2f)
    }

    @Test
    fun `intensity scales the deformation and zero keeps the balloon rigid`() {
        val gentle = BalloonBody().apply { intensity = 0.5f }
        drive(gentle, 3f, speed = 280f)
        val normal = BalloonBody()
        drive(normal, 3f, speed = 280f)
        val rigid = BalloonBody().apply { intensity = 0f }
        drive(rigid, 3f, speed = 280f * 2.6f, turnRate = 1.6f)
        assertEquals((1f - normal.shape[front]) * 0.5f, 1f - gentle.shape[front], 0.005f)
        for (s in rigid.shape) assertEquals(1f, s, 0.001f)
    }

    @Test
    fun `a turn swells the outer side and leads the nose into it`() {
        val body = BalloonBody()
        // Increasing angle turns toward +x of the balloon's frame (its right, local +x).
        drive(body, 3f, speed = 280f, turnRate = 1.6f)
        assertTrue("outer side swells more than the inner", body.shape[left] > body.shape[right])
        assertTrue("nose leads into the turn: ${body.yaw}", body.yaw > 0.02f && body.yaw < 0.3f)
    }

    @Test
    fun `stays bounded and settles back to rest whatever the frame rate`() {
        for (dt in floatArrayOf(1f / 30f, 1f / 60f, 1f / 90f, 1f / 144f)) {
            val body = BalloonBody()
            val random = Random(42)
            var frame = 0L
            var angle = 0f
            repeat((6f / dt).toInt()) {
                // Erratic driving: jerky turns, speed jumping between stop and full sprint.
                angle += (random.nextFloat() - 0.5f) * 6f * dt * 10f
                val speed = if (random.nextFloat() < 0.1f) random.nextFloat() * 730f else 280f
                body.prepare(frame++, true, 500f, 500f, 40f, cos(angle), sin(angle), speed, true, random.nextBoolean(), false, dt)
                body.settle(dt)
                body.simulate(dt)
                for (s in body.shape) assertTrue("dt=$dt shape $s", s.isFinite() && s in 0.6f..1.4f)
            }
            drive(body, 3f, dt = dt, startFrame = frame)
            for (s in body.shape) assertEquals("dt=$dt", 1f, s, 0.01f)
            assertEquals(0f, body.yaw, 0.01f)
        }
    }

    // How far the deformed outline of `body` (radius r) reaches toward a world direction.
    private fun reach(body: BalloonBody, r: Float, worldAngle: Float): Float {
        val local = worldAngle - (body.drawnHeading + PI.toFloat() / 2f)
        val wrapped = ((local % (2f * PI.toFloat())) + 2f * PI.toFloat()) % (2f * PI.toFloat())
        return body.outlineRadius(wrapped) * r
    }

    private fun pressTogether(ra: Float, rb: Float, seconds: Float = 1.5f): Pair<BalloonBody, BalloonBody> {
        val a = BalloonBody()
        val b = BalloonBody()
        val touch = BalloonBody.Touch()
        val dt = 1f / 60f
        var frame = 0L
        repeat((seconds / dt).toInt()) {
            // Head on, each driving into the other; the engine keeps them 2 px apart.
            a.prepare(frame, true, 500f, 500f, ra, 1f, 0f, 280f, true, false, false, dt)
            b.prepare(frame, false, 500f + ra + rb + 2f, 500f, rb, -1f, 0f, 280f, true, false, false, dt)
            frame++
            assertTrue(BalloonBody.touch(a, b, touch))
            a.settle(dt)
            b.settle(dt)
            a.simulate(dt)
            b.simulate(dt)
        }
        return a to b
    }

    @Test
    fun `two balloons pressed together share a face instead of overlapping`() {
        val (a, b) = pressTogether(40f, 40f)
        val dx = b.drawnX - a.drawnX
        val dy = b.drawnY - a.drawnY
        val distance = sqrt(dx * dx + dy * dy)
        assertTrue("drawn closer than touching: they press in", distance < 80f)
        val toward = atan2(dy, dx)
        // Across the whole contact face, not just dead center.
        for (offset in floatArrayOf(-0.3f, -0.15f, 0f, 0.15f, 0.3f)) {
            val ax = a.drawnX + cos(toward + offset) * reach(a, 40f, toward + offset)
            val ay = a.drawnY + sin(toward + offset) * reach(a, 40f, toward + offset)
            // The point of a's outline must not be inside b's outline.
            val bx = ax - b.drawnX
            val by = ay - b.drawnY
            val bAngle = atan2(by, bx)
            val bDistance = sqrt(bx * bx + by * by)
            assertTrue("a pokes into b by ${reach(b, 40f, bAngle) - bDistance}", bDistance >= reach(b, 40f, bAngle) - 0.8f)
        }
        // a faces b, so its front spoke points straight at the contact.
        assertTrue("the contact face is flattened: ${a.shape[front]}", a.shape[front] < 0.97f)
    }

    @Test
    fun `both give way by the same depth, so the smaller one shows it more`() {
        val (small, big) = pressTogether(36f, 44f)
        // Both face each other, so each one's front spoke points at the contact.
        val smallDent = 1f - small.shape[front]
        val bigDent = 1f - big.shape[front]
        assertEquals(smallDent * 36f, bigDent * 44f, 0.5f)
        assertTrue("small $smallDent big $bigDent", smallDent > bigDent)
    }

    @Test
    fun `released balloons spring back and ring down`() {
        val body = BalloonBody()
        val dt = 1f / 60f
        val partner = BalloonBody()
        val touch = BalloonBody.Touch()
        var frame = 0L
        repeat(60) {
            body.prepare(frame, true, 500f, 500f, 40f, 1f, 0f, 280f, true, false, false, dt)
            partner.prepare(frame, false, 582f, 500f, 40f, -1f, 0f, 280f, true, false, false, dt)
            frame++
            BalloonBody.touch(body, partner, touch)
            body.settle(dt)
            partner.settle(dt)
            body.simulate(dt)
            partner.simulate(dt)
        }
        assertTrue("dented: ${body.shape[front]}", body.shape[front] < 0.98f)
        var overshoot = 0f
        repeat(120) {
            body.prepare(frame++, true, 500f, 500f, 40f, 1f, 0f, 0f, false, false, false, dt)
            body.settle(dt)
            body.simulate(dt)
            overshoot = maxOf(overshoot, body.shape[front] - 1f)
        }
        assertTrue("springs back past rest a little: $overshoot", overshoot > 0.002f)
        assertEquals(1f, body.shape[front], 0.01f)
        assertTrue(abs(body.offsetX) < 0.5f)
    }
}
