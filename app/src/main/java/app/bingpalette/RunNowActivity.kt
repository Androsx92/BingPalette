package app.bingpalette

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/** Invisible activity behind the "Run now" app shortcut (long-press on the app icon). */
class RunNowActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val svc = PaletteService.instance
        if (svc == null) {
            Toast.makeText(this, "Enable the accessibility service first", Toast.LENGTH_LONG).show()
        } else {
            svc.runNow()
        }
        finish()
    }
}
