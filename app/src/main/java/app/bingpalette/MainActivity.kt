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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import org.json.JSONObject
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

    // Backup: export / import all settings as a small JSON file
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val json = JSONObject()
            for ((k, v) in prefs.all) {
                if (k == "log" || k == "palette_day" || k == "pause_day" || k.startsWith("last_")) continue
                val o = JSONObject()
                when (v) {
                    is Boolean -> o.put("t", "b").put("v", v)
                    is Int -> o.put("t", "i").put("v", v)
                    is Long -> o.put("t", "l").put("v", v)
                    is Float -> o.put("t", "f").put("v", v.toDouble())
                    is String -> o.put("t", "s").put("v", v)
                    else -> continue
                }
                json.put(k, o)
            }
            contentResolver.openOutputStream(uri)!!.use { it.write(json.toString(2).toByteArray()) }
            Toast.makeText(this, getString(R.string.backup_saved), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "Error", Toast.LENGTH_LONG).show()
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val text = contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) }
            val json = JSONObject(text)
            val e = prefs.edit()
            for (k in json.keys()) {
                val o = json.getJSONObject(k)
                when (o.getString("t")) {
                    "b" -> e.putBoolean(k, o.getBoolean("v"))
                    "i" -> e.putInt(k, o.getInt("v"))
                    "l" -> e.putLong(k, o.getLong("v"))
                    "f" -> e.putFloat(k, o.getDouble("v").toFloat())
                    "s" -> e.putString(k, o.getString("v"))
                }
            }
            e.apply()
            Toast.makeText(this, getString(R.string.backup_loaded), Toast.LENGTH_SHORT).show()
            recreate()
        } catch (ex: Exception) {
            Toast.makeText(this, getString(R.string.backup_failed), Toast.LENGTH_LONG).show()
        }
    }

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
                Toast.makeText(this, getString(R.string.need_service), Toast.LENGTH_LONG).show()
            } else {
                svc.openPalettePage()
            }
        }
        dotMain.setOnClickListener(openPalette)
        dotMainLabel.setOnClickListener(openPalette)

        findViewById<MaterialButton>(R.id.exportButton).setOnClickListener {
            exportLauncher.launch("BingPalette_settings.json")
        }
        findViewById<MaterialButton>(R.id.importButton).setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }
        findViewById<MaterialButton>(R.id.diagButton).setOnClickListener {
            val svc = PaletteService.instance
            if (svc == null) {
                Toast.makeText(this, getString(R.string.need_service), Toast.LENGTH_LONG).show()
            } else {
                svc.diagnose()
            }
        }
        findViewById<MaterialButton>(R.id.pauseButton).setOnClickListener {
            val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            if (prefs.getString("pause_day", "") == today) prefs.edit().remove("pause_day").apply()
            else prefs.edit().putString("pause_day", today).apply()
            refreshPause()
            RunTileService.refresh(this)
        }
        findViewById<MaterialButton>(R.id.clearLogButton).setOnClickListener {
            prefs.edit().remove("log").apply()
            refreshLog()
        }
        findViewById<MaterialButton>(R.id.setupButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        enableButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<MaterialButton>(R.id.runButton).setOnClickListener {
            val svc = PaletteService.instance
            if (svc == null) {
                Toast.makeText(this, getString(R.string.need_service), Toast.LENGTH_LONG).show()
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
                Toast.makeText(this, getString(R.string.no_browser), Toast.LENGTH_SHORT).show()
            }
        }

        bindSettings()

        val info = packageManager.getPackageInfo(packageName, 0)
        val buildDate = try {
            val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(BuildConfig.BUILD_DATE)!!
            java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(d)
        } catch (e: Exception) {
            BuildConfig.BUILD_DATE
        }
        val versionLabel = "Bing Palette v${info.versionName} · $buildDate"
        findViewById<TextView>(R.id.versionText).text = versionLabel
        findViewById<TextView>(R.id.infoVersion).text = versionLabel
    }

    private fun refreshPause() {
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val paused = prefs.getString("pause_day", "") == today
        findViewById<MaterialButton>(R.id.pauseButton).text =
            getString(if (paused) R.string.paused_today else R.string.pause_today)
    }

    private fun shareImage(bmp: Bitmap, info: Bing.Info?) {
        try {
            val dir = java.io.File(cacheDir, "shared")
            dir.mkdirs()
            val f = java.io.File(dir, "bing_image.jpg")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            val uri = FileProvider.getUriForFile(this, "$packageName.files", f)
            val send = Intent(Intent.ACTION_SEND)
                .setType("image/jpeg")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val caption = listOfNotNull(info?.title, info?.copyright).filter { it.isNotBlank() }.joinToString("\n")
            if (caption.isNotEmpty()) send.putExtra(Intent.EXTRA_TEXT, caption)
            startActivity(Intent.createChooser(send, getString(R.string.share_title)))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.share_failed), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPause()
        if (PaletteService.instance != null) {
            statusTitle.text = getString(R.string.ready)
            statusText.text = getString(R.string.status_on)
            enableButton.text = getString(R.string.manage)
        } else {
            statusTitle.text = getString(R.string.setup_needed)
            statusText.text = getString(R.string.setup_hint)
            enableButton.text = getString(R.string.enable)
        }
        findViewById<View>(R.id.setupCard).visibility =
            if (PaletteService.instance == null) View.VISIBLE else View.GONE
        refreshLog()
        loadPreview()
    }

    private fun refreshLog() {
        val lines = (prefs.getString("log", "") ?: "").split("\n").filter { it.isNotBlank() }
        findViewById<TextView>(R.id.logText).text = if (lines.isEmpty()) getString(R.string.no_runs) else lines.joinToString("\n") { l ->
            val f = l.split("|", limit = 4)
            if (f.size < 4) l else "${if (f[2] == "1") "✓" else "✗"} ${f[0]} · ${f[1]} · ${f[3]}"
        }
    }

    private fun loadPreview() {
        val mkt = prefs.getString("mkt", "de-DE") ?: "de-DE"
        previewSub.text = Markets.label(mkt)
        loadHistory(mkt)
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
                        previewTitle.text = info.title.ifEmpty { getString(R.string.today_image) }
                        previewSub.text = Markets.label(mkt) + "\n" + info.copyright
                        showColours(main, others)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { if (!isDestroyed) previewTitle.text = getString(R.string.preview_unavailable) }
            }
        }.start()
    }


    /** "Last 7 days": thumbnails of the past week's Bing images; tap one to view / download it. */
    private fun loadHistory(mkt: String) {
        val row = findViewById<LinearLayout>(R.id.historyRow)
        Thread {
            try {
                val items = Bing.list(mkt, 7)
                val thumbs = items.map { info ->
                    try {
                        val b = Bing.image(info, "320x240")
                        BitmapFactory.decodeByteArray(b, 0, b.size)
                    } catch (e: Exception) {
                        null
                    }
                }
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    row.removeAllViews()
                    val d = resources.displayMetrics.density
                    items.forEachIndexed { i, info ->
                        val bmp = thumbs[i] ?: return@forEachIndexed
                        val col = LinearLayout(this)
                        col.orientation = LinearLayout.VERTICAL
                        val iv = ImageView(this)
                        iv.scaleType = ImageView.ScaleType.CENTER_CROP
                        iv.setImageBitmap(bmp)
                        iv.clipToOutline = true
                        iv.background = GradientDrawable().apply { cornerRadius = 16 * d; setColor(Color.DKGRAY) }
                        iv.setOnClickListener { openHistoryImage(info) }
                        col.addView(iv, LinearLayout.LayoutParams((150 * d).toInt(), (100 * d).toInt()))
                        val label = TextView(this)
                        label.text = historyLabel(info.date, i)
                        label.textSize = 12f
                        label.setTextColor(previewSub.currentTextColor)
                        label.setPadding(0, (4 * d).toInt(), 0, 0)
                        col.addView(label)
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        lp.marginEnd = (12 * d).toInt()
                        row.addView(col, lp)
                    }
                }
            } catch (e: Exception) {
                // offline: leave the row as it is
            }
        }.start()
    }

    private fun historyLabel(date: String, index: Int): String {
        if (index == 0) return getString(R.string.today)
        return try {
            val d = SimpleDateFormat("yyyyMMdd", Locale.US).parse(date)!!
            SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(d)
        } catch (e: Exception) {
            date
        }
    }

    private fun openHistoryImage(info: Bing.Info) {
        Toast.makeText(this, getString(R.string.loading_toast), Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val b = Bing.image(info, "1366x768")
                val bmp = BitmapFactory.decodeByteArray(b, 0, b.size)
                runOnUiThread { if (!isDestroyed) showImageDialog(bmp, info) }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, getString(R.string.couldnt_load), Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    /** Most common colour, but favouring colourful ones over pale or near-black areas. */
    private fun pickMain(pal: Palette): Int = ColorUtil.pickMain(pal)

    private fun showImageDialog(bmp: Bitmap? = currentBmp, info: Bing.Info? = currentInfo) {
        if (bmp == null) {
            Toast.makeText(this, getString(R.string.image_not_loaded), Toast.LENGTH_SHORT).show()
            return
        }
        val d = resources.displayMetrics.density
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val frame = FrameLayout(this)
        frame.setBackgroundColor(Color.BLACK)
        val img = ZoomImageView(this)
        img.setImageBitmap(bmp)
        img.onTap = { dialog.dismiss() }
        frame.addView(img, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Caption: title + description of the image (hidden while zoomed in)
        val captionText = listOfNotNull(info?.title, info?.copyright).filter { it.isNotBlank() }.joinToString("\n")
        if (captionText.isNotEmpty()) {
            val caption = TextView(this)
            caption.text = captionText
            caption.setTextColor(Color.WHITE)
            caption.textSize = 14f
            val cp = (12 * d).toInt()
            caption.setPadding(cp, cp, cp, cp)
            caption.background = GradientDrawable().apply {
                setColor(Color.parseColor("#99000000"))
                cornerRadius = 16 * d
            }
            val clp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
            clp.leftMargin = (16 * d).toInt()
            clp.rightMargin = (16 * d).toInt()
            clp.bottomMargin = (124 * d).toInt()
            frame.addView(caption, clp)
            img.onZoomChanged = { zoomed -> caption.visibility = if (zoomed) View.GONE else View.VISIBLE }
        }

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        val download = MaterialButton(this)
        download.text = getString(R.string.btn_download)
        download.setOnClickListener { downloadImage(info) }
        val close = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        close.text = getString(R.string.btn_close)
        close.setOnClickListener { dialog.dismiss() }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.marginEnd = (12 * d).toInt()
        val share = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        share.text = getString(R.string.btn_share)
        share.setOnClickListener { shareImage(bmp, info) }
        val lp2 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp2.marginEnd = (12 * d).toInt()
        row.addView(download, lp)
        row.addView(share, lp2)
        row.addView(close)
        val rlp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        rlp.bottomMargin = (48 * d).toInt()
        frame.addView(row, rlp)

        dialog.setContentView(frame)
        // Really full screen: draw behind the status / navigation bars and the camera cut-out
        dialog.window?.let { w ->
            WindowCompat.setDecorFitsSystemWindows(w, false)
            w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.BLACK))
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            val lp = w.attributes
            lp.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            w.attributes = lp
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            w.statusBarColor = Color.BLACK
            w.navigationBarColor = Color.BLACK
        }
        dialog.show()
        dialog.window?.let { w ->
            val c = WindowCompat.getInsetsController(w, frame)
            c.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun downloadImage(info: Bing.Info? = currentInfo) {
        if (info == null) return
        Toast.makeText(this, getString(R.string.downloading), Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val bytes = try {
                    Bing.image(info, "UHD")
                } catch (e: Exception) {
                    Bing.image(info, "1920x1080")
                }
                val day = info.date.ifEmpty { SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) }
                val name = "BingPalette_" + day + "_" + info.title.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').take(40) + ".jpg"
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BingPalette")
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw RuntimeException("Could not create file")
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                runOnUiThread { Toast.makeText(this, getString(R.string.saved_to), Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, getString(R.string.download_failed, e.message ?: ""), Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun showMarketPicker() {
        val labels = Markets.codes.map { Markets.label(it) }.toTypedArray()
        val current = Markets.codes.indexOf(prefs.getString("mkt", "de-DE") ?: "de-DE")
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.market_title))
            .setSingleChoiceItems(labels, current) { dlg, which ->
                prefs.edit().putString("mkt", Markets.codes[which]).apply()
                marketDrop.setText(labels[which], false)
                loadPreview()
                dlg.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun oval(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke((1 * resources.displayMetrics.density).toInt(), 0x33808080)
    }

    private fun showColours(main: Int, others: List<Int>) {
        dotMain.background = oval(main)
        dotMainLabel.text = getString(R.string.dominant, 0xFFFFFF and main)
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
        val wifiSwitch = findViewById<MaterialSwitch>(R.id.wifiSwitch)
        wifiSwitch.isChecked = prefs.getBoolean("wifi_only", false)
        wifiSwitch.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("wifi_only", on).apply() }

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
                .setTitleText(getString(R.string.start_after))
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
        bindChips(R.id.speedGroup, "speed", listOf(R.id.chipSpeedSafe, R.id.chipSpeedFast, R.id.chipSpeedUltra, R.id.chipSpeedFaster, R.id.chipSpeedMax), 1)
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

        // swatch_mode: 0 = Samsung's choice, 1 = auto-match, 2..5 = always swatch 1..4
        bindChips(
            R.id.swatchGroup, "swatch_mode",
            listOf(R.id.chipSwSamsung, R.id.chipSwAuto, R.id.chipSw1, R.id.chipSw2, R.id.chipSw3, R.id.chipSw4),
            if (prefs.getBoolean("automatch", true)) 1 else 0
        )

        // App icon: Light / Dark = enable one of the two launcher aliases
        val iconGroup = findViewById<ChipGroup>(R.id.iconGroup)
        iconGroup.check(if (prefs.getBoolean("icon_dark", false)) R.id.chipIconDark else R.id.chipIconLight)
        iconGroup.setOnCheckedStateChangeListener { _, ids ->
            val dark = ids.firstOrNull() == R.id.chipIconDark
            if (dark == prefs.getBoolean("icon_dark", false)) return@setOnCheckedStateChangeListener
            prefs.edit().putBoolean("icon_dark", dark).apply()
            setLauncherIcon(dark)
        }
    }

    private fun setLauncherIcon(dark: Boolean) {
        val pm = packageManager
        val light = android.content.ComponentName(packageName, "app.bingpalette.LauncherLight")
        val darkC = android.content.ComponentName(packageName, "app.bingpalette.LauncherDark")
        val on = if (dark) darkC else light
        val off = if (dark) light else darkC
        pm.setComponentEnabledSetting(on, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP)
        pm.setComponentEnabledSetting(off, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP)
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
