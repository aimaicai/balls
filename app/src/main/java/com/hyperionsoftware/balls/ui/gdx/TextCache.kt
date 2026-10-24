package com.hyperionsoftware.balls.ui.gdx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import kotlin.math.ceil

// A text size/weight/alignment, mirroring the Android Paints the old Canvas HUD used (sizes are
// in the same raw pixels). Text is always rasterized white and tinted at draw time, so color
// and alpha aren't part of the style.
class TextStyle(val size: Float, val bold: Boolean = false, val align: Align = Align.LEFT) {
    enum class Align { LEFT, CENTER, RIGHT }

    internal val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = size
        isFakeBoldText = bold
    }
}

// Renders strings with Android's own text engine (same typeface, fake-bold and accented glyphs
// as before - no bitmap font to ship or keep in sync) into one shared texture, so all the text
// in a frame draws from a single texture instead of one draw call per string. Game text changes
// rarely (a HUD number ticking, an event callout), so after the first frame almost every lookup
// is a hit.
//
// Space is handed out in horizontal shelves, one height class per shelf. When the texture is
// full, the shelf whose strings have gone unused the longest is wiped and reused - never one
// drawn this frame or the last, since those may still be waiting in the batch.
class TextCache {
    class Entry(
        val region: TextureRegion,
        val width: Float,
        val baselineFromTop: Float,
        val paddingLeft: Float,
        val shelf: Shelf
    ) {
        var lastUsedFrame = 0L
    }

    class Shelf(val y: Int, val height: Int) {
        var cursorX = 0
        var lastUsedFrame = 0L
        val keys = ArrayList<Key>()
    }

    class Key(var text: String, var style: TextStyle) {
        override fun equals(other: Any?) = other is Key && other.style === style && other.text == text
        override fun hashCode() = text.hashCode() * 31 + System.identityHashCode(style)
    }

    // Set by the texture's own (re)generation - true right after a lost GL context was restored,
    // when everything packed into it so far is gone.
    @Volatile private var contentsLost = false
    val texture = Texture(GeneratedTextureData(WIDTH, HEIGHT, useMipMaps = false) { contentsLost = true }).apply {
        setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
    }

    private val entries = HashMap<Key, Entry>(256)
    private val shelves = ArrayList<Shelf>()
    private var nextShelfY = 0
    private var frame = 0L
    // Reused for lookups, so a cache hit allocates nothing.
    private val lookup = Key("", TextStyle(1f))

    fun beginFrame() {
        frame++
        if (contentsLost) {
            contentsLost = false
            entries.clear()
            shelves.clear()
            nextShelfY = 0
        }
    }

    fun get(text: String, style: TextStyle): Entry {
        lookup.text = text
        lookup.style = style
        val entry = entries[lookup] ?: create(text, style)
        entry.lastUsedFrame = frame
        entry.shelf.lastUsedFrame = frame
        return entry
    }

    fun dispose() {
        texture.dispose()
        entries.clear()
        shelves.clear()
    }

    private fun create(text: String, style: TextStyle): Entry {
        val paint = style.paint
        val metrics = paint.fontMetrics
        val textWidth = paint.measureText(text)
        // Transparent padding all around, so filtering never bleeds a neighbour in.
        val bitmapWidth = (ceil(textWidth).toInt() + PADDING * 2).coerceIn(1, WIDTH)
        val bitmapHeight = (ceil(metrics.descent - metrics.ascent).toInt() + PADDING * 2).coerceAtMost(HEIGHT)
        val baselineFromTop = PADDING - metrics.ascent

        val shelf = shelfFor(bitmapWidth, bitmapHeight)
        val x = shelf.cursorX
        shelf.cursorX += bitmapWidth

        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(text, PADDING.toFloat(), baselineFromTop, paint)
        val pixmap = bitmap.toPremultipliedPixmap()
        bitmap.recycle()
        // The shelf's area may still hold an older, longer string - but nothing outside this
        // string's own rectangle is ever sampled for it, so only that rectangle is written.
        // Uploaded directly rather than through Texture.draw, which refuses managed textures.
        texture.bind()
        Gdx.gl.glPixelStorei(GL20.GL_UNPACK_ALIGNMENT, 1)
        Gdx.gl.glTexSubImage2D(
            GL20.GL_TEXTURE_2D, 0, x, shelf.y, bitmapWidth, bitmapHeight,
            pixmap.glFormat, pixmap.glType, pixmap.pixels
        )
        pixmap.dispose()

        val region = TextureRegion(texture, x, shelf.y, bitmapWidth, bitmapHeight).apply { flip(false, true) }
        val key = Key(text, style)
        val entry = Entry(region, textWidth, baselineFromTop, PADDING.toFloat(), shelf)
        entries[key] = entry
        shelf.keys.add(key)
        return entry
    }

    private fun shelfFor(width: Int, height: Int): Shelf {
        val shelfHeight = ((height + SHELF_STEP - 1) / SHELF_STEP) * SHELF_STEP
        shelves.firstOrNull { it.height == shelfHeight && WIDTH - it.cursorX >= width }?.let { return it }
        if (nextShelfY + shelfHeight <= HEIGHT) {
            return Shelf(nextShelfY, shelfHeight).also {
                nextShelfY += shelfHeight
                shelves.add(it)
            }
        }
        // Full: recycle the least recently used shelf that's tall enough and hasn't been drawn
        // from this frame or the last. Failing that (only possible with an absurd amount of
        // different text on screen at once), the least recently used tall-enough one regardless,
        // and if not even that exists, start the whole texture over.
        val candidate = shelves
            .filter { it.height >= shelfHeight && it.lastUsedFrame < frame - 1 }
            .minByOrNull { it.lastUsedFrame }
            ?: shelves.filter { it.height >= shelfHeight }.minByOrNull { it.lastUsedFrame }
            ?: run {
                entries.clear()
                shelves.clear()
                nextShelfY = shelfHeight
                return Shelf(0, shelfHeight).also { shelves.add(it) }
            }
        for (key in candidate.keys) entries.remove(key)
        candidate.keys.clear()
        candidate.cursorX = 0
        return candidate
    }

    companion object {
        private const val WIDTH = 2048
        private const val HEIGHT = 1024
        private const val PADDING = 3
        private const val SHELF_STEP = 8
    }
}
