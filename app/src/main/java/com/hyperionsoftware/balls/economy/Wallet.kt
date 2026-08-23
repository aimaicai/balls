package com.hyperionsoftware.balls.economy

import android.content.Context

// The game's single soft currency - Helium, since balloons need it to float, which doubles
// as a cute in-universe reason for the name. Earned by playing (see HeliumRewards) and spent
// buying cosmetics outright instead of waiting for their achievement (see PurchasedCosmetics).
object Wallet {
    private const val PREFS_NAME = "wallet"
    private const val KEY_BALANCE = "helium_balance"

    fun getBalance(context: Context): Int =
        prefs(context).getInt(KEY_BALANCE, 0)

    fun add(context: Context, amount: Int) {
        if (amount <= 0) return
        prefs(context).edit().putInt(KEY_BALANCE, getBalance(context) + amount).apply()
    }

    // Returns true only if the balance was actually high enough to spend - the balance never
    // goes negative, callers should check the return value rather than assuming success.
    fun spend(context: Context, amount: Int): Boolean {
        val current = getBalance(context)
        if (amount <= 0 || current < amount) return false
        prefs(context).edit().putInt(KEY_BALANCE, current - amount).apply()
        return true
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
