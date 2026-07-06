package com.sicepat.xrayapp.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class TrafficUsageRingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paintUp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4FC3F7.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 20f
        strokeCap = Paint.Cap.ROUND
    }

    private val paintDown = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1976D2.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 20f
        strokeCap = Paint.Cap.ROUND
    }
    
    private val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2A2A2A.toInt() // dark bg
        style = Paint.Style.STROKE
        strokeWidth = 20f
    }

    private var upUsage: Long = 0
    private var downUsage: Long = 0

    fun updateUsage(up: Long, down: Long) {
        upUsage = up
        downUsage = down
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val w = width.toFloat()
        val h = height.toFloat()
        val padding = 20f
        
        val rect = RectF(padding, padding, w - padding, h - padding)
        
        canvas.drawArc(rect, 0f, 360f, false, paintBg)
        
        val total = upUsage + downUsage
        if (total > 0) {
            val upAngle = (upUsage.toFloat() / total) * 360f
            val downAngle = (downUsage.toFloat() / total) * 360f
            
            canvas.drawArc(rect, -90f, upAngle, false, paintUp)
            canvas.drawArc(rect, -90f + upAngle, downAngle, false, paintDown)
        }
    }
}
