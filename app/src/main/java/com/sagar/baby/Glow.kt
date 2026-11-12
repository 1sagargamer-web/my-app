package com.sagar.baby

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.SweepGradient
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Front camera ke charon taraf chamakti ring (punch-hole ke paas), jo batati hai ki Baby sun rahi hai.
 * Accessibility ON ho to status bar ke bhi upar dikhti hai; warna "Display over other apps" se.
 */
object Glow {

    private var view: GlowView? = null
    private var wm: WindowManager? = null
    private var type = 0
    private var mode = BabyBus.Mode.OFF

    private fun enabled(ctx: Context) =
        ctx.getSharedPreferences("baby", Context.MODE_PRIVATE).getBoolean("glow", true)

    /** Sahi jagah (accessibility / overlay) par dikhao ya chhupao. */
    fun refresh(ctx: Context) {
        val want = mode != BabyBus.Mode.OFF && enabled(ctx)
        val acc = AutoSendService.instance
        val desired = when {
            !want -> 0
            acc != null -> WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            Settings.canDrawOverlays(ctx) -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else -> 0
        }
        if (desired == type && view != null) return
        hide()
        if (desired == 0) return
        val host: Context = if (desired == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY) acc!! else ctx.applicationContext
        show(host, desired)
    }

    private fun show(ctx: Context, t: Int) {
        try {
            val w = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val v = GlowView(ctx)
            val density = ctx.resources.displayMetrics.density
            val cut = cutoutRect(w)
            val height = ((cut?.bottom ?: 0f) + 28 * density).toInt().coerceAtLeast((56 * density).toInt())
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, height, t,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                x = 0
                y = 0
                if (Build.VERSION.SDK_INT >= 30) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else if (Build.VERSION.SDK_INT >= 28) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            v.cutout = cut
            v.mode = mode
            w.addView(v, lp)
            view = v
            wm = w
            type = t
        } catch (_: Exception) {
            view = null
            type = 0
        }
    }

    fun hide() {
        try { view?.let { wm?.removeView(it) } } catch (_: Exception) { }
        view = null
        type = 0
    }

    fun setMode(m: BabyBus.Mode) {
        val wasOff = mode == BabyBus.Mode.OFF
        mode = m
        view?.mode = m
        if (m == BabyBus.Mode.OFF) hide()
        else if (wasOff || view == null) BabyApp.instance.let { refresh(it) }
    }

    fun setLevel(db: Float) { view?.setLevel(db) }

    /** Display ke upar wala camera cutout (punch-hole / notch), screen coordinates me. */
    @Suppress("DEPRECATION")
    private fun cutoutRect(w: WindowManager): RectF? {
        if (Build.VERSION.SDK_INT < 29) return null
        val c = w.defaultDisplay.cutout ?: return null
        val r = c.boundingRects.firstOrNull { it.top <= 4 && it.width() > 0 } ?: return null
        return RectF(r)
    }
}

private class GlowView(context: Context) : View(context) {

    var cutout: RectF? = null
    var mode: BabyBus.Mode = BabyBus.Mode.IDLE
        set(v) { field = v; postInvalidateOnAnimation() }

    private var level = 0f
    private var smooth = 0f
    private var phase = 0f
    private val density = resources.displayMetrics.density
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val loc = IntArray(2)

    private val anim = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 2400
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // blur ke liye
    }

    fun setLevel(db: Float) { level = ((db + 2f) / 12f).coerceIn(0f, 1f) }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    private fun palette(): IntArray = when (mode) {
        BabyBus.Mode.LISTENING -> intArrayOf(0xFF00E5FF.toInt(), 0xFF7C4DFF.toInt(), 0xFFFF4FD8.toInt(), 0xFF00FFA3.toInt(), 0xFF00E5FF.toInt())
        BabyBus.Mode.SPEAKING -> intArrayOf(0xFFFF4FD8.toInt(), 0xFFFFB86C.toInt(), 0xFF7C4DFF.toInt(), 0xFFFF4FD8.toInt())
        else -> intArrayOf(0xFF7C4DFF.toInt(), 0xFF3D5AFE.toInt(), 0xFF7C4DFF.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        getLocationOnScreen(loc)
        val target = when (mode) {
            BabyBus.Mode.LISTENING -> 0.35f + level * 0.65f
            BabyBus.Mode.SPEAKING -> 0.55f + 0.35f * sin(Math.toRadians(phase * 4.0)).toFloat()
            BabyBus.Mode.IDLE -> 0.3f
            else -> 0.12f
        }
        smooth += (target - smooth) * 0.2f
        // Dheere-dheere saans jaisi chamak (blink)
        val breath = 0.65f + 0.35f * sin(Math.toRadians(phase.toDouble())).toFloat()
        val intensity = (smooth * breath).coerceIn(0.08f, 1f)

        val c = cutout
        val rect: RectF
        val radius: Float
        if (c != null && c.bottom - loc[1] <= height) {
            val pad = 3.5f * density
            rect = RectF(c.left - loc[0] - pad, c.top - loc[1] - pad, c.right - loc[0] + pad, c.bottom - loc[1] + pad)
            if (rect.width() < rect.height() * 1.6f) {
                // Punch-hole: gol ring
                val s = max(rect.width(), rect.height())
                val cx = rect.centerX(); val cy = rect.centerY()
                rect.set(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2)
            }
            radius = min(rect.width(), rect.height()) / 2
        } else {
            // Cutout nahi: upar beech me chhota pill (dynamic island jaisa)
            val w = 78 * density; val h = 22 * density
            val cx = width / 2f; val top = 6 * density
            rect = RectF(cx - w / 2, top, cx + w / 2, top + h)
            radius = h / 2
        }

        val colors = palette()
        val shader = SweepGradient(rect.centerX(), rect.centerY(), colors, null)
        val m = android.graphics.Matrix().apply { setRotate(phase, rect.centerX(), rect.centerY()) }
        shader.setLocalMatrix(m)

        // Bahar ki narm chamak
        glow.shader = shader
        glow.strokeWidth = (6f + 10f * intensity) * density
        glow.alpha = (90 + 140 * intensity).toInt().coerceAtMost(255)
        glow.maskFilter = BlurMaskFilter((5f + 9f * intensity) * density, BlurMaskFilter.Blur.NORMAL)
        canvas.drawRoundRect(rect, radius, radius, glow)

        // Andar ki tez line
        core.shader = shader
        core.strokeWidth = (1.6f + 1.4f * intensity) * density
        core.alpha = (150 + 105 * intensity).toInt().coerceAtMost(255)
        canvas.drawRoundRect(rect, radius, radius, core)

        // Ek chhota chamakta moti ring par ghoomta hua
        if (mode == BabyBus.Mode.LISTENING || mode == BabyBus.Mode.SPEAKING) {
            val a = Math.toRadians(phase.toDouble() * 2)
            val px = rect.centerX() + (rect.width() / 2) * kotlin.math.cos(a).toFloat()
            val py = rect.centerY() + (rect.height() / 2) * kotlin.math.sin(a).toFloat()
            core.shader = null
            core.style = Paint.Style.FILL
            core.color = Color.WHITE
            core.alpha = (200 * intensity).toInt()
            canvas.drawCircle(px, py, 2.2f * density, core)
            core.style = Paint.Style.STROKE
        }
    }
}
