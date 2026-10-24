package com.hyperionsoftware.balls.ui.gdx

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.TextureData
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.GdxRuntimeException
import com.hyperionsoftware.balls.cosmetics.BalloonSticker
import com.hyperionsoftware.balls.cosmetics.ExhaustStyle
import com.hyperionsoftware.balls.game.PowerUpType

// Every sprite the game draws, generated once at startup with the regular Android Canvas (so
// shapes get proper antialiasing, gradients and blur) and uploaded as a single texture -
// GameRenderer/RaceRenderer can then draw an entire frame through one SpriteBatch with almost
// no texture switches. Nothing is loaded from files; the art is the same code-drawn look the
// old Canvas renderer had, just baked.
//
// Pixels are stored premultiplied by alpha (that's how Android bitmaps hold them anyway), so
// Painter blends with GL_ONE / GL_ONE_MINUS_SRC_ALPHA: no dark fringes when soft white edges
// are tinted and filtered, and additive glows come for free with the same textures.
class Atlas {
    val texture: Texture = Texture(GeneratedTextureData(WIDTH, HEIGHT, useMipMaps = true) { drawAtlas(it) }).apply {
        // Bilinear within the nearest mip level: visually indistinguishable here from full
        // trilinear filtering, at about half the texture sampling work.
        setFilter(Texture.TextureFilter.MipMapLinearNearest, Texture.TextureFilter.Linear)
    }

    // A plain white disc of radius DISC_RADIUS_FRACTION of the region's size - draw a circle of
    // radius r with a sprite DISC_SPRITE_SCALE * r on each side.
    val disc = region(slot(0), CELL)
    // Soft round falloff, white at the center to transparent at the edge (gaussian-ish) - smoke,
    // exhaust puffs, glows and drop shadows.
    val glow = region(slot(1), CELL)
    // Isosceles triangle, apex at the top center, base along the bottom - see Painter.triangle.
    val triangle = region(slot(2), CELL)
    // A solid white block (sampled well inside it, so filtering never reaches its edge) for
    // rectangles and flat-colored quads, and two "antialiased line" strips whose alpha ramps up
    // and back down across their height: drawn stretched across a line's width, the ramps give
    // it soft edges at any length. The thin one suits strokes a few pixels wide, the thick one
    // keeps a crisp edge on very wide bands.
    val white = TextureRegion(texture, slot(4).x + 8, slot(4).y + 8, 16, 16)
    // Both strips are drawn much wider than these regions, which sample only their middle: a
    // line is many quads laid end to end, and any filtering into the transparent texels beside
    // a strip would show as a faint seam at every joint between them.
    val thinLine = TextureRegion(texture, slot(4).x + 64 + 24, slot(4).y, 16, THIN_LINE_HEIGHT)
    val thickLine = TextureRegion(texture, slot(4).x + 128 + 24, slot(4).y, 16, CELL)
    val gameIcons: Array<TextureRegion> = Array(PowerUpType.values().size) { iconRegion(slot(5), it) }
    val raceIcons: Array<TextureRegion> = Array(PowerUpType.values().size) { iconRegion(slot(6), it) }
    private val exhaustStyleRegions: Array<TextureRegion?> = arrayOfNulls(ExhaustStyle.values().size)
    private val stickerRegions: Array<TextureRegion?> = arrayOfNulls(BalloonSticker.values().size)
    // A four-pointed sparkle, for glints and pop particles.
    val sparkle = region(slot(18), CELL)

    // Balloon shading + highlight, pre-rotated for LIGHTING_VARIANTS facing angles - see
    // drawLightingVariant for why it can't just be one texture.
    val lighting: Array<TextureRegion> = Array(LIGHTING_VARIANTS) { index ->
        val slotIndex = 10 + index / 4
        val sub = index % 4
        val origin = slot(slotIndex)
        flipped(TextureRegion(texture, origin.x + (sub % 2) * HALF_CELL, origin.y + (sub / 2) * HALF_CELL, HALF_CELL, HALF_CELL))
    }

    init {
        ExhaustStyle.values().forEachIndexed { index, style ->
            if (style != ExhaustStyle.CLASSIC) {
                val sub = EXHAUST_STYLE_ORDER.indexOf(style)
                val origin = slot(7)
                exhaustStyleRegions[index] = flipped(
                    TextureRegion(texture, origin.x + (sub % 2) * HALF_CELL, origin.y + (sub / 2) * HALF_CELL, HALF_CELL, HALF_CELL)
                )
            }
        }
        BalloonSticker.values().forEachIndexed { index, sticker ->
            if (sticker != BalloonSticker.NONE) {
                val position = index - 1
                val origin = slot(8 + position / 4)
                val sub = position % 4
                stickerRegions[index] = flipped(
                    TextureRegion(texture, origin.x + (sub % 2) * HALF_CELL, origin.y + (sub / 2) * HALF_CELL, HALF_CELL, HALF_CELL)
                )
            }
        }
        flipped(white)
        flipped(thinLine)
        flipped(thickLine)
    }

    // CLASSIC uses the plain glow (see Painter.exhaustPuff), every other style its own shape.
    fun exhaustPuff(style: ExhaustStyle): TextureRegion = exhaustStyleRegions[style.ordinal] ?: glow

    fun sticker(sticker: BalloonSticker): TextureRegion? = stickerRegions[sticker.ordinal]

    fun dispose() {
        texture.dispose()
    }

    private class Slot(val x: Int, val y: Int)

    private fun slot(index: Int) = Slot((index % COLUMNS) * CELL, (index / COLUMNS) * CELL)

    private fun region(slot: Slot, size: Int) = flipped(TextureRegion(texture, slot.x, slot.y, size, size))

    private fun iconRegion(slot: Slot, index: Int) =
        flipped(TextureRegion(texture, slot.x + (index % 4) * ICON_CELL, slot.y + (index / 4) * ICON_CELL, ICON_CELL, ICON_CELL))

    // The renderers use a y-down camera (so world coordinates match the old Canvas ones exactly),
    // which would otherwise draw every region upside down.
    private fun flipped(region: TextureRegion): TextureRegion = region.apply { flip(false, true) }

    companion object {
        const val WIDTH = 2048
        const val HEIGHT = 1024
        const val CELL = 256
        const val HALF_CELL = CELL / 2
        const val ICON_CELL = 64
        const val COLUMNS = WIDTH / CELL
        const val THIN_LINE_HEIGHT = 32
        const val LIGHTING_VARIANTS = 32

        // A disc fills this fraction of its region's half-size, leaving transparent padding so
        // mipmapped neighbours never bleed into each other.
        const val DISC_RADIUS_FRACTION = 0.4375f
        const val DISC_SPRITE_SCALE = 1f / DISC_RADIUS_FRACTION

        // Game-icon artwork is drawn this many times bigger than its world size (see
        // drawGameIcon) and race letters at RACE_ICON_SCALE - Painter scales them back down.
        const val GAME_ICON_SCALE = 3.2f
        const val RACE_ICON_SCALE = 2.5f
        // Exhaust-style puffs and stickers are drawn with a shape "size" of this many pixels
        // inside their 128px cell.
        const val PUFF_BAKE_RADIUS = 36f
        const val STICKER_BAKE_SIZE = 48f

        private val EXHAUST_STYLE_ORDER = listOf(
            ExhaustStyle.BUBBLES, ExhaustStyle.STARDUST, ExhaustStyle.HEARTS, ExhaustStyle.CLOUDS
        )

        private fun drawAtlas(bitmap: Bitmap) {
            val canvas = Canvas(bitmap)
            fun at(index: Int, size: Int = CELL, sub: Int = 0, subSize: Int = size, draw: (Canvas) -> Unit) {
                val x = (index % COLUMNS) * CELL + (sub % (CELL / subSize)) * subSize
                val y = (index / COLUMNS) * CELL + (sub / (CELL / subSize)) * subSize
                canvas.save()
                canvas.translate(x.toFloat(), y.toFloat())
                canvas.clipRect(0, 0, subSize, subSize)
                draw(canvas)
                canvas.restore()
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val center = CELL / 2f
            val discRadius = center * DISC_RADIUS_FRACTION * 2f

            at(0) { it.drawCircle(center, center, discRadius, paint.reset(Color.WHITE)) }

            at(1) {
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        center, center, center - 4f,
                        intArrayOf(Color.WHITE, Color.argb(150, 255, 255, 255), Color.argb(40, 255, 255, 255), Color.TRANSPARENT),
                        floatArrayOf(0f, 0.35f, 0.7f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                it.drawCircle(center, center, center - 4f, glowPaint)
            }

            at(2) {
                val path = Path().apply {
                    moveTo(center, TRIANGLE_APEX_Y)
                    lineTo(center + TRIANGLE_HALF_BASE, TRIANGLE_BASE_Y)
                    lineTo(center - TRIANGLE_HALF_BASE, TRIANGLE_BASE_Y)
                    close()
                }
                it.drawPath(path, paint.reset(Color.WHITE))
            }

            at(4) {
                it.drawRect(0f, 0f, 32f, 32f, paint.reset(Color.WHITE))
                drawLineStrip(it, 64f, THIN_LINE_HEIGHT.toFloat(), 7f)
                drawLineStrip(it, 128f, CELL.toFloat(), 3f)
            }

            PowerUpType.values().forEachIndexed { index, type ->
                at(5, sub = index, subSize = ICON_CELL) { drawGameIcon(it, type) }
                at(6, sub = index, subSize = ICON_CELL) { drawRaceIcon(it, type) }
            }

            EXHAUST_STYLE_ORDER.forEachIndexed { index, style ->
                at(7, sub = index, subSize = HALF_CELL) {
                    val puffPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        maskFilter = BlurMaskFilter(PUFF_BAKE_RADIUS * 0.28f, BlurMaskFilter.Blur.NORMAL)
                    }
                    style.drawPuff(it, puffPaint, HALF_CELL / 2f, HALF_CELL / 2f, PUFF_BAKE_RADIUS)
                }
            }

            BalloonSticker.values().filter { it != BalloonSticker.NONE }.forEachIndexed { position, sticker ->
                at(8 + position / 4, sub = position % 4, subSize = HALF_CELL) {
                    // Same ink colors GameView used, at full opacity - Painter applies the
                    // sticker's own 235/255 alpha (and the balloon's invisibility fade) on top.
                    val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F5F5F5") }
                    val detail = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(215, 0x21, 0x21, 0x21) }
                    it.translate(HALF_CELL / 2f, HALF_CELL / 2f)
                    sticker.drawInto(it, ink, detail, STICKER_BAKE_SIZE)
                }
            }

            for (index in 0 until LIGHTING_VARIANTS) {
                at(10 + index / 4, sub = index % 4, subSize = HALF_CELL) {
                    drawLightingVariant(it, index * 360f / LIGHTING_VARIANTS)
                }
            }

            at(18) { drawSparkle(it, center) }
        }

        // Balloons are egg-shaped and rotate to face their direction of travel, but their light
        // comes from a fixed direction (upper left) no matter which way they face - the old
        // renderer clipped world-aligned shading to the rotated silhouette every frame, which a
        // sprite can't do. Instead this bakes the world-aligned shading as seen from inside a
        // balloon rotated by `angleDeg` (and squashed/stretched like the body), so the lighting
        // sprite can simply share the body's own transform and still match its outline exactly.
        // With variants every 11.25 degrees, snapping to the nearest one never visibly steps.
        private fun drawLightingVariant(canvas: Canvas, angleDeg: Float) {
            val size = HALF_CELL
            val layer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val layerCanvas = Canvas(layer)
            val center = size / 2f
            val radius = center * DISC_RADIUS_FRACTION * 2f

            layerCanvas.save()
            layerCanvas.translate(center, center)
            layerCanvas.scale(radius, radius)
            layerCanvas.scale(1f / BALLOON_SQUASH, 1f / BALLOON_STRETCH)
            layerCanvas.rotate(-angleDeg)
            // From here on, units are "balloon radii" in world orientation (y down).
            val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    -0.32f, -0.38f, 1.65f,
                    intArrayOf(Color.TRANSPARENT, Color.argb(20, 0, 0, 0), Color.argb(120, 0, 0, 0)),
                    floatArrayOf(0f, 0.4f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            layerCanvas.drawRect(-2f, -2f, 2f, 2f, shade)
            val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    -0.3f, -0.4f, 0.36f,
                    intArrayOf(Color.argb(150, 255, 255, 255), Color.argb(70, 255, 255, 255), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            layerCanvas.drawCircle(-0.3f, -0.4f, 0.36f, highlight)
            // A small, sharper specular glint - reads as glossy latex rather than matte.
            val glint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    -0.36f, -0.47f, 0.1f,
                    intArrayOf(Color.argb(210, 255, 255, 255), Color.TRANSPARENT),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            layerCanvas.drawCircle(-0.36f, -0.47f, 0.1f, glint)
            layerCanvas.restore()

            // Cut it to the body's silhouette (in sprite space that's just the disc) with an
            // antialiased edge, rather than a hard clip: clear everything outside the circle. An
            // xfermode only touches the pixels its shape covers, hence the inverse-filled path.
            val outside = Path().apply {
                addCircle(center, center, radius, Path.Direction.CW)
                fillType = Path.FillType.INVERSE_WINDING
            }
            val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
            layerCanvas.drawPath(outside, clear)

            canvas.drawBitmap(layer, 0f, 0f, null)
            layer.recycle()
        }

        // Fully opaque in the middle, ramping to transparent over `ramp` pixels at the top and
        // bottom edges - see Atlas.thinLine.
        private fun drawLineStrip(canvas: Canvas, x: Float, height: Float, ramp: Float) {
            val paint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, height,
                    intArrayOf(Color.TRANSPARENT, Color.WHITE, Color.WHITE, Color.TRANSPARENT),
                    floatArrayOf(0f, ramp / height, 1f - ramp / height, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(x, 0f, x + 64f, height, paint)
        }

        private fun drawSparkle(canvas: Canvas, center: Float) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
            }
            val arm = center * 0.8f
            val waist = center * 0.12f
            val path = Path().apply {
                moveTo(center, center - arm)
                lineTo(center + waist, center - waist)
                lineTo(center + arm, center)
                lineTo(center + waist, center + waist)
                lineTo(center, center + arm)
                lineTo(center - waist, center + waist)
                lineTo(center - arm, center)
                lineTo(center - waist, center - waist)
                close()
            }
            canvas.drawPath(path, paint)
            canvas.drawCircle(center, center, center * 0.16f, paint)
        }

        // The exact icon artwork GameView drew around each power-up's center, scaled up by
        // GAME_ICON_SCALE so it stays sharp.
        private fun drawGameIcon(canvas: Canvas, type: PowerUpType) {
            val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                strokeCap = Paint.Cap.ROUND
                strokeWidth = 2.5f
            }
            canvas.translate(ICON_CELL / 2f, ICON_CELL / 2f)
            canvas.scale(GAME_ICON_SCALE, GAME_ICON_SCALE)
            val cx = 0f
            val cy = 0f
            when (type) {
                PowerUpType.SPEED -> for (i in 0..1) {
                    val ox = cx - 5f + i * 7f
                    canvas.drawLine(ox - 4f, cy - 6f, ox + 4f, cy, iconPaint)
                    canvas.drawLine(ox + 4f, cy, ox - 4f, cy + 6f, iconPaint)
                }
                PowerUpType.GROWTH -> {
                    canvas.drawLine(cx - 7f, cy, cx + 7f, cy, iconPaint)
                    canvas.drawLine(cx, cy - 7f, cx, cy + 7f, iconPaint)
                }
                PowerUpType.INVISIBILITY -> {
                    iconPaint.style = Paint.Style.STROKE
                    canvas.drawCircle(cx, cy, 7f, iconPaint)
                    iconPaint.style = Paint.Style.FILL
                    canvas.drawLine(cx - 8f, cy - 8f, cx + 8f, cy + 8f, iconPaint)
                }
                PowerUpType.SHIELD -> {
                    iconPaint.style = Paint.Style.STROKE
                    val path = Path().apply {
                        moveTo(cx, cy - 8f)
                        lineTo(cx + 7f, cy - 4f)
                        lineTo(cx + 7f, cy + 4f)
                        lineTo(cx, cy + 9f)
                        lineTo(cx - 7f, cy + 4f)
                        lineTo(cx - 7f, cy - 4f)
                        close()
                    }
                    canvas.drawPath(path, iconPaint)
                }
                PowerUpType.REPEL -> {
                    val dirs = arrayOf(0.707f to 0.707f, -0.707f to 0.707f, 0.707f to -0.707f, -0.707f to -0.707f)
                    for ((dx, dy) in dirs) {
                        canvas.drawLine(cx + dx * 3f, cy + dy * 3f, cx + dx * 9f, cy + dy * 9f, iconPaint)
                    }
                }
                PowerUpType.FREEZE -> {
                    canvas.drawLine(cx - 8f, cy, cx + 8f, cy, iconPaint)
                    canvas.drawLine(cx - 6f, cy - 6f, cx + 6f, cy + 6f, iconPaint)
                    canvas.drawLine(cx - 6f, cy + 6f, cx + 6f, cy - 6f, iconPaint)
                }
                PowerUpType.HOOK -> {
                    iconPaint.style = Paint.Style.STROKE
                    val path = Path().apply {
                        moveTo(cx - 3f, cy - 8f)
                        lineTo(cx - 3f, cy + 2f)
                        quadTo(cx - 3f, cy + 8f, cx + 4f, cy + 6f)
                    }
                    canvas.drawPath(path, iconPaint)
                }
                PowerUpType.SPEED_UP -> for (i in 0..1) {
                    val oy = cy + 5f - i * 7f
                    canvas.drawLine(cx - 6f, oy + 4f, cx, oy - 4f, iconPaint)
                    canvas.drawLine(cx, oy - 4f, cx + 6f, oy + 4f, iconPaint)
                }
                PowerUpType.AGILITY_UP -> {
                    iconPaint.style = Paint.Style.STROKE
                    canvas.drawArc(cx - 7f, cy - 7f, cx + 7f, cy + 7f, -30f, 270f, false, iconPaint)
                    iconPaint.style = Paint.Style.FILL
                    canvas.drawLine(cx + 6f, cy - 4f, cx + 9f, cy - 1f, iconPaint)
                    canvas.drawLine(cx + 6f, cy - 4f, cx + 3f, cy - 6f, iconPaint)
                }
                PowerUpType.POTENCY_UP -> {
                    val boltPath = Path().apply {
                        moveTo(cx + 2f, cy - 9f)
                        lineTo(cx - 4f, cy + 1f)
                        lineTo(cx, cy + 1f)
                        lineTo(cx - 2f, cy + 9f)
                        lineTo(cx + 5f, cy - 1f)
                        lineTo(cx + 1f, cy - 1f)
                        close()
                    }
                    canvas.drawPath(boltPath, iconPaint)
                }
            }
        }

        // RaceView's letter labels, baked at a fixed size - Painter scales them with the
        // power-up's own radius, same as the old textSize = radius.
        private fun drawRaceIcon(canvas: Canvas, type: PowerUpType) {
            val label = when (type) {
                PowerUpType.SPEED -> ">>"
                PowerUpType.GROWTH -> "+"
                PowerUpType.INVISIBILITY -> "?"
                PowerUpType.SHIELD -> "S"
                PowerUpType.REPEL -> "R"
                PowerUpType.FREEZE -> "F"
                PowerUpType.HOOK -> "H"
                PowerUpType.SPEED_UP -> "SU"
                PowerUpType.AGILITY_UP -> "AU"
                PowerUpType.POTENCY_UP -> "PU"
            }
            val textSize = 16f * RACE_ICON_SCALE
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                this.textSize = textSize
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(label, ICON_CELL / 2f, ICON_CELL / 2f + textSize * 0.35f, paint)
        }

        private fun Paint.reset(color: Int): Paint = apply {
            this.color = color
            style = Paint.Style.FILL
            shader = null
            maskFilter = null
        }

        const val BALLOON_SQUASH = 0.94f
        const val BALLOON_STRETCH = 1.12f

        // Triangle geometry inside its 256px cell - Painter.triangle maps it onto any base/tip.
        const val TRIANGLE_APEX_Y = 24f
        const val TRIANGLE_BASE_Y = 232f
        const val TRIANGLE_HALF_BASE = 104f
    }
}

// A texture whose pixels are drawn by `draw` into an Android bitmap. Managed: if the GL
// context is ever lost, libGDX simply calls prepare() again and the art is redrawn - nothing
// has to be kept around in memory just in case.
class GeneratedTextureData(
    private val width: Int,
    private val height: Int,
    private val useMipMaps: Boolean,
    private val draw: (Bitmap) -> Unit
) : TextureData {
    private var pixmap: Pixmap? = null

    override fun getType() = TextureData.TextureDataType.Pixmap
    override fun isPrepared() = pixmap != null

    override fun prepare() {
        if (pixmap != null) throw GdxRuntimeException("Already prepared")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        draw(bitmap)
        pixmap = bitmap.toPremultipliedPixmap()
        bitmap.recycle()
    }

    override fun consumePixmap(): Pixmap {
        val result = pixmap ?: throw GdxRuntimeException("Call prepare() first")
        pixmap = null
        return result
    }

    override fun disposePixmap() = true
    override fun consumeCustomData(target: Int) = throw GdxRuntimeException("Not a custom texture")
    override fun getWidth() = width
    override fun getHeight() = height
    override fun getFormat() = Pixmap.Format.RGBA8888
    override fun useMipMaps() = useMipMaps
    override fun isManaged() = true
}

// ARGB_8888 bitmaps already sit in memory as premultiplied R,G,B,A bytes - exactly an RGBA8888
// pixmap's layout - so this is a straight copy.
fun Bitmap.toPremultipliedPixmap(): Pixmap {
    val pixmap = Pixmap(width, height, Pixmap.Format.RGBA8888)
    pixmap.blending = Pixmap.Blending.None
    val buffer = pixmap.pixels
    buffer.position(0)
    copyPixelsToBuffer(buffer)
    buffer.position(0)
    return pixmap
}
