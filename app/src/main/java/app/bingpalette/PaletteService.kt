package app.bingpalette

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 1. Downloads today's Bing image and sets it as wallpaper (home / lock / both).
 * 2. Optionally opens Settings > Wallpaper and style > Colour palette and taps "Apply".
 *
 * Runs automatically once per day per screen (inner / cover on a foldable):
 * on unlock, or when the phone is folded / unfolded.
 */
class PaletteService : AccessibilityService() {

    companion object {
        const val PREFS = "cfg"
        const val TAG = "BingPalette"

        @Volatile
        var instance: PaletteService? = null

        private val PALETTE_ROW = listOf("Colour palette", "Color palette")
        private val WALLPAPER_ROW = listOf("Wallpaper and style")
        private val PALETTE_PAGE_MARKER = listOf("Apply palette to app icons")
        private val APPLY = listOf("Apply")
    }

    private val main = Handler(Looper.getMainLooper())
    private var running = false
    private var lastAttemptAt = 0L
    private var runState = "cover"
    private var startedAt = 0L
    private var paletteSeenAt = 0L
    private var applied = false
    private var appliedAt = 0L

    private fun prefs() = getSharedPreferences(PREFS, MODE_PRIVATE)
    private fun today() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Calendar.getInstance().time)

    /** "inner" = unfolded / large display, "cover" = folded or normal phone. */
    private fun screenState(): String {
        val dm = DisplayMetrics()
        val d = (getSystemService(DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
        @Suppress("DEPRECATION")
        d.getRealMetrics(dm)
        val minDp = minOf(dm.widthPixels, dm.heightPixels) / dm.density
        return if (minDp >= 500) "inner" else "cover"
    }

    private fun shouldRun(): Boolean {
        val p = prefs()
        if (!p.getBoolean("auto", true)) return false
        val state = screenState()
        when (p.getInt("screens", 0)) {
            1 -> if (state != "inner") return false
            2 -> if (state != "cover") return false
        }
        val cal = Calendar.getInstance()
        val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        if (minutes < p.getInt("hour", 5) * 60 + p.getInt("minute", 0)) return false
        if (p.getString("last_$state", "") == today()) return false
        if (lastAttemptAt != 0L && SystemClock.elapsedRealtime() - lastAttemptAt < 10 * 60 * 1000) return false
        return true
    }

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (shouldRun()) runNow(manual = false)
        }
    }

    private val foldCheck = Runnable {
        val km = getSystemService(KeyguardManager::class.java)
        val pm = getSystemService(PowerManager::class.java)
        if (!running && pm.isInteractive && !km.isKeyguardLocked && shouldRun()) runNow(manual = false)
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            main.removeCallbacks(foldCheck)
            main.postDelayed(foldCheck, 3000)
        }
    }

    override fun onServiceConnected() {
        instance = this
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(unlockReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(unlockReceiver, filter)
        }
        (getSystemService(DISPLAY_SERVICE) as DisplayManager).registerDisplayListener(displayListener, main)
    }

    override fun onDestroy() {
        instance = null
        hideCover()
        try {
            unregisterReceiver(unlockReceiver)
            (getSystemService(DISPLAY_SERVICE) as DisplayManager).unregisterDisplayListener(displayListener)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    // ---------------------------------------------------------------- step 1: wallpaper

    /**
     * @param manual true when started with the buttons in the app, false for the automatic daily run
     * @param wallpaperOnly true = never touch the colour palette on this run
     */
    fun runNow(manual: Boolean = true, wallpaperOnly: Boolean = false) {
        if (running) return
        running = true
        lastAttemptAt = SystemClock.elapsedRealtime()
        runState = screenState()
        toast("Bing wallpaper: starting (${if (runState == "inner") "inner screen" else "cover screen"})")
        Thread {
            var error: String? = null
            try {
                fetchAndSetWallpaper()
            } catch (e: Exception) {
                Log.e(TAG, "wallpaper failed", e)
                error = e.message ?: e.javaClass.simpleName
            }
            main.post {
                if (error == null) {
                    val p = prefs()
                    p.edit().putString("last_$runState", today()).apply()
                    // palette_when: 0 = daily automatic run only, 1 = "Run now" only, 2 = both
                    val whenOk = when (p.getInt("palette_when", 2)) {
                        0 -> !manual
                        1 -> manual
                        else -> true
                    }
                    // an automatic run touches the palette only once per day (not again for the 2nd screen)
                    val onceOk = manual || p.getString("palette_day", "") != today()
                    if (p.getBoolean("palette", true) && !wallpaperOnly && whenOk && onceOk) {
                        if (!manual) p.edit().putString("palette_day", today()).apply()
                        beginPalette()
                    } else {
                        running = false
                        toast("Wallpaper set")
                    }
                } else {
                    running = false
                    toast("Bing wallpaper failed: $error")
                }
            }
        }.start()
    }

    private fun fetchAndSetWallpaper() {
        val p = prefs()
        val mkt = p.getString("mkt", "de-DE") ?: "de-DE"
        val info = Bing.info(mkt)
        val bytes = try {
            Bing.image(info, "UHD")
        } catch (e: Exception) {
            Bing.image(info, "1920x1080")
        }
        val file = File(cacheDir, "bing.jpg")
        file.writeBytes(bytes)

        val flags = when (p.getInt("target", 0)) {
            1 -> WallpaperManager.FLAG_SYSTEM
            2 -> WallpaperManager.FLAG_LOCK
            else -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        }
        FileInputStream(file).use { input ->
            WallpaperManager.getInstance(this).setStream(input, null, true, flags)
        }
    }

    // ---------------------------------------------------------------- step 2: palette

    private var launches = 0
    private var lastLaunchAt = 0L
    private var scrollTries = 0
    // speed: 0 = Safe, 1 = Fast, 2 = Ultra fast  (all values in milliseconds)
    private fun speed() = prefs().getInt("speed", 1).coerceIn(0, 2)
    private fun tickMs() = longArrayOf(800, 500, 250)[speed()]
    private fun pageWaitMs() = longArrayOf(1500, 1000, 400)[speed()]
    private fun toggleWaitMs() = longArrayOf(3500, 2000, 1000)[speed()]
    private fun applyWaitMs() = longArrayOf(3500, 2500, 1500)[speed()]
    private fun startDelayMs() = longArrayOf(3000, 2500, 1500)[speed()]

    private var round = 0
    private var applyPresses = 0
    private var palettePhase = 0 // 0 arrived, 1 switched off, 2 switched on, 3 Apply pressed
    private var phaseAt = 0L

    private fun launchSettings() {
        launches++
        lastLaunchAt = SystemClock.uptimeMillis()
        startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun beginPalette() {
        startedAt = SystemClock.uptimeMillis()
        paletteSeenAt = 0L
        applied = false
        appliedAt = 0L
        palettePhase = 0
        round = 0
        phaseAt = 0L
        launches = 0
        scrollTries = 0
        toast("Updating colour palette - please don't touch the screen")
        showCover()
        launchSettings()
        main.postDelayed(tick, startDelayMs())
    }

    private val tick = object : Runnable {
        override fun run() {
            try {
                step()
            } catch (e: Exception) {
                Log.e(TAG, "step failed", e)
            }
            if (running) main.postDelayed(this, tickMs())
        }
    }

    private fun step() {
        val now = SystemClock.uptimeMillis()
        if (now - startedAt > 45000) {
            finish("Palette step timed out - the wallpaper itself is set")
            return
        }
        val root = rootInActiveWindow ?: return

        // Only ever touch Settings (or Samsung's palette screens). On any other screen,
        // e.g. the home screen while Settings is still opening, do nothing at all.
        val pkg = root.packageName?.toString() ?: ""
        val inSettings = pkg == "com.android.settings"
        if (!inSettings && !pkg.startsWith("com.samsung")) {
            if (launches < 3 && now - lastLaunchAt > 6000) launchSettings()
            return
        }

        // On the "Colour palette" page. One UI keeps showing the colour swatches of the
        // PREVIOUS wallpaper (with Apply greyed out) until the palette switch is turned
        // off and on again, so we do exactly that, then press Apply if it lights up.
        if (find(root, PALETTE_PAGE_MARKER) != null) {
            if (paletteSeenAt == 0L) paletteSeenAt = now
            if (now - paletteSeenAt < pageWaitMs()) return // let the page finish loading
            // Same sequence as by hand, in two rounds:
            //   round 0: switch OFF -> Apply        (page closes)
            //   round 1: open again, switch ON -> Apply
            // One UI reports the switch/Apply state of the accessibility nodes unreliably,
            // so the switch is tapped exactly ONCE per round (never re-checked) and Apply
            // is pressed after a fixed wait.
            val t = findToggle(root)
            if (t == null) {
                finish("Couldn't find the Colour palette switch")
                return
            }
            val wantOn = round == 1
            when (palettePhase) {
                0 -> {
                    setStatus(if (wantOn) "Switching colour palette on…" else "Switching colour palette off…")
                    val isOn = if (t.isCheckable) t.isChecked else !wantOn
                    if (isOn != wantOn) click(t)
                    palettePhase = 1
                    phaseAt = now
                    applyPresses = 0
                }
                1 -> if (now - phaseAt > toggleWaitMs()) {
                    val apply = find(root, APPLY)
                    if (apply != null) {
                        setStatus("Pressing Apply (${round + 1}/2)…")
                        click(apply)
                        applyPresses++
                        palettePhase = 2
                        appliedAt = now
                    } else if (now - phaseAt > 9000) {
                        finish("Couldn't find the Apply button")
                    }
                }
                2 -> if (now - appliedAt > applyWaitMs()) {
                    // Still on the palette page: Apply did not take, press it again (max 4 times)
                    if (applyPresses < 4) {
                        find(root, APPLY)?.let { click(it) }
                        applyPresses++
                        appliedAt = now
                    } else if (round == 0) {
                        round = 1
                        palettePhase = 0
                    } else {
                        finish("Palette refreshed and applied")
                    }
                }
            }
            return
        }
        // Not on the palette page. After round 0's Apply we land back on "Wallpaper and style".
        paletteSeenAt = 0L
        if (palettePhase == 2 && now - appliedAt > minOf(1500L, applyWaitMs())) {
            if (round == 0) {
                round = 1
                palettePhase = 0
                setStatus("Opening Colour palette again…")
            } else {
                finish("Palette refreshed and applied")
                return
            }
        }

        find(root, PALETTE_ROW)?.let { if (click(it)) return }
        find(root, WALLPAPER_ROW)?.let { if (click(it)) return }
        // Label not visible yet: scroll the Settings list a few times only.
        if (inSettings && scrollTries < 10) {
            scroll(root, scrollTries < 6)
            scrollTries++
        }
    }

    // ---------------------------------------------------------------- cover screen

    private var cover: FrameLayout? = null
    private var coverStatus: TextView? = null

    /** Full-screen cover (today's image + status card) drawn over Settings while we tap it. */
    private fun showCover() {
        if (cover != null || !prefs().getBoolean("cover", true)) return
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val d = resources.displayMetrics.density
            val frame = FrameLayout(this)
            frame.setBackgroundColor(Color.parseColor("#101418"))

            val img = ImageView(this)
            img.scaleType = ImageView.ScaleType.CENTER_CROP
            try {
                val f = File(cacheDir, "bing.jpg")
                val o = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeFile(f.path, o)?.let { img.setImageBitmap(it) }
            } catch (e: Exception) {
                Log.e(TAG, "cover image", e)
            }
            frame.addView(img, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

            val scrim = View(this)
            scrim.setBackgroundColor(Color.parseColor("#66000000"))
            frame.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.gravity = Gravity.CENTER_HORIZONTAL
            val pad = (24 * d).toInt()
            card.setPadding(pad, pad, pad, pad)
            card.background = GradientDrawable().apply {
                setColor(Color.parseColor("#F5000000"))
                cornerRadius = 28 * d
            }

            val title = TextView(this)
            title.text = "Updating colour palette"
            title.setTextColor(Color.WHITE)
            title.textSize = 20f
            card.addView(title)

            val status = TextView(this)
            status.text = "Wallpaper set"
            status.setTextColor(Color.parseColor("#DDFFFFFF"))
            status.textSize = 15f
            status.gravity = Gravity.CENTER
            val sp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            sp.topMargin = (8 * d).toInt()
            card.addView(status, sp)

            val bar = ProgressBar(this)
            val bp = LinearLayout.LayoutParams((40 * d).toInt(), (40 * d).toInt())
            bp.topMargin = (16 * d).toInt()
            card.addView(bar, bp)

            val cancel = Button(this)
            cancel.text = "Cancel"
            cancel.setOnClickListener { finish("Cancelled") }
            val cp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            cp.topMargin = (12 * d).toInt()
            card.addView(cancel, cp)

            val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            lp.leftMargin = (32 * d).toInt()
            lp.rightMargin = (32 * d).toInt()
            frame.addView(card, lp)

            val wlp = WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE
            )
            wlp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            wm.addView(frame, wlp)
            cover = frame
            coverStatus = status
        } catch (e: Exception) {
            Log.e(TAG, "cover failed", e)
            cover = null
        }
    }

    private fun setStatus(msg: String) {
        main.post { coverStatus?.text = msg }
    }

    private fun hideCover() {
        val c = cover ?: return
        cover = null
        coverStatus = null
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(c)
        } catch (e: Exception) {
            Log.e(TAG, "hide cover", e)
        }
    }

    private fun finish(message: String) {
        running = false
        hideCover()
        main.removeCallbacks(tick)
        performGlobalAction(GLOBAL_ACTION_HOME)
        toast(message)
    }

    // ---------------------------------------------------------------- helpers

    private fun find(root: AccessibilityNodeInfo, labels: List<String>): AccessibilityNodeInfo? {
        for (label in labels) {
            val hit = root.findAccessibilityNodeInfosByText(label)
                ?.firstOrNull { it.text?.toString()?.trim().equals(label, ignoreCase = true) }
            if (hit != null) return hit
        }
        return null
    }

    /** The "Colour palette" on/off switch = the top-most checkable node on the page. */
    private fun findToggle(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val checkable = ArrayList<AccessibilityNodeInfo>()
        collectCheckable(root, checkable)
        val rect = android.graphics.Rect()
        val top = checkable.minByOrNull { n -> n.getBoundsInScreen(rect); rect.top }
        if (top != null) return top
        // Fallback: the "Colour palette" label that sits lowest on screen (the row, not the title).
        val rowRect = android.graphics.Rect()
        return root.findAccessibilityNodeInfosByText(PALETTE_ROW[0])
            ?.filter { it.text?.toString()?.trim().equals(PALETTE_ROW[0], ignoreCase = true) }
            ?.maxByOrNull { n -> n.getBoundsInScreen(rowRect); rowRect.top }
    }

    private fun collectCheckable(n: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        if (n.isCheckable && n.isVisibleToUser) out.add(n)
        for (i in 0 until n.childCount) {
            n.getChild(i)?.let { collectCheckable(it, out) }
        }
    }

    private fun click(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            n = n.parent
        }
        return false
    }

    private fun scroll(root: AccessibilityNodeInfo, forward: Boolean) {
        val scrollables = ArrayList<AccessibilityNodeInfo>()
        collectScrollable(root, scrollables)
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        for (s in scrollables) s.performAction(action)
    }

    private fun collectScrollable(n: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        if (n.isScrollable) out.add(n)
        for (i in 0 until n.childCount) {
            n.getChild(i)?.let { collectScrollable(it, out) }
        }
    }

    private fun toast(msg: String) {
        main.post { Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show() }
    }
}
