/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.shared.clocks.miraclo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
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
import kotlin.math.min

/**
 * MIRACLO lock screen clocks (see [MiraClockFaces] for the faces). They are served by
 * [com.android.systemui.shared.clocks.DefaultClockProvider], so the wallpaper & style picker and
 * Mira Studio both list them.
 */
object MiraClocks {
    fun find(id: String?): MiraClockFaces.Face? = MiraClockFaces.find(id)

    fun metadata(): List<ClockMetadata> = MiraClockFaces.FACES.map { ClockMetadata(it.id) }

    fun createClock(ctx: Context, settings: ClockSettings): ClockController? =
        find(settings.clockId)?.let { MiraClockController(ctx, settings, it) }

    fun pickerConfig(ctx: Context, settings: ClockSettings): ClockPickerConfig? =
        find(settings.clockId)?.let {
            ClockPickerConfig(
                it.id,
                it.name,
                it.description,
                MiraClockThumbnail(ctx, it),
                isReactiveToTone = true,
            )
        }

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

/** The large clock: one view that draws the face across the width it is given. */
internal class MiraClockView(ctx: Context, private val face: MiraClockFaces.Face) : View(ctx) {
    var zone: TimeZone? = null
    var color: Int = Color.WHITE
        set(v) {
            field = v
            invalidate()
        }

    /** On the always-on display the digits are outlined, so few pixels are lit. */
    var outline: Boolean = false
        set(v) {
            if (field == v) return
            field = v
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private var hh = "12"
    private var mm = "00"
    private var minuteAngle = 0f
    private var hourAngle = 0f
    private var options = MiraClockFaces.Options()

    init {
        id = ClockViewIds.LOCKSCREEN_CLOCK_VIEW_LARGE
        refresh()
    }

    // Changes from Mira Studio show at once.
    private val observer =
        object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        runCatching {
            for (key in MiraClockFaces.KEYS) {
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
        val cal = Calendar.getInstance()
        zone?.let { cal.timeZone = java.util.TimeZone.getTimeZone(it.id) }
        minuteAngle = cal.get(Calendar.MINUTE) * 6f
        hourAngle = (cal.get(Calendar.HOUR) * 30f) + cal.get(Calendar.MINUTE) * 0.5f
        val newOptions = MiraClockFaces.options(context)
        val resize = h.length != hh.length || newOptions.scale != options.scale ||
            newOptions.stacked != options.stacked || newOptions.font != options.font ||
            newOptions.weight != options.weight
        hh = h
        mm = m
        options = newOptions
        contentDescription = "$hh:$mm"
        if (resize) requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wMode = MeasureSpec.getMode(widthMeasureSpec)
        val wSize = MeasureSpec.getSize(widthMeasureSpec)
        val avail = if (wMode == MeasureSpec.UNSPECIFIED) 0f else (wSize - paddingLeft - paddingRight).toFloat()
        // Measured with the widest digits so the clock does not jump as the time changes.
        val (w, h) = MiraClockFaces.measure(face, options, if (hh.length == 2) "00" else "0", "00", density, avail)
        // Take the whole width offered, so the face is centred (or left aligned) on screen.
        val width = if (wMode == MeasureSpec.UNSPECIFIED) w.toInt() + paddingLeft + paddingRight else wSize
        setMeasuredDimension(width, resolveSize(h.toInt() + paddingTop + paddingBottom, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        MiraClockFaces.draw(
            canvas, face, options, hh, mm, hourAngle, minuteAngle, color, outline,
            paddingLeft.toFloat(), paddingTop.toFloat(),
            (width - paddingLeft - paddingRight).toFloat(),
            (height - paddingTop - paddingBottom).toFloat(),
            density,
        )
    }
}

internal class MiraClockFaceController(
    private val ctx: Context,
    settings: ClockSettings,
    private val style: MiraClockFaces.Face,
    private val isLargeClock: Boolean,
) : ClockFaceController {

    private val large: MiraClockView? = if (isLargeClock) MiraClockView(ctx, style) else null
    private val small: TextView? =
        if (isLargeClock) null
        else
            TextView(ctx).apply {
                id = ClockViewIds.LOCKSCREEN_CLOCK_VIEW_SMALL
                setSingleLine(true)
                typeface = MiraClockFaces.smallTypeface(style, MiraClockFaces.options(ctx))
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
                    it.typeface = MiraClockFaces.smallTypeface(style, MiraClockFaces.options(ctx))
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
                large?.outline = fraction > 0.5f
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
    style: MiraClockFaces.Face,
) : ClockController {
    override val smallClock = MiraClockFaceController(ctx, settings, style, isLargeClock = false)
    override val largeClock = MiraClockFaceController(ctx, settings, style, isLargeClock = true)
    override val config = ClockConfig(style.id, style.name, style.description)
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

/** Picker thumbnail: the face itself showing 10:45. */
internal class MiraClockThumbnail(ctx: Context, private val face: MiraClockFaces.Face) : Drawable() {
    private val density = ctx.resources.displayMetrics.density
    private val options = MiraClockFaces.options(ctx)
    private var alphaValue = 255

    override fun draw(canvas: Canvas) {
        val b = bounds
        val pad = b.width() * 0.08f
        // The faces are drawn at lock screen size, then scaled into the thumbnail.
        val w = 340f * density
        val (fw, fh) = MiraClockFaces.measure(face, options, "10", "45", density, w)
        val k = min((b.width() - pad * 2) / maxOf(1f, fw), (b.height() - pad * 2) / maxOf(1f, fh)).coerceAtMost(1f)
        canvas.save()
        canvas.translate(b.exactCenterX() - fw * k / 2f, b.exactCenterY() - fh * k / 2f)
        canvas.scale(k, k)
        MiraClockFaces.draw(
            canvas, face, options, "10", "45", 300f + 22.5f, 270f, Color.argb(alphaValue, 255, 255, 255),
            false, 0f, 0f, fw, fh, density,
        )
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        alphaValue = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = (120 * density).toInt()

    override fun getIntrinsicHeight(): Int = (120 * density).toInt()
}
