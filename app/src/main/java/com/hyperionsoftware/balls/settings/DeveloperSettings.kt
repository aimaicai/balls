package com.hyperionsoftware.balls.settings

import android.content.Context

// Gates the Grand Prix mode, the "test final round" shortcut, and the match-settings sub-page
// behind a hidden developer mode - toggled only via MainMenuActivity's secret tap sequence on
// the title text, never exposed as an ordinary settings switch.
object DeveloperSettings {
    private const val PREFS_NAME = "settings"
    private const val KEY_ENABLED = "developer_mode_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}
