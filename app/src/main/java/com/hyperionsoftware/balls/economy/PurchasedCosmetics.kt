package com.hyperionsoftware.balls.economy

import android.content.Context

// Tracks which achievement-locked cosmetics have been bought outright with Helium instead of
// earned through their achievement - persisted as a simple delimited set of keys, same
// lightweight approach as Achievements. A purchased item counts as unlocked forever, exactly
// like an achievement unlock; the two paths are otherwise interchangeable everywhere a
// cosmetic's unlocked state is checked (see CustomizeActivity).
object PurchasedCosmetics {
    private const val PREFS_NAME = "purchased_cosmetics"
    private const val KEY_PURCHASED = "purchased"
    private const val SEPARATOR = ","

    fun isPurchased(context: Context, key: String): Boolean =
        key in loadPurchased(context)

    fun markPurchased(context: Context, key: String) {
        val current = loadPurchased(context)
        if (key in current) return
        save(context, current + key)
    }

    private fun loadPurchased(context: Context): Set<String> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PURCHASED, null) ?: return emptySet()
        return raw.split(SEPARATOR).filter { it.isNotBlank() }.toSet()
    }

    private fun save(context: Context, keys: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PURCHASED, keys.joinToString(SEPARATOR))
            .apply()
    }
}
