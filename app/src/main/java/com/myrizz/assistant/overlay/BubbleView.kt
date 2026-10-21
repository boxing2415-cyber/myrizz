package com.myrizz.assistant.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * The floating bubble: an original violet gradient orb with a sparkle. Draws itself (no bitmaps),
 * reports drags and taps to the service, and never reads anything from the screen.
 */
@SuppressLint("ViewConstructor")
class BubbleView(
    context: Context,
    private val onDrag: (dx: Float, dy: Float, first: Boolean) -> Unit,
    private val onDragEnd: () -> Unit,
    private val onTap: () -> Unit,
) : View(context) {

    private val density = resources.displayMetrics.density
    val sizePx = (BUBBLE_DP * density).toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = Color.argb(90, 255, 255, 255)
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 0, 0, 0)
        setShadowLayer(8f * density, 0f, 3f * density, Color.argb(120, 40, 10, 100))
    }
    private val star = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val path = Path()

    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private val slop = (8 * density)

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // required for setShadowLayer
        isClickable = true
        isFocusable = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(sizePx, sizePx)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        fill.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(Color.parseColor("#A855F7"), Color.parseColor("#7C3AED"), Color.parseColor("#4C1D95")),
            null,
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        val c = width / 2f
        val r = width / 2f - 8f * density
        canvas.drawCircle(c, c, r, shadow)
        canvas.drawCircle(c, c, r, fill)
        canvas.drawCircle(c, c, r - ring.strokeWidth / 2, ring)
        drawSparkle(canvas, c - 1f * density, c + 1f * density, r * 0.52f)
        drawSparkle(canvas, c + r * 0.42f, c - r * 0.44f, r * 0.2f)
    }

    private fun drawSparkle(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        path.reset()
        path.moveTo(cx, cy - radius)
        path.quadTo(cx, cy, cx + radius, cy)
        path.quadTo(cx, cy, cx, cy + radius)
        path.quadTo(cx, cy, cx - radius, cy)
        path.quadTo(cx, cy, cx, cy - radius)
        path.close()
        canvas.drawPath(path, star)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                dragging = false
                animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).start()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!dragging && hypot(dx, dy) > slop) {
                    dragging = true
                    onDrag(0f, 0f, true)
                }
                if (dragging) onDrag(dx, dy, false)
                return true
            }
            MotionEvent.ACTION_UP -> {
                animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                if (dragging) {
                    onDragEnd()
                } else {
                    performClick()
                    onTap()
                }
                dragging = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                if (dragging) onDragEnd()
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    companion object {
        const val BUBBLE_DP = 68 // 52dp orb + room for the shadow
    }
}
