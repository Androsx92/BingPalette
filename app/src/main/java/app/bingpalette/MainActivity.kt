package app.bingpalette

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.palette.graphics.Palette
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var statusTitle: TextView
    private lateinit var statusText: TextView
    private lateinit var enableButton: MaterialButton
    private lateinit var previewImage: ImageView
    private lateinit var previewTitle: TextView
    private lateinit var previewSub: TextView
    private lateinit var timeButton: MaterialButton
    private lateinit var dotMain: View
    private lateinit var dotMainLabel: TextView
    private lateinit var dotRow: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences(PaletteService.PREFS, MODE_PRIVATE)

        // Edge-to-edge: no coloured bars, content pads itself below the status bar.
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(b.left, b.top, b.right, 0)
            insets
        }
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }

        statusTitle = findViewById(R.id.statusTitle)
        statusText = findViewById(R.id.statusText)
        enableButton = findViewById(R.id.enableButton)
        previewImage = findViewById(R.id.previewImage)
        previewTitle = findViewById(R.id.previewTitle)
        previewSub = findViewById(R.id.previewSub)
        timeButton = findViewById(R.id.timeButton)
        dotMain = findViewById(R.id.dotMain)
        dotMainLabel = findViewById(R.id.dotMainLabel)
        dotRow = findViewById(R.id.dotRow)

        enableButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<MaterialButton>(R.id.runButton).setOnClickListener {
            val svc = PaletteService.instance
            if (svc == null) {
                Toast.makeText(this, "Enable the accessibility service first", Toast.LENGTH_LONG).show()
            } else {
                svc.runNow()
            }
        }

        // Bottom navigation: Home / Settings
        val home = findViewById<View>(R.id.pageHome)
        val settings = findViewById<View>(R.id.pageSettings)
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.setOnItemSelectedListener { item ->
            val isHome = item.itemId == R.id.nav_home
            home.visibility = if (isHome) View.VISIBLE else View.GONE
            settings.visibility = if (isHome) View.GONE else View.VISIBLE
            true
        }

        bindSettings()

        val info = packageManager.getPackageInfo(packageName, 0)
        findViewById<TextView>(R.id.versionText).text =
            "Bing Palette v${info.versionName} (build ${info.longVersionCode})"
    }

    override fun onResume() {
        super.onResume()
        if (PaletteService.instance != null) {
            statusTitle.text = "Ready"
            statusText.text = "Accessibility service is on"
            enableButton.text = "Manage"
        } else {
            statusTitle.text = "Setup needed"
            statusText.text = "Turn on Bing Palette in Accessibility"
            enableButton.text = "Enable"
        }
        loadPreview()
    }

    private fun loadPreview() {
        val mkt = prefs.getString("mkt", "de-DE") ?: "de-DE"
        previewSub.text = Markets.label(mkt)
        Thread {
            try {
                val info = Bing.info(mkt)
                val bytes = Bing.image(info, "1366x768")
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                val pal = Palette.from(bmp).maximumColorCount(16).generate()
                val main = pal.getDominantColor(0)
                val others = listOfNotNull(
                    pal.vibrantSwatch?.rgb, pal.mutedSwatch?.rgb, pal.darkVibrantSwatch?.rgb,
                    pal.lightVibrantSwatch?.rgb, pal.darkMutedSwatch?.rgb
                ).take(4)
                runOnUiThread {
                    if (!isDestroyed) {
                        previewImage.setImageBitmap(bmp)
                        previewTitle.text = info.title.ifEmpty { "Today's image" }
                        previewSub.text = Markets.label(mkt) + "\n" + info.copyright
                        showColours(main, others)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { if (!isDestroyed) previewTitle.text = "Preview unavailable (offline?)" }
            }
        }.start()
    }

    private fun oval(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke((1 * resources.displayMetrics.density).toInt(), 0x33808080)
    }

    private fun showColours(main: Int, others: List<Int>) {
        dotMain.background = oval(main)
        dotMainLabel.text = String.format(Locale.US, "Dominant colour\n#%06X", 0xFFFFFF and main)
        dotRow.removeAllViews()
        val d = resources.displayMetrics.density
        for (c in others) {
            val v = View(this)
            val lp = LinearLayout.LayoutParams((24 * d).toInt(), (24 * d).toInt())
            lp.marginStart = (6 * d).toInt()
            v.layoutParams = lp
            v.background = oval(c)
            dotRow.addView(v)
        }
    }

    // ---------------------------------------------------------------- settings tab

    private fun bindSettings() {
        val autoSwitch = findViewById<MaterialSwitch>(R.id.autoSwitch)
        autoSwitch.isChecked = prefs.getBoolean("auto", true)
        autoSwitch.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("auto", on).apply() }

        updateTimeText()
        timeButton.setOnClickListener {
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(prefs.getInt("hour", 5))
                .setMinute(prefs.getInt("minute", 0))
                .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
                .setTitleText("Start after")
                .build()
            picker.addOnPositiveButtonClickListener {
                prefs.edit().putInt("hour", picker.hour).putInt("minute", picker.minute).apply()
                updateTimeText()
            }
            picker.show(supportFragmentManager, "time")
        }

        val drop = findViewById<MaterialAutoCompleteTextView>(R.id.marketDrop)
        drop.setSimpleItems(Markets.codes.map { Markets.label(it) }.toTypedArray())
        drop.setText(Markets.label(prefs.getString("mkt", "de-DE") ?: "de-DE"), false)
        drop.setOnItemClickListener { _, _, position, _ ->
            prefs.edit().putString("mkt", Markets.codes[position]).apply()
            loadPreview()
        }

        bindChips(R.id.paletteWhenGroup, "palette_when", listOf(R.id.chipWhenDaily, R.id.chipWhenManual, R.id.chipWhenBoth), 2)
        bindChips(R.id.speedGroup, "speed", listOf(R.id.chipSpeedSafe, R.id.chipSpeedFast, R.id.chipSpeedUltra), 1)
        bindChips(R.id.targetGroup, "target", listOf(R.id.chipTargetBoth, R.id.chipTargetHome, R.id.chipTargetLock))
        bindChips(R.id.screensGroup, "screens", listOf(R.id.chipScreensBoth, R.id.chipScreensInner, R.id.chipScreensCover))

        val paletteSwitch = findViewById<MaterialSwitch>(R.id.paletteSwitch)
        paletteSwitch.isChecked = prefs.getBoolean("palette", true)
        paletteSwitch.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("palette", on).apply() }

        val coverSwitch = findViewById<MaterialSwitch>(R.id.coverSwitch)
        coverSwitch.isChecked = prefs.getBoolean("cover", true)
        coverSwitch.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("cover", on).apply() }
    }

    private fun updateTimeText() {
        timeButton.text = String.format(Locale.US, "%02d:%02d", prefs.getInt("hour", 5), prefs.getInt("minute", 0))
    }

    private fun bindChips(groupId: Int, key: String, ids: List<Int>, default: Int = 0) {
        val group = findViewById<ChipGroup>(groupId)
        group.check(ids[prefs.getInt(key, default).coerceIn(0, ids.size - 1)])
        group.setOnCheckedStateChangeListener { _, checked ->
            val i = ids.indexOf(checked.firstOrNull())
            if (i >= 0) prefs.edit().putInt(key, i).apply()
        }
    }
}
