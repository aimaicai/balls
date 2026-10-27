package com.hyperionsoftware.balls.ui

import android.graphics.Color
import com.hyperionsoftware.balls.game.Blob
import com.hyperionsoftware.balls.game.GameConfig
import com.hyperionsoftware.balls.game.GameEngine
import com.hyperionsoftware.balls.game.PowerUpType
import com.hyperionsoftware.balls.score.HighScores
import com.hyperionsoftware.balls.ui.gdx.Painter
import com.hyperionsoftware.balls.ui.gdx.TextStyle
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Classic mode's heads-up display: a solid bar across the top of the screen - the arena is
// drawn below it (see GameRenderer.draw), so the player never ends up playing underneath it -
// and the kill feed just under it. All of it in dp, so it's the same size on every phone.
// - On the left, the permanent stats: per stat its power-up's icon in its color and a row of
//   pips, the newest one lighting up when it's just been earned.
// - In the middle, the balloons still in play, the match time and the score (which punches up,
//   with a "+N" next to it, whenever an absorb or the final round adds to it).
// - On the right, room for the pause button (an Android view laid over it, see
//   activity_game.xml - its size and margins match BAR_HEIGHT).
// - The bar's bottom edge is the safe zone's gauge: how much of it is left, the slice about to
//   go blinking while it holds, pulsing while it shrinks.
// - Under the bar, the feed: up to three lines, each on a dark chip, the player's own marked.
// Drawn in two passes like the rest of the HUD (see GameRenderer.draw): every shape from the
// atlas first, then all the text.
class GameHud(private val painter: Painter, private val density: Float) {
    enum class FeedKind { OTHER, PLAYER_GAIN, PLAYER_LOSS }

    private class FeedEntry(val text: String, val kind: FeedKind, var ttl: Float)
    private class ScorePop(val text: String, var elapsed: Float = 0f)

    private val valueStyle = TextStyle(15f * density, bold = true)
    private val popStyle = TextStyle(13f * density, bold = true)
    private val feedStyle = TextStyle(12f * density)

    private val feed = ArrayList<FeedEntry>()
    private val pops = ArrayList<ScorePop>()
    private val statFlash = FloatArray(3)

    // Each value's text only changes when its number does.
    private var playersValue = Int.MIN_VALUE
    private var playersText = ""
    private var timeValue = Int.MIN_VALUE
    private var timeText = ""
    private var scoreValue = Int.MIN_VALUE
    private var scoreText = ""
    private var lastAbsorbed = 0
    private var lastFinalRound = false
    private var scorePunch = 0f

    // Where each value sits this frame, worked out by the shapes pass and reused by the text pass.
    private var playersX = 0f
    private var timeX = 0f
    private var scoreX = 0f
    private var scoreWidth = 0f

    // The bar's full height, its zone gauge included - the arena starts right below it.
    val barHeight: Float get() = (BAR_HEIGHT + GAUGE_HEIGHT) * density

    // How far down the screen the HUD reaches, feed included - the achievement popups go below it.
    val bottom: Float get() = barHeight + (FEED_GAP + MAX_FEED_ENTRIES * FEED_ROW) * density

    fun reset() {
        feed.clear()
        pops.clear()
        statFlash.fill(0f)
        playersValue = Int.MIN_VALUE
        timeValue = Int.MIN_VALUE
        scoreValue = Int.MIN_VALUE
        lastAbsorbed = 0
        lastFinalRound = false
        scorePunch = 0f
    }

    fun addFeed(text: String, kind: FeedKind) {
        feed.add(FeedEntry(text, kind, if (kind == FeedKind.OTHER) OTHER_FEED_SECONDS else PLAYER_FEED_SECONDS))
        if (feed.size > MAX_FEED_ENTRIES) feed.removeAt(0)
    }

    // A permanent stat just went up: 0 speed, 1 agility, 2 potency.
    fun flashStat(stat: Int) {
        statFlash[stat] = 1f
    }

    fun drawShapes(engine: GameEngine, width: Float, dt: Float) {
        updateValues(engine, dt)
        painter.color(BAR_COLOR)
        painter.rect(0f, 0f, width, BAR_HEIGHT * density)
        val statsRight = drawStats(engine.player)
        drawValues(width, statsRight)
        drawZoneGauge(engine, width)
        drawFeedChips()
    }

    fun drawText(dt: Float) {
        val d = density
        val baseline = BAR_HEIGHT * d / 2f + valueStyle.size * 0.36f
        painter.text(playersText, playersX, baseline, valueStyle, Color.WHITE, 0.9f)
        painter.text(timeText, timeX, baseline, valueStyle, Color.WHITE, 0.9f)
        // The score punches up from its own center when it jumps.
        val scale = 1f + 0.3f * scorePunch * scorePunch
        painter.textScaledAround(
            scoreText, scoreX, baseline, valueStyle, ACCENT_COLOR, 1f, scale,
            scoreX + scoreWidth / 2f, baseline - valueStyle.size * 0.36f
        )

        // "+N" floats up and away from the score's right side.
        val popIterator = pops.iterator()
        while (popIterator.hasNext()) {
            val pop = popIterator.next()
            pop.elapsed += dt
            if (pop.elapsed >= POP_SECONDS) {
                popIterator.remove()
                continue
            }
            val t = pop.elapsed / POP_SECONDS
            painter.text(pop.text, scoreX + scoreWidth + 5f * d, baseline - 10f * d * t, popStyle, ACCENT_COLOR, 1f - t * t)
        }

        val feedIterator = feed.iterator()
        var row = 0
        while (feedIterator.hasNext()) {
            val entry = feedIterator.next()
            entry.ttl -= dt
            if (entry.ttl <= 0f) {
                feedIterator.remove()
                continue
            }
            val color = when (entry.kind) {
                FeedKind.OTHER -> Color.WHITE
                FeedKind.PLAYER_GAIN -> PLAYER_GAIN_COLOR
                FeedKind.PLAYER_LOSS -> THREAT_COLOR
            }
            val strength = if (entry.kind == FeedKind.OTHER) 0.8f else 1f
            val y = feedRowTop(row) + FEED_CHIP_HEIGHT * d / 2f + feedStyle.size * 0.35f
            painter.text(entry.text, (FEED_LEFT + FEED_CHIP_PADDING) * d, y, feedStyle, color, strength * fadeOf(entry))
            row++
        }
    }

    private fun updateValues(engine: GameEngine, dt: Float) {
        val players = engine.aliveCount()
        if (players != playersValue) {
            playersValue = players
            playersText = players.toString()
        }
        val seconds = engine.matchElapsed.toInt()
        if (seconds != timeValue) {
            timeValue = seconds
            timeText = "%02d:%02d".format(seconds / 60, seconds % 60)
        }
        // playerWon is always false here since the match is still running - this omits the win
        // bonus HighScores.computeScore adds once it actually ends.
        val score = HighScores.computeScore(
            playerWon = false,
            elapsedSeconds = seconds,
            opponentsAbsorbed = engine.playerOpponentsAbsorbed,
            reachedFinalRound = engine.isFinalRoundActive
        )
        if (score != scoreValue) {
            // Only what the player did earns a pop - not the points every second brings.
            val earned = engine.playerOpponentsAbsorbed != lastAbsorbed || engine.isFinalRoundActive != lastFinalRound
            if (earned && scoreValue != Int.MIN_VALUE && score > scoreValue) {
                pops.add(ScorePop("+${score - scoreValue}"))
                scorePunch = 1f
            }
            scoreValue = score
            scoreText = score.toString()
        }
        lastAbsorbed = engine.playerOpponentsAbsorbed
        lastFinalRound = engine.isFinalRoundActive
        scorePunch = max(0f, scorePunch - dt / PUNCH_SECONDS)
        for (i in statFlash.indices) statFlash[i] = max(0f, statFlash[i] - dt / STAT_FLASH_SECONDS)
    }

    // The stats block on the left. Returns its right edge.
    private fun drawStats(player: Blob): Float {
        val d = density
        val pip = PIP_SIZE * d
        val pipGap = PIP_GAP * d
        val tiers = GameConfig.PERMANENT_STAT_TIER_COUNT
        val icon = STAT_ICON_SIZE * d
        val left = SIDE_PADDING * d
        val pipsLeft = left + icon + STAT_ICON_GAP * d
        val rowsTop = (BAR_HEIGHT - STAT_ROW * 3f) * d / 2f
        for (stat in 0 until 3) {
            val type = STAT_TYPES[stat]
            val multiplier = when (stat) {
                0 -> player.permanentSpeedMultiplier
                1 -> player.permanentTurnRateMultiplier
                else -> player.permanentPotencyMultiplier
            }
            val maxMultiplier = when (stat) {
                0 -> GameConfig.PERMANENT_SPEED_MAX_MULTIPLIER
                1 -> GameConfig.PERMANENT_TURN_RATE_MAX_MULTIPLIER
                else -> GameConfig.PERMANENT_POTENCY_MAX_MULTIPLIER
            }
            val fraction = ((multiplier - 1f) / (maxMultiplier - 1f)).coerceIn(0f, 1f)
            val filled = Math.round(fraction * tiers).coerceIn(0, tiers)
            val color = GameRenderer.powerUpColor(type)
            val cy = rowsTop + STAT_ROW * d * (stat + 0.5f)
            painter.color(color, if (filled > 0) 1f else 0.55f)
            // The icon's artwork fills a little under its cell, so it's drawn a touch bigger.
            painter.region(painter.atlas.gameIcons[type.ordinal], left + icon / 2f, cy, icon * 1.25f, icon * 1.25f)
            for (i in 0 until tiers) {
                painter.color(if (i < filled) color else Color.WHITE, if (i < filled) 1f else 0.12f)
                painter.roundedRect(pipsLeft + i * (pip + pipGap), cy - pip / 2f, pip, pip, 2f * d)
            }
            val flash = statFlash[stat]
            if (flash > 0f && filled > 0) {
                val px = pipsLeft + (filled - 1) * (pip + pipGap) + pip / 2f
                painter.additive(true)
                painter.color(color, flash)
                painter.glow(px, cy, pip * (1.6f + 1.6f * (1f - flash)))
                painter.color(Color.WHITE, 0.8f * flash)
                painter.rect(px - pip / 2f, cy - pip / 2f, pip, pip)
                painter.additive(false)
            }
        }
        return pipsLeft + tiers * pip + (tiers - 1) * pipGap
    }

    // The three values and their icons, centered on the screen when there's room - otherwise in
    // the middle of the space between the stats and the pause button. Each value keeps the width
    // of its widest digits, so nothing twitches as the numbers change.
    private fun drawValues(width: Float, statsRight: Float) {
        val d = density
        val playersWidth = max(painter.measure(playersText, valueStyle), painter.measure("000", valueStyle))
        val timeWidth = max(painter.measure(timeText, valueStyle), painter.measure("00:00", valueStyle))
        scoreWidth = max(painter.measure(scoreText, valueStyle), painter.measure("0000", valueStyle))
        val icon = ICON_SIZE * d
        val gap = ICON_GAP * d
        val segmentGap = SEGMENT_GAP * d
        val groupWidth = (icon + gap) * 3f + playersWidth + timeWidth + scoreWidth + segmentGap * 2f
        val minLeft = statsRight + SECTION_GAP * d
        val maxRight = width - PAUSE_AREA * d
        val left = if (maxRight - minLeft <= groupWidth) {
            minLeft
        } else {
            (width / 2f - groupWidth / 2f).coerceIn(minLeft, maxRight - groupWidth)
        }
        val cy = BAR_HEIGHT * d / 2f
        val height = BAR_HEIGHT * d

        var x = left
        drawBalloonIcon(x + icon / 2f, cy, icon)
        playersX = x + icon + gap
        x = playersX + playersWidth + segmentGap
        divider(x - segmentGap / 2f, cy, height)
        drawClockIcon(x + icon / 2f, cy, icon)
        timeX = x + icon + gap
        x = timeX + timeWidth + segmentGap
        divider(x - segmentGap / 2f, cy, height)
        painter.color(ACCENT_COLOR)
        painter.region(painter.atlas.sparkle, x + icon / 2f, cy, icon * 1.5f, icon * 1.5f)
        scoreX = x + icon + gap
        // A faint rule setting the stats apart from the values.
        divider(statsRight + SECTION_GAP * d / 2f, cy, height * 1.3f)
    }

    private fun divider(x: Float, cy: Float, height: Float) {
        painter.color(Color.WHITE, 0.1f)
        painter.rect(x - density * 0.5f, cy - height * 0.25f, density, height * 0.5f)
    }

    private fun drawBalloonIcon(cx: Float, cy: Float, size: Float) {
        val r = size * 0.36f
        val bodyY = cy - size * 0.08f
        painter.color(Color.WHITE, 0.85f)
        painter.filledCircle(cx, bodyY, r)
        painter.triangle(cx, bodyY + r + size * 0.16f, cx, bodyY + r - size * 0.04f, size * 0.26f)
    }

    private fun drawClockIcon(cx: Float, cy: Float, size: Float) {
        val r = size * 0.4f
        painter.color(Color.WHITE, 0.85f)
        painter.circleStroke(cx, cy, r, 1.6f * density)
        painter.line(cx, cy, cx, cy - r * 0.6f, 1.6f * density)
        painter.line(cx, cy, cx + r * 0.45f, cy, 1.6f * density)
    }

    // The bar's bottom edge, full width: what's left of the safe zone in red, and while it holds,
    // the slice the next shrink will take, blinking; while it shrinks it pulses, and in the final
    // round it burns down for good.
    private fun drawZoneGauge(engine: GameEngine, width: Float) {
        val top = BAR_HEIGHT * density
        val height = GAUGE_HEIGHT * density
        val pulse = 0.5f + 0.5f * sin(engine.matchElapsed * 6f)
        painter.color(GAUGE_TRACK_COLOR)
        painter.rect(0f, top, width, height)

        val remaining: Float
        if (engine.isFinalRoundActive) {
            remaining = (engine.safeZoneRadius / GameConfig.SAFE_ZONE_FINAL_START_RADIUS).coerceIn(0f, 1f)
            painter.color(SAFE_ZONE_COLOR, 0.65f + 0.35f * pulse)
        } else {
            remaining = 1f - engine.safeZoneProgress
            painter.color(SAFE_ZONE_COLOR, if (engine.isZoneHolding) 0.95f else 0.7f + 0.3f * pulse)
        }
        // Drawn from the middle out, so it closes in from both sides like the zone itself.
        val half = width / 2f
        painter.rect(half - half * remaining, top, width * remaining, height)

        if (!engine.isFinalRoundActive && engine.isZoneHolding) {
            val next = ((engine.nextSafeZoneRadius - GameConfig.SAFE_ZONE_MIN_RADIUS) /
                (GameConfig.SAFE_ZONE_INITIAL_RADIUS - GameConfig.SAFE_ZONE_MIN_RADIUS)).coerceIn(0f, 1f)
            if (next < remaining) {
                val slice = half * (remaining - next)
                painter.color(NEXT_ZONE_COLOR, 0.35f + 0.6f * pulse)
                painter.rect(half - half * remaining, top, slice, height)
                painter.rect(half + half * next, top, slice, height)
            }
        }
    }

    private fun drawFeedChips() {
        val d = density
        for (row in feed.indices) {
            val entry = feed[row]
            val textWidth = painter.measure(entry.text, feedStyle)
            val top = feedRowTop(row)
            val fade = fadeOf(entry)
            painter.color(BAR_COLOR, (if (entry.kind == FeedKind.OTHER) 0.5f else 0.65f) * fade)
            painter.roundedRect(FEED_LEFT * d, top, textWidth + FEED_CHIP_PADDING * 2f * d, FEED_CHIP_HEIGHT * d, 6f * d)
            if (entry.kind != FeedKind.OTHER) {
                // A stripe of the line's color down the chip's edge.
                painter.color(if (entry.kind == FeedKind.PLAYER_GAIN) PLAYER_GAIN_COLOR else THREAT_COLOR, fade)
                painter.roundedRect(FEED_LEFT * d, top + 3f * d, 3f * d, (FEED_CHIP_HEIGHT - 6f) * d, 1.5f * d)
            }
        }
    }

    private fun feedRowTop(row: Int): Float = barHeight + (FEED_GAP + row * FEED_ROW) * density

    // Each line holds steady, then fades out over its last half second.
    private fun fadeOf(entry: FeedEntry): Float = (entry.ttl / FEED_FADE_SECONDS).coerceIn(0f, 1f)

    companion object {
        // All in dp. BAR_HEIGHT must match the pause button's layout in activity_game.xml.
        private const val BAR_HEIGHT = 62f
        private const val GAUGE_HEIGHT = 4f
        private const val SIDE_PADDING = 14f
        // The pause button's width plus its margin, and a little air.
        private const val PAUSE_AREA = 64f
        private const val SECTION_GAP = 18f
        private const val ICON_SIZE = 14f
        private const val ICON_GAP = 6f
        private const val SEGMENT_GAP = 22f
        private const val STAT_ROW = 16f
        private const val STAT_ICON_SIZE = 13f
        private const val STAT_ICON_GAP = 7f
        private const val PIP_SIZE = 8f
        private const val PIP_GAP = 3f
        private const val FEED_GAP = 8f
        private const val FEED_LEFT = 12f
        private const val FEED_ROW = 23f
        private const val FEED_CHIP_HEIGHT = 20f
        private const val FEED_CHIP_PADDING = 8f
        private const val MAX_FEED_ENTRIES = 3

        private const val OTHER_FEED_SECONDS = 3.5f
        private const val PLAYER_FEED_SECONDS = 5f
        private const val FEED_FADE_SECONDS = 0.5f
        private const val POP_SECONDS = 0.9f
        private const val PUNCH_SECONDS = 0.3f
        private const val STAT_FLASH_SECONDS = 0.9f

        private val STAT_TYPES = arrayOf(PowerUpType.SPEED_UP, PowerUpType.AGILITY_UP, PowerUpType.POTENCY_UP)
        private val BAR_COLOR = Color.parseColor("#0A0F16")
        private val GAUGE_TRACK_COLOR = Color.parseColor("#2A1416")
        private val ACCENT_COLOR = Color.parseColor("#FFC107")
        private val SAFE_ZONE_COLOR = Color.parseColor("#EF5350")
        private val NEXT_ZONE_COLOR = Color.parseColor("#FFD54F")
        private val PLAYER_GAIN_COLOR = Color.parseColor("#8BC34A")
        private val THREAT_COLOR = Color.parseColor("#EF5350")
    }
}
