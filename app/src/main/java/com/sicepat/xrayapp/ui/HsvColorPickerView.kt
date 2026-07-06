package com.sicepat.xrayapp.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class HsvColorPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnColorChangedListener {
        fun onColorChanged(color: Int, hex: String)
    }

    var listener: OnColorChangedListener? = null

    private var hue = 0f
    private var saturation = 0f
    private var value = 0f

    private val huePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.BLACK
        strokeWidth = 3f
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.BLACK
        strokeWidth = 4f
    }

    private var centerX = 0f
    private var centerY = 0f
    private var outerRadius = 0f
    private var innerRadius = 0f
    private var ringThickness = 0f

    private var svRect = RectF()
    private var svBitmap: Bitmap? = null

    private var isDraggingHue = false
    private var isDraggingSV = false

    init {
        setColor(Color.RED)
    }

    fun setColor(color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
        regenerateSVBitmap()
        invalidate()
    }

    fun getColor(): Int {
        return Color.HSVToColor(floatArrayOf(hue, saturation, value))
    }

    fun getHexColor(): String {
        return String.format("#%06X", 0xFFFFFF and getColor())
    }

    private fun regenerateSVBitmap() {
        val size = 256
        val pixels = IntArray(size * size)
        val hsv = floatArrayOf(hue, 0f, 0f)
        var index = 0
        for (y in 0 until size) {
            val v = 1f - y / (size - 1f)
            hsv[2] = v
            for (x in 0 until size) {
                val s = x / (size - 1f)
                hsv[1] = s
                pixels[index++] = Color.HSVToColor(hsv)
            }
        }
        svBitmap?.recycle()
        svBitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        val maxDiameter = min(w, h)
        outerRadius = maxDiameter / 2f - 24f
        ringThickness = w.coerceAtMost(h) * 0.08f
        innerRadius = outerRadius - ringThickness

        val svSide = innerRadius * 1.15f
        svRect.set(
            centerX - svSide / 2f,
            centerY - svSide / 2f,
            centerX + svSide / 2f,
            centerY + svSide / 2f
        )

        val colors = intArrayOf(
            Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED
        )
        val shader = SweepGradient(centerX, centerY, colors, null)
        huePaint.shader = shader
        huePaint.strokeWidth = ringThickness
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw Hue Ring
        canvas.drawCircle(centerX, centerY, (outerRadius + innerRadius) / 2f, huePaint)

        // 2. Draw SV Square
        svBitmap?.let { bitmap ->
            canvas.drawBitmap(bitmap, null, svRect, null)
        }
        
        // Draw border
        canvas.drawRect(svRect, borderPaint)

        // 3. Draw Hue Handle/Indicator
        val angleRad = Math.toRadians(hue.toDouble())
        val handleRadius = (outerRadius + innerRadius) / 2f
        val hx = centerX + handleRadius * cos(angleRad).toFloat()
        val hy = centerY + handleRadius * sin(angleRad).toFloat()

        canvas.drawCircle(hx, hy, ringThickness / 2f + 4f, handleStrokePaint)
        canvas.drawCircle(hx, hy, ringThickness / 2f, handlePaint)

        // 4. Draw SV Handle/Indicator
        val sx = svRect.left + saturation * svRect.width()
        val sy = svRect.top + (1f - value) * svRect.height()

        canvas.drawCircle(sx, sy, 16f, handleStrokePaint)
        canvas.drawCircle(sx, sy, 12f, handlePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val dx = x - centerX
        val dy = y - centerY
        val distance = sqrt(dx * dx + dy * dy)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (distance >= innerRadius - 20f && distance <= outerRadius + 20f) {
                    isDraggingHue = true
                    updateHueFromTouch(dx, dy)
                } else if (svRect.contains(x, y)) {
                    isDraggingSV = true
                    updateSVFromTouch(x, y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDraggingHue) {
                    updateHueFromTouch(dx, dy)
                } else if (isDraggingSV) {
                    updateSVFromTouch(x, y)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDraggingHue = false
                isDraggingSV = false
            }
        }
        return true
    }

    private fun updateHueFromTouch(dx: Float, dy: Float) {
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (angle < 0) angle += 360f
        hue = angle
        regenerateSVBitmap()
        notifyListener()
        invalidate()
    }

    private fun updateSVFromTouch(tx: Float, ty: Float) {
        val clampedX = tx.coerceIn(svRect.left, svRect.right)
        val clampedY = ty.coerceIn(svRect.top, svRect.bottom)

        saturation = (clampedX - svRect.left) / svRect.width()
        value = 1f - (clampedY - svRect.top) / svRect.height()
        notifyListener()
        invalidate()
    }

    private fun notifyListener() {
        listener?.onColorChanged(getColor(), getHexColor())
    }
}
