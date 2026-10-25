package com.hyperionsoftware.balls.ui.gdx

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.NumberUtils
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt

// Thin drawing layer over one SpriteBatch + the Atlas, giving the renderers the same kind of
// vocabulary the old Canvas code had (circles, lines, rings, text...) while everything still
// goes out as batched quads from a single texture. Colors are Android ARGB ints throughout,
// converted here to premultiplied vertex colors to match the atlas (see Atlas).
class Painter {
    val atlas = Atlas()
    val text = TextCache()
    private val batch = SpriteBatch(MAX_SPRITES)
    private val vertices = FloatArray(20)
    private var additive = false

    init {
        batch.enableBlending()
        batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA)
    }

    fun begin(projection: Matrix4) {
        batch.projectionMatrix = projection
        batch.begin()
    }

    fun end() {
        batch.end()
        additive(false)
    }

    // Additive light (glows, flashes, sparks): same premultiplied colors, just added on top
    // instead of covering what's underneath.
    fun additive(enabled: Boolean) {
        if (additive == enabled) return
        additive = enabled
        if (enabled) batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE) else batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA)
    }

    // For fully opaque geometry (the floor): writing pixels straight out is cheaper than
    // blending them over what's already there.
    fun blending(enabled: Boolean) {
        if (enabled) batch.enableBlending() else batch.disableBlending()
    }

    // Sets the tint for everything drawn after it. `alpha` multiplies the color's own alpha.
    fun color(argb: Int, alpha: Float = 1f) {
        batch.packedColor = packedColor(argb, alpha)
    }

    fun region(region: TextureRegion, cx: Float, cy: Float, width: Float, height: Float, rotation: Float = 0f) {
        batch.draw(region, cx - width / 2f, cy - height / 2f, width / 2f, height / 2f, width, height, 1f, 1f, rotation)
    }

    // A region scaled by scaleX/scaleY around its center, then rotated - the tumbling confetti.
    fun transformed(
        region: TextureRegion,
        cx: Float,
        cy: Float,
        size: Float,
        scaleX: Float,
        scaleY: Float,
        rotation: Float
    ) {
        batch.draw(region, cx - size / 2f, cy - size / 2f, size / 2f, size / 2f, size, size, scaleX, scaleY, rotation)
    }

    // A square region drawn like region(), but as a fan of triangles around its center whose
    // spokes are each stretched by a deformed balloon's `shape` (see BalloonBody) - everything
    // in the region moves along with the latex. The fan covers the region's inscribed circle, so
    // whatever is drawn in it must stay inside that. The region is rotated by `rotationDeg`; the
    // shape belongs to a balloon rotated by `shapeRotationDeg` (the same, for the body itself).
    fun deformed(region: TextureRegion, cx: Float, cy: Float, size: Float, rotationDeg: Float, shape: FloatArray, shapeRotationDeg: Float) {
        val spokes = BalloonBody.SPOKES
        val half = size / 2f
        val cosR = MathUtils.cosDeg(rotationDeg)
        val sinR = MathUtils.sinDeg(rotationDeg)
        val shapeOffset = (rotationDeg - shapeRotationDeg) / 360f * spokes
        // Same texture mapping as SpriteBatch.draw: local +x toward u2, local +y toward v.
        val centerU = (region.u + region.u2) / 2f
        val centerV = (region.v + region.v2) / 2f
        val halfU = (region.u2 - region.u) / 2f
        val halfV = (region.v - region.v2) / 2f
        for (k in 0..spokes) {
            val i = k % spokes
            val scale = BalloonBody.sample(shape, i + shapeOffset)
            val lx = BalloonBody.COS[i] * half * scale
            val ly = BalloonBody.SIN[i] * half * scale
            fanX[k] = cx + cosR * lx - sinR * ly
            fanY[k] = cy + sinR * lx + cosR * ly
            fanU[k] = centerU + BalloonBody.COS[i] * halfU
            fanV[k] = centerV + BalloonBody.SIN[i] * halfV
        }
        val color = batch.packedColor
        // Each quad is two of the fan's triangles: center, k, k+1 and k+1, k+2, center.
        var k = 0
        while (k < spokes) {
            vertices[0] = cx; vertices[1] = cy; vertices[2] = color; vertices[3] = centerU; vertices[4] = centerV
            vertices[5] = fanX[k]; vertices[6] = fanY[k]; vertices[7] = color; vertices[8] = fanU[k]; vertices[9] = fanV[k]
            vertices[10] = fanX[k + 1]; vertices[11] = fanY[k + 1]; vertices[12] = color; vertices[13] = fanU[k + 1]; vertices[14] = fanV[k + 1]
            vertices[15] = fanX[k + 2]; vertices[16] = fanY[k + 2]; vertices[17] = color; vertices[18] = fanU[k + 2]; vertices[19] = fanV[k + 2]
            batch.draw(region.texture, vertices, 0, 20)
            k += 2
        }
    }

    private val fanX = FloatArray(BalloonBody.SPOKES + 1)
    private val fanY = FloatArray(BalloonBody.SPOKES + 1)
    private val fanU = FloatArray(BalloonBody.SPOKES + 1)
    private val fanV = FloatArray(BalloonBody.SPOKES + 1)

    fun disc(cx: Float, cy: Float, radius: Float) {
        region(atlas.disc, cx, cy, radius * Atlas.DISC_SPRITE_SCALE, radius * Atlas.DISC_SPRITE_SCALE)
    }

    // A large solid circle in the current color, as exact geometry: a fan of triangles (plus,
    // with `rim`, a thin antialiased edge). The disc sprite needs a quad well beyond the circle
    // itself (see Atlas.DISC_RADIUS_FRACTION), which for a circle hundreds of pixels across
    // means filling a lot of transparent pixels for nothing - this only touches the circle's own.
    fun filledCircle(cx: Float, cy: Float, radius: Float, rim: Boolean = true) {
        val color = batch.packedColor
        val segments = segmentsFor(radius)
        val step = MathUtils.PI2 / segments
        val inner = radius - 1f
        for (i in 0 until segments) {
            val a0 = i * step
            val a1 = a0 + step
            // Each quad is a degenerate "triangle" pair from the center out to two rim points.
            quad(
                atlas.white,
                cx, cy, color,
                cx, cy, color,
                cx + MathUtils.cos(a1) * inner, cy + MathUtils.sin(a1) * inner, color,
                cx + MathUtils.cos(a0) * inner, cy + MathUtils.sin(a0) * inner, color
            )
        }
        if (rim) circleStroke(cx, cy, radius - 0.5f, 2f)
    }

    fun glow(cx: Float, cy: Float, radius: Float) {
        region(atlas.glow, cx, cy, radius * 2f, radius * 2f)
    }

    fun rect(x: Float, y: Float, width: Float, height: Float) {
        batch.draw(atlas.white, x, y, width, height)
    }

    // Outline of an axis-aligned rectangle, stroke centered on its edges.
    fun rectStroke(x: Float, y: Float, width: Float, height: Float, strokeWidth: Float) {
        val half = strokeWidth / 2f
        rect(x - half, y - half, width + strokeWidth, strokeWidth)
        rect(x - half, y + height - half, width + strokeWidth, strokeWidth)
        rect(x - half, y + half, strokeWidth, height - strokeWidth)
        rect(x + width - half, y + half, strokeWidth, height - strokeWidth)
    }

    // An antialiased line of the current color. Hard-edged (`soft` = false) suits very wide
    // bands where a few pixels of softness would read as blur.
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, soft: Boolean = true) {
        val dx = x2 - x1
        val dy = y2 - y1
        val length = sqrt(dx * dx + dy * dy)
        if (length < 0.001f) return
        val region = if (!soft) atlas.white else if (width > THICK_LINE_THRESHOLD) atlas.thickLine else atlas.thinLine
        // The thin strip's soft ramps eat into its edges, so it's drawn a touch wider than the
        // requested stroke to keep the same visual weight.
        val drawWidth = if (soft && region === atlas.thinLine) width + 1.5f else width
        val angle = atan2(dy, dx) * MathUtils.radiansToDegrees
        batch.draw(region, x1, y1 - drawWidth / 2f, 0f, drawWidth / 2f, length, drawWidth, 1f, 1f, angle)
    }

    // A circle outline of constant stroke width, built from short antialiased segments rather
    // than a scaled ring texture (whose stroke would grow and shrink with the radius). With
    // `dashed`, only every other segment is drawn.
    fun circleStroke(cx: Float, cy: Float, radius: Float, width: Float, dashed: Boolean = false) {
        if (radius <= 0f) return
        val segments = segmentsFor(radius)
        val step = MathUtils.PI2 / segments
        val color = batch.packedColor
        val region = if (width > THICK_LINE_THRESHOLD) atlas.thickLine else atlas.thinLine
        val inner = radius - width / 2f - 0.75f
        val outer = radius + width / 2f + 0.75f
        for (i in 0 until segments) {
            if (dashed && i % 2 == 1) continue
            val a0 = i * step
            val a1 = a0 + step
            val c0 = MathUtils.cos(a0)
            val s0 = MathUtils.sin(a0)
            val c1 = MathUtils.cos(a1)
            val s1 = MathUtils.sin(a1)
            quad(
                region,
                cx + c0 * outer, cy + s0 * outer, color,
                cx + c1 * outer, cy + s1 * outer, color,
                cx + c1 * inner, cy + s1 * inner, color,
                cx + c0 * inner, cy + s0 * inner, color
            )
        }
    }

    // A filled band between two radii, with a color gradient from the inner edge to the outer
    // one - the safe zone's dimmed outside and its glowing wall.
    fun ring(cx: Float, cy: Float, innerRadius: Float, outerRadius: Float, innerArgb: Int, innerAlpha: Float, outerArgb: Int, outerAlpha: Float) {
        if (outerRadius <= innerRadius) return
        val segments = segmentsFor(outerRadius)
        val step = MathUtils.PI2 / segments
        val innerColor = packedColor(innerArgb, innerAlpha)
        val outerColor = packedColor(outerArgb, outerAlpha)
        val inner = max(innerRadius, 0f)
        for (i in 0 until segments) {
            val a0 = i * step
            val a1 = a0 + step
            val c0 = MathUtils.cos(a0)
            val s0 = MathUtils.sin(a0)
            val c1 = MathUtils.cos(a1)
            val s1 = MathUtils.sin(a1)
            quad(
                atlas.white,
                cx + c0 * outerRadius, cy + s0 * outerRadius, outerColor,
                cx + c1 * outerRadius, cy + s1 * outerRadius, outerColor,
                cx + c1 * inner, cy + s1 * inner, innerColor,
                cx + c0 * inner, cy + s0 * inner, innerColor
            )
        }
    }

    // An axis-aligned rectangle with its own color at each corner (top-left, top-right,
    // bottom-right, bottom-left), blended smoothly across it. Colors are packed - see packedColor.
    fun shadedRect(x: Float, y: Float, width: Float, height: Float, topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float) {
        quad(
            atlas.white,
            x, y, topLeft,
            x + width, y, topRight,
            x + width, y + height, bottomRight,
            x, y + height, bottomLeft
        )
    }

    // A triangle with its base centered on (baseX, baseY), `baseWidth` across, pointing at
    // (tipX, tipY) - the balloon's knot and the off-screen danger arrow.
    fun triangle(baseX: Float, baseY: Float, tipX: Float, tipY: Float, baseWidth: Float) {
        val dx = tipX - baseX
        val dy = tipY - baseY
        val length = sqrt(dx * dx + dy * dy)
        if (length < 0.001f) return
        val texelWidth = Atlas.TRIANGLE_HALF_BASE * 2f
        val texelHeight = Atlas.TRIANGLE_BASE_Y - Atlas.TRIANGLE_APEX_Y
        val width = Atlas.CELL * baseWidth / texelWidth
        val height = Atlas.CELL * length / texelHeight
        // The base center inside the (unrotated, y-down) sprite, which rotation pivots around.
        val originX = width / 2f
        val originY = height * Atlas.TRIANGLE_BASE_Y / Atlas.CELL
        // Unrotated, the apex points up (-y); rotate that onto the base->tip direction.
        val angle = atan2(dy, dx) * MathUtils.radiansToDegrees + 90f
        batch.draw(atlas.triangle, baseX - originX, baseY - originY, originX, originY, width, height, 1f, 1f, angle)
    }

    // Draws a string with its baseline at y (like Canvas.drawText). Returns its width.
    fun text(
        value: String,
        x: Float,
        baselineY: Float,
        style: TextStyle,
        argb: Int,
        alpha: Float = 1f,
        scale: Float = 1f
    ): Float {
        val entry = text.get(value, style)
        val width = entry.width * scale
        val left = when (style.align) {
            TextStyle.Align.LEFT -> x
            TextStyle.Align.CENTER -> x - width / 2f
            TextStyle.Align.RIGHT -> x - width
        }
        color(argb, alpha)
        batch.draw(
            entry.region,
            left - entry.paddingLeft * scale,
            baselineY - entry.baselineFromTop * scale,
            entry.region.regionWidth * scale,
            entry.region.regionHeight * scale
        )
        return width
    }

    // Same, but scaled around (pivotX, pivotY) instead of the text's own position - the
    // "punch-in" countdown numbers.
    fun textScaledAround(value: String, x: Float, baselineY: Float, style: TextStyle, argb: Int, alpha: Float, scale: Float, pivotX: Float, pivotY: Float) {
        text(value, pivotX + (x - pivotX) * scale, pivotY + (baselineY - pivotY) * scale, style, argb, alpha, scale)
    }

    fun measure(value: String, style: TextStyle): Float = text.get(value, style).width


    fun beginFrame() {
        text.beginFrame()
    }

    fun dispose() {
        batch.dispose()
        atlas.dispose()
        text.dispose()
    }

    private fun quad(
        region: TextureRegion,
        x1: Float, y1: Float, c1: Float,
        x2: Float, y2: Float, c2: Float,
        x3: Float, y3: Float, c3: Float,
        x4: Float, y4: Float, c4: Float
    ) {
        // Vertices 1/2 take the region's v (top) edge, 3/4 its v2 edge - for the line strips
        // that's what puts the soft ramps on the band's two long sides.
        val u = region.u
        val v = region.v
        val u2 = region.u2
        val v2 = region.v2
        vertices[0] = x1; vertices[1] = y1; vertices[2] = c1; vertices[3] = u; vertices[4] = v
        vertices[5] = x2; vertices[6] = y2; vertices[7] = c2; vertices[8] = u2; vertices[9] = v
        vertices[10] = x3; vertices[11] = y3; vertices[12] = c3; vertices[13] = u2; vertices[14] = v2
        vertices[15] = x4; vertices[16] = y4; vertices[17] = c4; vertices[18] = u; vertices[19] = v2
        batch.draw(region.texture, vertices, 0, 20)
    }

    private fun segmentsFor(radius: Float): Int = (radius / 6f).toInt().coerceIn(24, 160)

    companion object {
        private const val MAX_SPRITES = 6000
        private const val THICK_LINE_THRESHOLD = 24f
        private const val VIGNETTE_START = 0.5f
        private const val VIGNETTE_KNEE = 0.82f
        private const val VIGNETTE_KNEE_DARKNESS = 0.28f
        private const val VIGNETTE_EDGE_DARKNESS = 0.62f

        // How much the screen's vignette darkens a point at `distance` from its center, as a
        // fraction of half the screen's diagonal - baked into the classic-mode floor's vertex
        // colors (see GameRenderer.drawFloor), so it costs nothing per pixel.
        fun vignetteDarkness(distance: Float): Float = when {
            distance <= VIGNETTE_START -> 0f
            distance <= VIGNETTE_KNEE -> VIGNETTE_KNEE_DARKNESS * (distance - VIGNETTE_START) / (VIGNETTE_KNEE - VIGNETTE_START)
            else -> VIGNETTE_KNEE_DARKNESS + (VIGNETTE_EDGE_DARKNESS - VIGNETTE_KNEE_DARKNESS) *
                ((distance - VIGNETTE_KNEE) / (1f - VIGNETTE_KNEE)).coerceAtMost(1f)
        }

        // Android ARGB (straight alpha) -> libGDX packed ABGR float, premultiplied.
        fun packedColor(argb: Int, alpha: Float = 1f): Float {
            val a = ((argb ushr 24) / 255f * alpha).coerceIn(0f, 1f)
            val r = ((argb shr 16) and 0xFF) * a
            val g = ((argb shr 8) and 0xFF) * a
            val b = (argb and 0xFF) * a
            val abgr = ((a * 255f).toInt() shl 24) or (b.toInt() shl 16) or (g.toInt() shl 8) or r.toInt()
            return NumberUtils.intToFloatColor(abgr)
        }
    }
}
