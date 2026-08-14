package com.hyperionsoftware.balls.settings

import android.content.Context

// Whether the player has already seen the tutorial - checked once at launch (see
// SplashActivity) so it only shows automatically the first time; reachable afterwards
// any time from Options regardless of this flag.
object TutorialSettings {
    private const val PREFS_NAME = "settings"
    private const val KEY_HAS_SEEN_TUTORIAL = "has_seen_tutorial"

    fun hasSeenTutorial(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_HAS_SEEN_TUTORIAL, false)

    fun setHasSeenTutorial(context: Context, seen: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HAS_SEEN_TUTORIAL, seen)
            .apply()
    }
}
