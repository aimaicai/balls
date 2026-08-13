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
    private const val KEY_LAST_INITIALS = "last_initials"
    private const val DEFAULT_INITIALS = "AAA"
    const val MAX_ENTRIES = 10
    private const val ENTRY_SEPARATOR = ";"
    private const val FIELD_SEPARATOR = ","

    // Whatever initials were entered last time, pre-filled next time so a repeat player can
    // just confirm instead of retyping the same three letters every match.
    fun getLastInitials(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_INITIALS, DEFAULT_INITIALS) ?: DEFAULT_INITIALS

    private const val POINTS_PER_SECOND = 2
    private const val POINTS_PER_ABSORB = 100
    private const val FINAL_ROUND_BONUS = 500
    private const val WIN_BONUS = 1000

    // Every term here only ever increases over the course of a match - elapsed time,
    // absorbs, and once-triggered flags for reaching the final round or winning - so a
    // score shown live during play can only climb, never dip, unlike the old formula's
    // final-size term (radius drains constantly, so it could go down frame to frame).
    fun computeScore(
        playerWon: Boolean,
        elapsedSeconds: Int,
        opponentsAbsorbed: Int,
        reachedFinalRound: Boolean
    ): Int {
        return elapsedSeconds * POINTS_PER_SECOND +
            opponentsAbsorbed * POINTS_PER_ABSORB +
            (if (reachedFinalRound) FINAL_ROUND_BONUS else 0) +
            (if (playerWon) WIN_BONUS else 0)
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
    // timestampMillis defaults to now, but the caller can supply it explicitly to get back
    // a value it can later match against loadAll()'s results - e.g. to find and highlight
    // the just-recorded entry on the leaderboard screen.
    fun recordMatch(
        context: Context,
        initials: String,
        playerWon: Boolean,
        finalRadius: Int,
        opponentsAbsorbed: Int,
        elapsedSeconds: Int,
        reachedFinalRound: Boolean,
        timestampMillis: Long = System.currentTimeMillis()
    ): List<ScoreEntry> {
        val entry = ScoreEntry(
            initials = initials.take(3).uppercase(),
            score = computeScore(playerWon, elapsedSeconds, opponentsAbsorbed, reachedFinalRound),
            playerWon = playerWon,
            finalRadius = finalRadius,
            opponentsAbsorbed = opponentsAbsorbed,
            elapsedSeconds = elapsedSeconds,
            timestampMillis = timestampMillis
        )
        val updated = (loadAll(context) + entry).sortedByDescending { it.score }.take(MAX_ENTRIES)
        save(context, updated)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_INITIALS, entry.initials)
            .apply()
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
