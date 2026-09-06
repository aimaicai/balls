package com.hyperionsoftware.balls.audio

import android.content.Context

// Whether background music is on, and which track from the playlist to play - both
// persisted via SharedPreferences so the choice sticks across launches.
object MusicSettings {
    private const val PREFS_NAME = "settings"
    private const val KEY_MUSIC_ENABLED = "music_enabled"
    private const val KEY_SELECTED_TRACK = "selected_track"
    private const val KEY_MUSIC_VOLUME = "music_volume"

    // 50, not 100: matches the level the music was hardcoded to before this was adjustable,
    // so nobody's existing experience changes just because volume control now exists.
    const val DEFAULT_VOLUME = 50

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_MUSIC_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MUSIC_ENABLED, enabled)
            .apply()
    }

    // 0-100, matching the SeekBar it's driven by directly.
    fun getVolume(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_MUSIC_VOLUME, DEFAULT_VOLUME)

    fun setVolume(context: Context, volume: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_MUSIC_VOLUME, volume.coerceIn(0, 100))
            .apply()
    }

    // Falls back to the first track in the playlist if nothing's stored yet, or if a
    // previously selected track no longer exists (e.g. the playlist was rearranged).
    fun getSelectedTrack(context: Context): MusicTrack {
        val storedName = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_TRACK, null)
        return MusicTrack.entries.find { it.name == storedName } ?: MusicTrack.entries.first()
    }

    fun setSelectedTrack(context: Context, track: MusicTrack) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_TRACK, track.name)
            .apply()
    }
}
