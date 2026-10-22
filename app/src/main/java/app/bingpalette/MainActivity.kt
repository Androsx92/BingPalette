package app.bingpalette

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var statusTitle: TextView
    private lateinit var statusText: TextView
    private lateinit var enableButton: MaterialButton
    private lateinit var previewImage: ImageView
    private lateinit var previewTitle: TextView
    private lateinit var previewSub: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTitle = findViewById(R.id.statusTitle)
        statusText = findViewById(R.id.statusText)
        enableButton = findViewById(R.id.enableButton)
        previewImage = findViewById(R.id.previewImage)
        previewTitle = findViewById(R.id.previewTitle)
        previewSub = findViewById(R.id.previewSub)

        enableButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<MaterialButton>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
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
        loadPreview()
    }

    private fun loadPreview() {
        val mkt = getSharedPreferences(PaletteService.PREFS, MODE_PRIVATE).getString("mkt", "de-DE") ?: "de-DE"
        previewSub.text = Markets.label(mkt)
        Thread {
            try {
                val info = Bing.info(mkt)
                val bytes = Bing.image(info, "1366x768")
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                runOnUiThread {
                    if (!isDestroyed) {
                        previewImage.setImageBitmap(bmp)
                        previewTitle.text = info.title.ifEmpty { "Today's image" }
                        previewSub.text = Markets.label(mkt) + "\n" + info.copyright
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { if (!isDestroyed) previewTitle.text = "Preview unavailable (offline?)" }
            }
        }.start()
    }
}
