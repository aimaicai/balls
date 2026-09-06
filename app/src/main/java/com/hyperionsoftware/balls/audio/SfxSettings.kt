package com.hyperionsoftware.balls.audio

import android.content.Context

// Whether sound effects are on, and how loud - separate from music (see MusicSettings) so
// either can be turned down or off independently, both persisted the same lightweight way.
object SfxSettings {
    private const val PREFS_NAME = "settings"
    private const val KEY_SFX_ENABLED = "sfx_enabled"
    private const val KEY_SFX_VOLUME = "sfx_volume"
    const val DEFAULT_VOLUME = 100

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SFX_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SFX_ENABLED, enabled)
            .apply()
    }

    // 0-100, matching the SeekBar it's driven by directly.
    fun getVolume(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_SFX_VOLUME, DEFAULT_VOLUME)

    fun setVolume(context: Context, volume: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SFX_VOLUME, volume.coerceIn(0, 100))
            .apply()
    }
}
