package app.bingpalette

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.palette.graphics.Palette
import java.io.File
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

object ColorUtil {

    /** Most common colour, but favouring saturated ones over grey / near-white / near-black. */
    fun pickMain(pal: Palette): Int {
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

    /** Main colour of an image file (the wallpaper we just set), or null. */
    fun mainColourOf(file: File): Int? {
        if (!file.exists()) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
        val bmp = BitmapFactory.decodeFile(file.path, opts) ?: return null
        return pickMain(Palette.from(bmp).generate())
    }

    fun hex(c: Int) = String.format("#%06X", 0xFFFFFF and c)

    /** Average colour of a small square around (x, y). */
    fun sample(b: Bitmap, x: Int, y: Int, r: Int = 3): Int {
        var rs = 0; var gs = 0; var bs = 0; var n = 0
        for (yy in y - r..y + r) for (xx in x - r..x + r) {
            if (xx < 0 || yy < 0 || xx >= b.width || yy >= b.height) continue
            val c = b.getPixel(xx, yy)
            rs += Color.red(c); gs += Color.green(c); bs += Color.blue(c); n++
        }
        return if (n == 0) 0 else Color.rgb(rs / n, gs / n, bs / n)
    }

    /** The colour stripes of one One UI palette swatch, top to bottom. */
    fun stripes(b: Bitmap, l: Int, t: Int, r: Int, bottom: Int): IntArray {
        val h = bottom - t
        val y0 = t + h * 0.17f
        val y1 = bottom - h * 0.06f
        val x = (l + r) / 2
        return IntArray(6) { i -> sample(b, x, (y0 + (y1 - y0) * (i + 0.5f) / 6f).toInt()) }
    }

    /** Lower = closer. The top stripe is the swatch's main colour, so it counts most. */
    fun score(stripes: IntArray, target: Int): Double {
        val d0 = deltaE(stripes[0], target)
        val d12 = minOf(deltaE(stripes[1], target), deltaE(stripes[2], target))
        return 0.6 * d0 + 0.4 * d12
    }

    fun deltaE(a: Int, b: Int): Double {
        val x = lab(a); val y = lab(b)
        return sqrt((x[0] - y[0]).pow(2) + (x[1] - y[1]).pow(2) + (x[2] - y[2]).pow(2))
    }

    private fun lab(c: Int): DoubleArray {
        fun lin(v: Int): Double { val s = v / 255.0; return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
        val r = lin(Color.red(c)); val g = lin(Color.green(c)); val bl = lin(Color.blue(c))
        val x = (r * 0.4124 + g * 0.3576 + bl * 0.1805) / 0.95047
        val y = (r * 0.2126 + g * 0.7152 + bl * 0.0722)
        val z = (r * 0.0193 + g * 0.1192 + bl * 0.9505) / 1.08883
        fun f(t: Double) = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116.0
        val fx = f(x); val fy = f(y); val fz = f(z)
        return doubleArrayOf(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }
}
