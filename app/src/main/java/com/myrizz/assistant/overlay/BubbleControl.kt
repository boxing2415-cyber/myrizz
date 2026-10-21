package com.myrizz.assistant.overlay

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.myrizz.assistant.container

/** Single place that turns the floating bubble on or off. Nothing starts without a user action. */
object BubbleControl {

    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Turns the bubble on. Returns false when the overlay permission is missing or the system refuses. */
    fun enable(context: Context): Boolean {
        val app = context.applicationContext
        if (!canDraw(app)) return false
        app.container.settings.update { it.copy(bubbleEnabled = true) }
        return start(app, OverlayService.ACTION_SHOW_BUBBLE)
    }

    fun disable(context: Context) {
        val app = context.applicationContext
        app.container.settings.update { it.copy(bubbleEnabled = false) }
        app.stopService(Intent(app, OverlayService::class.java))
    }

    /** Opens the assistant panel (used by the share sheet). The bubble itself stays off unless enabled. */
    fun openPanel(context: Context): Boolean {
        val app = context.applicationContext
        if (!canDraw(app)) return false
        return start(app, OverlayService.ACTION_OPEN_PANEL)
    }

    private fun start(app: Context, action: String): Boolean = try {
        ContextCompat.startForegroundService(app, Intent(app, OverlayService::class.java).setAction(action))
        true
    } catch (_: Exception) {
        // e.g. ForegroundServiceStartNotAllowedException when started from the background
        false
    }
}
