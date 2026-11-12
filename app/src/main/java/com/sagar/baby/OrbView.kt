package com.sagar.baby

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min
import kotlin.math.sin

/** Beech me chamakta hua gola - sunte waqt awaaz ke saath dhadakta hai. */
class OrbView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class Mode { IDLE, LISTENING, SPEAKING }

    var mode: Mode = Mode.IDLE
        set(v) { field = v; invalidate() }

    private var level = 0f
    private var smooth = 0f
    private var phase = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 6000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    fun setLevel(rmsDb: Float) {
        level = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); animator.start() }
    override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }

    private fun colors(): IntArray = when (mode) {
        Mode.IDLE -> intArrayOf(Color.parseColor("#D9CCFF"), Color.parseColor("#7C5CFF"), Color.parseColor("#2A1670"))
        Mode.LISTENING -> intArrayOf(Color.parseColor("#E0FCFF"), Color.parseColor("#00D4FF"), Color.parseColor("#0049D6"))
        Mode.SPEAKING -> intArrayOf(Color.parseColor("#FFE0FA"), Color.parseColor("#FF4FD8"), Color.parseColor("#6B21D9"))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val base = min(width, height) / 2f * 0.58f
        val target = when (mode) {
            Mode.LISTENING -> level
            Mode.SPEAKING -> 0.35f + 0.25f * sin(Math.toRadians(phase * 8.0)).toFloat()
            Mode.IDLE -> 0f
        }
        smooth += (target - smooth) * 0.18f
        val breath = 0.03f * sin(Math.toRadians(phase * 3.0)).toFloat()
        val r = base * (1f + breath + smooth * 0.18f)
        val c = colors()

        // Glow
        val glowR = r * (1.55f + smooth * 0.3f)
        paint.shader = RadialGradient(
            cx, cy, glowR,
            intArrayOf(withAlpha(c[1], 120), withAlpha(c[1], 40), Color.TRANSPARENT),
            floatArrayOf(0.45f, 0.7f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, glowR, paint)

        // Ghoomte rings
        ring.strokeWidth = r * 0.04f
        for (i in 0..1) {
            val rr = r * (1.18f + i * 0.14f + smooth * 0.12f)
            canvas.save()
            canvas.rotate(if (i == 0) phase else -phase * 1.4f, cx, cy)
            ring.shader = SweepGradient(cx, cy, intArrayOf(Color.TRANSPARENT, withAlpha(c[1], 200), Color.TRANSPARENT), null)
            canvas.drawCircle(cx, cy, rr, ring)
            canvas.restore()
        }

        // Core
        paint.shader = RadialGradient(
            cx - r * 0.3f, cy - r * 0.35f, r * 1.4f,
            intArrayOf(c[0], c[1], c[2]), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null
    }

    private fun withAlpha(color: Int, a: Int) = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
}
