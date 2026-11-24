package app.bingpalette

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: one tap = full run (wallpaper + palette). */
class RunTileService : TileService() {

    companion object {
        /** Ask the system to redraw the tile (e.g. after Pause for today changed). */
        fun refresh(ctx: Context) {
            try {
                TileService.requestListeningState(ctx, ComponentName(ctx, RunTileService::class.java))
            } catch (_: Exception) {
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        val paused = getSharedPreferences(PaletteService.PREFS, MODE_PRIVATE).getString("pause_day", "") == today
        qsTile?.let {
            it.state = when {
                PaletteService.instance == null -> Tile.STATE_UNAVAILABLE
                paused -> Tile.STATE_INACTIVE
                else -> Tile.STATE_ACTIVE
            }
            it.subtitle = if (paused) getString(R.string.tile_paused) else null
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
