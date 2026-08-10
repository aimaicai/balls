package com.hyperionsoftware.balls.audio

import android.content.Context

// Whether background music is on, persisted via SharedPreferences so the choice sticks
// across launches without needing a dedicated settings screen.
object MusicSettings {
    private const val PREFS_NAME = "settings"
    private const val KEY_MUSIC_ENABLED = "music_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_MUSIC_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MUSIC_ENABLED, enabled)
            .apply()
    }
}
