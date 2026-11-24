package app.bingpalette

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Long press on the Quick Settings tile: pause / resume the automatic run for today. */
class PauseToggleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PaletteService.PREFS, MODE_PRIVATE)
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val paused = prefs.getString("pause_day", "") == today
        if (paused) prefs.edit().remove("pause_day").apply() else prefs.edit().putString("pause_day", today).apply()
        Toast.makeText(this, getString(if (paused) R.string.pause_today else R.string.tile_paused), Toast.LENGTH_SHORT).show()
        RunTileService.refresh(this)
        finish()
    }
}
