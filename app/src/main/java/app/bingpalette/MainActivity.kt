package app.bingpalette

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var statusTitle: TextView
    private lateinit var statusText: TextView
    private lateinit var enableButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val prefs = getSharedPreferences(PaletteService.PREFS, MODE_PRIVATE)

        statusTitle = findViewById(R.id.statusTitle)
        statusText = findViewById(R.id.statusText)
        enableButton = findViewById(R.id.enableButton)
        enableButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        val autoSwitch = findViewById<MaterialSwitch>(R.id.autoSwitch)
        autoSwitch.isChecked = prefs.getBoolean("auto", true)
        autoSwitch.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("auto", on).apply() }

        val hourInput = findViewById<TextInputEditText>(R.id.hourInput)
        hourInput.setText(prefs.getInt("hour", 5).toString())
        hourInput.addTextChangedListener(watcher { s ->
            val h = s.toIntOrNull()?.coerceIn(0, 23) ?: 5
            prefs.edit().putInt("hour", h).apply()
        })

        val marketInput = findViewById<TextInputEditText>(R.id.marketInput)
        marketInput.setText(prefs.getString("mkt", "de-DE"))
        marketInput.addTextChangedListener(watcher { s ->
            prefs.edit().putString("mkt", s.trim().ifEmpty { "de-DE" }).apply()
        })

        findViewById<MaterialButton>(R.id.runButton).setOnClickListener {
            val svc = PaletteService.instance
            if (svc == null) {
                Toast.makeText(this, "Enable the accessibility service first", Toast.LENGTH_LONG).show()
            } else {
                svc.runNow()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (PaletteService.instance != null) {
            statusTitle.text = "Ready"
            statusText.text = "The accessibility service is on."
            enableButton.text = "Accessibility settings"
        } else {
            statusTitle.text = "Setup needed"
            statusText.text = "Turn on Bing Palette in Accessibility > Installed apps."
            enableButton.text = "Open accessibility settings"
        }
    }

    private fun watcher(onChange: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
        override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) = onChange(s?.toString() ?: "")
    }
}
