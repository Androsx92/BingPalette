package app.bingpalette

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: one tap = full run (wallpaper + palette). */
class RunTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let {
            it.state = if (PaletteService.instance != null) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
            it.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        // Start through the invisible RunNowActivity, so the quick panel closes first.
        val intent = Intent(this, RunNowActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
