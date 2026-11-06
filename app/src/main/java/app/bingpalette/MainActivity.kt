package app.bingpalette

import android.app.Dialog
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.FrameLayout
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.text.SimpleDateFormat
import java.util.Date
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
    private lateinit var marketDrop: MaterialAutoCompleteTextView
    private var currentInfo: Bing.Info? = null
    private var currentBmp: Bitmap? = null

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

        // Tap targets on the preview card
        previewImage.setOnClickListener { showImageDialog() }
        previewSub.setOnClickListener { showMarketPicker() }
        val openPalette = View.OnClickListener {
            val svc = PaletteService.instance
            if (svc == null) {
                Toast.makeText(this, "Enable the accessibility service first", Toast.LENGTH_LONG).show()
            } else {
                svc.openPalettePage()
            }
        }
        dotMain.setOnClickListener(openPalette)
        dotMainLabel.setOnClickListener(openPalette)

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
        val infoPage = findViewById<View>(R.id.pageInfo)
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.setOnItemSelectedListener { item ->
            home.visibility = if (item.itemId == R.id.nav_home) View.VISIBLE else View.GONE
            settings.visibility = if (item.itemId == R.id.nav_settings) View.VISIBLE else View.GONE
            infoPage.visibility = if (item.itemId == R.id.nav_info) View.VISIBLE else View.GONE
            true
        }
        findViewById<MaterialButton>(R.id.githubButton).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Androsx92/BingPalette")))
            } catch (e: Exception) {
                Toast.makeText(this, "No browser found", Toast.LENGTH_SHORT).show()
            }
        }

        bindSettings()

        val info = packageManager.getPackageInfo(packageName, 0)
        val versionLabel = "Bing Palette v${info.versionName} (build ${info.longVersionCode})"
        findViewById<TextView>(R.id.versionText).text = versionLabel
        findViewById<TextView>(R.id.infoVersion).text = versionLabel
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
                val pal = Palette.from(bmp).maximumColorCount(24).generate()
                val main = pickMain(pal)
                val others = listOfNotNull(
                    pal.vibrantSwatch?.rgb, pal.mutedSwatch?.rgb, pal.darkVibrantSwatch?.rgb,
                    pal.lightVibrantSwatch?.rgb, pal.darkMutedSwatch?.rgb
                ).take(4)
                runOnUiThread {
                    if (!isDestroyed) {
                        currentInfo = info
                        currentBmp = bmp
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

    /** Most common colour, but favouring colourful ones over pale or near-black areas. */
    private fun pickMain(pal: Palette): Int {
        var best = pal.getDominantColor(0)
        var bestScore = -1f
        for (sw in pal.swatches) {
            val hsl = sw.hsl
            val sat = hsl[1]
            val light = hsl[2]
            val penalty = if (light > 0.85f || light < 0.12f) 0.3f else 1f
            val score = sw.population * (0.2f + sat) * penalty
            if (score > bestScore) {
                bestScore = score
                best = sw.rgb
            }
        }
        return best
    }

    private fun showImageDialog() {
        val bmp = currentBmp
        if (bmp == null) {
            Toast.makeText(this, "Image not loaded yet", Toast.LENGTH_SHORT).show()
            return
        }
        val d = resources.displayMetrics.density
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val frame = FrameLayout(this)
        frame.setBackgroundColor(Color.BLACK)
        val img = ImageView(this)
        img.scaleType = ImageView.ScaleType.FIT_CENTER
        img.setImageBitmap(bmp)
        img.setOnClickListener { dialog.dismiss() }
        frame.addView(img, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        val download = MaterialButton(this)
        download.text = "Download"
        download.setOnClickListener { downloadImage() }
        val close = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        close.text = "Close"
        close.setOnClickListener { dialog.dismiss() }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.marginEnd = (12 * d).toInt()
        row.addView(download, lp)
        row.addView(close)
        val rlp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        rlp.bottomMargin = (32 * d).toInt()
        frame.addView(row, rlp)

        dialog.setContentView(frame)
        dialog.show()
    }

    private fun downloadImage() {
        val info = currentInfo ?: return
        Toast.makeText(this, "Downloading…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val bytes = try {
                    Bing.image(info, "UHD")
                } catch (e: Exception) {
                    Bing.image(info, "1920x1080")
                }
                val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                val name = "BingPalette_" + day + "_" + info.title.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').take(40) + ".jpg"
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BingPalette")
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw RuntimeException("Could not create file")
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                runOnUiThread { Toast.makeText(this, "Saved to Pictures/BingPalette", Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun showMarketPicker() {
        val labels = Markets.codes.map { Markets.label(it) }.toTypedArray()
        val current = Markets.codes.indexOf(prefs.getString("mkt", "de-DE") ?: "de-DE")
        MaterialAlertDialogBuilder(this)
            .setTitle("Image country / language")
            .setSingleChoiceItems(labels, current) { dlg, which ->
                prefs.edit().putString("mkt", Markets.codes[which]).apply()
                marketDrop.setText(labels[which], false)
                loadPreview()
                dlg.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
        marketDrop = drop
        // Adapter that never filters: otherwise the list shrinks to the currently selected country.
        val labels = Markets.codes.map { Markets.label(it) }
        drop.setAdapter(object : ArrayAdapter<String>(
            this, com.google.android.material.R.layout.m3_auto_complete_simple_item, labels
        ) {
            override fun getFilter(): Filter = object : Filter() {
                override fun performFiltering(constraint: CharSequence?) = FilterResults().apply {
                    values = labels
                    count = labels.size
                }
                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    notifyDataSetChanged()
                }
            }
        })
        drop.setText(Markets.label(prefs.getString("mkt", "de-DE") ?: "de-DE"), false)
        drop.setOnItemClickListener { _, _, position, _ ->
            prefs.edit().putString("mkt", Markets.codes[position]).apply()
            loadPreview()
        }

        bindChips(R.id.paletteWhenGroup, "palette_when", listOf(R.id.chipWhenDaily, R.id.chipWhenManual, R.id.chipWhenBoth), 2)
        bindChips(R.id.speedGroup, "speed", listOf(R.id.chipSpeedSafe, R.id.chipSpeedFast, R.id.chipSpeedUltra), 1)
        bindChips(R.id.langGroup, "lang", listOf(R.id.chipLangAuto, R.id.chipLangEn, R.id.chipLangDe, R.id.chipLangIt), 0)
        bindChips(R.id.targetGroup, "target", listOf(R.id.chipTargetBoth, R.id.chipTargetHome, R.id.chipTargetLock))
        bindChips(R.id.screensGroup, "screens", listOf(R.id.chipScreensBoth, R.id.chipScreensInner, R.id.chipScreensCover))

        val paletteSwitch = findViewById<MaterialSwitch>(R.id.paletteSwitch)
        paletteSwitch.isChecked = prefs.getBoolean("palette", true)
        paletteSwitch.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("palette", on).apply() }

        val coverSwitch = findViewById<MaterialSwitch>(R.id.coverSwitch)
        coverSwitch.isChecked = prefs.getBoolean("cover", true)
        coverSwitch.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("cover", on).apply() }

        val notifySwitch = findViewById<MaterialSwitch>(R.id.notifySwitch)
        notifySwitch.isChecked = prefs.getBoolean("notify", true)
        notifySwitch.setOnCheckedChangeListener { _, on ->
            prefs.edit().putBoolean("notify", on).apply()
            if (on) askNotificationPermission()
        }
        if (notifySwitch.isChecked) askNotificationPermission()
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

    private fun askNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7)
        }
    }
}
