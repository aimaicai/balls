package com.hyperionsoftware.balls.settings

import android.content.Context

// Gates the Grand Prix mode, the "test final round" shortcut, the match-settings sub-page and
// the deformation tuning slider behind a hidden developer mode - toggled only via MainMenuActivity's secret tap sequence on
// the title text, never exposed as an ordinary settings switch.
object DeveloperSettings {
    private const val PREFS_NAME = "settings"
    private const val KEY_ENABLED = "developer_mode_enabled"
    private const val KEY_DEFORMATION_PERCENT = "developer_deformation_percent"
    const val DEFAULT_DEFORMATION_PERCENT = 100
    const val MAX_DEFORMATION_PERCENT = 300

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    // How exaggerated the balloons' latex deformation is (see BalloonBody.intensity), as a
    // percentage of the tuned default - only adjustable in developer mode (CustomizeActivity),
    // but read regardless, so a value set there sticks.
    fun getDeformationPercent(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_DEFORMATION_PERCENT, DEFAULT_DEFORMATION_PERCENT)

    fun setDeformationPercent(context: Context, percent: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_DEFORMATION_PERCENT, percent.coerceIn(0, MAX_DEFORMATION_PERCENT))
            .apply()
    }
}
