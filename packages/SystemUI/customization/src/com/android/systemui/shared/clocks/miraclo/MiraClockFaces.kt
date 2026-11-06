/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.shared.clocks.miraclo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.provider.Settings
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * MIRACLO lock screen clock faces. The same drawing code is in System UI (the lock screen) and
 * in Settings (Mira Studio's previews), so what you pick is exactly what you get.
 *
 * Settings.Secure:
 * - miraclo_lock_clock_weight: 0 = the face's own, else 100..900
 * - miraclo_lock_clock_scale: size in percent, 60..140
 * - miraclo_lock_clock_layout / _align / _font: the Custom face (0 one line, 1 two lines;
 *   0 centre, 1 left; 0 Mira Sans, 1 Serif, 2 Condensed, 3 Mono)
 */
object MiraClockFaces {
    enum class Layout { INLINE, STACK, DOTS, ANALOG }

    class Face(
        val key: String,
        val name: String,
        val description: String,
        val layout: Layout,
        /** System font family. */
        val family: String,
        /** Weight of the hours (and of everything when [minuteWeight] is 0). */
        val weight: Int,
        /** A lighter weight for the minutes, or 0 for the same. */
        val minuteWeight: Int,
        /** Digit height in dp at 100%. */
        val size: Float,
        /** Letter spacing in em. */
        val tracking: Float,
        val alignStart: Boolean,
        /** Outlined digits (always, not only on the always-on display). */
        val outlined: Boolean = false,
        /** Minutes slightly dimmer than the hours. */
        val softMinutes: Boolean = false,
    ) {
        val id = "MIRA_" + key.uppercase(java.util.Locale.ROOT)
    }

    val FACES =
        listOf(
            Face("coloros", "ColorOS", "Big, light and clean, the hours a touch stronger",
                Layout.INLINE, "mira-sans", 500, 250, 84f, -0.01f, false),
            Face("ios", "iOS", "Bold and centred, with the date above",
                Layout.INLINE, "mira-sans", 650, 0, 88f, -0.035f, false),
            Face("oneui", "One UI", "Hours over minutes, on the left",
                Layout.STACK, "mira-sans", 700, 0, 96f, -0.03f, true),
            Face("nothing", "Nothing", "Dot matrix digits",
                Layout.DOTS, "mira-sans", 400, 0, 78f, 0f, false),
            Face("hyperos", "HyperOS", "Tall stacked digits, thin and airy",
                Layout.STACK, "mira-sans", 250, 0, 118f, -0.02f, false, softMinutes = true),
            Face("analog", "Analog", "Hands and hour marks",
                Layout.ANALOG, "mira-sans", 400, 0, 0f, 0f, false),
            Face("outline", "Outline", "Outlined digits, easy on the always-on display",
                Layout.INLINE, "mira-sans", 800, 0, 84f, -0.02f, false, outlined = true),
            Face("custom", "Custom", "Your own: one or two lines, font, weight and size",
                Layout.INLINE, "mira-sans", 500, 0, 88f, -0.02f, false),
        )

    private val byId = FACES.associateBy { it.id }

    /** The face for a clock id; MIRACLO ids from older builds fall back to ColorOS. */
    fun find(id: String?): Face? =
        id?.let { byId[it] ?: if (it.startsWith("MIRA_")) FACES[0] else null }

    const val KEY_WEIGHT = "miraclo_lock_clock_weight"
    const val KEY_SCALE = "miraclo_lock_clock_scale"
    const val KEY_LAYOUT = "miraclo_lock_clock_layout"
    const val KEY_ALIGN = "miraclo_lock_clock_align"
    const val KEY_FONT = "miraclo_lock_clock_font"
    val KEYS = listOf(KEY_WEIGHT, KEY_SCALE, KEY_LAYOUT, KEY_ALIGN, KEY_FONT)
    val FONTS = listOf("mira-sans", "serif", "sans-serif-condensed", "monospace")
    val FONT_NAMES = listOf("Mira Sans", "Serif", "Condensed", "Mono")

    /** What the user set in Mira Studio, read once per change. */
    class Options(
        val weight: Int = 0,
        val scale: Float = 1f,
        val stacked: Boolean = false,
        val alignStart: Boolean = false,
        val font: Int = 0,
    )

    fun options(ctx: Context): Options {
        fun get(k: String, d: Int) =
            runCatching { Settings.Secure.getInt(ctx.contentResolver, k, d) }.getOrDefault(d)
        return Options(
            weight = get(KEY_WEIGHT, 0),
            scale = get(KEY_SCALE, 100).coerceIn(60, 140) / 100f,
            stacked = get(KEY_LAYOUT, 0) == 1,
            alignStart = get(KEY_ALIGN, 0) == 1,
            font = get(KEY_FONT, 0).coerceIn(0, FONTS.size - 1),
        )
    }

    /** The face with the Custom choices applied. */
    fun resolve(face: Face, o: Options): Face =
        if (face.key != "custom") face
        else
            Face(face.key, face.name, face.description,
                if (o.stacked) Layout.STACK else Layout.INLINE, FONTS[o.font], face.weight, 0,
                if (o.stacked) 104f else face.size, face.tracking, o.alignStart)

    private fun typeface(family: String, weight: Int): Typeface =
        Typeface.create(Typeface.create(family, Typeface.NORMAL), weight.coerceIn(1, 1000), false)

    /** Sets up [p] for [face]'s digits at [px] tall glyphs; returns the text size used. */
    private fun setUp(p: Paint, face: Face, weight: Int, sizePx: Float) {
        p.typeface = typeface(face.family, weight)
        p.setFontVariationSettings("'wght' $weight")
        p.letterSpacing = face.tracking
        // sizePx is the height of a digit; text size is about 1.38 x that for these fonts.
        p.textSize = sizePx * 1.38f
    }

    private val bounds = Rect()

    /** Height of the digits (top to baseline) for the current paint. */
    private fun digitHeight(p: Paint): Float {
        p.getTextBounds("0123456789", 0, 10, bounds)
        return -bounds.top.toFloat()
    }

    // 5 x 7 dot matrix digits (our own), and a two-dot colon.
    private val DOT_GLYPHS =
        mapOf(
            '0' to arrayOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
            '1' to arrayOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
            '2' to arrayOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
            '3' to arrayOf("11111", "00010", "00100", "00010", "00001", "10001", "01110"),
            '4' to arrayOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
            '5' to arrayOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
            '6' to arrayOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
            '7' to arrayOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
            '8' to arrayOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
            '9' to arrayOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
            ':' to arrayOf("0", "0", "1", "0", "1", "0", "0"),
        )

    /** Width and height in px the face needs at [density] (dp scale) for the time [hh]:[mm]. */
    fun measure(
        face0: Face, o: Options, hh: String, mm: String, density: Float, maxWidth: Float,
    ): Pair<Float, Float> {
        val face = resolve(face0, o)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val size = fitSize(face, o, hh, mm, density, maxWidth, p)
        return when (face.layout) {
            Layout.ANALOG -> (size to size)
            Layout.DOTS -> {
                val step = size / 7f
                (dotColumns("$hh:$mm") * step to size)
            }
            Layout.STACK -> {
                setUp(p, face, weightOf(face, o), size)
                val w = maxOf(p.measureText(hh), p.measureText(mm))
                (w to size * 2f + size * GAP)
            }
            Layout.INLINE -> {
                setUp(p, face, weightOf(face, o), size)
                (p.measureText("$hh:$mm") to size)
            }
        }
    }

    private const val GAP = 0.16f

    private fun weightOf(face: Face, o: Options) = if (o.weight in 100..900) o.weight else face.weight

    private fun dotColumns(t: String): Int =
        t.sumOf { (DOT_GLYPHS[it]?.get(0)?.length ?: 5) + 1 } - 1

    /** Digit height in px: the face's size times the user's size, shrunk to fit [maxWidth]. */
    private fun fitSize(
        face: Face, o: Options, hh: String, mm: String, density: Float, maxWidth: Float, p: Paint,
    ): Float {
        var size =
            if (face.layout == Layout.ANALOG) 210f * density * o.scale
            else face.size * density * o.scale
        if (maxWidth <= 0f) return size
        val w =
            when (face.layout) {
                Layout.ANALOG -> size
                Layout.DOTS -> dotColumns("$hh:$mm") * size / 7f
                Layout.STACK -> {
                    setUp(p, face, weightOf(face, o), size)
                    maxOf(p.measureText(hh), p.measureText(mm))
                }
                Layout.INLINE -> {
                    setUp(p, face, weightOf(face, o), size)
                    p.measureText("$hh:$mm")
                }
            }
        if (w > maxWidth * 0.94f) size *= maxWidth * 0.94f / w
        return size
    }

    /**
     * Draws the face filling [width] x [height] (px) in [color]. [aod] outlines the digits so
     * few pixels are lit. [hourAngle] and [minuteAngle] are for the analog face, in degrees.
     */
    fun draw(
        c: Canvas,
        face0: Face,
        o: Options,
        hh: String,
        mm: String,
        hourAngle: Float,
        minuteAngle: Float,
        color: Int,
        aod: Boolean,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        density: Float,
    ) {
        val face = resolve(face0, o)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        p.color = color
        val size = fitSize(face, o, hh, mm, density, width, p)
        val outline = aod || face.outlined
        fun styleFor(base: Paint) {
            if (outline) {
                base.style = Paint.Style.STROKE
                base.strokeWidth = (if (face.outlined && !aod) 2.2f else 1.6f) * density
                base.strokeJoin = Paint.Join.ROUND
            } else {
                base.style = Paint.Style.FILL
            }
        }
        val start = face.alignStart
        when (face.layout) {
            Layout.INLINE -> {
                val w = weightOf(face, o)
                val mw = if (face.minuteWeight > 0 && o.weight !in 100..900) face.minuteWeight else w
                setUp(p, face, w, size)
                styleFor(p)
                val baseline = top + (height + digitHeight(p)) / 2f
                if (mw == w) {
                    val t = "$hh:$mm"
                    val x = if (start) left else left + (width - p.measureText(t)) / 2f
                    c.drawText(t, x, baseline, p)
                } else {
                    val p2 = Paint(p)
                    setUp(p2, face, mw, size)
                    styleFor(p2)
                    val a = hh
                    val b = ":$mm"
                    val total = p.measureText(a) + p2.measureText(b)
                    val x = if (start) left else left + (width - total) / 2f
                    c.drawText(a, x, baseline, p)
                    c.drawText(b, x + p.measureText(a), baseline, p2)
                }
            }
            Layout.STACK -> {
                setUp(p, face, weightOf(face, o), size)
                styleFor(p)
                val dh = digitHeight(p)
                val block = dh * 2f + dh * GAP
                val y0 = top + (height - block) / 2f + dh
                fun line(t: String, y: Float, alpha: Int) {
                    p.alpha = alpha
                    val x = if (start) left else left + (width - p.measureText(t)) / 2f
                    c.drawText(t, x, y, p)
                }
                val base = android.graphics.Color.alpha(color)
                line(hh, y0, base)
                line(mm, y0 + dh * (1f + GAP), if (face.softMinutes) base * 3 / 4 else base)
            }
            Layout.DOTS -> {
                val t = "$hh:$mm"
                val step = size / 7f
                val total = dotColumns(t) * step
                var x = if (start) left else left + (width - total) / 2f
                val y = top + (height - size) / 2f
                val r = step * (if (aod) 0.30f else 0.40f)
                p.style = if (aod) Paint.Style.STROKE else Paint.Style.FILL
                p.strokeWidth = 1.2f * density
                for (ch in t) {
                    val g = DOT_GLYPHS[ch] ?: continue
                    val cols = g[0].length
                    for (row in 0 until 7) for (col in 0 until cols) {
                        if (g[row][col] == '1') {
                            c.drawCircle(x + col * step + step / 2f, y + row * step + step / 2f, r, p)
                        }
                    }
                    x += (cols + 1) * step
                }
            }
            Layout.ANALOG -> {
                val d = min(size, min(width, height))
                val cx = if (start) left + d / 2f else left + width / 2f
                val cy = top + height / 2f
                val radius = d / 2f - 4 * density
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                p.strokeWidth = 2f * density
                val base = android.graphics.Color.alpha(color)
                p.alpha = base * 35 / 100
                c.drawCircle(cx, cy, radius, p)
                p.alpha = base
                for (i in 0 until 12) {
                    val a = Math.toRadians(i * 30.0)
                    val outer = radius - 8 * density
                    val inner = outer - (if (i % 3 == 0) 14 else 6) * density
                    p.strokeWidth = (if (i % 3 == 0) 3f else 2f) * density
                    c.drawLine(
                        cx + (sin(a) * inner).toFloat(), cy - (cos(a) * inner).toFloat(),
                        cx + (sin(a) * outer).toFloat(), cy - (cos(a) * outer).toFloat(), p,
                    )
                }
                fun hand(angle: Float, length: Float, stroke: Float) {
                    val a = Math.toRadians(angle.toDouble())
                    p.strokeWidth = stroke
                    c.drawLine(cx, cy, cx + (sin(a) * length).toFloat(), cy - (cos(a) * length).toFloat(), p)
                }
                val k = (if (o.weight in 100..900) o.weight else 500) / 500f
                hand(hourAngle, radius * 0.5f, 7f * density * k)
                hand(minuteAngle, radius * 0.76f, 4.5f * density * k)
                p.style = if (aod) Paint.Style.STROKE else Paint.Style.FILL
                c.drawCircle(cx, cy, 6f * density, p)
            }
        }
    }

    /** Typeface for the small clock (shown when notifications take the space). */
    fun smallTypeface(face0: Face, o: Options): Typeface {
        val face = resolve(face0, o)
        val fam = if (face.layout == Layout.DOTS || face.layout == Layout.ANALOG) "mira-sans" else face.family
        return typeface(fam, weightOf(face, o).coerceIn(300, 800))
    }
}
