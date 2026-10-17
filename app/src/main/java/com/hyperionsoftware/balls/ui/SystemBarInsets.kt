package com.hyperionsoftware.balls.ui

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

// From targetSdk 35 Android always lays the window out edge-to-edge (behind the status and
// navigation bars), and from 36 the opt-out is gone. Rather than letting each screen deal with
// it, every activity draws edge-to-edge on every API level and pads its root view by the system
// bars and display cutout - the root's dark background still fills the area behind the bars,
// while buttons, the game field and the joystick stay inside the touchable, unobscured region.
fun Activity.fitContentInsideSystemBars(root: View) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val basePadding = intArrayOf(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
    ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
        val insets = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        view.setPadding(
            basePadding[0] + insets.left,
            basePadding[1] + insets.top,
            basePadding[2] + insets.right,
            basePadding[3] + insets.bottom
        )
        WindowInsetsCompat.CONSUMED
    }
}
