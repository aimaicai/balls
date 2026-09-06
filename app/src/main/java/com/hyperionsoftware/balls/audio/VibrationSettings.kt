package com.hyperionsoftware.balls.audio

import android.content.Context

// Whether haptic feedback (bounces, absorbs, power-ups, the final round alert) is on -
// no volume/intensity knob, just on or off, same as the other two toggles.
object VibrationSettings {
    private const val PREFS_NAME = "settings"
    private const val KEY_VIBRATION_ENABLED = "vibration_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_VIBRATION_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_VIBRATION_ENABLED, enabled)
            .apply()
    }
}
