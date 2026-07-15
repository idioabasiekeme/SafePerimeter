package com.example.safeperimeter

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin

/**
 * Radar-style view for the parent screen. Detected child devices are
 * drawn as green blips: the further from the centre, the further away
 * (log scale, 0.5 m .. 40 m). A rotating sweep animates continuously.
 */
class RadarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Blip(val label: String, val distanceMeters: Double)

    private var blips: List<Blip> = emptyList()
    private var sweepAngle = 0f

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0D1F12")
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#2E7D32")
    }
    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A5D6A7")
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    init {
        ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                sweepAngle = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /** Update the blips shown on the radar. */
    fun update(newBlips: List<Blip>) {
        blips = newBlips
        invalidate()
    }

    private fun distanceToRadius(d: Double, maxR: Float): Float {
        val clamped = d.coerceIn(0.5, 40.0)
        val t = (ln(clamped / 0.5) / ln(40.0 / 0.5)).toFloat()
        return t * maxR
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val maxR = min(cx, cy) - 8f

        canvas.drawCircle(cx, cy, maxR, bgPaint)
        for (f in floatArrayOf(0.25f, 0.5f, 0.75f, 1f)) {
            canvas.drawCircle(cx, cy, maxR * f, ringPaint)
        }
        canvas.drawLine(cx - maxR, cy, cx + maxR, cy, ringPaint)
        canvas.drawLine(cx, cy - maxR, cx, cy + maxR, ringPaint)

        // rotating sweep
        sweepPaint.shader = SweepGradient(
            cx, cy,
            intArrayOf(Color.TRANSPARENT, Color.parseColor("#5500E676")),
            floatArrayOf(0f, 1f)
        )
        canvas.save()
        canvas.rotate(sweepAngle, cx, cy)
        canvas.drawCircle(cx, cy, maxR, sweepPaint)
        canvas.restore()

        // blips: angle is a stable hash of the label so each device keeps its bearing
        for (b in blips) {
            val angle = Math.toRadians((abs(b.label.hashCode()) % 360).toDouble())
            val r = distanceToRadius(b.distanceMeters, maxR)
            val x = cx + (r * cos(angle)).toFloat()
            val y = cy + (r * sin(angle)).toFloat()
            canvas.drawCircle(x, y, 10f, blipPaint)
            canvas.drawText(
                String.format("%s (%.1f m)", b.label, b.distanceMeters),
                x, y - 18f, textPaint
            )
        }
    }
}
