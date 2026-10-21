package app.bingpalette

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PaletteService.PREFS, MODE_PRIVATE)
        val pad = (16 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            fitsSystemWindows = true
        }

        status = TextView(this).apply { textSize = 16f }
        root.addView(status)

        root.addView(Button(this).apply {
            text = "1. Enable accessibility service"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

        root.addView(Switch(this).apply {
            text = "Run automatically on first unlock of the day"
            isChecked = prefs.getBoolean("auto", true)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("auto", on).apply() }
        })

        root.addView(TextView(this).apply { text = "Not before hour (0-23):" })
        root.addView(EditText(this).apply {
            setText(prefs.getInt("hour", 5).toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setOnFocusChangeListener { _, focused ->
                if (!focused) {
                    val h = text.toString().toIntOrNull()?.coerceIn(0, 23) ?: 5
                    prefs.edit().putInt("hour", h).apply()
                }
            }
        })

        root.addView(TextView(this).apply { text = "Bing market (de-DE, en-US, it-IT ...):" })
        root.addView(EditText(this).apply {
            setText(prefs.getString("mkt", "de-DE"))
            setOnFocusChangeListener { _, focused ->
                if (!focused) prefs.edit().putString("mkt", text.toString().trim().ifEmpty { "de-DE" }).apply()
            }
        })

        root.addView(Button(this).apply {
            text = "2. Run now (test)"
            setOnClickListener {
                // commit text fields before running
                currentFocus?.clearFocus()
                val svc = PaletteService.instance
                if (svc == null) {
                    Toast.makeText(context, "Enable the accessibility service first", Toast.LENGTH_LONG).show()
                } else {
                    svc.runNow()
                }
            }
        })

        root.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        status.text = if (PaletteService.instance != null)
            "Accessibility service: ON"
        else
            "Accessibility service: OFF - enable it below"
    }
}
