package com.hyperionsoftware.balls.score

import android.content.Context

data class ScoreEntry(
    val initials: String,
    val score: Int,
    val playerWon: Boolean,
    val finalRadius: Int,
    val opponentsAbsorbed: Int,
    val elapsedSeconds: Int,
    val timestampMillis: Long
)

// Local top-scores history, persisted via SharedPreferences as a simple delimited string -
// this is a single-player, offline game, so there's no backend to post scores to, just
// enough to remember what you've done on this device across launches. The extra match
// details beyond initials/score are kept around even though the leaderboard screen itself
// only shows rank/initials/score (old-school arcade style) - harmless to keep, and available
// if a more detailed view is ever wanted later.
object HighScores {
    private const val PREFS_NAME = "high_scores"
    private const val KEY_ENTRIES = "entries"
    const val MAX_ENTRIES = 10
    private const val ENTRY_SEPARATOR = ";"
    private const val FIELD_SEPARATOR = ","

    // Absorbing opponents is the core skill the game is about, so it dominates the score;
    // final size and a flat win bonus round it out so a long, cautious survival still counts
    // for something even with few absorbs.
    fun computeScore(playerWon: Boolean, finalRadius: Int, opponentsAbsorbed: Int): Int {
        return opponentsAbsorbed * 100 + finalRadius * 3 + if (playerWon) 1000 else 0
    }

    // Whether this score would actually make the saved top MAX_ENTRIES - classic arcades
    // only ever ask for initials when you've actually earned a spot on the board.
    fun wouldRank(context: Context, score: Int): Boolean {
        val entries = loadAll(context)
        if (entries.size < MAX_ENTRIES) return true
        return score > entries.minOf { it.score }
    }

    // Scores this match, appends it to the saved history, trims to the top MAX_ENTRIES, and
    // returns the updated list so the caller can show it without a separate read.
    fun recordMatch(
        context: Context,
        initials: String,
        playerWon: Boolean,
        finalRadius: Int,
        opponentsAbsorbed: Int,
        elapsedSeconds: Int
    ): List<ScoreEntry> {
        val entry = ScoreEntry(
            initials = initials.take(3).uppercase(),
            score = computeScore(playerWon, finalRadius, opponentsAbsorbed),
            playerWon = playerWon,
            finalRadius = finalRadius,
            opponentsAbsorbed = opponentsAbsorbed,
            elapsedSeconds = elapsedSeconds,
            timestampMillis = System.currentTimeMillis()
        )
        val updated = (loadAll(context) + entry).sortedByDescending { it.score }.take(MAX_ENTRIES)
        save(context, updated)
        return updated
    }

    fun loadAll(context: Context): List<ScoreEntry> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ENTRIES, null) ?: return emptyList()
        return raw.split(ENTRY_SEPARATOR).mapNotNull { chunk -> parseEntry(chunk) }
    }

    private fun parseEntry(chunk: String): ScoreEntry? {
        if (chunk.isBlank()) return null
        val parts = chunk.split(FIELD_SEPARATOR)
        if (parts.size != 7) return null
        return runCatching {
            ScoreEntry(
                initials = parts[0],
                score = parts[1].toInt(),
                playerWon = parts[2].toBoolean(),
                finalRadius = parts[3].toInt(),
                opponentsAbsorbed = parts[4].toInt(),
                elapsedSeconds = parts[5].toInt(),
                timestampMillis = parts[6].toLong()
            )
        }.getOrNull()
    }

    private fun save(context: Context, entries: List<ScoreEntry>) {
        val raw = entries.joinToString(ENTRY_SEPARATOR) { e ->
            listOf(e.initials, e.score, e.playerWon, e.finalRadius, e.opponentsAbsorbed, e.elapsedSeconds, e.timestampMillis)
                .joinToString(FIELD_SEPARATOR)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENTRIES, raw)
            .apply()
    }
}
