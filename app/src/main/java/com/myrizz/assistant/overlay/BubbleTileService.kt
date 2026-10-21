package com.myrizz.assistant.overlay

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.myrizz.assistant.MainActivity
import com.myrizz.assistant.container

/** Quick Settings tile: turn the floating bubble on or off without opening the app. */
class BubbleTileService : TileService() {

    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        val app = applicationContext
        val on = app.container.settings.current.bubbleEnabled && BubbleControl.canDraw(app)
        when {
            on -> BubbleControl.disable(app)
            BubbleControl.canDraw(app) -> if (!BubbleControl.enable(app)) openApp()
            else -> openApp() // permission missing: the app explains and asks for it
        }
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val on = applicationContext.container.settings.current.bubbleEnabled && BubbleControl.canDraw(applicationContext)
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    @Suppress("DEPRECATION")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pi)
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
