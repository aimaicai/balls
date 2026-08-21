package com.hyperionsoftware.balls.records

import android.content.Context
import com.hyperionsoftware.balls.R

// A little "career" leaderboard against yourself, separate from HighScores (which only ever
// keeps the top-N matches by score - a match that misses the leaderboard entirely can still
// set a personal best here for a single stat). Each entry is its own simple running maximum,
// persisted independently of everything else.
enum class RecordType(val titleResId: Int) {
    POPS(R.string.record_pops_title),
    COMBO(R.string.record_combo_title),
    SIZE(R.string.record_size_title),
    LONGEST_MATCH(R.string.record_longest_match_title),
    DAILY_STREAK(R.string.record_daily_streak_title),
    TOTAL_WINS(R.string.record_total_wins_title)
}

object PersonalRecords {
    private const val PREFS_NAME = "personal_records"

    fun get(context: Context, type: RecordType): Int =
        prefs(context).getInt(type.name, 0)

    // Raises the stored record for `type` to `value` if it's higher than what's already
    // there. Returns true only when this call actually raised it, so a caller can tell a
    // genuine new record apart from a value that merely ties or falls short of it - safe to
    // call unconditionally every time a stat might have improved, same as Achievements.unlock.
    fun tryBeat(context: Context, type: RecordType, value: Int): Boolean {
        if (value <= get(context, type)) return false
        prefs(context).edit().putInt(type.name, value).apply()
        return true
    }

    // TOTAL_WINS isn't a "beat the max" record like the others - it only ever goes up by
    // exactly one per win, so it gets its own increment instead of a fetch-then-tryBeat dance.
    fun incrementTotalWins(context: Context): Int {
        val newTotal = get(context, RecordType.TOTAL_WINS) + 1
        prefs(context).edit().putInt(RecordType.TOTAL_WINS.name, newTotal).apply()
        return newTotal
    }

    // Shared by the Records screen and the in-match "new record" popup, so both always
    // render a given record's raw stored int the exact same way.
    fun formatValue(context: Context, type: RecordType, value: Int): String = when (type) {
        RecordType.COMBO -> if (value == 0) "-" else context.getString(R.string.record_value_combo_format, value)
        RecordType.LONGEST_MATCH -> "%02d:%02d".format(value / 60, value % 60)
        RecordType.DAILY_STREAK -> context.getString(R.string.record_value_days_format, value)
        RecordType.POPS, RecordType.SIZE, RecordType.TOTAL_WINS -> value.toString()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
