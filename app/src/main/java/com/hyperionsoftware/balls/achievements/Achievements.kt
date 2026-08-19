package com.hyperionsoftware.balls.achievements

import android.content.Context
import com.hyperionsoftware.balls.R

// Each one nudges toward a specific mechanic the player might not have tried otherwise -
// the carried-item types especially, since those are easy to go a whole match without ever
// touching if you never happen to pick one up.
enum class Achievement(val titleResId: Int, val descriptionResId: Int) {
    FIRST_ABSORB(R.string.achievement_first_absorb_title, R.string.achievement_first_absorb_desc),
    ABSORB_STREAK(R.string.achievement_absorb_streak_title, R.string.achievement_absorb_streak_desc),
    FIRST_WIN(R.string.achievement_first_win_title, R.string.achievement_first_win_desc),
    FINAL_ROUND(R.string.achievement_final_round_title, R.string.achievement_final_round_desc),
    USE_SPEED(R.string.achievement_use_speed_title, R.string.achievement_use_speed_desc),
    USE_INVISIBILITY(R.string.achievement_use_invisibility_title, R.string.achievement_use_invisibility_desc),
    USE_REPEL(R.string.achievement_use_repel_title, R.string.achievement_use_repel_desc),
    USE_FREEZE(R.string.achievement_use_freeze_title, R.string.achievement_use_freeze_desc),
    USE_HOOK(R.string.achievement_use_hook_title, R.string.achievement_use_hook_desc),
    SUPPLY_DROP(R.string.achievement_supply_drop_title, R.string.achievement_supply_drop_desc),
    MAX_SPEED_STAT(R.string.achievement_max_speed_stat_title, R.string.achievement_max_speed_stat_desc),
    MAX_AGILITY_STAT(R.string.achievement_max_agility_stat_title, R.string.achievement_max_agility_stat_desc),
    MAX_POTENCY_STAT(R.string.achievement_max_potency_stat_title, R.string.achievement_max_potency_stat_desc),
    MAX_BOOST(R.string.achievement_max_boost_title, R.string.achievement_max_boost_desc),
    MAX_SIZE(R.string.achievement_max_size_title, R.string.achievement_max_size_desc),
    COMBO_MASTER(R.string.achievement_combo_master_title, R.string.achievement_combo_master_desc),
    DAILY_DEDICATION(R.string.achievement_daily_dedication_title, R.string.achievement_daily_dedication_desc)
}

// Local unlock state, persisted via SharedPreferences as a simple delimited set of unlocked
// achievement names - same lightweight approach as HighScores, no backend involved.
object Achievements {
    private const val PREFS_NAME = "achievements"
    private const val KEY_UNLOCKED = "unlocked"
    private const val SEPARATOR = ","

    fun isUnlocked(context: Context, achievement: Achievement): Boolean =
        achievement.name in loadUnlockedNames(context)

    // Returns true only the first time this achievement is unlocked, so the caller can show
    // an "unlocked" popup once and stay silent on every later, already-unlocked occurrence.
    fun unlock(context: Context, achievement: Achievement): Boolean {
        val current = loadUnlockedNames(context)
        if (achievement.name in current) return false
        save(context, current + achievement.name)
        return true
    }

    fun unlockedCount(context: Context): Int = loadUnlockedNames(context).size

    private fun loadUnlockedNames(context: Context): Set<String> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_UNLOCKED, null) ?: return emptySet()
        return raw.split(SEPARATOR).filter { it.isNotBlank() }.toSet()
    }

    private fun save(context: Context, names: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_UNLOCKED, names.joinToString(SEPARATOR))
            .apply()
    }
}
