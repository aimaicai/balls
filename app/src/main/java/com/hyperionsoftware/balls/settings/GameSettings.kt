package com.hyperionsoftware.balls.settings

import android.content.Context
import com.hyperionsoftware.balls.game.GameConfig

// Match setup (bot count, power-up frequency, arena size), persisted via SharedPreferences
// so it survives a trip to the Options screen and back - previously this only ever lived
// in MainMenuActivity's own fields, which worked fine as long as the SeekBars controlling
// it lived on that same screen.
object GameSettings {
    private const val PREFS_NAME = "settings"
    private const val KEY_BOT_COUNT = "bot_count"
    private const val KEY_POWERUP_FREQUENCY = "powerup_frequency"
    private const val KEY_ARENA_SIZE = "arena_size"
    const val DEFAULT_BOT_COUNT = 100

    fun getBotCount(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_BOT_COUNT, DEFAULT_BOT_COUNT)

    fun setBotCount(context: Context, count: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_BOT_COUNT, count)
            .apply()
    }

    fun getPowerUpFrequency(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_POWERUP_FREQUENCY, GameConfig.POWERUP_DEFAULT_FREQUENCY_LEVEL)

    fun setPowerUpFrequency(context: Context, level: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_POWERUP_FREQUENCY, level)
            .apply()
    }

    fun getArenaSize(context: Context): GameConfig.ArenaSize {
        val storedName = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ARENA_SIZE, null)
        return GameConfig.ArenaSize.entries.find { it.name == storedName } ?: GameConfig.ArenaSize.NORMAL
    }

    fun setArenaSize(context: Context, size: GameConfig.ArenaSize) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ARENA_SIZE, size.name)
            .apply()
    }
}
