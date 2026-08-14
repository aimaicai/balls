package com.hyperionsoftware.balls.challenges

import android.content.Context
import java.time.LocalDate

// One challenge a day, picked deterministically from the date (year * 1000 + day-of-year,
// modulo the challenge count) rather than randomly - stable across app restarts and the same
// for every player, which is as close to a real shared "daily" as an app with no backend can
// get. Completing it is tracked locally, along with a streak of consecutive days completed;
// missing a day resets the streak back to zero the next time one is completed.
object DailyChallenges {
    private const val PREFS_NAME = "daily_challenges"
    private const val KEY_LAST_COMPLETED_DATE = "last_completed_date"
    private const val KEY_STREAK = "streak"

    fun todaysChallenge(): DailyChallenge {
        val today = LocalDate.now()
        val seed = today.year * 1000 + today.dayOfYear
        val challenges = DailyChallenge.entries
        return challenges[seed % challenges.size]
    }

    fun isCompletedToday(context: Context): Boolean =
        lastCompletedDate(context) == LocalDate.now().toString()

    fun streak(context: Context): Int =
        prefs(context).getInt(KEY_STREAK, 0)

    // Returns true only if this call is what actually completed today's challenge - i.e. it
    // wasn't already done - so the caller can tell a fresh "just completed" moment apart from
    // a match that merely re-satisfies an already-completed day.
    fun recordMatchResult(context: Context, result: MatchResult): Boolean {
        if (isCompletedToday(context)) return false
        if (!todaysChallenge().isSatisfiedBy(result)) return false

        val today = LocalDate.now()
        val previousDate = lastCompletedDate(context)
        val newStreak = if (previousDate == today.minusDays(1).toString()) streak(context) + 1 else 1
        prefs(context).edit()
            .putString(KEY_LAST_COMPLETED_DATE, today.toString())
            .putInt(KEY_STREAK, newStreak)
            .apply()
        return true
    }

    private fun lastCompletedDate(context: Context): String? =
        prefs(context).getString(KEY_LAST_COMPLETED_DATE, null)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
