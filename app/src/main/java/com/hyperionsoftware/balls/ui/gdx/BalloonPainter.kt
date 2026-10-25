package com.hyperionsoftware.balls.ui.gdx

import android.graphics.Color
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.MathUtils
import com.hyperionsoftware.balls.cosmetics.BalloonSticker
import com.hyperionsoftware.balls.cosmetics.ExhaustStyle
import kotlin.math.atan2
import kotlin.math.sin

// Everything a single balloon looks like, shared by GameRenderer and RaceRenderer so a Grand
// Prix racer is always exactly the same balloon as a classic-mode one. Takes plain values
// rather than a Blob/RaceBlob, since those two class hierarchies are deliberately separate.
// The body is a real balloon's teardrop with its tied knot (see Atlas.balloonPath), lit from a
// fixed direction, with a soft drop shadow, smoke left behind and a glow while sprinting; the
// string sway and the four-puff exhaust trail are the old Canvas renderer's.
class BalloonPainter(private val painter: Painter, private val particles: Particles) {
    class State {
        var x = 0f
        var y = 0f
        var radius = 0f
        var facingX = 0f
        var facingY = -1f
        var color = 0
        // 1 normally, faded while invisible.
        var alpha = 1f
        var exhaustPhase = 0f
        var isThrusting = false
        var isBoosting = false
        var isFrozen = false
        var isShielded = false
        var isSpeedBoosted = false
        var sticker: BalloonSticker = BalloonSticker.NONE
        var cordColor = 0
        var exhaustStyle: ExhaustStyle = ExhaustStyle.CLASSIC
        // The membrane's deformation, if it's being simulated (see BalloonBody) - null draws the
        // balloon at rest. x/y/facing are already the drawn ones (BalloonBody.drawnX/drawnHeading).
        var body: BalloonBody? = null
        // Static charge from rubbing, 0..1 - full draws the electric aura.
        var charge = 0f
        // Anything stable per balloon, so its aura's sparks don't all flicker in step.
        var seed = 0
    }

    private val atlas = painter.atlas
    private val auraRandom = java.util.Random()

    // Drawn for every balloon before any balloon body, so a shadow never lands on top of a
    // neighbouring balloon - it reads as resting on the floor, lifting the balloons off it.
    fun drawShadow(s: State) {
        painter.color(SHADOW_COLOR, 0.42f * s.alpha)
        painter.region(atlas.glow, s.x + s.radius * 0.28f, s.y + s.radius * 0.42f, s.radius * 2.5f, s.radius * 2.1f)
    }

    // Leaves a trail of drifting smoke behind a moving balloon, densest (and fiery) while
    // sprinting. Called once per frame per balloon near the screen - smoke for balloons nobody
    // can see would just be wasted work.
    fun emitSmoke(s: State, dt: Float) {
        if (!s.isThrusting || dt <= 0f) return
        val rate = if (s.isBoosting) 34f else 7f
        var count = rate * dt
        val backX = -s.facingX
        val backY = -s.facingY
        val tip = s.radius * (Atlas.KNOT_END_Y + 0.1f) * rearScale(s)
        while (count > 0f) {
            if (count < 1f && MathUtils.random() > count) break
            count -= 1f
            val spread = MathUtils.random(-0.35f, 0.35f)
            val speed = MathUtils.random(40f, 90f) + if (s.isBoosting) 80f else 0f
            val dirX = backX - backY * spread
            val dirY = backY + backX * spread
            val size = s.radius * (if (s.isBoosting) 0.55f else 0.4f)
            if (s.isBoosting) {
                particles.spawn(
                    Particles.Kind.SMOKE,
                    s.x + backX * tip, s.y + backY * tip,
                    dirX * speed, dirY * speed,
                    MathUtils.random(0.5f, 0.8f),
                    size, size * 2.6f,
                    BOOST_SMOKE_HOT, BOOST_SMOKE_COOL,
                    alpha = 0.5f * s.alpha,
                    drag = 2.2f
                )
            } else {
                particles.spawn(
                    Particles.Kind.SMOKE,
                    s.x + backX * tip, s.y + backY * tip,
                    dirX * speed, dirY * speed,
                    MathUtils.random(0.6f, 1f),
                    size, size * 2.2f,
                    CRUISE_SMOKE, CRUISE_SMOKE,
                    alpha = 0.22f * s.alpha,
                    drag = 1.8f
                )
            }
        }
    }

    fun drawBalloon(s: State) {
        drawExhaust(s)
        drawBody(s)
        drawSticker(s)
        if (s.isFrozen) {
            // An icy coat over the whole balloon, its own silhouette slightly enlarged.
            painter.color(FROZEN_COLOR, 0.4f * s.alpha)
            val size = s.radius * Atlas.BALLOON_SPRITE_SCALE * 1.05f
            balloonLayer(s, atlas.balloon, size, facingAngleDeg(s.facingX, s.facingY))
            painter.color(Color.WHITE, 0.35f * s.alpha)
            painter.region(atlas.sparkle, s.x - s.radius * 0.35f, s.y - s.radius * 0.3f, s.radius * 0.7f, s.radius * 0.7f, 15f)
        }
        if (s.isShielded) {
            val pulse = 0.6f + 0.4f * sin(s.exhaustPhase * 3f)
            painter.color(SHIELD_COLOR, 0.5f * pulse * s.alpha)
            painter.circleStroke(s.x, s.y, s.radius * 1.18f, 5f)
        }
        if (s.isSpeedBoosted) {
            val bolt = atlas.sticker(BalloonSticker.LIGHTNING)
            if (bolt != null) {
                // Same bolt shape and size as the old badge (radius * 0.55), upright.
                painter.color(SPEED_BADGE_COLOR, s.alpha)
                val size = Atlas.HALF_CELL * (s.radius * 0.55f / Atlas.STICKER_BAKE_SIZE)
                painter.region(bolt, s.x, s.y, size, size)
            }
        }
        drawString(s)
    }

    // Additive light on top of everything: a hot glow at the nozzle while sprinting, and a
    // soft cool aura while shielded.
    fun drawGlow(s: State) {
        if (s.isBoosting && s.isThrusting) {
            val pulse = 0.75f + 0.25f * sin(s.exhaustPhase * 22f)
            val edge = s.radius * Atlas.NECK_Y * rearScale(s)
            painter.color(BOOST_GLOW_COLOR, 0.7f * pulse * s.alpha)
            painter.glow(s.x - s.facingX * (edge + s.radius * 0.35f), s.y - s.facingY * (edge + s.radius * 0.35f), s.radius * 0.85f)
        }
        if (s.isShielded) {
            painter.color(SHIELD_COLOR, 0.22f * s.alpha)
            painter.glow(s.x, s.y, s.radius * 1.35f)
        }
        if (s.charge >= 1f) drawStaticAura(s)
    }

    // Fully charged: a flickering blue-violet halo and little arcs of lightning crawling over
    // the latex, hugging its (deformed) outline. The arcs are redrawn in a new place a dozen or
    // so times a second, each balloon on its own beat.
    private fun drawStaticAura(s: State) {
        val phase = s.exhaustPhase
        val flicker = 0.75f + 0.25f * sin(phase * 31f + s.seed) * sin(phase * 17f + s.seed * 0.7f)
        painter.color(AURA_COLOR, 0.55f * flicker * s.alpha)
        painter.glow(s.x, s.y, s.radius * 1.8f)
        painter.color(AURA_ARC_GLOW_COLOR, 0.3f * flicker * s.alpha)
        painter.glow(s.x, s.y, s.radius * 1.3f)

        val bucket = (phase * AURA_ARC_RATE).toInt()
        auraRandom.setSeed(bucket * 7919L + s.seed * 104729L)
        val rotation = facingAngleDeg(s.facingX, s.facingY) * MathUtils.degreesToRadians
        for (arc in 0 until AURA_ARCS) {
            val start = auraRandom.nextFloat() * MathUtils.PI2
            val span = 0.5f + auraRandom.nextFloat() * 0.6f
            var previousX = 0f
            var previousY = 0f
            for (i in 0..AURA_ARC_POINTS) {
                val local = start + span * i / AURA_ARC_POINTS
                val reach = s.radius * (outlineRadius(s, local) + 0.08f + (auraRandom.nextFloat() - 0.5f) * 0.16f)
                val world = local + rotation
                val px = s.x + MathUtils.cos(world) * reach
                val py = s.y + MathUtils.sin(world) * reach
                if (i > 0) {
                    painter.color(AURA_ARC_GLOW_COLOR, 0.6f * s.alpha)
                    painter.line(previousX, previousY, px, py, 8f)
                    painter.color(AURA_CORE_COLOR, 0.95f * s.alpha)
                    painter.line(previousX, previousY, px, py, 2.2f)
                }
                previousX = px
                previousY = py
            }
        }
    }

    // A few stray sparks jump off the latex as it charges up - rarely at first, constantly once
    // it's full - so the meter shows on the balloon itself, the player's and everyone else's.
    fun emitStaticSparks(s: State, dt: Float) {
        if (s.charge <= 0.2f || dt <= 0f) return
        val rate = if (s.charge >= 1f) 9f else 5f * s.charge * s.charge
        if (MathUtils.random() > rate * dt) return
        val local = MathUtils.random(MathUtils.PI2)
        val world = local + facingAngleDeg(s.facingX, s.facingY) * MathUtils.degreesToRadians
        val reach = s.radius * outlineRadius(s, local)
        val dirX = MathUtils.cos(world)
        val dirY = MathUtils.sin(world)
        val speed = MathUtils.random(60f, 160f)
        particles.spawn(
            Particles.Kind.SPARK,
            s.x + dirX * reach, s.y + dirY * reach,
            dirX * speed, dirY * speed,
            MathUtils.random(0.12f, 0.25f),
            MathUtils.random(10f, 18f), 3f,
            AURA_CORE_COLOR, AURA_COLOR,
            alpha = s.alpha,
            drag = 5f
        )
    }

    // The outline's reach at a local angle, in radii (see BalloonBody.outlineRadius).
    private fun outlineRadius(s: State, localAngle: Float): Float =
        s.body?.outlineRadius(localAngle) ?: BalloonBody.sample(BalloonBody.PROFILE, localAngle / MathUtils.PI2 * BalloonBody.SPOKES)

    // How far the neck and knot have moved with the latex behind them.
    private fun rearScale(s: State): Float = s.body?.shape?.get(BalloonBody.SPOKES / 4) ?: 1f

    // A real balloon's teardrop shape (see Atlas.balloonPath), its front - the wide, round end -
    // toward the direction of travel and its knot trailing behind.
    private fun drawBody(s: State) {
        val angle = facingAngleDeg(s.facingX, s.facingY)
        val size = s.radius * Atlas.BALLOON_SPRITE_SCALE
        painter.color(s.color, s.alpha)
        balloonLayer(s, atlas.balloon, size, angle)

        // The pre-rotated shading variant nearest this angle (see Atlas.drawLightingVariant), so
        // the light stays put as the balloon turns...
        val variants = Atlas.LIGHTING_VARIANTS
        val variant = Math.round(((angle % 360f + 360f) % 360f) / (360f / variants)) % variants
        painter.color(Color.WHITE, s.alpha)
        balloonLayer(s, atlas.lighting[variant], size, angle)
        // ...and the highlights on top, never rotated, so they glide along with no steps at all.
        val highlightSize = s.radius * Atlas.HIGHLIGHT_EXTENT * 2f
        val body = s.body
        if (body != null) {
            painter.deformed(atlas.highlight, s.x, s.y, highlightSize, 0f, body.shape, angle)
        } else {
            painter.region(atlas.highlight, s.x, s.y, highlightSize, highlightSize)
        }
    }

    // One layer of the balloon's body, stretched with its latex when it's being simulated.
    private fun balloonLayer(s: State, region: TextureRegion, size: Float, angle: Float) {
        val body = s.body
        if (body != null) {
            painter.deformed(region, s.x, s.y, size, angle, body.shape, angle)
        } else {
            painter.region(region, s.x, s.y, size, size, angle)
        }
    }

    private fun drawSticker(s: State) {
        val region = atlas.sticker(s.sticker) ?: return
        // Printed on the balloon's own surface: rotates with the body, at the same size the old
        // renderer used (radius * 0.5), shifted a touch toward the wide front where a print
        // naturally sits on a teardrop.
        val size = Atlas.HALF_CELL * (s.radius * 0.5f / Atlas.STICKER_BAKE_SIZE)
        painter.color(Color.WHITE, (235f / 255f) * s.alpha)
        val offset = s.radius * 0.1f
        painter.region(region, s.x + s.facingX * offset, s.y + s.facingY * offset, size, size, facingAngleDeg(s.facingX, s.facingY))
    }

    private fun drawString(s: State) {
        // Tied on at the end of the knot.
        val backX = -s.facingX
        val backY = -s.facingY
        val knot = s.radius * Atlas.KNOT_END_Y * rearScale(s)
        val startX = s.x + backX * knot
        val startY = s.y + backY * knot
        val length = s.radius * 0.9f
        val sway = sin(s.exhaustPhase * 2.3f) * s.radius * 0.15f
        val controlX = startX + backX * length * 0.5f + sway
        val controlY = startY + backY * length * 0.5f
        val endX = startX + backX * length + sway * 0.6f
        val endY = startY + backY * length

        painter.color(s.cordColor, 0.6f * s.alpha)
        // The same quadratic curve as before, as a few short segments.
        var previousX = startX
        var previousY = startY
        for (i in 1..STRING_SEGMENTS) {
            val t = i / STRING_SEGMENTS.toFloat()
            val inv = 1f - t
            val px = inv * inv * startX + 2f * inv * t * controlX + t * t * endX
            val py = inv * inv * startY + 2f * inv * t * controlY + t * t * endY
            painter.line(previousX, previousY, px, py, 2f)
            previousX = px
            previousY = py
        }
    }

    // A trail of soft, shrinking puffs that wobble like real smoke - the old renderer's exact
    // shape, with the blurred circles now the atlas's pre-blurred puff (or the player's own
    // ExhaustStyle shape).
    private fun drawExhaust(s: State) {
        if (!s.isThrusting && !s.isBoosting) return
        val boosting = s.isBoosting
        val backX = -s.facingX
        val backY = -s.facingY
        val perpX = -backY
        val perpY = backX
        val pulse = 0.7f + 0.3f * sin(s.exhaustPhase * (if (boosting) 22f else 14f))
        val intensity = if (boosting) 1.7f else 1f
        // The air escapes from the neck.
        val edgeRadius = s.radius * Atlas.NECK_Y * rearScale(s)
        val trailLength = s.radius * (1.4f + 0.5f * pulse) * intensity
        val baseAlphaFraction = if (boosting) 0.75f else 0.5f
        val classic = s.exhaustStyle == ExhaustStyle.CLASSIC
        val region = atlas.exhaustPuff(s.exhaustStyle)
        val color = if (boosting) EXHAUST_BOOST_COLOR else EXHAUST_CRUISE_COLOR

        for (i in 0 until EXHAUST_PUFFS) {
            val fraction = i / (EXHAUST_PUFFS - 1).toFloat()
            val distance = edgeRadius + trailLength * fraction
            val wobble = sin(s.exhaustPhase * (5f + i * 2.1f) + i * 1.9f) * s.radius * 0.22f * fraction
            val puffX = s.x + backX * distance + perpX * wobble
            val puffY = s.y + backY * distance + perpY * wobble
            val puffRadius = (s.radius * (0.21f + fraction * 0.24f) * intensity).coerceAtLeast(1f)
            val alpha = baseAlphaFraction * pulse * (1f - fraction * 0.7f) * s.alpha
            if (classic) {
                // The glow fades out gradually all the way from its center, so it's drawn larger
                // and stronger to cover about what the old blurred solid circle did.
                painter.color(color, (alpha * 1.7f).coerceAtMost(1f))
                painter.glow(puffX, puffY, puffRadius * 1.7f)
            } else {
                painter.color(color, alpha)
                val size = Atlas.HALF_CELL * (puffRadius / Atlas.PUFF_BAKE_RADIUS)
                painter.region(region, puffX, puffY, size, size)
            }
        }
    }

    companion object {
        // How far a balloon's drawing reaches past its center, as a multiple of its radius:
        // the sprinting exhaust trail is the longest part (see drawExhaust), the shadow next.
        const val VISUAL_EXTENT = 5f

        private const val EXHAUST_PUFFS = 4
        private const val STRING_SEGMENTS = 6
        private val SHADOW_COLOR = Color.parseColor("#000000")
        private val EXHAUST_CRUISE_COLOR = Color.parseColor("#B3E5FC")
        private val EXHAUST_BOOST_COLOR = Color.parseColor("#FF7043")
        private val CRUISE_SMOKE = Color.parseColor("#CFE3EE")
        private val BOOST_SMOKE_HOT = Color.parseColor("#FFAB40")
        private val BOOST_SMOKE_COOL = Color.parseColor("#5D6670")
        private val BOOST_GLOW_COLOR = Color.parseColor("#FF8A3D")
        private val FROZEN_COLOR = Color.parseColor("#B3E5FC")
        private val SHIELD_COLOR = Color.parseColor("#4FC3F7")
        private val SPEED_BADGE_COLOR = Color.parseColor("#FFEB3B")
        private val AURA_COLOR = Color.parseColor("#5C6BFF")
        private val AURA_ARC_GLOW_COLOR = Color.parseColor("#448AFF")
        private val AURA_CORE_COLOR = Color.parseColor("#E1F5FE")
        private const val AURA_ARCS = 3
        private const val AURA_ARC_POINTS = 6
        private const val AURA_ARC_RATE = 14f

        fun facingAngleDeg(facingX: Float, facingY: Float): Float =
            atan2(facingY, facingX) * MathUtils.radiansToDegrees + 90f

        fun darken(color: Int, factor: Float): Int {
            val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
            val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
            val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
            return Color.argb(Color.alpha(color), r, g, b)
        }
    }
}
