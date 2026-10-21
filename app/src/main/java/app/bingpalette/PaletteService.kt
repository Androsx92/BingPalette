package app.bingpalette

import android.accessibilityservice.AccessibilityService
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 1. Downloads today's Bing image and sets it as home + lock wallpaper.
 * 2. Opens Settings > Wallpaper and style > Colour palette and taps "Apply".
 *
 * Runs automatically on the first unlock of the day (after the configured hour),
 * or on demand via runNow().
 */
class PaletteService : AccessibilityService() {

    companion object {
        const val PREFS = "cfg"
        const val TAG = "BingPalette"

        @Volatile
        var instance: PaletteService? = null

        // UI labels of the Samsung One UI screens (English UI, as in your recordings).
        private val PALETTE_ROW = listOf("Colour palette", "Color palette")
        private val WALLPAPER_ROW = listOf("Wallpaper and style")
        private val PALETTE_PAGE_MARKER = listOf("Apply palette to app icons")
        private val APPLY = listOf("Apply")
    }

    private val main = Handler(Looper.getMainLooper())
    private var running = false
    private var startedAt = 0L
    private var paletteSeenAt = 0L
    private var applied = false
    private var appliedAt = 0L

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val p = getSharedPreferences(PREFS, MODE_PRIVATE)
            if (!p.getBoolean("auto", true)) return
            val cal = Calendar.getInstance()
            if (cal.get(Calendar.HOUR_OF_DAY) < p.getInt("hour", 5)) return
            val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.time)
            if (p.getString("last", "") == today) return
            runNow()
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
    }

    override fun onDestroy() {
        instance = null
        try {
            unregisterReceiver(unlockReceiver)
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
        toast("Bing wallpaper: starting")
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
                    val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Calendar.getInstance().time)
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("last", today).apply()
                    beginPalette()
                } else {
                    running = false
                    toast("Bing wallpaper failed: $error")
                }
            }
        }.start()
    }

    private fun fetchAndSetWallpaper() {
        val mkt = getSharedPreferences(PREFS, MODE_PRIVATE).getString("mkt", "de-DE") ?: "de-DE"
        val meta = httpGet("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=$mkt")
        val base = JSONObject(String(meta)).getJSONArray("images").getJSONObject(0).getString("urlbase")

        val bytes = try {
            httpGet("https://www.bing.com${base}_UHD.jpg")
        } catch (e: Exception) {
            httpGet("https://www.bing.com${base}_1920x1080.jpg")
        }
        val file = File(cacheDir, "bing.jpg")
        file.writeBytes(bytes)

        FileInputStream(file).use { input ->
            WallpaperManager.getInstance(this).setStream(
                input, null, true,
                WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            )
        }
    }

    private fun httpGet(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        try {
            if (c.responseCode != 200) throw RuntimeException("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    // ---------------------------------------------------------------- step 2: palette

    private fun beginPalette() {
        startedAt = SystemClock.uptimeMillis()
        paletteSeenAt = 0L
        applied = false
        appliedAt = 0L
        startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        // give the system a few seconds to process the new wallpaper
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

        // On the "Colour palette" page: tap Apply once it is enabled.
        if (find(root, PALETTE_PAGE_MARKER) != null) {
            if (paletteSeenAt == 0L) paletteSeenAt = now
            val apply = find(root, APPLY)
            if (apply != null && apply.isEnabled) {
                click(apply)
                applied = true
                appliedAt = now
            } else if (now - paletteSeenAt > 5000) {
                // Apply stays disabled = palette already matches the wallpaper.
                finish(true)
            }
            return
        }

        // On "Wallpaper and style": open Colour palette.
        find(root, PALETTE_ROW)?.let { if (click(it)) return }
        // On the main Settings list: open Wallpaper and style.
        find(root, WALLPAPER_ROW)?.let { if (click(it)) return }
        // Not visible yet: scroll the lists.
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
