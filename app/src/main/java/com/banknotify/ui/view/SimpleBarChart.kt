package com.banknotify.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

class SimpleBarChart @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Bar(val label: String, val value: Float, val color: Int = Color.parseColor("#4CAF50"))

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        textAlign = Paint.Align.CENTER
        color = Color.GRAY
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        textAlign = Paint.Align.CENTER
        color = Color.DKGRAY
        typeface = Typeface.DEFAULT_BOLD
    }

    var bars: List<Bar> = emptyList()
        set(value) { field = value; invalidate() }

    var barColor: Int = Color.parseColor("#4CAF50")

    override fun onDraw(canvas: Canvas) {
        if (bars.isEmpty()) return
        val w = width.toFloat() - paddingLeft - paddingRight
        val h = height.toFloat() - paddingTop - paddingBottom
        val barCount = bars.size
        val barWidth = w / barCount * 0.6f
        val gap = w / barCount * 0.4f
        val maxVal = bars.maxOf { it.value }.coerceAtLeast(1f)

        bars.forEachIndexed { i, bar ->
            val left = paddingLeft + i * (w / barCount) + gap / 2
            val barH = (bar.value / maxVal) * (h - 60f)
            val top = paddingTop + (h - 60f - barH)
            val right = left + barWidth

            barPaint.color = bar.color
            canvas.drawRoundRect(left, top, right, paddingTop + h - 60f, 8f, 8f, barPaint)

            val cx = left + barWidth / 2
            canvas.drawText(bar.label.substring(5), cx, paddingTop + h - 8f, labelPaint)
            canvas.drawText(formatVal(bar.value), cx, top - 10f, valuePaint)
        }
    }

    private fun formatVal(v: Float): String = when {
        v >= 1_000_000 -> String.format("%.1fM", v / 1_000_000)
        v >= 1_000 -> String.format("%.0fK", v / 1_000)
        else -> String.format("%.0f", v)
    }
}
