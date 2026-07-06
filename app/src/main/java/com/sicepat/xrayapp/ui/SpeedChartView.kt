package com.sicepat.xrayapp.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import java.util.LinkedList
import kotlin.math.max

class SpeedChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paintLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4FC3F7.toInt()
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val paintGradient = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dataPoints = LinkedList<Float>()
    private val maxPoints = 50
    private var maxSpeed = 1024f

    fun addSpeed(speed: Float) {
        dataPoints.add(speed)
        if (dataPoints.size > maxPoints) {
            dataPoints.removeFirst()
        }
        val currentMax = dataPoints.maxOrNull() ?: 1024f
        maxSpeed = max(1024f, currentMax)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dataPoints.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        val stepX = w / (maxPoints - 1).toFloat()

        val path = Path()
        val fillPath = Path()

        var currentX = w - ((dataPoints.size - 1) * stepX)
        val firstY = h - (dataPoints.first() / maxSpeed) * h * 0.8f
        
        path.moveTo(currentX, firstY)
        fillPath.moveTo(currentX, h)
        fillPath.lineTo(currentX, firstY)

        for (i in 1 until dataPoints.size) {
            currentX += stepX
            val y = h - (dataPoints[i] / maxSpeed) * h * 0.8f
            // Use cubic bezier for smooth wave
            val prevX = currentX - stepX
            val prevY = h - (dataPoints[i-1] / maxSpeed) * h * 0.8f
            path.cubicTo(prevX + stepX / 2, prevY, prevX + stepX / 2, y, currentX, y)
            fillPath.cubicTo(prevX + stepX / 2, prevY, prevX + stepX / 2, y, currentX, y)
        }

        fillPath.lineTo(currentX, h)
        fillPath.close()

        val gradient = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(0x664FC3F7, 0x004FC3F7),
            null,
            Shader.TileMode.CLAMP
        )
        paintGradient.shader = gradient

        canvas.drawPath(fillPath, paintGradient)
        canvas.drawPath(path, paintLine)
    }
}
