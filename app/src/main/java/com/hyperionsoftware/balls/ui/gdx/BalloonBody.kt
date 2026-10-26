package com.hyperionsoftware.balls.ui.gdx

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// How a balloon's latex gives way, purely for the drawing: the gameplay still treats every
// balloon as a rigid circle. The membrane is SPOKES radial "spokes" around the center, each one
// how far the latex reaches along it relative to rest (`shape`, 1 = undeformed), and each a
// damped oscillator coupled to its neighbours:
//
//  - Tension: a stretched membrane resists curvature, so a spoke is pulled toward the average of
//    its neighbours (a discrete Laplacian) on top of its own spring. The two are balanced so that
//    the shape modes ring like a membrane under tension - mode n's frequency grows with n
//    (omega_n^2 ~ n^2 - 1 for a ring), the n=2 "ellipse" wobble being the slowest real one. A
//    damping term on the neighbours' relative motion (the latex's own viscosity) makes the fine
//    ripples die out faster than the broad ones, as they do in rubber.
//  - Strain hardening: latex gets stiffer the more it's stretched or dented (a cubic term), so a
//    hard sprint flattens the front noticeably but never absurdly.
//  - Gas: the air inside pushes every spoke outward when the outline's area drops below rest - a
//    dent on one side bulges the others. Only partly, since a real balloon also bulges toward
//    and away from the camera, which a top-down view never sees.
//  - Air flow: the pressure a moving sphere feels (front stagnation, suction past the shoulders,
//    a separated wake behind - measured subcritical sphere pressure coefficients) presses the
//    front flat and lets the sides swell; the jet pushing the balloon acts on its neck, squeezing
//    it from behind by as much as the drag does from the front.
//  - Inertia: when the balloon speeds up or curves, the latex and the air inside lag behind -
//    the side away from the acceleration swells, and the whole body squashes along it. Its yaw
//    lags too: the nose leads slightly into a turn and swings past when the turn ends.
//  - The neck flutters while air is forced out of it at a sprint, like a balloon rocket's does.
//  - Contact: two balloons pressed together share one flat face where their latex meets
//    (pressure balances across it); both dent by about the same depth (it's the latex's
//    tension that resists, which doesn't depend on size), and a frozen one hardly at all.
//    The rest of each outline bulges out to keep the air's volume, and when they part the latex
//    springs back and rings down.
//
// A real party balloon rings in the tens of hertz with deformations of a few percent - too fast
// to see at 60 frames a second and too small to read. The frequencies here are slowed to a few
// hertz and the deformations exaggerated about threefold, the usual trade for animation that
// reads as the real thing. The gains below were tuned against an offline model of the same
// equations (front flattening, turn bulge, contact faces and ring-down checked frame by frame).
//
// Integrated with semi-implicit Euler in fixed small substeps (under the stiffest mode's
// stability limit) - a few hundred multiply-adds per balloon per frame.
class BalloonBody {
    // How far the latex reaches along each spoke, relative to rest. Spoke k points at angle
    // k * 2pi / SPOKES in the balloon's own frame (the unrotated sprite: x right, y down, the
    // front - the way it travels - toward -y).
    val shape = FloatArray(SPOKES) { 1f }
    private val shapeSpeed = FloatArray(SPOKES)
    private val load = FloatArray(SPOKES)
    private val force = FloatArray(SPOKES)

    var x = 0f
        private set
    var y = 0f
        private set
    var radius = 1f
        private set
    var isPlayer = false
        private set

    // How exaggerated the deformation is, 1 = the tuned default (see BalloonBodies.intensity):
    // scales every external push and the contact depth, and relaxes the strain hardening to
    // match, so the response keeps the same character at any strength.
    var intensity = 1f
    private var velX = 0f
    private var velY = 0f
    private var accX = 0f
    private var accY = 0f
    private var facingAngle = 0f
    private var turnRate = 0f
    private var thrusting = false
    private var boosting = false
    private var frozen = false
    // The last frame this body was prepared for (see BalloonBodies).
    var frame = Long.MIN_VALUE
        private set

    // Where the balloon is drawn relative to its gameplay position: pressed against a
    // neighbour, the two close the small gap the engine's bounce leaves and lean into each
    // other, so their latex actually meets (see touch).
    var offsetX = 0f
        private set
    var offsetY = 0f
        private set
    private var targetOffsetX = 0f
    private var targetOffsetY = 0f

    // The drawn orientation's lag/lead relative to the gameplay facing, radians.
    var yaw = 0f
        private set
    private var yawSpeed = 0f
    private var flutter = 0f

    private val partners = arrayOfNulls<BalloonBody>(MAX_CONTACTS)
    private val shares = FloatArray(MAX_CONTACTS)
    private val planeNx = FloatArray(MAX_CONTACTS)
    private val planeNy = FloatArray(MAX_CONTACTS)
    private val planeH = FloatArray(MAX_CONTACTS)
    private var contactCount = 0

    val drawnX: Float get() = x + offsetX
    val drawnY: Float get() = y + offsetY

    // The drawn heading (radians, like atan2 of the facing), including the yaw lag.
    val drawnHeading: Float get() = facingAngle + yaw

    fun reset() {
        shape.fill(1f)
        shapeSpeed.fill(0f)
        offsetX = 0f
        offsetY = 0f
        yaw = 0f
        yawSpeed = 0f
        accX = 0f
        accY = 0f
        turnRate = 0f
        flutter = 0f
    }

    // Struck by a static discharge: the latex shudders all over at once, then rings down.
    fun shock(random: java.util.Random) {
        for (k in 0 until SPOKES) {
            val kick = (if (k % 2 == 0) 1f else -1f) * (0.4f + random.nextFloat()) - 0.5f
            shapeSpeed[k] += kick * SHOCK_KICK * intensity
        }
    }

    // Feeds in this frame's gameplay state. `speed` is how fast the balloon is driving itself
    // along its facing (0 while frozen). A body that skipped a frame (off screen, or just
    // spawned) starts again from rest rather than reading a jump as a violent acceleration.
    fun prepare(
        frame: Long,
        isPlayer: Boolean,
        x: Float,
        y: Float,
        radius: Float,
        facingX: Float,
        facingY: Float,
        speed: Float,
        thrusting: Boolean,
        boosting: Boolean,
        frozen: Boolean,
        dt: Float
    ) {
        val continuing = this.frame == frame - 1
        this.frame = frame
        val angle = atan2(facingY, facingX)
        val vx = facingX * speed
        val vy = facingY * speed
        if (!continuing) {
            reset()
        } else if (dt > 0f) {
            var ax = (vx - velX) / dt
            var ay = (vy - velY) / dt
            val magnitude = sqrt(ax * ax + ay * ay)
            if (magnitude > MAX_ACCELERATION) {
                ax *= MAX_ACCELERATION / magnitude
                ay *= MAX_ACCELERATION / magnitude
            }
            val k = 1f - exp(-dt / ACCELERATION_SMOOTHING)
            accX += (ax - accX) * k
            accY += (ay - accY) * k
            turnRate += (wrapAngle(angle - facingAngle) / dt - turnRate) * k
        }
        this.isPlayer = isPlayer
        this.x = x
        this.y = y
        this.radius = max(radius, 1f)
        this.thrusting = thrusting
        this.boosting = boosting
        this.frozen = frozen
        facingAngle = angle
        velX = vx
        velY = vy
        contactCount = 0
        targetOffsetX = 0f
        targetOffsetY = 0f
    }

    // Eases the drawn position toward where this frame's contacts want it.
    fun settle(dt: Float) {
        var tx = targetOffsetX
        var ty = targetOffsetY
        val limit = radius * MAX_OFFSET
        val length = sqrt(tx * tx + ty * ty)
        if (length > limit) {
            tx *= limit / length
            ty *= limit / length
        }
        val k = 1f - exp(-dt / OFFSET_SMOOTHING)
        offsetX += (tx - offsetX) * k
        offsetY += (ty - offsetY) * k
    }

    fun simulate(dt: Float) {
        if (dt <= 0f) return
        // The balloon's own frame: the sprite is rotated by heading + 90 degrees (its front is -y).
        val rotation = drawnHeading + HALF_PI
        val c = cos(rotation)
        val s = sin(rotation)
        computeLoads(c, s)
        computeContactPlanes(c, s)

        val frequency = (BASE_FREQUENCY * sqrt(REFERENCE_RADIUS / radius)).coerceIn(MIN_FREQUENCY, MAX_FREQUENCY)
        val omega = TWO_PI * frequency
        val stiffness = omega * omega
        val damping = 2f * DAMPING_RATIO * omega * (if (frozen) FROZEN_DAMPING_FACTOR else 1f)
        val viscosity = LATEX_VISCOSITY * omega
        val hardening = HARDENING / max(intensity * intensity, MIN_HARDENING_SCALE)
        val substeps = ceil(dt / MAX_SUBSTEP).toInt().coerceIn(1, MAX_SUBSTEPS)
        val h = dt / substeps
        repeat(substeps) {
            var areaSum = 0f
            for (k in 0 until SPOKES) areaSum += shape[k] * shape[(k + 1) % SPOKES]
            val gas = GAS_STIFFNESS * (1f - areaSum / SPOKES)
            for (k in 0 until SPOKES) {
                val previous = (k + SPOKES - 1) % SPOKES
                val next = (k + 1) % SPOKES
                val d = shape[k] - 1f
                val curvature = shape[previous] + shape[next] - 2f * shape[k]
                val curvatureSpeed = shapeSpeed[previous] + shapeSpeed[next] - 2f * shapeSpeed[k]
                force[k] = stiffness * (-SPRING * d - hardening * d * d * d + TENSION * curvature + gas + load[k]) -
                    damping * shapeSpeed[k] + viscosity * curvatureSpeed
            }
            for (k in 0 until SPOKES) {
                shapeSpeed[k] += force[k] * h
                shape[k] = (shape[k] + shapeSpeed[k] * h).coerceIn(MIN_SCALE, MAX_SCALE)
            }
            applyContacts()
        }

        // The yaw: a damped torsion spring around a small lead into the turn.
        val speed = sqrt(velX * velX + velY * velY)
        val target = (YAW_LEAD * intensity * turnRate * min(speed / REFERENCE_SPEED, 2f)).coerceIn(-MAX_YAW, MAX_YAW)
        val yawOmega = TWO_PI * YAW_FREQUENCY
        yawSpeed += (-yawOmega * yawOmega * (yaw - target) - 2f * YAW_DAMPING_RATIO * yawOmega * yawSpeed) * dt
        yaw = (yaw + yawSpeed * dt).coerceIn(-MAX_YAW, MAX_YAW)
    }

    // How far the deformed outline reaches from the center at `localAngle` (radians, the
    // balloon's own frame - see shape), in radii.
    fun outlineRadius(localAngle: Float): Float {
        val index = localAngle / TWO_PI * SPOKES
        return sample(PROFILE, index) * sample(shape, index)
    }

    // The external pressures for this frame, in units of the membrane's own stiffness, so that
    // their effect is the same fraction of the radius whatever the balloon's size.
    private fun computeLoads(c: Float, s: Float) {
        load.fill(0f)
        if (frozen) return
        val localVx = c * velX + s * velY
        val localVy = -s * velX + c * velY
        val speed = sqrt(localVx * localVx + localVy * localVy)
        if (speed > 1f) {
            val q = DRAG * intensity * (speed / REFERENCE_SPEED) * (speed / REFERENCE_SPEED)
            val ux = localVx / speed
            val uy = localVy / speed
            for (k in 0 until SPOKES) {
                val alignment = COS[k] * ux + SIN[k] * uy
                load[k] -= q * (pressureCoefficient(alignment) + if (thrusting) THRUST_LOBE[k] else 0f)
            }
        }
        val localAx = c * accX + s * accY
        val localAy = -s * accX + c * accY
        val acceleration = sqrt(localAx * localAx + localAy * localAy)
        if (acceleration > 1f) {
            for (k in 0 until SPOKES) {
                val along = COS[k] * localAx + SIN[k] * localAy
                load[k] -= INERTIA * intensity * (along + SQUASH * (along * along / acceleration - 0.5f * acceleration)) / REFERENCE_ACCELERATION
            }
        }
        if (boosting && thrusting) {
            // Smoothed noise, so it shakes the broad neck modes rather than exciting the finest
            // ripples - a wag of the neck from side to side.
            flutter += (Random.nextFloat() * 2f - 1f - flutter) * FLUTTER_SMOOTHING
            for (k in 0 until SPOKES) load[k] += FLUTTER * intensity * flutter * COS[k] * NECK_WEIGHT[k]
        }
    }

    // One flat face per contact, where this balloon's latex meets the other's: a plane at
    // planeH radii from the drawn center along the local normal (planeNx, planeNy).
    private fun computeContactPlanes(c: Float, s: Float) {
        var planes = 0
        for (i in 0 until contactCount) {
            val other = partners[i] ?: continue
            val dx = other.drawnX - drawnX
            val dy = other.drawnY - drawnY
            val distance = sqrt(dx * dx + dy * dy)
            if (distance < 0.001f) continue
            val overlap = radius + other.radius - distance
            if (overlap <= 0f) continue
            val nx = dx / distance
            val ny = dy / distance
            planeNx[planes] = c * nx + s * ny
            planeNy[planes] = -s * nx + c * ny
            planeH[planes] = (radius - overlap * shares[i]) / radius
            planes++
        }
        contactPlanes = planes
    }

    private var contactPlanes = 0

    private fun applyContacts() {
        for (p in 0 until contactPlanes) {
            val nx = planeNx[p]
            val ny = planeNy[p]
            val h = planeH[p]
            for (k in 0 until SPOKES) {
                val alignment = COS[k] * nx + SIN[k] * ny
                if (alignment <= MIN_CONTACT_ALIGNMENT) continue
                val limit = h / (PROFILE[k] * alignment)
                if (shape[k] > limit) {
                    shape[k] = max(limit, MIN_SCALE)
                    if (shapeSpeed[k] > 0f) shapeSpeed[k] = 0f
                }
            }
        }
    }

    private fun addContact(other: BalloonBody, share: Float) {
        if (contactCount >= MAX_CONTACTS) return
        partners[contactCount] = other
        shares[contactCount] = share
        contactCount++
    }

    class Touch {
        // How fast the two surfaces slide past each other, and how fast they're driving into
        // each other, px/s.
        var slidingSpeed = 0f
        var pressingSpeed = 0f
    }

    companion object {
        const val SPOKES = 32
        private const val TWO_PI = (Math.PI * 2).toFloat()
        private const val HALF_PI = (Math.PI / 2).toFloat()

        // The gameplay's cruising speed and its sharpest cruising turn (speed times turn rate),
        // the scales every gain below is measured against.
        const val REFERENCE_SPEED = 280f
        private const val REFERENCE_ACCELERATION = 448f
        private const val REFERENCE_RADIUS = 40f

        // The n=2 wobble's frequency at REFERENCE_RADIUS; bigger balloons ring slower.
        private const val BASE_FREQUENCY = 7f
        private const val MIN_FREQUENCY = 3.5f
        private const val MAX_FREQUENCY = 10f
        private const val DAMPING_RATIO = 0.16f
        private const val FROZEN_DAMPING_FACTOR = 4f
        // At cruising speed the front flattens by about 4% of the radius, at a full sprint
        // about 15%; a cruising turn swells the outer side by about 6%. Smaller than that
        // barely reads on a balloon a few dozen pixels across.
        private const val DRAG = 0.045f
        private const val INERTIA = 0.03f
        private const val SQUASH = 0.5f
        private const val HARDENING = 40f
        private const val MIN_HARDENING_SCALE = 0.1f
        private const val GAS_STIFFNESS = 1.2f
        private const val FLUTTER = 0.06f
        private const val FLUTTER_SMOOTHING = 0.5f
        private const val MAX_ACCELERATION = REFERENCE_ACCELERATION * 3f
        private const val ACCELERATION_SMOOTHING = 0.05f
        // How hard a static shock kicks the membrane, in radii per second.
        private const val SHOCK_KICK = 5f
        private const val MIN_SCALE = 0.6f
        private const val MAX_SCALE = 1.4f
        private const val MAX_SUBSTEP = 1f / 300f
        private const val MAX_SUBSTEPS = 16

        private const val YAW_LEAD = 0.1f
        private const val YAW_FREQUENCY = 2.2f
        private const val YAW_DAMPING_RATIO = 0.35f
        private const val MAX_YAW = 0.3f

        // Contacts: how far apart (beyond touching) two balloons still count as pressed together
        // - the engine's bounce always leaves them a couple of pixels apart - and how deep they
        // press into each other when driving into one another.
        private const val CONTACT_SLOP_PX = 4f
        private const val CONTACT_SLOP_FRACTION = 0.02f
        private const val PRESS_DEPTH_PER_REFERENCE_SPEED = 0.2f
        private const val MAX_PRESS_DEPTH = 0.3f
        private const val FROZEN_COMPLIANCE = 0.05f
        private const val OFFSET_SMOOTHING = 0.035f
        private const val MAX_OFFSET = 0.3f
        private const val MIN_CONTACT_ALIGNMENT = 0.05f
        private const val MAX_CONTACTS = 6

        val COS = FloatArray(SPOKES) { cos(it * TWO_PI / SPOKES) }
        val SIN = FloatArray(SPOKES) { sin(it * TWO_PI / SPOKES) }

        // Where the latex (or the knot) is along each spoke at rest, in radii.
        val PROFILE: FloatArray = BalloonOutline.radialProfile(SPOKES)

        // How far along each spoke a balloon-shaped sprite actually needs drawing, as a fraction
        // of its half size (see Painter.deformed). The margin keeps the silhouette's soft,
        // filtered edge, a few texels wide even on a mipmapped small balloon.
        val SPRITE_REACH: FloatArray = BalloonOutline.fanReach(SPOKES, Atlas.BALLOON_EXTENT, 0.06f)
        // The same for a fan using every other spoke (see Painter.deformed's stride).
        val COARSE_SPRITE_REACH: FloatArray = BalloonOutline.fanReach(SPOKES / 2, Atlas.BALLOON_EXTENT, 0.06f)

        // Mode n of a SPOKES-ring's Laplacian has eigenvalue 4 sin^2(pi n / SPOKES). SPRING and
        // TENSION (in units of the n=2 mode's stiffness) put mode 2 at exactly the base
        // frequency and the rigid shift of mode 1 at 0.6 of it - it's what lets the body lag
        // behind its center, and it has to come back.
        private val EIGEN_1 = 4f * sin(Math.PI.toFloat() / SPOKES).let { it * it }
        private val EIGEN_2 = 4f * sin(2f * Math.PI.toFloat() / SPOKES).let { it * it }
        private val TENSION = 0.65f / (EIGEN_2 - EIGEN_1)
        private val SPRING = 1f - TENSION * EIGEN_2
        private val LATEX_VISCOSITY = 0.6f * DAMPING_RATIO / EIGEN_2

        // The jet's push on the neck, spread over the back and scaled so its total squeeze along
        // the axis matches the drag's on the front (the two balance while cruising).
        private val THRUST_LOBE: FloatArray = run {
            val rear = FloatArray(SPOKES) { k -> max(0f, SIN[k]).let { it * it * it * it } }
            var dragAxial = 0f
            var lobeAxial = 0f
            for (k in 0 until SPOKES) {
                val alongFront = -SIN[k]
                dragAxial += pressureCoefficient(alongFront) * alongFront
                lobeAxial += rear[k] * -alongFront
            }
            FloatArray(SPOKES) { k -> rear[k] * dragAxial / lobeAxial }
        }
        private val NECK_WEIGHT = FloatArray(SPOKES) { k -> max(0f, SIN[k]).let { it * it } }

        // Pressure coefficient on a sphere in air at the speeds involved (subcritical): ideal
        // flow from the stagnation point up to the suction peak near the shoulders, then a
        // separated wake of roughly constant pressure. `alignment` is the cosine between the
        // surface's direction and the flow coming at it.
        fun pressureCoefficient(alignment: Float): Float {
            val a = alignment.coerceIn(-1f, 1f)
            val attached = max(1f - 2.25f * (1f - a * a), -0.6f)
            // The flow separates from ~75 degrees past the front and is fully separated by ~105
            // (blended linearly in the cosine, which is close to linear in angle there).
            val t = ((SEPARATION_START_COS - a) / (2f * SEPARATION_START_COS)).coerceIn(0f, 1f)
            return attached * (1f - t) + WAKE_PRESSURE * t
        }

        private const val SEPARATION_START_COS = 0.2588f
        private const val WAKE_PRESSURE = -0.35f

        // Linear interpolation around a closed ring of spoke values, at a fractional spoke index.
        fun sample(values: FloatArray, index: Float): Float {
            val n = values.size
            val floor = kotlin.math.floor(index)
            val i0 = ((floor.toInt() % n) + n) % n
            val f = index - floor
            return values[i0] + (values[(i0 + 1) % n] - values[i0]) * f
        }

        // Checks whether two prepared bodies are pressed together this frame and, if so, records
        // the contact on both (see simulate) and fills `touch`. Returns false if they aren't.
        fun touch(a: BalloonBody, b: BalloonBody, touch: Touch): Boolean {
            val dx = b.x - a.x
            val dy = b.y - a.y
            val distance = sqrt(dx * dx + dy * dy)
            val touching = a.radius + b.radius
            val slop = CONTACT_SLOP_PX + CONTACT_SLOP_FRACTION * min(a.radius, b.radius)
            if (distance >= touching + slop || distance < 0.001f) return false
            val nx = dx / distance
            val ny = dy / distance
            val relativeX = a.velX - b.velX
            val relativeY = a.velY - b.velY
            val pressing = max(0f, a.velX * nx + a.velY * ny) + max(0f, -(b.velX * nx + b.velY * ny))
            touch.slidingSpeed = abs(relativeX * ny - relativeY * nx)
            touch.pressingSpeed = pressing

            // Drawn, they close whatever gap the engine left and then press in by how hard
            // they're driving into each other - the lighter one giving way more.
            val depth = min(PRESS_DEPTH_PER_REFERENCE_SPEED * a.intensity * pressing / REFERENCE_SPEED, MAX_PRESS_DEPTH) *
                min(a.radius, b.radius)
            val closing = distance - touching + depth
            val aWeight = b.radius / touching
            val bWeight = a.radius / touching
            a.targetOffsetX += nx * closing * aWeight
            a.targetOffsetY += ny * closing * aWeight
            b.targetOffsetX -= nx * closing * bWeight
            b.targetOffsetY -= ny * closing * bWeight

            // How deep a pressurized membrane dents under a contact force depends on its tension
            // (F ~ 4 pi T delta), not its size - Laplace's law trades the bigger balloon's lower
            // pressure for its larger radius - and the same latex blown to similar sizes carries
            // about the same tension. So both give way by the same amount (the smaller one shows
            // it more, relative to its size); a frozen one hardly gives way at all.
            val aCompliance = if (a.frozen) FROZEN_COMPLIANCE else 1f
            val bCompliance = if (b.frozen) FROZEN_COMPLIANCE else 1f
            val aShare = aCompliance / (aCompliance + bCompliance)
            a.addContact(b, aShare)
            b.addContact(a, 1f - aShare)
            return true
        }

        private fun wrapAngle(angle: Float): Float {
            var a = angle % TWO_PI
            if (a > Math.PI.toFloat()) a -= TWO_PI
            if (a < -Math.PI.toFloat()) a += TWO_PI
            return a
        }
    }
}
