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
import android.util.DisplayMetrics
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
            if (shouldRun()) runNow()
        }
    }

    private val foldCheck = Runnable {
        val km = getSystemService(KeyguardManager::class.java)
        val pm = getSystemService(PowerManager::class.java)
        if (!running && pm.isInteractive && !km.isKeyguardLocked && shouldRun()) runNow()
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

    fun runNow() {
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
                    prefs().edit().putString("last_$runState", today()).apply()
                    if (prefs().getBoolean("palette", true)) {
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

    private fun beginPalette() {
        startedAt = SystemClock.uptimeMillis()
        paletteSeenAt = 0L
        applied = false
        appliedAt = 0L
        startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        main.postDelayed(tick, 4000)
    }

    private val tick = object : Runnable {
        override fun run() {
            try {
                step()
            } catch (e: Exception) {
                Log.e(TAG, "step failed", e)
            }
            if (running) main.postDelayed(this, 800)
        }
    }

    private fun step() {
        val now = SystemClock.uptimeMillis()
        if (now - startedAt > 60000) {
            finish(false)
            return
        }
        val root = rootInActiveWindow ?: return

        if (applied) {
            if (now - appliedAt > 3500) finish(true)
            return
        }

        if (find(root, PALETTE_PAGE_MARKER) != null) {
            if (paletteSeenAt == 0L) paletteSeenAt = now
            val apply = find(root, APPLY)
            if (apply != null && apply.isEnabled) {
                click(apply)
                applied = true
                appliedAt = now
            } else if (now - paletteSeenAt > 5000) {
                finish(true)
            }
            return
        }

        find(root, PALETTE_ROW)?.let { if (click(it)) return }
        find(root, WALLPAPER_ROW)?.let { if (click(it)) return }
        scroll(root)
    }

    private fun finish(ok: Boolean) {
        running = false
        main.removeCallbacks(tick)
        performGlobalAction(GLOBAL_ACTION_HOME)
        toast(if (ok) "Wallpaper + palette done" else "Palette step timed out")
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

    private fun click(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            n = n.parent
        }
        return false
    }

    private fun scroll(root: AccessibilityNodeInfo) {
        val scrollables = ArrayList<AccessibilityNodeInfo>()
        collectScrollable(root, scrollables)
        for (s in scrollables) {
            if (!s.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                s.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            }
        }
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
