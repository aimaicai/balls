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
        // Knocked out by a static discharge: scorched and still crackling.
        var isStunned = false
        var isShielded = false
        var isSpeedBoosted = false
        var isInvisible = false
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
        // How fast it's moving (world units per second), so what it gives off can travel along
        // with it for a moment instead of being left behind at once.
        var velocityX = 0f
        var velocityY = 0f
    }

    private val atlas = painter.atlas
    private val auraRandom = java.util.Random()

    // Drawn for every balloon before any balloon body, so a shadow never lands on top of a
    // neighbouring balloon - it reads as resting on the floor, lifting the balloons off it.
    fun drawShadow(s: State) {
        painter.color(SHADOW_COLOR, 0.42f * s.alpha)
        painter.glowEllipse(s.x + s.radius * 0.28f, s.y + s.radius * 0.42f, s.radius * 1.25f, s.radius * 1.05f)
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
        if (s.isSpeedBoosted) drawAfterimages(s)
        drawExhaust(s)
        drawBody(s)
        drawSticker(s)
        if (s.isFrozen) drawIce(s)
        if (s.isStunned) {
            // Scorched by the bolt: a sooty, flickering coat over the latex.
            val flicker = 0.8f + 0.2f * sin(s.exhaustPhase * 47f + s.seed)
            painter.color(SCORCH_COLOR, 0.38f * flicker * s.alpha)
            balloonLayer(s, atlas.balloon, s.radius * Atlas.BALLOON_SPRITE_SCALE, facingAngleDeg(s.facingX, s.facingY))
        }
        if (s.isShielded) drawShieldBubble(s)
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
        if (s.isShielded) drawShieldLight(s)
        if (s.isFrozen) {
            // Cold radiating off the ice.
            painter.color(FROZEN_GLOW_COLOR, 0.2f * s.alpha)
            painter.glow(s.x, s.y, s.radius * 1.45f)
        }
        if (s.isSpeedBoosted) drawSpeedLines(s)
        if (s.isInvisible) {
            // Barely there: just a faint shimmer where the light bends around it.
            val shimmer = 0.5f + 0.5f * sin(s.exhaustPhase * 9f + s.seed)
            painter.color(INVISIBLE_SHIMMER_COLOR, (0.05f + 0.06f * shimmer) * s.alpha)
            balloonLayer(s, atlas.balloon, s.radius * Atlas.BALLOON_SPRITE_SCALE * 1.04f, facingAngleDeg(s.facingX, s.facingY))
        }
        if (s.charge >= 1f) drawStaticAura(s)
        if (s.isStunned) {
            // The bolt still running through it: a cold flicker and lightning racing over the
            // latex, faster and wilder than a charge's aura.
            val flicker = 0.6f + 0.4f * sin(s.exhaustPhase * 53f + s.seed)
            painter.color(STUN_GLOW_COLOR, 0.35f * flicker * s.alpha)
            painter.glow(s.x, s.y, s.radius * 1.5f)
            drawArcs(s, STUN_ARCS, STUN_ARC_RATE, 0.22f, STUN_GLOW_COLOR, Color.WHITE, 1)
        }
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

        drawArcs(s, AURA_ARCS, AURA_ARC_RATE, 0.16f, AURA_ARC_GLOW_COLOR, AURA_CORE_COLOR, 0)
    }

    // Little arcs of lightning crawling over the balloon, hugging its (deformed) outline, redrawn
    // in a new place `rate` times a second - each balloon on its own beat. `jitter` is how far,
    // in radii, they wander off the outline; `salt` keeps two kinds of arcs on one balloon apart.
    private fun drawArcs(s: State, count: Int, rate: Float, jitter: Float, glowColor: Int, coreColor: Int, salt: Int) {
        val bucket = (s.exhaustPhase * rate).toInt()
        auraRandom.setSeed(bucket * 7919L + s.seed * 104729L + salt * 15485863L)
        val rotation = facingAngleDeg(s.facingX, s.facingY) * MathUtils.degreesToRadians
        for (arc in 0 until count) {
            val start = auraRandom.nextFloat() * MathUtils.PI2
            val span = 0.5f + auraRandom.nextFloat() * 0.6f
            // A kink every dozen pixels or so reads as jagged at any size - more than that on a
            // small balloon is just more sprites.
            val points = (span * s.radius / ARC_KINK_SPACING).toInt().coerceIn(2, AURA_ARC_POINTS)
            var previousX = 0f
            var previousY = 0f
            for (i in 0..points) {
                val local = start + span * i / points
                val reach = s.radius * (outlineRadius(s, local) + jitter / 2f + (auraRandom.nextFloat() - 0.5f) * jitter)
                val world = local + rotation
                val px = s.x + MathUtils.cos(world) * reach
                val py = s.y + MathUtils.sin(world) * reach
                if (i > 0) {
                    painter.color(glowColor, 0.6f * s.alpha)
                    painter.line(previousX, previousY, px, py, 8f)
                    painter.color(coreColor, 0.95f * s.alpha)
                    painter.line(previousX, previousY, px, py, 2.2f)
                }
                previousX = px
                previousY = py
            }
        }
    }

    // What a balloon gives off because of its state, once per frame while it's on screen: cold
    // vapor and flakes of ice while frozen, wisps of fog while invisible, and the static sparks.
    fun emitStatusParticles(s: State, dt: Float) {
        if (dt <= 0f) return
        if (s.isFrozen) emitFrost(s, dt)
        if (s.isInvisible) emitWisps(s, dt)
        emitStaticSparks(s, dt)
    }

    // Frozen: cold vapor rolling off the ice and the odd flake breaking away.
    private fun emitFrost(s: State, dt: Float) {
        if (MathUtils.random() < FROST_VAPOR_RATE * dt) {
            pickOutlinePoint(s, 0.9f)
            particles.spawn(
                Particles.Kind.MIST, pointX, pointY,
                pointDirX * 18f, pointDirY * 18f + 14f,
                MathUtils.random(0.7f, 1.1f),
                s.radius * 0.25f, s.radius * 0.7f,
                FROST_VAPOR_COLOR,
                alpha = 0.32f * s.alpha,
                drag = 1f
            )
        }
        if (MathUtils.random() < FROST_FLAKE_RATE * dt) {
            pickOutlinePoint(s, 1f)
            val speed = MathUtils.random(25f, 60f)
            particles.spawn(
                Particles.Kind.FLAKE, pointX, pointY,
                pointDirX * speed, pointDirY * speed,
                MathUtils.random(0.5f, 0.9f),
                MathUtils.random(9f, 15f), 4f,
                Color.WHITE, FROZEN_COLOR,
                alpha = 0.8f * s.alpha,
                drag = 2f
            )
        }
    }

    // Invisible: thin wisps of fog trailing off it, faint enough not to give it away.
    private fun emitWisps(s: State, dt: Float) {
        if (MathUtils.random() > INVISIBLE_WISP_RATE * dt) return
        pickOutlinePoint(s, 0.8f)
        particles.spawn(
            Particles.Kind.MIST, pointX, pointY,
            pointDirX * 20f + s.velocityX * 0.3f, pointDirY * 20f + s.velocityY * 0.3f,
            MathUtils.random(0.6f, 1f),
            s.radius * 0.3f, s.radius * 0.9f,
            FOG_COLOR,
            alpha = 0.18f,
            drag = 1.5f
        )
    }

    // A random point on the balloon's (deformed) outline, `inset` of the way out from its
    // center, into pointX/pointY - and the outward direction there into pointDirX/pointDirY.
    private fun pickOutlinePoint(s: State, inset: Float) {
        val local = MathUtils.random(MathUtils.PI2)
        val world = local + facingAngleDeg(s.facingX, s.facingY) * MathUtils.degreesToRadians
        val reach = s.radius * outlineRadius(s, local) * inset
        pointDirX = MathUtils.cos(world)
        pointDirY = MathUtils.sin(world)
        pointX = s.x + pointDirX * reach
        pointY = s.y + pointDirY * reach
    }

    private var pointX = 0f
    private var pointY = 0f
    private var pointDirX = 0f
    private var pointDirY = 0f

    // A few stray sparks jump off the latex as it charges up - rarely at first, constantly once
    // it's full - so the meter shows on the balloon itself, the player's and everyone else's.
    private fun emitStaticSparks(s: State, dt: Float) {
        if (s.isStunned) emitScorch(s, dt)
        if (s.charge <= 0.2f && !s.isStunned) return
        val rate = if (s.isStunned) 16f else if (s.charge >= 1f) 9f else 5f * s.charge * s.charge
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

    // A stunned balloon smoulders: thin wisps of smoke curling off where the bolt went through.
    private fun emitScorch(s: State, dt: Float) {
        if (MathUtils.random() > SCORCH_SMOKE_RATE * dt) return
        val local = MathUtils.random(MathUtils.PI2)
        val world = local + facingAngleDeg(s.facingX, s.facingY) * MathUtils.degreesToRadians
        val reach = s.radius * outlineRadius(s, local) * 0.8f
        particles.spawn(
            Particles.Kind.SMOKE,
            s.x + MathUtils.cos(world) * reach, s.y + MathUtils.sin(world) * reach,
            MathUtils.cos(world) * 25f, MathUtils.sin(world) * 25f - 30f,
            MathUtils.random(0.5f, 0.9f),
            s.radius * 0.25f, s.radius * 0.8f,
            SCORCH_SMOKE_COLOR,
            alpha = 0.45f * s.alpha,
            drag = 1.2f
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
            painter.deformed(atlas.highlight, s.x, s.y, highlightSize, 0f, body.shape, angle, stride = fanStride(s))
        } else {
            painter.region(atlas.highlight, s.x, s.y, highlightSize, highlightSize)
        }
    }

    // One layer of the balloon's body (anything balloon-shaped), stretched with its latex when
    // it's being simulated - and either way drawn as a fan hugging the silhouette, not the
    // sprite's whole square: on a big balloon, the corners alone were thousands of transparent
    // pixels blended per layer for nothing. Centered on the balloon unless given (x, y).
    private fun balloonLayer(s: State, region: TextureRegion, size: Float, angle: Float, x: Float = s.x, y: Float = s.y) {
        val stride = fanStride(s)
        val reach = if (stride == 1) BalloonBody.SPRITE_REACH else BalloonBody.COARSE_SPRITE_REACH
        painter.deformed(region, x, y, size, angle, s.body?.shape ?: REST_SHAPE, angle, reach, stride)
    }

    // The balloon's silhouette (or the frost coat), `scale` times its size, in the current color -
    // for effects washing over the whole balloon (see PowerUpFx).
    fun drawSilhouette(s: State, scale: Float = 1f, frost: Boolean = false) {
        balloonLayer(s, if (frost) atlas.frost else atlas.balloon, s.radius * Atlas.BALLOON_SPRITE_SCALE * scale, facingAngleDeg(s.facingX, s.facingY))
    }

    // Frozen solid: a pale icy tint over the latex, the crackled coat of ice on top - a touch
    // bigger than the balloon, like a shell around it - and a glint now and then.
    private fun drawIce(s: State) {
        val angle = facingAngleDeg(s.facingX, s.facingY)
        val size = s.radius * Atlas.BALLOON_SPRITE_SCALE
        painter.color(FROZEN_COLOR, 0.38f * s.alpha)
        balloonLayer(s, atlas.balloon, size * 1.04f, angle)
        painter.color(Color.WHITE, 0.75f * s.alpha)
        balloonLayer(s, atlas.frost, size * 1.04f, angle)
        val glint = sin(s.exhaustPhase * 2.6f + s.seed)
        if (glint > 0.4f) {
            val strength = (glint - 0.4f) / 0.6f
            painter.color(Color.WHITE, 0.8f * strength * s.alpha)
            val glintSize = s.radius * (0.35f + 0.25f * strength)
            painter.region(atlas.sparkle, s.x - s.radius * 0.38f, s.y - s.radius * 0.42f, glintSize, glintSize, 15f + strength * 30f)
        }
    }

    // The shield's bubble, the part that covers what's behind it: a clear sphere, a little
    // denser toward its rim.
    private fun drawShieldBubble(s: State) {
        val r = s.radius * SHIELD_SCALE
        painter.ring(s.x, s.y, r * 0.7f, r, SHIELD_COLOR, 0f, SHIELD_COLOR, 0.22f * s.alpha)
    }

    // ...and its light: the honeycomb shimmering over it, slowly turning, a bright rim and a glint
    // running around it.
    private fun drawShieldLight(s: State) {
        val r = s.radius * SHIELD_SCALE
        val pulse = 0.75f + 0.25f * sin(s.exhaustPhase * 3f)
        painter.color(SHIELD_COLOR, 0.32f * pulse * s.alpha)
        val hexSize = r * 2f / Atlas.SHIELD_RADIUS_FRACTION
        painter.region(atlas.hexShield, s.x, s.y, hexSize, hexSize, s.exhaustPhase * 8f)
        painter.color(SHIELD_RIM_COLOR, 0.55f * pulse * s.alpha)
        painter.circleStroke(s.x, s.y, r, 3f)
        val glintAngle = s.exhaustPhase * 1.7f + s.seed
        val gx = s.x + MathUtils.cos(glintAngle) * r
        val gy = s.y + MathUtils.sin(glintAngle) * r
        painter.color(Color.WHITE, 0.55f * s.alpha)
        painter.glow(gx, gy, 14f + s.radius * 0.1f)
        painter.region(atlas.sparkle, gx, gy, 20f + s.radius * 0.2f, 20f + s.radius * 0.2f, glintAngle * 40f)
    }

    // Sped up: fading copies of the balloon left behind along its path, tinted with the boost.
    private fun drawAfterimages(s: State) {
        val angle = facingAngleDeg(s.facingX, s.facingY)
        val size = s.radius * Atlas.BALLOON_SPRITE_SCALE
        for (k in AFTERIMAGES downTo 1) {
            val back = s.radius * 0.6f * k
            painter.color(mix(s.color, SPEED_BADGE_COLOR, 0.45f), 0.26f / k * s.alpha)
            balloonLayer(s, atlas.balloon, size * (1f - 0.06f * k), angle, s.x - s.facingX * back, s.y - s.facingY * back)
        }
    }

    // ...and streaks of light rushing past it on both sides.
    private fun drawSpeedLines(s: State) {
        val sideX = -s.facingY
        val sideY = s.facingX
        for (i in 0 until SPEED_LINES) {
            val flow = (s.exhaustPhase * 2.2f + i * 0.37f) % 1f
            val lateral = s.radius * (if (i % 2 == 0) 1f else -1f) * (0.75f + 0.12f * (i / 2))
            val along = s.radius * (0.9f - 2.8f * flow)
            val length = s.radius * 0.9f
            val x0 = s.x + sideX * lateral + s.facingX * along
            val y0 = s.y + sideY * lateral + s.facingY * along
            painter.color(SPEED_BADGE_COLOR, 0.55f * sin(flow * MathUtils.PI) * s.alpha)
            painter.line(x0, y0, x0 - s.facingX * length, y0 - s.facingY * length, 3f)
        }
    }

    // A balloon a few dozen pixels across can't show its latex's finest ripples anyway: half the
    // spokes draw it just the same, for half the triangles - and that's most balloons on screen.
    private fun fanStride(s: State): Int = if (s.radius < COARSE_FAN_RADIUS) 2 else 1

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
        val segments = if (s.radius < COARSE_FAN_RADIUS) STRING_SEGMENTS / 2 else STRING_SEGMENTS
        for (i in 1..segments) {
            val t = i / segments.toFloat()
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
        private val REST_SHAPE = FloatArray(BalloonBody.SPOKES) { 1f }
        private const val COARSE_FAN_RADIUS = 90f
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
        private val FROZEN_GLOW_COLOR = Color.parseColor("#80DEEA")
        private val FROST_VAPOR_COLOR = Color.parseColor("#E1F5FE")
        private const val FROST_VAPOR_RATE = 7f
        private const val FROST_FLAKE_RATE = 4f
        private val FOG_COLOR = Color.parseColor("#B8B0D0")
        private val INVISIBLE_SHIMMER_COLOR = Color.parseColor("#D1C4E9")
        private const val INVISIBLE_WISP_RATE = 6f
        private val SHIELD_COLOR = Color.parseColor("#4FC3F7")
        private val SHIELD_RIM_COLOR = Color.parseColor("#B3E5FC")
        // The shield bubble's radius, in balloon radii - wide enough to clear the knot.
        const val SHIELD_SCALE = 1.22f
        private const val AFTERIMAGES = 3
        private const val SPEED_LINES = 6
        private val SPEED_BADGE_COLOR = Color.parseColor("#FFEB3B")
        private val AURA_COLOR = Color.parseColor("#5C6BFF")
        private val AURA_ARC_GLOW_COLOR = Color.parseColor("#448AFF")
        private val AURA_CORE_COLOR = Color.parseColor("#E1F5FE")
        private const val AURA_ARCS = 3
        private const val AURA_ARC_POINTS = 6
        private const val ARC_KINK_SPACING = 12f
        private const val AURA_ARC_RATE = 14f
        private val SCORCH_COLOR = Color.parseColor("#1A1420")
        private val SCORCH_SMOKE_COLOR = Color.parseColor("#4A4650")
        private const val SCORCH_SMOKE_RATE = 10f
        private val STUN_GLOW_COLOR = Color.parseColor("#7C8CFF")
        private const val STUN_ARCS = 4
        private const val STUN_ARC_RATE = 24f

        fun facingAngleDeg(facingX: Float, facingY: Float): Float =
            atan2(facingY, facingX) * MathUtils.radiansToDegrees + 90f

        // `from` blended toward `to` by t (0..1), keeping `from`'s alpha.
        fun mix(from: Int, to: Int, t: Float): Int = Color.argb(
            Color.alpha(from),
            (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt(),
            (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt(),
            (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt()
        )

        fun darken(color: Int, factor: Float): Int {
            val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
            val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
            val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
            return Color.argb(Color.alpha(color), r, g, b)
        }
    }
}
