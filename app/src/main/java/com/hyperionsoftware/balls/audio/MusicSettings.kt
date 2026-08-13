package com.hyperionsoftware.balls.audio

import android.content.Context

// Whether background music is on, and which of the synthesized tracks to play - both
// persisted via SharedPreferences so the choice sticks across launches without needing a
// dedicated settings screen.
object MusicSettings {
    private const val PREFS_NAME = "settings"
    private const val KEY_MUSIC_ENABLED = "music_enabled"
    private const val KEY_SELECTED_TRACK = "selected_track"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_MUSIC_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MUSIC_ENABLED, enabled)
            .apply()
    }

    // Defaults to ARCADE - the track that always played before this was selectable, so
    // nobody's experience changes on update until they actually pick something else.
    fun getSelectedTrack(context: Context): MusicTrack {
        val storedName = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_TRACK, null)
        return MusicTrack.entries.find { it.name == storedName } ?: MusicTrack.ARCADE
    }

    fun setSelectedTrack(context: Context, track: MusicTrack) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_TRACK, track.name)
            .apply()
    }
}
