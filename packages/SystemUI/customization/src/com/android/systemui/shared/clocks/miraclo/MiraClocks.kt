/*
 * SPDX-FileCopyrightText: MiraCLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.shared.clocks.miraclo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.icu.util.TimeZone
import android.provider.Settings
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import com.android.systemui.customization.clocks.view.DefaultClockFaceLayout
import com.android.systemui.plugins.keyguard.VRect
import com.android.systemui.plugins.keyguard.data.model.AlarmData
import com.android.systemui.plugins.keyguard.data.model.WeatherData
import com.android.systemui.plugins.keyguard.data.model.ZenData
import com.android.systemui.plugins.keyguard.ui.clocks.ClockAnimations
import com.android.systemui.plugins.keyguard.ui.clocks.ClockAxisStyle
import com.android.systemui.plugins.keyguard.ui.clocks.ClockConfig
import com.android.systemui.plugins.keyguard.ui.clocks.ClockController
import com.android.systemui.plugins.keyguard.ui.clocks.ClockEventListeners
import com.android.systemui.plugins.keyguard.ui.clocks.ClockEvents
import com.android.systemui.plugins.keyguard.ui.clocks.ClockFaceConfig
import com.android.systemui.plugins.keyguard.ui.clocks.ClockFaceController
import com.android.systemui.plugins.keyguard.ui.clocks.ClockFaceEvents
import com.android.systemui.plugins.keyguard.ui.clocks.ClockMetadata
import com.android.systemui.plugins.keyguard.ui.clocks.ClockPickerConfig
import com.android.systemui.plugins.keyguard.ui.clocks.ClockPositionAnimationArgs
import com.android.systemui.plugins.keyguard.ui.clocks.ClockSettings
import com.android.systemui.plugins.keyguard.ui.clocks.ClockViewIds
import com.android.systemui.plugins.keyguard.ui.clocks.ThemeConfig
import com.android.systemui.plugins.keyguard.ui.clocks.TimeFormatKind
import java.io.PrintWriter
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * MiraCLO lock screen clocks: 18 styles drawn with the system fonts. They are served by
 * [com.android.systemui.shared.clocks.DefaultClockProvider], so the wallpaper & style picker and
 * Mira Studio both list them. Weight and size come from Mira Studio (Settings.Secure
 * "miraclo_lock_clock_weight" 0 = the style's own, 100..900; "miraclo_lock_clock_scale" percent).
 */
object MiraClocks {
    enum class Layout { INLINE, STACK, SPLIT, QUAD, OUTLINE, ANALOG }

    class Style(
        val key: String,
        val name: String,
        val family: String,
        val weight: Int,
        val layout: Layout,
        /** Large clock text size in dp at 100%. */
        val size: Float,
        /** Letter spacing in em. */
        val tracking: Float,
        val alignStart: Boolean,
    ) {
        val id = "MIRA_" + key.uppercase(Locale.ROOT)
    }

    val STYLES =
        listOf(
            Style("stack", "Stack", "sans-serif", 300, Layout.STACK, 132f, -0.03f, true),
            Style("bold", "Bold", "sans-serif", 650, Layout.INLINE, 96f, -0.03f, false),
            Style("duo", "Duo", "sans-serif", 700, Layout.STACK, 118f, -0.02f, false),
            Style("slim", "Slim", "sans-serif", 200, Layout.INLINE, 90f, -0.01f, true),
            Style("classic", "Classic", "sans-serif", 500, Layout.INLINE, 82f, 0f, false),
            Style("tall", "Tall", "sans-serif-condensed", 500, Layout.STACK, 150f, 0.01f, false),
            Style("serif", "Serif", "serif", 400, Layout.INLINE, 92f, -0.02f, false),
            Style("lens", "Lens", "sans-serif", 200, Layout.INLINE, 102f, -0.035f, true),
            Style("mono", "Mono", "monospace", 700, Layout.INLINE, 76f, -0.02f, false),
            Style("round", "Round", "sans-serif", 600, Layout.STACK, 124f, -0.02f, false),
            Style("dot", "Dot", "monospace", 400, Layout.INLINE, 84f, 0.02f, false),
            Style("outline", "Outline", "sans-serif", 800, Layout.OUTLINE, 84f, -0.02f, false),
            Style("split", "Split", "sans-serif", 700, Layout.SPLIT, 132f, -0.04f, true),
            Style("quad", "Quad", "sans-serif", 800, Layout.QUAD, 108f, -0.03f, false),
            Style("elegant", "Elegant", "serif", 500, Layout.INLINE, 96f, -0.01f, false),
            Style("wide", "Wide", "sans-serif", 300, Layout.INLINE, 62f, 0.06f, false),
            Style("neo", "Neo", "sans-serif", 300, Layout.SPLIT, 124f, -0.03f, true),
            Style("analog", "Analog", "sans-serif", 400, Layout.ANALOG, 0f, 0f, false),
        )

    private val byId = STYLES.associateBy { it.id }

    fun find(id: String?): Style? = id?.let { byId[it] }

    fun metadata(): List<ClockMetadata> = STYLES.map { ClockMetadata(it.id) }

    fun createClock(ctx: Context, settings: ClockSettings): ClockController? =
        find(settings.clockId)?.let { MiraClockController(ctx, settings, it) }

    fun pickerConfig(ctx: Context, settings: ClockSettings): ClockPickerConfig? =
        find(settings.clockId)?.let {
            ClockPickerConfig(
                it.id,
                it.name,
                "${it.name} clock",
                MiraClockThumbnail(ctx, it),
                isReactiveToTone = true,
            )
        }

    internal const val KEY_WEIGHT = "miraclo_lock_clock_weight"
    internal const val KEY_SCALE = "miraclo_lock_clock_scale"

    internal fun typeface(ctx: Context, style: Style): Typeface {
        val override =
            runCatching { Settings.Secure.getInt(ctx.contentResolver, KEY_WEIGHT, 0) }.getOrDefault(0)
        val weight = if (override in 100..900) override else style.weight
        return Typeface.create(Typeface.create(style.family, Typeface.NORMAL), weight, false)
    }

    internal fun scale(ctx: Context): Float =
        runCatching { Settings.Secure.getInt(ctx.contentResolver, KEY_SCALE, 100) }
            .getOrDefault(100)
            .coerceIn(60, 140) / 100f

    /** Hours and minutes for now, as shown (12 or 24 hour). */
    internal fun timeParts(ctx: Context, zone: TimeZone?): Pair<String, String> {
        val cal = Calendar.getInstance()
        zone?.let { cal.timeZone = java.util.TimeZone.getTimeZone(it.id) }
        val h24 = DateFormat.is24HourFormat(ctx)
        val h = cal.get(Calendar.HOUR_OF_DAY).let { if (h24) it else (it % 12).let { v -> if (v == 0) 12 else v } }
        val m = cal.get(Calendar.MINUTE)
        val hh = if (h24) "%02d".format(h) else "$h"
        return hh to "%02d".format(m)
    }
}

/** The large clock face: one view that draws the style. */
internal class MiraClockView(ctx: Context, private val style: MiraClocks.Style) : View(ctx) {
    var zone: TimeZone? = null
    var color: Int = Color.WHITE
        set(v) {
            field = v
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private var hh = "12"
    private var mm = "00"
    private var minuteAngle = 0f
    private var hourAngle = 0f
    private var scale = 1f

    init {
        id = ClockViewIds.LOCKSCREEN_CLOCK_VIEW_LARGE
        refresh()
    }

    // Weight and size changes from Mira Studio show at once.
    private val observer =
        object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        runCatching {
            for (key in listOf(MiraClocks.KEY_WEIGHT, MiraClocks.KEY_SCALE)) {
                context.contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(key), false, observer)
            }
        }
        refresh()
    }

    override fun onDetachedFromWindow() {
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
        super.onDetachedFromWindow()
    }

    fun refresh() {
        val (h, m) = MiraClocks.timeParts(context, zone)
        hh = h
        mm = m
        val cal = Calendar.getInstance()
        zone?.let { cal.timeZone = java.util.TimeZone.getTimeZone(it.id) }
        minuteAngle = cal.get(Calendar.MINUTE) * 6f
        hourAngle = (cal.get(Calendar.HOUR) * 30f) + cal.get(Calendar.MINUTE) * 0.5f
        val newScale = MiraClocks.scale(context)
        paint.typeface = MiraClocks.typeface(context, style)
        paint.textSize = style.size * density * newScale
        paint.letterSpacing = style.tracking
        contentDescription = "$hh:$mm"
        scale = newScale
        requestLayout()
        invalidate()
    }

    private fun lineHeight(): Float = paint.fontMetrics.let { it.descent - it.ascent } * 0.86f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w: Float
        val h: Float
        when (style.layout) {
            MiraClocks.Layout.ANALOG -> {
                w = 220 * density * scale
                h = w
            }
            MiraClocks.Layout.STACK, MiraClocks.Layout.QUAD -> {
                w = maxOf(paint.measureText(hh), paint.measureText(mm), paint.measureText("00"))
                h = lineHeight() * 2
            }
            MiraClocks.Layout.SPLIT -> {
                w = paint.measureText(hh) * 0.5f + paint.measureText(mm) * 1.05f
                h = lineHeight() * 2
            }
            else -> {
                w = paint.measureText("$hh:$mm")
                h = lineHeight()
            }
        }
        setMeasuredDimension(
            resolveSize(w.toInt() + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(h.toInt() + paddingTop + paddingBottom, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        paint.color = color
        paint.style = Paint.Style.FILL
        val fm = paint.fontMetrics
        val lh = lineHeight()
        val baseline1 = paddingTop - fm.ascent - (fm.descent - fm.ascent - lh) / 2f
        val left = paddingLeft.toFloat()
        val width = (width - paddingLeft - paddingRight).toFloat()
        fun line(text: String, baseline: Float, alpha: Int = 255) {
            paint.alpha = alpha
            val x = if (style.alignStart) left else left + (width - paint.measureText(text)) / 2f
            canvas.drawText(text, x, baseline, paint)
            paint.alpha = 255
        }
        when (style.layout) {
            MiraClocks.Layout.INLINE -> {
                if (style.key == "dot") drawDots(canvas, "$hh:$mm", baseline1) else line("$hh:$mm", baseline1)
            }
            MiraClocks.Layout.OUTLINE -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f * density
                line("$hh:$mm", baseline1)
                paint.style = Paint.Style.FILL
            }
            MiraClocks.Layout.STACK -> {
                line(hh, baseline1)
                line(mm, baseline1 + lh, if (style.key == "duo") 170 else 255)
            }
            MiraClocks.Layout.QUAD -> {
                // Four digits on a 2 x 2 grid, each in its own cell.
                val h2 = hh.padStart(2, '0')
                val cell = width / 2f
                listOf(h2[0], h2[1], mm[0], mm[1]).forEachIndexed { i, d ->
                    val s = d.toString()
                    val x = left + cell * (i % 2) + (cell - paint.measureText(s)) / 2f
                    canvas.drawText(s, x, baseline1 + lh * (i / 2), paint)
                }
            }
            MiraClocks.Layout.SPLIT -> {
                canvas.drawText(hh, left, baseline1, paint)
                paint.alpha = 190
                val x = left + width - paint.measureText(mm)
                canvas.drawText(mm, x, baseline1 + lh, paint)
                paint.alpha = 255
            }
            MiraClocks.Layout.ANALOG -> drawAnalog(canvas)
        }
    }

    /** Dot-matrix look: the digits sampled onto a grid of round dots. */
    private fun drawDots(canvas: Canvas, text: String, baseline: Float) {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        val mask = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ALPHA_8)
        val mc = Canvas(mask)
        val x = if (style.alignStart) paddingLeft.toFloat() else (w - paint.measureText(text)) / 2f
        mc.drawText(text, x, baseline, paint)
        val step = (5.5f * density * scale).coerceAtLeast(3f)
        val r = step * 0.36f
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = this@MiraClockView.color }
        var yy = step / 2f
        while (yy < h) {
            var xx = step / 2f
            while (xx < w) {
                if (Color.alpha(mask.getPixel(xx.toInt().coerceIn(0, w - 1), yy.toInt().coerceIn(0, h - 1))) > 110) {
                    canvas.drawCircle(xx, yy, r, dot)
                }
                xx += step
            }
            yy += step
        }
        mask.recycle()
    }

    private fun drawAnalog(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - 4 * density
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.alpha = 90
        canvas.drawCircle(cx, cy, radius, paint)
        paint.alpha = 255
        // Hour marks.
        paint.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0)
            val outer = radius - 6 * density
            val inner = outer - (if (i % 3 == 0) 14 else 7) * density
            canvas.drawLine(
                cx + (sin(a) * inner).toFloat(), cy - (cos(a) * inner).toFloat(),
                cx + (sin(a) * outer).toFloat(), cy - (cos(a) * outer).toFloat(), paint,
            )
        }
        fun hand(angle: Float, length: Float, stroke: Float) {
            val a = Math.toRadians(angle.toDouble())
            paint.strokeWidth = stroke
            canvas.drawLine(cx, cy, cx + (sin(a) * length).toFloat(), cy - (cos(a) * length).toFloat(), paint)
        }
        hand(hourAngle, radius * 0.52f, 7f * density)
        hand(minuteAngle, radius * 0.78f, 4.5f * density)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, 6f * density, paint)
    }
}

internal class MiraClockFaceController(
    private val ctx: Context,
    settings: ClockSettings,
    private val style: MiraClocks.Style,
    private val isLargeClock: Boolean,
) : ClockFaceController {

    private val large: MiraClockView? = if (isLargeClock) MiraClockView(ctx, style) else null
    private val small: TextView? =
        if (isLargeClock) null
        else
            TextView(ctx).apply {
                id = ClockViewIds.LOCKSCREEN_CLOCK_VIEW_SMALL
                setSingleLine(true)
                typeface = MiraClocks.typeface(ctx, style)
                letterSpacing = style.tracking.coerceAtLeast(-0.02f)
                includeFontPadding = false
            }

    var zone: TimeZone? = null
        set(v) {
            field = v
            large?.zone = v
        }

    private var dozeFraction = 0f

    override val view: View = large ?: small!!
    override val layout = DefaultClockFaceLayout(view)
    override val config = ClockFaceConfig()
    override var theme = ThemeConfig(isDarkTheme = true, settings.seedColor)

    private fun applyColor() {
        val lock = theme.getDefaultColor(ctx)
        val aod = theme.getAodColor(ctx)
        val c = blend(lock, aod, dozeFraction)
        large?.color = c
        small?.setTextColor(c)
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val u = t.coerceIn(0f, 1f)
        fun ch(x: Int, y: Int) = (x + (y - x) * u).toInt()
        return Color.argb(
            ch(Color.alpha(a), Color.alpha(b)), ch(Color.red(a), Color.red(b)),
            ch(Color.green(a), Color.green(b)), ch(Color.blue(a), Color.blue(b)),
        )
    }

    override val events =
        object : ClockFaceEvents {
            override fun onTimeTick() {
                large?.refresh()
                small?.let {
                    val (h, m) = MiraClocks.timeParts(ctx, zone)
                    it.typeface = MiraClocks.typeface(ctx, style)
                    it.text = "$h:$m"
                }
            }

            override fun onThemeChanged(theme: ThemeConfig) {
                this@MiraClockFaceController.theme = theme
                applyColor()
            }

            override fun onFontSettingChanged(fontSizePx: Float) {
                small?.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSizePx)
            }

            override fun onTargetRegionChanged(targetRegion: VRect) {}

            override fun onSecondaryDisplayChanged(onSecondaryDisplay: Boolean) {}
        }

    override val animations =
        object : ClockAnimations {
            override fun enter() {}

            override fun doze(fraction: Float) {
                dozeFraction = fraction
                applyColor()
            }

            override fun fold(fraction: Float) {}

            override fun charge() {}

            override fun onPositionAnimated(anim: ClockPositionAnimationArgs) {}

            override fun onPickerCarouselSwiping(swipingFraction: Float) {}

            override fun onFidgetTap(x: Float, y: Float) {}

            override fun onFontAxesChanged(style: ClockAxisStyle) {}
        }
}

internal class MiraClockController(
    ctx: Context,
    settings: ClockSettings,
    style: MiraClocks.Style,
) : ClockController {
    override val smallClock = MiraClockFaceController(ctx, settings, style, isLargeClock = false)
    override val largeClock = MiraClockFaceController(ctx, settings, style, isLargeClock = true)
    override val config = ClockConfig(style.id, style.name, "${style.name} clock")
    override val eventListeners = ClockEventListeners()

    private fun tick() {
        smallClock.events.onTimeTick()
        largeClock.events.onTimeTick()
    }

    override val events =
        object : ClockEvents {
            override fun onTimeZoneChanged(timeZone: TimeZone) {
                smallClock.zone = timeZone
                largeClock.zone = timeZone
                tick()
            }

            override fun onTimeFormatChanged(formatKind: TimeFormatKind) = tick()

            override fun onLocaleChanged(locale: Locale) = tick()

            override fun onWeatherDataChanged(data: WeatherData) {}

            override fun onAlarmDataChanged(data: AlarmData) {}

            override fun onZenDataChanged(data: ZenData) {}
        }

    override fun initialize(isDarkTheme: Boolean, dozeFraction: Float, foldFraction: Float) {
        for (face in listOf(smallClock, largeClock)) {
            face.events.onThemeChanged(face.theme.copy(isDarkTheme = isDarkTheme))
            face.animations.doze(dozeFraction)
            face.events.onTimeTick()
        }
    }

    override fun dump(pw: PrintWriter) {
        pw.println("MiraClockController ${config.id}")
    }
}

/** Picker thumbnail: the style's own "10:45" (or a small dial for Analog). */
internal class MiraClockThumbnail(ctx: Context, private val style: MiraClocks.Style) : Drawable() {
    private val density = ctx.resources.displayMetrics.density
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.create(this@MiraClockThumbnail.style.family, Typeface.NORMAL), this@MiraClockThumbnail.style.weight, false)
            letterSpacing = this@MiraClockThumbnail.style.tracking
        }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        if (style.layout == MiraClocks.Layout.ANALOG) {
            val r = min(b.width(), b.height()) * 0.36f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2 * density
            canvas.drawCircle(cx, cy, r, paint)
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(cx, cy, cx + r * 0.45f, cy - r * 0.2f, paint)
            canvas.drawLine(cx, cy, cx - r * 0.1f, cy - r * 0.75f, paint)
            paint.style = Paint.Style.FILL
            return
        }
        val stacked = style.layout in setOf(MiraClocks.Layout.STACK, MiraClocks.Layout.QUAD, MiraClocks.Layout.SPLIT)
        paint.textSize = b.height() * (if (stacked) 0.34f else 0.4f)
        if (style.layout == MiraClocks.Layout.OUTLINE) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f * density
        } else {
            paint.style = Paint.Style.FILL
        }
        if (stacked) {
            canvas.drawText("10", cx - paint.measureText("10") / 2f, cy - paint.textSize * 0.08f, paint)
            canvas.drawText("45", cx - paint.measureText("45") / 2f, cy + paint.textSize * 0.82f, paint)
        } else {
            canvas.drawText("10:45", cx - paint.measureText("10:45") / 2f, cy + paint.textSize * 0.36f, paint)
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = (120 * density).toInt()

    override fun getIntrinsicHeight(): Int = (120 * density).toInt()
}
