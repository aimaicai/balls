package com.hyperionsoftware.balls.ui.gdx

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.badlogic.gdx.backends.android.AndroidFragmentApplication

// The libGDX surface that GameView and RaceView each host inside themselves. A fragment rather
// than a plain view because that's how libGDX embeds into an existing activity without taking
// the whole activity over - so the joystick, buttons and dialogs all stay ordinary Android views
// layered on top, exactly as before. The hosting activity has to implement
// AndroidFragmentApplication.Callbacks (libGDX checks this on attach).
class GdxHostFragment : AndroidFragmentApplication() {
    var pendingListener: ApplicationListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Only null for a copy the FragmentManager restored on its own after process death -
        // attachGdxHost removes that copy before it ever gets this far, this is just a guard.
        val listener = pendingListener ?: return View(requireContext())
        val config = AndroidApplicationConfiguration().apply {
            // 8 bits per color channel (the default 565 would band the soft gradients), and no
            // destination alpha - the surface is opaque.
            r = 8
            g = 8
            b = 8
            a = 0
            // Pure 2D sprites: no depth or stencil buffer needed, and antialiasing comes from the
            // textures' own soft edges rather than (much more expensive) multisampling.
            depth = 0
            stencil = 0
            numSamples = 0
            useAccelerometer = false
            useCompass = false
            useGyroscope = false
            useRotationVectorSensor = false
            // All sound goes through the app's own Android players (SoundEffectPlayer etc.).
            disableAudio = true
            useImmersiveMode = false
            useWakelock = false
        }
        return initializeForView(listener, config)
    }

    // libGDX only releases its static registries of managed textures/meshes/shaders (keyed by
    // this fragment) from onPause, and only when the activity is finishing. An activity
    // destroyed without finishing - recreated for a dark-mode or font-size change, or reclaimed
    // in the background - would otherwise leave this fragment, its activity and the whole match
    // referenced from those registries for the rest of the process. Clearing twice is harmless.
    override fun onDestroy() {
        graphics?.clearManagedCaches()
        super.onDestroy()
    }
}

// Adds a GdxHostFragment rendering `listener` inside `container` (which must have an id), on
// the hosting activity's own FragmentManager. Safe to call from Activity.onCreate. Any copy of
// the fragment the FragmentManager restored by itself (after process death, with no listener)
// is dropped first, so there's only ever the one live surface.
fun attachGdxHost(container: ViewGroup, listener: ApplicationListener): GdxHostFragment {
    require(container.id != View.NO_ID) { "The libGDX container needs an id" }
    val fragmentManager = container.context.findFragmentActivity().supportFragmentManager
    val tag = "gdx-host-${container.id}"
    val transaction = fragmentManager.beginTransaction()
    fragmentManager.findFragmentByTag(tag)?.let { transaction.remove(it) }
    val fragment = GdxHostFragment().apply { pendingListener = listener }
    transaction.add(container.id, fragment, tag)
    transaction.commitNow()
    return fragment
}

private fun Context.findFragmentActivity(): FragmentActivity {
    var current: Context = this
    while (current !is FragmentActivity) {
        current = (current as? ContextWrapper)?.baseContext
            ?: error("GameView/RaceView must live inside a FragmentActivity")
    }
    return current
}
