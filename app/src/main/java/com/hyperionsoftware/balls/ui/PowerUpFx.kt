package com.hyperionsoftware.balls.ui

import android.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.hyperionsoftware.balls.game.PowerUpType
import com.hyperionsoftware.balls.ui.gdx.Atlas
import com.hyperionsoftware.balls.ui.gdx.BalloonPainter
import com.hyperionsoftware.balls.ui.gdx.Painter
import com.hyperionsoftware.balls.ui.gdx.Particles
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Classic mode's power-up animations: what a balloon looks like for a moment after picking each
// one up - frost creeping over it, fog swirling around it, a shield bubble forming - and what
// each item looks like going off: REPEL's shockwave, FREEZE's burst of cold, HOOK's line. The
// lasting looks (frozen, shielded, sped up, invisible) are BalloonPainter's.
//
// Everything here is short-lived, follows its balloon by id while it's on screen and simply
// isn't drawn while it's not, and is built from the same few sprites as the rest of the frame -
// a handful of extra quads per effect, nothing full-screen.
class PowerUpFx(
    private val painter: Painter,
    private val particles: Particles,
    private val balloons: BalloonPainter,
    // The balloon with this id as it's drawn this frame, or null if it's gone or off screen.
    // Always the same shared object, so it's only good until the next call.
    private val stateOf: (Int) -> BalloonPainter.State?
) {
    // A balloon taking in a power-up it just picked up, or setting off a SPEED/INVISIBILITY it
    // carried (`used`).
    private class Aura(val blobId: Int, val type: PowerUpType, val used: Boolean, val duration: Float, val seed: Float) {
        var elapsed = 0f
    }

    // A REPEL/FREEZE's wave, from where it was set off out to its real reach.
    private class Nova(val type: PowerUpType, val x: Float, val y: Float, val sourceRadius: Float, val range: Float, val duration: Float) {
        var elapsed = 0f
    }

    // An item catching one balloon: REPEL's shove, FREEZE's beam of cold, HOOK's line. `from`
    // is where the target was, `to` where the item left it.
    private class Hit(
        val type: PowerUpType,
        val sourceId: Int,
        val targetId: Int,
        val fromX: Float,
        val fromY: Float,
        var toX: Float,
        var toY: Float,
        val targetRadius: Float,
        val targetColor: Int,
        val duration: Float
    ) {
        var elapsed = 0f
    }

    // A fading flash of light.
    private class Burst(val x: Float, val y: Float, val radius: Float, val color: Int, val duration: Float) {
        var elapsed = 0f
    }

    private val auras = ArrayList<Aura>()
    private val novas = ArrayList<Nova>()
    private val hits = ArrayList<Hit>()
    private val bursts = ArrayList<Burst>()
    private val atlas = painter.atlas

    fun clear() {
        auras.clear()
        novas.clear()
        hits.clear()
        bursts.clear()
    }

    fun collected(blobId: Int, type: PowerUpType) {
        val s = stateOf(blobId) ?: return
        val permanent = type == PowerUpType.SPEED_UP || type == PowerUpType.AGILITY_UP || type == PowerUpType.POTENCY_UP
        auras.add(Aura(blobId, type, false, if (permanent) 1.3f else 1f, MathUtils.random(MathUtils.PI2)))
        val color = GameRenderer.powerUpColor(type)
        when {
            permanent -> motes(s, color, 12, rising = true)
            type == PowerUpType.GROWTH -> motes(s, color, 10, rising = true)
            type == PowerUpType.FREEZE -> flakes(s.x, s.y, s.radius * 1.2f, 8, 120f, 260f)
            type == PowerUpType.INVISIBILITY -> fog(s, 6, 0.4f)
            else -> {}
        }
    }

    // `x`/`y` is where the source was when it set the item off, `range` the item's real reach.
    fun used(blobId: Int, type: PowerUpType, x: Float, y: Float, sourceRadius: Float, range: Float) {
        when (type) {
            PowerUpType.REPEL -> {
                novas.add(Nova(type, x, y, sourceRadius, range, 0.55f))
                bursts.add(Burst(x, y, sourceRadius * 2f, GameRenderer.powerUpColor(type), 0.3f))
                radialSparks(x, y, sourceRadius, 26, REPEL_LIGHT_COLOR, GameRenderer.powerUpColor(type), 500f, 950f)
            }
            PowerUpType.FREEZE -> {
                novas.add(Nova(type, x, y, sourceRadius, range, 0.7f))
                bursts.add(Burst(x, y, sourceRadius * 2.2f, ICE_LIGHT_COLOR, 0.35f))
                flakes(x, y, sourceRadius, 22, 380f, 820f)
                coldPuffs(x, y, sourceRadius, range)
            }
            PowerUpType.HOOK -> bursts.add(Burst(x, y, sourceRadius * 1.6f, GameRenderer.powerUpColor(type), 0.25f))
            PowerUpType.SPEED -> {
                val s = stateOf(blobId) ?: return
                auras.add(Aura(blobId, type, true, 0.6f, 0f))
                // A kick of light out the back.
                for (i in 0 until 14) {
                    val spread = MathUtils.random(-0.5f, 0.5f)
                    val dirX = -s.facingX - s.facingY * spread
                    val dirY = -s.facingY + s.facingX * spread
                    val speed = MathUtils.random(300f, 650f)
                    particles.spawn(
                        Particles.Kind.SPARK, s.x - s.facingX * s.radius, s.y - s.facingY * s.radius,
                        dirX * speed, dirY * speed,
                        MathUtils.random(0.25f, 0.45f),
                        MathUtils.random(14f, 24f), 3f,
                        Color.WHITE, SPEED_COLOR,
                        drag = 4f
                    )
                }
            }
            PowerUpType.INVISIBILITY -> {
                val s = stateOf(blobId) ?: return
                auras.add(Aura(blobId, type, true, 0.8f, MathUtils.random(MathUtils.PI2)))
                fog(s, 12, 0.55f)
            }
            else -> {}
        }
    }

    fun hit(type: PowerUpType, sourceId: Int, targetId: Int, fromX: Float, fromY: Float, toX: Float, toY: Float, targetRadius: Float, targetColor: Int) {
        val duration = when (type) {
            PowerUpType.REPEL -> 0.45f
            PowerUpType.FREEZE -> 0.5f
            else -> 0.75f
        }
        hits.add(Hit(type, sourceId, targetId, fromX, fromY, toX, toY, targetRadius, targetColor, duration))
        when (type) {
            PowerUpType.REPEL -> radialSparks(toX, toY, targetRadius * 0.8f, 10, Color.WHITE, GameRenderer.powerUpColor(type), 200f, 420f)
            PowerUpType.FREEZE -> {
                flakes(toX, toY, targetRadius, 10, 120f, 320f)
                bursts.add(Burst(toX, toY, targetRadius * 1.8f, ICE_LIGHT_COLOR, 0.4f))
            }
            PowerUpType.HOOK -> radialSparks(toX, toY, targetRadius * 0.8f, 12, HOOK_LIGHT_COLOR, GameRenderer.powerUpColor(type), 180f, 400f)
            else -> {}
        }
    }

    // A frozen balloon thawing out: its ice cracking off in shards.
    fun thawed(s: BalloonPainter.State) {
        flakes(s.x, s.y, s.radius, 12, 160f, 380f)
        for (i in 0 until 8) {
            val angle = MathUtils.random(MathUtils.PI2)
            val speed = MathUtils.random(150f, 320f)
            particles.spawn(
                Particles.Kind.CONFETTI,
                s.x + MathUtils.cos(angle) * s.radius * 0.8f, s.y + MathUtils.sin(angle) * s.radius * 0.8f,
                MathUtils.cos(angle) * speed, MathUtils.sin(angle) * speed,
                MathUtils.random(0.35f, 0.6f),
                MathUtils.random(8f, 14f), 4f,
                ICE_SHARD_COLOR,
                alpha = 0.85f,
                drag = 3f
            )
        }
        bursts.add(Burst(s.x, s.y, s.radius * 1.6f, ICE_LIGHT_COLOR, 0.3f))
    }

    // How see-through the balloon with this id should be drawn right now, on top of its own
    // alpha: fading in and out of view as an invisibility takes hold of it.
    fun alphaFor(blobId: Int): Float {
        var alpha = 1f
        for (aura in auras) {
            if (aura.blobId != blobId || aura.type != PowerUpType.INVISIBILITY) continue
            val t = aura.elapsed / aura.duration
            val flicker = 0.7f + 0.3f * sin(aura.elapsed * 47f)
            alpha = min(alpha, 1f - (if (aura.used) 0.4f else 0.6f) * bell(t) * flicker)
        }
        return alpha
    }

    fun update(dt: Float) {
        if (dt <= 0f) return
        val auraIterator = auras.iterator()
        while (auraIterator.hasNext()) {
            val aura = auraIterator.next()
            aura.elapsed += dt
            if (aura.elapsed >= aura.duration) auraIterator.remove()
        }
        val novaIterator = novas.iterator()
        while (novaIterator.hasNext()) {
            val nova = novaIterator.next()
            nova.elapsed += dt
            if (nova.elapsed >= nova.duration) novaIterator.remove()
        }
        val hitIterator = hits.iterator()
        while (hitIterator.hasNext()) {
            val hit = hitIterator.next()
            hit.elapsed += dt
            if (hit.elapsed >= hit.duration) hitIterator.remove()
        }
        val burstIterator = bursts.iterator()
        while (burstIterator.hasNext()) {
            val burst = burstIterator.next()
            burst.elapsed += dt
            if (burst.elapsed >= burst.duration) burstIterator.remove()
        }
    }

    // What covers the balloons (alpha-blended): frost and fog over them, the trail a shoved or
    // yanked balloon leaves, HOOK's line.
    fun drawOver(left: Float, top: Float, right: Float, bottom: Float) {
        for (aura in auras) {
            val t = aura.elapsed / aura.duration
            when (aura.type) {
                PowerUpType.FREEZE -> {
                    val s = stateOf(aura.blobId) ?: continue
                    // The frost creeps over the latex, holds a moment, then melts back.
                    val a = ramp(t, 0f, 0.3f) * (1f - ramp(t, 0.6f, 1f))
                    painter.color(ICE_TINT_COLOR, 0.32f * a * s.alpha)
                    balloons.drawSilhouette(s, 1.04f)
                    painter.color(Color.WHITE, 0.85f * a * s.alpha)
                    balloons.drawSilhouette(s, 1.04f, frost = true)
                }
                PowerUpType.INVISIBILITY -> {
                    val s = stateOf(aura.blobId) ?: continue
                    drawFogSwirl(s, t, aura.seed, if (aura.used) 0.8f else 0.6f)
                }
                else -> {}
            }
        }
        for (hit in hits) {
            if (hit.type == PowerUpType.FREEZE) continue
            followTarget(hit)
            if (!touches(hit.fromX, hit.fromY, hit.toX, hit.toY, hit.targetRadius * 2f, left, top, right, bottom)) continue
            val t = hit.elapsed / hit.duration
            drawDragTrail(hit, t)
            if (hit.type == PowerUpType.HOOK) drawHookLine(hit, t)
        }
    }

    // The light (additive).
    fun drawLight(left: Float, top: Float, right: Float, bottom: Float) {
        for (aura in auras) {
            val s = stateOf(aura.blobId) ?: continue
            drawAuraLight(aura, s, aura.elapsed / aura.duration)
        }
        for (nova in novas) {
            if (!touches(nova.x, nova.y, nova.x, nova.y, nova.range, left, top, right, bottom)) continue
            drawNova(nova, nova.elapsed / nova.duration)
        }
        for (hit in hits) {
            if (!touches(hit.fromX, hit.fromY, hit.toX, hit.toY, hit.targetRadius * 2f, left, top, right, bottom)) continue
            val t = hit.elapsed / hit.duration
            when (hit.type) {
                PowerUpType.REPEL -> drawShoveLines(hit, t)
                PowerUpType.FREEZE -> drawIceBeam(hit, t)
                PowerUpType.HOOK -> {
                    drawHookGlow(hit, t)
                    painter.color(GameRenderer.powerUpColor(hit.type), 0.5f * (1f - t))
                    painter.glow(hit.toX, hit.toY, hit.targetRadius * 1.3f)
                }
                else -> {}
            }
        }
        for (burst in bursts) {
            if (!touches(burst.x, burst.y, burst.x, burst.y, burst.radius, left, top, right, bottom)) continue
            val t = burst.elapsed / burst.duration
            painter.color(burst.color, 0.6f * (1f - t))
            painter.glow(burst.x, burst.y, burst.radius * (0.6f + 0.4f * t))
        }
    }

    // ---- Auras ---------------------------------------------------------------------------------

    private fun drawAuraLight(aura: Aura, s: BalloonPainter.State, t: Float) {
        val color = GameRenderer.powerUpColor(aura.type)
        val r = fxRadius(s)
        if (!aura.used) {
            // Whatever was picked up flares and sinks into the latex.
            painter.color(color, 0.5f * (1f - t) * (1f - t) * s.alpha)
            painter.glow(s.x, s.y, r * (1.3f + 0.5f * t))
            if (aura.type != PowerUpType.SHIELD && !isPermanent(aura.type)) {
                painter.color(color, 0.55f * (1f - t) * s.alpha)
                painter.circleStroke(s.x, s.y, r * (2.3f - 1.2f * easeOut(t)), 3f)
            }
        }
        when (aura.type) {
            PowerUpType.FREEZE -> drawSnowflakesClosingIn(s, t, aura.seed)
            PowerUpType.SPEED -> drawSpeedRush(s, t, aura.used)
            PowerUpType.GROWTH -> {
                sheen(s, color, 0.35f * bell(t))
                for (k in 0 until 3) {
                    val tk = (t * 1.4f - k * 0.2f).coerceIn(0f, 1f)
                    if (tk <= 0f || tk >= 1f) continue
                    painter.color(color, 0.7f * bell(tk) * s.alpha)
                    painter.circleStroke(s.x, s.y, r * (2.2f - 1.15f * tk), 4f)
                }
            }
            PowerUpType.SHIELD -> drawShieldForming(s, t)
            PowerUpType.REPEL -> {
                for (k in 0 until 2) {
                    val tk = (t * 1.5f - k * 0.35f).coerceIn(0f, 1f)
                    if (tk <= 0f || tk >= 1f) continue
                    val radius = r * (1.05f + 0.9f * tk)
                    painter.color(color, 0.65f * (1f - tk) * s.alpha)
                    painter.circleStroke(s.x, s.y, radius, 5f)
                    radialDashes(s.x, s.y, radius * 1.05f, r * 0.3f, 8, aura.seed + k, color, 0.5f * (1f - tk) * s.alpha)
                }
            }
            PowerUpType.HOOK -> {
                // A line winding itself in around the balloon, barbs pointing inward.
                val radius = r * (2f - 0.85f * easeOut(t))
                val spin = aura.seed + t * 8f
                painter.color(color, 0.75f * bell(t) * s.alpha)
                painter.circleStroke(s.x, s.y, radius, 4f, dashed = true, startAngle = spin)
                painter.color(HOOK_LIGHT_COLOR, 0.8f * bell(t) * s.alpha)
                for (i in 0 until 3) {
                    val angle = spin + i * MathUtils.PI2 / 3f
                    val c = MathUtils.cos(angle)
                    val sn = MathUtils.sin(angle)
                    painter.triangle(s.x + c * (radius + 12f), s.y + sn * (radius + 12f), s.x + c * (radius - 4f), s.y + sn * (radius - 4f), 12f)
                }
            }
            PowerUpType.INVISIBILITY -> sheen(s, INVISIBLE_LIGHT_COLOR, 0.25f * bell(t))
            PowerUpType.SPEED_UP, PowerUpType.AGILITY_UP, PowerUpType.POTENCY_UP -> drawLevelUp(aura, s, t, color)
        }
    }

    // Snowflakes spiraling in onto the balloon as the frost takes it.
    private fun drawSnowflakesClosingIn(s: BalloonPainter.State, t: Float, seed: Float) {
        val alpha = bell(t) * s.alpha
        val r = fxRadius(s)
        for (i in 0 until 6) {
            val angle = seed + i * MathUtils.PI2 / 6f + t * 2.5f
            val distance = r * (2f - 1.25f * easeOut(t))
            val size = r * 0.38f * (1f - 0.45f * t)
            painter.color(ICE_LIGHT_COLOR, 0.9f * alpha)
            painter.region(atlas.snowflake, s.x + MathUtils.cos(angle) * distance, s.y + MathUtils.sin(angle) * distance, size, size, angle * 60f)
        }
        // Glints popping up on the ice once it's set.
        if (t in 0.35f..0.85f) {
            val glint = bell((t - 0.35f) / 0.5f)
            painter.color(Color.WHITE, 0.8f * glint * s.alpha)
            val size = s.radius * 0.5f * glint
            painter.region(atlas.sparkle, s.x - s.radius * 0.35f, s.y - s.radius * 0.4f, size, size, 20f)
            painter.region(atlas.sparkle, s.x + s.radius * 0.4f, s.y + s.radius * 0.25f, size * 0.7f, size * 0.7f, 50f)
        }
    }

    // Streaks of light rushing past the balloon from front to back.
    private fun drawSpeedRush(s: BalloonPainter.State, t: Float, used: Boolean) {
        sheen(s, SPEED_COLOR, (if (used) 0.5f else 0.35f) * bell(t))
        val sideX = -s.facingY
        val sideY = s.facingX
        val r = fxRadius(s)
        val lines = if (used) 12 else 8
        for (i in 0 until lines) {
            val flow = (t * 1.7f + i * 0.13f) % 1f
            val lateral = r * (-1.15f + 2.3f * ((i * 7) % lines) / (lines - 1))
            val along = r * (1.4f - 3.6f * flow)
            val length = r * (if (used) 1.3f else 0.9f)
            val x0 = s.x + sideX * lateral + s.facingX * along
            val y0 = s.y + sideY * lateral + s.facingY * along
            painter.color(SPEED_COLOR, 0.75f * (1f - t) * sin(flow * MathUtils.PI) * s.alpha)
            painter.line(x0, y0, x0 - s.facingX * length, y0 - s.facingY * length, 3f)
        }
        if (used) {
            painter.color(SPEED_COLOR, 0.6f * (1f - t) * s.alpha)
            painter.circleStroke(s.x, s.y, r * (1.1f + 1.4f * easeOut(t)), 4f)
        }
    }

    // The bubble blowing up around the balloon and settling into the shield it'll wear.
    private fun drawShieldForming(s: BalloonPainter.State, t: Float) {
        val final = s.radius * BalloonPainter.SHIELD_SCALE
        val radius = final * (0.65f + 0.35f * easeOut(min(1f, t * 1.6f)))
        painter.color(SHIELD_LIGHT_COLOR, 0.9f * (1f - 0.7f * t) * s.alpha)
        painter.circleStroke(s.x, s.y, radius, 2f + 6f * (1f - t))
        painter.color(GameRenderer.powerUpColor(PowerUpType.SHIELD), 0.6f * (1f - t) * s.alpha)
        val hexSize = radius * 2f / Atlas.SHIELD_RADIUS_FRACTION
        painter.region(atlas.hexShield, s.x, s.y, hexSize, hexSize, t * 40f)
        painter.color(Color.WHITE, 0.9f * bell(t) * s.alpha)
        for (i in 0 until 6) {
            val angle = i * MathUtils.PI2 / 6f + t * 2f
            val size = 26f * bell(((t * 2f + i * 0.17f) % 1f))
            painter.region(atlas.sparkle, s.x + MathUtils.cos(angle) * radius, s.y + MathUtils.sin(angle) * radius, size, size, angle * 50f)
        }
    }

    // A permanent upgrade: a ring of light spreading from the balloon, its latex lit up in the
    // stat's color, and the stat's own sign - chevrons rising for speed, sparks whirling around it
    // for agility, power rushing into it for potency.
    private fun drawLevelUp(aura: Aura, s: BalloonPainter.State, t: Float, color: Int) {
        val r = fxRadius(s)
        sheen(s, color, 0.35f * bell(t))
        painter.color(color, 0.6f * (1f - t) * s.alpha)
        painter.circleStroke(s.x, s.y, r * (1.05f + 1.3f * easeOut(t)), 5f * (1f - t) + 1f)
        when (aura.type) {
            PowerUpType.SPEED_UP -> for (k in 0 until 3) {
                val tk = (t * 1.4f - k * 0.2f).coerceIn(0f, 1f)
                if (tk <= 0f || tk >= 1f) continue
                val y = s.y - r * (0.1f + 2.3f * tk)
                val half = r * 0.45f
                painter.color(color, 0.85f * bell(tk) * s.alpha)
                painter.line(s.x - half, y + half * 0.6f, s.x, y, 7f)
                painter.line(s.x, y, s.x + half, y + half * 0.6f, 7f)
            }
            PowerUpType.AGILITY_UP -> for (k in 0 until 3) {
                // Each spark leaves a short tail of fading dots behind it on its spiral.
                for (tail in 0 until 5) {
                    val tt = (t - tail * 0.025f).coerceAtLeast(0f)
                    val angle = aura.seed + k * MathUtils.PI2 / 3f + tt * 11f
                    val distance = r * (1.15f + 0.5f * tt)
                    painter.color(color, 0.85f * bell(t) * (1f - tail / 5f) * s.alpha)
                    painter.glow(s.x + MathUtils.cos(angle) * distance, s.y + MathUtils.sin(angle) * distance, 16f - tail * 2f)
                }
            }
            PowerUpType.POTENCY_UP -> {
                val pull = easeIn(min(1f, t * 1.5f))
                for (i in 0 until 10) {
                    val angle = aura.seed + i * MathUtils.PI2 / 10f
                    val outer = r * (2.8f - 1.7f * pull)
                    val inner = max(r * 0.95f, outer - r * 0.7f)
                    val c = MathUtils.cos(angle)
                    val sn = MathUtils.sin(angle)
                    painter.color(color, 0.8f * (1f - t) * s.alpha)
                    painter.line(s.x + c * outer, s.y + sn * outer, s.x + c * inner, s.y + sn * inner, 4f)
                }
                if (t > 0.45f) {
                    painter.color(Color.WHITE, 0.5f * bell((t - 0.45f) / 0.55f) * s.alpha)
                    painter.glow(s.x, s.y, r * 1.1f)
                }
            }
            else -> {}
        }
    }

    // Fog swirling around (and over) the balloon as it slips out of sight.
    private fun drawFogSwirl(s: BalloonPainter.State, t: Float, seed: Float, strength: Float) {
        val r = fxRadius(s)
        for (i in 0 until FOG_PUFFS) {
            // Each puff at its own distance and size, so together they read as a loose bank of
            // fog rather than a ring.
            val jitter = sin(seed * 3.1f + i * 2.7f)
            val angle = seed + i * MathUtils.PI2 / FOG_PUFFS + t * (2f + 0.6f * jitter)
            val distance = r * (0.45f + 0.35f * (0.5f + 0.5f * jitter) + 0.4f * t)
            val radius = r * (0.55f + 0.2f * jitter + 0.35f * bell(t))
            painter.color(FOG_COLOR, strength * bell(t))
            painter.glow(s.x + MathUtils.cos(angle) * distance, s.y + MathUtils.sin(angle) * distance, radius)
        }
    }

    // ---- Items going off -----------------------------------------------------------------------

    // A wave of the item's color spreading out to its reach - a soft band with a bright leading
    // edge, and for REPEL streaks of force radiating out, for FREEZE snowflakes riding the front.
    private fun drawNova(nova: Nova, t: Float) {
        val color = if (nova.type == PowerUpType.FREEZE) ICE_COLOR else GameRenderer.powerUpColor(nova.type)
        val edgeColor = if (nova.type == PowerUpType.FREEZE) Color.WHITE else REPEL_LIGHT_COLOR
        val radius = nova.sourceRadius + (nova.range - nova.sourceRadius) * easeOut(t)
        val fade = 1f - t
        val band = 30f + 60f * fade
        painter.ring(nova.x, nova.y, max(0f, radius - band), radius, color, 0f, color, 0.4f * fade)
        painter.color(edgeColor, 0.85f * fade)
        painter.circleStroke(nova.x, nova.y, radius, 4f)
        painter.color(color, 0.35f * fade)
        painter.circleStroke(nova.x, nova.y, radius * 0.82f, 3f)
        if (nova.type == PowerUpType.REPEL) {
            radialDashes(nova.x, nova.y, radius * 0.6f, radius * 0.32f, 16, nova.x * 0.01f, color, 0.45f * fade)
        } else {
            for (i in 0 until 10) {
                val angle = i * MathUtils.PI2 / 10f + t
                val size = 24f + nova.sourceRadius * 0.15f
                painter.color(Color.WHITE, 0.8f * fade)
                painter.region(atlas.snowflake, nova.x + MathUtils.cos(angle) * radius, nova.y + MathUtils.sin(angle) * radius, size, size, angle * 90f + t * 200f)
            }
        }
    }

    // A balloon shoved or yanked across the floor leaves fading copies of itself along the way,
    // fainter the further back.
    private fun drawDragTrail(hit: Hit, t: Float) {
        val fade = 1f - t
        if (hit.type == PowerUpType.HOOK) {
            // Where the line caught it, for as long as the line's still reaching there.
            val held = 1f - ramp(t, HOOK_SHOOT, HOOK_REEL)
            painter.color(hit.targetColor, 0.35f * held)
            painter.disc(hit.fromX, hit.fromY, hit.targetRadius * 0.9f)
        }
        for (k in 1..3) {
            val f = k / 4f
            painter.color(hit.targetColor, 0.09f * k * fade)
            painter.disc(hit.fromX + (hit.toX - hit.fromX) * f, hit.fromY + (hit.toY - hit.fromY) * f, hit.targetRadius * (0.7f + 0.06f * k))
        }
    }

    // Lines of force along a shoved balloon's path.
    private fun drawShoveLines(hit: Hit, t: Float) {
        val dx = hit.toX - hit.fromX
        val dy = hit.toY - hit.fromY
        val length = hypot(dx, dy)
        if (length < 1f) return
        val sideX = -dy / length * hit.targetRadius * 0.7f
        val sideY = dx / length * hit.targetRadius * 0.7f
        val color = GameRenderer.powerUpColor(hit.type)
        // They race after the balloon: drawn over the latter part of its path, shrinking toward it.
        val start = 0.35f + 0.45f * t
        painter.color(color, 0.6f * (1f - t))
        for (side in -1..1) {
            if (side == 0) continue
            painter.line(
                hit.fromX + dx * start + sideX * side, hit.fromY + dy * start + sideY * side,
                hit.toX - dx * 0.08f + sideX * side, hit.toY - dy * 0.08f + sideY * side,
                2.5f
            )
        }
    }

    // The cold reaching out from the FREEZE to each balloon it catches: a pale beam with
    // snowflakes riding it.
    private fun drawIceBeam(hit: Hit, t: Float) {
        val source = stateOf(hit.sourceId) ?: return
        val sx = source.x
        val sy = source.y
        val sourceRadius = source.radius
        val target = stateOf(hit.targetId)
        val tx = target?.x ?: hit.toX
        val ty = target?.y ?: hit.toY
        val dx = tx - sx
        val dy = ty - sy
        val length = hypot(dx, dy)
        if (length <= sourceRadius + hit.targetRadius) return
        val ux = dx / length
        val uy = dy / length
        val x0 = sx + ux * sourceRadius
        val y0 = sy + uy * sourceRadius
        val x1 = tx - ux * hit.targetRadius * 0.8f
        val y1 = ty - uy * hit.targetRadius * 0.8f
        val fade = (1f - t) * (1f - t)
        painter.color(ICE_COLOR, 0.35f * fade)
        painter.line(x0, y0, x1, y1, 16f)
        painter.color(Color.WHITE, 0.85f * fade)
        painter.line(x0, y0, x1, y1, 3f)
        for (i in 0 until 3) {
            val f = (t * 2.2f + i / 3f) % 1f
            painter.color(Color.WHITE, 0.9f * fade)
            painter.region(atlas.snowflake, x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, 20f, 20f, f * 360f)
        }
    }

    // HOOK's line: a chain of links shooting out from the balloon that threw it to where its
    // catch was, then reeling it in - the catch itself has already been yanked over by then (see
    // GameEngine.applyHookPull), so the line's end follows its trail back in.
    private fun drawHookLine(hit: Hit, t: Float) {
        if (!hookLine(hit, t)) return
        val alpha = 1f - ramp(t, HOOK_REEL, 1f)
        var d = 0f
        var link = 0
        while (d < hookLength) {
            val end = min(d + HOOK_LINK_LENGTH, hookLength)
            val x0 = hookX + hookUx * d
            val y0 = hookY + hookUy * d
            val x1 = hookX + hookUx * end
            val y1 = hookY + hookUy * end
            painter.color(HOOK_DARK_COLOR, 0.95f * alpha)
            painter.line(x0, y0, x1, y1, if (link % 2 == 0) 9f else 5f)
            painter.color(HOOK_LIGHT_COLOR, 0.9f * alpha)
            painter.line(x0, y0, x1, y1, if (link % 2 == 0) 3f else 2f)
            d += HOOK_LINK_LENGTH + 2f
            link++
        }
        // The hook itself, biting into its catch.
        val tipX = hookX + hookUx * hookLength
        val tipY = hookY + hookUy * hookLength
        painter.color(HOOK_LIGHT_COLOR, alpha)
        painter.triangle(tipX - hookUx * 14f, tipY - hookUy * 14f, tipX + hookUx * 8f, tipY + hookUy * 8f, 16f)
    }

    // ...and a glint running along it.
    private fun drawHookGlow(hit: Hit, t: Float) {
        if (!hookLine(hit, t)) return
        val alpha = 1f - ramp(t, HOOK_REEL, 1f)
        painter.color(GameRenderer.powerUpColor(hit.type), 0.4f * alpha)
        painter.line(hookX, hookY, hookX + hookUx * hookLength, hookY + hookUy * hookLength, 18f)
        val glint = (t * 3f) % 1f
        painter.color(Color.WHITE, 0.8f * alpha)
        painter.glow(hookX + hookUx * hookLength * glint, hookY + hookUy * hookLength * glint, 12f)
    }

    // HOOK's line this frame, into hookX/hookY (its start, on the thrower's surface), hookUx/hookUy
    // (its direction) and hookLength - or false if there's none to draw. It shoots out from the
    // thrower to where its catch was, then reels in along the catch's path.
    private fun hookLine(hit: Hit, t: Float): Boolean {
        val source = stateOf(hit.sourceId) ?: return false
        val reel = easeOut(ramp(t, HOOK_SHOOT, HOOK_REEL))
        val endX = hit.fromX + (hit.toX - hit.fromX) * reel
        val endY = hit.fromY + (hit.toY - hit.fromY) * reel
        val dx = endX - source.x
        val dy = endY - source.y
        val distance = hypot(dx, dy)
        if (distance < 1f) return false
        hookUx = dx / distance
        hookUy = dy / distance
        hookX = source.x + hookUx * source.radius
        hookY = source.y + hookUy * source.radius
        // Up to the catch's near side (at least a stub, even pulled right up against it).
        val full = max(distance - source.radius - hit.targetRadius * 0.7f, HOOK_LINK_LENGTH * 2f)
        hookLength = full * min(1f, t / HOOK_SHOOT)
        return true
    }

    private var hookX = 0f
    private var hookY = 0f
    private var hookUx = 0f
    private var hookUy = 0f
    private var hookLength = 0f

    // Keeps a hit's far end on the balloon it caught while it moves on.
    private fun followTarget(hit: Hit) {
        val target = stateOf(hit.targetId) ?: return
        hit.toX = target.x
        hit.toY = target.y
    }

    // ---- Particles -----------------------------------------------------------------------------

    private fun radialSparks(x: Float, y: Float, radius: Float, count: Int, color: Int, endColor: Int, minSpeed: Float, maxSpeed: Float) {
        for (i in 0 until count) {
            val angle = MathUtils.random(MathUtils.PI2)
            val speed = MathUtils.random(minSpeed, maxSpeed)
            particles.spawn(
                Particles.Kind.SPARK,
                x + MathUtils.cos(angle) * radius, y + MathUtils.sin(angle) * radius,
                MathUtils.cos(angle) * speed, MathUtils.sin(angle) * speed,
                MathUtils.random(0.25f, 0.45f),
                MathUtils.random(14f, 26f), 3f,
                color, endColor,
                drag = 4f
            )
        }
    }

    private fun flakes(x: Float, y: Float, radius: Float, count: Int, minSpeed: Float, maxSpeed: Float) {
        for (i in 0 until count) {
            val angle = MathUtils.random(MathUtils.PI2)
            val speed = MathUtils.random(minSpeed, maxSpeed)
            particles.spawn(
                Particles.Kind.FLAKE,
                x + MathUtils.cos(angle) * radius, y + MathUtils.sin(angle) * radius,
                MathUtils.cos(angle) * speed, MathUtils.sin(angle) * speed,
                MathUtils.random(0.55f, 0.95f),
                MathUtils.random(14f, 26f), 6f,
                Color.WHITE, ICE_COLOR,
                drag = 3f
            )
        }
    }

    // FREEZE's breath: cold fog rolling out behind its wave.
    private fun coldPuffs(x: Float, y: Float, sourceRadius: Float, range: Float) {
        for (i in 0 until 8) {
            val angle = i * MathUtils.PI2 / 8f + MathUtils.random(-0.3f, 0.3f)
            val speed = (range - sourceRadius) * MathUtils.random(0.8f, 1.2f)
            particles.spawn(
                Particles.Kind.MIST,
                x + MathUtils.cos(angle) * sourceRadius, y + MathUtils.sin(angle) * sourceRadius,
                MathUtils.cos(angle) * speed, MathUtils.sin(angle) * speed,
                MathUtils.random(0.7f, 1f),
                sourceRadius * 0.5f, min(sourceRadius * 1.4f, 150f),
                ICE_FOG_COLOR,
                alpha = 0.3f,
                drag = 2.5f
            )
        }
    }

    private fun fog(s: BalloonPainter.State, count: Int, alpha: Float) {
        for (i in 0 until count) {
            val angle = MathUtils.random(MathUtils.PI2)
            val speed = MathUtils.random(40f, 120f)
            particles.spawn(
                Particles.Kind.MIST,
                s.x + MathUtils.cos(angle) * s.radius * 0.6f, s.y + MathUtils.sin(angle) * s.radius * 0.6f,
                MathUtils.cos(angle) * speed + s.velocityX * 0.6f, MathUtils.sin(angle) * speed + s.velocityY * 0.6f,
                MathUtils.random(0.7f, 1.1f),
                s.radius * 0.5f, s.radius * 1.4f,
                FOG_COLOR,
                alpha = alpha,
                drag = 2f
            )
        }
    }

    // Little dots of light drifting up off the balloon (or out of it).
    private fun motes(s: BalloonPainter.State, color: Int, count: Int, rising: Boolean) {
        for (i in 0 until count) {
            val angle = MathUtils.random(MathUtils.PI2)
            val reach = s.radius * MathUtils.random(0.3f, 1f)
            val up = if (rising) MathUtils.random(70f, 170f) else 0f
            particles.spawn(
                Particles.Kind.MOTE,
                s.x + MathUtils.cos(angle) * reach, s.y + MathUtils.sin(angle) * reach,
                MathUtils.cos(angle) * 30f + s.velocityX * 0.8f, MathUtils.sin(angle) * 30f - up + s.velocityY * 0.8f,
                MathUtils.random(0.6f, 1.1f),
                MathUtils.random(10f, 18f), 3f,
                Color.WHITE, color,
                drag = 1.2f
            )
        }
    }

    // ---- Helpers -------------------------------------------------------------------------------

    // The whole balloon lit up in `color` for a moment.
    private fun sheen(s: BalloonPainter.State, color: Int, alpha: Float) {
        if (alpha <= 0.01f) return
        painter.color(color, alpha * s.alpha)
        balloons.drawSilhouette(s)
    }

    // `count` short dashes pointing straight out from (x, y), starting `from` away and `length` long.
    private fun radialDashes(x: Float, y: Float, from: Float, length: Float, count: Int, seed: Float, color: Int, alpha: Float) {
        painter.color(color, alpha)
        for (i in 0 until count) {
            val angle = seed + i * MathUtils.PI2 / count
            val c = MathUtils.cos(angle)
            val sn = MathUtils.sin(angle)
            painter.line(x + c * from, y + sn * from, x + c * (from + length), y + sn * (from + length), 3f)
        }
    }

    private fun touches(x0: Float, y0: Float, x1: Float, y1: Float, margin: Float, left: Float, top: Float, right: Float, bottom: Float) =
        max(x0, x1) + margin >= left && min(x0, x1) - margin <= right && max(y0, y1) + margin >= top && min(y0, y1) - margin <= bottom

    // The radius an aura's decorations are laid out around: the balloon's own, but never so small
    // that they'd crowd into a few pixels on a small balloon.
    private fun fxRadius(s: BalloonPainter.State): Float = max(s.radius, MIN_FX_RADIUS)

    private fun isPermanent(type: PowerUpType) =
        type == PowerUpType.SPEED_UP || type == PowerUpType.AGILITY_UP || type == PowerUpType.POTENCY_UP

    companion object {
        private const val HOOK_LINK_LENGTH = 12f
        private const val MIN_FX_RADIUS = 40f
        // HOOK's line takes this long (as a fraction of its effect) to shoot out, and has reeled
        // its catch all the way in by the second.
        private const val HOOK_SHOOT = 0.12f
        private const val HOOK_REEL = 0.55f
        private const val FOG_PUFFS = 9
        private val SPEED_COLOR = Color.parseColor("#FFD54F")
        private val ICE_COLOR = Color.parseColor("#4DD0E1")
        private val ICE_LIGHT_COLOR = Color.parseColor("#B2EBF2")
        private val ICE_TINT_COLOR = Color.parseColor("#B3E5FC")
        private val ICE_SHARD_COLOR = Color.parseColor("#E0F7FA")
        private val ICE_FOG_COLOR = Color.parseColor("#D7F2F8")
        private val FOG_COLOR = Color.parseColor("#B8B0D0")
        private val INVISIBLE_LIGHT_COLOR = Color.parseColor("#9575CD")
        private val SHIELD_LIGHT_COLOR = Color.parseColor("#B3E5FC")
        private val REPEL_LIGHT_COLOR = Color.parseColor("#F8BBD0")
        private val HOOK_LIGHT_COLOR = Color.parseColor("#D7CCC8")
        private val HOOK_DARK_COLOR = Color.parseColor("#5D4037")

        private fun bell(t: Float): Float = sin(t.coerceIn(0f, 1f) * MathUtils.PI)
        private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)
        private fun easeIn(t: Float): Float = t * t
        // 0 before `from`, 1 after `to`, linear between.
        private fun ramp(t: Float, from: Float, to: Float): Float = ((t - from) / (to - from)).coerceIn(0f, 1f)
    }
}
