package com.hyperionsoftware.balls.score

import android.content.Context

data class ScoreEntry(
    val score: Int,
    val playerWon: Boolean,
    val finalRadius: Int,
    val opponentsAbsorbed: Int,
    val elapsedSeconds: Int,
    val timestampMillis: Long
)

// Local top-scores history, persisted via SharedPreferences as a simple delimited string -
// this is a single-player, offline game, so there's no backend to post scores to, just
// enough to remember what you've done on this device across launches.
object HighScores {
    private const val PREFS_NAME = "high_scores"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 10
    private const val ENTRY_SEPARATOR = ";"
    private const val FIELD_SEPARATOR = ","

    // Absorbing opponents is the core skill the game is about, so it dominates the score;
    // final size and a flat win bonus round it out so a long, cautious survival still counts
    // for something even with few absorbs.
    fun computeScore(playerWon: Boolean, finalRadius: Int, opponentsAbsorbed: Int): Int {
        return opponentsAbsorbed * 100 + finalRadius * 3 + if (playerWon) 1000 else 0
    }

    // Scores this match, appends it to the saved history, trims to the top MAX_ENTRIES, and
    // returns the updated list so the caller can show it (and detect a new personal best)
    // without a separate read.
    fun recordMatch(
        context: Context,
        playerWon: Boolean,
        finalRadius: Int,
        opponentsAbsorbed: Int,
        elapsedSeconds: Int
    ): List<ScoreEntry> {
        val entry = ScoreEntry(
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
        if (parts.size != 6) return null
        return runCatching {
            ScoreEntry(
                score = parts[0].toInt(),
                playerWon = parts[1].toBoolean(),
                finalRadius = parts[2].toInt(),
                opponentsAbsorbed = parts[3].toInt(),
                elapsedSeconds = parts[4].toInt(),
                timestampMillis = parts[5].toLong()
            )
        }.getOrNull()
    }

    private fun save(context: Context, entries: List<ScoreEntry>) {
        val raw = entries.joinToString(ENTRY_SEPARATOR) { e ->
            listOf(e.score, e.playerWon, e.finalRadius, e.opponentsAbsorbed, e.elapsedSeconds, e.timestampMillis)
                .joinToString(FIELD_SEPARATOR)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENTRIES, raw)
            .apply()
    }
}
