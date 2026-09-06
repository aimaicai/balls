package com.hyperionsoftware.balls.settings

import android.content.Context
import com.hyperionsoftware.balls.race.RaceConfig
import com.hyperionsoftware.balls.race.RaceTrack

// Grand Prix match setup (track, laps, opponent count), persisted the same way as classic
// mode's GameSettings so it survives leaving RaceSetupActivity and coming back.
object RaceSettings {
    private const val PREFS_NAME = "race_settings"
    private const val KEY_TRACK = "track"
    private const val KEY_LAPS = "laps"
    private const val KEY_BOT_COUNT = "bot_count"

    fun getTrack(context: Context): RaceTrack {
        val storedName = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TRACK, null)
        return RaceTrack.values().find { it.name == storedName } ?: RaceTrack.OVAL
    }

    fun setTrack(context: Context, track: RaceTrack) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TRACK, track.name)
            .apply()
    }

    fun getLaps(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_LAPS, RaceConfig.DEFAULT_LAPS)

    fun setLaps(context: Context, laps: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LAPS, laps)
            .apply()
    }

    fun getBotCount(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_BOT_COUNT, RaceConfig.DEFAULT_BOT_COUNT)

    fun setBotCount(context: Context, count: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_BOT_COUNT, count)
            .apply()
    }
}
