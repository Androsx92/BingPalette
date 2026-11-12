package app.bingpalette

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView

/** ImageView with pinch-to-zoom, drag while zoomed, and double-tap to zoom in / out. */
class ZoomImageView(context: Context) : ImageView(context) {

    var onTap: (() -> Unit)? = null

    private val m = Matrix()
    private var bmpW = 0f
    private var bmpH = 0f
    private var minScale = 1f
    private val maxScale get() = minScale * 6f

    init {
        scaleType = ScaleType.MATRIX
    }

    private fun scale(): Float {
        val v = FloatArray(9)
        m.getValues(v)
        return v[Matrix.MSCALE_X]
    }

    private fun zoomed() = scale() > minScale * 1.02f

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        bmpW = bm?.width?.toFloat() ?: 0f
        bmpH = bm?.height?.toFloat() ?: 0f
        reset()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        reset()
    }

    /** Fit the whole image in the view, centred. */
    private fun reset() {
        if (width == 0 || height == 0 || bmpW == 0f || bmpH == 0f) return
        minScale = minOf(width / bmpW, height / bmpH)
        m.reset()
        m.postScale(minScale, minScale)
        m.postTranslate((width - bmpW * minScale) / 2f, (height - bmpH * minScale) / 2f)
        imageMatrix = m
    }

    /** Keep the image from being dragged out of view. */
    private fun fix() {
        val r = RectF(0f, 0f, bmpW, bmpH)
        m.mapRect(r)
        var dx = 0f
        var dy = 0f
        if (r.width() <= width) dx = (width - r.width()) / 2f - r.left
        else if (r.left > 0) dx = -r.left
        else if (r.right < width) dx = width - r.right
        if (r.height() <= height) dy = (height - r.height()) / 2f - r.top
        else if (r.top > 0) dy = -r.top
        else if (r.bottom < height) dy = height - r.bottom
        m.postTranslate(dx, dy)
        imageMatrix = m
    }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val cur = scale()
            val target = (cur * d.scaleFactor).coerceIn(minScale, maxScale)
            val f = target / cur
            m.postScale(f, f, d.focusX, d.focusY)
            fix()
            return true
        }
    })

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (zoomed() && !scaleDetector.isInProgress) {
                m.postTranslate(-dx, -dy)
                fix()
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (zoomed()) {
                reset()
            } else {
                val f = (minScale * 3f) / scale()
                m.postScale(f, f, e.x, e.y)
                fix()
            }
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (!zoomed()) onTap?.invoke()
            return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestures.onTouchEvent(event)
        return true
    }
}
