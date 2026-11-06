/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.miraclo

import android.content.Context
import android.database.ContentObserver
import android.graphics.Typeface
import android.net.TrafficStats
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.TypefaceSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.TextView
import com.android.systemui.miraclo.island.IslandStatusBarSpace
import com.android.systemui.res.R

/**
 * Network speed in the status bar (Mira Studio › Status bar › Network speed).
 *
 * The default look follows ColorOS and HyperOS: the speed in small bold figures with its unit
 * underneath, both centred, as narrow as an icon ("12.6" over "KB/s"). It can also be one line,
 * or down and up speeds on two lines.
 *
 * Two copies live in the status bar: one after the clock ([side] = 0) and one before the system
 * icons ([side] = 1); only the copy on the chosen side shows. It fades out while the island shows
 * anything, takes the clock's colour, and only samples while it is on screen.
 */
class MiraCLONetSpeedView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TextView(context, attrs) {

    private val side: Int
    private val handler = Handler(Looper.getMainLooper())
    private var position = 1
    private var style = 0
    private var arrows = false
    private var unit = 0
    private var size = 1
    private var hideIdle = true
    private var islandActive = false
    private var lastRx = -1L
    private var lastTx = -1L
    private var lastAt = 0L
    private var running = false
    private val sans = Typeface.create("mira-sans", Typeface.NORMAL)
    private val bold = Typeface.create(sans, 650, false)
    private val medium = Typeface.create(sans, 500, false)

    init {
        val a = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.tag))
        side = if (a.getString(0) == "start") 0 else 1
        a.recycle()
        gravity = Gravity.CENTER
        typeface = bold
        fontFeatureSettings = "tnum"
        includeFontPadding = false
        setSingleLine(false)
        visibility = GONE
    }

    private val observer =
        object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) = reload()
        }

    private val islandListener = Runnable {
        islandActive = IslandStatusBarSpace.isActive()
        animate().cancel()
        animate().alpha(if (islandActive) 0f else 1f).setDuration(if (islandActive) 120 else 180).start()
    }

    private val tick =
        object : Runnable {
            override fun run() {
                sample()
                if (running) handler.postDelayed(this, 1000L)
            }
        }

    // Take the clock's colour (light or dark status bar), checked cheaply before each draw.
    private val tint =
        ViewTreeObserver.OnPreDrawListener {
            val clock = rootView?.findViewById<TextView>(R.id.clock)
            if (clock != null && clock.currentTextColor != currentTextColor) {
                setTextColor(clock.currentTextColor)
            }
            true
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        for (k in KEYS) {
            context.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(k), false, observer)
        }
        IslandStatusBarSpace.addListener(islandListener)
        viewTreeObserver.addOnPreDrawListener(tint)
        islandListener.run()
        reload()
    }

    override fun onDetachedFromWindow() {
        context.contentResolver.unregisterContentObserver(observer)
        IslandStatusBarSpace.removeListener(islandListener)
        viewTreeObserver.removeOnPreDrawListener(tint)
        stop()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(v: Int) {
        super.onWindowVisibilityChanged(v)
        if (v == View.VISIBLE) reload() else stop()
    }

    private fun get(key: String, def: Int) = Settings.Secure.getInt(context.contentResolver, key, def)

    private fun px(sp: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics).toInt()

    /** Text sizes in sp for the figures and the unit. */
    private fun sizes(): Pair<Float, Float> {
        val k = when (size) { 0 -> 0.88f; 2 -> 1.14f; else -> 1f }
        return when (style) {
            0 -> (10.5f * k) to (7.5f * k)
            1 -> (11f * k) to (9f * k)
            else -> (8.5f * k) to (7f * k)
        }
    }

    private fun reload() {
        val enabled = get(KEY_ON, 0) != 0
        position = get(KEY_POS, 1).coerceIn(0, 1)
        style = get(KEY_STYLE, 0).coerceIn(0, 2)
        arrows = get(KEY_ARROWS, 0) != 0
        unit = get(KEY_UNIT, 0).coerceIn(0, 2)
        size = get(KEY_SIZE, 1).coerceIn(0, 2)
        hideIdle = get(KEY_HIDE_IDLE, 0) != 0
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizes().first)
        setLineSpacing(0f, if (style == 1) 1f else 0.86f)
        maxLines = if (style == 1) 1 else 2
        // Room for "888" so the width does not jump as the figures change.
        minWidth = if (style == 0) px(sizes().first * 1.9f) else 0
        val mine = enabled && position == side
        if (mine && windowVisibility == View.VISIBLE) start() else { stop(); visibility = GONE }
    }

    private fun start() {
        if (running) return
        running = true
        lastRx = -1L
        handler.post(tick)
    }

    private fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    private fun sample() {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val now = SystemClock.elapsedRealtime()
        if (rx < 0 || tx < 0) { visibility = GONE; return }
        if (lastRx < 0) { lastRx = rx; lastTx = tx; lastAt = now; show(0f, 0f); return }
        val sec = ((now - lastAt).coerceAtLeast(1L)) / 1000f
        val down = ((rx - lastRx).coerceAtLeast(0L)) / sec
        val up = ((tx - lastTx).coerceAtLeast(0L)) / sec
        lastRx = rx; lastTx = tx; lastAt = now
        if (hideIdle && down + up < IDLE_BYTES) { visibility = GONE; return }
        show(down, up)
    }

    private fun show(down: Float, up: Float) {
        val (big, small) = sizes()
        val out = SpannableStringBuilder()
        fun add(text: String, sp: Float, face: Typeface) {
            val start = out.length
            out.append(text)
            out.setSpan(AbsoluteSizeSpan(px(sp)), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(TypefaceSpan(face), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        when (style) {
            0 -> {
                // ColorOS / HyperOS: the total speed over its unit.
                val (n, u) = fmt(down + up)
                add(n, big, bold)
                out.append("\n")
                add(u, small, medium)
            }
            1 -> {
                val (n, u) = fmt(down + up)
                if (arrows) add(if (down >= up) "↓ " else "↑ ", small, medium)
                add(n, big, bold)
                add(" $u", small, medium)
            }
            else -> {
                val (dn, du) = fmt(down)
                val (un, uu) = fmt(up)
                add(if (arrows) "↓ " else "", small, medium)
                add(dn, big, bold)
                add(" $du", small, medium)
                out.append("\n")
                add(if (arrows) "↑ " else "", small, medium)
                add(un, big, bold)
                add(" $uu", small, medium)
            }
        }
        text = out
        visibility = VISIBLE
    }

    /** Bytes per second as three figures and a unit: "0.00" "KB/s", "12.6" "KB/s", "128" "MB/s". */
    private fun fmt(bytesPerSec: Float): Pair<String, String> {
        val v: Float
        val u: String
        when (unit) {
            1 -> { // bits
                val b = bytesPerSec * 8
                if (b >= 1_000_000f) { v = b / 1_000_000f; u = "Mb/s" } else { v = b / 1000f; u = "Kb/s" }
            }
            2 -> { v = bytesPerSec / (1024f * 1024f); u = "MB/s" }
            else -> {
                if (bytesPerSec >= 1000f * 1024f) { v = bytesPerSec / (1024f * 1024f); u = "MB/s" }
                else { v = bytesPerSec / 1024f; u = "KB/s" }
            }
        }
        val n = when {
            v >= 100f -> "%.0f".format(v)
            v >= 10f -> "%.1f".format(v)
            else -> "%.2f".format(v)
        }
        return n to u
    }

    companion object {
        const val KEY_ON = "miraclo_sb_speed"
        const val KEY_POS = "miraclo_sb_speed_pos"       // 0 left after the clock, 1 right
        const val KEY_STYLE = "miraclo_sb_speed_style"   // 0 speed over unit, 1 one line, 2 down and up
        const val KEY_ARROWS = "miraclo_sb_speed_arrows"
        const val KEY_UNIT = "miraclo_sb_speed_unit"     // 0 auto bytes, 1 bits, 2 always MB/s
        const val KEY_SIZE = "miraclo_sb_speed_size"     // 0 small, 1 normal, 2 large
        const val KEY_HIDE_IDLE = "miraclo_sb_speed_hide_idle"
        private const val IDLE_BYTES = 1024f
        private val KEYS = listOf(KEY_ON, KEY_POS, KEY_STYLE, KEY_ARROWS, KEY_UNIT, KEY_SIZE, KEY_HIDE_IDLE)
    }
}
